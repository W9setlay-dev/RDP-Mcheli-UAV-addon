package net.vas.rdp.uav.mission;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AircraftAssignmentRegistry {
    private final Map<UUID, UUID> aircraftToMission = new HashMap<UUID, UUID>();

    public synchronized boolean reserve(UUID aircraftId, UUID missionId) {
        if (aircraftId == null || missionId == null || aircraftToMission.containsKey(aircraftId)) {
            return false;
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
}
