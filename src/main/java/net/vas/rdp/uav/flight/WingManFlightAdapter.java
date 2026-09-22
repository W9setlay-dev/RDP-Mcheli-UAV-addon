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
import com.norwood.mcheli.weapon.MCH_WeaponParam;
import net.minecraft.entity.Entity;
import net.vas.rdp.uav.RdpUavDebug;
import net.vas.rdp.uav.energy.UavEnergyHandler;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.UavVehicleProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/**
 * The only RDP-facing boundary for WingMan control.
 */
public final class WingManFlightAdapter {
    private final Set<UUID> nativeAttackCompletions = new HashSet<>();
    private final Map<UUID, ContinuousTargetTracker> targetTrackers = new HashMap<>();
    private final Map<UUID, FpvFlightStateMachine> fpvStates = new HashMap<>();
    private final Map<UUID, FlightCommand> fpvCommands = new HashMap<>();
    private final Map<UUID, Integer> terminalWaypointReplacements = new HashMap<>();
    private final Map<UUID, Double> terminalPreviousDistance = new HashMap<>();
    private final Map<UUID, Long> terminalLastDiagnosticTick = new HashMap<>();
    private final Map<UUID, TerminalGuidanceMetrics> terminalMetrics = new HashMap<>();
    private final Map<UUID, TerminalGuidanceMetrics.Classification> terminalLastClassification
            = new HashMap<>();
    private static final double KAMIKAZE_ATTACK_ANGLE_DEGREES = 35.0D;
    private static final double MIN_DIVE_RUN_METERS = 32.0D;
    private static final double MAX_DIVE_RUN_METERS = 160.0D;
    public boolean dispatch(MCH_EntityAircraft aircraft, double x, double y, double z,
                            double orbitRadius, int orbitTicks) {
        return dispatch(aircraft, x, y, z, orbitRadius, orbitTicks, false, null);
    }

    private static boolean usesHelicopterFlightModel(MCH_EntityAircraft aircraft) {
        if (aircraft == null || aircraft.getAcInfo() == null) return aircraft instanceof MCH_EntityHeli;
        UavVehicleProfile profile = UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name);
        return aircraft instanceof MCH_EntityHeli || profile.isHelicopterProfile();
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
        if (supportsAutonomousRoute(aircraft)) {
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
        fpvStates.remove(aircraftId);
        fpvCommands.remove(aircraftId);
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
            fpvCommands.put(aircraftId,
                    FlightCommand.approach(1.0D, safeY - aircraft.posY, 1.0D));
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

    /**
     * Rebuilds only the native WingMan route.  The aircraft remains under
     * WingMan control; this never writes position, yaw, pitch, or motion.
     */
    public boolean refreshObservationRoute(MCH_EntityAircraft aircraft, double x, double y, double z,
                                           double orbitRadius, boolean orbit, UUID targetId) {
        if (supportsAutonomousRoute(aircraft)) return false;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        if (entry == null || entry.mission == null || entry.autoState == AutonomousState.RTB
                || entry.autoState == AutonomousState.LANDING
                || entry.autoState == AutonomousState.PARKED) return false;
        if (isFpv(aircraft)) {
            FpvFlightState state = fpvFlightState(aircraft);
            if (state == FpvFlightState.HOVER || state == FpvFlightState.TERMINAL_APPROACH) {
                return false;
            }
            fpvCommands.put(aircraft.getUniqueID(),
                    FlightCommand.approach(1.0D, y - aircraft.posY, 1.0D));
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
        if (supportsAutonomousRoute(aircraft)) return false;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        if (entry == null || entry.mission == null
                || entry.autoState == AutonomousState.RTB
                || entry.autoState == AutonomousState.LANDING
                || entry.autoState == AutonomousState.PARKED) return false;
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
        return entry != null && entry.mission != null && entry.missionIndex > 0
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
        if (!isFpv(aircraft)) return;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        if (entry == null || entry.mission == null
                || entry.autoState == AutonomousState.RTB
                || entry.autoState == AutonomousState.LANDING
                || entry.autoState == AutonomousState.PARKED) return;
        FpvFlightStateMachine state = fpvStates.get(aircraft.getUniqueID());
        if (state == null) {
            state = new FpvFlightStateMachine();
            fpvStates.put(aircraft.getUniqueID(), state);
        }
        if (state.getState() == FpvFlightState.HOVER) return;
        if (state.getState() == FpvFlightState.TERMINAL_APPROACH) return;
        state.hover();
        fpvCommands.put(aircraft.getUniqueID(),
                FlightCommand.hover());
        entry.mission = new ArrayList<>();
        entry.mission.add(MissionNode.flyTo(aircraft.posX, aircraft.posY, aircraft.posZ));
        entry.mission.add(MissionNode.loiter(40));
        entry.missionIndex = 0;
        entry.missionNodeTimer = 0;
        entry.autoTargetX = aircraft.posX;
        entry.autoTargetY = aircraft.posY;
        entry.autoTargetZ = aircraft.posZ;
        entry.autoState = AutonomousState.ENROUTE;
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
        if (entry == null || entry.autoState == AutonomousState.RTB
                || entry.autoState == AutonomousState.LANDING || entry.autoState == AutonomousState.PARKED) return;
        if (isFpv(aircraft) && fpvFlightState(aircraft) == FpvFlightState.HOVER) {
            // Reacquisition is the explicit exit request, but the hover route
            // owns this update. Pursuit may write only on the next tick.
            leaveFpvHover(aircraft);
            return;
        }
        double dx = x - entry.autoTargetX, dy = y - entry.autoTargetY, dz = z - entry.autoTargetZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double maxStep = 128.0D;
        if (length > maxStep) {
            double scale = maxStep / length;
            x = entry.autoTargetX + dx * scale;
            y = entry.autoTargetY + dy * scale;
            z = entry.autoTargetZ + dz * scale;
        }
        double safeY = normalizeHelicopterTargetY(aircraft, y);
        if (isFpv(aircraft)) {
            FpvFlightState state = fpvFlightState(aircraft);
            if (state != FpvFlightState.TERMINAL_APPROACH) {
                fpvCommands.put(aircraft.getUniqueID(),
                        FlightCommand.approach(1.0D, safeY - aircraft.posY, 1.0D));
            }
        }
        entry.currentAutoTarget = targetId;
        entry.autoTargetX = x; entry.autoTargetY = safeY; entry.autoTargetZ = z;
    }

    public boolean armAttack(MCH_EntityAircraft aircraft, double x, double y, double z,
                             double attackRadius, UUID targetId) {
        return armAttack(aircraft, x, y, z, attackRadius, targetId, "");
    }

    public boolean armAttack(MCH_EntityAircraft aircraft, double x, double y, double z,
                             double attackRadius, UUID targetId, String weaponPriority) {
        if (supportsAutonomousRoute(aircraft)) return false;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        if (entry == null || entry.mission == null) return false;
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
        if (plan == null || supportsAutonomousRoute(aircraft)) return false;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        if (entry == null || entry.mission == null) return false;
        if (!hasAttackAmmunition(aircraft) && !hasSelfDestructWeaponWithAmmo(aircraft)) return false;
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
        if (isFpv(aircraft)) {
            fpvCommands.put(aircraft.getUniqueID(),
                    FlightCommand.terminalApproach(1.0D, y - aircraft.posY, 1.0D));
        }
        int weaponId = selectSelfDestructWeapon(aircraft);
        if (weaponId < 0) return true;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        if (entry == null || entry.mission == null) return true;
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
        if (aircraft == null) return false;
        if (requireNoseTrigger && !KamikazeImpactDetector.hasNoseImpact(aircraft, KamikazeConfig.noseTriggerDistance)) {
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
        if (!(aircraft instanceof MCH_EntityHeli)) {
            int ammoBefore = weapon.getAmmo();
            // Plane and other non-heli airframes use the direct payload detonation branch.
            MCH_Explosion.newExplosion(
                    aircraft.world,
                    null,
                    aircraft,
                    aircraft.posX,
                    aircraft.posY,
                    aircraft.posZ,
                    weapon.getInfo().explosion,
                    weapon.getInfo().explosionBlock,
                    true,
                    true,
                    weapon.getInfo().flaming,
                    true,
                    0);
            weapon.setAmmo(ammoBefore - 1);
            aircraft.destruct();
            RdpUavDebug.debug("Kamikaze",
                    "self-destruct detonated directly aircraft={} weapon={} ammo={}",
                    aircraft.getUniqueID(), weapon.getName(), weapon.getAmmo());
            return true;
        }
        MCH_WeaponParam parameter = new MCH_WeaponParam();
        parameter.entity = aircraft;
        parameter.user = aircraft;
        parameter.setPosAndRot(aircraft.posX, aircraft.posY, aircraft.posZ,
                aircraft.getYaw(), aircraft.getPitch());
        parameter.rotRoll = aircraft.getRoll();
        parameter.isInfinity = false;
        boolean fired = weapon.use(parameter);
        RdpUavDebug.debug("Kamikaze", "self-destruct fire aircraft={} weapon={} fired={} ammo={}",
                aircraft.getUniqueID(), weapon.getName(), fired, weapon.getAmmo());
        return fired;
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
        if (supportsAutonomousRoute(aircraft)) return true;
        return !hasSelfDestructWeapon(aircraft);
    }

    private boolean supportsAutonomousRoute(MCH_EntityAircraft aircraft) {
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

    public boolean hasFuelForMission(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return true;
        if (UavEnergyHandler.hasOperatingEnergy(aircraft)) return false;
        if (!aircraft.canUseFuel()) return false;
        return aircraft.getMaxFuel() <= 0
                || aircraft.getFuel() <= Math.max(1, aircraft.getMaxFuel() / 5);
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
        double dx = aircraft.posX - x;
        double dz = aircraft.posZ - z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance < 0.001D) {
            dx = 0.0D;
            dz = 1.0D;
            horizontalDistance = 1.0D;
        }

        double altitudeDifference = Math.max(0.0D, aircraft.posY - y);
        double angleRadians = Math.toRadians(KAMIKAZE_ATTACK_ANGLE_DEGREES);
        double diveRun = altitudeDifference / Math.tan(angleRadians);
        diveRun = Math.max(MIN_DIVE_RUN_METERS, Math.min(MAX_DIVE_RUN_METERS, diveRun));

        double approachX = x + (dx / horizontalDistance) * diveRun;
        double approachZ = z + (dz / horizontalDistance) * diveRun;
        double approachY = y + Math.tan(angleRadians) * diveRun;
        entry.mission.add(MissionNode.flyTo(approachX, approachY, approachZ));
        // A single approach waypoint is not enough to guarantee that native
        // WingMan enters the target-facing terminal leg. Keep the existing
        // payload/detonation owner, but finish the route at the objective so
        // the aircraft's native nose points through the target before impact.
        entry.mission.add(MissionNode.flyTo(x, y, z));
    }

    public void release(MCH_EntityAircraft aircraft) {
        if (aircraft != null) {
            aircraft.getEntityData().removeTag(KamikazeConfig.ARMED_KEY);
            aircraft.getEntityData().removeTag(KamikazeConfig.MIN_SPEED_KEY);
            WingmanRegistry.remove(aircraft.getUniqueID());
            targetTrackers.remove(aircraft.getUniqueID());
            fpvStates.remove(aircraft.getUniqueID());
            fpvCommands.remove(aircraft.getUniqueID());
            terminalWaypointReplacements.remove(aircraft.getUniqueID());
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
        ContinuousTargetTracker tracker = targetTrackers.get(aircraftId);
        if (tracker == null) {
            tracker = new ContinuousTargetTracker(trackingParameters(aircraft));
            targetTrackers.put(aircraftId, tracker);
        }
        ContinuousTargetTracker.Solution solution = tracker.update(tick,
                aircraft.posX, aircraft.posY, aircraft.posZ,
                target.posX, guidanceY, target.posZ,
                target.motionX, target.motionY, target.motionZ);
        updateTargetGuidance(aircraft, solution.getX(), solution.getY(),
                solution.getZ(), target.getUniqueID());
    }

    /**
     * Replaces the active terminal waypoint with one bounded, moving-target
     * correction. The native WingMan route remains the movement authority;
     * terminal guidance never appends an orbit or loiter node.
     */
    public void updateTerminalGuidance(MCH_EntityAircraft aircraft,
                                       Entity target,
                                       double guidanceY, long tick,
                                       UUID missionId, String phase, String controlOwner) {
        if (aircraft == null || target == null || supportsAutonomousRoute(aircraft)) return;
        UUID aircraftId = aircraft.getUniqueID();
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
        if (entry == null || entry.autoState == AutonomousState.RTB
                || entry.autoState == AutonomousState.LANDING
                || entry.autoState == AutonomousState.PARKED) return;
        double safeY = normalizeHelicopterTargetY(aircraft, solution.getY());
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
        entry.mission = new ArrayList<>();
        entry.mission.add(MissionNode.flyTo(solution.getX(), safeY, solution.getZ()));
        entry.missionIndex = 0;
        entry.missionNodeTimer = 0;
        entry.currentAutoTarget = target.getUniqueID();
        entry.autoTargetX = solution.getX();
        entry.autoTargetY = safeY;
        entry.autoTargetZ = solution.getZ();
        entry.autoState = AutonomousState.ENROUTE;
        int replacements = terminalWaypointReplacements.containsKey(aircraftId)
                ? terminalWaypointReplacements.get(aircraftId) + 1 : 1;
        terminalWaypointReplacements.put(aircraftId, replacements);
        emitTerminalDiagnostics(aircraft, target, entry, tick, missionId, phase,
                controlOwner, replacements, solution);
        if (isFpv(aircraft)) {
            beginFpvTerminalApproach(aircraft);
            fpvCommands.put(aircraftId,
                    FlightCommand.terminalApproach(1.0D, safeY - aircraft.posY, 1.0D));
        }
    }

    private void emitTerminalDiagnostics(MCH_EntityAircraft aircraft, Entity target,
                                         WingmanEntry entry, long tick, UUID missionId,
                                         String phase, String controlOwner,
                                         int replacements,
                                         ContinuousTargetTracker.Solution solution) {
        if (!RdpUavDebug.isEnabled()
                || tick - (terminalLastDiagnosticTick.containsKey(aircraft.getUniqueID())
                ? terminalLastDiagnosticTick.get(aircraft.getUniqueID()) : Long.MIN_VALUE)
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
    public FlightCommand fpvFlightCommand(MCH_EntityAircraft aircraft) {
        return aircraft == null ? null : fpvCommands.get(aircraft.getUniqueID());
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
