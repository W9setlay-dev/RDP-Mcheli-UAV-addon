package net.vas.rdp.uav.mission;

public final class MissionSafety {
    private MissionSafety() {
    }

    public enum RouteDecision {
        DIRECT_INTERCEPT,
        OFFSET_ORBIT,
        SAFE_SIDE_APPROACH,
        HOLD_AND_REEVALUATE
    }

    public static final class CorridorAssessment {
        private final RouteDecision routeDecision;
        private final boolean safe;
        private final boolean terrainClear;
        private final boolean lineOfSightClear;
        private final boolean altitudeSafe;
        private final boolean threatExposureLow;
        private final boolean fuelReady;
        private final boolean returnViable;
        private final String reason;

        private CorridorAssessment(RouteDecision routeDecision, boolean safe, boolean terrainClear,
                                  boolean lineOfSightClear, boolean altitudeSafe,
                                  boolean threatExposureLow, boolean fuelReady,
                                  boolean returnViable, String reason) {
            this.routeDecision = routeDecision;
            this.safe = safe;
            this.terrainClear = terrainClear;
            this.lineOfSightClear = lineOfSightClear;
            this.altitudeSafe = altitudeSafe;
            this.threatExposureLow = threatExposureLow;
            this.fuelReady = fuelReady;
            this.returnViable = returnViable;
            this.reason = reason;
        }

        public RouteDecision getRouteDecision() { return routeDecision; }
        public boolean isSafe() { return safe; }
        public boolean isTerrainClear() { return terrainClear; }
        public boolean isLineOfSightClear() { return lineOfSightClear; }
        public boolean isAltitudeSafe() { return altitudeSafe; }
        public boolean isThreatExposureLow() { return threatExposureLow; }
        public boolean isFuelReady() { return fuelReady; }
        public boolean isReturnViable() { return returnViable; }
        public String getReason() { return reason; }
    }

    public static boolean requiresTarget(MissionState state) {
        return state == MissionState.TARGET_LOCKED || state == MissionState.ATTACK_TARGET
                || state == MissionState.APPROACHING || state == MissionState.ON_TARGET
                || state == MissionState.ORBITING || state == MissionState.SCANNING;
    }

    public static boolean canAdvanceToDangerousAction(MissionState state) {
        return state == MissionState.TARGET_LOCKED || state == MissionState.ATTACK_TARGET
                || state == MissionState.KAMIKADZE_TARGET;
    }

    public static CorridorAssessment evaluateCorridor(double distance, double altitudeDelta,
                                                    boolean corridorBlocked, boolean lineOfSightBlocked,
                                                    boolean hostileExposure, boolean fuelReady,
                                                    boolean returnViable, double observationDistance) {
        if (Double.isNaN(distance) || Double.isInfinite(distance) || distance <= 0.0D) {
            return new CorridorAssessment(RouteDecision.HOLD_AND_REEVALUATE, false, false, false,
                    false, false, false, false, "invalid range");
        }

        double safeDistance = Math.max(2.0D, observationDistance * 1.5D);
        double safeAltitude = Math.max(18.0D, observationDistance * 0.75D);
        boolean terrainClear = !corridorBlocked;
        boolean lineOfSightClear = !lineOfSightBlocked;
        boolean altitudeSafe = altitudeDelta <= safeAltitude;
        boolean threatExposureLow = !hostileExposure;
        boolean readyForApproach = terrainClear && lineOfSightClear && altitudeSafe && threatExposureLow
                && fuelReady && returnViable;

        if (distance <= safeDistance && readyForApproach) {
            return new CorridorAssessment(RouteDecision.DIRECT_INTERCEPT, true, true, true, true,
                    true, fuelReady, true, "direct intercept corridor clear");
        }
        if (corridorBlocked && distance > observationDistance) {
            return new CorridorAssessment(RouteDecision.OFFSET_ORBIT, false, false, lineOfSightClear,
                    altitudeSafe, threatExposureLow, fuelReady, returnViable,
                    "obstruction forces offset orbit");
        }
        if (altitudeDelta > safeAltitude) {
            return new CorridorAssessment(RouteDecision.SAFE_SIDE_APPROACH, false, terrainClear,
                    lineOfSightClear, false, threatExposureLow, fuelReady, returnViable,
                    "altitude mismatch requires side approach");
        }
        return new CorridorAssessment(RouteDecision.HOLD_AND_REEVALUATE, false, terrainClear,
                lineOfSightClear, altitudeSafe, threatExposureLow, fuelReady, returnViable,
                "route not currently safe for commitment");
    }

    public static RouteDecision decideRoute(double distance, double altitudeDelta,
                                           boolean corridorBlocked, double observationDistance) {
        CorridorAssessment assessment = evaluateCorridor(distance, altitudeDelta, corridorBlocked,
                false, false, true, true, observationDistance);
        return assessment.getRouteDecision();
    }
}
