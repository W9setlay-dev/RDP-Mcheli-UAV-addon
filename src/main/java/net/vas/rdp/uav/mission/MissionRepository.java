package net.vas.rdp.uav.mission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MissionRepository {
    private final Map<UUID, RDPMission> missions = new LinkedHashMap<>();

    public synchronized void add(RDPMission mission) {
        if (mission == null || missions.containsKey(mission.getMissionId())) {
            throw new IllegalArgumentException("Mission is missing or already stored");
        }
        missions.put(mission.getMissionId(), mission);
    }

    public synchronized RDPMission find(UUID missionId) {
        return missions.get(missionId);
    }

    public synchronized List<RDPMission> byOwner(UUID ownerId) {
        List<RDPMission> result = new ArrayList<>();
        for (RDPMission mission : missions.values()) {
            if (mission.getOwnerId().equals(ownerId)) {
                result.add(mission);
            }
        }
        return result;
    }

    public synchronized RDPMission activeForAircraft(UUID aircraftId) {
        if (aircraftId == null) return null;
        RDPMission latest = null;
        for (RDPMission mission : missions.values()) {
            if (aircraftId.equals(mission.getAircraftId()) && !mission.isTerminal()
                    && (latest == null || mission.getLastUpdateTick() > latest.getLastUpdateTick())) {
                latest = mission;
            }
        }
        return latest;
    }

    public synchronized List<RDPMission> snapshot() {
        return new ArrayList<>(missions.values());
    }

    public synchronized void clear() {
        missions.clear();
    }

    public synchronized boolean remove(UUID missionId) {
        return missionId != null && missions.remove(missionId) != null;
    }
}
