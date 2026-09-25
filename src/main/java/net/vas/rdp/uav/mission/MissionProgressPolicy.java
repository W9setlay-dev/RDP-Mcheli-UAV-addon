package net.vas.rdp.uav.mission;

/**
 * Deterministic policy for deciding whether a no-progress mission should hold,
 * replan, or declare the target lost.
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
        if (state == null || tick < 0L || stallWindowTicks < 1L) {
            throw new IllegalArgumentException("Invalid progress policy inputs");
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
                || state == MissionState.ATTACK_TARGET
                || state == MissionState.ON_TARGET
                || state == MissionState.ORBITING
                || state == MissionState.SCANNING
                || state == MissionState.DEEP_SCANNING
                || state == MissionState.REPORTING) {
            return new Decision(Action.REACQUIRE, hasTarget ? "TARGET_LOST" : "TARGET_LOSS_STALLED");
        }
        return new Decision(Action.HOLD, "NO_PROGRESS_TIMEOUT");
    }
}
