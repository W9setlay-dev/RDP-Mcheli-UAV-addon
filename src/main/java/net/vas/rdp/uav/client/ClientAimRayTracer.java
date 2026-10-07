package net.vas.rdp.uav.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;
import java.util.Locale;

/** Client-only crosshair trace that follows the active Minecraft render camera. */
@SideOnly(Side.CLIENT)
public final class ClientAimRayTracer {
    public static final double MAX_REACH = 500.0D;
    private static final double BROAD_PHASE_GROWTH = 1.0D;

    private ClientAimRayTracer() { }

    /**
     * Trace from the active view entity. Blocks are ray-traced first; an entity
     * is returned only if its actual bounding box intersects the ray before
     * the first block hit (or anywhere on the ray if there is no block).
     */
    public static AimTarget trace(Minecraft minecraft, float partialTicks,
                                  Entity... ignoredEntities) {
        if (minecraft == null || minecraft.world == null) return AimTarget.miss(null);

        World world = minecraft.world;
        // McHeliCE can replace the render view entity with its UAV camera proxy.
        Entity camera = minecraft.getRenderViewEntity();
        if (camera == null) camera = minecraft.player;
        if (camera == null || camera.world != world) return AimTarget.miss(null);

        float ticks = Float.isFinite(partialTicks)
                ? Math.max(0.0F, Math.min(1.0F, partialTicks)) : 1.0F;
        Vec3d start = camera.getPositionEyes(ticks);
        Vec3d look = camera.getLook(ticks);
        if (hasInvalidVector(start) || hasInvalidVector(look)
                || look.lengthSquared() < 1.0E-12D) {
            return AimTarget.miss(start);
        }

        // Minecraft's yaw/pitch projection is:
        // dx = -sin(yaw) * cos(pitch), dy = -sin(pitch),
        // dz =  cos(yaw) * cos(pitch). Entity#getLook applies this using the
        // render-interpolated camera angles (yaw=0 looks toward +Z).
        look = look.normalize();
        Vec3d end = start.add(look.scale(MAX_REACH));

        RayTraceResult blockHit = world.rayTraceBlocks(
                start, end, false, true, false);
        boolean blockHitFound = blockHit != null
                && blockHit.typeOfHit == RayTraceResult.Type.BLOCK
                && blockHit.hitVec != null;

        Vec3d entityTraceEnd = blockHitFound ? blockHit.hitVec : end;
        double closestDistanceSquared = blockHitFound
                ? start.squareDistanceTo(blockHit.hitVec)
                : start.squareDistanceTo(end);

        AxisAlignedBB searchBox = new AxisAlignedBB(
                Math.min(start.x, entityTraceEnd.x),
                Math.min(start.y, entityTraceEnd.y),
                Math.min(start.z, entityTraceEnd.z),
                Math.max(start.x, entityTraceEnd.x),
                Math.max(start.y, entityTraceEnd.y),
                Math.max(start.z, entityTraceEnd.z)
        ).grow(BROAD_PHASE_GROWTH);

        List<Entity> candidates = world.getEntitiesWithinAABBExcludingEntity(camera, searchBox);
        Entity closestEntity = null;
        Vec3d closestEntityHit = null;
        for (Entity candidate : candidates) {
            if (candidate == null || candidate.isDead || candidate.world != world
                    || !candidate.canBeCollidedWith()
                    || isIgnored(candidate, camera, ignoredEntities)) {
                continue;
            }

            // Deliberately use the actual box without expanding it: a merely
            // nearby entity must not steal a point selection from the crosshair.
            RayTraceResult intercept = candidate.getEntityBoundingBox()
                    .calculateIntercept(start, entityTraceEnd);
            if (intercept == null || intercept.hitVec == null) continue;

            double distanceSquared = start.squareDistanceTo(intercept.hitVec);
            // Strictly closer: the block wins an exact-distance tie.
            if (distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared;
                closestEntity = candidate;
                closestEntityHit = intercept.hitVec;
            }
        }

        AimTarget result;
        if (closestEntity != null) {
            result = AimTarget.entity(closestEntity, closestEntityHit);
        } else if (blockHitFound) {
            result = AimTarget.block(blockHit.getBlockPos(), blockHit.hitVec);
        } else {
            result = AimTarget.miss(end);
        }

        logResult(camera, start, look, result);
        return result;
    }

    private static boolean isIgnored(Entity candidate, Entity camera,
                                     Entity... ignoredEntities) {
        if (candidate == camera || ignoredEntities == null) return candidate == camera;
        for (Entity ignored : ignoredEntities) {
            if (candidate == ignored) return true;
        }
        return false;
    }

    private static boolean hasInvalidVector(Vec3d vector) {
        return vector == null || !Double.isFinite(vector.x)
                || !Double.isFinite(vector.y) || !Double.isFinite(vector.z);
    }

    private static void logResult(Entity camera, Vec3d start, Vec3d look,
                                  AimTarget result) {
        Vec3d hit = result.getHitVec();
        double distance = hit == null ? Double.NaN
                : Math.sqrt(start.squareDistanceTo(hit));
        String target = result.isBlock() ? String.valueOf(result.getBlockPos())
                : result.isEntity() ? result.getEntity().getName() + "/"
                + result.getEntity().getUniqueID() : "none";
        System.out.printf(Locale.ROOT,
                "[RDP-UAV][AimRay] camera=%s yaw=%.2f pitch=%.2f "
                        + "look=(%.4f,%.4f,%.4f) type=%s target=%s "
                        + "hit=(%s) distance=%.2f%n",
                camera.getClass().getName(), camera.rotationYaw, camera.rotationPitch,
                look.x, look.y, look.z, result.getKind(), target,
                hit == null ? "unknown" : String.format(Locale.ROOT,
                        "%.3f,%.3f,%.3f", hit.x, hit.y, hit.z), distance);
    }

    public static final class AimTarget {
        public enum Kind { BLOCK, ENTITY, MISS }

        private final Kind kind;
        private final BlockPos blockPos;
        private final Entity entity;
        private final Vec3d hitVec;

        private AimTarget(Kind kind, BlockPos blockPos, Entity entity, Vec3d hitVec) {
            this.kind = kind;
            this.blockPos = blockPos;
            this.entity = entity;
            this.hitVec = hitVec;
        }

        private static AimTarget block(BlockPos pos, Vec3d hitVec) {
            return new AimTarget(Kind.BLOCK, pos, null, hitVec);
        }

        private static AimTarget entity(Entity entity, Vec3d hitVec) {
            return new AimTarget(Kind.ENTITY, null, entity, hitVec);
        }

        private static AimTarget miss(Vec3d end) {
            return new AimTarget(Kind.MISS, null, null, end);
        }

        public Kind getKind() { return kind; }
        public boolean isBlock() { return kind == Kind.BLOCK; }
        public boolean isEntity() { return kind == Kind.ENTITY; }
        public boolean isMiss() { return kind == Kind.MISS; }
        public boolean isHit() { return kind != Kind.MISS; }
        public BlockPos getBlockPos() { return blockPos; }
        public Entity getEntity() { return entity; }
        public Vec3d getHitVec() { return hitVec; }
    }
}
