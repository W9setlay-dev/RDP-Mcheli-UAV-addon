package net.vas.rdp.uav.flight;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.helicopter.MCH_EntityHeli;
import com.norwood.mcheli.MCH_Explosion;
import com.norwood.mcheli.wingman.mission.MissionNode;
import com.norwood.mcheli.wingman.mission.MissionPlan;
import com.norwood.mcheli.wingman.mission.AutonomousState;
import com.norwood.mcheli.wingman.wingman.WingmanEntry;
import com.norwood.mcheli.wingman.wingman.WingmanRegistry;
import com.norwood.mcheli.weapon.MCH_WeaponSet;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.vas.rdp.uav.RdpUavDebug;
import net.vas.rdp.uav.energy.UavEnergyHandler;
import net.vas.rdp.uav.mission.MissionManager;
import net.vas.rdp.uav.mission.MissionMovementAuthority;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.mission.PatrolRoutePlanner;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.UavVehicleProfiles;
import net.vas.rdp.uav.world.MinecraftWorldPerceptionAdapter;
import net.vas.rdp.uav.world.WorldPerceptionAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/**
 * The boundary between WingMan route sequencing and the RDP flight computer.
 */
public final class WingManFlightAdapter {
    private final WorldPerceptionAdapter terrainPerception = new MinecraftWorldPerceptionAdapter();
    private final AircraftStateEstimator aircraftStates = new AircraftStateEstimator();
    private final Set<UUID> nativeAttackCompletions = new HashSet<>();
    private final Map<UUID, ContinuousTargetTracker> targetTrackers = new HashMap<>();
    private final Map<UUID, FpvFlightStateMachine> fpvStates = new HashMap<>();
    private final Set<UUID> targetLossHoldRoutes = new HashSet<>();
    private final Map<UUID, Integer> terminalWaypointReplacements = new HashMap<>();
    private final Map<UUID, Double> terminalPreviousDistance = new HashMap<>();
    private final Map<UUID, Long> terminalLastDiagnosticTick = new HashMap<>();
    private final Map<UUID, CachedTerrainPlan> terrainPlans = new HashMap<>();
    private final Map<UUID, TerminalGuidanceMetrics> terminalMetrics = new HashMap<>();
    private final Map<UUID, TerminalGuidanceMetrics.Classification> terminalLastClassification
            = new HashMap<>();
    public boolean dispatch(MCH_EntityAircraft aircraft, double x, double y, double z,
                            double orbitRadius, int orbitTicks) {
        return dispatch(aircraft, x, y, z, orbitRadius, orbitTicks, false, null);
    }

    public void observeAircraftState(MCH_EntityAircraft aircraft,
                                     RDPMission mission,
                                     long tick) {
        if (aircraft == null) return;
        aircraftStates.observe(aircraft, getEntry(aircraft), mission, tick);
    }

    /**
     * Runs at world-tick START before native WingMan's normal-priority handler
     * and before the aircraft entity update. The mission lease causes the
     * transformer guards to skip WingMan's later movement writers, while this
     * profile controller supplies the setpoints consumed by MCheliCE physics.
     * WingMan supplies route intent only.
     */
    public void applyNativeControl(World world,
                                   MissionManager missions,
                                   MCH_EntityAircraft aircraft,
                                   RDPMission mission,
                                   long tick) {
        if (world == null || missions == null || aircraft == null || mission == null
                || world.isRemote || mission.isTerminal() || aircraft.isDead) return;
        if (mission.getState() == net.vas.rdp.uav.mission.MissionState.LANDING
                || mission.getState() == net.vas.rdp.uav.mission.MissionState.PARKING) {
            return;
        }
        WingmanEntry entry = getEntry(aircraft);
        if (entry == null || !entry.isAutonomous()) return;
        UavVehicleProfile profile = aircraft.getAcInfo() == null ? null
                : UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name);
        if (profile == null || profile.getExecutionProfile() == UavExecutionProfile.UNKNOWN) {
            RdpUavDebug.debug("FlightControl", "control rejected aircraft={} reason=UNSUPPORTED_PROFILE",
                    aircraft.getUniqueID());
            return;
        }
        // WingMan refreshes these effective setpoints while advancing route
        // nodes. They also carry moving-target updates and remain meaningful
        // for LOITER/ATTACK nodes, whose node coordinates may be defaults.
        double targetX = entry.autoTargetX;
        double targetY = entry.autoTargetY;
        double targetZ = entry.autoTargetZ;
        if (!Double.isFinite(targetX) || !Double.isFinite(targetY) || !Double.isFinite(targetZ)) {
            RdpUavDebug.debug("FlightControl", "control held aircraft={} reason=NO_FINITE_ROUTE_NODE",
                    aircraft.getUniqueID());
            return;
        }
        AircraftState state = aircraftStates.observe(aircraft, entry, mission, tick);
        double baseTargetX = targetX;
        double baseTargetY = targetY;
        double baseTargetZ = targetZ;
        AirframeController controller = AirframeController.forProfile(profile,
                usesHelicopterFlightModel(aircraft));
        NativeFlightControlCommand baseCommand = controller.update(state,
                baseTargetX, baseTargetY, baseTargetZ, tick);
        net.vas.rdp.uav.flight.OperatorTrimState.Snapshot trim =
                OperatorTrimRegistry.snapshot(aircraft.getUniqueID(), mission.getMissionId(), tick);
        if (trim != null) {
            OperatorTrimState.Target mixed = OperatorTrimState.mixTarget(targetX, targetY, targetZ,
                    state.getX(), state.getY(), state.getZ(), trim, profile,
                    0.0D, Math.max(1.0D, world.getHeight() - 2.0D));
            targetX = mixed.x;
            targetZ = mixed.z;
        }
        double horizontalDistance = Math.hypot(targetX - state.getX(), targetZ - state.getZ());
        boolean terminalCommit = mission.getExecutionStage()
                == net.vas.rdp.uav.mission.MissionExecutionStage.TERMINAL
                && horizontalDistance <= Math.max(48.0D, state.getHorizontalSpeed() * 40.0D);
        double trimPitch = trim == null ? 0.0D : trim.getPitchDegrees();
        TerrainRoutePlanner.Plan terrainPlan = cachedTerrainPlan(world, aircraft,
                mission, state, targetX, targetY, targetZ, profile, trimPitch,
                terminalCommit, tick);
        targetX = terrainPlan.getX();
        targetY = terrainPlan.getY();
        targetZ = terrainPlan.getZ();
        if (!terrainPlan.isSafe()) {
            RdpUavDebug.event("FlightControl", "TERRAIN_HOLD",
                    "mission", mission.getMissionId(), "aircraft", aircraft.getUniqueID(),
                    "tick", tick, "reason", terrainPlan.getReason(),
                    "position", state.getX() + "," + state.getY() + "," + state.getZ(),
                    "safeNode", targetX + "," + targetY + "," + targetZ,
                    "operatorTrimYaw", trim == null ? 0.0D : trim.getYawDegrees(),
                    "operatorTrimPitch", trim == null ? 0.0D : trim.getPitchDegrees());
        }
        NativeFlightControlCommand command = controller.update(state, targetX, targetY, targetZ, tick);
        if (!command.isSupported()) {
            RdpUavDebug.debug("FlightControl", "control held aircraft={} reason={} sampleTick={}",
                    aircraft.getUniqueID(), command.getReason(), state.getSampleTick());
            return;
        }
        boolean applied = NativeAircraftControlAdapter.apply(world, missions, aircraft,
                mission, command, tick);
        if (applied && tick % 20L == 0L) {
            RdpUavDebug.debug("FlightControl",
                    "controller=RDP_FLIGHT_COMPUTER controlOwner={} aircraft={} profile={} node={} pos=({},{},{}) "
                            + "motion=({},{},{}) yaw={} track={} desiredTrack={} courseError={} "
                            + "pitch={} roll={} throttle={} desiredVerticalSpeed={} terrain={} AGL={} "
                            + "trimYaw={} trimPitch={} trimAge={} baseCommand=({},{},{},{}) "
                            + "finalCommand=({},{},{},{})",
                    state.getControlOwner(), aircraft.getUniqueID(),
                    profile.getExecutionProfile(), state.getRouteNode(),
                    state.getX(), state.getY(), state.getZ(), state.getMotionX(),
                    state.getMotionY(), state.getMotionZ(), state.getYaw(),
                    state.getGroundTrackYaw(), command.getDesiredTrack(),
                    command.getCourseError(), aircraft.getPitch(), aircraft.getRoll(),
                    aircraft.getThrottle(), command.getDesiredVerticalSpeed(),
                    terrainPlan.getReason(), terrainPlan.getCurrentAgl(),
                    trim == null ? 0.0D : trim.getYawDegrees(),
                    trim == null ? 0.0D : trim.getPitchDegrees(),
                    trim == null ? -1L : tick - trim.getSampleTick(),
                    baseCommand.isSupported() ? baseCommand.getYaw() : Float.NaN,
                    baseCommand.isSupported() ? baseCommand.getPitch() : Float.NaN,
                    baseCommand.isSupported() ? baseCommand.getRoll() : Float.NaN,
                    baseCommand.isSupported() ? baseCommand.getThrottle() : Double.NaN,
                    command.getYaw(), command.getPitch(), command.getRoll(), command.getThrottle());
        }
    }

    private static boolean usesHelicopterFlightModel(MCH_EntityAircraft aircraft) {
        if (aircraft == null || aircraft.getAcInfo() == null) return aircraft instanceof MCH_EntityHeli;
        UavVehicleProfile profile = UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name);
        return aircraft instanceof MCH_EntityHeli || profile.isHelicopterProfile();
    }

    private TerrainRoutePlanner.Plan cachedTerrainPlan(World world,
                                                       MCH_EntityAircraft aircraft,
                                                       RDPMission mission,
                                                       AircraftState state,
                                                       double targetX, double targetY,
                                                       double targetZ,
                                                       UavVehicleProfile profile,
                                                       double pitchTrim,
                                                       boolean terminalCommit,
                                                       long tick) {
        UUID id = aircraft.getUniqueID();
        CachedTerrainPlan cached = terrainPlans.get(id);
        if (cached != null && cached.missionId.equals(mission.getMissionId())
                && tick >= cached.tick && tick - cached.tick < 5L
                && Math.abs(cached.requestX - targetX) < 2.0D
                && Math.abs(cached.requestY - targetY) < 2.0D
                && Math.abs(cached.requestZ - targetZ) < 2.0D
                && Math.abs(cached.pitchTrim - pitchTrim) < 0.25D
                && cached.terminal == terminalCommit) return cached.plan;
        TerrainRoutePlanner.Plan plan = TerrainRoutePlanner.planTransit(world,
                terrainPerception, state.getX(), state.getY(), state.getZ(),
                state.getHorizontalSpeed(), targetX, targetY, targetZ, profile,
                tick, pitchTrim, terminalCommit);
        terrainPlans.put(id, new CachedTerrainPlan(mission.getMissionId(), tick,
                targetX, targetY, targetZ, pitchTrim, terminalCommit, plan));
        return plan;
    }

    private static final class CachedTerrainPlan {
        private final UUID missionId;
        private final long tick;
        private final double requestX, requestY, requestZ, pitchTrim;
        private final boolean terminal;
        private final TerrainRoutePlanner.Plan plan;
        private CachedTerrainPlan(UUID missionId, long tick, double requestX,
                                  double requestY, double requestZ, double pitchTrim,
                                  boolean terminal, TerrainRoutePlanner.Plan plan) {
            this.missionId = missionId; this.tick = tick;
            this.requestX = requestX; this.requestY = requestY; this.requestZ = requestZ;
            this.pitchTrim = pitchTrim; this.terminal = terminal; this.plan = plan;
        }
    }

    private static double normalizeHelicopterTargetY(MCH_EntityAircraft aircraft, double targetY) {
        if (!usesHelicopterFlightModel(aircraft)) return targetY;
        UavVehicleProfile profile = aircraft.getAcInfo() == null
                ? new UavVehicleProfile(UavExecutionProfile.HELICOPTER_ONE_WAY_FPV, 8D, 4D, 32D, 6D, false, "SELF_DESTRUCT")
                : UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name);
        double minimum = profile.getMinimumExecutionAltitude() > 0D
                ? profile.getMinimumExecutionAltitude() : 4D;
        double standoff = Math.max(8D, minimum);
        return Math.max(targetY + standoff, minimum);
    }

    @SuppressWarnings("unused")
    public boolean dispatch(MCH_EntityAircraft aircraft, double x, double y, double z,
                            double orbitRadius, int orbitTicks, boolean attack, UUID targetId) {
        if (nativeRouteUnsupported(aircraft)) {
            RdpUavDebug.debug("WingMan",
                    "dispatch rejected: aircraft={} definition={} isUav={} profile={}",
                    aircraft == null ? null : aircraft.getUniqueID(),
                    aircraft == null || aircraft.getAcInfo() == null
                            ? null : aircraft.getAcInfo().name,
                    aircraft != null && aircraft.isUAV(),
                    aircraft == null || aircraft.getAcInfo() == null
                            ? null : UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name)
                            .getExecutionProfile());
            return false;
        }
        UUID aircraftId = aircraft.getUniqueID();
        targetLossHoldRoutes.remove(aircraftId);
        fpvStates.remove(aircraftId);
        WingmanEntry existing = WingmanRegistry.get(aircraftId);
        if (existing != null) {
            // A previous RDP mission can leave an inert PARKED/NONE entry
            // behind when the native handler finishes between ticks. Do not
            // turn that stale bookkeeping record into a permanent rejection,
            // but never take over an active native controller.
            boolean inert = existing.mission == null && !existing.hasOrder()
                    && (existing.autoState == AutonomousState.NONE
                    || existing.autoState == AutonomousState.PARKED);
            if (!inert) {
                RdpUavDebug.debug("WingMan",
                        "dispatch rejected: active entry aircraft={} state={} node={}",
                        aircraftId, existing.autoState, existing.currentNode());
                return false;
            }
            WingmanRegistry.remove(aircraftId);
            RdpUavDebug.debug("WingMan", "removed stale entry before dispatch aircraft={}", aircraftId);
        }

        double safeY = normalizeHelicopterTargetY(aircraft, y);
        List<MissionNode> nodes = new ArrayList<>();
        nodes.add(MissionNode.flyTo(x, safeY, z));
        // Attack routes must reach the target immediately.  A long loiter
        // before the attack node made one-way aircraft appear to orbit
        // forever while the mission controller waited for completion.
        if (orbitTicks > 0 && !attack) {
            nodes.add(MissionNode.loiter(orbitTicks));
        }

        WingmanEntry entry = new WingmanEntry(aircraft,
                attack ? WingmanEntry.ATK_AUTO : WingmanEntry.ATK_NONE);
        entry.currentAutoTarget = targetId;
        entry.autoTargetX = x;
        entry.autoTargetY = safeY;
        entry.autoTargetZ = z;
        if (isFpv(aircraft)) {
            FpvFlightStateMachine state = new FpvFlightStateMachine();
            state.moveToTarget();
            fpvStates.put(aircraftId, state);
        }
        entry.mission = nodes;
        entry.missionIndex = 0;
        entry.missionNodeTimer = 0;
        // WingMan's native mission command starts custom node plans in
        // ENROUTE.  A mission list with the default NONE state is registered
        // but is not advanced by the autonomous flight handler.
        entry.autoState = AutonomousState.ENROUTE;
        WingmanRegistry.put(aircraftId, entry);
        RdpUavDebug.debug("WingMan",
                "dispatch accepted aircraft={} target=({}, {}, {}) nodes={} attack={} targetId={}",
                aircraftId, x, y, z, nodes.size(), attack, targetId);
        return true;
    }

    /** Installs a finite patrol route as native WingMan nodes; never writes flight motion. */
    public boolean dispatchPatrolRoute(MCH_EntityAircraft aircraft,
                                       List<PatrolRoutePlanner.FlightWaypoint> waypoints) {
        if (nativeRouteUnsupported(aircraft) || waypoints == null || waypoints.isEmpty()
                || waypoints.size() > 16) return false;
        UUID aircraftId = aircraft.getUniqueID();
        WingmanEntry existing = WingmanRegistry.get(aircraftId);
        boolean inert = existing != null && existing.mission == null && !existing.hasOrder()
                && (existing.autoState == AutonomousState.NONE
                || existing.autoState == AutonomousState.PARKED);
        if (existing != null && !inert) return false;
        if (inert) WingmanRegistry.remove(aircraftId);

        List<MissionNode> nodes = new ArrayList<>(waypoints.size() * 2);
        for (PatrolRoutePlanner.FlightWaypoint waypoint : waypoints) {
            if (waypoint == null || !Double.isFinite(waypoint.getX())
                    || !Double.isFinite(waypoint.getY()) || !Double.isFinite(waypoint.getZ())
                    || waypoint.getLoiterTicks() < 0
                    || waypoint.getLoiterTicks() > net.vas.rdp.uav.mission.MissionWaypoint.MAX_LOITER_TICKS) {
                return false;
            }
            nodes.add(MissionNode.flyTo(waypoint.getX(),
                    normalizeHelicopterTargetY(aircraft, waypoint.getY()), waypoint.getZ()));
            if (waypoint.getLoiterTicks() > 0) {
                nodes.add(MissionNode.loiter(waypoint.getLoiterTicks()));
            }
        }
        if (nodes.isEmpty()) return false;
        WingmanEntry entry = new WingmanEntry(aircraft, WingmanEntry.ATK_NONE);
        PatrolRoutePlanner.FlightWaypoint first = waypoints.get(0);
        entry.autoTargetX = first.getX();
        entry.autoTargetY = normalizeHelicopterTargetY(aircraft, first.getY());
        entry.autoTargetZ = first.getZ();
        entry.mission = nodes;
        entry.missionIndex = 0;
        entry.missionNodeTimer = 0;
        entry.autoState = AutonomousState.ENROUTE;
        WingmanRegistry.put(aircraftId, entry);
        return true;
    }

    public boolean hasCompletedRoute(MCH_EntityAircraft aircraft) {
        WingmanEntry entry = getEntry(aircraft);
        return entry != null && entry.mission != null
                && entry.missionIndex >= entry.mission.size();
    }

    /**
     * Rebuilds only the native WingMan route.  The aircraft remains under
     * WingMan control; this never writes position, yaw, pitch, or motion.
     */
    public boolean refreshObservationRoute(MCH_EntityAircraft aircraft, double x, double y, double z,
                                           double orbitRadius, boolean orbit, UUID targetId) {
        if (nativeRouteUnsupported(aircraft)) return false;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        boolean resumedHold = resumeTargetLossHold(aircraft.getUniqueID(), entry);
        if (entry == null || entry.mission == null || entry.autoState == AutonomousState.RTB
                || entry.autoState == AutonomousState.LANDING
                || (entry.autoState == AutonomousState.PARKED && !resumedHold)) return false;
        if (isFpv(aircraft)) {
            FpvFlightState state = fpvFlightState(aircraft);
            if (state == FpvFlightState.HOVER || state == FpvFlightState.TERMINAL_APPROACH) {
                return false;
            }
        }

        double safeY = normalizeHelicopterTargetY(aircraft, y);
        List<MissionNode> route = new ArrayList<>();
        if (!orbit) {
            route.add(MissionNode.flyTo(x, safeY, z));
            route.add(MissionNode.loiter(40));
        } else {
            double radius = Math.max(4.0D, orbitRadius);
            double angle = Math.atan2(aircraft.posZ - z, aircraft.posX - x);
            for (int i = 0; i < 4; i++) {
                double waypointAngle = angle + (Math.PI * 2.0D * i / 4.0D);
                route.add(MissionNode.flyTo(
                        x + Math.cos(waypointAngle) * radius,
                        safeY,
                        z + Math.sin(waypointAngle) * radius));
                route.add(MissionNode.loiter(30));
            }
        }
        entry.mission = route;
        entry.missionIndex = 0;
        entry.missionNodeTimer = 0;
        entry.currentAutoTarget = targetId;
        entry.autoTargetX = x;
        entry.autoTargetY = safeY;
        entry.autoTargetZ = z;
        entry.autoState = AutonomousState.ENROUTE;
        return true;
    }

    /**
     * Installs one finite side detour and then the target point. This is a
     * route decision, not a second navigation controller.
     */
    public boolean refreshBoundedDetour(MCH_EntityAircraft aircraft, double x, double y, double z,
                                        double detourDistance, UUID targetId) {
        if (nativeRouteUnsupported(aircraft)) return false;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        boolean resumedHold = resumeTargetLossHold(aircraft.getUniqueID(), entry);
        if (entry == null || entry.mission == null
                || entry.autoState == AutonomousState.RTB
                || entry.autoState == AutonomousState.LANDING
                || (entry.autoState == AutonomousState.PARKED && !resumedHold)) return false;
        double dx = x - aircraft.posX;
        double dz = z - aircraft.posZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0D) return false;
        double offset = Math.max(4.0D, Math.min(32.0D, detourDistance));
        double sideX = aircraft.posX - dz / length * offset;
        double sideZ = aircraft.posZ + dx / length * offset;
        double safeY = normalizeHelicopterTargetY(aircraft, y);
        List<MissionNode> route = new ArrayList<>();
        route.add(MissionNode.flyTo(sideX, safeY, sideZ));
        route.add(MissionNode.flyTo(x, safeY, z));
        entry.mission = route;
        entry.missionIndex = 0;
        entry.missionNodeTimer = 0;
        entry.currentAutoTarget = targetId;
        entry.autoTargetX = x;
        entry.autoTargetY = safeY;
        entry.autoTargetZ = z;
        entry.autoState = AutonomousState.ENROUTE;
        return true;
    }

    public boolean hasReachedRoutePoint(MCH_EntityAircraft aircraft) {
        WingmanEntry entry = getEntry(aircraft);
        if (entry == null || entry.mission == null) return false;
        int routeSize = entry.mission.size();
        if (routeSize <= 1) return true;
        return entry.missionIndex >= routeSize - 1
                && (entry.autoState == AutonomousState.LOITER
                || entry.autoState == AutonomousState.ENROUTE
                || entry.autoState == AutonomousState.ON_STATION
                || entry.autoState == AutonomousState.STRIKE_PASS);
    }

    public void clearGuidance(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        if (entry == null) return;
        entry.autoTargetX = aircraft.posX;
        entry.autoTargetY = aircraft.posY;
        entry.autoTargetZ = aircraft.posZ;
    }

    /**
     * Holds an FPV vehicle at its current position while preserving the
     * authoritative target identity for reacquisition.
     */
    @SuppressWarnings("unused")
    public void enterFpvHover(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return;
        enterFpvHover(aircraft, aircraft.posX, aircraft.posY, aircraft.posZ);
    }

    private boolean enterFpvHover(MCH_EntityAircraft aircraft, double targetX,
                                  double targetY, double targetZ) {
        if (!usesHelicopterFlightModel(aircraft)) return false;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        if (entry == null || entry.mission == null
                || entry.autoState == AutonomousState.RTB
                || entry.autoState == AutonomousState.LANDING
                || entry.autoState == AutonomousState.PARKED) return false;
        FpvFlightStateMachine state = fpvStates.get(aircraft.getUniqueID());
        if (state == null) {
            state = new FpvFlightStateMachine();
            fpvStates.put(aircraft.getUniqueID(), state);
        }
        if (state.getState() == FpvFlightState.HOVER
                && Math.abs(entry.autoTargetX - targetX) < 1.0D
                && Math.abs(entry.autoTargetY - targetY) < 1.0D
                && Math.abs(entry.autoTargetZ - targetZ) < 1.0D) return true;
        if (state.getState() == FpvFlightState.TERMINAL_APPROACH) return false;
        state.hover();
        entry.mission = new ArrayList<>();
        entry.mission.add(MissionNode.flyTo(targetX, targetY, targetZ));
        entry.mission.add(MissionNode.loiter(40));
        entry.missionIndex = 0;
        entry.missionNodeTimer = 0;
        entry.autoTargetX = targetX;
        entry.autoTargetY = targetY;
        entry.autoTargetZ = targetZ;
        entry.autoState = AutonomousState.ENROUTE;
        return true;
    }

    /** Profile-specific post-recon station: rotorcraft native hover, fixed-wing forward orbit. */
    public boolean enterStationHold(MCH_EntityAircraft aircraft, double centerX,
                                    double altitude, double centerZ, double radius) {
        if (aircraft == null || aircraft.isDead
                || !Double.isFinite(centerX) || !Double.isFinite(altitude)
                || !Double.isFinite(centerZ) || !Double.isFinite(radius)) return false;
        UavVehicleProfile profile = aircraft.getAcInfo() == null ? null
                : UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name);
        if (profile == null || profile.getExecutionProfile() == UavExecutionProfile.UNKNOWN) return false;
        if (usesHelicopterFlightModel(aircraft)) {
            return enterFpvHover(aircraft, centerX, altitude, centerZ);
        }
        WingmanEntry entry = getEntry(aircraft);
        if (entry == null || entry.mission == null || radius < 16.0D) return false;
        double dx = aircraft.posX - centerX;
        double dz = aircraft.posZ - centerZ;
        double radial = Math.hypot(dx, dz);
        double angle = radial < 2.0D
                ? Math.toRadians(aircraft.getYaw())
                : Math.atan2(dz, dx);
        double tangentX = Math.sin(angle);
        double tangentZ = -Math.cos(angle);
        double direction = aircraft.motionX * tangentX + aircraft.motionZ * tangentZ >= 0.0D
                ? 1.0D : -1.0D;
        List<MissionNode> route = new ArrayList<>();
        // Eight connected forward waypoints approximate a continuous circle
        // while keeping each native waypoint short and deterministic.
        for (int i = 1; i <= 8; i++) {
            double pointAngle = angle + direction * Math.PI * 2.0D * i / 8.0D;
            route.add(MissionNode.flyTo(centerX + Math.cos(pointAngle) * radius,
                    altitude, centerZ + Math.sin(pointAngle) * radius));
        }
        entry.mission = route;
        entry.missionIndex = 0;
        entry.missionNodeTimer = 0;
        entry.attackMode = WingmanEntry.ATK_NONE;
        entry.currentAutoTarget = null;
        entry.autoTargetX = route.get(0).x;
        entry.autoTargetY = route.get(0).y;
        entry.autoTargetZ = route.get(0).z;
        entry.autoState = AutonomousState.ENROUTE;
        return true;
    }

    /**
     * Replaces a stale target route with a short, non-attacking hold leg while
     * preserving the mission's control lease and the aircraft's current
     * physical momentum. Fixed-wing aircraft continue along their current
     * heading; FPV aircraft use their hover controller unless terminal
     * guidance has already committed a finite approach route.
     */
    public void holdForTargetReacquisition(MCH_EntityAircraft aircraft) {
        if (aircraft == null || aircraft.isDead) return;
        if (isFpv(aircraft)) {
            targetLossHoldRoutes.remove(aircraft.getUniqueID());
            enterFpvHover(aircraft);
            return;
        }
        WingmanEntry entry = getEntry(aircraft);
        if (entry == null || entry.mission == null) return;
        UUID aircraftId = aircraft.getUniqueID();
        boolean alreadyHolding = targetLossHoldRoutes.contains(aircraftId);
        MissionNode current = entry.currentNode();
        boolean holdRouteInProgress = alreadyHolding
                && entry.autoState != AutonomousState.RTB
                && entry.autoState != AutonomousState.LANDING
                && entry.autoState != AutonomousState.PARKED
                && current != null
                && (current.type == MissionNode.Type.FLY_TO
                || current.type == MissionNode.Type.LOITER);
        if (holdRouteInProgress) return;
        targetLossHoldRoutes.remove(aircraftId);
        if (entry.autoState == AutonomousState.RTB
                || entry.autoState == AutonomousState.LANDING
                || (entry.autoState == AutonomousState.PARKED && !alreadyHolding)) return;

        double yaw = Math.toRadians(aircraft.getYaw());
        double horizontalSpeed = Math.hypot(aircraft.motionX, aircraft.motionZ);
        double lookAhead = Math.max(24.0D, Math.min(64.0D, horizontalSpeed * 40.0D));
        double holdX = aircraft.posX - Math.sin(yaw) * lookAhead;
        double holdZ = aircraft.posZ + Math.cos(yaw) * lookAhead;
        List<MissionNode> route = new ArrayList<>();
        route.add(MissionNode.flyTo(holdX, aircraft.posY, holdZ));
        route.add(MissionNode.loiter(40));
        entry.mission = route;
        entry.missionIndex = 0;
        entry.missionNodeTimer = 0;
        entry.attackMode = WingmanEntry.ATK_NONE;
        entry.currentAutoTarget = null;
        entry.autoTargetX = holdX;
        entry.autoTargetY = aircraft.posY;
        entry.autoTargetZ = holdZ;
        entry.autoState = AutonomousState.ENROUTE;
        targetLossHoldRoutes.add(aircraftId);
    }

    private boolean resumeTargetLossHold(UUID aircraftId, WingmanEntry entry) {
        boolean resumed = targetLossHoldRoutes.remove(aircraftId);
        if (resumed && entry != null && entry.autoState == AutonomousState.PARKED) {
            entry.autoState = AutonomousState.ENROUTE;
        }
        return resumed;
    }

    public FpvFlightState fpvFlightState(MCH_EntityAircraft aircraft) {
        FpvFlightStateMachine state = aircraft == null ? null
                : fpvStates.get(aircraft.getUniqueID());
        return state == null ? null : state.getState();
    }

    /**
     * Updates only WingMan's native target coordinates.  A corrupt or
     * teleported contact cannot inject an unbounded waypoint into the native
     * controller; the update is capped to one bounded step.
     */
    public void updateTargetGuidance(MCH_EntityAircraft aircraft, double x, double y, double z,
                                     UUID targetId) {
        if (aircraft == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        boolean reacquiredFromHold = resumeTargetLossHold(aircraft.getUniqueID(), entry);
        if (entry == null || entry.autoState == AutonomousState.RTB
                || entry.autoState == AutonomousState.LANDING
                || (entry.autoState == AutonomousState.PARKED && !reacquiredFromHold)) return;
        if (isFpv(aircraft) && fpvFlightState(aircraft) == FpvFlightState.HOVER) {
            // Reacquisition is the explicit exit request, but the hover route
            // owns this update. Pursuit may write only on the next tick.
            leaveFpvHover(aircraft);
            return;
        }
        y = normalizeHelicopterTargetY(aircraft, y);
        double dx = x - entry.autoTargetX, dy = y - entry.autoTargetY, dz = z - entry.autoTargetZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double maxStep = 128.0D;
        if (length > maxStep) {
            double scale = maxStep / length;
            x = entry.autoTargetX + dx * scale;
            y = entry.autoTargetY + dy * scale;
            z = entry.autoTargetZ + dz * scale;
        }
        if (reacquiredFromHold) {
            // Drop the hold leg's LOITER suffix so it cannot become an
            // accidental orbit before a later attack node is appended.
            entry.mission = new ArrayList<>();
            entry.mission.add(MissionNode.flyTo(x, y, z));
            entry.missionIndex = 0;
            entry.missionNodeTimer = 0;
            entry.attackMode = WingmanEntry.ATK_NONE;
            entry.autoState = AutonomousState.ENROUTE;
        }
        WingmanEntry routeEntry = WingmanRegistry.get(aircraft.getUniqueID());
        MissionNode activeNode = routeEntry == null ? null : routeEntry.currentNode();
        if (activeNode != null && activeNode.type == MissionNode.Type.FLY_TO
                && routeEntry.missionIndex >= 0 && routeEntry.mission != null
                && routeEntry.missionIndex < routeEntry.mission.size()) {
            // Keep the route's active node synchronized with the filtered
            // moving-target setpoint. WingMan refreshes autoTarget from this
            // node at the start of its next tick.
            routeEntry.mission.set(routeEntry.missionIndex,
                    MissionNode.flyTo(x, y, z));
        }
        entry.currentAutoTarget = targetId;
        entry.autoTargetX = x; entry.autoTargetY = y; entry.autoTargetZ = z;
    }

    public boolean armAttack(MCH_EntityAircraft aircraft, double x, double y, double z,
                             double attackRadius, UUID targetId) {
        return armAttack(aircraft, x, y, z, attackRadius, targetId, "");
    }

    public boolean armAttack(MCH_EntityAircraft aircraft, double x, double y, double z,
                             double attackRadius, UUID targetId, String weaponPriority) {
        if (nativeRouteUnsupported(aircraft)) return false;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        if (entry == null || entry.mission == null) return false;
        targetLossHoldRoutes.remove(aircraft.getUniqueID());
        entry.attackMode = WingmanEntry.ATK_AUTO;
        selectAttackWeapon(aircraft, weaponPriority);
        double safeY = normalizeHelicopterTargetY(aircraft, y);
        entry.currentAutoTarget = targetId;
        entry.autoTargetX = x;
        entry.autoTargetY = safeY;
        entry.autoTargetZ = z;
        appendAttackNode(entry, attackRadius);
        return true;
    }

    private void selectAttackWeapon(MCH_EntityAircraft aircraft, String priority) {
        if (aircraft == null || aircraft.getWeapons() == null) return;
        MCH_WeaponSet[] weapons = aircraft.getWeapons();
        if (priority != null && !priority.trim().isEmpty()) {
            String[] names = priority.split(",");
            for (String requested : names) {
                String wanted = requested.trim().toLowerCase(Locale.ROOT);
                for (int i = 0; i < weapons.length; i++) {
                    MCH_WeaponSet weapon = weapons[i];
                    if (weapon != null && weapon.getName() != null
                            && weapon.getName().toLowerCase(Locale.ROOT).equals(wanted)
                            && (weapon.getAmmo() > 0 || weapon.getAmmoReserve() > 0)) {
                        aircraft.updateWeaponID(0, i);
                        return;
                    }
                }
            }
        }
        for (int i = 0; i < weapons.length; i++) {
            MCH_WeaponSet weapon = weapons[i];
            if (weapon != null && (weapon.getAmmo() > 0 || weapon.getAmmoReserve() > 0)) {
                aircraft.updateWeaponID(0, i);
                return;
            }
        }
    }

    @SuppressWarnings("unused")
    public boolean armSquareStrike(MCH_EntityAircraft aircraft, SquareStrikePlan plan,
                                   UUID targetId) {
        if (plan == null || nativeRouteUnsupported(aircraft)) return false;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        if (entry == null || entry.mission == null) return false;
        if (!hasAttackAmmunition(aircraft) && !hasSelfDestructWeaponWithAmmo(aircraft)) return false;
        targetLossHoldRoutes.remove(aircraft.getUniqueID());
        entry.attackMode = WingmanEntry.ATK_AUTO;
        entry.currentAutoTarget = targetId;
        entry.mission.addAll(plan.createNodes());
        return true;
    }

    @SuppressWarnings("unused")
    public boolean armKamikaze(MCH_EntityAircraft aircraft, double x, double y, double z,
                               double attackRadius, UUID targetId) {
        if (supportsKamikaze(aircraft)) return true;
        beginFpvTerminalApproach(aircraft);
        int weaponId = selectSelfDestructWeapon(aircraft);
        if (weaponId < 0) return true;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        if (entry == null || entry.mission == null) return true;
        targetLossHoldRoutes.remove(aircraft.getUniqueID());
        entry.attackMode = WingmanEntry.ATK_AUTO;
        entry.currentAutoTarget = targetId;
        entry.autoTargetX = x;
        entry.autoTargetY = y;
        entry.autoTargetZ = z;
        appendDiveApproach(entry, aircraft, x, y, z);
        // WingMan fires the aircraft's selected seat weapon. Select the
        // destructive loaded set, but the addon owns detonation timing.
        aircraft.updateWeaponID(0, weaponId);
        entry.weaponSeat = 0;
        entry.weaponType = aircraft.getWeapon(weaponId).getName();
        aircraft.getEntityData().setBoolean(KamikazeConfig.ARMED_KEY, true);
        aircraft.getEntityData().setDouble(KamikazeConfig.MIN_SPEED_KEY,
                KamikazeConfig.minimumImpactSpeed);
        return false;
    }

    public boolean fireSelfDestructWeapon(MCH_EntityAircraft aircraft) {
        return fireSelfDestructWeapon(aircraft, false);
    }

    public boolean fireSelfDestructWeapon(MCH_EntityAircraft aircraft, boolean requireNoseTrigger) {
        return detonateSelfDestructWeapon(aircraft,
                aircraft == null ? 0.0D : aircraft.posX,
                aircraft == null ? 0.0D : aircraft.posY,
                aircraft == null ? 0.0D : aircraft.posZ,
                requireNoseTrigger);
    }

    /**
     * Detonates the armed payload immediately at the authoritative terminal
     * point. This is deliberately independent of the helicopter/plane weapon
     * animation path so manual and AI detonation have identical effects.
     */
    public boolean detonateSelfDestructWeapon(MCH_EntityAircraft aircraft,
                                                double x, double y, double z,
                                                boolean requireNoseTrigger) {
        if (aircraft == null || aircraft.world == null || aircraft.world.isRemote) return false;
        boolean noseHit = !requireNoseTrigger || KamikazeImpactDetector.hasNoseImpact(
                aircraft, Math.max(KamikazeConfig.noseTriggerDistance, KamikazeConfig.lowTickDetonationSweep));
        if (requireNoseTrigger && !noseHit) {
            return false;
        }
        int weaponId = selectSelfDestructWeapon(aircraft);
        if (weaponId < 0) return false;
        MCH_WeaponSet weapon = aircraft.getWeapon(weaponId);
        if (weapon == null || weapon.getInfo() == null || !weapon.getInfo().destruct
                || weapon.getInfo().explosion <= 0.0F) return false;
        aircraft.updateWeaponID(0, weaponId);
        if (weapon.getAmmo() <= 0 && weapon.getAmmoReserve() > 0) {
            weapon.manualReload();
        }
        if (weapon.getAmmo() <= 0) {
            RdpUavDebug.debug("Kamikaze",
                    "self-destruct rejected: weapon has no loaded ammo aircraft={} weapon={} reserve={}",
                    aircraft.getUniqueID(), weapon.getName(), weapon.getAmmoReserve());
            return false;
        }
        if (!weapon.canFire()) return false;
        int ammoBefore = weapon.getAmmo();
        MCH_Explosion.ExplosionResult result = MCH_Explosion.newExplosion(
                aircraft.world, null, aircraft, x, y, z,
                weapon.getInfo().explosion, weapon.getInfo().explosionBlock,
                true, true, weapon.getInfo().flaming, true, 0);
        if (result == null) {
            RdpUavDebug.debug("Kamikaze",
                    "self-destruct rejected: native explosion produced no result aircraft={} weapon={}",
                    aircraft.getUniqueID(), weapon.getName());
            return false;
        }
        weapon.setAmmo(ammoBefore - 1);
        aircraft.destruct();
        RdpUavDebug.event("Kamikaze", "PAYLOAD_DETONATED",
                "aircraft", aircraft.getUniqueID(), "weapon", weapon.getName(),
                "x", x, "y", y, "z", z, "ammo", weapon.getAmmo(),
                "nativeExplosionCreated", true, "hitEntity", result.hitEntity,
                "targetDestroyed", false);
        return true;
    }

    public boolean isKamikazeSpeedReached(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return false;
        double speedSquared = aircraft.motionX * aircraft.motionX
                + aircraft.motionY * aircraft.motionY
                + aircraft.motionZ * aircraft.motionZ;
        return speedSquared >= KamikazeConfig.minimumImpactSpeed
                * KamikazeConfig.minimumImpactSpeed;
    }

    public boolean supportsKamikaze(MCH_EntityAircraft aircraft) {
        if (nativeRouteUnsupported(aircraft)) return true;
        return !hasSelfDestructWeapon(aircraft);
    }

    private boolean nativeRouteUnsupported(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return true;
        if (aircraft.isUAV()) return false;
        return aircraft.getAcInfo() == null
                || UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name)
                .getExecutionProfile() != UavExecutionProfile.ONE_WAY;
    }

    public boolean hasSelfDestructWeapon(MCH_EntityAircraft aircraft) {
        if (aircraft == null || aircraft.getWeapons() == null) return false;
        for (MCH_WeaponSet weapon : aircraft.getWeapons()) {
            if (weapon != null && weapon.getInfo() != null && weapon.getInfo().destruct) return true;
        }
        return false;
    }

    public boolean hasSelfDestructWeaponWithAmmo(MCH_EntityAircraft aircraft) {
        return selectSelfDestructWeapon(aircraft) >= 0;
    }

    private int selectSelfDestructWeapon(MCH_EntityAircraft aircraft) {
        if (aircraft == null || aircraft.getWeapons() == null) return -1;
        MCH_WeaponSet[] weapons = aircraft.getWeapons();
        for (int i = 0; i < weapons.length; i++) {
            MCH_WeaponSet weapon = weapons[i];
            if (weapon != null && weapon.getInfo() != null && weapon.getInfo().destruct
                    && (weapon.getAmmo() > 0 || weapon.getAmmoReserve() > 0)) return i;
        }
        return -1;
    }

    /** Returns true only when the aircraft is below the mission reserve. */
    public boolean isFuelInsufficientForMission(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return true;
        if (UavEnergyHandler.hasOperatingEnergy(aircraft)) return false;
        if (!aircraft.canUseFuel()) return false;
        return aircraft.getMaxFuel() <= 0
                || aircraft.getFuel() <= Math.max(1, aircraft.getMaxFuel() / 5);
    }

    /** @deprecated use {@link #isFuelInsufficientForMission(MCH_EntityAircraft)}. */
    @Deprecated
    public boolean hasFuelForMission(MCH_EntityAircraft aircraft) {
        return isFuelInsufficientForMission(aircraft);
    }

    public boolean hasAttackAmmunition(MCH_EntityAircraft aircraft) {
        if (aircraft == null || aircraft.getWeapons() == null) return false;
        for (MCH_WeaponSet weapon : aircraft.getWeapons()) {
            if (weapon != null && (weapon.getAmmo() > 0 || weapon.getAmmoReserve() > 0)) return true;
        }
        return false;
    }

    private void appendAttackNode(WingmanEntry entry, double attackRadius) {
        if (entry.mission.isEmpty()
                || entry.mission.get(entry.mission.size() - 1).type != MissionNode.Type.ATTACK) {
            entry.mission.add(MissionNode.attack(attackRadius));
        }
    }

    private void appendDiveApproach(WingmanEntry entry, MCH_EntityAircraft aircraft,
                                    double x, double y, double z) {
        TerminalApproachPlanner.Plan approach = TerminalApproachPlanner.plan(
                aircraft.posX, aircraft.posY, aircraft.posZ, x, y, z,
                aircraft.rotationYaw);
        entry.mission.add(MissionNode.flyTo(approach.getApproachX(),
                approach.getApproachY(), approach.getApproachZ()));
        // A single approach waypoint is not enough to guarantee that native
        // WingMan enters the target-facing terminal leg. Keep the existing
        // payload/detonation owner, but finish the route at the objective so
        // the aircraft's native nose points through the target before impact.
        entry.mission.add(MissionNode.flyTo(approach.getTargetX(),
                approach.getTargetY(), approach.getTargetZ()));
    }

    public void release(MCH_EntityAircraft aircraft) {
        if (aircraft != null) {
            MissionMovementAuthority.releaseControl(aircraft.getUniqueID());
            aircraft.getEntityData().removeTag(KamikazeConfig.ARMED_KEY);
            aircraft.getEntityData().removeTag(KamikazeConfig.MIN_SPEED_KEY);
            WingmanRegistry.remove(aircraft.getUniqueID());
            aircraftStates.remove(aircraft.getUniqueID());
            targetTrackers.remove(aircraft.getUniqueID());
            fpvStates.remove(aircraft.getUniqueID());
            terminalWaypointReplacements.remove(aircraft.getUniqueID());
            targetLossHoldRoutes.remove(aircraft.getUniqueID());
            terminalPreviousDistance.remove(aircraft.getUniqueID());
            terminalLastDiagnosticTick.remove(aircraft.getUniqueID());
            terminalMetrics.remove(aircraft.getUniqueID());
            terminalLastClassification.remove(aircraft.getUniqueID());
        }
    }

    /**
     * Applies one bounded native route correction toward a moving entity.
     * Target reads are O(1); no entity search, chunk load, or terrain scan is
     * performed here.
     */
    public void updateMovingEntityGuidance(MCH_EntityAircraft aircraft,
                                           Entity target,
                                           double guidanceY, long tick) {
        if (aircraft == null || target == null) return;
        UUID aircraftId = aircraft.getUniqueID();
        AircraftState observed = aircraftStates.get(aircraftId);
        if (observed == null) {
            observed = aircraftStates.observe(aircraft, getEntry(aircraft), null, tick);
        }
        UavVehicleProfile vehicleProfile = profileFor(aircraft);
        double targetRadius = targetRadius(target);
        FlightGuidancePlanner.Plan plan = FlightGuidancePlanner.plan(observed,
                target.posX, guidanceY, target.posZ,
                target.motionX, target.motionY, target.motionZ,
                targetRadius, vehicleProfile, false);
        if (!plan.isFeasible()) {
            RdpUavDebug.event("TerminalGuidance", "GUIDANCE_REJECTED",
                    "aircraft", aircraftId, "target", target.getUniqueID(),
                    "tick", tick, "reason", plan.getReason());
            return;
        }
        ContinuousTargetTracker tracker = targetTrackers.get(aircraftId);
        if (tracker == null) {
            tracker = new ContinuousTargetTracker(trackingParameters(aircraft));
            targetTrackers.put(aircraftId, tracker);
        }
        ContinuousTargetTracker.Solution solution = tracker.update(tick,
                aircraft.posX, aircraft.posY, aircraft.posZ,
                target.posX, guidanceY, target.posZ,
                target.motionX, target.motionY, target.motionZ);
        updateTargetGuidance(aircraft,
                blendGuidance(plan.getX(), solution.getX()),
                clamp(plan.getY(), vehicleProfile.getMinimumExecutionAltitude(),
                        vehicleProfile.getMaximumExecutionAltitude()),
                blendGuidance(plan.getZ(), solution.getZ()), target.getUniqueID());
    }

    /**
     * Replaces the active terminal waypoint with one bounded, moving-target
     * correction. WingMan retains route sequencing; the RDP flight computer
     * owns the leased movement setpoints. Terminal guidance never appends an
     * orbit or loiter node.
     */
    public void updateTerminalGuidance(MCH_EntityAircraft aircraft,
                                       Entity target,
                                       double guidanceY, long tick,
                                       UUID missionId, String phase, String controlOwner) {
        if (aircraft == null || target == null || nativeRouteUnsupported(aircraft)) return;
        UUID aircraftId = aircraft.getUniqueID();
        AircraftState observed = aircraftStates.get(aircraftId);
        if (observed == null) {
            observed = aircraftStates.observe(aircraft, getEntry(aircraft), null, tick);
        }
        FlightGuidancePlanner.Plan plan = FlightGuidancePlanner.plan(observed,
                target.posX, guidanceY, target.posZ,
                target.motionX, target.motionY, target.motionZ,
                targetRadius(target), profileFor(aircraft), true);
        if (!plan.isFeasible()) {
            RdpUavDebug.event("TerminalGuidance", "GUIDANCE_REJECTED",
                    "mission", missionId, "aircraft", aircraftId,
                    "target", target.getUniqueID(), "tick", tick,
                    "reason", plan.getReason());
            return;
        }
        ContinuousTargetTracker tracker = targetTrackers.get(aircraftId);
        if (tracker == null) {
            tracker = new ContinuousTargetTracker(terminalTrackingParameters(aircraft));
            targetTrackers.put(aircraftId, tracker);
        }
        ContinuousTargetTracker.Solution solution = tracker.update(tick,
                aircraft.posX, aircraft.posY, aircraft.posZ,
                target.posX, guidanceY, target.posZ,
                target.motionX, target.motionY, target.motionZ);
        if (!solution.isUpdated()) return;

        WingmanEntry entry = WingmanRegistry.get(aircraftId);
        boolean resumedHold = resumeTargetLossHold(aircraftId, entry);
        if (entry == null || entry.autoState == AutonomousState.RTB
                || entry.autoState == AutonomousState.LANDING
                || (entry.autoState == AutonomousState.PARKED && !resumedHold)) return;
        double plannedX = blendGuidance(plan.getX(), solution.getX());
        double plannedZ = blendGuidance(plan.getZ(), solution.getZ());
        double safeY = normalizeHelicopterTargetY(aircraft,
                blendGuidance(plan.getY(), solution.getY()));
        TerminalGuidanceMetrics metrics = terminalMetrics.get(aircraftId);
        if (metrics == null || !target.getUniqueID().equals(metrics.getTargetId())) {
            metrics = new TerminalGuidanceMetrics(target.getUniqueID(), tick);
            terminalMetrics.put(aircraftId, metrics);
            RdpUavDebug.event("TerminalGuidance", "TERMINAL_METRIC_START",
                    "mission", missionId, "target", target.getUniqueID(), "tick", tick,
                    "phase", phase, "controlOwner", controlOwner);
        }
        double targetDx = target.posX - aircraft.posX;
        double targetDy = target.posY - aircraft.posY;
        double targetDz = target.posZ - aircraft.posZ;
        metrics.record(tick, Math.sqrt(targetDx * targetDx + targetDy * targetDy
                + targetDz * targetDz));
        TerminalGuidanceMetrics.Classification classification = metrics.getClassification();
        if (classification != terminalLastClassification.get(aircraftId)) {
            terminalLastClassification.put(aircraftId, classification);
            RdpUavDebug.event("TerminalGuidance", "TERMINAL_CONVERGENCE_STATE",
                    "mission", missionId, "target", target.getUniqueID(),
                    "tick", tick, "classification", classification,
                    "distance", metrics.getLatestDistance(),
                    "closingRate", metrics.getClosingRate(),
                    "minimumDistance", metrics.getMinimumDistance(),
                    "phase", phase, "controlOwner", controlOwner);
        }
        if (resumedHold) {
            entry.mission = new ArrayList<>();
            entry.mission.add(MissionNode.flyTo(plannedX, safeY, plannedZ));
            entry.missionIndex = 0;
            entry.missionNodeTimer = 0;
            entry.attackMode = WingmanEntry.ATK_NONE;
            entry.autoState = AutonomousState.ENROUTE;
        }
        int activeIndex = entry.missionIndex;
        MissionNode current = entry.currentNode();
        if (current == null) {
            entry.mission = new ArrayList<>();
            entry.mission.add(MissionNode.flyTo(plan.getX(), safeY, plan.getZ()));
            entry.missionIndex = 0;
            activeIndex = 0;
        } else if (current.type != MissionNode.Type.FLY_TO) {
            RdpUavDebug.event("TerminalGuidance", "WAYPOINT_UPDATE_REJECTED",
                    "mission", missionId, "aircraft", aircraftId, "target", target.getUniqueID(),
                    "tick", tick, "reason", "NATIVE_NODE_NOT_FLY_TO",
                    "node", current.type, "missionIndex", entry.missionIndex);
            return;
        } else {
            double waypointDx = plannedX - current.x;
            double waypointDy = safeY - current.y;
            double waypointDz = plannedZ - current.z;
            if (waypointDx * waypointDx + waypointDy * waypointDy + waypointDz * waypointDz
                    < 4.0D) {
                return;
            }
            // MissionNode is immutable, but replacing only the active node
            // preserves WingMan's route suffix, mission index, and timer.
            entry.mission.set(activeIndex, MissionNode.flyTo(
                    plannedX, safeY, plannedZ));
        }
        entry.currentAutoTarget = target.getUniqueID();
        entry.autoTargetX = plannedX;
        entry.autoTargetY = safeY;
        entry.autoTargetZ = plannedZ;
        int replacements = terminalWaypointReplacements.containsKey(aircraftId)
                ? terminalWaypointReplacements.get(aircraftId) + 1 : 1;
        terminalWaypointReplacements.put(aircraftId, replacements);
        RdpUavDebug.event("TerminalGuidance", "WAYPOINT_UPDATE_ACCEPTED",
                "mission", missionId, "aircraft", aircraftId, "target", target.getUniqueID(),
                "tick", tick, "missionIndex", activeIndex,
                "nodeTimer", entry.missionNodeTimer,
                "routeSize", entry.mission == null ? 0 : entry.mission.size(),
                "replacements", replacements);
        emitTerminalDiagnostics(aircraft, target, entry, tick, missionId, phase,
                controlOwner, replacements, solution);
        if (isFpv(aircraft)) beginFpvTerminalApproach(aircraft);
    }

    public void markTerminalTimeout(UUID aircraftId) {
        TerminalGuidanceMetrics metrics = terminalMetrics.get(aircraftId);
        if (metrics != null) metrics.timeout();
    }

    public String terminalGuidanceFailureReason(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return null;
        TerminalGuidanceMetrics metrics = terminalMetrics.get(aircraft.getUniqueID());
        if (metrics == null) return null;
        TerminalGuidanceMetrics.Classification classification = metrics.getClassification();
        if (classification == TerminalGuidanceMetrics.Classification.ORBITING
                || classification == TerminalGuidanceMetrics.Classification.DIVERGING
                || classification == TerminalGuidanceMetrics.Classification.OVERSHOT
                || classification == TerminalGuidanceMetrics.Classification.TIMEOUT
                || terminalWaypointReplacements.getOrDefault(aircraft.getUniqueID(), 0) > 64) {
            return "TERMINAL_GUIDANCE_" + classification.name();
        }
        return null;
    }

    private void emitTerminalDiagnostics(MCH_EntityAircraft aircraft, Entity target,
                                         WingmanEntry entry, long tick, UUID missionId,
                                         String phase, String controlOwner,
                                         int replacements,
                                         ContinuousTargetTracker.Solution solution) {
        if (!RdpUavDebug.isEnabled()
                || tick - (terminalLastDiagnosticTick.getOrDefault(aircraft.getUniqueID(), Long.MIN_VALUE))
                < KamikazeConfig.terminalDiagnosticsIntervalTicks) return;
        terminalLastDiagnosticTick.put(aircraft.getUniqueID(), tick);
        double dx = target.posX - aircraft.posX;
        double dy = target.posY - aircraft.posY;
        double dz = target.posZ - aircraft.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double distance = Math.sqrt(horizontal * horizontal + dy * dy);
        Double previous = terminalPreviousDistance.put(aircraft.getUniqueID(), distance);
        String trend = previous == null ? "INITIAL"
                : distance < previous - 0.05D ? "CONVERGING"
                : distance > previous + 0.05D ? "DIVERGING" : "STABLE";
        net.minecraft.util.math.AxisAlignedBB bounds = target.getEntityBoundingBox();
        double aimX = (bounds.minX + bounds.maxX) * 0.5D;
        double aimY = (bounds.minY + bounds.maxY) * 0.5D;
        double aimZ = (bounds.minZ + bounds.maxZ) * 0.5D;
        RdpUavDebug.event("TerminalGuidance", "UPDATE",
                "mission", missionId, "aircraft", aircraft.getUniqueID(),
                "target", target.getUniqueID(), "phase", phase,
                "controlOwner", controlOwner, "x", aircraft.posX,
                "y", aircraft.posY, "z", aircraft.posZ,
                "targetX", target.posX, "targetY", target.posY, "targetZ", target.posZ,
                "aimX", aimX, "aimY", aimY, "aimZ", aimZ,
                "motionX", aircraft.motionX, "motionY", aircraft.motionY,
                "motionZ", aircraft.motionZ, "targetMotionX", target.motionX,
                "targetMotionY", target.motionY, "targetMotionZ", target.motionZ,
                "horizontalDistance", horizontal, "distance", distance,
                "verticalError", aimY - aircraft.posY,
                "heading", aircraft.getYaw(),
                "bearing", Math.toDegrees(Math.atan2(dz, dx)),
                "waypointX", solution.getX(), "waypointY", solution.getY(),
                "waypointZ", solution.getZ(), "replacements", replacements,
                "node", entry.currentNode() == null ? null : entry.currentNode().type,
                "routeSize", entry.mission == null ? 0 : entry.mission.size(),
                "trend", trend, "classification",
                terminalMetrics.get(aircraft.getUniqueID()).getClassification(),
                "closingRate", terminalMetrics.get(aircraft.getUniqueID()).getClosingRate(),
                "minimumDistance", terminalMetrics.get(aircraft.getUniqueID()).getMinimumDistance(),
                "entryDistance", terminalMetrics.get(aircraft.getUniqueID()).getDistanceAtEntry(),
                "elapsedTerminalTicks", tick
                        - terminalMetrics.get(aircraft.getUniqueID()).getEntryTick());
    }

    private ContinuousTargetTracker.Parameters terminalTrackingParameters(
            MCH_EntityAircraft aircraft) {
        return new ContinuousTargetTracker.Parameters(0.0D, 0.65D,
                isFpv(aircraft) ? 0.22D : 0.30D, 0.5D, 24.0D, 2);
    }

    private ContinuousTargetTracker.Parameters trackingParameters(MCH_EntityAircraft aircraft) {
        String name = aircraft.getAcInfo() == null ? "" : aircraft.getAcInfo().name;
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (normalized.contains("shahed") || normalized.contains("mq9")
                || UavVehicleProfiles.forAircraft(name).getExecutionProfile()
                == UavExecutionProfile.LONG_RANGE_REUSABLE_UAV) {
            return new ContinuousTargetTracker.Parameters(8.0D, 0.18D,
                    0.12D, 1.5D, 96.0D, 4);
        }

        return new ContinuousTargetTracker.Parameters(3.0D, 0.45D,
                0.35D, 0.75D, 48.0D, 2);
    }

    private UavVehicleProfile profileFor(MCH_EntityAircraft aircraft) {
        return UavVehicleProfiles.forAircraft(aircraft == null || aircraft.getAcInfo() == null
                ? null : aircraft.getAcInfo().name);
    }

    private static double targetRadius(Entity target) {
        if (target == null) return 1.0D;
        net.minecraft.util.math.AxisAlignedBB bounds = target.getEntityBoundingBox();
        double width = Math.max(bounds.maxX - bounds.minX, bounds.maxZ - bounds.minZ);
        return Math.max(1.0D, width * 0.5D);
    }

    private static double blendGuidance(double planned, double smoothed) {
        if (!Double.isFinite(smoothed)) return planned;
        return planned * 0.75D + smoothed * 0.25D;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private boolean isFpv(MCH_EntityAircraft aircraft) {
        return aircraft != null && aircraft.getAcInfo() != null
                && UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name).isHelicopterProfile();
    }

    private void leaveFpvHover(MCH_EntityAircraft aircraft) {
        if (!isFpv(aircraft)) return;
        FpvFlightStateMachine state = fpvStates.get(aircraft.getUniqueID());
        if (state != null && state.getState() == FpvFlightState.HOVER) {
            state.moveToTarget();
        }
    }

    private void beginFpvTerminalApproach(MCH_EntityAircraft aircraft) {
        if (!isFpv(aircraft)) return;
        FpvFlightStateMachine state = fpvStates.get(aircraft.getUniqueID());
        if (state == null) {
            state = new FpvFlightStateMachine();
            fpvStates.put(aircraft.getUniqueID(), state);
        }
        state.beginTerminalApproach();
    }

    @SuppressWarnings("unused")
    public boolean isDispatched(MCH_EntityAircraft aircraft) {
        return aircraft != null && WingmanRegistry.get(aircraft.getUniqueID()) != null;
    }

    public WingmanEntry getEntry(MCH_EntityAircraft aircraft) {
        return aircraft == null ? null : WingmanRegistry.get(aircraft.getUniqueID());
    }

    @SuppressWarnings("unused")
    public FlightState readState(MCH_EntityAircraft aircraft) {
        return FlightState.capture(aircraft, getEntry(aircraft));
    }

    /**
     * Ask WingMan to stop the mission and recover the aircraft.  This is
     * intentionally a recovery-only operation; it never arms weapons.
     */
    public boolean requestReturn(MCH_EntityAircraft aircraft, String reason) {
        if (aircraft != null) targetLossHoldRoutes.remove(aircraft.getUniqueID());
        WingmanEntry entry = getEntry(aircraft);
        if (entry == null) {
            RdpUavDebug.debug("WingMan", "return rejected: missing entry aircraft={}",
                    aircraft == null ? null : aircraft.getUniqueID());
            return true;
        }
        entry.attackMode = WingmanEntry.ATK_NONE;
        entry.rtbReason = reason == null ? "RDP_MISSION_COMPLETE" : reason;
        entry.autoState = AutonomousState.RTB;
        RdpUavDebug.debug("WingMan", "return requested aircraft={} reason={}",
                aircraft.getUniqueID(), entry.rtbReason);
        return false;
    }

    public boolean isReturning(MCH_EntityAircraft aircraft) {
        WingmanEntry entry = getEntry(aircraft);
        return entry != null && entry.autoState == AutonomousState.RTB;
    }

    /**
     * Reports completion of WingMan's real attack pass, not merely arrival at
     * the target. The attack node must have been consumed or WingMan must
     * have entered recovery before the mission may advance.
     */
    public boolean isAttackComplete(MCH_EntityAircraft aircraft) {
        WingmanEntry entry = getEntry(aircraft);
        if (entry == null || entry.mission == null) return false;
        if (nativeAttackCompletions.remove(aircraft.getUniqueID())) return true;
        if (entry.autoState == AutonomousState.RTB
                || entry.autoState == AutonomousState.LANDING
                || entry.autoState == AutonomousState.TAXI_IN
                || entry.autoState == AutonomousState.PARKED) {
            return true;
        }
        int attackIndex = -1;
        for (int i = 0; i < entry.mission.size(); i++) {
            if (entry.mission.get(i).type == MissionNode.Type.ATTACK) {
                attackIndex = i;
                break;
            }
        }
        if (attackIndex < 0 || entry.missionIndex <= attackIndex) return false;
        com.norwood.mcheli.wingman.mission.MissionNode current = entry.currentNode();
        return current == null || current.type != MissionNode.Type.ATTACK;
    }

    /** Records a launch performed through a native weapon API, without touching flight physics. */
    public void markNativeAttackComplete(MCH_EntityAircraft aircraft) {
        if (aircraft != null) nativeAttackCompletions.add(aircraft.getUniqueID());
    }

    public boolean isParked(MCH_EntityAircraft aircraft) {
        WingmanEntry entry = getEntry(aircraft);
        return entry != null && entry.autoState == AutonomousState.PARKED;
    }

    public UUID getAircraftId(MCH_EntityAircraft aircraft) {
        return aircraft == null ? null : aircraft.getUniqueID();
    }

    @SuppressWarnings("unused")
    public void savePlan(String name, List<MissionNode> nodes) {
        if (name == null || name.trim().isEmpty() || nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("WingMan plan name and nodes are required");
        }
        MissionPlan.put(name, new ArrayList<>(nodes));
    }
}
