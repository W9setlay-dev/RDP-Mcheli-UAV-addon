package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.profile.MissionDoctrine;

/**
 * Pure recovery decision seam. It does not perform recovery or mutate a
 * mission; the existing lifecycle remains the behavior owner in this phase.
 */
public final class RecoveryPolicy {
    public RecoveryAction decide(MissionDoctrine doctrine, RecoveryCondition condition) {
        if (doctrine == null || condition == null) {
            throw new IllegalArgumentException("Doctrine and recovery condition are required");
        }
        switch (condition) {
            case TARGET_LOST:
                return doctrine == MissionDoctrine.EXPENDABLE_ATTACK
                        ? RecoveryAction.REACQUIRE : RecoveryAction.SEARCH;
            case CHUNK_UNLOADED:
                return RecoveryAction.WAIT;
            case GUIDANCE_BLOCKED:
                return RecoveryAction.REPLAN;
            case LOW_FUEL:
            case TIMEOUT:
                return doctrine == MissionDoctrine.REUSABLE_STRIKE
                        ? RecoveryAction.RETURN : RecoveryAction.ABORT;
            case OBJECTIVE_FAILED:
            default:
                return RecoveryAction.ABORT;
        }
    }
}
