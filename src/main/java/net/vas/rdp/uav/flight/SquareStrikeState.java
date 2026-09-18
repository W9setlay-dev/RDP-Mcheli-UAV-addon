package net.vas.rdp.uav.flight;

public enum SquareStrikeState {
    CREATED,
    VALIDATING,
    QUEUED,
    ASSIGNED,
    LAUNCHING,
    TRANSIT,
    AREA_ENTRY,
    SEARCHING,
    TARGET_DETECTED,
    TARGET_VALIDATING,
    TARGET_SELECTED,
    ENGAGEMENT_PREPARING,
    ENGAGEMENT_ACTIVE,
    POST_ENGAGEMENT_ASSESSMENT,
    RESUME_SEARCH,
    MISSION_COMPLETE,
    RTB,
    LANDING,
    COMPLETED,
    OPERATOR_OVERRIDE,
    PAUSED,
    ABORTED,
    EMERGENCY;

    public boolean canTransitionTo(SquareStrikeState next) {
        if (next == null || this == COMPLETED || this == ABORTED) return false;
        if (next == ABORTED || next == EMERGENCY || next == OPERATOR_OVERRIDE) return true;
        if (this == OPERATOR_OVERRIDE) return next == PAUSED || next == RESUME_SEARCH || next == RTB;
        if (this == PAUSED) return next == RESUME_SEARCH || next == RTB;
        switch (this) {
            case CREATED: return next == VALIDATING;
            case VALIDATING: return next == QUEUED;
            case QUEUED: return next == ASSIGNED;
            case ASSIGNED: return next == LAUNCHING;
            case LAUNCHING: return next == TRANSIT;
            case TRANSIT: return next == AREA_ENTRY;
            case AREA_ENTRY: return next == SEARCHING;
            case SEARCHING: return next == TARGET_DETECTED || next == MISSION_COMPLETE;
            case TARGET_DETECTED: return next == TARGET_VALIDATING;
            case TARGET_VALIDATING: return next == TARGET_SELECTED
                    || next == RESUME_SEARCH;
            case TARGET_SELECTED: return next == ENGAGEMENT_PREPARING
                    || next == RESUME_SEARCH;
            case ENGAGEMENT_PREPARING: return next == ENGAGEMENT_ACTIVE
                    || next == RESUME_SEARCH;
            case ENGAGEMENT_ACTIVE: return next == POST_ENGAGEMENT_ASSESSMENT;
            case POST_ENGAGEMENT_ASSESSMENT: return next == RESUME_SEARCH
                    || next == MISSION_COMPLETE;
            case RESUME_SEARCH: return next == SEARCHING;
            case MISSION_COMPLETE: return next == RTB;
            case RTB: return next == LANDING;
            case LANDING: return next == COMPLETED;
            case EMERGENCY: return next == RTB || next == LANDING;
            default: return false;
        }
    }
}
