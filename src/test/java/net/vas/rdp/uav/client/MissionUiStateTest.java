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

    @Test
    public void alertAndToneClassificationMatchServerMissionLifecycle() {
        assertTrue(MissionUiState.isAlert("WAITING_FOR_FUEL"));
        assertTrue(MissionUiState.isAlert("OUTCOME_UNKNOWN"));
        assertFalse(MissionUiState.isAlert("TARGET_LOCKED"));
        assertEquals("ALERT", MissionUiState.stateTone("WAITING_FOR_FUEL"));
        assertEquals("TERMINAL", MissionUiState.stateTone("FAILED"));
        assertEquals("ACTIVE", MissionUiState.stateTone("TARGET_LOCKED"));
    }

    @Test
    public void observationStatusDistinguishesFreshStalePointAndMissingContacts() {
        assertEquals("FRESH", MissionUiState.observationStatus(
                "minecraft:zombie", "target-uuid", true, "ATTACK"));
        assertEquals("STALE", MissionUiState.observationStatus(
                "minecraft:zombie", "target-uuid", false, "LOST"));
        assertEquals("STALE", MissionUiState.observationStatus(
                "minecraft:zombie", "target-uuid", true, "EXPIRED"));
        assertEquals("POINT", MissionUiState.observationStatus(
                "rdpuav:point", "", false, "NONE"));
        assertEquals("NONE", MissionUiState.observationStatus(
                "", "", false, "NONE"));
    }
}
