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
        AxisAlignedBB sweep = new AxisAlignedBB(
                Math.min(previousNose.x, currentNose.x),
                Math.min(previousNose.y, currentNose.y),
                Math.min(previousNose.z, currentNose.z),
                Math.max(previousNose.x, currentNose.x),
                Math.max(previousNose.y, currentNose.y),
                Math.max(previousNose.z, currentNose.z))
                .grow(KamikazeConfig.impactSweepPadding);

        World world = aircraft.world;
        if (!world.getCollisionBoxes(aircraft, sweep).isEmpty()) {
            RdpUavDebug.debug("Kamikaze", "nose block impact aircraft={} speed={}",
                    aircraft.getUniqueID(), Math.sqrt(speedSquared));
            return true;
        }
        List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(aircraft, sweep);
        for (Entity entity : entities) {
            if (entity == null || entity.isDead || !entity.canBeCollidedWith()
                    || entity == aircraft.getRidingEntity()
                    || entity.isRidingSameEntity(aircraft)) {
                continue;
            }
            RdpUavDebug.debug("Kamikaze", "nose entity impact aircraft={} target={} type={} speed={}",
                    aircraft.getUniqueID(), entity.getUniqueID(), entity.getClass().getName(),
                    Math.sqrt(speedSquared));
            return true;
        }
        return false;
    }

    private static Vec3d nosePosition(MCH_EntityAircraft aircraft, double x, double y, double z,
                                      float yaw, float pitch) {
        double forwardX = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        double forwardY = -Math.sin(Math.toRadians(pitch));
        double forwardZ = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        AxisAlignedBB bounds = aircraft.getEntityBoundingBox();
        double length = Math.max(bounds.maxX - bounds.minX, bounds.maxZ - bounds.minZ) * 0.5D + 0.5D;
        return new Vec3d(x + forwardX * length, y + forwardY * length, z + forwardZ * length);
    }
}
