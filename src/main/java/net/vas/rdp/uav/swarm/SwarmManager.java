package net.vas.rdp.uav.swarm;

import net.minecraft.world.World;
import net.vas.rdp.uav.persistence.RDPUavSwarmWorldData;
import java.util.UUID;
import net.vas.rdp.uav.mission.MissionManager;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.mission.AircraftRecord;
import net.vas.rdp.uav.persistence.RDPMissionWorldData;
import net.vas.rdp.uav.flight.WingManFlightAdapter;
import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.vas.rdp.uav.target.TargetPolicy;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.UavVehicleProfiles;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.mission.MissionDefinition;
import net.vas.rdp.uav.mission.PatrolRoutePlanner;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.MissionValidationResult;
import net.vas.rdp.uav.mission.UavControlAccess;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.jetbrains.annotations.NotNull;

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
        if (world == null || world.isRemote || owner == null || !SwarmConfig.enabled) {
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
        return record != null && owner != null && owner.equals(record.getOwnerId());
    }
    public boolean addMember(World world, UUID owner, UUID swarmId, SwarmMember member) {
        if (world == null || world.isRemote || owner == null || !SwarmConfig.enabled) return false;
        SwarmRecord record = find(world, swarmId);
        if (!owns(record, owner) || member == null
                || record.getState() == SwarmState.DISBANDED) return false;
        for (SwarmRecord existing : RDPUavSwarmWorldData.get(world)
                .getRepository().snapshot()) {
            if (!existing.getSwarmId().equals(swarmId)
                    && isMember(existing, member.getAircraftId())) return false;
        }
        if (!record.isMissionParticipation()
                || record.getMembers().size() >= Math.min(record.getMissionMaximumMembers(),
                Math.min(record.getConfiguration().getMaximumMembers(), Math.min(SwarmConfig.maximumSize,
                        SwarmConfig.maximumForProfile(member.getProfile()))))) return false;
        record.add(member);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }

    /** Creates an owner-scoped swarm and enrolls the aircraft the server verified as operated. */
    public SwarmRecord createForOperatedAircraft(World world, UUID owner,
                                                  String name,
                                                  MCH_EntityAircraft operatedAircraft) {
        if (world == null || world.isRemote || owner == null
                || operatedAircraft == null || operatedAircraft.world != world) {
            throw new IllegalArgumentException("A server world, owner, and operated aircraft are required");
        }
        SwarmRecord record = create(world, owner, name);
        if (!addOperatedAircraft(world, owner, record.getSwarmId(), operatedAircraft)) {
            RDPUavSwarmWorldData data = RDPUavSwarmWorldData.get(world);
            data.getRepository().remove(record.getSwarmId());
            data.markDirty();
            throw new IllegalArgumentException("The operated UAV is unavailable or unsupported");
        }
        return record;
    }

    /** Adds only a currently operated, configured McHeli UAV to an owned swarm. */
    public boolean addOperatedAircraft(World world, UUID owner, UUID swarmId,
                                       MCH_EntityAircraft operatedAircraft) {
        if (world == null || world.isRemote || operatedAircraft == null
                || operatedAircraft.world != world || !operatedAircraft.isUAV()
                || operatedAircraft.getAcInfo() == null || missions == null) return false;
        UavVehicleProfile profile = UavVehicleProfiles.forAircraft(operatedAircraft.getAcInfo().name);
        if (profile.getExecutionProfile() == UavExecutionProfile.UNKNOWN) return false;
        for (SwarmRecord existingSwarm : RDPUavSwarmWorldData.get(world)
                .getRepository().snapshot()) {
            if (!existingSwarm.getSwarmId().equals(swarmId)
                    && isMember(existingSwarm, operatedAircraft.getUniqueID())) return false;
        }
        AircraftRecord registered = missions.findAircraft(operatedAircraft.getUniqueID());
        if (registered == null) {
            missions.registerAircraft(new AircraftRecord(operatedAircraft.getUniqueID(), owner,
                    world.provider.getDimension(), operatedAircraft.getAcInfo().name, true, true));
        } else if (!owner.equals(registered.getOwnerId())
                || registered.getDimension() != world.provider.getDimension()
                || !operatedAircraft.getAcInfo().name.equals(registered.getType())) {
            return false;
        }
        if (missions.isAircraftUnavailable(world, operatedAircraft.getUniqueID(), owner)) return false;
        SwarmMember member = new SwarmMember(operatedAircraft.getUniqueID(),
                profile.getExecutionProfile().name(), profile.supportsLongRangeLock(),
                operatedAircraft.getWeapons() != null);
        member.setOperatorControlled(UavControlAccess.hasDirectAircraftRider(operatedAircraft));
        return addMember(world, owner, swarmId, member);
    }

    /**
     * Fans one validated mission intent out to eligible swarm members. Each
     * aircraft remains independently owned by MissionManager/MissionLifecycle.
     */
    public int launchMission(World world, UUID owner, UUID swarmId,
                             MissionDefinition definition) {
        if (world == null || world.isRemote || owner == null || missions == null
                || definition == null || !SwarmConfig.enabled) {
            throw new IllegalArgumentException("Swarm mission launch is unavailable on this side");
        }
        SwarmRecord swarm = find(world, swarmId);
        if (!owns(swarm, owner)) throw new IllegalArgumentException("Swarm is not owned by this operator");
        if (swarm.getState() == SwarmState.DISBANDED) {
            throw new IllegalArgumentException("A disbanded swarm cannot launch missions");
        }
        if (!definition.isSwarmEnabled() || !swarmId.equals(definition.getSwarmId())) {
            throw new IllegalArgumentException("Mission definition does not identify this swarm");
        }
        if (definition.getSwarmConfiguration().getMaximumMembers() > SwarmConfig.maximumSize) {
            throw new IllegalArgumentException("Requested swarm size exceeds server policy");
        }
        if (definition.getSwarmConfiguration().getFormation() == SwarmFormation.CUSTOM
                || definition.getSwarmConfiguration().getSearchPattern() == SearchPattern.CUSTOM) {
            throw new IllegalArgumentException("Custom swarm layouts need explicit route geometry, which is not available yet");
        }
        MissionValidationResult validation = MissionValidationResult.validate(definition);
        if (!validation.isValid()) throw new IllegalArgumentException(validation.getErrors().get(0));
        MissionType type = getType(definition);

        java.util.Set<UUID> explicitlySelected = new java.util.LinkedHashSet<>(definition.getSelectedUavs());
        java.util.Set<UUID> knownMembers = new java.util.HashSet<>();
        for (SwarmMember member : swarm.getMembers()) knownMembers.add(member.getAircraftId());
        if (!knownMembers.containsAll(explicitlySelected)) {
            throw new IllegalArgumentException("Selected UAV is not a member of this swarm");
        }

        int limit = Math.min(definition.getSwarmMaximumMembers(),
                Math.min(swarm.getMissionMaximumMembers(),
                        Math.min(swarm.getConfiguration().getMaximumMembers(), SwarmConfig.maximumSize)));
        if (!swarm.isMissionParticipation() || limit < 1) {
            throw new IllegalArgumentException("Swarm mission participation is disabled");
        }

        java.util.List<LaunchCandidate> candidates = new java.util.ArrayList<>();
        java.util.Map<String, Integer> profileCounts = new java.util.HashMap<>();
        java.util.List<SwarmMember> orderedMembers = new java.util.ArrayList<>(swarm.getMembers());
        orderedMembers.sort(java.util.Comparator.comparing(member -> member.getAircraftId().toString()));
            for (SwarmMember member : orderedMembers) {
            UUID aircraftId = member.getAircraftId();
            if (!explicitlySelected.isEmpty() && !explicitlySelected.contains(aircraftId)) continue;
            MCH_EntityAircraft aircraft = aircraft(world, aircraftId);
            if (aircraft == null || !aircraft.isUAV() || aircraft.getAcInfo() == null
                    || aircraft.dimension != world.provider.getDimension()) {
                if (!explicitlySelected.isEmpty()) {
                    throw new IllegalArgumentException("A selected UAV is not loaded in this dimension");
                }
                continue;
            }
            member.setAvailable(true);
            member.setOperatorControlled(UavControlAccess.hasDirectAircraftRider(aircraft));
            if (member.isOperatorControlled()) {
                if (!explicitlySelected.isEmpty()) {
                    throw new IllegalArgumentException("A selected UAV still has a direct pilot");
                }
                continue;
            }
            UavVehicleProfile profile = UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name);
            String profileName = profile.getExecutionProfile().name();
            if (profile.getExecutionProfile() == UavExecutionProfile.UNKNOWN
                    || !profileName.equals(member.getProfile())) {
                if (!explicitlySelected.isEmpty()) {
                    throw new IllegalArgumentException("A selected UAV profile no longer matches its swarm record");
                }
                continue;
            }
            AircraftRecord registered = missions.findAircraft(aircraftId);
            if (registered == null) {
                missions.registerAircraft(new AircraftRecord(aircraftId, owner,
                        world.provider.getDimension(), aircraft.getAcInfo().name, true, true));
            } else if (!owner.equals(registered.getOwnerId())
                    || registered.getDimension() != world.provider.getDimension()
                    || !aircraft.getAcInfo().name.equals(registered.getType())) {
                if (!explicitlySelected.isEmpty()) {
                    throw new IllegalArgumentException("A selected UAV is not authorized to this operator");
                }
                continue;
            }
            if (missions.isAircraftUnavailable(world, aircraftId, owner)) {
                if (!explicitlySelected.isEmpty()) {
                    throw new IllegalArgumentException("A selected UAV already has an active mission");
                }
                continue;
            }
            int profileLimit = SwarmConfig.maximumForProfile(profileName);
            int profileCount = profileCounts.containsKey(profileName) ? profileCounts.get(profileName) : 0;
            if (profileCount >= profileLimit) continue;
            if (type == MissionType.PATROL) {
                PatrolRoutePlanner.Plan routePlan = PatrolRoutePlanner.plan(world, aircraft,
                        definition.getWaypoints(), profile, world.getTotalWorldTime());
                if (!routePlan.isAccepted()) {
                    if (!explicitlySelected.isEmpty()) {
                        throw new IllegalArgumentException("A selected UAV rejected the patrol route: "
                                + routePlan.getReason());
                    }
                    continue;
                }
            }
            if (definition.isAttack()) {
                if (!flight.hasAttackAmmunition(aircraft)
                        && !flight.hasSelfDestructWeaponWithAmmo(aircraft)) {
                    if (!explicitlySelected.isEmpty()) {
                        throw new IllegalArgumentException("A selected UAV has no supported attack payload");
                    }
                    continue;
                }
                if (!definition.getGuidedWeapon().trim().isEmpty()
                        && !hasSupportedWeapon(aircraft, definition.getGuidedWeapon())) {
                    if (!explicitlySelected.isEmpty()) {
                        throw new IllegalArgumentException("A selected UAV lacks the configured guided weapon");
                    }
                    continue;
                }
            }
            candidates.add(new LaunchCandidate(member, aircraft, profile));
            profileCounts.put(profileName, profileCount + 1);
            if (candidates.size() >= limit) break;
        }
        if (candidates.isEmpty()) throw new IllegalArgumentException("No eligible autonomous swarm members are available");
        if (!explicitlySelected.isEmpty() && candidates.size() != explicitlySelected.size()) {
            throw new IllegalArgumentException("Not every selected UAV passed launch validation");
        }

        swarm.setConfiguration(definition.getSwarmConfiguration());
        swarm.setMissionMaximumMembers(limit);
        swarm.setMissionParticipation(true);
        swarm.setMissionState(SwarmMissionState.ASSIGNED);
        taskAllocator.assignAutonomyRoles(swarm);
        long tick = world.getTotalWorldTime();
        TargetPriority priority = definition.getPriority();
        java.util.List<RDPMission> created = new java.util.ArrayList<>();
        try {
            for (LaunchCandidate candidate : candidates) {
                TargetSpec target = type == MissionType.PATROL
                        ? TargetSpec.point(definition.getWaypoints().get(0).getX(),
                        candidate.aircraft.posY, definition.getWaypoints().get(0).getZ(), priority)
                        : definition.getTarget().regionMatches(true, 0, "mod=", 0, 4)
                        ? TargetSpec.modNamespace(definition.getTarget().substring(4), priority)
                        : TargetSpec.manual(definition.getTarget(), priority);
                assert candidate.aircraft.getAcInfo() != null;
                RDPMission mission = new RDPMission(UUID.randomUUID(), owner,
                        candidate.aircraft.getUniqueID(), candidate.aircraft.getAcInfo().name,
                        type, target, definition.isAttack(), type == MissionType.DESTROYING,
                        true, Math.max(1D, candidate.profile.getDetectionRange()), 8D, tick);
                mission.setDimension(world.provider.getDimension());
                if (definition.getCruiseAltitudeY() != null) {
                    if (definition.getCruiseAltitudeY() > world.getHeight() - 2) {
                        throw new IllegalArgumentException(
                                "Cruise altitude is outside this world's safe bounds");
                    }
                    mission.setCruiseAltitudeY(definition.getCruiseAltitudeY());
                    net.vas.rdp.uav.RdpUavDebug.event("MissionRuntime",
                            "CRUISE_ALTITUDE_ACCEPTED", "mission", mission.getMissionId(),
                            "aircraft", mission.getAircraftId(),
                            "dimension", mission.getDimension(),
                            "worldY", mission.getCruiseAltitudeY(),
                            "source", "SWARM_MISSION_DEFINITION");
                }
                mission.setTargetPolicy(definition.getTargetPolicy());
                if (type == MissionType.PATROL) mission.setPatrolRoute(definition.getWaypoints());
                if (!definition.getGuidedWeapon().trim().isEmpty()) {
                    mission.setGuidedWeapon(definition.getGuidedWeapon());
                }
                mission.setSwarmId(swarmId);
                missions.create(world, mission);
                created.add(mission);
                candidate.member.setState(SwarmMemberState.ASSIGNED);
            }
        } catch (RuntimeException failure) {
            for (RDPMission mission : created) {
                mission.fail("SWARM_LAUNCH_ROLLED_BACK", tick);
                missions.release(mission);
            }
            RDPMissionWorldData.get(world).markDirty();
            throw new IllegalArgumentException("Swarm mission launch rolled back: "
                    + (failure.getMessage() == null ? "assignment failed" : failure.getMessage()), failure);
        }
        swarm.setMissionState(SwarmMissionState.ASSIGNED);
        swarm.setState(SwarmState.DEPLOYING);
        RDPUavSwarmWorldData.get(world).markDirty();
        RDPMissionWorldData.get(world).markDirty();
        return created.size();
    }

    @NotNull
    private static MissionType getType(MissionDefinition definition) {
        MissionType type = definition.getMissionType();
        if (type != MissionType.RECONNAISSANCE && type != MissionType.OBSERVATION
                && type != MissionType.PATROL
                && type != MissionType.STRIKE && type != MissionType.DESTROYING) {
            throw new IllegalArgumentException("Mission type is unsupported for swarm fan-out");
        }
        if (definition.isAttack() != (type == MissionType.STRIKE || type == MissionType.DESTROYING)) {
            throw new IllegalArgumentException("Strike mission type and attack authorization must agree");
        }
        return type;
    }

    private static final class LaunchCandidate {
        private final SwarmMember member;
        private final MCH_EntityAircraft aircraft;
        private final UavVehicleProfile profile;

        private LaunchCandidate(SwarmMember member, MCH_EntityAircraft aircraft,
                                UavVehicleProfile profile) {
            this.member = member;
            this.aircraft = aircraft;
            this.profile = profile;
        }
    }

    private static boolean hasSupportedWeapon(MCH_EntityAircraft aircraft, String requested) {
        if (aircraft == null || aircraft.getWeapons() == null) return false;
        for (com.norwood.mcheli.weapon.MCH_WeaponSet weapon : aircraft.getWeapons()) {
            if (weapon == null || weapon.getInfo() == null
                    || !requested.equalsIgnoreCase(weapon.getName())) continue;
            return net.vas.rdp.uav.weapon.GuidedWeaponRegistry.find(
                    weapon.getName(), weapon.getInfo().type) != null;
        }
        return false;
    }
    public boolean setState(World world, UUID owner, UUID id, SwarmState state) {
        SwarmRecord record = find(world, id);
        if (world == null || world.isRemote || !owns(record, owner) || state == null
                || (record.getState() == SwarmState.DISBANDED
                && state != SwarmState.DISBANDED)) return false;
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
        if (world == null || world.isRemote || !owns(record, owner) || formation == null
                || record.getState() == SwarmState.DISBANDED) return false;
        record.setFormation(formation);
        propagateFormation(world, record);
        record.setState(SwarmState.REORGANIZING);
        record.setState(SwarmState.READY);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }
    public boolean removeMember(World world, UUID owner, UUID swarmId, UUID aircraftId) {
        SwarmRecord record = find(world, swarmId);
        if (world == null || world.isRemote || !owns(record, owner)
                || aircraftId == null || !isMember(record, aircraftId)) return false;
        RDPMission activeMission = missionFor(world, swarmId, aircraftId);
        if (activeMission != null && !activeMission.isTerminal()) {
            if (missions != null) missions.removeQueuedMission(activeMission);
            activeMission.abort("SWARM_MEMBER_REMOVED", world.getTotalWorldTime());
            MCH_EntityAircraft memberAircraft = aircraft(world, aircraftId);
            if (memberAircraft != null) flight.release(memberAircraft);
            if (missions != null) missions.release(activeMission);
            RDPMissionWorldData.get(world).markDirty();
        }
        record.remove(aircraftId);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }

    public boolean updateConfiguration(World world, UUID owner, UUID swarmId,
                                       SwarmConfiguration configuration,
                                       boolean participation) {
        SwarmRecord record = find(world, swarmId);
        if (world == null || world.isRemote || !owns(record, owner)
                || configuration == null
                || configuration.getMaximumMembers() > SwarmConfig.maximumSize) return false;
        record.setConfiguration(configuration);
        record.setMissionMaximumMembers(configuration.getMaximumMembers());
        record.setMissionParticipation(participation);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }

    public boolean setDesignatedLeader(World world, UUID owner, UUID swarmId,
                                       UUID aircraftId) {
        SwarmRecord record = find(world, swarmId);
        if (world == null || world.isRemote || !owns(record, owner)
                || aircraftId == null || !isMember(record, aircraftId)
                || record.getState() == SwarmState.DISBANDED) return false;
        record.setDesignatedLeader(aircraftId);
        record.setCommanderId(aircraftId);
        record.setLeadership(SwarmLeadership.DESIGNATED_LEADER);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }
    public boolean assignTarget(World world, UUID owner, UUID swarmId, UUID aircraftId,
                                UUID targetId, boolean allowDuplicate) {
        SwarmRecord record = find(world, swarmId);
        if (world == null || world.isRemote || !owns(record, owner)
                || !record.assignTarget(aircraftId, targetId,
                allowDuplicate && record.isAllowDuplicateTargets())) {
            return false;
        }
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }
    public boolean releaseTarget(World world, UUID owner, UUID swarmId, UUID aircraftId) {
        SwarmRecord record = find(world, swarmId);
        if (world == null || world.isRemote || !owns(record, owner)) return false;
        record.releaseTarget(aircraftId);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }

    public boolean assignRole(World world, UUID owner, UUID swarmId, UUID aircraftId, SwarmRole role) {
        SwarmRecord record = find(world, swarmId);
        if (world == null || world.isRemote || !owns(record, owner) || role == null
                || !record.getAssignmentManager().assign(record, aircraftId, role)) {
            return false;
        }
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }

    public boolean selectCommander(World world, UUID owner, UUID swarmId, UUID aircraftId) {
        SwarmRecord record = find(world, swarmId);
        if (world == null || world.isRemote || !owns(record, owner) || aircraftId == null
                || record.getState() == SwarmState.DISBANDED) return false;
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
        if (world == null || world.isRemote || !owns(record, owner) || targetId == null) return false;
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
        if (world == null || world.isRemote || !owns(record, owner)) return false;
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
        if (world == null || world.isRemote || !owns(record, owner)) return false;
        record.getTargetRegistry().clear();
        record.setLockState(SwarmLockState.CLEAR);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }

    /** Returns the server-validated shared identity for an owned swarm mission. */
    public UUID sharedTargetId(World world, RDPMission mission) {
        if (world == null || world.isRemote || mission == null || mission.getSwarmId() == null) {
            return null;
        }
        SwarmRecord record = find(world, mission.getSwarmId());
        if (!owns(record, mission.getOwnerId())) return null;
        SwarmTargetRegistry shared = record.getTargetRegistry();
        return shared.getDimension() == world.provider.getDimension()
                ? shared.getTargetId() : null;
    }

    /** Enforces the configured per-swarm target exclusivity policy. */
    public boolean canMissionUseTarget(World world, RDPMission mission, UUID targetId) {
        if (mission == null || targetId == null) return false;
        UUID swarmId = mission.getSwarmId();
        if (swarmId == null) return true;
        SwarmRecord swarm = find(world, swarmId);
        if (!owns(swarm, mission.getOwnerId())) return false;
        if (swarm.getConfiguration().isAllowDuplicateTargets()) return true;
        for (RDPMission other : RDPMissionWorldData.get(world).getRepository().snapshot()) {
            if (mission.getMissionId().equals(other.getMissionId()) || other.isTerminal()
                    || !swarmId.equals(other.getSwarmId())) continue;
            if (targetId.equals(other.getTargetId())) return false;
        }
        for (SwarmMember member : swarm.getMembers()) {
            if (!mission.getAircraftId().equals(member.getAircraftId())
                    && targetId.equals(member.getAssignedTarget())) return false;
        }
        return true;
    }

    public void reconcile(World world) {
        if (world == null) return;
        boolean changed = false;
        boolean observationPersistenceDue = false;
        for (SwarmRecord swarm : RDPUavSwarmWorldData.get(world).getRepository().snapshot()) {
            SwarmState previousState = swarm.getState();
            SwarmMissionState previousMissionState = swarm.getMissionState();
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
                MCH_EntityAircraft currentAircraft = aircraft(world, member.getAircraftId());
                member.setOperatorControlled(UavControlAccess.hasDirectAircraftRider(currentAircraft));
                RDPMission mission = missionFor(world, swarm.getSwarmId(), member.getAircraftId());
                if (mission != null && !mission.isTerminal()) {
                    member.setState(mission.isPaused() ? SwarmMemberState.HOLDING
                            : SwarmMemberState.ASSIGNED);
                    member.setAssignedTarget(mission.getTargetId());
                } else {
                    member.setAssignedTarget(null);
                }
                if (!loaded && member.getState() != SwarmMemberState.DESTROYED) {
                    member.setState(SwarmMemberState.DISCONNECTED);
                    replanner.reassignUnavailableTarget(swarm, member.getAircraftId());
                }
            }
            reconcileMissionState(world, swarm);
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
            if (swarm.getLeadership() != SwarmLeadership.NO_SINGLE_LEADER
                    && swarm.referenceMember() == null && !swarm.getMembers().isEmpty()) {
                swarm.setState(SwarmState.DEGRADED);
            } else if (swarm.getState() == SwarmState.DEGRADED) {
                swarm.setState(SwarmState.PARTIAL);
            }
            recovery.reconcile(swarm);
            changed |= previousState != swarm.getState()
                    || previousMissionState != swarm.getMissionState()
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
                        SwarmConfig.targetLossSearchRadius,
                        swarm.getConfiguration().getSearchPattern());
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
            mission.setSwarmId(swarm.getSwarmId());
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
            RDPMission mission = missionFor(world, record.getSwarmId(), member.getAircraftId());
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
            RDPMission mission = missionFor(world, record.getSwarmId(), member.getAircraftId());
            if (mission != null && !mission.isTerminal()) {
                mission.setPaused(false);
                MCH_EntityAircraft aircraft = aircraft(world, member.getAircraftId());
                if (aircraft != null) flight.clearGuidance(aircraft);
            }
        }
    }

    private RDPMission missionFor(World world, UUID swarmId, UUID aircraftId) {
        if (world == null || swarmId == null || aircraftId == null) return null;
        RDPMission latest = null;
        for (RDPMission mission : RDPMissionWorldData.get(world).getRepository().snapshot()) {
            if (swarmId.equals(mission.getSwarmId())
                    && aircraftId.equals(mission.getAircraftId())
                    && (latest == null || mission.getCreatedTick() > latest.getCreatedTick())) {
                latest = mission;
            }
        }
        return latest;
    }

    private void reconcileMissionState(World world, SwarmRecord swarm) {
        boolean found = false;
        boolean active = false;
        boolean queued = false;
        boolean allComplete = true;
        for (RDPMission mission : RDPMissionWorldData.get(world).getRepository().snapshot()) {
            if (!swarm.getSwarmId().equals(mission.getSwarmId())) continue;
            found = true;
            if (!mission.isTerminal()) {
                if (mission.isQueued()
                        || mission.getState() == net.vas.rdp.uav.mission.MissionState.PLANNED) {
                    queued = true;
                } else {
                    active = true;
                }
                allComplete = false;
            } else if (mission.getState() != net.vas.rdp.uav.mission.MissionState.COMPLETE) {
                allComplete = false;
            }
        }
        if (!found) return;
        swarm.setMissionState(active ? SwarmMissionState.EXECUTING
                : queued ? SwarmMissionState.ASSIGNED
                : allComplete ? SwarmMissionState.COMPLETE : SwarmMissionState.FAILED);
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
