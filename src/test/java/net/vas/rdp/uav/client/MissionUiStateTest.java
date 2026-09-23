package net.vas.rdp.uav.client;

import org.junit.Test;

import static org.junit.Assert.*;

public class MissionUiStateTest {
    @Test
    public void terminalAndUnknownStatesAreNotActionable() {
        assertFalse(MissionUiState.isActionable("COMPLETE"));
        assertFalse(MissionUiState.isActionable("ABORTED"));
        assertFalse(MissionUiState.isActionable("FAILED"));
        assertFalse(MissionUiState.isActionable("OUTCOME_UNKNOWN"));
        assertTrue(MissionUiState.isActionable("PROJECTILE_TRACKED"));
    }

    @Test
    public void terminalStatesHideMarkersButActiveLocksRemainVisible() {
        assertFalse(MissionUiState.isMarkerVisible("COMPLETE", "CONTACT"));
        assertFalse(MissionUiState.isMarkerVisible("AIRCRAFT_LOST", "CONTACT"));
        assertFalse(MissionUiState.isMarkerVisible("OUTCOME_UNKNOWN", "CONTACT"));
        assertTrue(MissionUiState.isMarkerVisible("TARGET_LOCKED", "CONTACT"));
        assertFalse(MissionUiState.isMarkerVisible("TARGET_LOCKED", "HIDDEN"));
    }
}
