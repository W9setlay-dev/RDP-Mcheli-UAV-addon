package net.vas.rdp.uav.flight;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.helicopter.MCH_EntityHeli;
import com.norwood.mcheli.MCH_Explosion;
import com.norwood.mcheli.wingman.mission.MissionNode;
import com.norwood.mcheli.wingman.mission.MissionOrder;
import com.norwood.mcheli.wingman.mission.MissionPlan;
import com.norwood.mcheli.wingman.mission.MissionType;
import com.norwood.mcheli.wingman.mission.AutonomousState;
import com.norwood.mcheli.wingman.wingman.WingmanEntry;
import com.norwood.mcheli.wingman.wingman.WingmanRegistry;
import com.norwood.mcheli.weapon.MCH_WeaponSet;
import com.norwood.mcheli.weapon.MCH_WeaponParam;
import net.vas.rdp.uav.RdpUavDebug;
import net.vas.rdp.uav.energy.UavEnergyHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The only RDP-facing boundary for WingMan control.
 */
public final class WingManFlightAdapter {
    private static final double KAMIKAZE_ATTACK_ANGLE_DEGREES = 35.0D;
    private static final double MIN_DIVE_RUN_METERS = 32.0D;
    private static final double MAX_DIVE_RUN_METERS = 160.0D;
    public boolean dispatch(MCH_EntityAircraft aircraft, double x, double y, double z,
                            double orbitRadius, int orbitTicks) {
        return dispatch(aircraft, x, y, z, orbitRadius, orbitTicks, false, null);
    }

    public boolean dispatch(MCH_EntityAircraft aircraft, double x, double y, double z,
                            double orbitRadius, int orbitTicks, boolean attack, UUID targetId) {
        if (aircraft == null || !aircraft.isUAV()) {
            RdpUavDebug.debug("WingMan", "dispatch rejected: aircraft missing or not UAV");
            return false;
        }
        UUID aircraftId = aircraft.getUniqueID();
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

        List<MissionNode> nodes = new ArrayList<MissionNode>();
        nodes.add(MissionNode.flyTo(x, y, z));
        if (orbitTicks > 0) {
            nodes.add(MissionNode.loiter(orbitTicks));
        }

        WingmanEntry entry = new WingmanEntry(aircraft,
                attack ? WingmanEntry.ATK_AUTO : WingmanEntry.ATK_NONE);
        entry.currentAutoTarget = targetId;
        entry.autoTargetX = x;
        entry.autoTargetY = y;
        entry.autoTargetZ = z;
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

    public boolean armAttack(MCH_EntityAircraft aircraft, double x, double y, double z,
                             double attackRadius, UUID targetId) {
        if (aircraft == null || !aircraft.isUAV()) return false;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        if (entry == null || entry.mission == null) return false;
        entry.attackMode = WingmanEntry.ATK_AUTO;
        entry.currentAutoTarget = targetId;
        entry.autoTargetX = x;
        entry.autoTargetY = y;
        entry.autoTargetZ = z;
        appendAttackNode(entry, attackRadius);
        return true;
    }

    public boolean armSquareStrike(MCH_EntityAircraft aircraft, SquareStrikePlan plan,
                                   UUID targetId) {
        if (aircraft == null || plan == null || !aircraft.isUAV()) return false;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        if (entry == null || entry.mission == null) return false;
        if (!hasAttackAmmunition(aircraft) && !hasSelfDestructWeaponWithAmmo(aircraft)) return false;
        entry.attackMode = WingmanEntry.ATK_AUTO;
        entry.currentAutoTarget = targetId;
        entry.mission.addAll(plan.createNodes());
        return true;
    }

    public boolean armKamikaze(MCH_EntityAircraft aircraft, double x, double y, double z,
                               double attackRadius, UUID targetId) {
        if (!supportsKamikaze(aircraft)) return false;
        int weaponId = selectSelfDestructWeapon(aircraft);
        if (weaponId < 0) return false;
        WingmanEntry entry = WingmanRegistry.get(aircraft.getUniqueID());
        if (entry == null || entry.mission == null) return false;
        entry.attackMode = WingmanEntry.ATK_AUTO;
        entry.currentAutoTarget = targetId;
        entry.autoTargetX = x;
        entry.autoTargetY = y;
        entry.autoTargetZ = z;
        appendDiveApproach(entry, aircraft, x, y, z);
        appendAttackNode(entry, attackRadius);
        // WingMan fires the aircraft's selected seat weapon. Select the
        // destructive loaded set before arming its autonomous attack node.
        aircraft.updateWeaponID(0, weaponId);
        entry.weaponSeat = 0;
        entry.weaponType = aircraft.getWeapon(weaponId).getName();
        aircraft.getEntityData().setBoolean(KamikazeConfig.ARMED_KEY, true);
        aircraft.getEntityData().setDouble(KamikazeConfig.MIN_SPEED_KEY,
                KamikazeConfig.minimumImpactSpeed);
        return true;
    }

    public boolean fireSelfDestructWeapon(MCH_EntityAircraft aircraft) {
        int weaponId = selectSelfDestructWeapon(aircraft);
        if (weaponId < 0 || aircraft == null) return false;
        MCH_WeaponSet weapon = aircraft.getWeapon(weaponId);
        if (weapon == null || weapon.getInfo() == null || !weapon.getInfo().destruct
                || !weapon.canFire()) return false;
        aircraft.updateWeaponID(0, weaponId);
        MCH_WeaponParam parameter = new MCH_WeaponParam();
        parameter.entity = aircraft;
        parameter.user = aircraft;
        parameter.setPosAndRot(aircraft.posX, aircraft.posY, aircraft.posZ,
                aircraft.getYaw(), aircraft.getPitch());
        parameter.rotRoll = aircraft.getRoll();
        parameter.isInfinity = false;
        boolean fired = weapon.use(parameter);
        if (fired && !aircraft.world.isRemote && !(aircraft instanceof MCH_EntityHeli)) {
            // McHeliCE's native SelfDestruct branch only handles helicopters.
            // Mirror that branch for planes and other aircraft so their
            // payload is not consumed without producing a warhead explosion.
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
            aircraft.destruct();
        }
        RdpUavDebug.debug("Kamikaze", "self-destruct fire aircraft={} weapon={} fired={} ammo={}",
                aircraft.getUniqueID(), weapon.getName(), fired, weapon.getAmmo());
        return fired;
    }

    public boolean supportsKamikaze(MCH_EntityAircraft aircraft) {
        if (aircraft == null || !aircraft.isUAV()) return false;
        return hasSelfDestructWeapon(aircraft);
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
        if (aircraft == null) return false;
        if (UavEnergyHandler.hasOperatingEnergy(aircraft)) return true;
        if (!aircraft.canUseFuel()) return true;
        return aircraft.getMaxFuel() > 0
                && aircraft.getFuel() > Math.max(1, aircraft.getMaxFuel() / 5);
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
    }

    public void release(MCH_EntityAircraft aircraft) {
        if (aircraft != null) {
            aircraft.getEntityData().removeTag(KamikazeConfig.ARMED_KEY);
            aircraft.getEntityData().removeTag(KamikazeConfig.MIN_SPEED_KEY);
            WingmanRegistry.remove(aircraft.getUniqueID());
        }
    }

    public boolean isDispatched(MCH_EntityAircraft aircraft) {
        return aircraft != null && WingmanRegistry.get(aircraft.getUniqueID()) != null;
    }

    public WingmanEntry getEntry(MCH_EntityAircraft aircraft) {
        return aircraft == null ? null : WingmanRegistry.get(aircraft.getUniqueID());
    }

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
            return false;
        }
        entry.attackMode = WingmanEntry.ATK_NONE;
        entry.rtbReason = reason == null ? "RDP_MISSION_COMPLETE" : reason;
        entry.autoState = AutonomousState.RTB;
        RdpUavDebug.debug("WingMan", "return requested aircraft={} reason={}",
                aircraft.getUniqueID(), entry.rtbReason);
        return true;
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

    public boolean isParked(MCH_EntityAircraft aircraft) {
        WingmanEntry entry = getEntry(aircraft);
        return entry != null && entry.autoState == AutonomousState.PARKED;
    }

    public UUID getAircraftId(MCH_EntityAircraft aircraft) {
        return aircraft == null ? null : aircraft.getUniqueID();
    }

    public void savePlan(String name, List<MissionNode> nodes) {
        if (name == null || name.trim().isEmpty() || nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("WingMan plan name and nodes are required");
        }
        MissionPlan.put(name, new ArrayList<MissionNode>(nodes));
    }
}
