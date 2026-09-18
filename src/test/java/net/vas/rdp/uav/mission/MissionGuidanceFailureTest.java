package net.vas.rdp.uav.mission;

import org.junit.Test;

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
}
