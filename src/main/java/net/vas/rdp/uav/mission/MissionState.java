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
    HOLDING,
    WAITING_FOR_WORLD,
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
                || this == KAMIKADZE_DETONATED) {
            return false;
        }
        if (next == FAILED || next == ABORTED) {
            return true;
        }
        if (next == this) return false;
        if (next == WAITING_FOR_WORLD || next == EXECUTION_STALLED
                || next == REPLAN || next == RECOVERING) {
            return true;
        }
        switch (this) {
            case PLANNED: return next == DISPATCHED || next == WAITING_FOR_TARGET;
            case DISPATCHED: return next == TAXI || next == TAKEOFF;
            case TAXI: return next == TAKEOFF;
            case TAKEOFF: return next == CRUISE;
            case CRUISE: return next == SEARCHING;
            case SEARCHING: return next == TARGET_ACQUIRED || next == WAITING_FOR_TARGET;
            case WAITING_FOR_TARGET: return next == SEARCHING || next == TARGET_ACQUIRED;
            case LOST: return next == SEARCHING || next == WAITING_FOR_TARGET;
            case TARGET_ACQUIRED: return next == TARGET_LOCKED || next == LOST;
            case TARGET_LOCKED: return next == APPROACHING || next == ATTACK_TARGET
                    || next == KAMIKADZE_TARGET || next == LOST;
            case ATTACK_TARGET: return next == APPROACHING || next == ON_TARGET
                    || next == RETURNING || next == KAMIKADZE_TARGET;
            case KAMIKADZE_TARGET: return next == ON_TARGET || next == RETURNING
                    || next == KAMIKADZE_DETONATED;
            case APPROACHING: return next == ON_TARGET || next == LOST;
            case ON_TARGET: return next == ORBITING || next == SCANNING;
            case ORBITING: return next == SCANNING;
            case SCANNING: return next == DEEP_SCANNING || next == REPORTING;
            case DEEP_SCANNING: return next == REPORTING;
            case REPORTING: return next == HOLDING || next == RETURNING;
            case HOLDING: return next == RETURNING || next == COMPLETE;
            case WAITING_FOR_WORLD:
            case EXECUTION_STALLED:
            case RECOVERING:
                return next == REPLAN || next == WAITING_FOR_WORLD
                        || next == EXECUTION_STALLED || next == RECOVERING;
            case REPLAN:
                return next == SEARCHING || next == TARGET_LOCKED
                        || next == APPROACHING || next == ATTACK_TARGET;
            case RETURNING: return next == LANDING;
            case LANDING: return next == PARKING;
            case PARKING: return next == COMPLETE;
            default: return false;
        }
    }
}
