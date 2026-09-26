package net.vas.rdp.uav.mission;

public enum MissionState {
    PLANNED,
    DISPATCHED,
    TAXI,
    TAKEOFF,
    CRUISE,
    SEARCHING,
    WAITING_FOR_TARGET,
    LOST,
    TARGET_ACQUIRED,
    TARGET_LOCKED,
    ATTACK_TARGET,
    KAMIKADZE_TARGET,
    KAMIKADZE_DETONATED,
    APPROACHING,
    ON_TARGET,
    ORBITING,
    SCANNING,
    DEEP_SCANNING,
    REPORTING,
    /** Native launch was accepted; outcome is not established yet. */
    LAUNCH_ACCEPTED,
    PROJECTILE_TRACKED,
    IMPACT_OBSERVED,
    EFFECT_CONFIRMED,
    OUTCOME_UNKNOWN,
    /** The aircraft was lost before the payload effect could be observed. */
    AIRCRAFT_LOST,
    /** Legacy name retained for persisted missions; no longer terminal. */
    DIRECT_LAUNCH_COMPLETE,
    HOLDING,
    WAITING_FOR_WORLD,
    WAITING_FOR_FUEL,
    EXECUTION_STALLED,
    REPLAN,
    RECOVERING,
    RETURNING,
    LANDING,
    PARKING,
    COMPLETE,
    ABORTED,
    FAILED;

    public boolean canTransitionTo(MissionState next) {
        if (next == null || this == COMPLETE || this == ABORTED || this == FAILED
                || this == KAMIKADZE_DETONATED || this == AIRCRAFT_LOST) {
            return false;
        }
        if (next == FAILED || next == ABORTED) {
            return true;
        }
        if (next == this) return false;
        if (next == WAITING_FOR_WORLD || next == WAITING_FOR_FUEL || next == EXECUTION_STALLED
                || next == REPLAN || next == RECOVERING) {
            return true;
        }
        switch (this) {
            case PLANNED: return next == DISPATCHED || next == WAITING_FOR_TARGET;
            case DISPATCHED: return next == TAXI || next == TAKEOFF;
            case TAXI: return next == TAKEOFF;
            case TAKEOFF: return next == CRUISE;
            case CRUISE: return next == SEARCHING;
            case SEARCHING: return next == TARGET_ACQUIRED || next == WAITING_FOR_TARGET
                    || next == LOST;
            case WAITING_FOR_TARGET: return next == SEARCHING || next == TARGET_ACQUIRED
                    || next == LOST;
            case LOST: return next == SEARCHING || next == WAITING_FOR_TARGET;
            case TARGET_ACQUIRED: return next == TARGET_LOCKED || next == LOST;
            case TARGET_LOCKED: return next == APPROACHING || next == ATTACK_TARGET
                    || next == KAMIKADZE_TARGET || next == LAUNCH_ACCEPTED
                    || next == DIRECT_LAUNCH_COMPLETE
                    || next == LOST;
            case LAUNCH_ACCEPTED: return next == PROJECTILE_TRACKED
                    || next == IMPACT_OBSERVED || next == OUTCOME_UNKNOWN;
            case PROJECTILE_TRACKED: return next == IMPACT_OBSERVED
                    || next == EFFECT_CONFIRMED || next == OUTCOME_UNKNOWN;
            case IMPACT_OBSERVED: return next == EFFECT_CONFIRMED
                    || next == OUTCOME_UNKNOWN || next == RETURNING;
            case EFFECT_CONFIRMED: return next == RETURNING || next == COMPLETE;
            case OUTCOME_UNKNOWN: return next == RETURNING || next == COMPLETE;
            case DIRECT_LAUNCH_COMPLETE: return next == LAUNCH_ACCEPTED
                    || next == PROJECTILE_TRACKED || next == OUTCOME_UNKNOWN;
            case KAMIKADZE_TARGET: return next == ON_TARGET || next == RETURNING
                    || next == KAMIKADZE_DETONATED || next == AIRCRAFT_LOST;
            case APPROACHING: return next == ON_TARGET || next == LOST;
            case ON_TARGET: return next == ORBITING || next == SCANNING || next == LOST;
            case ORBITING: return next == SCANNING || next == LOST;
            case SCANNING: return next == DEEP_SCANNING || next == REPORTING || next == LOST;
            case DEEP_SCANNING: return next == REPORTING;
            case REPORTING: return next == HOLDING || next == RETURNING;
            case HOLDING: return next == RETURNING || next == COMPLETE;
            case REPLAN:
                return next == SEARCHING || next == TARGET_LOCKED
                        || next == APPROACHING || next == ATTACK_TARGET || next == LOST;
            case ATTACK_TARGET:
                return next == APPROACHING || next == ON_TARGET || next == RETURNING
                        || next == KAMIKADZE_TARGET || next == LOST;
            case RETURNING: return next == LANDING;
            case LANDING: return next == PARKING;
            case PARKING: return next == COMPLETE;
            default: return false;
        }
    }
}
