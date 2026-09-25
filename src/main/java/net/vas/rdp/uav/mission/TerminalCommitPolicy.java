package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.flight.RouteDetourPlanner;

/**
 * Server-side gate for whether a mission may commit to a terminal approach or
 * must hold for a safer detour/recovery route.
 *
 * <p>This policy never writes aircraft motion. It only evaluates whether the
 * current mission geometry and safety window are sufficient to release a
 * terminal action and, if not, whether a bounded detour is still viable.</p>
 */
public final class TerminalCommitPolicy {
    private TerminalCommitPolicy() { }

    public enum DecisionState {
        RELEASE,
        DETOUR_REQUIRED,
        HOLD,
        ABORT
    }

    public static final class Decision {
        private final DecisionState state;
        private final MissionSafety.CorridorAssessment corridor;
        private final RouteDetourPlanner.Plan detour;
        private final String reason;

        private Decision(DecisionState state, MissionSafety.CorridorAssessment corridor,
                         RouteDetourPlanner.Plan detour, String reason) {
            this.state = state;
            this.corridor = corridor;
            this.detour = detour;
            this.reason = reason;
        }

        public DecisionState getState() { return state; }
        public MissionSafety.CorridorAssessment getCorridor() { return corridor; }
        public RouteDetourPlanner.Plan getDetour() { return detour; }
        public String getReason() { return reason; }
    }

    public static Decision evaluate(double distance, double altitudeDelta,
                                   boolean corridorBlocked, boolean lineOfSightBlocked,
                                   boolean hostileExposure, boolean fuelReady,
                                   boolean returnViable, double observationDistance,
                                   double preferredDetourOffset) {
        if (!Double.isFinite(distance) || !Double.isFinite(altitudeDelta)
                || !Double.isFinite(observationDistance)
                || !Double.isFinite(preferredDetourOffset)) {
            throw new IllegalArgumentException("Invalid terminal commit inputs");
        }

        MissionSafety.CorridorAssessment corridor = MissionSafety.evaluateCorridor(
                distance, altitudeDelta, corridorBlocked, lineOfSightBlocked,
                hostileExposure, fuelReady, returnViable, observationDistance);

        RouteDetourPlanner.Plan detour = RouteDetourPlanner.plan(
                0.0D, 0.0D, 0.0D,
                Math.max(0.0D, distance), Math.max(0.0D, altitudeDelta), 0.0D,
                corridorBlocked, preferredDetourOffset);

        boolean safeForRelease = corridor.isSafe() && corridor.isTerrainClear()
                && corridor.isLineOfSightClear() && corridor.isAltitudeSafe()
                && corridor.isThreatExposureLow() && corridor.isFuelReady()
                && corridor.isReturnViable();

        if (safeForRelease) {
            return new Decision(DecisionState.RELEASE, corridor, detour, "terminal commit authorized");
        }
        if (corridorBlocked || corridor.getRouteDecision() == MissionSafety.RouteDecision.OFFSET_ORBIT
                || corridor.getRouteDecision() == MissionSafety.RouteDecision.SAFE_SIDE_APPROACH) {
            return new Decision(DecisionState.DETOUR_REQUIRED, corridor, detour,
                    detour.isDetourRequired() ? detour.getReason() : corridor.getReason());
        }
        if (corridor.getRouteDecision() == MissionSafety.RouteDecision.HOLD_AND_REEVALUATE) {
            return new Decision(DecisionState.HOLD, corridor, detour, corridor.getReason());
        }
        return new Decision(DecisionState.ABORT, corridor, detour, "terminal route unsafe");
    }
}
