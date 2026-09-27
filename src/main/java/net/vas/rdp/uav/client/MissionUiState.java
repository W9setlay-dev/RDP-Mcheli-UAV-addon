package net.vas.rdp.uav.client;

import net.vas.rdp.uav.mission.MissionState;

/** Client presentation policy; it never authorizes a server action. */
public final class MissionUiState {
    private MissionUiState() { }

    public static boolean isTerminal(String state) {
        if (state == null) return false;
        try {
            MissionState value = MissionState.valueOf(state);
            return value == MissionState.COMPLETE
                    || value == MissionState.ABORTED
                    || value == MissionState.FAILED
                    || value == MissionState.KAMIKADZE_DETONATED
                    || value == MissionState.AIRCRAFT_LOST;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static boolean isActionable(String state) {
        return state != null && !isTerminal(state)
                && !"OUTCOME_UNKNOWN".equals(state);
    }

    public static boolean isAlert(String state) {
        if (state == null) return false;
        if (isTerminal(state)) return true;
        if ("OUTCOME_UNKNOWN".equals(state)) return true;
        try {
            MissionState value = MissionState.valueOf(state);
            return value == MissionState.LOST
                    || value == MissionState.WAITING_FOR_WORLD
                    || value == MissionState.WAITING_FOR_FUEL
                    || value == MissionState.EXECUTION_STALLED
                    || value == MissionState.REPLAN
                    || value == MissionState.RECOVERING;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static String stateTone(String state) {
        if (state == null || state.trim().isEmpty()) return "UNKNOWN";
        if (isTerminal(state)) return "TERMINAL";
        if (isAlert(state)) return "ALERT";
        return isActionable(state) ? "ACTIVE" : "UNKNOWN";
    }

    public static boolean isMarkerVisible(String missionState, String markerState) {
        return isActionable(missionState) && markerState != null
                && !"HIDDEN".equalsIgnoreCase(markerState);
    }
}
