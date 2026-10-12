package net.vas.rdp.uav.mission;

/** Durable coarse execution phase, separate from fine-grained mission state. */
public enum MissionExecutionStage {
    PLANNING, TAKE_OFF, TRANSIT, SEARCH, TARGET_TRACK, TERMINAL,
    RECON_REPORT, STATION_HOLD, WEAPON_OUTCOME, RECOVERY,
    COMPLETE, ABORTED, FAILED;

    public static MissionExecutionStage fromState(MissionState state, MissionType type) {
        if (state == null) return PLANNING;
        switch (state) {
            case PLANNED: case DISPATCHED: case TAXI: return PLANNING;
            case TAKEOFF: return TAKE_OFF;
            case CRUISE: return TRANSIT;
            case SEARCHING: case WAITING_FOR_TARGET: case LOST: return SEARCH;
            case TARGET_ACQUIRED: case TARGET_LOCKED: return TARGET_TRACK;
            case KAMIKADZE_TARGET: return TERMINAL;
            case APPROACHING: case ATTACK_TARGET: case ON_TARGET:
                return TARGET_TRACK;
            case ORBITING: case SCANNING: case DEEP_SCANNING:
            case REPORTING: return type == MissionType.RECONNAISSANCE
                    || type == MissionType.OBSERVATION ? RECON_REPORT : TARGET_TRACK;
            case HOLDING: return STATION_HOLD;
            case LAUNCH_ACCEPTED: case PROJECTILE_TRACKED: case IMPACT_OBSERVED:
            case EFFECT_CONFIRMED: case OUTCOME_UNKNOWN: case DIRECT_LAUNCH_COMPLETE:
                return WEAPON_OUTCOME;
            case RETURNING: case LANDING: case PARKING: case RECOVERING:
                return RECOVERY;
            case COMPLETE: case KAMIKADZE_DETONATED: return COMPLETE;
            case ABORTED: return ABORTED;
            case FAILED: case AIRCRAFT_LOST: return FAILED;
            case WAITING_FOR_WORLD: case WAITING_FOR_FUEL: case EXECUTION_STALLED:
            case REPLAN: return TRANSIT;
            default: return PLANNING;
        }
    }
}
