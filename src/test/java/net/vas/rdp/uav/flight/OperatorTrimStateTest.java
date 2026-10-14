package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.mission.MissionMovementAuthority;
import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
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
    public void supervisedTrimAddsBoundedCorrectionsToAiYawPitchAndRoll() {
        UUID missionId = UUID.randomUUID();
        OperatorTrimState state = new OperatorTrimState(missionId);
        UavVehicleProfile profile = profile(UavExecutionProfile.ONE_WAY);
        assertTrue(state.accept(1, 200, 6.0D, 4.0D, 2.0D, 200, profile));
        OperatorTrimState.Snapshot trim = state.snapshot(200);
        RDPMission mission = new RDPMission(missionId, UUID.randomUUID(), UUID.randomUUID(),
                "TEST_UAV", MissionType.RECONNAISSANCE,
                TargetSpec.point(0.0D, 32.0D, 0.0D, TargetPriority.NORMAL),
                false, false, false, 64.0D, 32.0D, 1L);
        mission.enterSupervisedOperatorMode(200L);
        NativeFlightControlCommand baseline = NativeFlightControlCommand.setpoints(
                10.0F, 5.0F, 3.0F, 0.65D, 90.0D, 2.0D, 0.1D);
        NativeFlightControlCommand mixed = MissionMovementAuthority.fuseAiAndOperator(
                mission, baseline, trim.getYawDegrees(), trim.getPitchDegrees(),
                trim.getRollDegrees(), profile.getMaximumYawCorrectionDegrees(),
                profile.getMaximumPitchCorrectionDegrees(),
                NativeControlMath.maximumRollDegrees(profile, false));

        assertEquals(16.0F, mixed.getYaw(), 0.0001F);
        assertEquals(1.0F, mixed.getPitch(), 0.0001F);
        assertEquals(5.0F, mixed.getRoll(), 0.0001F);
        assertEquals("operator corrections do not take throttle authority",
                baseline.getThrottle(), mixed.getThrottle(), 0.0D);
        assertEquals(baseline.getDesiredTrack(), mixed.getDesiredTrack(), 0.0D);
    }

    @Test
    public void rollCorrectionIsBoundedByTheHelicopterEnvelopeAndDecays() {
        OperatorTrimState state = new OperatorTrimState(UUID.randomUUID());
        UavVehicleProfile profile = profile(UavExecutionProfile.HELICOPTER_ONE_WAY_FPV);
        assertTrue(state.accept(1, 300, 0.0D, 0.0D, 6.0D, 300, profile));
        assertTrue(state.accept(2, 301, 0.0D, 0.0D, 6.0D, 301, profile));
        OperatorTrimState.Snapshot active = state.snapshot(301);
        assertEquals(11.76D, active.getRollDegrees(), 1.0E-9D);
        assertTrue(state.accept(3, 302, 0.0D, 0.0D, 6.0D, 302, profile));
        assertEquals(17.2896D, state.snapshot(302).getRollDegrees(), 1.0E-9D);
        assertTrue(state.snapshot(332).getRollDegrees() < 17.2896D);
    }

    @Test
    public void rollCorrectionRejectsOversizedSamplesAndClampsToProfileLimit() {
        OperatorTrimState state = new OperatorTrimState(UUID.randomUUID());
        UavVehicleProfile profile = profile(UavExecutionProfile.HELICOPTER_ONE_WAY_FPV);
        assertFalse(state.accept(1, 400, 0.0D, 0.0D, 6.1D, 400, profile));
        assertTrue(state.accept(1, 400, 0.0D, 0.0D, 6.0D, 400, profile));
        assertTrue(state.accept(2, 401, 0.0D, 0.0D, 6.0D, 401, profile));
        assertTrue(state.accept(3, 402, 0.0D, 0.0D, 6.0D, 402, profile));
        assertTrue(state.accept(4, 403, 0.0D, 0.0D, 6.0D, 403, profile));
        assertEquals(20.0D, state.snapshot(403).getRollDegrees(), 0.0D);
    }

    @Test
    public void operatorCorrectionsDoNotApplyOutsideSupervisedOwnership() {
        RDPMission mission = new RDPMission(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "TEST_UAV", MissionType.RECONNAISSANCE,
                TargetSpec.point(0.0D, 32.0D, 0.0D, TargetPriority.NORMAL),
                false, false, false, 64.0D, 32.0D, 1L);
        NativeFlightControlCommand baseline = NativeFlightControlCommand.setpoints(
                10.0F, 5.0F, 3.0F, 0.65D, 90.0D, 2.0D, 0.1D);

        NativeFlightControlCommand result = MissionMovementAuthority.fuseAiAndOperator(
                mission, baseline, 8.0D, 6.0D, 5.0D, 20.0D, 10.0D, 35.0D);

        assertSame(baseline, result);
    }

    @Test
    public void operatorCorrectionsDoNotReplaceTerminalGuidanceOwnership() {
        RDPMission mission = new RDPMission(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "TEST_UAV", MissionType.RECONNAISSANCE,
                TargetSpec.point(0.0D, 32.0D, 0.0D, TargetPriority.NORMAL),
                false, false, false, 64.0D, 32.0D, 1L);
        mission.transitionTo(MissionState.DISPATCHED, 1L);
        mission.transitionTo(MissionState.TAKEOFF, 2L);
        mission.transitionTo(MissionState.CRUISE, 3L);
        mission.transitionTo(MissionState.SEARCHING, 4L);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5L);
        mission.lockTarget(UUID.randomUUID(), 6L);
        mission.transitionTo(MissionState.KAMIKADZE_TARGET, 6L);
        NativeFlightControlCommand baseline = NativeFlightControlCommand.setpoints(
                10.0F, 5.0F, 3.0F, 0.65D, 90.0D, 2.0D, 0.1D);

        NativeFlightControlCommand result = MissionMovementAuthority.fuseAiAndOperator(
                mission, baseline, 2.0D, 2.0D, 2.0D, 20.0D, 10.0D, 35.0D);

        assertEquals(12.0F, result.getYaw(), 0.0001F);
        assertEquals(3.0F, result.getPitch(), 0.0001F);
        assertEquals(5.0F, result.getRoll(), 0.0001F);
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

    private static UavVehicleProfile profile(UavExecutionProfile type) {
        boolean heli = type == UavExecutionProfile.HELICOPTER_ONE_WAY_FPV;
        return new UavVehicleProfile(type, heli ? 8 : 32, heli ? 4 : 8,
                heli ? 32 : 128, 4, false, "SELF_DESTRUCT",
                256, 256, 64, "", 0.0D, 64.0D,
                heli ? 0.0D : 96.0D, 20.0D, 10.0D);
    }
}
