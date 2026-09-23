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

    public static boolean isMarkerVisible(String missionState, String markerState) {
        return isActionable(missionState) && markerState != null
                && !"HIDDEN".equalsIgnoreCase(markerState);
    }
}
