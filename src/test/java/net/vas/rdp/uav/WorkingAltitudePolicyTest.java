package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.AircraftState;
import net.vas.rdp.uav.flight.FixedWingFlightController;
import net.vas.rdp.uav.flight.HelicopterFlightController;
import net.vas.rdp.uav.flight.NativeFlightControlCommand;
import net.vas.rdp.uav.flight.WorkingAltitudePolicy;
import net.vas.rdp.uav.mission.ControlOwner;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WorkingAltitudePolicyTest {
    @Test
    public void enforceGroundSafetyWinsOverTargetTracking() {
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.ONE_WAY,
                32.0D,
                8.0D,
                160.0D,
                4.0D,
                false,
                "NONE");

        assertEquals(32.0D,
                WorkingAltitudePolicy.enforceGroundSafety(profile, 15.0D, 24.0D, 256.0D),
                0.0001D);
    }

    @Test
    public void maintainWorkingAltitudeConvergesToSafeTargetOutsideTolerance() {
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.ONE_WAY,
                32.0D,
                8.0D,
                160.0D,
                6.0D,
                false,
                "NONE");

        assertEquals(40.0D,
                WorkingAltitudePolicy.maintainWorkingAltitude(profile, 40.0D, 20.0D, 24.0D, 256.0D),
                0.0001D);
        assertEquals(80.0D,
                WorkingAltitudePolicy.maintainWorkingAltitude(profile, 80.0D, 20.0D, 24.0D, 256.0D),
                0.0001D);
        assertEquals(40.0D,
                WorkingAltitudePolicy.maintainWorkingAltitude(profile, 42.0D, 40.0D, 24.0D, 256.0D),
                0.0001D);
    }

    @Test
    public void clampToProfilePreservesSafeWorkingEnvelope() {
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.REUSABLE_UAV,
                80.0D,
                40.0D,
                180.0D,
                6.0D,
                true,
                "NONE");

        assertEquals(40.0D,
                WorkingAltitudePolicy.clampToProfile(profile, 20.0D),
                0.0001D);
        assertEquals(180.0D,
                WorkingAltitudePolicy.clampToProfile(profile, 250.0D),
                0.0001D);
    }

    @Test
    public void controllerUsesSafeProfileAltitudeEnvelope() {
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.ONE_WAY,
                32.0D,
                8.0D,
                160.0D,
                6.0D,
                false,
                "NONE");

        AircraftState state = AircraftState.observe(
                java.util.UUID.randomUUID(), 0, 1,
                0.0D, 16.0D, 0.0D,
                0.0D, 0.0D, 0.0D,
                0, 0, 0, 1, "ENROUTE",
                java.util.UUID.randomUUID(), ControlOwner.MISSION_AI,
                true, 0, 0, 100, 100, 0, 100, "KNOWN");

        NativeFlightControlCommand fixedWing = new FixedWingFlightController(profile).update(
                state, 10.0D, 500.0D, 0.0D, 1L);
        NativeFlightControlCommand helicopter = new HelicopterFlightController(profile).update(
                state, 10.0D, 500.0D, 0.0D, 1L);

        assertTrue(fixedWing.isSupported());
        assertTrue(helicopter.isSupported());
        assertTrue(Math.abs(fixedWing.getPitch()) <= 20.0F + 0.0001F);
        assertTrue(Math.abs(helicopter.getPitch()) <= 35.0F + 0.0001F);
    }
}
