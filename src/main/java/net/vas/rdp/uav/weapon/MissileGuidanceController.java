package net.vas.rdp.uav.weapon;

import com.norwood.mcheli.weapon.MCH_EntityASMissile;
import com.norwood.mcheli.weapon.MCH_EntityBaseBullet;
import com.norwood.mcheli.weapon.MCH_EntityTvMissile;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import net.vas.rdp.uav.RdpUavDebug;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side objective handoff for missiles whose native implementations
 * expose coordinate fields. The controller never teleports a projectile,
 * writes projectile motion, or changes gravity, collision, fuse, or impact.
 */
public final class MissileGuidanceController {
    private static final Map<UUID, Assignment> ASSIGNMENTS = new HashMap<>();
    private static final Map<UUID, ProjectileGuidanceMission> PROJECTILES =
            new HashMap<>();
    private static final Map<UUID, AvoidancePlan> AVOIDANCE_PLANS =
            new HashMap<>();
    private static final Map<UUID, Long> LAST_COORDINATE_LOG =
            new HashMap<>();
    private static final Map<UUID, CoordinatePoint> LAST_COORDINATES =
            new HashMap<>();
    private static final Map<UUID, CoordinatePoint> LAST_APPLIED_NATIVE_TARGETS =
            new HashMap<>();
    private static final Map<UUID, Long> LAST_OVERRIDE_LOG =
            new HashMap<>();
    private static final Map<UUID, Long> LAST_CORRECTION_TICK =
            new HashMap<>();
    private static final Map<UUID, Long> LAST_CORRIDOR_LOG =
            new HashMap<>();
    private static final Map<UUID, String> UNKNOWN_OUTCOMES =
            new HashMap<>();
    private static final int MAX_UNKNOWN_OUTCOMES = 512;
    private static final int MAX_ROUTE_SAMPLES = 12;
    private static final int MAX_AVOIDANCE_CANDIDATES = 8;
    private static final double AVOIDANCE_OFFSET = 6.0D;
    private static final long AVOIDANCE_PLAN_TICKS = 10L;

    private MissileGuidanceController() { }

    public static synchronized void authorize(UUID aircraftId, String weapon,
                                              GuidedStrikeObjective objective) {
        if (
                aircraftId == null
                || weapon == null
                || objective == null
        ) return;

        ASSIGNMENTS.put(aircraftId,
                new Assignment(weapon, objective));
        RdpUavDebug.event("Guidance", "assignment_authorized",
                "aircraft", aircraftId, "weapon", weapon,
                "objective", objective.getObjectiveId(), "objectiveType", objective.getType(),
                "target", objective.getContactId());
    }

    public static synchronized void authorize(UUID aircraftId, String weapon,
                                              GuidedStrikeObjective objective,
                                              GuidedTargetFilter filter) {
        if (aircraftId == null || weapon == null || objective == null) return;
        ASSIGNMENTS.put(aircraftId, new Assignment(weapon, objective, filter));
    }

    public static synchronized void abort(UUID aircraftId) {
        if (aircraftId != null) {

            Assignment assignment = ASSIGNMENTS.get(aircraftId);

            if (assignment != null) assignment.mode = GuidanceControlMode.ABORTED;

            ASSIGNMENTS.remove(aircraftId);
            RdpUavDebug.event("Guidance", "assignment_aborted", "aircraft", aircraftId);
            RdpUavDebug.debug("Guidance", "aircraft={} owner=PLAYER/native action=TAKEOVER", aircraftId);
        }
    }
    public static synchronized void clear(UUID aircraftId) {
        if (aircraftId != null) {
            ASSIGNMENTS.remove(aircraftId);
            java.util.Iterator<Map.Entry<UUID, ProjectileGuidanceMission>> iterator =
                    PROJECTILES.entrySet().iterator();
            while (iterator.hasNext()) {
                ProjectileGuidanceMission mission = iterator.next().getValue();
                if (aircraftId.equals(mission.getAircraftId())) {
                    mission.abort();
                    iterator.remove();
                }
            }
        }
    }

    public static synchronized boolean reassign(UUID aircraftId, UUID projectileId,
                                                 GuidedStrikeObjective objective) {
        if (aircraftId == null || projectileId == null || objective == null) return false;
        Assignment assignment = ASSIGNMENTS.get(aircraftId);
        ProjectileGuidanceMission projectile = PROJECTILES.get(projectileId);
        if (assignment == null || projectile == null
                || !aircraftId.equals(projectile.getAircraftId())) return false;
        ASSIGNMENTS.put(aircraftId, new Assignment(assignment.weapon, objective, assignment.filter));
        return true;
    }

    public static synchronized boolean reassign(UUID aircraftId,
                                                 GuidedStrikeObjective objective) {
        if (aircraftId == null || objective == null) return false;
        Assignment assignment = ASSIGNMENTS.get(aircraftId);
        if (assignment == null) return false;
        for (Map.Entry<UUID, ProjectileGuidanceMission> entry : PROJECTILES.entrySet()) {
            ProjectileGuidanceMission projectile = entry.getValue();
            if (aircraftId.equals(projectile.getAircraftId())) {
                return reassign(aircraftId, entry.getKey(), objective);
            }
        }
        return false;
    }

    public static synchronized boolean adopt(World world, UUID aircraftId, UUID projectileId,
                                              GuidedStrikeObjective objective) {
        if (world == null || aircraftId == null || projectileId == null || objective == null) {
            return false;
        }
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof MCH_EntityBaseBullet)
                    || !projectileId.equals(entity.getUniqueID())) continue;
            MCH_EntityBaseBullet missile = (MCH_EntityBaseBullet) entity;
            Entity shooter = missile.shootingAircraft == null
                    ? missile.shootingEntity : missile.shootingAircraft;
            if (shooter == null || !aircraftId.equals(shooter.getUniqueID())
                    || matchesNative(missile)) return false;
            String weapon = missile.getInfo() == null ? "" : missile.getInfo().name;
            if (weapon.isEmpty() || ASSIGNMENTS.containsKey(aircraftId)) return false;
            Assignment assignment = new Assignment(weapon, objective);
            ASSIGNMENTS.put(aircraftId, assignment);
            projectileMission(missile, shooter, assignment);
            return true;
        }
        return false;
    }

    public static synchronized List<WeaponTelemetry> telemetry(World world, UUID aircraftId) {
        List<WeaponTelemetry> result = new ArrayList<>();
        if (world == null || aircraftId == null) return result;
        Assignment assignment = ASSIGNMENTS.get(aircraftId);
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof MCH_EntityBaseBullet)) continue;
            MCH_EntityBaseBullet missile = (MCH_EntityBaseBullet) entity;
            if (!aircraftId.equals(shooterId(missile)) || matchesNative(missile)) continue;
            ProjectileGuidanceMission projectile = PROJECTILES.get(missile.getUniqueID());
            String state = projectile == null ? "NATIVE" : projectile.getState().name();
            String weapon = missile.getInfo() == null ? "" : missile.getInfo().name;
            String objective = assignment == null ? "" :
                    assignment.objective.getObjectiveId().toString();
            result.add(new WeaponTelemetry(weapon, state, missile.getUniqueID(),
                    missile.posX, missile.posY, missile.posZ, objective));
            if (result.size() >= 32) break;
        }
        return result;
    }

    /**
     * Bridges the native projectile observation back to the owning mission.
     * A launch is not considered tracked until the spawned projectile has
     * actually been seen in the server world.
     */
    public static synchronized boolean isProjectileTracked(UUID objectiveId) {
        if (objectiveId == null) return false;
        for (ProjectileGuidanceMission projectile : PROJECTILES.values()) {
            if (objectiveId.equals(projectile.getObjective().getObjectiveId())
                    && projectile.getState() != ProjectileGuidanceState.ABORTED
                    && projectile.getState() != ProjectileGuidanceState.TIMED_OUT) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true when a previously observed projectile disappeared without
     * an authoritative impact/effect event. Disappearance is not success.
     */
    public static synchronized boolean isProjectileOutcomeUnknown(UUID objectiveId) {
        return objectiveId != null && UNKNOWN_OUTCOMES.containsKey(objectiveId);
    }

    private static boolean matchesNative(MCH_EntityBaseBullet missile) {
        return !(missile instanceof MCH_EntityASMissile)
                && !(missile instanceof MCH_EntityTvMissile)
                && (missile.getInfo() == null
                || (!"aamissile".equalsIgnoreCase(missile.getInfo().type)
                && !"atmissile".equalsIgnoreCase(missile.getInfo().type)));
    }

    public static void tick(World world) {
        if (world == null || world.isRemote) return;
        Set<UUID> liveProjectiles = new HashSet<>();
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof MCH_EntityBaseBullet)) continue;
            MCH_EntityBaseBullet missile = (MCH_EntityBaseBullet) entity;
            liveProjectiles.add(missile.getUniqueID());
            Entity shooter = missile.shootingAircraft;
            if (shooter == null) shooter = missile.shootingEntity;
            if (shooter == null) continue;
            Assignment assignment;
            synchronized (MissileGuidanceController.class) {
                assignment = ASSIGNMENTS.get(shooter.getUniqueID());
            }
            if (assignment == null
                    || assignment.mode
                    != GuidanceControlMode.AI_GUIDANCE)
                continue;

            if (!matches(assignment.weapon, missile)) continue;

            ProjectileGuidanceMission projectile =
                    projectileMission(missile, shooter, assignment);

            projectile.nativeGuidanceActive();

            if (assignment.filter != null && assignment.objective.getType() == GuidedObjectiveType.ENTITY) {
                Entity selected = assignment.filter.select(world, missile);
                if (selected != null && !selected.getUniqueID().equals(assignment.objective.getContactId())) {
                    RdpUavDebug.debug("Guidance", "aircraft={} objective-switch old={} new={}",
                            shooter.getUniqueID(), assignment.objective.getContactId(),
                            selected.getUniqueID());
                    assignment.objective.switchContact(selected.getUniqueID(), selected.posX,
                            selected.posY, selected.posZ, world.getTotalWorldTime());
                    RdpUavDebug.event("Guidance", "target_switched",
                            "aircraft", shooter.getUniqueID(), "projectile", missile.getUniqueID(),
                            "objective", assignment.objective.getObjectiveId(),
                            "target", selected.getUniqueID());
                }
            }
            apply(world, missile, assignment);
        }
        synchronized (MissileGuidanceController.class) {
            for (ProjectileGuidanceMission projectile : PROJECTILES.values()) {
                UUID projectileId = projectile.getProjectileId();
                if (liveProjectiles.contains(projectileId)
                        || projectile.getState() == ProjectileGuidanceState.IMPACTED
                        || projectile.getState() == ProjectileGuidanceState.ABORTED
                        || projectile.getState() == ProjectileGuidanceState.TIMED_OUT) {
                    continue;
                }
                projectile.timeout();
                UNKNOWN_OUTCOMES.put(projectile.getObjective().getObjectiveId(),
                        "PROJECTILE_DISAPPEARED_WITHOUT_IMPACT");
                RdpUavDebug.event("Guidance", "projectile_outcome_unknown",
                        "aircraft", projectile.getAircraftId(),
                        "projectile", projectileId,
                        "objective", projectile.getObjective().getObjectiveId(),
                        "weapon", projectile.getWeapon(),
                        "reason", "PROJECTILE_DISAPPEARED_WITHOUT_IMPACT");
            }
            PROJECTILES.keySet().retainAll(liveProjectiles);
            AVOIDANCE_PLANS.keySet().retainAll(liveProjectiles);
            LAST_COORDINATE_LOG.keySet().retainAll(liveProjectiles);
            LAST_COORDINATES.keySet().retainAll(liveProjectiles);
            LAST_APPLIED_NATIVE_TARGETS.keySet().retainAll(liveProjectiles);
            LAST_OVERRIDE_LOG.keySet().retainAll(liveProjectiles);
            LAST_CORRECTION_TICK.keySet().retainAll(liveProjectiles);
            LAST_CORRIDOR_LOG.keySet().retainAll(liveProjectiles);
            while (UNKNOWN_OUTCOMES.size() > MAX_UNKNOWN_OUTCOMES) {
                UNKNOWN_OUTCOMES.remove(UNKNOWN_OUTCOMES.keySet().iterator().next());
            }
        }
    }

    private static boolean matches(String weapon, MCH_EntityBaseBullet missile) {
        if (missile.getInfo() == null || weapon == null) return false;
        return weapon.equalsIgnoreCase(missile.getInfo().name)
                || (missile instanceof MCH_EntityTvMissile && "tvmissile".equalsIgnoreCase(weapon))
                || (missile instanceof MCH_EntityASMissile && "asmissile".equalsIgnoreCase(weapon));
    }

    private static void apply(World world, MCH_EntityBaseBullet missile,
                              Assignment assignment) {
        GuidedStrikeObjective objective = assignment.objective;
        ProjectileGuidanceMission projectile = projectileMission(missile,
                missile.shootingAircraft == null ? missile.shootingEntity
                        : missile.shootingAircraft, assignment);
        double x = objective.getX();
        double y = objective.getY();
        double z = objective.getZ();
        if (objective.getType() == GuidedObjectiveType.ENTITY) {
            Entity target = find(world, objective.getContactId());
            if (target != null) {
                x = target.posX; y = target.posY; z = target.posZ;
                objective.updateContact(x, y, z, world.getTotalWorldTime());
            } else {
                boolean firstLoss = projectile.getState() != ProjectileGuidanceState.TARGET_LOST;
                objective.loseContact();
                projectile.targetLost();
                if (firstLoss) {
                    RdpUavDebug.event("Guidance", "target_lost",
                            "aircraft", shooterId(missile),
                            "projectile", missile.getUniqueID(), "objective", objective.getObjectiveId(),
                            "target", objective.getContactId(), "weapon", assignment.weapon);
                }
                RdpUavDebug.debug("Guidance", "aircraft={} projectile target lost weapon={}",
                        shooterId(missile), assignment.weapon);
                return;
            }
        }
        AimPoint aim = chooseAimPoint(world, missile, x, y, z);
        x = aim.x;
        y = aim.y;
        z = aim.z;
        CoordinatePoint nativeAim = normalizeNativeTarget(world, missile, x, y, z);
        x = nativeAim.x;
        y = nativeAim.y;
        z = nativeAim.z;
        CoordinatePoint corrected = applyCoordinatePolicy(world, missile, x, y, z);
        x = corrected.x;
        y = corrected.y;
        z = corrected.z;
        logNativeGuidanceObservation(world, missile, objective, x, y, z);
        if (aim.avoiding) {
            boolean firstAvoidance =
                    projectile.getState() != ProjectileGuidanceState.AVOIDANCE_ACTIVE;
            projectile.avoidanceActive();
            if (firstAvoidance) {
                RdpUavDebug.event("Guidance", "avoidance_waypoint",
                        "aircraft", shooterId(missile),
                        "projectile", missile.getUniqueID(), "objective", objective.getObjectiveId(),
                        "x", x, "y", y, "z", z);
            }

            RdpUavDebug.debug("Guidance",
                    "aircraft={} projectile={} native avoidance candidate=({}, {}, {})",
                    shooterId(missile),
                    missile.getUniqueID(), x, y, z);
        }
        CoordinatePoint previousNativeTarget = LAST_APPLIED_NATIVE_TARGETS.get(missile.getUniqueID());
        CoordinatePoint currentNativeTarget = nativeTarget(missile);
        if (previousNativeTarget != null && currentNativeTarget != null
                && !samePoint(previousNativeTarget, currentNativeTarget)) {
            long now = world.getTotalWorldTime();
            Long previousLog = LAST_OVERRIDE_LOG.get(missile.getUniqueID());
            if (previousLog == null || now - previousLog >= AVOIDANCE_PLAN_TICKS) {
                LAST_OVERRIDE_LOG.put(missile.getUniqueID(), now);
                RdpUavDebug.event("Guidance", "native_target_overwritten",
                        "aircraft", shooterId(missile),
                        "projectile", missile.getUniqueID(),
                        "weapon", assignment.weapon,
                        "previousX", previousNativeTarget.x,
                        "previousY", previousNativeTarget.y,
                        "previousZ", previousNativeTarget.z,
                        "currentX", currentNativeTarget.x,
                        "currentY", currentNativeTarget.y,
                        "currentZ", currentNativeTarget.z,
                        "owner", "MCHELI_NATIVE_OR_OTHER");
            }
        }
        if (missile instanceof MCH_EntityTvMissile) {
            MCH_EntityTvMissile tv = (MCH_EntityTvMissile) missile;
            // These are McHeli's native TV target fields. Do not replace
            // projectile motion; McHeli performs steering and collision.
            tv.targetPosX = x;
            tv.targetPosY = y;
            tv.targetPosZ = z;
        } else if (missile instanceof MCH_EntityASMissile) {
            MCH_EntityASMissile as = (MCH_EntityASMissile) missile;
            // asmissile retains its native flight, gravity, seeker, and
            // impact behavior; only the native target coordinates are updated.
            as.targetPosX = x;
            as.targetPosY = y;
            as.targetPosZ = z;
        }
        if (missile instanceof MCH_EntityTvMissile
                || missile instanceof MCH_EntityASMissile) {
            LAST_APPLIED_NATIVE_TARGETS.put(missile.getUniqueID(),
                    new CoordinatePoint(x, y, z));
        }
        long now = world.getTotalWorldTime();
        Long previous = LAST_COORDINATE_LOG.get(missile.getUniqueID());
        if (previous == null || now - previous >= AVOIDANCE_PLAN_TICKS) {
            LAST_COORDINATE_LOG.put(missile.getUniqueID(), now);
            RdpUavDebug.event("Guidance", "native_target_update",
                    "aircraft", shooterId(missile),
                    "projectile", missile.getUniqueID(), "objective", objective.getObjectiveId(),
                    "weapon", assignment.weapon, "mode", behaviorFor(missile),
                    "target", objective.getContactId(),
                    "x", x, "y", y, "z", z);
        }
    }

    private static CoordinatePoint applyCoordinatePolicy(World world,
                                                          MCH_EntityBaseBullet missile,
                                                          double x, double y, double z) {
        if (!(missile instanceof MCH_EntityASMissile)
                || missile.getInfo() == null) {
            return new CoordinatePoint(x, y, z);
        }
        GuidedWeaponCapability capability = GuidedWeaponRegistry.find(
                missile.getInfo().name, missile.getInfo().type);
        if (capability == null) return new CoordinatePoint(x, y, z);
        GuidedTerminalProfile profile = capability.getTerminalProfile();
        double maximum = profile.getMaximumCoordinateCorrection();
        if (maximum <= 0.0D) return new CoordinatePoint(x, y, z);
        long now = world.getTotalWorldTime();
        Long previousTick = LAST_CORRECTION_TICK.get(missile.getUniqueID());
        CoordinatePoint previous = LAST_COORDINATES.get(missile.getUniqueID());
        if (previous != null && previousTick != null
                && now - previousTick < profile.getCoordinateCorrectionInterval()) {
            return previous;
        }
        if (previous == null) {
            CoordinatePoint initial = new CoordinatePoint(x, y, z);
            LAST_COORDINATES.put(missile.getUniqueID(), initial);
            LAST_CORRECTION_TICK.put(missile.getUniqueID(), now);
            RdpUavDebug.event("Guidance", "coordinate_policy_started",
                    "projectile", missile.getUniqueID(), "weapon", missile.getInfo().name,
                    "maximum", maximum, "interval", profile.getCoordinateCorrectionInterval());
            return initial;
        }
        double dx = x - previous.x;
        double dy = y - previous.y;
        double dz = z - previous.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > maximum) {
            double scale = maximum / distance;
            x = previous.x + dx * scale;
            y = previous.y + dy * scale;
            z = previous.z + dz * scale;
        }
        CoordinatePoint result = new CoordinatePoint(x, y, z);
        LAST_COORDINATES.put(missile.getUniqueID(), result);
        LAST_CORRECTION_TICK.put(missile.getUniqueID(), now);
        if (distance > maximum) {
            RdpUavDebug.event("Guidance", "coordinate_correction_clamped",
                    "projectile", missile.getUniqueID(), "weapon", missile.getInfo().name,
                    "maximum", maximum, "x", x, "y", y, "z", z);
        }
        return result;
    }

    /**
     * McHeli's AS/TV native guidance branches only steer when their target
     * BlockPos contains a movement-blocking material. Mission coordinates are
     * points and may legitimately be in air above the strike block, so resolve
     * a loaded air point to the nearest solid block below it without touching
     * projectile motion.
     */
    private static CoordinatePoint normalizeNativeTarget(World world,
                                                          MCH_EntityBaseBullet missile,
                                                          double x, double y, double z) {
        if (!(missile instanceof MCH_EntityASMissile)
                && !(missile instanceof MCH_EntityTvMissile)) {
            return new CoordinatePoint(x, y, z);
        }
        BlockPos requested = new BlockPos(x, y, z);
        if (!world.isBlockLoaded(requested)
                || world.getBlockState(requested).getMaterial().blocksMovement()) {
            return new CoordinatePoint(x, y, z);
        }
        int minY = Math.max(0, requested.getY() - 32);
        for (int candidateY = requested.getY() - 1; candidateY >= minY; candidateY--) {
            BlockPos candidate = new BlockPos(requested.getX(), candidateY, requested.getZ());
            if (world.getBlockState(candidate).getMaterial().blocksMovement()) {
                RdpUavDebug.event("Guidance", "native_target_normalized",
                        "projectile", missile.getUniqueID(),
                        "weapon", missile.getInfo() == null ? "" : missile.getInfo().name,
                        "requestedX", x, "requestedY", y, "requestedZ", z,
                        "nativeX", candidate.getX() + 0.5D,
                        "nativeY", candidate.getY(),
                        "nativeZ", candidate.getZ() + 0.5D,
                        "reason", "MCHELI_NATIVE_GUIDANCE_REQUIRES_SOLID_TARGET");
                return new CoordinatePoint(candidate.getX() + 0.5D,
                        candidate.getY(), candidate.getZ() + 0.5D);
            }
        }
        return new CoordinatePoint(x, y, z);
    }

    private static CoordinatePoint nativeTarget(MCH_EntityBaseBullet missile) {
        if (missile instanceof MCH_EntityTvMissile) {
            MCH_EntityTvMissile tv = (MCH_EntityTvMissile) missile;
            return new CoordinatePoint(tv.targetPosX, tv.targetPosY, tv.targetPosZ);
        }
        if (missile instanceof MCH_EntityASMissile) {
            MCH_EntityASMissile as = (MCH_EntityASMissile) missile;
            return new CoordinatePoint(as.targetPosX, as.targetPosY, as.targetPosZ);
        }
        return null;
    }

    private static boolean samePoint(CoordinatePoint first, CoordinatePoint second) {
        return Math.abs(first.x - second.x) < 0.001D
                && Math.abs(first.y - second.y) < 0.001D
                && Math.abs(first.z - second.z) < 0.001D;
    }

    private static synchronized ProjectileGuidanceMission projectileMission(
            MCH_EntityBaseBullet missile, Entity shooter, Assignment assignment) {
        ProjectileGuidanceMission existing = PROJECTILES.get(missile.getUniqueID());
        if (existing != null) return existing;
        ProjectileGuidanceMission created = new ProjectileGuidanceMission(
                missile.getUniqueID(), shooter.getUniqueID(), assignment.weapon,
                behaviorFor(missile), assignment.objective);
        PROJECTILES.put(missile.getUniqueID(), created);
        return created;
    }

    private static GuidedNativeBehavior behaviorFor(MCH_EntityBaseBullet missile) {
        if (missile instanceof MCH_EntityTvMissile) {
            return GuidedNativeBehavior.COORDINATE_TV_LASER;
        }
        if (missile instanceof MCH_EntityASMissile) {
            return GuidedNativeBehavior.COORDINATE_BALLISTIC;
        }
        return GuidedNativeBehavior.ENTITY_SEEKER;
    }

    private static AimPoint chooseAimPoint(World world, MCH_EntityBaseBullet missile,
                                           double x, double y, double z) {
        if (!(missile instanceof MCH_EntityTvMissile)
                && !(missile instanceof MCH_EntityASMissile)) {
            // McHeli's native seeker owns these families. Do not reject or
            // redirect them based on an addon corridor approximation.
            return new AimPoint(x, y, z, false);
        }
        AvoidancePlan existing = AVOIDANCE_PLANS.get(missile.getUniqueID());
        long now = world.getTotalWorldTime();
        if (existing != null && now <= existing.expiresAt
                && corridorCheck(world, missile.posX, missile.posY, missile.posZ,
                existing.x, existing.y, existing.z).clear) {
            return new AimPoint(existing.x, existing.y, existing.z, true);
        }
        AVOIDANCE_PLANS.remove(missile.getUniqueID());
        CorridorCheck direct = corridorCheck(world, missile.posX, missile.posY,
                missile.posZ, x, y, z);
        if (direct.clear) {
            return new AimPoint(x, y, z, false);
        }
        logCorridorFailure(world, missile, "DIRECT", x, y, z, direct);
        double[][] offsets = getOffsets(missile, x, z);
        AimPoint best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < MAX_AVOIDANCE_CANDIDATES; i++) {
            double candidateX = x + offsets[i][0] * AVOIDANCE_OFFSET;
            double candidateY = Math.max(1.0D, y + offsets[i][1] * AVOIDANCE_OFFSET);
            double candidateZ = z + offsets[i][2] * AVOIDANCE_OFFSET;
            CorridorCheck outbound = corridorCheck(world, missile.posX, missile.posY,
                    missile.posZ, candidateX, candidateY, candidateZ);
            CorridorCheck inbound = corridorCheck(world, candidateX, candidateY,
                    candidateZ, x, y, z);
            if (!outbound.clear || !inbound.clear) {
                CorridorCheck failed = outbound.clear ? inbound : outbound;
                logCorridorFailure(world, missile, "DETOUR_" + i,
                        failed.x, failed.y, failed.z, failed);
                continue;
            }
            double deviation = Math.abs(candidateX - x) + Math.abs(candidateY - y)
                    + Math.abs(candidateZ - z);
            double score = deviation + Math.abs(candidateY - missile.posY) * 0.25D;
            if (score < bestScore) {
                bestScore = score;
                best = new AimPoint(candidateX, candidateY, candidateZ, true);
            }
        }
        if (best != null) {
            AVOIDANCE_PLANS.put(missile.getUniqueID(),
                    new AvoidancePlan(best.x, best.y, best.z, now + AVOIDANCE_PLAN_TICKS));
            return best;
        }
        /*
         * Corridor analysis is an addon safety/avoidance aid, not a native
         * guidance prerequisite. If every detour is blocked, still submit the
         * requested point so McHeli can steer and resolve collision itself.
         * Returning null here silently disabled TV/AS steering altogether.
         */
        RdpUavDebug.event("Guidance", "native_guidance_fallback",
                "projectile", missile.getUniqueID(),
                "weapon", missile.getInfo() == null ? "" : missile.getInfo().name,
                "reason", "NO_SAFE_DETOUR",
                "directFailure", direct.reason,
                "directSample", direct.sample + "/" + MAX_ROUTE_SAMPLES,
                "directBlock", direct.block);
        return new AimPoint(x, y, z, false);
    }

    @NotNull
    private static double[][] getOffsets(MCH_EntityBaseBullet missile, double x, double z) {
        double dx = x - missile.posX;
        double dz = z - missile.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.001D) {
            dx = 1.0D;
            dz = 0.0D;
            horizontal = 1.0D;
        }
        double leftX = -dz / horizontal;
        double leftZ = dx / horizontal;
        return new double[][]{
                {leftX, 0.0D, leftZ},
                {-leftX, 0.0D, -leftZ},
                {0.0D, 1.0D, 0.0D},
                {0.0D, -1.0D, 0.0D},
                {leftX, 1.0D, leftZ},
                {-leftX, 1.0D, -leftZ},
                {leftX, -1.0D, leftZ},
                {-leftX, -1.0D, -leftZ}
        };
    }

    private static CorridorCheck corridorCheck(World world, double startX, double startY,
                                               double startZ, double x, double y, double z) {
        double dx = x - startX;
        double dy = y - startY;
        double dz = z - startZ;
        for (int i = 1; i <= MAX_ROUTE_SAMPLES; i++) {
            double f = i / (double) MAX_ROUTE_SAMPLES;
            BlockPos sample = new BlockPos(
                    startX + dx * f, startY + dy * f, startZ + dz * f);
            if (!world.isBlockLoaded(sample)) {
                return new CorridorCheck(false, "UNLOADED", i, sample,
                        startX + dx * f, startY + dy * f, startZ + dz * f);
            }
            /*
             * A solid terminal block is the intended strike surface for a
             * square objective. It is not an obstacle in the final sample;
             * earlier solid samples remain blocking.
             */
            if (i < MAX_ROUTE_SAMPLES
                    && world.getBlockState(sample).getMaterial().blocksMovement()) {
                return new CorridorCheck(false, "SOLID_BLOCK", i, sample,
                        startX + dx * f, startY + dy * f, startZ + dz * f);
            }
        }
        return new CorridorCheck(true, "CLEAR", MAX_ROUTE_SAMPLES,
                new BlockPos(x, y, z), x, y, z);
    }

    private static void logCorridorFailure(World world, MCH_EntityBaseBullet missile,
                                           String leg, double x, double y, double z,
                                           CorridorCheck check) {
        long now = world.getTotalWorldTime();
        Long previous = LAST_CORRIDOR_LOG.get(missile.getUniqueID());
        if (previous != null && now - previous < AVOIDANCE_PLAN_TICKS) return;
        LAST_CORRIDOR_LOG.put(missile.getUniqueID(), now);
        RdpUavDebug.event("Guidance", "corridor_blocked",
                "projectile", missile.getUniqueID(),
                "weapon", missile.getInfo() == null ? "" : missile.getInfo().name,
                "leg", leg,
                "reason", check.reason,
                "sample", check.sample + "/" + MAX_ROUTE_SAMPLES,
                "sampleX", check.x,
                "sampleY", check.y,
                "sampleZ", check.z,
                "block", check.block,
                "targetX", x,
                "targetY", y,
                "targetZ", z);
    }

    private static void logNativeGuidanceObservation(World world,
                                                     MCH_EntityBaseBullet missile,
                                                     GuidedStrikeObjective objective,
                                                     double targetX, double targetY,
                                                     double targetZ) {
        long now = world.getTotalWorldTime();
        Long previous = LAST_COORDINATE_LOG.get(missile.getUniqueID());
        if (previous != null && now - previous < AVOIDANCE_PLAN_TICKS) return;

        BlockPos targetBlock = new BlockPos(targetX, targetY, targetZ);
        boolean targetLoaded = world.isBlockLoaded(targetBlock);
        String targetMaterial = targetLoaded
                ? world.getBlockState(targetBlock).getMaterial().toString() : "UNLOADED";
        String infoName = missile.getInfo() == null ? "" : missile.getInfo().name;
        String infoType = missile.getInfo() == null ? "" : missile.getInfo().type;
        RdpUavDebug.event("Guidance", "native_guidance_observation",
                "projectile", missile.getUniqueID(),
                "projectileClass", missile.getClass().getName(),
                "aircraft", shooterId(missile),
                "objective", objective.getObjectiveId(),
                "objectiveType", objective.getType(),
                "weaponDefinition", infoName,
                "weaponType", infoType,
                "chunkLoadingEnabled", missile.isChunkLoadingEnabled(),
                "dimension", world.provider.getDimension(),
                "projectileX", missile.posX,
                "projectileY", missile.posY,
                "projectileZ", missile.posZ,
                "projectileChunk", (MathHelper.floor(missile.posX) >> 4) + ","
                        + (MathHelper.floor(missile.posZ) >> 4),
                "motionX", missile.motionX,
                "motionY", missile.motionY,
                "motionZ", missile.motionZ,
                "targetX", targetX,
                "targetY", targetY,
                "targetZ", targetZ,
                "targetBlock", targetBlock,
                "targetChunk", (targetBlock.getX() >> 4) + "," + (targetBlock.getZ() >> 4),
                "targetLoaded", targetLoaded,
                "targetMaterial", targetMaterial,
                "targetBlocksMovement",
                targetLoaded && world.getBlockState(targetBlock).getMaterial().blocksMovement(),
                "guidanceBranch", behaviorFor(missile));
    }

    private static final class CorridorCheck {
        private final boolean clear;
        private final String reason;
        private final int sample;
        private final double x;
        private final double y;
        private final double z;
        private final String block;

        private CorridorCheck(boolean clear, String reason, int sample, BlockPos position,
                              double x, double y, double z) {
            this.clear = clear;
            this.reason = reason;
            this.sample = sample;
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = position == null ? "" : position.toString();
        }
    }

    private static final class AimPoint {
        private final double x;
        private final double y;
        private final double z;
        private final boolean avoiding;

        private AimPoint(double x, double y, double z, boolean avoiding) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.avoiding = avoiding;
        }

    }

    private static final class CoordinatePoint {
        private final double x;
        private final double y;
        private final double z;

        private CoordinatePoint(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z;
        }
    }

    private static final class AvoidancePlan {
        private final double x;
        private final double y;
        private final double z;
        private final long expiresAt;

        private AvoidancePlan(double x, double y, double z, long expiresAt) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.expiresAt = expiresAt;
        }
    }

    private static Entity find(World world, UUID id) {
        if (id == null) return null;
        for (Entity entity : world.loadedEntityList) {
            if (entity != null && id.equals(entity.getUniqueID())) return entity;
        }
        return null;
    }

    private static UUID shooterId(MCH_EntityBaseBullet missile) {
        Entity shooter = missile.shootingAircraft == null
                ? missile.shootingEntity : missile.shootingAircraft;
        return shooter == null ? null : shooter.getUniqueID();
    }

    private static final class Assignment {
        private final String weapon;
        private final GuidedStrikeObjective objective;
        private final GuidedTargetFilter filter;
        private GuidanceControlMode mode = GuidanceControlMode.AI_GUIDANCE;
        private Assignment(String weapon, GuidedStrikeObjective objective) {
            this(weapon, objective, null);
        }
        private Assignment(String weapon, GuidedStrikeObjective objective, GuidedTargetFilter filter) {
            this.weapon = weapon; this.objective = objective; this.filter = filter;
        }
    }

    public static final class WeaponTelemetry {
            private final String weapon;
            private final String state;
        private final double x;
            private final double y;
            private final double z;

        private WeaponTelemetry(String weapon, String state, UUID projectileId,
                                double x, double y, double z, String objectiveId) {
                this.weapon = weapon == null ? "" : weapon;
                this.state = state == null ? "UNKNOWN" : state;
            this.x = x; this.y = y; this.z = z;
        }
        public String getWeapon() { return weapon; }
        public String getState() { return state; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
    }
}
