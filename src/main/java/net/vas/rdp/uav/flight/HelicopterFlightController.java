package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.profile.UavVehicleProfile;

/**
 * Helicopter controller with independent vertical and horizontal control.
 */
public final class HelicopterFlightController implements AirframeController {
    private final UavVehicleProfile profile;

    public HelicopterFlightController(UavVehicleProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("Profile is required");
        }
        this.profile = profile;
    }

    @Override
    public FlightCommand update(AircraftState state, double targetX, double targetY,
                                double targetZ, long currentTick) {
        if (state == null || !Double.isFinite(targetX) || !Double.isFinite(targetY)
                || !Double.isFinite(targetZ) || currentTick < 0L) {
            throw new IllegalArgumentException("Invalid helicopter controller inputs");
        }
        if (state.getSampleTick() < 0L || currentTick - state.getSampleTick() > 40L) {
            return FlightCommand.hover();
        }
        double dx = targetX - state.getX();
        double dy = targetY - state.getY();
        double dz = targetZ - state.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double altitudeDelta = targetY - state.getY();
        double tolerance = Math.max(0.75D, profile.getAltitudeTolerance());
        if (horizontalDistance < 1.0D && Math.abs(altitudeDelta) < tolerance) {
            return FlightCommand.hover();
        }
        double forward = clamp(horizontalDistance > 0.0D ? (dx / horizontalDistance) : 0.0D,
                -1.0D, 1.0D);
        double lateral = clamp((dz / Math.max(horizontalDistance, 1.0D)) * 0.5D,
                -1.0D, 1.0D);
        double vertical = clamp(altitudeDelta * 0.85D, -12.0D, 12.0D);
        double speed = clamp(profile.getCruiseSpeed(), 0.25D, 1.0D);
        return new FlightCommand(forward, lateral, vertical, 0.0D, 0.0D, speed,
                net.vas.rdp.uav.flight.FpvFlightMode.AI_APPROACH, "NONE", "NONE");
    }

    public UavVehicleProfile getProfile() {
        return profile;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
