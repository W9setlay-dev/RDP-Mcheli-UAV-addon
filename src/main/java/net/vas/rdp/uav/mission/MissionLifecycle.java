package net.vas.rdp.uav.mission;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.wingman.wingman.WingmanEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.world.World;
import net.vas.rdp.uav.flight.WingManFlightAdapter;
import net.vas.rdp.uav.persistence.RDPMissionWorldData;
import net.vas.rdp.uav.scan.RDPScanReport;
import net.vas.rdp.uav.scan.RDPScanReportRepository;
import net.vas.rdp.uav.target.TargetResolver;
import net.vas.rdp.uav.target.TargetSelectionMode;
import net.vas.rdp.uav.flight.KamikazeImpactDetector;
import net.vas.rdp.uav.flight.KamikazePilotOverride;

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
    private static final long QUEUE_WAIT_TIMEOUT = 600L;
    private static final long MISSION_TIMEOUT = 12000L;
    private static final long WINGMAN_ENTRY_GRACE_TICKS = 20L;

    private final MissionManager manager;
    private final WingManFlightAdapter flight = new WingManFlightAdapter();
    private final TargetResolver targets = new TargetResolver();
    private final Map<World, Boolean> restoredWorlds = new WeakHashMap<World, Boolean>();
    private Map<UUID, MCH_EntityAircraft> aircraftThisTick = new HashMap<UUID, MCH_EntityAircraft>();

    public MissionLifecycle(MissionManager manager) {
        if (manager == null) throw new IllegalArgumentException("Mission manager is required");
        this.manager = manager;
    }

    public void tick(World world) {
        if (world == null || world.isRemote) return;
        RdpUavDebug.debug("Lifecycle", "tick dimension={} time={} queued={}",
                world.provider.getDimension(), world.getTotalWorldTime(), manager.queuedMissionCount());
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
            if (mission.getDimension() != world.provider.getDimension()) {
                RdpUavDebug.debug("Lifecycle", "mission {} deferred for dimension {} (current {})",
                        mission.getMissionId(), mission.getDimension(), world.provider.getDimension());
                manager.enqueueQueuedMission(mission);
                continue;
            }
            dispatch(world, mission);
        }
    }

    private void dispatch(World world, RDPMission mission) {
        long tick = world.getTotalWorldTime();
        MCH_EntityAircraft aircraft = findAircraft(world, mission.getAircraftId());
        if (aircraft == null) {
            RdpUavDebug.debug("Lifecycle", "mission {} failed: aircraft not found {}",
                    mission.getMissionId(), mission.getAircraftId());
            fail(world, mission, "AIRCRAFT_NOT_FOUND", tick);
            return;
        }
        if (aircraft.getAcInfo() == null) {
            fail(world, mission, "AIRCRAFT_METADATA_UNAVAILABLE", tick);
            return;
        }
        // MissionManager.create already assigned this aircraft. Re-registering
        // it here would replace the ON_MISSION record with a fresh READY
        // record and allow a second mission to claim the same entity.
        if (!flight.hasFuelForMission(aircraft)) {
            fail(world, mission, "INSUFFICIENT_FUEL", tick);
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
            if (tick - mission.getCreatedTick() > QUEUE_WAIT_TIMEOUT) {
                fail(world, mission, "TARGET_NOT_FOUND", tick);
            } else {
                manager.enqueueQueuedMission(mission);
            }
            return;
        }

        try {
            if (target.entity != null && mission.getTargetId() == null) {
                mission.reacquireTarget(target.entity.getUniqueID(), tick);
            }
            mission.transitionTo(MissionState.DISPATCHED, tick);
            mission.transitionTo(MissionState.TAKEOFF, tick);
            mission.transitionTo(MissionState.CRUISE, tick);
            mission.transitionTo(MissionState.SEARCHING, tick);

            // WingMan must fly the real aircraft to the observation point
            // before the mission reports acquisition or arms an attack.
            if (!flight.dispatch(aircraft, target.x, target.y, target.z,
                    mission.getObservationDistance(), 200, mission.isAttack(), mission.getTargetId())) {
                fail(world, mission, "WINGMAN_DISPATCH_REJECTED", tick);
            } else {
                LOGGER.info("Mission {} dispatched aircraft {} type={} target={} at tick {}",
                        mission.getMissionId(), aircraft.getUniqueID(), mission.getAircraftType(),
                        formatTarget(target), tick);
            }
        } catch (RuntimeException failure) {
            fail(world, mission, failure.getMessage() == null
                    ? "MISSION_DISPATCH_FAILED" : failure.getMessage(), tick);
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
            if (tick - mission.getLastUpdateTick() > MISSION_TIMEOUT) {
                fail(world, mission, mission.getState().name() + "_TIMEOUT", tick);
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
                fail(world, mission, "AIRCRAFT_LOST", tick);
                dirty = true;
                continue;
            }
            if (!flight.hasFuelForMission(aircraft)
                    && mission.getState() != MissionState.RETURNING
                    && mission.getState() != MissionState.LANDING
                    && mission.getState() != MissionState.PARKING) {
                if (!flight.requestReturn(aircraft, "RDP_LOW_FUEL")) {
                    fail(world, mission, "LOW_FUEL_RETURN_REJECTED", tick);
                } else {
                    mission.transitionTo(MissionState.RETURNING, tick);
                }
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
                    ? Target.point(mission.getTargetSpec().getX(), mission.getTargetSpec().getY(),
                    mission.getTargetSpec().getZ())
                    : reacquire(world, aircraft, mission, tick);
            if (mission.getTargetSelectionMode() != TargetSelectionMode.POINT && target == null
                    && requiresTarget(mission.getState())) {
                recoverFromTargetLoss(world, mission, aircraft, tick);
                dirty = true;
                continue;
            }
            if (mission.getState() == MissionState.KAMIKADZE_TARGET
                    && KamikazeImpactDetector.hasNoseImpact(aircraft)) {
                if (flight.fireSelfDestructWeapon(aircraft)) {
                    LOGGER.info("Mission {} kamikaze impact detonation aircraft={} tick={}",
                            mission.getMissionId(), aircraft.getUniqueID(), tick);
                    mission.markKamikazeDetonated(tick);
                    flight.release(aircraft);
                    manager.release(mission);
                    dirty = true;
                    continue;
                }
                if (mission.getState() == MissionState.KAMIKADZE_TARGET
                        && target != null && target.entity != null
                        && target.entity.getDistance(aircraft) <= net.vas.rdp.uav.flight.KamikazeConfig.closeFireDistance
                        && KamikazePilotOverride.targetFor(aircraft.getUniqueID()) != null
                        && KamikazePilotOverride.targetFor(aircraft.getUniqueID()).equals(target.entity.getUniqueID())) {
                    if (flight.fireSelfDestructWeapon(aircraft)) {
                        LOGGER.info("Mission {} AI close-range kamikaze fire aircraft={} target={} tick={}",
                                mission.getMissionId(), aircraft.getUniqueID(), target.entity.getUniqueID(), tick);
                        mission.markKamikazeDetonated(tick);
                        KamikazePilotOverride.clear(aircraft.getUniqueID());
                        flight.release(aircraft);
                        manager.release(mission);
                        dirty = true;
                        continue;
                    }
                    fail(world, mission, "KAMIKAZE_CLOSE_FIRE_REJECTED", tick);
                    dirty = true;
                    continue;
                }
                fail(world, mission, "KAMIKAZE_IMPACT_FIRE_REJECTED", tick);
                dirty = true;
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
                dirty = true;
            }
        }
        if (dirty) RDPMissionWorldData.get(world).markDirty();
    }

    private boolean updateMission(World world, RDPMission mission, MCH_EntityAircraft aircraft,
                                  Target target, long tick) {
        WingmanEntry entry = flight.getEntry(aircraft);
        if (entry == null) {
            RdpUavDebug.debug("Lifecycle", "mission {} WingMan entry missing age={}",
                    mission.getMissionId(), tick - mission.getCreatedTick());
            if (tick - mission.getCreatedTick() <= WINGMAN_ENTRY_GRACE_TICKS) {
                return false;
            }
            fail(world, mission, "WINGMAN_ENTRY_LOST", tick);
            return true;
        }
        if (target != null) {
            entry.autoTargetX = target.x;
            entry.autoTargetY = target.y;
            entry.autoTargetZ = target.z;
            if (entry.order != null) {
                entry.order.targetX = target.x;
                entry.order.targetZ = target.z;
            }
        }
        if (flight.isParked(aircraft) && mission.getState() != MissionState.RETURNING) {
            fail(world, mission, "WINGMAN_TERMINATED_BEFORE_RETURN", tick);
            return true;
        }
        if (flight.isReturning(aircraft) && mission.getState() != MissionState.RETURNING) {
            if (mission.getState() == MissionState.ATTACK_TARGET
                    || mission.getState() == MissionState.REPORTING) {
                mission.transitionTo(MissionState.RETURNING, tick);
            } else {
                fail(world, mission, "WINGMAN_RTB_BEFORE_REPORT", tick);
            }
            return true;
        }

        switch (mission.getState()) {
        case SEARCHING:
            if (!atTarget(aircraft, target, mission)) return false;
            mission.transitionTo(MissionState.TARGET_ACQUIRED, tick);
            if (target.entity == null) mission.lockPoint(tick);
            else mission.lockTarget(target.entity.getUniqueID(), tick);
            return true;
        case TARGET_LOCKED:
                UUID pilotTarget = KamikazePilotOverride.targetFor(aircraft.getUniqueID());
                if (mission.isAttack() && pilotTarget != null && target != null
                        && pilotTarget.equals(target.entity == null ? null : target.entity.getUniqueID())) {
                    if (!flight.armKamikaze(aircraft, target.x, target.y, target.z,
                            mission.getObservationDistance(), target.entity.getUniqueID())) {
                        fail(world, mission, "AI_KAMIKAZE_ARM_REJECTED", tick);
                    } else {
                        mission.transitionTo(MissionState.KAMIKADZE_TARGET, tick);
                    }
                    return true;
                }
                if (mission.isAttack()) {
                    if (mission.getMissionType() == MissionType.SQUARE_STRIKE) {
                        net.vas.rdp.uav.flight.SquareStrikePlan plan =
                                new net.vas.rdp.uav.flight.SquareStrikePlan(
                                        target.x, Math.max(target.y + 32.0D, aircraft.posY),
                                target.z, mission.getSearchRadius(), mission.getSearchRadius(), 0.0D,
                                Math.max(8.0D, mission.getSearchRadius() / 4.0D),
                                mission.getObservationDistance(), 1);
                        if (!flight.armSquareStrike(aircraft, plan, mission.getTargetId())) {
                            fail(world, mission, "WINGMAN_SQUARE_STRIKE_REJECTED", tick);
                        } else {
                            mission.transitionTo(MissionState.ATTACK_TARGET, tick);
                        }
                        return true;
                    }
                    if (!flight.armAttack(aircraft, target.x, target.y, target.z,
                            mission.getObservationDistance(), mission.getTargetId())) {
                        fail(world, mission, "WINGMAN_ATTACK_ARM_REJECTED", tick);
                    } else {
                        mission.transitionTo(MissionState.ATTACK_TARGET, tick);
                    }
                    return true;
                }
                if (atTarget(aircraft, target, mission)) {
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
                if (atTarget(aircraft, target, mission)) {
                    mission.transitionTo(MissionState.ON_TARGET, tick);
                    mission.transitionTo(MissionState.SCANNING, tick);
                    return true;
                }
                return false;
            case ON_TARGET:
            case ORBITING:
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
                if (!flight.requestReturn(aircraft, "RDP_REPORT_COMPLETE")) {
                    fail(world, mission, "WINGMAN_RETURN_REJECTED", tick);
                } else {
                    mission.transitionTo(MissionState.RETURNING, tick);
                }
                return true;
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
        if (current != null) return Target.entity(current);
        List<TargetResolver.EntityCandidate> candidates = targets.find(world, aircraft.posX,
                aircraft.posY, aircraft.posZ, mission.getSearchRadius(), mission.getTargetSpec());
        if (candidates.isEmpty()) return null;
        Entity reacquired = candidates.get(0).getEntity();
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
            return Target.point(mission.getTargetSpec().getX(), mission.getTargetSpec().getY(),
                    mission.getTargetSpec().getZ());
        }
        if (!allowAcquire) return null;
        Entity current = targets.findLoaded(world, mission.getTargetId());
        if (current == null && mission.getTargetSelectionMode() == TargetSelectionMode.MANUAL) {
            current = targets.findLoadedByRegistryId(world, mission.getTargetSpec().getRegistryId());
        }
        if (current != null && aircraft.getDistanceSq(current) <=
                mission.getSearchRadius() * mission.getSearchRadius()) {
            return Target.entity(current);
        }
        if (current != null) return null;
        List<TargetResolver.EntityCandidate> candidates = targets.find(world, aircraft.posX,
                aircraft.posY, aircraft.posZ, mission.getSearchRadius(), mission.getTargetSpec());
        return candidates.isEmpty() ? null : Target.entity(candidates.get(0).getEntity());
    }

    private void recoverFromTargetLoss(World world, RDPMission mission,
                                       MCH_EntityAircraft aircraft, long tick) {
        if (mission.getState() == MissionState.ATTACK_TARGET
                && flight.requestReturn(aircraft, "RDP_TARGET_LOST")) {
            mission.transitionTo(MissionState.RETURNING, tick);
        } else {
            fail(world, mission, "TARGET_LOST", tick);
        }
    }

    private boolean atTarget(MCH_EntityAircraft aircraft, Target target, RDPMission mission) {
        if (target == null) return false;
        double dx = aircraft.posX - target.x;
        double dy = aircraft.posY - target.y;
        double dz = aircraft.posZ - target.z;
        return dx * dx + dy * dy + dz * dz
                <= mission.getObservationDistance() * mission.getObservationDistance();
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
        Map<UUID, MCH_EntityAircraft> result = new HashMap<UUID, MCH_EntityAircraft>();
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof MCH_EntityAircraft && !entity.isDead
                    && entity.dimension == world.provider.getDimension()
                    && ((MCH_EntityAircraft) entity).isUAV()) {
                result.put(entity.getUniqueID(), (MCH_EntityAircraft) entity);
            }
        }
        return result;
    }

    private void fail(World world, RDPMission mission, String reason, long tick) {
        if (!terminal(mission.getState())) {
            LOGGER.warn("Mission {} FAILED reason={} state={} aircraft={} dimension={} tick={} age={}",
                    mission.getMissionId(), reason, mission.getState(), mission.getAircraftId(),
                    mission.getDimension(), tick, Math.max(0L, tick - mission.getCreatedTick()));
            mission.fail(reason, tick);
            MCH_EntityAircraft aircraft = findAircraft(world, mission.getAircraftId());
            if (aircraft != null) flight.release(aircraft);
            manager.release(mission);
            RDPMissionWorldData.get(world).markDirty();
        }
    }

    private String formatTarget(Target target) {
        return target == null ? "none" : target.entity == null
                ? String.format(java.util.Locale.ROOT, "point(%.1f,%.1f,%.1f)",
                target.x, target.y, target.z)
                : target.entity.getUniqueID() + "/" + EntityList.getKey(target.entity);
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
        return state == MissionState.TARGET_LOCKED || state == MissionState.ATTACK_TARGET
                || state == MissionState.APPROACHING || state == MissionState.ON_TARGET
                || state == MissionState.ORBITING || state == MissionState.SCANNING;
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
}
