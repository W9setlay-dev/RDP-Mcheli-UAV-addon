package net.vas.rdp.uav.mission;

public enum MissionState {
    PLANNED,
    DISPATCHED,
    TAXI,
    TAKEOFF,
    CRUISE,
    SEARCHING,
    TARGET_ACQUIRED,
    TARGET_LOCKED,
    ATTACK_TARGET,
    KAMIKADZE_TARGET,
    APPROACHING,
    ON_TARGET,
    ORBITING,
    SCANNING,
    DEEP_SCANNING,
    REPORTING,
    RETURNING,
    LANDING,
    PARKING,
    COMPLETE,
    ABORTED,
    FAILED;

    public boolean canTransitionTo(MissionState next) {
        if (next == null || this == COMPLETE || this == ABORTED || this == FAILED) {
            return false;
        }
        if (next == FAILED || next == ABORTED) {
            return true;
        }
        switch (this) {
            case PLANNED: return next == DISPATCHED;
            case DISPATCHED: return next == TAXI || next == TAKEOFF;
            case TAXI: return next == TAKEOFF;
            case TAKEOFF: return next == CRUISE;
            case CRUISE: return next == SEARCHING;
            case SEARCHING: return next == TARGET_ACQUIRED;
            case TARGET_ACQUIRED: return next == TARGET_LOCKED;
            case TARGET_LOCKED: return next == APPROACHING;
            case APPROACHING: return next == ON_TARGET;
            case ON_TARGET: return next == ORBITING || next == SCANNING;
            case ORBITING: return next == SCANNING;
            case SCANNING: return next == DEEP_SCANNING || next == REPORTING;
            case DEEP_SCANNING: return next == REPORTING;
            case REPORTING: return next == RETURNING;
            case RETURNING: return next == LANDING;
            case LANDING: return next == PARKING;
            case PARKING: return next == COMPLETE;
            default: return false;
        }
    }
}
