package net.vas.rdp.uav.swarm;

import net.minecraft.world.World;
import net.vas.rdp.uav.persistence.RDPUavSwarmWorldData;
import java.util.UUID;
import net.vas.rdp.uav.mission.MissionManager;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.persistence.RDPMissionWorldData;
import net.vas.rdp.uav.flight.WingManFlightAdapter;
import com.norwood.mcheli.aircraft.MCH_EntityAircraft;

/** Server-side swarm aggregate; individual missions remain MissionManager-owned. */
public final class SwarmManager {
    private final MissionManager missions;
    private final WingManFlightAdapter flight = new WingManFlightAdapter();
    private final SwarmRecoveryManager recovery = new SwarmRecoveryManager();
    private final SwarmReplanner replanner = new SwarmReplanner();

    public SwarmManager() { this(null); }
    public SwarmManager(MissionManager missions) { this.missions = missions; }
    public SwarmRecord create(World world, UUID owner, String name) {
        if (!SwarmConfig.enabled) {
            throw new IllegalStateException("Swarm system is disabled by server configuration.");
        }
        SwarmRecord record = new SwarmRecord(UUID.randomUUID(), owner, name);
        RDPUavSwarmWorldData.get(world).put(record);
        return record;
    }
    public SwarmRecord find(World world, UUID id) {
        return world == null ? null : RDPUavSwarmWorldData.get(world).getRepository().find(id);
    }
    public boolean owns(SwarmRecord record, UUID owner) {
        return record == null || owner == null || !owner.equals(record.getOwnerId());
    }
    public boolean addMember(World world, UUID owner, UUID swarmId, SwarmMember member) {
        if (!SwarmConfig.enabled) return false;
        SwarmRecord record = find(world, swarmId);
        if (owns(record, owner) || member == null) return false;
        if (!record.isMissionParticipation()
                || record.getMembers().size() >= Math.min(record.getMissionMaximumMembers(),
                Math.min(SwarmConfig.maximumSize,
                        SwarmConfig.maximumForProfile(member.getProfile())))) return false;
        record.add(member);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }
    public boolean setState(World world, UUID owner, UUID id, SwarmState state) {
        SwarmRecord record = find(world, id);
        if (owns(record, owner) || state == null) return false;
        if (state == SwarmState.AWAITING_OPERATOR && record.getResult() == SwarmResult.AWAITING_OPERATOR) {
            record.setResult(SwarmResult.AWAITING_OPERATOR);
        }
        record.setState(state);
        propagateState(world, record, state);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }
    public boolean reform(World world, UUID owner, UUID id, SwarmFormation formation) {
        SwarmRecord record = find(world, id);
        if (owns(record, owner) || formation == null) return false;
        record.setFormation(formation);
        propagateFormation(world, record);
        record.setState(SwarmState.REORGANIZING);
        record.setState(SwarmState.READY);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }
    public boolean removeMember(World world, UUID owner, UUID swarmId, UUID aircraftId) {
        SwarmRecord record = find(world, swarmId);
        if (owns(record, owner)) return false;
        record.remove(aircraftId);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }
    public boolean assignTarget(World world, UUID owner, UUID swarmId, UUID aircraftId,
                                UUID targetId, boolean allowDuplicate) {
        SwarmRecord record = find(world, swarmId);
        if (owns(record, owner) || !record.assignTarget(aircraftId, targetId,
                allowDuplicate && record.isAllowDuplicateTargets())) {
            return false;
        }
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }
    public boolean releaseTarget(World world, UUID owner, UUID swarmId, UUID aircraftId) {
        SwarmRecord record = find(world, swarmId);
        if (owns(record, owner)) return false;
        record.releaseTarget(aircraftId);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }

    public boolean assignRole(World world, UUID owner, UUID swarmId, UUID aircraftId, SwarmRole role) {
        SwarmRecord record = find(world, swarmId);
        if (owns(record, owner) || role == null
                || !record.getAssignmentManager().assign(record, aircraftId, role)) {
            return false;
        }
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }

    public boolean selectCommander(World world, UUID owner, UUID swarmId, UUID aircraftId) {
        SwarmRecord record = find(world, swarmId);
        if (owns(record, owner) || aircraftId == null) return false;
        for (SwarmMember member : record.getMembers()) {
            if (aircraftId.equals(member.getAircraftId()) && member.isAvailable()) {
                record.setDesignatedLeader(aircraftId);
                record.setCommanderId(aircraftId);
                record.setLeadership(SwarmLeadership.DESIGNATED_LEADER);
                RDPUavSwarmWorldData.get(world).markDirty();
                return true;
            }
        }
        return false;
    }

    public boolean lockSharedTarget(World world, UUID owner, UUID swarmId,
                                    UUID targetId, long tick) {
        SwarmRecord record = find(world, swarmId);
        if (owns(record, owner) || targetId == null) return false;
        record.getTargetRegistry().lock(targetId, tick);
        record.setLockState(SwarmLockState.LOCKED);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }

    public boolean clearSharedTarget(World world, UUID owner, UUID swarmId) {
        SwarmRecord record = find(world, swarmId);
        if (owns(record, owner)) return false;
        record.getTargetRegistry().clear();
        record.setLockState(SwarmLockState.CLEAR);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }
    public void reconcile(World world) {
        if (world == null) return;
        for (SwarmRecord swarm : RDPUavSwarmWorldData.get(world).getRepository().snapshot()) {
            for (SwarmMember member : swarm.getMembers()) {
                boolean loaded = false;
                for (net.minecraft.entity.Entity entity : world.loadedEntityList) {
                    if (member.getAircraftId().equals(entity.getUniqueID()) && !entity.isDead) {
                        loaded = true;
                        break;
                    }
                }
                member.setAvailable(loaded);
                RDPMission mission = missionFor(world, member.getAircraftId());
                if (mission != null && !mission.isTerminal()) {
                    member.setState(mission.isPaused() ? SwarmMemberState.HOLDING
                            : SwarmMemberState.ASSIGNED);
                }
                if (!loaded && member.getState() != SwarmMemberState.DESTROYED) {
                    member.setState(SwarmMemberState.DISCONNECTED);
                    replanner.reassignUnavailableTarget(swarm, member.getAircraftId());
                }
            }
            if (swarm.referenceMember() == null && !swarm.getMembers().isEmpty()) {
                swarm.setState(SwarmState.DEGRADED);
            } else if (swarm.getState() == SwarmState.DEGRADED) {
                swarm.setState(SwarmState.PARTIAL);
            }
            recovery.reconcile(swarm);
        }
        RDPUavSwarmWorldData.get(world).markDirty();
    }

    private void propagateState(World world, SwarmRecord record, SwarmState state) {
        for (SwarmMember member : record.getMembers()) {
            RDPMission mission = missionFor(world, member.getAircraftId());
            if (mission == null || mission.isTerminal()) continue;
            MCH_EntityAircraft aircraft = aircraft(world, member.getAircraftId());
            if (state == SwarmState.AWAITING_OPERATOR) {
                mission.setPaused(true);
                member.setState(SwarmMemberState.HOLDING);
                if (aircraft != null) flight.clearGuidance(aircraft);
            } else if (state == SwarmState.READY || state == SwarmState.SEARCHING
                    || state == SwarmState.DEPLOYING) {
                mission.setPaused(false);
                member.setState(SwarmMemberState.ASSIGNED);
            } else if (state == SwarmState.DISBANDED) {
                if (missions != null) missions.removeQueuedMission(mission);
                if (!mission.isTerminal()) {
                    mission.transitionTo(MissionState.ABORTED, world.getTotalWorldTime());
                }
                if (aircraft != null) flight.release(aircraft);
                if (missions != null) missions.release(mission);
                member.setState(SwarmMemberState.IDLE);
            }
        }
        RDPMissionWorldData.get(world).markDirty();
    }

    private void propagateFormation(World world, SwarmRecord record) {
        for (SwarmMember member : record.getMembers()) {
            RDPMission mission = missionFor(world, member.getAircraftId());
            if (mission != null && !mission.isTerminal()) {
                mission.setPaused(false);
                MCH_EntityAircraft aircraft = aircraft(world, member.getAircraftId());
                if (aircraft != null) flight.clearGuidance(aircraft);
            }
        }
    }

    private RDPMission missionFor(World world, UUID aircraftId) {
        if (world == null || aircraftId == null) return null;
        for (RDPMission mission : RDPMissionWorldData.get(world).getRepository().snapshot()) {
            if (aircraftId.equals(mission.getAircraftId()) && !mission.isTerminal()) return mission;
        }
        return null;
    }

    private MCH_EntityAircraft aircraft(World world, UUID id) {
        if (world == null) return null;
        for (net.minecraft.entity.Entity entity : world.loadedEntityList) {
            if (entity instanceof MCH_EntityAircraft && id.equals(entity.getUniqueID())
                    && !entity.isDead) return (MCH_EntityAircraft) entity;
        }
        return null;
    }
}
