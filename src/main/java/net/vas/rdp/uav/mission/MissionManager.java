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
    /**
     * Returns whether the aircraft is unavailable to the requested owner.
     * Callers use this as a rejection predicate before creating a mission.
     */
    public boolean isAircraftUnavailable(UUID aircraftId, UUID ownerId) {
        return assignments.missionFor(aircraftId) != null
                || !aircraft.canAssign(aircraftId, ownerId);
    }

    /**
     * Assignment entry point used by live mission creation. Reconciles a terminal or
     * missing persisted mission before checking the in-memory aircraft reservation.
     */
    public boolean isAircraftUnavailable(World world, UUID aircraftId, UUID ownerId) {
        if (world != null && aircraftId != null) reconcileStaleAssignment(world, aircraftId);
        return isAircraftUnavailable(aircraftId, ownerId);
    }

    public boolean ownsAssignment(RDPMission mission) {
        if (mission == null) return false;
        UUID missionId = mission.getMissionId();
        AircraftRecord record = aircraft.find(mission.getAircraftId());
        return missionId.equals(assignments.missionFor(mission.getAircraftId()))
                && record != null && missionId.equals(record.getMissionId());
    }

    private void reconcileStaleAssignment(World world, UUID aircraftId) {
        RDPMissionWorldData data = RDPMissionWorldData.get(world);
        UUID assignmentId = assignments.missionFor(aircraftId);
        AircraftRecord record = aircraft.find(aircraftId);
        UUID recordMissionId = record == null ? null : record.getMissionId();
        boolean changed = false;

        if (isStale(world, assignmentId)) {
            releaseStale(world, aircraftId, assignmentId);
            changed = true;
        }
        if (recordMissionId != null && !recordMissionId.equals(assignmentId)
                && isStale(world, recordMissionId)) {
            releaseStale(world, aircraftId, recordMissionId);
            changed = true;
        }

        UUID currentAssignment = assignments.missionFor(aircraftId);
        record = aircraft.find(aircraftId);
        UUID currentRecordMission = record == null ? null : record.getMissionId();
        if (changed && currentAssignment == null && currentRecordMission == null) {
            // No live mission owns this aircraft, so orphaned target claims can be cleared.
            coordinator.release(aircraftId);
        }
        if (changed) data.markDirty();
    }

    private boolean isStale(World world, UUID missionId) {
        if (missionId == null) return false;
        RDPMission mission = RDPMissionWorldData.get(world).getRepository().find(missionId);
        return mission == null || mission.isTerminal();
    }

    private void releaseStale(World world, UUID aircraftId, UUID missionId) {
        RDPMission mission = RDPMissionWorldData.get(world).getRepository().find(missionId);
        if (mission != null) {
            release(mission);
            return;
        }
        assignments.release(aircraftId, missionId);
        aircraft.release(aircraftId, missionId);
        MissionMovementAuthority.releaseControl(aircraftId, missionId);
        net.vas.rdp.uav.flight.KamikazePilotOverride.clear(aircraftId, missionId);
        net.vas.rdp.uav.flight.OperatorTrimRegistry.clear(aircraftId, missionId);
    }

    /**
     * McHeli's UAV station is the ownership boundary. Re-registering the
     * currently operated craft makes authorization survive server reloads
     * without allowing a player who is not operating it to claim the craft.
     */
    public boolean ensureAuthorized(MCH_EntityAircraft aircraft, EntityPlayer player) {
        if (aircraft == null || player == null) return true;
        AircraftRecord existing = this.aircraft.find(aircraft.getUniqueID());
        if (existing == null) {
            if (aircraft.getAcInfo() == null) return true;
            registerAircraft(new AircraftRecord(aircraft.getUniqueID(), player.getUniqueID(),
                    aircraft.dimension, aircraft.getAcInfo().name, true, true));
            return false;
        }
        return !existing.getOwnerId().equals(player.getUniqueID())
                || existing.getDimension() != aircraft.dimension;
    }

    public void create(World world, RDPMission mission) {
        reconcileStaleAssignment(world, mission.getAircraftId());
        if (!assignments.reserve(mission.getAircraftId(), mission.getMissionId())) {
            throw new IllegalStateException("AIRCRAFT_ALREADY_ASSIGNED");
        }
        net.vas.rdp.uav.flight.OperatorTrimRegistry.clear(mission.getAircraftId(), null);

        try {
            aircraft.assign(mission.getAircraftId(), mission.getMissionId(), mission.getOwnerId());
            if (mission.getTargetId() != null && !coordinator.claim(mission.getAircraftId(), mission.getTargetId())) {
                throw new IllegalStateException("TARGET_ALREADY_ASSIGNED");
            }
            RDPMissionWorldData.get(world).add(mission);
            queue.enqueue(mission);
        } catch (RuntimeException failure) {
            assignments.release(mission.getAircraftId(), mission.getMissionId());
            aircraft.release(mission.getAircraftId(), mission.getMissionId());
            coordinator.release(mission.getAircraftId(), mission.getTargetId());
            MissionMovementAuthority.releaseControl(
                    mission.getAircraftId(), mission.getMissionId());
            net.vas.rdp.uav.flight.KamikazePilotOverride.clear(
                    mission.getAircraftId(), mission.getMissionId());
            throw failure;
        }
    }

    /**
     * Validated reconnaissance-to-strike handoff. Reservations remain attached
     * to the aircraft throughout the transfer; a failed validation leaves the
     * reconnaissance hold and its assignment unchanged.
     */
    public synchronized boolean transferToLinkedStrike(World world, RDPMission parent,
                                                       RDPMission child, long tick) {
        if (world == null || world.isRemote || parent == null || child == null
                || tick < parent.getLastUpdateTick()
                || parent.getState() != MissionState.HOLDING
                || (parent.getMissionType() != MissionType.RECONNAISSANCE
                && parent.getMissionType() != MissionType.OBSERVATION)
                || parent.getReportId() == null || parent.getReportId().trim().isEmpty()
                || (child.getMissionType() != MissionType.STRIKE
                && child.getMissionType() != MissionType.SQUARE_STRIKE)
                || !parent.getAircraftId().equals(child.getAircraftId())
                || !parent.getOwnerId().equals(child.getOwnerId())
                || child.getDimension() != parent.getDimension()
                || !ownsAssignment(parent)
                || activeMission(world, parent.getAircraftId()) != parent) return false;

        RDPMissionWorldData data = RDPMissionWorldData.get(world);
        if (data.getRepository().find(child.getMissionId()) != null) return false;
        AircraftRecord record = aircraft.find(parent.getAircraftId());
        if (record == null || !parent.getMissionId().equals(record.getMissionId())) return false;
        UUID oldTarget = parent.getTargetId();
        UUID newTarget = child.getTargetId();
        if (newTarget != null && coordinator.isTargetOccupied(newTarget)
                && !coordinator.isAssigned(parent.getAircraftId(), newTarget)) return false;
        if (newTarget != null && !coordinator.claim(parent.getAircraftId(), newTarget)) return false;

        boolean assignmentTransferred = false;
        boolean recordTransferred = false;
        boolean childStored = false;
        UUID previousChild = parent.getChildMissionId();
        UUID previousParent = child.getParentMissionId();
        try {
            assignmentTransferred = assignments.transfer(parent.getAircraftId(),
                    parent.getMissionId(), child.getMissionId());
            if (!assignmentTransferred) throw new IllegalStateException("ASSIGNMENT_TRANSFER_REJECTED");
            recordTransferred = record.transfer(parent.getMissionId(), child.getMissionId());
            if (!recordTransferred) throw new IllegalStateException("AIRCRAFT_RECORD_TRANSFER_REJECTED");
            parent.linkChildMission(child);
            data.add(child);
            childStored = true;
            queue.enqueue(child);
            parent.complete(tick);
            MissionMovementAuthority.releaseControl(parent.getAircraftId(), parent.getMissionId());
            net.vas.rdp.uav.flight.KamikazePilotOverride.clear(
                    parent.getAircraftId(), parent.getMissionId());
            // Retire the reconnaissance route only after the assignment and
            // child record have committed. The queued strike will install its
            // own route on the next lifecycle dispatch.
            com.norwood.mcheli.wingman.wingman.WingmanRegistry.remove(
                    parent.getAircraftId());
            net.vas.rdp.uav.flight.OperatorTrimRegistry.clear(
                    parent.getAircraftId(), parent.getMissionId());
            if (oldTarget != null && !oldTarget.equals(newTarget)) {
                coordinator.release(parent.getAircraftId(), oldTarget);
            }
            data.markDirty();
            return true;
        } catch (RuntimeException failure) {
            queue.remove(child);
            if (childStored) data.getRepository().remove(child.getMissionId());
            parent.restoreExecutionMetadata(parent.getExecutionStage(), null, previousChild);
            child.restoreExecutionMetadata(child.getExecutionStage(), previousParent, null);
            if (recordTransferred) record.transfer(child.getMissionId(), parent.getMissionId());
            if (assignmentTransferred) assignments.transfer(parent.getAircraftId(),
                    child.getMissionId(), parent.getMissionId());
            if (newTarget != null && !newTarget.equals(oldTarget)) {
                coordinator.release(parent.getAircraftId(), newTarget);
            }
            return false;
        }
    }

    public void release(RDPMission mission) {
        if (mission != null) {
            queue.remove(mission);
            mission.setQueued(false);
            assignments.release(mission.getAircraftId(), mission.getMissionId());
            aircraft.release(mission.getAircraftId(), mission.getMissionId());
            coordinator.release(mission.getAircraftId(), mission.getTargetId());
            MissionMovementAuthority.releaseControl(
                    mission.getAircraftId(), mission.getMissionId());
            net.vas.rdp.uav.flight.KamikazePilotOverride.clear(
                    mission.getAircraftId(), mission.getMissionId());
            net.vas.rdp.uav.flight.OperatorTrimRegistry.clear(
                    mission.getAircraftId(), mission.getMissionId());
        }
    }

    /**
     * Rebuilds in-memory assignment state after a world reload. Persisted
     * missions remain authoritative; terminal records are intentionally not
     * re-reserved.
     */
    public void restoreAssignments(World world) {
        if (world == null) return;
        RDPMissionWorldData data = RDPMissionWorldData.get(world);
        for (RDPMission mission : RDPMissionWorldData.get(world).getRepository().snapshot()) {
            if (mission.isTerminal()) {
                continue;
            }
            if (!assignments.reserve(mission.getAircraftId(), mission.getMissionId())) {
                mission.fail("AIRCRAFT_RESTORE_CONFLICT", mission.getLastUpdateTick());
                data.markDirty();
                continue;
            }
            AircraftRecord record = aircraft.find(mission.getAircraftId());
            if (record == null) {
                record = new AircraftRecord(mission.getAircraftId(), mission.getOwnerId(),
                        mission.getDimension(), mission.getAircraftType(), false, true);
                aircraft.register(record);
            }
            if (record.getMissionId() == null) {
                try {
                    aircraft.assign(mission.getAircraftId(), mission.getMissionId(),
                            mission.getOwnerId());
                } catch (IllegalStateException unavailable) {
                    mission.fail("AIRCRAFT_RESTORE_UNAVAILABLE", mission.getLastUpdateTick());
                    release(mission);
                    data.markDirty();
                    continue;
                }
            } else if (!mission.getMissionId().equals(record.getMissionId())) {
                mission.fail("AIRCRAFT_RESTORE_CONFLICT", mission.getLastUpdateTick());
                assignments.release(mission.getAircraftId(), mission.getMissionId());
                MissionMovementAuthority.releaseControl(
                        mission.getAircraftId(), mission.getMissionId());
                net.vas.rdp.uav.flight.KamikazePilotOverride.clear(
                        mission.getAircraftId(), mission.getMissionId());
            net.vas.rdp.uav.flight.OperatorTrimRegistry.clear(
                    mission.getAircraftId(), mission.getMissionId());
                data.markDirty();
                continue;
            }
            if (mission.getTargetId() != null
                    && !coordinator.claim(mission.getAircraftId(), mission.getTargetId())) {
                mission.fail("TARGET_RESTORE_CONFLICT", mission.getLastUpdateTick());
                release(mission);
                data.markDirty();
            }
        }
    }

    public UUID assignedMission(UUID aircraftId) {
        return assignments.missionFor(aircraftId);
    }

    public RDPMission activeMission(World world, UUID aircraftId) {
        if (world == null || aircraftId == null) return null;
        UUID missionId = assignments.missionFor(aircraftId);
        return missionId == null
                ? null
                : RDPMissionWorldData.get(world).getRepository().find(missionId);
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
