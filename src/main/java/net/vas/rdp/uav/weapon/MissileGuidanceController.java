package net.vas.rdp.uav.weapon;

import com.norwood.mcheli.weapon.MCH_EntityASMissile;
import com.norwood.mcheli.weapon.MCH_EntityBaseBullet;
import com.norwood.mcheli.weapon.MCH_EntityTvMissile;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.List;
import net.vas.rdp.uav.RdpUavDebug;

/**
 * Server-side objective handoff for missiles whose native implementations
 * expose coordinate fields. The controller never teleports a projectile,
 * writes projectile motion, or changes gravity, collision, fuse, or impact.
 */
public final class MissileGuidanceController {
    private static final Map<UUID, Assignment> ASSIGNMENTS = new HashMap<UUID, Assignment>();
    private static final Map<UUID, ProjectileGuidanceMission> PROJECTILES =
            new HashMap<UUID, ProjectileGuidanceMission>();
    private static final Map<UUID, AvoidancePlan> AVOIDANCE_PLANS =
            new HashMap<UUID, AvoidancePlan>();
    private static final Map<UUID, Long> LAST_COORDINATE_LOG =
            new HashMap<UUID, Long>();
    private static final int MAX_ROUTE_SAMPLES = 12;
    private static final int MAX_AVOIDANCE_CANDIDATES = 8;
    private static final double AVOIDANCE_OFFSET = 4.0D;
    private static final long AVOIDANCE_PLAN_TICKS = 10L;

    private MissileGuidanceController() { }

    public static synchronized void authorize(UUID aircraftId, String weapon,
                                              GuidedStrikeObjective objective) {
        if (aircraftId == null || weapon == null || objective == null) return;
        ASSIGNMENTS.put(aircraftId, new Assignment(weapon, objective));
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

    public static void tick(World world) {
        if (world == null || world.isRemote) return;
        Set<UUID> liveProjectiles = new HashSet<UUID>();
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
            if (assignment == null || assignment.mode != GuidanceControlMode.AI_GUIDANCE) continue;
            if (!matches(assignment.weapon, missile)) continue;
            ProjectileGuidanceMission projectile = projectileMission(missile, shooter, assignment);
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
            PROJECTILES.keySet().retainAll(liveProjectiles);
            AVOIDANCE_PLANS.keySet().retainAll(liveProjectiles);
            LAST_COORDINATE_LOG.keySet().retainAll(liveProjectiles);
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
        if (aim == null) {
            boolean firstObstacleFailure =
                    projectile.getState() != ProjectileGuidanceState.OBSTACLE_UNAVAILABLE;
            projectile.obstacleUnavailable();
            if (firstObstacleFailure) {
                RdpUavDebug.event("Guidance", "obstacle_unavailable",
                        "aircraft", shooterId(missile),
                        "projectile", missile.getUniqueID(), "objective", objective.getObjectiveId(),
                        "weapon", assignment.weapon);
            }
            RdpUavDebug.debug("Guidance",
                    "aircraft={} projectile={} avoidance no safe candidate weapon={}",
                    shooterId(missile),
                    missile.getUniqueID(), assignment.weapon);
            return;
        }
        x = aim.x;
        y = aim.y;
        z = aim.z;
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
        if (missile instanceof MCH_EntityTvMissile) {
            MCH_EntityTvMissile tv = (MCH_EntityTvMissile) missile;
            // These are McHeli's native TV target fields. Do not replace
            // projectile motion; McHeli performs steering and collision.
            tv.targetPosX = x;
            tv.targetPosY = y;
            tv.targetPosZ = z;
        } else if (missile instanceof MCH_EntityASMissile) {
            MCH_EntityASMissile as = (MCH_EntityASMissile) missile;
            // ASM/Maverick retains its native flight, gravity, seeker, and
            // impact behavior; only the native target coordinates are updated.
            as.targetPosX = x;
            as.targetPosY = y;
            as.targetPosZ = z;
        }
        long now = world.getTotalWorldTime();
        Long previous = LAST_COORDINATE_LOG.get(missile.getUniqueID());
        if (previous == null || now - previous.longValue() >= AVOIDANCE_PLAN_TICKS) {
            LAST_COORDINATE_LOG.put(missile.getUniqueID(), now);
            RdpUavDebug.event("Guidance", "native_target_update",
                    "aircraft", shooterId(missile),
                    "projectile", missile.getUniqueID(), "objective", objective.getObjectiveId(),
                    "weapon", assignment.weapon, "mode", behaviorFor(missile),
                    "x", x, "y", y, "z", z);
        }
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
                && corridorClear(world, missile, existing.x, existing.y, existing.z)) {
            return new AimPoint(existing.x, existing.y, existing.z, true);
        }
        AVOIDANCE_PLANS.remove(missile.getUniqueID());
        if (corridorClear(world, missile, x, y, z)) {
            return new AimPoint(x, y, z, false);
        }
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
        double[][] offsets = {
                {leftX, 0.0D, leftZ},
                {-leftX, 0.0D, -leftZ},
                {0.0D, 1.0D, 0.0D},
                {0.0D, -1.0D, 0.0D},
                {leftX, 1.0D, leftZ},
                {-leftX, 1.0D, -leftZ},
                {leftX, -1.0D, leftZ},
                {-leftX, -1.0D, -leftZ}
        };
        AimPoint best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < Math.min(MAX_AVOIDANCE_CANDIDATES, offsets.length); i++) {
            double candidateX = x + offsets[i][0] * AVOIDANCE_OFFSET;
            double candidateY = Math.max(1.0D, y + offsets[i][1] * AVOIDANCE_OFFSET);
            double candidateZ = z + offsets[i][2] * AVOIDANCE_OFFSET;
            if (!corridorClear(world, missile, candidateX, candidateY, candidateZ)
                    || !corridorClear(world, candidateX, candidateY, candidateZ, x, y, z)) {
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
        }
        return best;
    }

    private static boolean corridorClear(World world, MCH_EntityBaseBullet missile,
                                         double x, double y, double z) {
        return corridorClear(world, missile.posX, missile.posY, missile.posZ, x, y, z);
    }

    private static boolean corridorClear(World world, double startX, double startY, double startZ,
                                         double x, double y, double z) {
        double dx = x - startX;
        double dy = y - startY;
        double dz = z - startZ;
        for (int i = 1; i <= MAX_ROUTE_SAMPLES; i++) {
            double f = i / (double) MAX_ROUTE_SAMPLES;
            net.minecraft.util.math.BlockPos sample = new net.minecraft.util.math.BlockPos(
                    startX + dx * f, startY + dy * f, startZ + dz * f);
            if (!world.isBlockLoaded(sample)
                    || world.getBlockState(sample).getMaterial().blocksMovement()) {
                return false;
            }
        }
        return true;
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
}
