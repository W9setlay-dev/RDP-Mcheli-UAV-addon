package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.deconfliction.TrajectorySample;
import net.vas.rdp.uav.mission.ControlOwner;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TrajectoryControllerTest {
    @Test
    public void fixedWingUpdate_pathSampleUsesSampleHeadingInYawLead() {
        AircraftState state = state();
        TrajectorySample sample = new TrajectorySample(0.0D, 70.0D, 100.0D,
                180.0F, 0.0F, 0.0F, 5L, 1.0D);
        FixedWingFlightController controller = new FixedWingFlightController(profile());

        NativeFlightControlCommand pathCommand = controller.update(state, sample, 1L);
        NativeFlightControlCommand directCommand = controller.update(
                state, 0.0D, 70.0D, 100.0D, 1L);

        assertTrue(pathCommand.isSupported());
        assertTrue(Math.abs(pathCommand.getYaw() - directCommand.getYaw()) > 1.0F);
    }

    @Test
    public void helicopterUpdate_pathSampleUsesSampleHeadingInYawLead() {
        AircraftState state = state();
        TrajectorySample sample = new TrajectorySample(0.0D, 70.0D, 100.0D,
                180.0F, 0.0F, 0.0F, 5L, 1.0D);
        HelicopterFlightController controller = new HelicopterFlightController(profile());

        NativeFlightControlCommand pathCommand = controller.update(state, sample, 1L);
        NativeFlightControlCommand directCommand = controller.update(
                state, 0.0D, 70.0D, 100.0D, 1L);

        assertTrue(pathCommand.isSupported());
        assertEquals(12.0F, pathCommand.getYaw(), 1.0E-4F);
        assertTrue(Math.abs(pathCommand.getYaw() - directCommand.getYaw()) > 1.0F);
    }

    @Test
    public void airframeControllerContractPreservesTrajectoryHeading() {
        AircraftState state = state();
        TrajectorySample sample = new TrajectorySample(0.0D, 70.0D, 100.0D,
                180.0F, 0.0F, 0.0F, 5L, 1.0D);
        AirframeController controller = new FixedWingFlightController(profile());

        NativeFlightControlCommand pathCommand = controller.update(state, sample, 1L, false);
        NativeFlightControlCommand directCommand = controller.update(
                state, sample.getX(), sample.getY(), sample.getZ(), 1L, false);

        assertTrue(pathCommand.isSupported());
        assertTrue(Math.abs(pathCommand.getYaw() - directCommand.getYaw()) > 1.0F);
    }

    private static AircraftState state() {
        UUID aircraft = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID mission = UUID.fromString("00000000-0000-0000-0000-000000000022");
        return AircraftState.observe(aircraft, 0, 1L,
                0.0D, 70.0D, 0.0D, 0.0D, 70.0D, 0.0D,
                0.0F, 0.0F, 0.0F, 0.5D, "CRUISE", mission,
                ControlOwner.MISSION_AI, true, 0, 0.0D,
                100.0D, 100.0D, 100.0D, 0L, "CLEAR");
    }

    private static UavVehicleProfile profile() {
        return new UavVehicleProfile(UavExecutionProfile.REUSABLE_UAV, 80.0D,
                40.0D, 160.0D, 8.0D, true, "NONE");
    }
}
