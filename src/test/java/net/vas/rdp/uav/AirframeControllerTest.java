package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.AircraftState;
import net.vas.rdp.uav.flight.AirframeController;
import net.vas.rdp.uav.flight.FixedWingFlightController;
import net.vas.rdp.uav.flight.HelicopterFlightController;
import net.vas.rdp.uav.flight.NativeFlightControlCommand;
import net.vas.rdp.uav.mission.ControlOwner;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class AirframeControllerTest {
    @Test
    public void fixedWingUsesTrackCourseAndAltitudeFeedback() {
        UavVehicleProfile profile = new UavVehicleProfile(UavExecutionProfile.ONE_WAY,
                50.0D, 20.0D, 200.0D, 6.0D, false, "SELF_DESTRUCT");
        AircraftState state = state(10L, 0.0D, 20.0D, 0.0D,
                0.0F);

        NativeFlightControlCommand command = AirframeController.forProfile(profile)
                .update(state, 300.0D, 60.0D, 0.0D, 10L);

        assertTrue(command.isSupported());
        assertTrue(command.getYaw() < 0.0F);
        assertTrue(command.getRoll() < 0.0F);
        assertTrue(command.getPitch() < 0.0F);
        assertTrue(command.getThrottle() >= profile.getStallSpeed());
        assertTrue(command.getDesiredVerticalSpeed() > 0.0D);
        assertTrue(Math.abs(command.getRoll()) <= profile.getMaximumBankDegrees());
    }

    @Test
    public void helicopterSeparatesHorizontalAttitudeAndCollective() {
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.HELICOPTER_ONE_WAY_FPV,
                8.0D, 4.0D, 32.0D, 6.0D, false, "NONE");
        AircraftState state = state(12L, 10.0D, 8.0D, 4.0D,
                0.0F);

        NativeFlightControlCommand command = new HelicopterFlightController(profile)
                .update(state, 30.0D, 28.0D, 20.0D, 12L);

        assertTrue(command.isSupported());
        assertTrue(command.getPitch() < 0.0F);
        assertTrue(command.getRoll() > 0.0F);
        assertTrue(command.getThrottle() >= 0.25D && command.getThrottle() <= 0.88D);
        assertTrue(command.getDesiredVerticalSpeed() > 0.0D);
    }

    @Test
    public void helicopterCommandsOpposingAttitudeToBrakeAtWaypoint() {
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.HELICOPTER_ONE_WAY_FPV,
                8.0D, 4.0D, 32.0D, 6.0D, false, "NONE");
        AircraftState state = AircraftState.observe(UUID.randomUUID(), 0, 12L,
                10.0D, 8.0D, 4.0D, 9.0D, 8.0D, 3.0D,
                0.0F, 0.0F, 0.0F, 0.6D, "ENROUTE",
                UUID.randomUUID(), ControlOwner.MISSION_AI, true,
                0, 0.0D, 0.0D, 0.0D, 0.0D, 12L, "KNOWN");

        NativeFlightControlCommand command = new HelicopterFlightController(profile)
                .update(state, 10.0D, 8.0D, 4.0D, 12L);

        assertTrue(command.isSupported());
        assertTrue("positive forward/lateral velocity should be opposed", command.getPitch() > 0.0F);
        assertTrue("positive lateral velocity should be opposed", command.getRoll() < 0.0F);
    }

    @Test
    public void helicopterAtWaypointCommandsNeutralAttitudeAndNoVerticalRate() {
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.HELICOPTER_ONE_WAY_FPV,
                8.0D, 4.0D, 32.0D, 6.0D, false, "NONE");
        AircraftState state = state(12L, 10.0D, 8.0D, 4.0D, 0.0F);

        NativeFlightControlCommand command = new HelicopterFlightController(profile)
                .update(state, 10.0D, 8.0D, 4.0D, 12L);

        assertTrue(command.isSupported());
        assertEquals(0.0F, command.getPitch(), 0.0F);
        assertEquals(0.0F, command.getRoll(), 0.0F);
        assertEquals(0.0D, command.getDesiredVerticalSpeed(), 0.0D);
        assertTrue("collective remains in the controller's bounded hold range",
                command.getThrottle() >= 0.35D && command.getThrottle() <= 0.68D);
    }

    @Test
    public void nativeHelicopterAirframeUsesHelicopterControllerEvenWithFixedWingProfile() {
        UavVehicleProfile profile = new UavVehicleProfile(UavExecutionProfile.ONE_WAY,
                32.0D, 8.0D, 128.0D, 4.0D, false, "SELF_DESTRUCT");

        assertTrue(AirframeController.forProfile(profile, true)
                instanceof HelicopterFlightController);
        assertTrue(AirframeController.forProfile(profile, false)
                instanceof FixedWingFlightController);
    }

    @Test
    public void staleStateFailsClosedWithoutInventingACommand() {
        UavVehicleProfile profile = new UavVehicleProfile(UavExecutionProfile.ONE_WAY,
                50.0D, 20.0D, 200.0D, 6.0D, false, "SELF_DESTRUCT");

        NativeFlightControlCommand command = new FixedWingFlightController(profile)
                .update(state(1L, 0.0D, 10.0D, 0.0D,
                                0.0F),
                        20.0D, 10.0D, 10.0D, 4L);

        assertFalse(command.isSupported());
        assertEquals("STALE_OR_INVALID_AIRCRAFT_STATE", command.getReason());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nativeFlightCommandRejectsReverseThrottle() {
        NativeFlightControlCommand.setpoints(
                0.0F, 0.0F, 0.0F, -0.01D, 0.0D, 0.0D, 0.0D);
    }

    @Test
    public void terminalFixedWingUsesRouteDiveSlopeAndCanCommandSteeperPitch() {
        UavVehicleProfile profile = new UavVehicleProfile(UavExecutionProfile.ONE_WAY,
                50.0D, 20.0D, 200.0D, 6.0D, false, "SELF_DESTRUCT");
        AircraftState state = state(
        );

        NativeFlightControlCommand command = new FixedWingFlightController(profile)
                .update(state, 50.0D, 0.0D, 0.0D, 12L);

        assertTrue(command.isSupported());
        assertTrue("terminal setpoint must follow the approach slope",
                command.getPitch() > 40.0F);
        assertTrue(command.getPitch() <= 60.0F);
    }

    @Test
    public void finiteExtremeRouteStillProducesBoundedSetpoints() {
        UavVehicleProfile profile = new UavVehicleProfile(UavExecutionProfile.ONE_WAY,
                50.0D, 20.0D, 200.0D, 6.0D, false, "SELF_DESTRUCT");
        NativeFlightControlCommand command = new FixedWingFlightController(profile)
                .update(state(1L, 0.0D, 50.0D, 0.0D,
                                179.0F),
                        Double.MAX_VALUE, 100.0D, Double.MAX_VALUE, 1L);

        assertTrue(command.isSupported());
        assertTrue(Float.isFinite(command.getYaw()));
        assertTrue(Float.isFinite(command.getPitch()));
        assertTrue(Float.isFinite(command.getRoll()));
        assertTrue(Double.isFinite(command.getThrottle()));
        assertTrue(command.getThrottle() >= 0.0D && command.getThrottle() <= 1.0D);
    }

    private static AircraftState state(long tick, double x, double y, double z,
                                       float yaw) {
        return state(tick, ControlOwner.MISSION_AI, x, y, z,
                yaw);
    }

    private static AircraftState state() {
        return state(12L, ControlOwner.TERMINAL_GUIDANCE, 0.0, 100.0, 0.0, (float) 0.0);
    }

    private static AircraftState state(long tick, ControlOwner owner,
                                       double x, double y, double z,
                                       float yaw) {
        return AircraftState.observeWithPhysicalMotion(UUID.randomUUID(), 0, tick,
                x, y, z, x - 0.0, y - 0.0, z - 0.0,
                0.0, 0.0, 0.0, 1.0D,
                yaw, (float) 0.0, (float) 0.0, 0.6D,
                "ENROUTE", UUID.randomUUID(), owner, true,
                0, 0.0D, 100.0D, 100.0D, 0.0D, tick, "KNOWN");
    }
}
