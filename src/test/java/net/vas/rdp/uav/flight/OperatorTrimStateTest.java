package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.mission.ControlOwner;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class OperatorTrimStateTest {
    @Test
    public void acceptsOnlyFreshOrderedBoundedDeltasAndDecaysAfterRelease() {
        OperatorTrimState state = new OperatorTrimState(UUID.randomUUID());
        UavVehicleProfile profile = profile(UavExecutionProfile.ONE_WAY);

        assertTrue(state.accept(10, 100, 7.0D, 4.0D, 100, profile));
        assertFalse("duplicate sequence is replay", state.accept(10, 101,
                1.0D, 1.0D, 101, profile));
        assertFalse("per-packet yaw clamp", state.accept(11, 101,
                7.6D, 0.0D, 101, profile));
        assertFalse("stale client sample", state.accept(11, 0,
                1.0D, 1.0D, 101, profile));
        assertTrue(state.accept(11, 101, 7.0D, 4.0D, 101, profile));

        OperatorTrimState.Snapshot active = state.snapshot(101);
        assertEquals(13.72D, active.getYawDegrees(), 1.0E-9D);
        assertEquals(7.84D, active.getPitchDegrees(), 1.0E-9D);
        OperatorTrimState.Snapshot released = state.snapshot(131);
        assertTrue(released.getYawDegrees() > 0.0D);
        assertTrue(released.getYawDegrees() < active.getYawDegrees());
        assertTrue(released.getPitchDegrees() < active.getPitchDegrees());
    }

    @Test
    public void cameraYawTrimChangesFixedWingCourseAndRollWithoutSettingPose() {
        UUID missionId = UUID.randomUUID();
        OperatorTrimState state = new OperatorTrimState(missionId);
        UavVehicleProfile profile = profile(UavExecutionProfile.ONE_WAY);
        assertTrue(state.accept(1, 200, 6.0D, 0.0D, 200, profile));
        OperatorTrimState.Snapshot trim = state.snapshot(200);
        AircraftState aircraft = aircraftState(ControlOwner.MISSION_AI, 200L);
        NativeFlightControlCommand baseline = new FixedWingFlightController(profile)
                .update(aircraft, 0.0D, 32.0D, 100.0D, 200L);
        OperatorTrimState.Target corrected = OperatorTrimState.mixTarget(
                0.0D, 32.0D, 100.0D, 0.0D, 32.0D, 0.0D,
                trim, profile, 0.0D, 250.0D);
        NativeFlightControlCommand mixed = new FixedWingFlightController(profile)
                .update(aircraft, corrected.x, corrected.y, corrected.z, 200L);

        assertTrue(baseline.isSupported());
        assertTrue(mixed.isSupported());
        assertNotEquals(baseline.getDesiredTrack(), mixed.getDesiredTrack(), 0.1D);
        assertNotEquals(baseline.getRoll(), mixed.getRoll(), 0.1D);
        assertEquals(0.0D, aircraft.getX(), 0.0D);
        assertEquals(0.0D, aircraft.getZ(), 0.0D);
    }

    @Test
    public void helicopterConsumesTrimThroughItsOwnCyclicAndCollectiveController() {
        UUID missionId = UUID.randomUUID();
        OperatorTrimState trimState = new OperatorTrimState(missionId);
        UavVehicleProfile profile = profile(UavExecutionProfile.HELICOPTER_ONE_WAY_FPV);
        assertTrue(trimState.accept(1, 300, 4.0D, 2.0D, 300, profile));
        OperatorTrimState.Snapshot trim = trimState.snapshot(300);
        AircraftState aircraft = aircraftState(ControlOwner.MISSION_AI, 300L);
        OperatorTrimState.Target corrected = OperatorTrimState.mixTarget(
                0.0D, 8.0D, 80.0D, 0.0D, 32.0D, 0.0D,
                trim, profile, 4.0D, 250.0D);
        NativeFlightControlCommand command = new HelicopterFlightController(profile)
                .update(aircraft, corrected.x, corrected.y, corrected.z, 300L);

        assertTrue(command.isSupported());
        assertNotEquals(0.0D, command.getDesiredTrack(), 0.1D);
        assertTrue(Double.isFinite(command.getThrottle()));
        assertTrue(Math.abs(command.getPitch()) <= 22.0F);
        assertTrue(Math.abs(command.getRoll()) <= 20.0F);
    }

    @Test
    public void terrainAltitudeMaintainsCruiseAglAndRefusesImpossibleWorldClearance() {
        UavVehicleProfile profile = profile(UavExecutionProfile.ONE_WAY);
        TerrainRoutePlanner.AltitudeDecision cruise = TerrainRoutePlanner.transitAltitude(
                100.0D, 256.0D, 0.0D, profile);
        TerrainRoutePlanner.AltitudeDecision pitchCorrection = TerrainRoutePlanner.transitAltitude(
                100.0D, 256.0D, 10.0D, profile);
        TerrainRoutePlanner.AltitudeDecision capped = TerrainRoutePlanner.transitAltitude(
                100.0D, 110.0D, 10.0D, profile);
        TerrainRoutePlanner.AltitudeDecision impossible = TerrainRoutePlanner.transitAltitude(
                100.0D, 107.0D, 0.0D, profile);

        assertTrue(cruise.isSafe());
        assertEquals(132.0D, cruise.getY(), 0.0D);
        assertTrue(pitchCorrection.getY() > cruise.getY());
        assertTrue(capped.isSafe());
        assertEquals(108.0D, capped.getY(), 0.0D);
        assertFalse(impossible.isSafe());
        assertEquals("MINIMUM_AGL_EXCEEDS_WORLD_HEIGHT", impossible.getReason());
    }

    @Test
    public void terrainPlannerRejectsTerrainOutsideClimbEnvelope() {
        assertTrue(TerrainRoutePlanner.canReachClearance(100.0D, 110.0D,
                40.0D, 1.0D, 0.45D));
        assertFalse(TerrainRoutePlanner.canReachClearance(100.0D, 130.0D,
                40.0D, 1.0D, 0.45D));
        assertFalse(TerrainRoutePlanner.canReachClearance(100.0D, 110.0D,
                40.0D, 0.0D, Double.NaN));
    }

    private static AircraftState aircraftState(ControlOwner owner, long tick) {
        return AircraftState.observe(UUID.randomUUID(), 0, tick,
                0.0D, 32.0D, 0.0D, 0.0D, 32.0D, -1.0D,
                0.0F, 0.0F, 0.0F, 0.65D, "ENROUTE", UUID.randomUUID(),
                owner, true, 0, 0.0D, 100.0D, 100.0D, 100.0D,
                100L, "KNOWN");
    }

    private static UavVehicleProfile profile(UavExecutionProfile type) {
        boolean heli = type == UavExecutionProfile.HELICOPTER_ONE_WAY_FPV;
        return new UavVehicleProfile(type, heli ? 8 : 32, heli ? 4 : 8,
                heli ? 32 : 128, 4, false, "SELF_DESTRUCT",
                256, 256, 64, "", 0.0D, 64.0D,
                heli ? 0.0D : 96.0D, 20.0D, 10.0D);
    }
}
