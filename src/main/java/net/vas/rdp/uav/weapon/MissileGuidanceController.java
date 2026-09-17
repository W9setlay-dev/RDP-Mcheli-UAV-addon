package net.vas.rdp.uav.weapon;

import com.norwood.mcheli.weapon.MCH_EntityASMissile;
import com.norwood.mcheli.weapon.MCH_EntityBaseBullet;
import com.norwood.mcheli.weapon.MCH_EntityTvMissile;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side objective handoff for missiles whose native implementations
 * expose coordinate fields and setMotion. The controller never teleports a
 * projectile and never changes gravity, collision, fuse, or impact handling.
 */
public final class MissileGuidanceController {
    private static final Map<UUID, Assignment> ASSIGNMENTS = new HashMap<UUID, Assignment>();
    private static final int MAX_ROUTE_SAMPLES = 12;

    private MissileGuidanceController() { }

    public static synchronized void authorize(UUID aircraftId, String weapon,
                                              GuidedStrikeObjective objective) {
        if (aircraftId == null || weapon == null || objective == null) return;
        ASSIGNMENTS.put(aircraftId, new Assignment(weapon, objective));
    }

    public static synchronized void abort(UUID aircraftId) {
        if (aircraftId != null) {
            Assignment assignment = ASSIGNMENTS.get(aircraftId);
            if (assignment != null) assignment.mode = GuidanceControlMode.ABORTED;
            ASSIGNMENTS.remove(aircraftId);
        }
    }

    public static synchronized void clear(UUID aircraftId) {
        if (aircraftId != null) ASSIGNMENTS.remove(aircraftId);
    }

    public static void tick(World world) {
        if (world == null || world.isRemote) return;
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof MCH_EntityBaseBullet)) continue;
            MCH_EntityBaseBullet missile = (MCH_EntityBaseBullet) entity;
            Entity shooter = missile.shootingAircraft;
            if (shooter == null) shooter = missile.shootingEntity;
            if (shooter == null) continue;
            Assignment assignment;
            synchronized (MissileGuidanceController.class) {
                assignment = ASSIGNMENTS.get(shooter.getUniqueID());
            }
            if (assignment == null || assignment.mode != GuidanceControlMode.AI_GUIDANCE) continue;
            if (!matches(assignment.weapon, missile)) continue;
            apply(world, missile, assignment.objective);
        }
    }

    private static boolean matches(String weapon, MCH_EntityBaseBullet missile) {
        if (missile.getInfo() == null || weapon == null) return false;
        return weapon.equalsIgnoreCase(missile.getInfo().name)
                || (missile instanceof MCH_EntityTvMissile && "tvmissile".equalsIgnoreCase(weapon))
                || (missile instanceof MCH_EntityASMissile && "asmissile".equalsIgnoreCase(weapon));
    }

    private static void apply(World world, MCH_EntityBaseBullet missile,
                              GuidedStrikeObjective objective) {
        double x = objective.getX();
        double y = objective.getY();
        double z = objective.getZ();
        if (objective.getType() == GuidedObjectiveType.ENTITY) {
            Entity target = find(world, objective.getContactId());
            if (target != null) {
                x = target.posX; y = target.posY; z = target.posZ;
                objective.updateContact(x, y, z, world.getTotalWorldTime());
            }
        }
        BlockPos waypoint = nextWaypoint(world, missile, x, y, z);
        if (missile instanceof MCH_EntityTvMissile) {
            MCH_EntityTvMissile tv = (MCH_EntityTvMissile) missile;
            tv.targetPosX = waypoint.getX() + 0.5D;
            tv.targetPosY = waypoint.getY() + 0.5D;
            tv.targetPosZ = waypoint.getZ() + 0.5D;
            steer(missile, tv.targetPosX, tv.targetPosY, tv.targetPosZ);
        } else if (missile instanceof MCH_EntityASMissile) {
            MCH_EntityASMissile as = (MCH_EntityASMissile) missile;
            as.targetPosX = waypoint.getX() + 0.5D;
            as.targetPosY = waypoint.getY() + 0.5D;
            as.targetPosZ = waypoint.getZ() + 0.5D;
        }
    }

    private static void steer(MCH_EntityBaseBullet missile, double x, double y, double z) {
        double dx = x - missile.posX;
        double dy = y - missile.posY;
        double dz = z - missile.posZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 0.001D) missile.setMotion(dx / length, dy / length, dz / length);
    }

    private static BlockPos nextWaypoint(World world, MCH_EntityBaseBullet missile,
                                         double x, double y, double z) {
        double dx = x - missile.posX;
        double dy = y - missile.posY;
        double dz = z - missile.posZ;
        for (int i = 1; i <= MAX_ROUTE_SAMPLES; i++) {
            double f = i / (double) MAX_ROUTE_SAMPLES;
            BlockPos sample = new BlockPos(missile.posX + dx * f, missile.posY + dy * f,
                    missile.posZ + dz * f);
            if (!world.isBlockLoaded(sample) || world.getBlockState(sample).getMaterial().blocksMovement()) {
                return new BlockPos(missile.posX, missile.posY + 2.0D, missile.posZ);
            }
        }
        return new BlockPos(x, y, z);
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
        private GuidanceControlMode mode = GuidanceControlMode.AI_GUIDANCE;
        private Assignment(String weapon, GuidedStrikeObjective objective) {
            this.weapon = weapon; this.objective = objective;
        }
    }
}
