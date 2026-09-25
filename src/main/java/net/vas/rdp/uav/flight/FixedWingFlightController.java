package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.mission.ControlOwner;
import net.vas.rdp.uav.profile.UavVehicleProfile;

/**
 * Fixed-wing controller tuned to the conservative native MCheliCE envelopes.
 */
public final class FixedWingFlightController implements AirframeController {
    private final UavVehicleProfile profile;

    public FixedWingFlightController(UavVehicleProfile profile) {
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
            throw new IllegalArgumentException("Invalid fixed-wing controller inputs");
        }
        if (state.getSampleTick() < 0L || currentTick - state.getSampleTick() > 40L) {
            return FlightCommand.hover();
        }
        double dx = targetX - state.getX();
        double dy = targetY - state.getY();
        double dz = targetZ - state.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double altitudeDelta = targetY - state.getY();
        double tolerance = Math.max(1.0D, profile.getAltitudeTolerance());
        double speed = clamp(profile.getCruiseSpeed(), 0.3D, 1.0D);
        if (horizontalDistance < 1.0D && Math.abs(altitudeDelta) < tolerance) {
            return FlightCommand.hover();
        }
        double forward = clamp(horizontalDistance / Math.max(12.0D, profile.getTurnRadius()), 0.0D, 1.0D);
        double vertical = clamp(altitudeDelta * 0.75D, -16.0D, 16.0D);
        if (Math.abs(altitudeDelta) < tolerance) {
            vertical = 0.0D;
        }
        return FlightCommand.approach(forward, vertical, speed);
    }

    public UavVehicleProfile getProfile() {
        return profile;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
