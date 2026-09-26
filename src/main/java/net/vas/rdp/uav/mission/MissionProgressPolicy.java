package net.vas.rdp.uav.mission;

/**
 * Deterministic policy for deciding whether a no-progress mission should hold,
 * replan a route. Contact freshness is evaluated independently by target tracking.
 */
public final class MissionProgressPolicy {
    private MissionProgressPolicy() { }

    public enum Action {
        CONTINUE,
        HOLD,
        REPLAN,
        REACQUIRE,
        FAIL
    }

    public static final class Decision {
        private final Action action;
        private final String reason;

        private Decision(Action action, String reason) {
            this.action = action == null ? Action.HOLD : action;
            this.reason = reason == null || reason.trim().isEmpty() ? "NO_PROGRESS" : reason.trim();
        }

        public Action getAction() { return action; }
        public String getReason() { return reason; }
    }

    public static Decision evaluate(MissionState state, boolean hasTarget,
                                   boolean meaningfulProgress, long tick,
                                   long lastMeaningfulProgressTick,
                                   long stallWindowTicks) {
        return evaluate(state, hasTarget, meaningfulProgress, true, tick,
                lastMeaningfulProgressTick, stallWindowTicks);
    }

    public static Decision evaluate(MissionState state, boolean hasTarget,
                                   boolean meaningfulProgress, boolean progressExpected,
                                   long tick, long lastMeaningfulProgressTick,
                                   long stallWindowTicks) {
        if (state == null || tick < 0L || stallWindowTicks < 1L) {
            throw new IllegalArgumentException("Invalid progress policy inputs");
        }
        if (!progressExpected) {
            return new Decision(Action.HOLD, "PROGRESS_NOT_EXPECTED");
        }
        if (meaningfulProgress) {
            return new Decision(Action.CONTINUE, "MEANINGFUL_PROGRESS");
        }
        long elapsed = tick - lastMeaningfulProgressTick;
        if (elapsed < stallWindowTicks) {
            return new Decision(Action.HOLD, "ROUTE_STABLE");
        }
        if (state == MissionState.SEARCHING
                || state == MissionState.WAITING_FOR_TARGET
                || state == MissionState.REPLAN
                || state == MissionState.RECOVERING
                || state == MissionState.EXECUTION_STALLED) {
            return new Decision(Action.REPLAN, "NO_PROGRESS_TIMEOUT");
        }
        if (state == MissionState.TARGET_ACQUIRED
                || state == MissionState.TARGET_LOCKED
                || state == MissionState.APPROACHING
                || state == MissionState.ATTACK_TARGET) {
            return new Decision(Action.REPLAN,
                    hasTarget ? "ROUTE_STALLED_TARGET_RETAINED" : "ROUTE_STALLED_NO_TARGET");
        }
        return new Decision(Action.HOLD, "NO_PROGRESS_TIMEOUT");
    }
}
