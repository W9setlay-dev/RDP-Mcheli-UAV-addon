package net.vas.rdp.uav.swarm;

/** Bounded recovery policy; a lost contact is never replaced by another UUID. */
public final class SwarmTargetRecoveryPolicy {
    public Decision decide(SwarmTargetRegistry target, int dimension, long tick,
                           int timeoutTicks) {
        if (target == null || tick < 0L || timeoutTicks < 1) {
            throw new IllegalArgumentException("Target, tick, and timeout are required");
        }
        if (target.getTargetId() == null || target.getState() == SwarmTargetState.CLEAR) {
            return Decision.RESUME_PATROL;
        }
        if (target.getDimension() != Integer.MIN_VALUE
                && target.getDimension() != dimension) {
            return Decision.HOLD;
        }
        if (target.getState() != SwarmTargetState.LOST) return Decision.TRACKING;
        long age = tick - target.getLastSeenTick();
        if (age < 0L) return Decision.HOLD;
        return age <= timeoutTicks ? Decision.SEARCH_SAME_UUID : Decision.RESUME_PATROL;
    }

    public enum Decision {
        TRACKING,
        SEARCH_SAME_UUID,
        RESUME_PATROL,
        HOLD
    }
}
