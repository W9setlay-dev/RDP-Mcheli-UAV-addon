package net.vas.rdp.uav.mission;

import net.minecraft.world.World;
import net.vas.rdp.uav.persistence.RDPMissionWorldData;

import java.util.UUID;

public final class MissionManager {
    private final AircraftAssignmentRegistry assignments = new AircraftAssignmentRegistry();
    private final AircraftRegistry aircraft = new AircraftRegistry();
    private final MissionQueue queue = new MissionQueue();

    public void registerAircraft(AircraftRecord record) { aircraft.register(record); }
    public AircraftRecord findAircraft(UUID aircraftId) { return aircraft.find(aircraftId); }

    public RDPMission create(World world, RDPMission mission) {
        if (!assignments.reserve(mission.getAircraftId(), mission.getMissionId())) {
            throw new IllegalStateException("AIRCRAFT_ALREADY_ASSIGNED");
        }

        try {
            RDPMissionWorldData.get(world).add(mission);
            queue.enqueue(mission);
            return mission;
        } catch (RuntimeException failure) {
            assignments.release(mission.getAircraftId(), mission.getMissionId());
            throw failure;
        }
    }

    public void release(RDPMission mission) {
        if (mission != null) {
            assignments.release(mission.getAircraftId(), mission.getMissionId());
        }
    }

    public UUID assignedMission(UUID aircraftId) {
        return assignments.missionFor(aircraftId);
    }

    public RDPMission pollQueuedMission() {
        return queue.poll();
    }

    public RDPMission peekQueuedMission() { return queue.peek(); }
    public boolean acceptQueuedMission(RDPMission mission) {
        return mission != null && queue.remove(mission);
    }
}
