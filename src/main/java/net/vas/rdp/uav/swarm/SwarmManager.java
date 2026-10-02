package net.vas.rdp.uav.swarm;

import net.minecraft.world.World;
import net.vas.rdp.uav.persistence.RDPUavSwarmWorldData;
import java.util.UUID;
import net.vas.rdp.uav.mission.MissionManager;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.persistence.RDPMissionWorldData;
import net.vas.rdp.uav.flight.WingManFlightAdapter;
import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.vas.rdp.uav.target.TargetPolicy;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.UavVehicleProfiles;

/** Server-side swarm aggregate; individual missions remain MissionManager-owned. */
public final class SwarmManager {
    private final MissionManager missions;
    private final WingManFlightAdapter flight = new WingManFlightAdapter();
    private final SwarmRecoveryManager recovery = new SwarmRecoveryManager();
    private final SwarmReplanner replanner = new SwarmReplanner();
    private final SwarmTaskAllocator taskAllocator = new SwarmTaskAllocator();
    private final SwarmTargetRecoveryPolicy targetRecovery = new SwarmTargetRecoveryPolicy();

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
        if (world == null || world.isRemote || owns(record, owner) || targetId == null) return false;
        Entity target = loadedEntity(world, targetId);
        if (!validSharedTarget(world, targetId, target)) return false;
        if (!TargetPolicy.builder().evaluate(world, target, null).isAccepted()) return false;
        if (!record.getTargetRegistry().lock(targetId, world.provider.getDimension(),
                target.posX, target.posY, target.posZ, tick)) return false;
        record.setLockState(SwarmLockState.LOCKED);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }

    /** Shares only a mission-validated, loaded observation from a member aircraft. */
    public boolean publishValidatedObservation(World world, UUID observingAircraft,
                                               UUID targetId, int targetDimension,
                                               double x, double y, double z, long tick) {
        if (world == null || world.isRemote || observingAircraft == null || targetId == null
                || targetDimension != world.provider.getDimension()
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || tick < 0L) return false;
        Entity target = loadedEntity(world, targetId);
        if (!validSharedTarget(world, targetId, target)
                || Math.abs(target.posX - x) > 1.0D
                || Math.abs(target.posY - y) > 1.0D
                || Math.abs(target.posZ - z) > 1.0D) return false;
        boolean updated = false;
        boolean persistenceDue = false;
        for (SwarmRecord swarm : RDPUavSwarmWorldData.get(world).getRepository().snapshot()) {
            if (!isMember(swarm, observingAircraft)) continue;
            boolean changed = swarm.getTargetRegistry().observe(targetId, targetDimension,
                    target.posX, target.posY, target.posZ, tick);
            if (changed) {
                swarm.setLockState(SwarmLockState.LOCKED);
                updated = true;
                persistenceDue |= swarm.getTargetRegistry().recordObservationPersistence(
                        tick, SwarmConfig.updateIntervalTicks);
            }
        }
        if (updated && persistenceDue) RDPUavSwarmWorldData.get(world).markDirty();
        return updated;
    }

    public boolean allocateAutonomyRoles(World world, UUID owner, UUID swarmId) {
        SwarmRecord record = find(world, swarmId);
        if (world == null || world.isRemote || owns(record, owner)) return false;
        taskAllocator.assignAutonomyRoles(record);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }

    public SwarmTargetRecoveryPolicy.Decision targetRecoveryDecision(
            World world, UUID swarmId, long tick) {
        SwarmRecord record = find(world, swarmId);
        if (world == null || record == null) {
            return SwarmTargetRecoveryPolicy.Decision.HOLD;
        }
        return targetRecovery.decide(record.getTargetRegistry(),
                world.provider.getDimension(), tick, SwarmConfig.targetLossTimeoutTicks);
    }

    public java.util.List<SearchSector> targetLossSearchSectors(World world, UUID swarmId) {
        SwarmRecord record = find(world, swarmId);
        if (world == null || record == null) return java.util.Collections.emptyList();
        SwarmTargetRegistry target = record.getTargetRegistry();
        if (target.getTargetId() == null || target.getDimension() != world.provider.getDimension()
                || !Double.isFinite(target.getLastX()) || !Double.isFinite(target.getLastZ())) {
            return java.util.Collections.emptyList();
        }
        int searchers = 0;
        for (SwarmMember member : record.getMembers()) {
            if (member.isAvailable() && !member.isOperatorControlled()
                    && (member.getRole() == SwarmRole.SEARCHER
                    || member.getRole() == SwarmRole.TRACKER)) searchers++;
        }
        if (searchers == 0) return java.util.Collections.emptyList();
        double radius = SwarmConfig.targetLossSearchRadius;
        return new SearchCoordinator().sectors(target.getLastX(), target.getLastZ(),
                radius * 2.0D, radius * 2.0D, searchers);
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
        boolean changed = false;
        boolean observationPersistenceDue = false;
        for (SwarmRecord swarm : RDPUavSwarmWorldData.get(world).getRepository().snapshot()) {
            SwarmState previousState = swarm.getState();
            SwarmLockState previousLockState = swarm.getLockState();
            UUID previousCommanderId = swarm.getCommanderId();
            SwarmTargetState previousTargetState = swarm.getTargetRegistry().getState();
            UUID previousTargetId = swarm.getTargetRegistry().getTargetId();
            boolean previousSearchDispatched =
                    swarm.getTargetRegistry().isSearchDispatched();
            java.util.Map<UUID, MemberSnapshot> previousMembers = new java.util.HashMap<>();
            for (SwarmMember member : swarm.getMembers()) {
                previousMembers.put(member.getAircraftId(), new MemberSnapshot(member));
            }
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
            taskAllocator.assignAutonomyRoles(swarm);
            SwarmTargetRegistry sharedTarget = swarm.getTargetRegistry();
            if (sharedTarget.getTargetId() != null
                    && sharedTarget.getDimension() == world.provider.getDimension()) {
                Entity exactTarget = loadedEntity(world, sharedTarget.getTargetId());
                if (validSharedTarget(world, sharedTarget.getTargetId(), exactTarget)) {
                    boolean observed = sharedTarget.observe(sharedTarget.getTargetId(),
                            world.provider.getDimension(), exactTarget.posX, exactTarget.posY,
                            exactTarget.posZ, world.getTotalWorldTime());
                    if (observed) {
                        swarm.setLockState(SwarmLockState.LOCKED);
                        observationPersistenceDue |=
                                sharedTarget.recordObservationPersistence(
                                        world.getTotalWorldTime(),
                                        SwarmConfig.updateIntervalTicks);
                    }
                } else if (sharedTarget.getState() != SwarmTargetState.LOST
                        && Double.isFinite(sharedTarget.getLastX())
                        && Double.isFinite(sharedTarget.getLastY())
                        && Double.isFinite(sharedTarget.getLastZ())
                        && world.isBlockLoaded(new BlockPos(sharedTarget.getLastX(),
                        sharedTarget.getLastY(), sharedTarget.getLastZ()))) {
                    sharedTarget.markLost(world.getTotalWorldTime());
                    swarm.setLockState(SwarmLockState.LOST);
                }
            }
            SwarmTargetRecoveryPolicy.Decision recoveryDecision = targetRecovery.decide(
                    sharedTarget, world.provider.getDimension(), world.getTotalWorldTime(),
                    SwarmConfig.targetLossTimeoutTicks);
            boolean searchLocationLoaded = sharedTarget.getDimension()
                    == world.provider.getDimension()
                    && Double.isFinite(sharedTarget.getLastX())
                    && Double.isFinite(sharedTarget.getLastY())
                    && Double.isFinite(sharedTarget.getLastZ())
                    && world.isBlockLoaded(new BlockPos(sharedTarget.getLastX(),
                    sharedTarget.getLastY(), sharedTarget.getLastZ()));
            if (recoveryDecision == SwarmTargetRecoveryPolicy.Decision.SEARCH_SAME_UUID
                    && searchLocationLoaded
                    && !sharedTarget.isSearchDispatched()) {
                dispatchTargetLossSearch(world, swarm, sharedTarget);
            } else if (recoveryDecision == SwarmTargetRecoveryPolicy.Decision.RESUME_PATROL
                    && sharedTarget.getState() == SwarmTargetState.LOST) {
                sharedTarget.clear();
                swarm.setLockState(SwarmLockState.CLEAR);
            }
            if (swarm.referenceMember() == null && !swarm.getMembers().isEmpty()) {
                swarm.setState(SwarmState.DEGRADED);
            } else if (swarm.getState() == SwarmState.DEGRADED) {
                swarm.setState(SwarmState.PARTIAL);
            }
            recovery.reconcile(swarm);
            changed |= previousState != swarm.getState()
                    || previousLockState != swarm.getLockState()
                    || !java.util.Objects.equals(previousCommanderId, swarm.getCommanderId())
                    || previousTargetState != swarm.getTargetRegistry().getState()
                    || previousSearchDispatched
                    != swarm.getTargetRegistry().isSearchDispatched()
                    || !java.util.Objects.equals(previousTargetId,
                    swarm.getTargetRegistry().getTargetId());
            for (SwarmMember member : swarm.getMembers()) {
                MemberSnapshot previous = previousMembers.get(member.getAircraftId());
                if (previous == null || !previous.matches(member)) {
                    changed = true;
                    break;
                }
            }
        }
        if (changed || observationPersistenceDue) {
            RDPUavSwarmWorldData.get(world).markDirty();
        }
    }

    private void dispatchTargetLossSearch(World world, SwarmRecord swarm,
                                          SwarmTargetRegistry target) {
        if (missions == null || target.getTargetId() == null
                || target.getDimension() != world.provider.getDimension()) return;
        for (SwarmMember member : swarm.getMembers()) {
            if (member.getRole() != SwarmRole.SEARCHER || !member.isAvailable()
                    || member.isOperatorControlled()) continue;
            MCH_EntityAircraft aircraft = aircraft(world, member.getAircraftId());
            if (aircraft == null || !aircraft.isUAV() || aircraft.getAcInfo() == null) continue;
            UavVehicleProfile profile =
                    UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name);
            if (!profile.isReusable()
                    || !profile.getExecutionProfile().name().equals(member.getProfile())) continue;
            net.vas.rdp.uav.mission.AircraftRecord registered =
                    missions.findAircraft(member.getAircraftId());
            if (registered == null) {
                missions.registerAircraft(new net.vas.rdp.uav.mission.AircraftRecord(
                        member.getAircraftId(), swarm.getOwnerId(),
                        world.provider.getDimension(), aircraft.getAcInfo().name, true, true));
            } else if (!swarm.getOwnerId().equals(registered.getOwnerId())
                    || registered.getDimension() != world.provider.getDimension()
                    || !aircraft.getAcInfo().name.equals(registered.getType())) {
                continue;
            }
            if (missions.isAircraftUnavailable(world, member.getAircraftId(),
                    swarm.getOwnerId())) continue;
            java.util.List<net.vas.rdp.uav.mission.MissionWaypoint> route;
            try {
                route = SwarmSearchRoutePlanner.route(target.getLastX(), target.getLastZ(),
                        SwarmConfig.targetLossSearchRadius);
            } catch (IllegalArgumentException invalid) {
                return;
            }
            net.vas.rdp.uav.mission.PatrolRoutePlanner.Plan plan =
                    net.vas.rdp.uav.mission.PatrolRoutePlanner.plan(
                            world, aircraft, route, profile, world.getTotalWorldTime());
            if (!plan.isAccepted()) {
                net.vas.rdp.uav.RdpUavDebug.debug("Swarm",
                        "same-UUID search route rejected swarm={} aircraft={} reason={}",
                        swarm.getSwarmId(), member.getAircraftId(), plan.getReason());
                return;
            }
            net.vas.rdp.uav.mission.MissionWaypoint first = route.get(0);
            net.vas.rdp.uav.mission.RDPMission mission =
                    new net.vas.rdp.uav.mission.RDPMission(UUID.randomUUID(),
                            swarm.getOwnerId(), member.getAircraftId(),
                            aircraft.getAcInfo().name,
                            net.vas.rdp.uav.mission.MissionType.PATROL,
                            net.vas.rdp.uav.target.TargetSpec.point(first.getX(),
                                    aircraft.posY, first.getZ(),
                                    net.vas.rdp.uav.target.TargetPriority.NORMAL),
                            false, false, false, Math.max(1.0D, profile.getDetectionRange()),
                            8.0D, world.getTotalWorldTime());
            mission.setDimension(world.provider.getDimension());
            mission.setPatrolRoute(route);
            try {
                missions.create(world, mission);
                target.setSearchDispatched(true);
                RDPUavSwarmWorldData.get(world).markDirty();
                return;
            } catch (IllegalStateException unavailable) {
                net.vas.rdp.uav.RdpUavDebug.debug("Swarm",
                        "same-UUID search assignment rejected swarm={} aircraft={} reason={}",
                        swarm.getSwarmId(), member.getAircraftId(), unavailable.getMessage());
                return;
            }
        }
    }

    private static final class MemberSnapshot {
        private final boolean available;
        private final SwarmMemberState state;
        private final SwarmRole role;
        private final UUID assignedTarget;

        private MemberSnapshot(SwarmMember member) {
            available = member.isAvailable();
            state = member.getState();
            role = member.getRole();
            assignedTarget = member.getAssignedTarget();
        }

        private boolean matches(SwarmMember member) {
            return available == member.isAvailable()
                    && state == member.getState()
                    && role == member.getRole()
                    && java.util.Objects.equals(assignedTarget, member.getAssignedTarget());
        }
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
                    mission.abort("SWARM_ABORT", world.getTotalWorldTime());
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

    private static boolean isMember(SwarmRecord swarm, UUID aircraftId) {
        for (SwarmMember member : swarm.getMembers()) {
            if (aircraftId.equals(member.getAircraftId())) return true;
        }
        return false;
    }

    private static boolean validSharedTarget(World world, UUID targetId, Entity target) {
        return target != null && !target.isDead && targetId.equals(target.getUniqueID())
                && target.world == world && target.dimension == world.provider.getDimension()
                && world.isBlockLoaded(new BlockPos(target.posX, target.posY, target.posZ));
    }

    private static Entity loadedEntity(World world, UUID entityId) {
        if (world == null || entityId == null) return null;
        for (Entity entity : world.loadedEntityList) {
            if (entityId.equals(entity.getUniqueID()) && !entity.isDead) return entity;
        }
        return null;
    }
}
