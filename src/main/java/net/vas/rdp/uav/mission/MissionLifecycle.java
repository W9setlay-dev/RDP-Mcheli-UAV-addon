package net.vas.rdp.uav.mission;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.helicopter.MCH_EntityHeli;
import com.norwood.mcheli.wingman.wingman.WingmanEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityList;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.vas.rdp.uav.flight.WingManFlightAdapter;
import net.vas.rdp.uav.persistence.RDPMissionWorldData;
import net.vas.rdp.uav.scan.RDPScanReport;
import net.vas.rdp.uav.scan.RDPScanReportRepository;
import net.vas.rdp.uav.target.TargetResolver;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.UavVehicleProfiles;
import net.vas.rdp.uav.target.TargetSelectionMode;
import net.vas.rdp.uav.target.ContactLifecycle;
import net.vas.rdp.uav.target.ContactSnapshot;
import net.vas.rdp.uav.target.ContactTracker;
import net.vas.rdp.uav.flight.KamikazeImpactDetector;
import net.vas.rdp.uav.flight.KamikazePilotOverride;
import net.vas.rdp.uav.weapon.GuidedStrikeObjective;
import net.vas.rdp.uav.weapon.GuidedWeaponAdapter;
import net.vas.rdp.uav.weapon.GuidedWeaponDecision;

import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.vas.rdp.uav.RdpUavDebug;

/**
 * Server-side bridge between persisted RDP missions and WingMan.
 *
 * <p>WingMan remains the sole aircraft controller.  This class only submits
 * safe recon/recovery orders and observes the public WingMan entry state.</p>
 */
public final class MissionLifecycle {
    private static final Logger LOGGER = LogManager.getLogger("RDP-UAV/MissionLifecycle");
    private static final long MISSION_TIMEOUT = 12000L;
    private static final long WINGMAN_ENTRY_GRACE_TICKS = 20L;

    private final MissionManager manager;
    private final WingManFlightAdapter flight = new WingManFlightAdapter();
    private final GuidedWeaponAdapter guidedWeapons = new GuidedWeaponAdapter(flight);
    private final TargetResolver targets = new TargetResolver();
    private final ContactTracker contactTracker = new ContactTracker();
    /** Operator cursor is deliberately separate from RDPMission.targetId (the mission lock). */
    private final Map<UUID, UUID> operatorContacts = new HashMap<>();
    private final Map<World, Boolean> restoredWorlds = new WeakHashMap<>();
    private final Map<UUID, RouteRefresh> routeRefreshes = new HashMap<>();
    private Map<UUID, MCH_EntityAircraft> aircraftThisTick = new HashMap<>();

    public MissionLifecycle(MissionManager manager) {
        if (manager == null) throw new IllegalArgumentException("Mission manager is required");
        this.manager = manager;
    }

    public void tick(World world) {
        if (world == null || world.isRemote) return;
        aircraftThisTick = indexAircraft(world);
        restoreQueue(world);
        dispatchQueued(world);
        updateActive(world);
    }

    private void restoreQueue(World world) {
        synchronized (restoredWorlds) {
            if (restoredWorlds.containsKey(world)) return;
            restoredWorlds.put(world, Boolean.TRUE);
        }
        manager.restoreAssignments(world);
        manager.restoreQueued(world);
    }

    private void dispatchQueued(World world) {
        int count = manager.queuedMissionCount();
        for (int i = 0; i < count; i++) {
            RDPMission mission = manager.pollQueuedMission();
            if (mission == null) return;
            if (mission.isPaused()) {
                manager.enqueueQueuedMission(mission);
                continue;
            }
            if (mission.getDimension() != world.provider.getDimension()) {
                RdpUavDebug.debug("Lifecycle", "mission {} deferred for dimension {} (current {})",
                        mission.getMissionId(), mission.getDimension(), world.provider.getDimension());
                manager.enqueueQueuedMission(mission);
                continue;
            }
            /*
             * A persisted queue flag can outlive the state transition that
             * consumed the mission (for example after a save during target
             * locking). Such a mission is already in the active state
             * machine; dispatching it again would attempt TARGET_LOCKED ->
             * DISPATCHED and stall it forever.
             */
            if (mission.getState() != MissionState.PLANNED) {
                mission.setQueued(false);
                RdpUavDebug.debug("Lifecycle",
                        "mission {} queue flag repaired state={}",
                        mission.getMissionId(), mission.getState());
                RDPMissionWorldData.get(world).markDirty();
                continue;
            }
            dispatch(world, mission);
        }
    }

    private void dispatch(World world, RDPMission mission) {
        long tick = world.getTotalWorldTime();
        MCH_EntityAircraft aircraft = findAircraft(world, mission.getAircraftId());
        if (aircraft == null) {
            RdpUavDebug.debug("Lifecycle", "mission {} waiting for aircraft {}",
                    mission.getMissionId(), mission.getAircraftId());
            defer(world, mission, "AIRCRAFT_NOT_FOUND", MissionState.WAITING_FOR_WORLD, tick);
            return;
        }
        if (aircraft.getAcInfo() == null) {
            defer(world, mission, "AIRCRAFT_METADATA_UNAVAILABLE",
                    MissionState.WAITING_FOR_WORLD, tick);
            return;
        }
        // MissionManager.create already assigned this aircraft. Re-registering
        // it here would replace the ON_MISSION record with a fresh READY
        // record and allow a second mission to claim the same entity.
        if (flight.hasFuelForMission(aircraft)) {
            defer(world, mission, "INSUFFICIENT_FUEL", MissionState.WAITING_FOR_FUEL, tick);
            return;
        }
        if (mission.isAttack() && !flight.hasAttackAmmunition(aircraft)
                && !flight.hasSelfDestructWeaponWithAmmo(aircraft)) {
            fail(world, mission, "NO_ATTACK_AMMUNITION", tick);
            return;
        }

        Target target = locateTarget(world, aircraft, mission, true);
        RdpUavDebug.debug("Lifecycle", "mission {} target lookup result={} state={}",
                mission.getMissionId(), formatTarget(target), mission.getState());
        if (target == null) {
            defer(world, mission, "TARGET_NOT_AVAILABLE", MissionState.WAITING_FOR_TARGET, tick);
            return;
        }
        double dispatchRange = Math.max(mission.getMissionRangeLimit(),
                profile(aircraft).getDetectionRange());
        if (aircraft.getDistance(target.x, target.y, target.z) > dispatchRange) {
            defer(world, mission, "OUT_OF_RANGE_REPLAN", MissionState.REPLAN, tick);
            return;
        }

        try {
            /*
             * WingMan deliberately controls UAVs only. A piloted/non-UAV
             * aircraft may still release a configured guided weapon through
             * McHeli's native weapon API; do that once the server has made
             * the same target and ammunition checks as an autonomous route.
             */
            if (mission.isAttack() && !aircraft.isUAV()
                    && profile(aircraft).getExecutionProfile()
                    != net.vas.rdp.uav.profile.UavExecutionProfile.ONE_WAY) {
                GuidedStrikeObjective objective = target.entity == null
                        ? GuidedStrikeObjective.square(mission.getMissionId(),
                        world.provider.getDimension(), target.x, target.y, target.z, tick)
                        : GuidedStrikeObjective.entity(mission.getMissionId(),
                        target.entity.getUniqueID(), world.provider.getDimension(),
                        target.x, target.y, target.z, tick);
                objective.authorize();
                objective.beginTracking();
                GuidedWeaponDecision guided = guidedWeapons.arm(world, aircraft, objective,
                        aircraftSpeed(aircraft), profile(aircraft).getAttackEnvelope(), true,
                        weaponPriority(mission, aircraft));
                if (!guided.isAccepted()) {
                    if (isPermanentGuidanceFailure(guided.getReason())) {
                        fail(world, mission, guided.getReason(), tick);
                    } else {
                        defer(world, mission, guided.getReason(),
                                guidanceRecoveryState(guided.getReason()), tick);
                    }
                    return;
                }
                if (mission.getState() == MissionState.PLANNED) {
                    mission.transitionTo(MissionState.DISPATCHED, tick);
                    mission.transitionTo(MissionState.TAKEOFF, tick);
                    mission.transitionTo(MissionState.CRUISE, tick);
                    mission.transitionTo(MissionState.SEARCHING, tick);
                }
                mission.transitionTo(MissionState.TARGET_ACQUIRED, tick);
                if (target.entity == null) mission.lockPoint(tick);
                else mission.lockTarget(target.entity.getUniqueID(), tick);
                mission.transitionTo(MissionState.DIRECT_LAUNCH_COMPLETE, tick);
                manager.release(mission);
                RDPMissionWorldData.get(world).markDirty();
                return;
            }
            double guidanceY = terminalRouteAltitude(aircraft, target, mission);
            if (target.entity != null && mission.getTargetId() == null) {
                mission.reacquireTarget(target.entity.getUniqueID(), tick);
            }
            mission.transitionTo(MissionState.DISPATCHED, tick);
            mission.transitionTo(MissionState.TAKEOFF, tick);
            mission.transitionTo(MissionState.CRUISE, tick);
            mission.transitionTo(MissionState.SEARCHING, tick);

            // WingMan must fly the real aircraft to the observation point
            // before the mission reports acquisition or arms an attack.
            if (!flight.dispatch(aircraft, target.x, guidanceY, target.z,
                    mission.getObservationDistance(), 200, mission.isAttack(), mission.getTargetId())) {
                defer(world, mission, "WINGMAN_DISPATCH_REJECTED",
                        MissionState.EXECUTION_STALLED, tick);
            } else {
                mission.setQueued(false);
                LOGGER.info("Mission {} dispatched aircraft {} type={} target={} at tick {}",
                        mission.getMissionId(), aircraft.getUniqueID(), mission.getAircraftType(),
                        formatTarget(target), tick);
            }
        } catch (RuntimeException failure) {
            defer(world, mission, failure.getMessage() == null
                ? "MISSION_DISPATCH_RETRY_REQUIRED" : failure.getMessage(),
                MissionState.EXECUTION_STALLED, tick);
        }
        RDPMissionWorldData.get(world).markDirty();
    }

    private void updateActive(World world) {
        long tick = world.getTotalWorldTime();
        List<RDPMission> missions = RDPMissionWorldData.get(world).getRepository().snapshot();
        boolean dirty = false;
        for (RDPMission mission : missions) {
            if (mission.isQueued() || terminal(mission.getState())
                    || mission.getDimension() != world.provider.getDimension()) continue;
            if (mission.isPaused()) {
                flight.clearGuidance(findAircraft(world, mission.getAircraftId()));
                continue;
            }
            if (mission.getState() != MissionState.HOLDING
                    && mission.getState() != MissionState.WAITING_FOR_FUEL
                    && tick - mission.getLastUpdateTick() > MISSION_TIMEOUT) {
                defer(world, mission, mission.getState().name() + "_TIMEOUT",
                        MissionState.EXECUTION_STALLED, tick);
                dirty = true;
                continue;
            }

            MCH_EntityAircraft aircraft = findAircraft(world, mission.getAircraftId());
            if (aircraft == null) {
                if (mission.getState() == MissionState.KAMIKADZE_TARGET) {
                    mission.markKamikazeDetonated(tick);
                    manager.release(mission);
                    dirty = true;
                    continue;
                }
                defer(world, mission, "AIRCRAFT_UNAVAILABLE", MissionState.WAITING_FOR_WORLD, tick);
                dirty = true;
                continue;
            }
            if (mission.getState() == MissionState.WAITING_FOR_FUEL
                    && flight.hasFuelForMission(aircraft)) {
                dirty = true;
                continue;
            }
            if (mission.getState() == MissionState.WAITING_FOR_WORLD
                    || mission.getState() == MissionState.WAITING_FOR_FUEL
                    || mission.getState() == MissionState.EXECUTION_STALLED
                    || mission.getState() == MissionState.RECOVERING
                    || mission.getState() == MissionState.REPLAN) {
                if (mission.getState() != MissionState.REPLAN) {
                    mission.transitionTo(MissionState.REPLAN, tick);
                }
                dirty = true;
            }
            if (flight.hasFuelForMission(aircraft)
                    && mission.getState() != MissionState.RETURNING
                    && mission.getState() != MissionState.LANDING
                    && mission.getState() != MissionState.PARKING) {
                // Low fuel is reported to the operator; autonomous mission
                // logic must never initiate RTB.  The player can explicitly
                // request native return through the GUI or command.
                defer(world, mission, "INSUFFICIENT_FUEL",
                        MissionState.WAITING_FOR_FUEL, tick);
                dirty = true;
                continue;
            }
            if (mission.isAttack() && mission.getState() == MissionState.TARGET_LOCKED
                    && !flight.hasAttackAmmunition(aircraft)
                    && !flight.hasSelfDestructWeaponWithAmmo(aircraft)) {
                fail(world, mission, "AMMUNITION_DEPLETED", tick);
                dirty = true;
                continue;
            }

            Target target = mission.getTargetSelectionMode() == TargetSelectionMode.POINT
                    ? resolvePointTarget(world, mission.getTargetSpec().getX(),
                    mission.getTargetSpec().getY(), mission.getTargetSpec().getZ())
                    : reacquire(world, aircraft, mission, tick);
            if (mission.getTargetSelectionMode() != TargetSelectionMode.POINT && target == null) {
                flight.clearGuidance(aircraft);
                if (mission.isAttack()) {
                    // Do not leave a stale native loiter route active while
                    // an entity lock is temporarily unavailable. REPLAN will
                    // rebuild the route when the same objective is available.
                    flight.release(aircraft);
                }
                if (mission.isAttack() && mission.getMissionType() != MissionType.SQUARE_STRIKE
                        && (mission.getState() == MissionState.ATTACK_TARGET
                        || mission.getState() == MissionState.TARGET_LOCKED
                        || mission.getState() == MissionState.APPROACHING
                        || mission.getState() == MissionState.KAMIKADZE_TARGET)) {
                    defer(world, mission, "TARGET_TEMPORARILY_UNAVAILABLE",
                            MissionState.REPLAN, tick);
                    dirty = true;
                    continue;
                }
                if (mission.getState() == MissionState.SEARCHING
                        || mission.getState() == MissionState.WAITING_FOR_TARGET) {
                    if (mission.getState() == MissionState.SEARCHING) {
                        mission.transitionTo(MissionState.WAITING_FOR_TARGET, tick);
                    }
                    RdpUavDebug.debug("Lifecycle",
                            "mission {} waiting for target reason=NO_CANDIDATE aircraft={}",
                            mission.getMissionId(), aircraft.getUniqueID());
                    dirty = true;
                    continue;
                }
                if (mission.getState() == MissionState.LOST) {
                    mission.transitionTo(MissionState.WAITING_FOR_TARGET, tick);
                    dirty = true;
                    continue;
                }
                if (mission.getState() == MissionState.REPLAN) {
                    RdpUavDebug.debug("Lifecycle",
                            "mission {} replan waiting for original target aircraft={}",
                            mission.getMissionId(), aircraft.getUniqueID());
                    dirty = true;
                    continue;
                }
                if (requiresTarget(mission.getState())) {
                    mission.clearTarget("TARGET_LOST_NO_CANDIDATE", tick);
                    mission.transitionTo(MissionState.LOST, tick);
                    RdpUavDebug.debug("Lifecycle",
                            "mission {} target lost reason=NO_CANDIDATE aircraft={}",
                            mission.getMissionId(), aircraft.getUniqueID());
                    dirty = true;
                    continue;
                }
            } else if (mission.getState() == MissionState.WAITING_FOR_TARGET
                    || mission.getState() == MissionState.LOST) {
                mission.transitionTo(MissionState.SEARCHING, tick);
                dirty = true;
            }
            if (target != null) mission.clearRecovery();
            if (mission.getTargetSelectionMode() != TargetSelectionMode.POINT && target == null
                    && requiresTarget(mission.getState())) {
                continue;
            }
            if (mission.getState() == MissionState.WAITING_FOR_TARGET
                    || mission.getState() == MissionState.LOST) {
                dirty = true;
                continue;
            }
            if (mission.getState() == MissionState.KAMIKADZE_TARGET) {
                boolean noseImpact = KamikazeImpactDetector.hasNoseImpact(aircraft);
                boolean speedReached = flight.isKamikazeSpeedReached(aircraft);
                boolean targetInRadius = target != null
                        && targetDistance(aircraft, target)
                        <= net.vas.rdp.uav.flight.KamikazeConfig.detonationRadius;
                boolean terminalGeometry = target != null
                        && terminalGeometrySatisfied(aircraft, target, mission);
                if (!speedReached || (!noseImpact && !targetInRadius) || !terminalGeometry) {
                    if (tick % 20L == 0L) {
                        RdpUavDebug.debug("Lifecycle",
                                "mission {} kamikaze approaching aircraft={} speedReached={} targetDistance={} target={}",
                                mission.getMissionId(), aircraft.getUniqueID(),
                                speedReached,
                                target == null ? null : targetDistance(aircraft, target),
                                formatTarget(target));
                    }
                    continue;
                }
                if (flight.fireSelfDestructWeapon(aircraft, true)) {
                    LOGGER.info("Mission {} kamikaze detonation aircraft={} impact={} inRadius={} target={} tick={}",
                            mission.getMissionId(), aircraft.getUniqueID(), noseImpact,
                            targetInRadius, formatTarget(target), tick);
                    mission.markKamikazeDetonated(tick);
                    flight.release(aircraft);
                    manager.release(mission);
                    dirty = true;
                    continue;
                }
                LOGGER.debug("Mission {} kamikaze trigger reached but weapon not ready aircraft={} impact={} inRadius={}",
                        mission.getMissionId(), aircraft.getUniqueID(), noseImpact, targetInRadius);
                // A collision can be detected while the weapon is still on
                // cooldown, or before the target is inside the detonation
                // radius. Keep the armed mission alive and retry next tick.
                if (tick % 20L == 0L) {
                    RdpUavDebug.debug("Lifecycle",
                            "mission {} kamikaze waiting aircraft={} targetDistance={} target={}",
                            mission.getMissionId(), aircraft.getUniqueID(),
                            targetDistance(aircraft, target),
                            formatTarget(target));
                }
                continue;
            }

            MissionState before = mission.getState();
            if (updateMission(world, mission, aircraft, target, tick)) dirty = true;
            if (before != mission.getState()) {
                LOGGER.info("Mission {} state {} -> {} aircraft={} wingman={}",
                        mission.getMissionId(), before, mission.getState(),
                        aircraft.getUniqueID(), describeWingman(flight.getEntry(aircraft)));
            } else if (tick % 20L == 0L) {
                LOGGER.debug("Mission {} state={} aircraft={} wingman={}",
                        mission.getMissionId(), mission.getState(), aircraft.getUniqueID(),
                        describeWingman(flight.getEntry(aircraft)));
            }
            if (terminal(mission.getState())) {
                flight.release(aircraft);
                manager.release(mission);
                routeRefreshes.remove(aircraft.getUniqueID());
                dirty = true;
            }
        }
        if (dirty) RDPMissionWorldData.get(world).markDirty();
    }

    private double targetDistance(MCH_EntityAircraft aircraft, Target target) {
        if (target.entity != null) return target.entity.getDistance(aircraft);
        double dx = aircraft.posX - target.x;
        double dy = aircraft.posY - target.y;
        double dz = aircraft.posZ - target.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private boolean updateMission(World world, RDPMission mission, MCH_EntityAircraft aircraft,
                                  Target target, long tick) {
        if (mission.isTerminal()) return false;
        if (mission.getState() == MissionState.REPLAN && target != null
                && isDirectNativeWeaponMission(mission, aircraft)) {
            launchDirectWeapon(world, mission, aircraft, target, tick);
            return true;
        }
        if (mission.getState() == MissionState.REPLAN && target != null) {
            flight.release(aircraft);
            double routeY = terminalRouteAltitude(aircraft, target, mission);
            if (!flight.dispatch(aircraft, target.x, routeY, target.z,
                    mission.getObservationDistance(), 200, mission.isAttack(),
                    target.entity == null ? null : target.entity.getUniqueID())) {
                defer(world, mission, "REPLAN_DISPATCH_REJECTED",
                        MissionState.EXECUTION_STALLED, tick);
                return true;
            }
            mission.clearRecovery();
            mission.transitionTo(MissionState.SEARCHING, tick);
            return true;
        }
        WingmanEntry entry = flight.getEntry(aircraft);
        if (entry == null) {
            RdpUavDebug.debug("Lifecycle", "mission {} WingMan entry missing age={}",
                    mission.getMissionId(), tick - mission.getCreatedTick());
            if (tick - mission.getCreatedTick() <= WINGMAN_ENTRY_GRACE_TICKS) {
                return false;
            }
            defer(world, mission, "WINGMAN_ENTRY_UNAVAILABLE",
                    MissionState.EXECUTION_STALLED, tick);
            return true;
        }
        if (target != null) {
            double targetGuidanceY = terminalRouteAltitude(aircraft, target, mission);
            if (target.entity != null) {
                flight.updateMovingEntityGuidance(aircraft, target.entity,
                        targetGuidanceY, tick);
            } else {
                flight.updateTargetGuidance(aircraft, target.x, targetGuidanceY, target.z, null);
            }
            if (entry.order != null) {
                entry.order.targetX = target.x;
                entry.order.targetZ = target.z;
            }
        }
        if (flight.isParked(aircraft) && mission.getState() != MissionState.RETURNING) {
            if (mission.isAttack() && profile(aircraft).isReusable()
                    && mission.getState() == MissionState.ATTACK_TARGET
                    && flight.isAttackComplete(aircraft)) {
                mission.transitionTo(MissionState.APPROACHING, tick);
                mission.transitionTo(MissionState.ON_TARGET, tick);
                mission.transitionTo(MissionState.SCANNING, tick);
                return true;
            }
            if (mission.isAttack() && !profile(aircraft).isReusable() && target != null) {
                if (withinMissionExecutionRange(aircraft, target, mission)
                        && terminalGeometrySatisfied(aircraft, target, mission)
                        && flight.fireSelfDestructWeapon(aircraft)) {
                    if (armKamikazeTerminalState(mission, target, tick)) {
                        fail(world, mission, "KAMIKAZE_TERMINAL_STATE_REJECTED", tick);
                        return true;
                    }
                    mission.markKamikazeDetonated(tick);
                    manager.release(mission);
                    return true;
                }
                flight.release(aircraft);
                double routeY = terminalRouteAltitude(aircraft, target, mission);
                if (flight.dispatch(aircraft, target.x, routeY, target.z,
                        mission.getObservationDistance(), 0, true,
                        target.entity == null ? null : target.entity.getUniqueID())) {
                    mission.transitionTo(MissionState.REPLAN, tick);
                    return true;
                }
            }
            if (mission.isTerminal()) return false;
            defer(world, mission, "WINGMAN_TERMINATED_BEFORE_RETURN",
                    MissionState.EXECUTION_STALLED, tick);
            return true;
        }
        if (flight.isReturning(aircraft) && mission.getState() != MissionState.RETURNING) {
            if (mission.getState() == MissionState.ATTACK_TARGET
                    || mission.getState() == MissionState.REPORTING) {
                mission.transitionTo(MissionState.RETURNING, tick);
            } else {
                defer(world, mission, "WINGMAN_RTB_BEFORE_REPORT",
                        MissionState.RECOVERING, tick);
            }
            return true;
        }

        switch (mission.getState()) {
        case SEARCHING:
            UavVehicleProfile vehicleProfile = profile(aircraft);
            if (vehicleProfile.supportsLongRangeLock()) {
                assert target != null;
                if (target.entity != null
                        && targetDistance(aircraft, target) <= vehicleProfile.getLockRange()) {
                    mission.transitionTo(MissionState.TARGET_ACQUIRED, tick);
                    mission.lockTarget(target.entity.getUniqueID(), tick);
                    return true;
                }
            }
            if (!atTarget(aircraft, target, mission)
                    || !flight.hasReachedRoutePoint(aircraft)) return false;
            mission.transitionTo(MissionState.TARGET_ACQUIRED, tick);
            if (target.entity == null) mission.lockPoint(tick);
            else mission.lockTarget(target.entity.getUniqueID(), tick);
            return true;
        case TARGET_LOCKED:
                MissionSafety.RouteDecision approach = evaluateApproach(world, aircraft, target, mission);
                if (approach == MissionSafety.RouteDecision.HOLD_AND_REEVALUATE) {
                    RdpUavDebug.debug("Lifecycle",
                            "mission {} hold-and-reevaluate aircraft={} target={} route={} distance={}",
                            mission.getMissionId(), aircraft.getUniqueID(), formatTarget(target),
                            approach, target == null ? -1.0D : targetDistance(aircraft, target));
                    return false;
                }
                refreshRoute(aircraft, target, mission, approach, tick);
                if (!flight.hasReachedRoutePoint(aircraft)) return false;
                if (!withinMissionExecutionRange(aircraft, target, mission)) {
                    return false;
                }
                if (approach != MissionSafety.RouteDecision.DIRECT_INTERCEPT) {
                    mission.transitionTo(MissionState.APPROACHING, tick);
                    return true;
                }
                UUID pilotTarget = KamikazePilotOverride.targetFor(aircraft.getUniqueID());
                if (mission.isAttack() && flight.hasSelfDestructWeaponWithAmmo(aircraft) && (!profile(aircraft).isReusable() || mission.isDestructiveAuthorized()) && (pilotTarget == null || pilotTarget.equals(
                        target.entity == null ? null : target.entity.getUniqueID()))) {
                    if (flight.armKamikaze(aircraft, target.x, target.y, target.z,
                            mission.getObservationDistance(),
                            target.entity == null ? null : target.entity.getUniqueID())) {
                        fail(world, mission, "AI_KAMIKAZE_ARM_REJECTED", tick);
                    } else {
                        mission.transitionTo(MissionState.KAMIKADZE_TARGET, tick);
                    }
                    return true;
                }
                if (mission.isAttack()) {
                    GuidedStrikeObjective guidedObjective = target.entity == null
                            ? GuidedStrikeObjective.square(mission.getMissionId(),
                            world.provider.getDimension(), target.x, target.y, target.z, tick)
                            : GuidedStrikeObjective.entity(mission.getMissionId(),
                            target.entity.getUniqueID(), world.provider.getDimension(),
                            target.x, target.y, target.z, tick);
                    guidedObjective.authorize();
                    guidedObjective.beginTracking();
                    boolean directReusableLaunch = profile(aircraft).isReusable();
                    GuidedWeaponDecision guided = directReusableLaunch
                            ? guidedWeapons.armDirect(world, aircraft, guidedObjective,
                            aircraftSpeed(aircraft), profile(aircraft).getAttackEnvelope(), true,
                            weaponPriority(mission, aircraft))
                            : guidedWeapons.arm(world, aircraft, guidedObjective,
                            aircraftSpeed(aircraft), profile(aircraft).getAttackEnvelope(), true,
                            weaponPriority(mission, aircraft));
                    if (guided.isAccepted()) {
                        guidedObjective.release();
                        mission.transitionTo(MissionState.ATTACK_TARGET, tick);
                        return true;
                    }
                    if (!"NO_GUIDED_WEAPON".equals(guided.getReason())) {
                        RdpUavDebug.debug("Lifecycle",
                                "mission {} guided attack decision={} aircraft={} target={}",
                                mission.getMissionId(), guided.getReason(), aircraft.getUniqueID(),
                                formatTarget(target));
                        if (guided.getReason().startsWith("GUIDED_HOLD_")
                                || guided.getReason().startsWith("GUIDED_WAIT_")) {
                            defer(world, mission, guided.getReason(),
                                    guidanceRecoveryState(guided.getReason()), tick);
                            return true;
                        }
                        if (isPermanentGuidanceFailure(guided.getReason())) {
                            fail(world, mission, guided.getReason(), tick);
                            return true;
                        }
                        defer(world, mission, guided.getReason(),
                                guidanceRecoveryState(guided.getReason()), tick);
                        return true;
                    }
                    if (profile(aircraft).isReusable()
                            && mission.getMissionType() == MissionType.SQUARE_STRIKE) {
                        fail(world, mission, "REUSABLE_UAV_GUIDED_WEAPON_REQUIRED", tick);
                        return true;
                    }
                    if (!profile(aircraft).isReusable()
                            && mission.getMissionType() == MissionType.SQUARE_STRIKE) {
                        fail(world, mission, "ONE_WAY_SQUARE_TERMINAL_PAYLOAD_REQUIRED", tick);
                        return true;
                    }
                    if (!flight.armAttack(aircraft, target.x, target.y, target.z,
                            profile(aircraft).getAttackEnvelope(), mission.getTargetId(),
                            weaponPriority(mission, aircraft))) {
                        defer(world, mission, "WINGMAN_ATTACK_ARM_REJECTED",
                                MissionState.EXECUTION_STALLED, tick);
                    } else {
                        mission.transitionTo(MissionState.ATTACK_TARGET, tick);
                    }
                    return true;
                }
                if (withinMissionExecutionRange(aircraft, target, mission)) {
                    mission.transitionTo(MissionState.APPROACHING, tick);
                    mission.transitionTo(MissionState.ON_TARGET, tick);
                    mission.transitionTo(MissionState.SCANNING, tick);
                    return true;
                }
                return false;
            case ATTACK_TARGET:
                // No damage or self-destruct result is inferred.  WingMan's
                // attack node must complete before scan/report/RTB can begin.
                if (flight.isReturning(aircraft)) {
                    mission.transitionTo(MissionState.RETURNING, tick);
                    return true;
                }
                if (flight.isAttackComplete(aircraft)) {
                    if (mission.getMissionType() == MissionType.SQUARE_STRIKE
                            && !profile(aircraft).isReusable()) {
                        if ("SELF_DESTRUCT".equalsIgnoreCase(
                                profile(aircraft).getTerminalPayloadBehavior())
                                && terminalGeometrySatisfied(aircraft, target, mission)
                                && flight.fireSelfDestructWeapon(aircraft)) {
                            if (armKamikazeTerminalState(mission, target, tick)) {
                                fail(world, mission, "KAMIKAZE_TERMINAL_STATE_REJECTED", tick);
                                return true;
                            }
                            mission.markKamikazeDetonated(tick);
                            flight.release(aircraft);
                            manager.release(mission);
                            return true;
                        }

                        fail(world, mission, "ONE_WAY_SQUARE_TERMINAL_PAYLOAD_UNAVAILABLE", tick);
                        return true;
                    }
                    mission.transitionTo(MissionState.APPROACHING, tick);
                    mission.transitionTo(MissionState.ON_TARGET, tick);
                    mission.transitionTo(MissionState.SCANNING, tick);
                    return true;
                }
                return false;
            case KAMIKADZE_TARGET:
                if (flight.isReturning(aircraft)) {
                    mission.transitionTo(MissionState.RETURNING, tick);
                    return true;
                }
                if (flight.isAttackComplete(aircraft)) {
                    mission.markKamikazeDetonated(tick);
                    return true;
                }
                return false;
            case APPROACHING:
                MissionSafety.RouteDecision approachRoute =
                        evaluateApproach(world, aircraft, target, mission);
                if (approachRoute == MissionSafety.RouteDecision.HOLD_AND_REEVALUATE) return false;
                refreshRoute(aircraft, target, mission, approachRoute, tick);
                if (atTarget(aircraft, target, mission)
                        && flight.hasReachedRoutePoint(aircraft)) {
                    mission.transitionTo(MissionState.ON_TARGET, tick);
                    if (approachRoute == MissionSafety.RouteDecision.OFFSET_ORBIT
                            || approachRoute == MissionSafety.RouteDecision.SAFE_SIDE_APPROACH) {
                        mission.transitionTo(MissionState.ORBITING, tick);
                    } else {
                        mission.transitionTo(MissionState.SCANNING, tick);
                    }
                    return true;
                }
                return false;
            case ON_TARGET:
            case ORBITING:
                if (!flight.hasReachedRoutePoint(aircraft)) return false;
                mission.transitionTo(MissionState.SCANNING, tick);
                return true;
            case SCANNING:
                if (mission.isDeepScan()) {
                    mission.transitionTo(MissionState.DEEP_SCANNING, tick);
                } else {
                    persistReport(world, mission, target, tick);
                    mission.transitionTo(MissionState.REPORTING, tick);
                }
                return true;
            case DEEP_SCANNING:
                persistReport(world, mission, target, tick);
                mission.transitionTo(MissionState.REPORTING, tick);
                return true;
            case REPORTING:
                // Reporting ends the task but does not silently land or RTB.
                // The operator must explicitly request return-to-base.
                mission.transitionTo(MissionState.HOLDING, tick);
                return true;
            case HOLDING:
                return false;
            case RETURNING:
                if (flight.isParked(aircraft)) {
                    mission.transitionTo(MissionState.LANDING, tick);
                    mission.transitionTo(MissionState.PARKING, tick);
                    mission.transitionTo(MissionState.COMPLETE, tick);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private boolean armKamikazeTerminalState(RDPMission mission, Target target, long tick) {
        if (mission == null || target == null) return true;
        switch (mission.getState()) {
        case SEARCHING:
            mission.transitionTo(MissionState.TARGET_ACQUIRED, tick);
            if (target.entity == null) mission.lockPoint(tick);
            else mission.lockTarget(target.entity.getUniqueID(), tick);
            mission.transitionTo(MissionState.KAMIKADZE_TARGET, tick);
            return false;
        case TARGET_ACQUIRED:
            if (target.entity == null) mission.lockPoint(tick);
            else mission.lockTarget(target.entity.getUniqueID(), tick);
            mission.transitionTo(MissionState.KAMIKADZE_TARGET, tick);
            return false;
        case TARGET_LOCKED:
            mission.transitionTo(MissionState.KAMIKADZE_TARGET, tick);
            return false;
        case ATTACK_TARGET:
            mission.transitionTo(MissionState.KAMIKADZE_TARGET, tick);
            return false;
        case KAMIKADZE_TARGET:
            return false;
        default:
            return true;
        }
    }

    private void persistReport(World world, RDPMission mission, Target target, long tick) {
        if (mission.getReportId() != null || target == null) return;
        UUID targetId = mission.getTargetId() == null ? mission.getMissionId() : mission.getTargetId();
        net.minecraft.util.ResourceLocation key = target.entity == null
                ? null : EntityList.getKey(target.entity);
        String registryId = key == null ? "rdpuav:unknown" : key.toString();
        String reportId = mission.getMissionId().toString();
        RDPScanReport report = new RDPScanReport(reportId, mission.getMissionId(), targetId,
                registryId, world.provider.getDimension(), target.x, target.y, target.z,
                mission.isDeepScan(), mission.getTargetPriority(),
                Math.max(0L, tick - mission.getCreatedTick()), tick);
        RDPScanReportRepository.get(world).put(report);
        mission.setReportId(reportId);
    }

    private Target reacquire(World world, MCH_EntityAircraft aircraft, RDPMission mission, long tick) {
        Entity current = targets.findLoaded(world, mission.getTargetId());
        UavVehicleProfile vehicleProfile = profile(aircraft);
        if (current != null && targetDistance(aircraft, Target.entity(current))
                <= vehicleProfile.getLockRange()) {
            contactTracker.observe(world, current, targetDistance(aircraft, Target.entity(current)),
                    tick, ContactLifecycle.TRACKING);
            return Target.entity(current);
        }
        if (mission.getTargetId() != null) {
            contactTracker.markLost(mission.getTargetId(), tick);
            // A tracked identity may be reacquired, but it must never be
            // replaced by an unrelated nearby entity after disappearing.
            return null;
        }
        List<TargetResolver.EntityCandidate> candidates = targets.find(world, aircraft.posX,
                aircraft.posY, aircraft.posZ,
                vehicleProfile.getDetectionRange(),
                mission.getTargetSpec());
        if (candidates.isEmpty()) return null;
        Entity reacquired = candidates.get(0).getEntity();
        contactTracker.observe(world, reacquired,
                Math.sqrt(candidates.get(0).getDistanceSquared()), tick, ContactLifecycle.SELECTABLE);
        if (mission.getTargetId() == null) mission.reacquireTarget(reacquired.getUniqueID(), tick);
        else if (!mission.getTargetId().equals(reacquired.getUniqueID())) {
            // Manual and automatic searches may recover a moved entity, but
            // never silently substitute another entity after a lock.
            if (mission.getTargetSelectionMode() == TargetSelectionMode.MANUAL) return null;
            mission.reacquireTarget(reacquired.getUniqueID(), tick);
        }
        return Target.entity(reacquired);
    }

    private Target locateTarget(World world, MCH_EntityAircraft aircraft, RDPMission mission,
                                boolean allowAcquire) {
        if (mission.getTargetSelectionMode() == TargetSelectionMode.POINT) {
            return resolvePointTarget(world, mission.getTargetSpec().getX(),
                    mission.getTargetSpec().getY(), mission.getTargetSpec().getZ());
        }
        if (!allowAcquire) return null;
        Entity current = targets.findLoaded(world, mission.getTargetId());
        if (current == null && mission.getTargetSelectionMode() == TargetSelectionMode.MANUAL) {
            current = targets.findLoadedByRegistryId(world, mission.getTargetSpec().getRegistryId());
        }
        if (current != null && (mission.getTargetSelectionMode() == TargetSelectionMode.MANUAL
                || aircraft.getDistanceSq(current) <=
                profile(aircraft).getDetectionRange() * profile(aircraft).getDetectionRange())) {
            contactTracker.observe(world, current, aircraft.getDistance(current),
                    world.getTotalWorldTime(), ContactLifecycle.SELECTED);
            return Target.entity(current);
        }
        if (current != null) return null;
        List<TargetResolver.EntityCandidate> candidates = targets.find(world, aircraft.posX,
                aircraft.posY, aircraft.posZ,
                profile(aircraft).getDetectionRange(),
                mission.getTargetSpec());
        if (candidates.isEmpty()) return null;
        Entity selected = candidates.get(0).getEntity();
        contactTracker.observe(world, selected,
                Math.sqrt(candidates.get(0).getDistanceSquared()), world.getTotalWorldTime(),
                ContactLifecycle.SELECTABLE);
        return Target.entity(selected);
    }

    public ContactSnapshot contact(UUID targetId) {
        return contactTracker.get(targetId);
    }

    public ContactSnapshot operatorContact(MCH_EntityAircraft aircraft) {
        return aircraft == null ? null : contactTracker.get(operatorContacts.get(aircraft.getUniqueID()));
    }

    public boolean cycleContact(World world, MCH_EntityAircraft aircraft, boolean next) {
        return cycleContact(world, aircraft, null, next);
    }

    public boolean cycleContact(World world, MCH_EntityAircraft aircraft, EntityPlayer operator,
                                boolean next) {
        if (world == null || aircraft == null) return false;
        for (RDPMission mission : RDPMissionWorldData.get(world).getRepository().snapshot()) {
            if (!mission.getAircraftId().equals(aircraft.getUniqueID()) || mission.isTerminal()) continue;
            UUID current = operatorContacts.get(aircraft.getUniqueID());
            List<ContactSnapshot> available = new java.util.ArrayList<>(
                    contactTracker.selectable());
            if (available.isEmpty()) return false;
            int start = -1;
            for (int i = 0; i < available.size(); i++) {
                if (available.get(i).getEntityId().equals(current)) { start = i; break; }
            }
            int offset = next ? 1 : available.size() - 1;
            for (int i = 0; i < available.size(); i++) {
                ContactSnapshot candidate = available.get((start < 0 ? 0 : start + offset + i)
                        % available.size());
                Entity entity = targets.findLoaded(world, candidate.getEntityId());
                if (entity != null && net.vas.rdp.uav.target.TargetPolicy
                        .isSelectableContact(world, entity, operator)) {
                    contactTracker.select(candidate.getEntityId());
                    operatorContacts.put(aircraft.getUniqueID(), candidate.getEntityId());
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    public boolean clearContact(World world, MCH_EntityAircraft aircraft) {
        if (world == null || aircraft == null) return false;
        for (RDPMission mission : RDPMissionWorldData.get(world).getRepository().snapshot()) {
            if (mission.getAircraftId().equals(aircraft.getUniqueID()) && !mission.isTerminal()) {
                operatorContacts.remove(aircraft.getUniqueID());
                return true;
            }
        }
        return false;
    }

    private MissionSafety.RouteDecision evaluateApproach(World world, MCH_EntityAircraft aircraft,
                                                        Target target, RDPMission mission) {
        if (world == null || aircraft == null || target == null) {
            return MissionSafety.RouteDecision.HOLD_AND_REEVALUATE;
        }
        /*
         * One-way platforms are terminal strike vehicles, not observation
         * aircraft. Never let corridor safety convert their terminal route
         * into an offset orbit or a reusable-UAV hold.
         */
        if (mission.isAttack() && !profile(aircraft).isReusable()) {
            return MissionSafety.RouteDecision.DIRECT_INTERCEPT;
        }

        double dx = target.x - aircraft.posX;
        double dy = target.y - aircraft.posY;
        double dz = target.z - aircraft.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= 1.0D) {
            return MissionSafety.RouteDecision.HOLD_AND_REEVALUATE;
        }

        double altitudeDelta = Math.abs(dy);
        double observationDistance = mission.getObservationDistance();
        Vec3d start = new Vec3d(aircraft.posX, aircraft.posY + aircraft.getEyeHeight(), aircraft.posZ);
        Vec3d end = new Vec3d(target.x, target.y + 1.0D, target.z);
        RayTraceResult hit = world.rayTraceBlocks(start, end, false, true, false);
        boolean corridorBlocked = hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK;
        return MissionSafety.decideRoute(distance, altitudeDelta, corridorBlocked, observationDistance);
    }

    private void refreshRoute(MCH_EntityAircraft aircraft, Target target, RDPMission mission,
                              MissionSafety.RouteDecision decision, long tick) {
        if (target == null || decision == MissionSafety.RouteDecision.HOLD_AND_REEVALUATE) return;
        UUID aircraftId = aircraft.getUniqueID();
        RouteRefresh previous = routeRefreshes.get(aircraftId);
        boolean orbit = !mission.isAttack()
                && (decision == MissionSafety.RouteDecision.OFFSET_ORBIT
                || decision == MissionSafety.RouteDecision.SAFE_SIDE_APPROACH);
        boolean moved = previous == null
                || previous.distanceSquared(target.x, target.y, target.z) >= 16.0D;
        boolean due = previous == null
                || (tick - previous.tick >= 10L && flight.hasReachedRoutePoint(aircraft));
        if (!moved && !due) return;
        double guidanceY = terminalRouteAltitude(aircraft, target, mission);
        if (flight.refreshObservationRoute(aircraft, target.x, guidanceY, target.z,
                mission.getObservationDistance(), orbit, mission.getTargetId())) {
            routeRefreshes.put(aircraftId, new RouteRefresh(tick, target.x, guidanceY, target.z));
        }
    }

    private boolean atTarget(MCH_EntityAircraft aircraft, Target target, RDPMission mission) {
        if (target == null) return false;
        UavVehicleProfile profile = profile(aircraft);
        double dx = aircraft.posX - target.x;
        double dz = aircraft.posZ - target.z;
        if (profile.isReusable()) {
            return dx * dx + dz * dz
                    <= mission.getObservationDistance() * mission.getObservationDistance()
                    && Math.abs(aircraft.posY - profile.getExecutionAltitude())
                    <= profile.getAltitudeTolerance();
        }
        double dy = aircraft.posY - target.y;
        return dx * dx + dy * dy + dz * dz
                <= mission.getObservationDistance() * mission.getObservationDistance();
    }

    private boolean withinMissionExecutionRange(MCH_EntityAircraft aircraft, Target target,
                                                 RDPMission mission) {
        if (target == null) return false;
        UavVehicleProfile profile = profile(aircraft);
        double dx = aircraft.posX - target.x;
        double dz = aircraft.posZ - target.z;
        if (profile.isReusable()) {
            return dx * dx + dz * dz
                    <= profile.getMissionExecutionRange() * profile.getMissionExecutionRange();
        }
        return targetDistance(aircraft, target) <= profile.getMissionExecutionRange();
    }

    private boolean terminalGeometrySatisfied(MCH_EntityAircraft aircraft, Target target,
                                               RDPMission mission) {
        if (aircraft == null || target == null || mission == null) return false;
        if (profile(aircraft).isReusable()) return true;
        double dx = aircraft.posX - target.x;
        double dz = aircraft.posZ - target.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double vertical = Math.abs(aircraft.posY - target.y);
        // A point strike is terminal only at the resolved surface plane. The
        // old 3D-radius-only gate allowed a plane to detonate while hovering
        // tens of blocks above the square.
        return horizontal <= Math.max(4.0D, mission.getObservationDistance())
                && vertical <= 3.0D;
    }

    private double guidanceAltitude(MCH_EntityAircraft aircraft, double targetY) {
        UavVehicleProfile profile = profile(aircraft);
        if (!profile.isReusable()) {
            // FPV helicopters need a small, bounded standoff height before
            // their terminal dive.  Sending the raw target Y to native
            // WingMan makes a ground target look like an unlimited descent/
            // climb correction for helicopter flight models.
            if (aircraft instanceof MCH_EntityHeli) {
                return Math.max(targetY + 8.0D, profile.getMinimumExecutionAltitude());
            }

            return targetY;
        }
        return Math.max(profile.getMinimumExecutionAltitude(),
                Math.min(profile.getMaximumExecutionAltitude(), profile.getExecutionAltitude()));
    }

    private double terminalRouteAltitude(MCH_EntityAircraft aircraft, Target target,
                                         RDPMission mission) {
        return guidanceAltitude(aircraft, target.y);
    }

    private Target resolvePointTarget(World world, double x, double requestedY, double z) {
        if (world == null) return Target.point(x, requestedY, z);
        net.minecraft.util.math.BlockPos column =
                new net.minecraft.util.math.BlockPos(x, 0, z);
        int surfaceY = world.getHeight(column).getY();
        double resolvedY = Math.max(requestedY, surfaceY + 1.0D
                + net.vas.rdp.uav.flight.SquareStrikeConfig.altitudeOffset);
        return Target.point(x, resolvedY, z);
    }

    private double aircraftSpeed(MCH_EntityAircraft aircraft) {
        return Math.sqrt(aircraft.motionX * aircraft.motionX
                + aircraft.motionY * aircraft.motionY + aircraft.motionZ * aircraft.motionZ);
    }

    static boolean isPermanentGuidanceFailure(String reason) {
        if (reason == null) return false;
        return "NO_GUIDED_WEAPON".equals(reason)
                || "UNSUPPORTED_GUIDANCE".equals(reason)
                || "UNSUPPORTED_OBJECTIVE_COMBINATION".equals(reason)
                || "AI_TV_GUIDANCE_UNSUPPORTED".equals(reason)
                || "UNSUPPORTED_SEEKER_TARGET_ASSIGNMENT".equals(reason)
                || "NATIVE_SEEKER_LAUNCH_REJECTED".equals(reason)
                || "NATIVE_WINGMAN_REJECTED".equals(reason)
                || "NO_AMMUNITION".equals(reason)
                || "REUSABLE_UAV_GUIDED_WEAPON_REQUIRED".equals(reason)
                || "ONE_WAY_SQUARE_TERMINAL_PAYLOAD_REQUIRED".equals(reason);
    }

    private UavVehicleProfile profile(MCH_EntityAircraft aircraft) {
        return UavVehicleProfiles.forAircraft(aircraft == null || aircraft.getAcInfo() == null
                ? null : aircraft.getAcInfo().name);
    }

    private MCH_EntityAircraft findAircraft(World world, UUID id) {
        if (id == null) return null;
        MCH_EntityAircraft aircraft = aircraftThisTick.get(id);
        if (aircraft != null && !aircraft.isDead
                && aircraft.dimension == world.provider.getDimension()) {
            return aircraft;
        }
        return null;
    }

    private Map<UUID, MCH_EntityAircraft> indexAircraft(World world) {
        Map<UUID, MCH_EntityAircraft> result = new HashMap<>();
        for (Entity entity : world.loadedEntityList) {
            // Piloted aircraft are valid mission platforms for native guided
            // weapon release even though WingMan itself only controls UAVs.
            // Filtering this index to isUAV() made aircraft such as the A-10
            // appear unavailable immediately after a weapon mission was
            // created.
            if (entity instanceof MCH_EntityAircraft && !entity.isDead
                    && entity.dimension == world.provider.getDimension()) {
                result.put(entity.getUniqueID(), (MCH_EntityAircraft) entity);
            }
        }
        return result;
    }

    private boolean isDirectNativeWeaponMission(RDPMission mission,
                                                 MCH_EntityAircraft aircraft) {
        return mission.isAttack() && aircraft != null && !aircraft.isUAV()
                && profile(aircraft).getExecutionProfile()
                != net.vas.rdp.uav.profile.UavExecutionProfile.ONE_WAY;
    }

    private String weaponPriority(RDPMission mission, MCH_EntityAircraft aircraft) {
        if (mission != null && mission.getGuidedWeapon() != null
                && !mission.getGuidedWeapon().trim().isEmpty()) {
            return mission.getGuidedWeapon();
        }
        return profile(aircraft).getAttackWeaponPriority();
    }

    private void launchDirectWeapon(World world, RDPMission mission,
                                    MCH_EntityAircraft aircraft, Target target, long tick) {
        GuidedStrikeObjective objective = target.entity == null
                ? GuidedStrikeObjective.square(mission.getMissionId(),
                world.provider.getDimension(), target.x, target.y, target.z, tick)
                : GuidedStrikeObjective.entity(mission.getMissionId(),
                target.entity.getUniqueID(), world.provider.getDimension(),
                target.x, target.y, target.z, tick);
        objective.authorize();
        objective.beginTracking();
        GuidedWeaponDecision decision = guidedWeapons.arm(world, aircraft, objective,
                aircraftSpeed(aircraft), profile(aircraft).getAttackEnvelope(), true,
                weaponPriority(mission, aircraft));
        if (!decision.isAccepted()) {
            RdpUavDebug.debug("Lifecycle",
                    "mission {} direct guided launch decision={} aircraft={} target={}",
                    mission.getMissionId(), decision.getReason(), aircraft.getUniqueID(),
                    formatTarget(target));
            if (isPermanentGuidanceFailure(decision.getReason())) {
                fail(world, mission, decision.getReason(), tick);
            } else if (!decision.getReason().startsWith("GUIDED_HOLD_")
                    && !decision.getReason().startsWith("GUIDED_WAIT_")) {
                defer(world, mission, decision.getReason(),
                        guidanceRecoveryState(decision.getReason()), tick);
            }
            return;
        }
        objective.release();
        mission.transitionTo(MissionState.TARGET_LOCKED, tick);
        mission.transitionTo(MissionState.DIRECT_LAUNCH_COMPLETE, tick);
        manager.release(mission);
        RDPMissionWorldData.get(world).markDirty();
    }

    private void fail(World world, RDPMission mission, String reason, long tick) {
        if (!terminal(mission.getState())) {
            MissionState previousState = mission.getState();
            mission.fail(reason, tick);
            LOGGER.warn("Mission {} FAILED reason={} previousState={} newState={} aircraft={} dimension={} tick={} age={}",
                    mission.getMissionId(), reason, previousState, mission.getState(),
                    mission.getAircraftId(), mission.getDimension(), tick,
                    Math.max(0L, tick - mission.getCreatedTick()));
            RdpUavDebug.event("Lifecycle", "mission_failed",
                    "mission", mission.getMissionId(),
                    "previousState", previousState,
                    "newState", mission.getState(),
                    "reason", reason,
                    "aircraft", mission.getAircraftId());
            MCH_EntityAircraft aircraft = findAircraft(world, mission.getAircraftId());
            if (aircraft != null) flight.release(aircraft);
            routeRefreshes.remove(mission.getAircraftId());
            manager.release(mission);
            RDPMissionWorldData.get(world).markDirty();
        }
    }

    private void defer(World world, RDPMission mission, String reason,
                       MissionState recoveryState, long tick) {
        if (mission == null || mission.isTerminal()) return;
        boolean retained = mission.defer(reason, recoveryState, tick);
        if (!retained) {
            MCH_EntityAircraft aircraft = findAircraft(world, mission.getAircraftId());
            if (aircraft != null) flight.release(aircraft);
            routeRefreshes.remove(mission.getAircraftId());
            manager.release(mission);
            RDPMissionWorldData.get(world).markDirty();
        } else {
            RdpUavDebug.debug("Lifecycle", "mission {} deferred state={} reason={} attempt={}",
                    mission.getMissionId(), recoveryState, reason, mission.getRecoveryAttempts());
            RDPMissionWorldData.get(world).markDirty();
        }
    }

    private String formatTarget(Target target) {
        return target == null ? "none" : target.entity == null
                ? String.format(java.util.Locale.ROOT, "point(%.1f,%.1f,%.1f)",
                target.x, target.y, target.z)
                : target.entity.getUniqueID() + "/" + EntityList.getKey(target.entity);
    }

    private MissionState guidanceRecoveryState(String reason) {
        if ("GUIDED_WAIT_TARGET_CHUNK".equals(reason)
                || "GUIDED_HOLD_CHUNK_UNLOADED".equals(reason)
                || "GUIDED_REJECT_DIMENSION".equals(reason)) {
            return MissionState.WAITING_FOR_WORLD;
        }
        return MissionState.REPLAN;
    }

    private String describeWingman(WingmanEntry entry) {
        if (entry == null) return "missing";
        MissionNodeView node = MissionNodeView.from(entry);
        return entry.autoState + " node=" + entry.missionIndex + "/" + node.size
                + " current=" + node.current;
    }

    private static final class MissionNodeView {
        private final int size;
        private final String current;

        private MissionNodeView(int size, String current) {
            this.size = size;
            this.current = current;
        }

        private static MissionNodeView from(WingmanEntry entry) {
            if (entry.mission == null) return new MissionNodeView(0, "none");
            com.norwood.mcheli.wingman.mission.MissionNode node = null;
            if (entry.missionIndex >= 0 && entry.missionIndex < entry.mission.size()) {
                node = entry.mission.get(entry.missionIndex);
            }
            return new MissionNodeView(entry.mission.size(), node == null ? "none" : node.type.name());
        }
    }

    private boolean requiresTarget(MissionState state) {
        return MissionSafety.requiresTarget(state);
    }

    private boolean terminal(MissionState state) {
        return state == MissionState.COMPLETE || state == MissionState.ABORTED
                || state == MissionState.FAILED || state == MissionState.KAMIKADZE_DETONATED;
    }

    private static final class Target {
        private final Entity entity;
        private final double x;
        private final double y;
        private final double z;

        private Target(Entity entity, double x, double y, double z) {
            this.entity = entity;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static Target entity(Entity entity) {
            return new Target(entity, entity.posX, entity.posY, entity.posZ);
        }

        private static Target point(double x, double y, double z) {
            return new Target(null, x, y, z);
        }
    }

    private static final class RouteRefresh {
        private final long tick;
        private final double x;
        private final double y;
        private final double z;

        private RouteRefresh(long tick, double x, double y, double z) {
            this.tick = tick;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private double distanceSquared(double x, double y, double z) {
            double dx = this.x - x;
            double dy = this.y - y;
            double dz = this.z - z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
