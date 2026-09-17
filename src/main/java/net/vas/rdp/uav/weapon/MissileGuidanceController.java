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
 * expose coordinate fields and setMotion. The controller never teleports a
 * projectile and never changes gravity, collision, fuse, or impact handling.
 */
public final class MissileGuidanceController {
    private static final Map<UUID, Assignment> ASSIGNMENTS = new HashMap<UUID, Assignment>();
    private static final Map<UUID, ProjectileGuidanceMission> PROJECTILES =
            new HashMap<UUID, ProjectileGuidanceMission>();
    private static final int MAX_ROUTE_SAMPLES = 12;

    private MissileGuidanceController() { }

    public static synchronized void authorize(UUID aircraftId, String weapon,
                                              GuidedStrikeObjective objective) {
        if (aircraftId == null || weapon == null || objective == null) return;
        ASSIGNMENTS.put(aircraftId, new Assignment(weapon, objective));
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
                }
            }
            apply(world, missile, assignment);
        }
        synchronized (MissileGuidanceController.class) {
            PROJECTILES.keySet().retainAll(liveProjectiles);
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
                objective.loseContact();
                projectile.targetLost();
                RdpUavDebug.debug("Guidance", "aircraft={} projectile target lost weapon={}",
                        missile.shootingAircraft == null ? null
                                : missile.shootingAircraft.getUniqueID(), assignment.weapon);
                return;
            }
        }
        if (!corridorClear(world, missile, x, y, z)) {
            projectile.obstacleUnavailable();
            RdpUavDebug.debug("Guidance",
                    "aircraft={} projectile={} native obstacle avoidance unavailable weapon={}",
                    missile.shootingAircraft == null ? null
                            : missile.shootingAircraft.getUniqueID(),
                    missile.getUniqueID(), assignment.weapon);
            return;
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

    private static boolean corridorClear(World world, MCH_EntityBaseBullet missile,
                                         double x, double y, double z) {
        double dx = x - missile.posX;
        double dy = y - missile.posY;
        double dz = z - missile.posZ;
        for (int i = 1; i <= MAX_ROUTE_SAMPLES; i++) {
            double f = i / (double) MAX_ROUTE_SAMPLES;
            net.minecraft.util.math.BlockPos sample = new net.minecraft.util.math.BlockPos(
                    missile.posX + dx * f, missile.posY + dy * f, missile.posZ + dz * f);
            if (!world.isBlockLoaded(sample)
                    || world.getBlockState(sample).getMaterial().blocksMovement()) {
                return false;
            }
        }
        return true;
    }

    private static Entity find(World world, UUID id) {
        if (id == null) return null;
        for (Entity entity : world.loadedEntityList) {
            if (entity != null && id.equals(entity.getUniqueID())) return entity;
        }
        return null;
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
