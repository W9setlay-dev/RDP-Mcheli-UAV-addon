package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MissionGuidanceFailureTest {
    @Test
    public void permanentGuidanceReasonsCannotEnterReplanLoop() {
        assertTrue(MissionLifecycle.isPermanentGuidanceFailure(
                "UNSUPPORTED_OBJECTIVE_COMBINATION"));
        assertTrue(MissionLifecycle.isPermanentGuidanceFailure(
                "NATIVE_SEEKER_LAUNCH_REJECTED"));
        assertTrue(MissionLifecycle.isPermanentGuidanceFailure("NO_GUIDED_WEAPON"));
        assertTrue(MissionLifecycle.isPermanentGuidanceFailure("NO_AMMUNITION"));
        assertTrue(MissionLifecycle.isPermanentGuidanceFailure(
                "REUSABLE_UAV_GUIDED_WEAPON_REQUIRED"));
        assertTrue(MissionLifecycle.isPermanentGuidanceFailure(
                "ONE_WAY_SQUARE_TERMINAL_PAYLOAD_REQUIRED"));
    }

    @Test
    public void transientGuidanceReasonsRemainRecoverable() {
        assertFalse(MissionLifecycle.isPermanentGuidanceFailure("GUIDED_HOLD_TERRAIN"));
        assertFalse(MissionLifecycle.isPermanentGuidanceFailure("GUIDED_HOLD_LINE_OF_SIGHT"));
        assertFalse(MissionLifecycle.isPermanentGuidanceFailure("GUIDED_HOLD_CHUNK_UNLOADED"));
        assertFalse(MissionLifecycle.isPermanentGuidanceFailure("TARGET_NOT_AVAILABLE"));
        assertFalse(MissionLifecycle.isPermanentGuidanceFailure("REPLAN_DISPATCH_REJECTED"));
    }

    @Test
    public void everyKnownGuidanceFailureHasAnExplicitLifecycleClass() {
        String[] permanent = {
                "UNSUPPORTED_GUIDANCE",
                "AI_TV_GUIDANCE_UNSUPPORTED",
                "UNSUPPORTED_SEEKER_TARGET_ASSIGNMENT",
                "NATIVE_WINGMAN_REJECTED",
                "REUSABLE_UAV_GUIDED_WEAPON_REQUIRED",
                "ONE_WAY_SQUARE_TERMINAL_PAYLOAD_REQUIRED"
        };
        for (String reason : permanent) {
            assertTrue("Expected permanent reason: " + reason,
                    MissionLifecycle.isPermanentGuidanceFailure(reason));
        }

        String[] transientReasons = {
                "GUIDED_HOLD_TERRAIN",
                "GUIDED_HOLD_CHUNK_UNLOADED",
                "GUIDED_HOLD_LINE_OF_SIGHT",
                "GUIDED_REJECT_DIMENSION",
                "TARGET_NOT_AVAILABLE",
                "REPLAN_DISPATCH_REJECTED"
        };
        for (String reason : transientReasons) {
            assertFalse("Expected recoverable reason: " + reason,
                    MissionLifecycle.isPermanentGuidanceFailure(reason));
        }
    }

    @Test
    public void permanentCompatibilityFailureIsTerminalFromPlannedOrReplan() {
        RDPMission planned = mission();
        planned.fail("UNSUPPORTED_OBJECTIVE_COMBINATION", 1);
        assertEquals(MissionState.FAILED, planned.getState());
        assertTrue(planned.isTerminal());

        RDPMission replanning = mission();
        replanning.transitionTo(MissionState.WAITING_FOR_WORLD, 1);
        replanning.transitionTo(MissionState.REPLAN, 2);
        replanning.fail("UNSUPPORTED_OBJECTIVE_COMBINATION", 3);
        assertEquals(MissionState.FAILED, replanning.getState());
        assertTrue(replanning.isTerminal());
        assertFalse(replanning.getState() == MissionState.REPLAN);
    }

    @Test
    public void failedMissionCannotBeResurrected() {
        RDPMission mission = mission();
        mission.fail("UNSUPPORTED_OBJECTIVE_COMBINATION", 1);
        try {
            mission.transitionTo(MissionState.REPLAN, 2);
            org.junit.Assert.fail("FAILED mission must reject transitions");
        } catch (IllegalStateException expected) {
            assertEquals(MissionState.FAILED, mission.getState());
        }
        mission.fail("UNSUPPORTED_OBJECTIVE_COMBINATION", 3);
        assertEquals(MissionState.FAILED, mission.getState());
    }

    private static RDPMission mission() {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "switchblade", MissionType.STRIKE,
                TargetSpec.point(0, 80, 0, TargetPriority.HIGH),
                true, false, false, 64, 32, 0);
    }
}
