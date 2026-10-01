package net.vas.rdp.uav.mission;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AircraftAssignmentRegistry {
    private final Map<UUID, UUID> aircraftToMission = new HashMap<>();

    public synchronized boolean reserve(UUID aircraftId, UUID missionId) {
        if (aircraftId == null || missionId == null) {
            return false;
        }
        UUID assignedMission = aircraftToMission.get(aircraftId);
        if (assignedMission != null) {
            return assignedMission.equals(missionId);
        }
        aircraftToMission.put(aircraftId, missionId);
        return true;
    }

    public synchronized void release(UUID aircraftId, UUID missionId) {
        if (aircraftId == null) {
            return;
        }
        UUID assignedMission = aircraftToMission.get(aircraftId);
        if (missionId != null && !missionId.equals(assignedMission)) {
            return;
        }
        aircraftToMission.remove(aircraftId);
    }

    public synchronized UUID missionFor(UUID aircraftId) {
        return aircraftToMission.get(aircraftId);
    }

    /** Atomic compare-and-transfer used for a linked reconnaissance retask. */
    public synchronized boolean transfer(UUID aircraftId, UUID expectedMission,
                                         UUID replacementMission) {
        if (aircraftId == null || expectedMission == null || replacementMission == null
                || expectedMission.equals(replacementMission)
                || !expectedMission.equals(aircraftToMission.get(aircraftId))) return false;
        aircraftToMission.put(aircraftId, replacementMission);
        return true;
    }
}
