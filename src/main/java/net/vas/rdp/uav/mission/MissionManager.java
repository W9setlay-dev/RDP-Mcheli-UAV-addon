package net.vas.rdp.uav.mission;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.vas.rdp.uav.persistence.RDPMissionWorldData;

import java.util.UUID;

public final class MissionManager {
    private final AircraftAssignmentRegistry assignments = new AircraftAssignmentRegistry();
    private final AircraftRegistry aircraft = new AircraftRegistry();
    private final MissionQueue queue = new MissionQueue();
    private final MissionCoordinator coordinator = new MissionCoordinator();

    public void registerAircraft(AircraftRecord record) { aircraft.register(record); }
    public AircraftRecord findAircraft(UUID aircraftId) { return aircraft.find(aircraftId); }
    public boolean canAssignAircraft(UUID aircraftId, UUID ownerId) {
        return aircraft.canAssign(aircraftId, ownerId);
    }

    /**
     * McHeli's UAV station is the ownership boundary. Re-registering the
     * currently operated craft makes authorization survive server reloads
     * without allowing a player who is not operating it to claim the craft.
     */
    public boolean ensureAuthorized(MCH_EntityAircraft aircraft, EntityPlayer player) {
        if (aircraft == null || player == null) return false;
        AircraftRecord existing = this.aircraft.find(aircraft.getUniqueID());
        if (existing == null) {
            registerAircraft(new AircraftRecord(aircraft.getUniqueID(), player.getUniqueID(),
                    aircraft.dimension, aircraft.getAcInfo().name, true, true));
            return true;
        }
        return existing.getOwnerId().equals(player.getUniqueID())
                && existing.getDimension() == aircraft.dimension;
    }

    public RDPMission create(World world, RDPMission mission) {
        if (!assignments.reserve(mission.getAircraftId(), mission.getMissionId())) {
            throw new IllegalStateException("AIRCRAFT_ALREADY_ASSIGNED");
        }

        try {
            aircraft.assign(mission.getAircraftId(), mission.getMissionId(), mission.getOwnerId());
            if (mission.getTargetId() != null && !coordinator.claim(mission.getAircraftId(), mission.getTargetId())) {
                throw new IllegalStateException("TARGET_ALREADY_ASSIGNED");
            }
            RDPMissionWorldData.get(world).add(mission);
            queue.enqueue(mission);
            return mission;
        } catch (RuntimeException failure) {
            assignments.release(mission.getAircraftId(), mission.getMissionId());
            aircraft.release(mission.getAircraftId());
            coordinator.release(mission.getAircraftId(), mission.getTargetId());
            throw failure;
        }
    }

    public void release(RDPMission mission) {
        if (mission != null) {
            assignments.release(mission.getAircraftId(), mission.getMissionId());
            aircraft.release(mission.getAircraftId(), mission.getMissionId());
            coordinator.release(mission.getAircraftId(), mission.getTargetId());
        }
    }

    /**
     * Rebuilds in-memory assignment state after a world reload. Persisted
     * missions remain authoritative; terminal records are intentionally not
     * re-reserved.
     */
    public void restoreAssignments(World world) {
        if (world == null) return;
        for (RDPMission mission : RDPMissionWorldData.get(world).getRepository().snapshot()) {
            if (mission.getState() == MissionState.COMPLETE
                    || mission.getState() == MissionState.ABORTED
                    || mission.getState() == MissionState.FAILED) {
                continue;
            }
            assignments.reserve(mission.getAircraftId(), mission.getMissionId());
            AircraftRecord record = aircraft.find(mission.getAircraftId());
            if (record == null) {
                aircraft.register(new AircraftRecord(mission.getAircraftId(), mission.getOwnerId(),
                        mission.getDimension(), mission.getAircraftType(), false, true));
            }
        }
    }

    public UUID assignedMission(UUID aircraftId) {
        return assignments.missionFor(aircraftId);
    }

    public RDPMission pollQueuedMission() {
        return queue.poll();
    }

    public void enqueueQueuedMission(RDPMission mission) {
        if (mission != null && mission.isQueued() && !queue.contains(mission)) {
            queue.enqueue(mission);
        }
    }

    public int queuedMissionCount() {
        return queue.size();
    }

    public void restoreQueued(World world) {
        if (world != null) {
            RDPMissionWorldData.get(world).restoreQueued(queue);
        }
    }

    public RDPMission peekQueuedMission() { return queue.peek(); }
    public boolean acceptQueuedMission(RDPMission mission) {
        return mission != null && queue.remove(mission);
    }

    public void removeQueuedMission(RDPMission mission) {
        if (mission != null) queue.remove(mission);
    }
}
