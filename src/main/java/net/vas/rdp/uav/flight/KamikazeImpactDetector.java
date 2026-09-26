package net.vas.rdp.uav.flight;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.vas.rdp.uav.RdpUavDebug;

import java.util.List;

/**
 * Detects a forward/nose impact without replacing McHeli's collision physics.
 */
public final class KamikazeImpactDetector {
    private KamikazeImpactDetector() { }

    public static boolean hasNoseImpact(MCH_EntityAircraft aircraft) {
        return hasNoseImpact(aircraft, KamikazeConfig.noseTriggerDistance);
    }

    public static boolean hasNoseImpact(MCH_EntityAircraft aircraft, double triggerDistance) {
        if (aircraft == null || aircraft.world == null) return false;
        double speedSquared = aircraft.motionX * aircraft.motionX
                + aircraft.motionY * aircraft.motionY
                + aircraft.motionZ * aircraft.motionZ;
        if (speedSquared < KamikazeConfig.minimumImpactSpeed
                * KamikazeConfig.minimumImpactSpeed) return false;

        Vec3d previousNose = nosePosition(aircraft, aircraft.prevPosX, aircraft.prevPosY,
                aircraft.prevPosZ, aircraft.prevRotationYaw, aircraft.prevRotationPitch);
        Vec3d currentNose = nosePosition(aircraft, aircraft.posX, aircraft.posY,
                aircraft.posZ, aircraft.rotationYaw, aircraft.rotationPitch);
        Vec3d forward = forwardVector(aircraft.rotationYaw, aircraft.rotationPitch);
        Vec3d noseEnd = currentNose.add(forward.scale(triggerDistance));
        AxisAlignedBB sweep = new AxisAlignedBB(previousNose, noseEnd)
                .grow(KamikazeConfig.impactSweepPadding);

        World world = aircraft.world;
        net.minecraft.util.math.RayTraceResult blockHit = world.rayTraceBlocks(
                previousNose, noseEnd, false, true, false);
        if (blockHit != null
                && blockHit.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
            if (aircraft.ticksExisted % 20 == 0) {
                RdpUavDebug.debug("Kamikaze",
                        "nose block contact aircraft={} block={} speed={}",
                        aircraft.getUniqueID(), blockHit.getBlockPos(), Math.sqrt(speedSquared));
            }
            return true;
        }
        List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(aircraft, sweep);
        for (Entity entity : entities) {
            if (entity == null || entity.isDead || !entity.canBeCollidedWith()
                    || entity == aircraft.getRidingEntity()
                    || entity.isRidingSameEntity(aircraft)) {
                continue;
            }
            AxisAlignedBB contactBounds = entity.getEntityBoundingBox()
                    .grow(KamikazeConfig.impactSweepPadding);
            if (contactBounds.calculateIntercept(previousNose, noseEnd) != null) {
                if (aircraft.ticksExisted % 20 == 0) {
                    RdpUavDebug.debug("Kamikaze",
                            "nose entity contact aircraft={} target={} type={} speed={}",
                            aircraft.getUniqueID(), entity.getUniqueID(), entity.getClass().getName(),
                            Math.sqrt(speedSquared));
                }
                return true;
            }
        }
        return false;
    }

    public static boolean isAimedAtTargetInNoseZone(double aircraftX, double aircraftY, double aircraftZ,
                                                    float yaw, float pitch,
                                                    double targetX, double targetY, double targetZ,
                                                    double triggerDistance) {
        Vec3d forward = forwardVector(yaw, pitch);
        double dx = targetX - aircraftX;
        double dy = targetY - aircraftY;
        double dz = targetZ - aircraftZ;
        double projection = dx * forward.x + dy * forward.y + dz * forward.z;
        if (projection <= 0.0D || projection > triggerDistance + 1.0D) {
            return false;
        }
        double offsetX = dx - projection * forward.x;
        double offsetY = dy - projection * forward.y;
        double offsetZ = dz - projection * forward.z;
        double offsetSq = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
        return offsetSq <= (1.25D + triggerDistance * 0.15D) * (1.25D + triggerDistance * 0.15D);
    }

    private static Vec3d forwardVector(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vec3d(x, y, z);
    }

    private static Vec3d nosePosition(MCH_EntityAircraft aircraft, double x, double y, double z,
                                      float yaw, float pitch) {
        Vec3d forward = forwardVector(yaw, pitch);
        AxisAlignedBB bounds = aircraft.getEntityBoundingBox();
        double length = Math.max(bounds.maxX - bounds.minX, bounds.maxZ - bounds.minZ) * 0.5D + 0.5D;
        return new Vec3d(x + forward.x * length, y + forward.y * length, z + forward.z * length);
    }
}
