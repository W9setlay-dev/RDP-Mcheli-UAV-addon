package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.target.ContactSnapshot;

/**
 * Pure target-relative geometry for planner and simulation tests.
 *
 * <p>This deliberately stops at a geometric vector. It does not write native
 * McHeli state and is not wired into WingMan steering in this phase.</p>
 */
public final class TargetVectorGuidance implements GuidanceController {
    @Override
    public GuidanceVector calculate(double vehicleX, double vehicleY, double vehicleZ,
                                    ContactSnapshot target, double predictionTicks) {
        if (target == null || !Double.isFinite(predictionTicks) || predictionTicks < 0.0D) {
            throw new IllegalArgumentException("Target and prediction are required");
        }
        double targetX = target.getX() + target.getVelocityX() * predictionTicks;
        double targetY = target.getY() + target.getVelocityY() * predictionTicks;
        double targetZ = target.getZ() + target.getVelocityZ() * predictionTicks;
        return new GuidanceVector(targetX - vehicleX, targetY - vehicleY,
                targetZ - vehicleZ);
    }
}
