package net.vas.rdp.uav;

import net.vas.rdp.uav.mission.RecoveryAction;
import net.vas.rdp.uav.mission.RecoveryCondition;
import net.vas.rdp.uav.mission.RecoveryPolicy;
import net.vas.rdp.uav.profile.MissionDoctrine;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RecoveryPolicyTest {
    private final RecoveryPolicy policy = new RecoveryPolicy();

    @Test
    public void targetLossUsesDoctrineSpecificSearchBehavior() {
        assertEquals(RecoveryAction.REACQUIRE,
                policy.decide(MissionDoctrine.EXPENDABLE_ATTACK,
                        RecoveryCondition.TARGET_LOST));
        assertEquals(RecoveryAction.SEARCH,
                policy.decide(MissionDoctrine.REUSABLE_STRIKE,
                        RecoveryCondition.TARGET_LOST));
    }

    @Test
    public void unloadedChunkWaitsAndBlockedGuidanceReplans() {
        assertEquals(RecoveryAction.WAIT,
                policy.decide(MissionDoctrine.RECONNAISSANCE,
                        RecoveryCondition.CHUNK_UNLOADED));
        assertEquals(RecoveryAction.REPLAN,
                policy.decide(MissionDoctrine.RECONNAISSANCE,
                        RecoveryCondition.GUIDANCE_BLOCKED));
    }

    @Test
    public void reusableTimeoutReturnsWhileExpendableAborts() {
        assertEquals(RecoveryAction.RETURN,
                policy.decide(MissionDoctrine.REUSABLE_STRIKE,
                        RecoveryCondition.TIMEOUT));
        assertEquals(RecoveryAction.ABORT,
                policy.decide(MissionDoctrine.EXPENDABLE_ATTACK,
                        RecoveryCondition.TIMEOUT));
    }
}
