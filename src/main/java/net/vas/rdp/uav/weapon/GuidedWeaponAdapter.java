package net.vas.rdp.uav.weapon;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.weapon.MCH_WeaponGuidanceSystem;
import com.norwood.mcheli.weapon.MCH_WeaponParam;
import com.norwood.mcheli.weapon.MCH_WeaponSet;
import net.vas.rdp.uav.flight.WingManFlightAdapter;
import net.minecraft.world.World;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.vas.rdp.uav.RdpUavDebug;
import net.vas.rdp.uav.target.TargetPolicy;

/**
 * The only guided-weapon boundary. It reads verified McHeli metadata and
 * writes only native WingMan target fields; it never steers projectiles.
 */
public final class GuidedWeaponAdapter {
    private final WingManFlightAdapter flight;

    public GuidedWeaponAdapter(WingManFlightAdapter flight) {
        if (flight == null) throw new IllegalArgumentException("Flight adapter is required");
        this.flight = flight;
    }

    public GuidedWeaponDecision validate(MCH_EntityAircraft aircraft, GuidedStrikeObjective objective,
                                         double aircraftSpeed) {
        MCH_WeaponSet weapon = findAnyGuidedWeapon(aircraft);
        return validate(aircraft, objective, aircraftSpeed,
                weapon == null ? "" : weapon.getName());
    }

    /**
     * Arms native WingMan attack routing after validation. Entity seekers keep
     * the authoritative UUID; coordinate/TV/laser weapons receive coordinates.
     */
    public GuidedWeaponDecision arm(MCH_EntityAircraft aircraft, GuidedStrikeObjective objective,
                                    double aircraftSpeed, double attackEnvelope) {
        return GuidedWeaponDecision.reject("GUIDED_SAFETY_CONTEXT_REQUIRED");
    }

    /**
     * Validates the world-facing release gates before handing a native attack
     * node to WingMan.
     */
    public GuidedWeaponDecision arm(World world, MCH_EntityAircraft aircraft,
                                    GuidedStrikeObjective objective, double aircraftSpeed,
                                    double attackEnvelope, boolean requireLineOfSight) {
        return arm(world, aircraft, objective, aircraftSpeed, attackEnvelope,
                requireLineOfSight, "");
    }

    /**
     * The priority is also the opt-in boundary for guided dispatch.  An empty
     * priority deliberately leaves the existing conventional WingMan path
     * untouched, even when an aircraft happens to carry a guided definition.
     */
    public GuidedWeaponDecision arm(World world, MCH_EntityAircraft aircraft,
                                    GuidedStrikeObjective objective, double aircraftSpeed,
                                    double attackEnvelope, boolean requireLineOfSight,
                                    String weaponPriority) {
        return arm(world, aircraft, objective, aircraftSpeed, attackEnvelope,
                requireLineOfSight, weaponPriority, false);
    }

    /**
     * Launches directly from a validated reusable-aircraft firing envelope.
     * This deliberately bypasses WingMan's aircraft attack route; McHeli
     * remains authoritative for projectile creation and guidance.
     */
    public GuidedWeaponDecision armDirect(World world, MCH_EntityAircraft aircraft,
                                          GuidedStrikeObjective objective, double aircraftSpeed,
                                          double attackEnvelope, boolean requireLineOfSight,
                                          String weaponPriority) {
        return arm(world, aircraft, objective, aircraftSpeed, attackEnvelope,
                requireLineOfSight, weaponPriority, true);
    }

    private GuidedWeaponDecision arm(World world, MCH_EntityAircraft aircraft,
                                     GuidedStrikeObjective objective, double aircraftSpeed,
                                     double attackEnvelope, boolean requireLineOfSight,
                                     String weaponPriority, boolean directLaunch) {
        if (world == null) {
            return GuidedWeaponDecision.reject("GUIDED_SAFETY_CONTEXT_REQUIRED");
        }
        String selectedPriority = weaponPriority;
        if (selectedPriority == null || selectedPriority.trim().isEmpty()) {
            MCH_WeaponSet available = findAnyGuidedWeapon(aircraft);
            selectedPriority = available == null ? "" : available.getName();
        }
        if (findWeapon(aircraft, selectedPriority) == null) {
            RdpUavDebug.event("Guidance", "validation_rejected",
                    "aircraft", aircraft == null ? null : aircraft.getUniqueID(),
                    "weapon", selectedPriority, "reason", "NO_GUIDED_WEAPON");
            return GuidedWeaponDecision.reject("NO_GUIDED_WEAPON");
        }
        GuidedWeaponDecision decision = validate(aircraft, objective, aircraftSpeed, selectedPriority);
        if (!decision.isAccepted()) {
            RdpUavDebug.debug("Guidance", "aircraft={} weapon={} objective={} decision={}",
                    aircraft.getUniqueID(), selectedPriority, objective.getType(),
                    decision.getReason());
            return decision;
        }
        GuidedWeaponCapability capability = capabilityFor(aircraft, decision.getWeapon());
        if (capability == null || capability.getBehavior() == GuidedNativeBehavior.UNSUPPORTED) {
            RdpUavDebug.event("Guidance", "validation_rejected",
                    "aircraft", aircraft.getUniqueID(), "weapon", decision.getWeapon(),
                    "objective", objective.getType(), "reason", "UNSUPPORTED_GUIDANCE");
            return GuidedWeaponDecision.reject("UNSUPPORTED_GUIDANCE");
        }
        RdpUavDebug.event("Guidance", "compatibility_evaluated",
                "aircraft", aircraft.getUniqueID(),
                "weapon", decision.getWeapon(),
                "resolvedDefinition", capability.getDefinition(),
                "resolvedType", capability.getType(),
                "objective", objective.getType(),
                "targetType", objective.getType(),
                "target", objective.getX() + "," + objective.getY() + "," + objective.getZ(),
                "behavior", capability.getBehavior(),
                "objectives", capability.getObjectives(),
                "supportsObjective", capability.supports(objective.getType()));
        if (!GuidedNativeContract.supportsAiGuidance(capability.getBehavior())) {
            RdpUavDebug.debug("Guidance", "aircraft={} weapon={} behavior={} decision=AI_UNSUPPORTED",
                    aircraft.getUniqueID(), decision.getWeapon(), capability.getBehavior());
            return GuidedWeaponDecision.reject("AI_TV_GUIDANCE_UNSUPPORTED");
        }
        GuidedSafetyDecision safety = GuidedSafetyPolicy.evaluate(world, aircraft, objective,
                requireLineOfSight, capability.getTerminalProfile());
        if (safety != GuidedSafetyDecision.ALLOW) {
            RdpUavDebug.event("Guidance", "safety_rejected",
                    "aircraft", aircraft.getUniqueID(), "weapon", decision.getWeapon(),
                    "objective", objective.getType(), "reason", safetyReason(safety));
            return GuidedWeaponDecision.reject(safetyReason(safety));
        }
        if (capability.getBehavior() == GuidedNativeBehavior.ENTITY_SEEKER) {
            GuidedStrikeObjective nativeObjective = objective;
            Entity target = findLoadedTarget(world, objective.getContactId());
            if (objective.getType() == GuidedObjectiveType.SQUARE) {
                target = findSquareTarget(world, objective);
                if (target == null) {
                    RdpUavDebug.event("Guidance", "area_target_unavailable",
                            "aircraft", aircraft.getUniqueID(), "weapon", decision.getWeapon(),
                            "objective", objective.getObjectiveId(), "x", objective.getX(),
                            "y", objective.getY(), "z", objective.getZ(), "radius", 8.0D);
                    return GuidedWeaponDecision.reject("GUIDED_TARGET_LOST");
                }
                nativeObjective = GuidedStrikeObjective.entity(objective.getObjectiveId(),
                        target.getUniqueID(), objective.getDimension(), target.posX,
                        target.posY, target.posZ, world.getTotalWorldTime());
                nativeObjective.authorize();
                nativeObjective.beginTracking();
                RdpUavDebug.event("Guidance", "area_target_acquired",
                        "aircraft", aircraft.getUniqueID(), "weapon", decision.getWeapon(),
                        "objective", objective.getObjectiveId(), "target", target.getUniqueID(),
                        "radius", 8.0D);
            }
            String nativeDecision = GuidedNativeContract.entityDecision(
                    GuidedNativeContract.supportsEntitySeeker(findWeapon(aircraft, decision.getWeapon())),
                    target != null);
            if (!"OK".equals(nativeDecision)) return GuidedWeaponDecision.reject(nativeDecision);
            if (!fireNativeEntitySeeker(aircraft, decision.getWeapon(), target)) {
                assert target != null;
                RdpUavDebug.event("Guidance", "native_launch_rejected",
                        "aircraft", aircraft.getUniqueID(), "weapon", decision.getWeapon(),
                        "objective", nativeObjective.getObjectiveId(), "target", target.getUniqueID(),
                        "mode", "ENTITY_SEEKER");
                return GuidedWeaponDecision.reject("NATIVE_SEEKER_LAUNCH_REJECTED");
            }
            MissileGuidanceController.authorize(aircraft.getUniqueID(), decision.getWeapon(), nativeObjective);
            assert target != null;
            RdpUavDebug.event("Guidance", "native_launch_accepted",
                    "aircraft", aircraft.getUniqueID(), "weapon", decision.getWeapon(),
                    "objective", nativeObjective.getObjectiveId(), "target", target.getUniqueID(),
                    "mode", "ENTITY_SEEKER");
            RdpUavDebug.debug("Guidance", "aircraft={} weapon={} owner=AI objective=ENTITY launch=native",
                    aircraft.getUniqueID(), decision.getWeapon());
            flight.markNativeAttackComplete(aircraft);
            return decision;
        }
        boolean armed;
        if (aircraft.isUAV() && !directLaunch) {
            armed = flight.armAttack(aircraft, objective.getX(), objective.getY(),
                    objective.getZ(), attackEnvelope, objective.getContactId(), decision.getWeapon());
        } else {
            armed = fireNativeCoordinateWeapon(aircraft, decision.getWeapon(), objective);
        }
        if (!armed) {
            RdpUavDebug.event("Guidance", "native_launch_rejected",
                    "aircraft", aircraft.getUniqueID(), "weapon", decision.getWeapon(),
                    "objective", objective.getObjectiveId(), "mode",
                    aircraft.isUAV() && !directLaunch ? "WINGMAN" : "NATIVE_DIRECT",
                    "reason", "NATIVE_WINGMAN_REJECTED");
            return GuidedWeaponDecision.reject("NATIVE_WINGMAN_REJECTED");
        }
        MissileGuidanceController.authorize(aircraft.getUniqueID(), decision.getWeapon(), objective);
        RdpUavDebug.event("Guidance", "native_launch_accepted",
                "aircraft", aircraft.getUniqueID(), "weapon", decision.getWeapon(),
                "objective", objective.getObjectiveId(), "objectiveType", objective.getType(),
                "mode", aircraft.isUAV() && !directLaunch ? "WINGMAN" : "NATIVE_DIRECT");
        RdpUavDebug.debug("Guidance", "aircraft={} weapon={} owner=AI objective={} launch={}",
                aircraft.getUniqueID(), decision.getWeapon(), objective.getType(),
                aircraft.isUAV() && !directLaunch ? "WINGMAN" : "NATIVE_DIRECT");
        if (directLaunch) flight.markNativeAttackComplete(aircraft);
        if (aircraft.isUAV()) return decision;
        return GuidedWeaponDecision.accept(decision.getWeapon());
    }

    private boolean fireNativeEntitySeeker(MCH_EntityAircraft aircraft, String weaponName,
                                           Entity target) {
        MCH_WeaponSet weapon = findWeapon(aircraft, weaponName);
        if (weapon == null || target == null) return false;
        MCH_WeaponGuidanceSystem guidance = GuidedNativeContract.guidanceSystem(weapon);
        if (guidance == null) return false;
        int weaponIndex = indexOf(aircraft, weapon);
        if (weaponIndex < 0) return false;
        aircraft.updateWeaponID(0, weaponIndex);
        MCH_WeaponParam parameter = new MCH_WeaponParam();
        parameter.entity = aircraft;
        parameter.user = aircraft;
        parameter.setPosAndRot(aircraft.posX, aircraft.posY, aircraft.posZ,
                aircraft.getYaw(), aircraft.getPitch());
        parameter.rotRoll = aircraft.getRoll();
        parameter.isInfinity = false;
        guidance.setTarget(target);
        boolean fired = weapon.use(parameter);
        if (!fired) guidance.setTarget(null);
        return fired;
    }

    private boolean fireNativeCoordinateWeapon(MCH_EntityAircraft aircraft, String weaponName,
                                               GuidedStrikeObjective objective) {
        MCH_WeaponSet weapon = findWeapon(aircraft, weaponName);
        if (weapon == null || weapon.getInfo() == null) return false;
        int weaponIndex = indexOf(aircraft, weapon);
        if (weaponIndex < 0) return false;
        aircraft.updateWeaponID(0, weaponIndex);
        MCH_WeaponParam parameter = new MCH_WeaponParam();
        parameter.entity = aircraft;
        parameter.user = aircraft;
        double dx = objective.getX() - aircraft.posX;
        double dy = objective.getY() - aircraft.posY;
        double dz = objective.getZ() - aircraft.posZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI - 90.0D);
        float pitch = (float) (-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))
                * 180.0D / Math.PI);
        parameter.setPosAndRot(aircraft.posX, aircraft.posY, aircraft.posZ, yaw, pitch);
        parameter.rotRoll = aircraft.getRoll();
        parameter.isInfinity = false;
        return weapon.use(parameter);
    }

    private Entity findLoadedTarget(World world, java.util.UUID targetId) {
        if (world == null || targetId == null) return null;
        for (Entity entity : world.loadedEntityList) {
            if (entity != null && !entity.isDead
                    && entity.dimension == world.provider.getDimension()
                    && targetId.equals(entity.getUniqueID())) return entity;
        }
        return null;
    }

    private Entity findSquareTarget(World world, GuidedStrikeObjective objective) {
        AxisAlignedBB area = new AxisAlignedBB(objective.getX() - 8.0, objective.getY() - 8.0,
                objective.getZ() - 8.0, objective.getX() + 8.0, objective.getY() + 8.0,
                objective.getZ() + 8.0);
        Entity selected = null;
        double selectedDistance = Double.MAX_VALUE;
        for (Entity candidate : world.getEntitiesWithinAABB(Entity.class, area)) {
            if (candidate == null || candidate.isDead
                    || candidate.dimension != world.provider.getDimension()
                    || !TargetPolicy.isSafeContact(world, candidate)) continue;
            double distance = candidate.getDistanceSq(objective.getX(), objective.getY(), objective.getZ());
            if (distance < selectedDistance) {
                selected = candidate;
                selectedDistance = distance;
            }
        }
        return selected;
    }

    private int indexOf(MCH_EntityAircraft aircraft, MCH_WeaponSet selected) {
        MCH_WeaponSet[] weapons = aircraft == null ? null : aircraft.getWeapons();
        if (weapons == null) return -1;
        for (int i = 0; i < weapons.length; i++) {
            if (weapons[i] == selected) return i;
        }
        return -1;
    }

    private GuidedWeaponCapability capabilityFor(MCH_EntityAircraft aircraft, String weaponName) {
        if (aircraft == null || aircraft.getWeapons() == null) return null;
        for (MCH_WeaponSet weapon : aircraft.getWeapons()) {
            if (weapon != null && weaponName != null && weaponName.equals(weapon.getName())
                    && weapon.getInfo() != null) {
                return capabilityFor(weapon);
            }
        }
        return null;
    }

    private String safetyReason(GuidedSafetyDecision decision) {
        switch (decision) {
        case REJECT_DIMENSION: return "GUIDED_REJECT_DIMENSION";
        case HOLD_CHUNK_UNLOADED: return "GUIDED_HOLD_CHUNK_UNLOADED";
        case HOLD_LINE_OF_SIGHT: return "GUIDED_HOLD_LINE_OF_SIGHT";
        case HOLD_TERRAIN: return "GUIDED_HOLD_TERRAIN";
        default: return "GUIDED_SAFETY_REJECTED";
        }
    }

    private MCH_WeaponSet findWeapon(MCH_EntityAircraft aircraft, String priority) {
        if (aircraft == null) return null;
        MCH_WeaponSet[] weapons = aircraft.getWeapons();
        if (weapons == null) return null;
        if (priority == null || priority.trim().isEmpty()) return null;
        for (String requested : priority.split(",")) {
            String wanted = requested.trim();
            for (MCH_WeaponSet weapon : weapons) {
                if (weapon != null && wanted.equalsIgnoreCase(weapon.getName())
                        && capabilityFor(weapon) != null) {
                    return weapon;
                }
            }
        }
        return null;
    }

    private GuidedWeaponDecision validate(MCH_EntityAircraft aircraft,
                                          GuidedStrikeObjective objective,
                                          double aircraftSpeed,
                                          String priority) {
        if (aircraft == null || objective == null) {
            return GuidedWeaponDecision.reject("AIRCRAFT_OR_OBJECTIVE_INVALID");
        }
        if (objective.getType() == GuidedObjectiveType.ENTITY
                && objective.getState() == GuidedObjectiveState.LOST) {
            return GuidedWeaponDecision.reject("GUIDED_TARGET_LOST");
        }
        if (objective.getState() != GuidedObjectiveState.AUTHORIZED
                && objective.getState() != GuidedObjectiveState.TRACKING) {
            return GuidedWeaponDecision.reject("UNSUPPORTED_OBJECTIVE_STATE");
        }
        MCH_WeaponSet weapon = findWeapon(aircraft, priority);
        if (weapon == null) return GuidedWeaponDecision.reject("NO_GUIDED_WEAPON");
        GuidedWeaponCapability capability = capabilityFor(weapon);
        if (capability == null || capability.getBehavior() == GuidedNativeBehavior.UNSUPPORTED) {
            return GuidedWeaponDecision.reject("UNSUPPORTED_GUIDANCE");
        }
        if (!capability.supports(objective.getType())) {
            RdpUavDebug.event("Guidance", "compatibility_rejected",
                    "weapon", weapon.getName(),
                    "resolvedDefinition", capability.getDefinition(),
                    "resolvedType", capability.getType(),
                    "objective", objective.getType(),
                    "behavior", capability.getBehavior(),
                    "objectives", capability.getObjectives(),
                    "reason", "UNSUPPORTED_OBJECTIVE_COMBINATION");
            return GuidedWeaponDecision.reject("UNSUPPORTED_OBJECTIVE_COMBINATION");
        }
        double limit = capability.getTerminalProfile().getMaximumAircraftSpeed();
        if (limit > 0.0D && aircraftSpeed > limit) {
            return GuidedWeaponDecision.reject("TERMINAL_SPEED_LIMIT");
        }
        if (weapon.getAmmo() <= 0 && weapon.getAmmoReserve() <= 0) {
            return GuidedWeaponDecision.reject("NO_AMMUNITION");
        }
        return GuidedWeaponDecision.accept(weapon.getName());
    }

    private MCH_WeaponSet findAnyGuidedWeapon(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return null;
        MCH_WeaponSet[] weapons = aircraft.getWeapons();
        if (weapons == null) return null;
        MCH_WeaponSet empty = null;
        for (MCH_WeaponSet weapon : weapons) {
            GuidedWeaponCapability capability = capabilityFor(weapon);
            if (weapon != null && weapon.getInfo() != null && capability != null
                    && capability.getBehavior() != GuidedNativeBehavior.UNSUPPORTED) {
                if (weapon.getAmmo() > 0 || weapon.getAmmoReserve() > 0) return weapon;
                empty = weapon;
            }
        }
        return empty;
    }

    private GuidedWeaponCapability capabilityFor(MCH_WeaponSet weapon) {
        if (weapon == null || weapon.getInfo() == null) return null;
        GuidedWeaponCapability capability = GuidedWeaponRegistry.find(weapon.getName(),
                weapon.getInfo().type);
        return capability == null
                ? GuidedWeaponRegistry.find(weapon.getInfo().name, weapon.getInfo().type)
                : capability;
    }
}
