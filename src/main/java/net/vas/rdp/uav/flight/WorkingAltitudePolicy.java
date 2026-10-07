package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.profile.UavVehicleProfile;

/**
 * Working altitude policy for autonomous guidance.
 *
 * <p>Ground safety is authoritative. The target-tracking altitude must remain
 * within the configured working envelope, and any value that would violate the
 * terrain minimum or airframe maximum is clamped back into the safe band.</p>
 */
public final class WorkingAltitudePolicy {
    private WorkingAltitudePolicy() { }

    public static double clampToProfile(UavVehicleProfile profile, double desiredY) {
        if (profile == null || !Double.isFinite(desiredY)) {
            return desiredY;
        }
        return clamp(desiredY,
                profile.getMinimumExecutionAltitude(),
                profile.getMaximumExecutionAltitude());
    }

    public static double enforceGroundSafety(UavVehicleProfile profile,
                                             double desiredY,
                                             double terrainSurfaceY,
                                             double worldHeight) {
        if (profile == null || !Double.isFinite(desiredY)
                || !Double.isFinite(terrainSurfaceY)
                || !Double.isFinite(worldHeight) || worldHeight <= 1.0D) {
            return clampToProfile(profile, desiredY);
        }

        double minSafeY = terrainSurfaceY + profile.getMinimumAgl();
        double maxSafeY = Math.min(worldHeight - 2.0D,
                terrainSurfaceY + profile.getMaximumAgl());
        if (maxSafeY < minSafeY) {
            return clampToProfile(profile, Math.max(0.0D, minSafeY));
        }
        return clamp(desiredY, minSafeY, maxSafeY);
    }

    public static double maintainWorkingAltitude(UavVehicleProfile profile,
                                                double targetY,
                                                double currentY,
                                                double terrainSurfaceY,
                                                double worldHeight) {
        if (profile == null || !Double.isFinite(targetY)
                || !Double.isFinite(currentY)
                || !Double.isFinite(terrainSurfaceY)
                || !Double.isFinite(worldHeight) || worldHeight <= 1.0D) {
            return clampToProfile(profile, targetY);
        }

        double safeTargetY = enforceGroundSafety(profile, targetY, terrainSurfaceY, worldHeight);
        double tolerance = Math.max(0.0D, profile.getAltitudeTolerance());
        double safeCurrentY = enforceGroundSafety(profile, currentY, terrainSurfaceY, worldHeight);
        return Math.abs(safeTargetY - safeCurrentY) <= tolerance
                ? safeCurrentY : safeTargetY;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
