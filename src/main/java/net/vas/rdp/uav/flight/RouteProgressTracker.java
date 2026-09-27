package net.vas.rdp.uav.flight;

/**
 * Deterministic route-progress tracking for server-authoritative mission telemetry.
 *
 * <p>The purpose is to distinguish real route gain from static or oscillating
 * native state. A mission is considered stalled only after a bounded period of
 * no meaningful progress toward the active waypoint/target envelope.</p>
 */
public final class RouteProgressTracker {
    private RouteProgressTracker() { }

    public static final class Snapshot {
        private final double routeProgress;
        private final long lastMeaningfulProgressTick;
        private final boolean meaningfulProgress;
        private final boolean measuredDistanceProgress;
        private final boolean stalled;
        private final String reason;

        private Snapshot(double routeProgress, long lastMeaningfulProgressTick,
                         boolean meaningfulProgress, boolean measuredDistanceProgress,
                         boolean stalled, String reason) {
            this.routeProgress = routeProgress;
            this.lastMeaningfulProgressTick = lastMeaningfulProgressTick;
            this.meaningfulProgress = meaningfulProgress;
            this.measuredDistanceProgress = measuredDistanceProgress;
            this.stalled = stalled;
            this.reason = reason;
        }

        public double getRouteProgress() { return routeProgress; }
        public long getLastMeaningfulProgressTick() { return lastMeaningfulProgressTick; }
        public boolean isMeaningfulProgress() { return meaningfulProgress; }
        public boolean isMeasuredDistanceProgress() { return measuredDistanceProgress; }
        public boolean isStalled() { return stalled; }
        public String getReason() { return reason; }
    }

    public static Snapshot evaluate(long tick, double previousRouteProgress,
                                   double currentRouteProgress,
                                   double previousDistanceToTarget,
                                   double currentDistanceToTarget,
                                   double previousDistanceToWaypoint,
                                   double currentDistanceToWaypoint,
                                   long previousMeaningfulProgressTick,
                                   double progressThreshold,
                                   long stallWindowTicks) {
        if (tick < 0L || progressThreshold < 0.0D || stallWindowTicks < 1L) {
            throw new IllegalArgumentException("Invalid progress tracking inputs");
        }
        double previousProgress = Double.isFinite(previousRouteProgress)
                ? previousRouteProgress : 0.0D;
        double currentProgress = Double.isFinite(currentRouteProgress)
                ? currentRouteProgress : previousProgress;

        double targetDelta = measurableDelta(previousDistanceToTarget, currentDistanceToTarget);
        double waypointDelta = measurableDelta(previousDistanceToWaypoint, currentDistanceToWaypoint);
        double routeDelta = currentProgress - previousProgress;
        boolean measuredDistanceProgress = targetDelta > progressThreshold
                || waypointDelta > progressThreshold;
        boolean meaningfulProgress = routeDelta > 0.0D
                || measuredDistanceProgress;
        long nextProgressTick = meaningfulProgress ? tick : previousMeaningfulProgressTick;
        boolean stalled = tick - nextProgressTick >= stallWindowTicks;

        double retainedRouteProgress = Math.max(previousProgress, currentProgress);
        String reason = meaningfulProgress
                ? "ROUTE_PROGRESS"
                : (stalled ? "NO_PROGRESS_TIMEOUT" : "ROUTE_STABLE");
        return new Snapshot(retainedRouteProgress, nextProgressTick,
                meaningfulProgress, measuredDistanceProgress, stalled, reason);
    }

    private static double measurableDelta(double previousDistance, double currentDistance) {
        if (!Double.isFinite(previousDistance) || !Double.isFinite(currentDistance)) {
            return 0.0D;
        }
        return previousDistance - currentDistance;
    }
}
