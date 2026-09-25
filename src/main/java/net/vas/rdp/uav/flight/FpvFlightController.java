package net.vas.rdp.uav.flight;

/**
 * Deterministic FPV/heli guidance contract for native mission ticks.
 *
 * <p>This controller produces a bounded target-relative command without
 * writing entity position, motion, or operator input. It is intentionally a
 * pure policy bridge between mission intent and the native McHeli flight
 * command layer.</p>
 */
public final class FpvFlightController {
    private FpvFlightController() { }

    public static FlightCommand compute(double currentX, double currentY, double currentZ,
                                        double targetX, double targetY, double targetZ,
                                        double currentYaw, boolean terminal) {
        if (!Double.isFinite(currentX) || !Double.isFinite(currentY)
                || !Double.isFinite(currentZ) || !Double.isFinite(targetX)
                || !Double.isFinite(targetY) || !Double.isFinite(targetZ)
                || !Double.isFinite(currentYaw)) {
            throw new IllegalArgumentException("Invalid FPV controller inputs");
        }
        double dx = targetX - currentX;
        double dy = targetY - currentY;
        double dz = targetZ - currentZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.25D && Math.abs(dy) < 0.25D) {
            return FlightCommand.hover();
        }
        double forward = clamp(horizontal / 24.0D, 0.0D, 1.0D);
        double vertical = clamp(dy * 0.75D, -16.0D, 16.0D);
        double speed = clamp(0.45D + horizontal / 64.0D, 0.25D, 1.0D);
        if (terminal) {
            return FlightCommand.terminalApproach(forward, vertical, speed);
        }
        return FlightCommand.approach(forward, vertical, speed);
    }

    public static FlightCommand compute(AircraftState state, double targetX, double targetY,
                                        double targetZ, boolean terminal) {
        if (state == null) {
            throw new IllegalArgumentException("Aircraft state is required");
        }
        return compute(state.getX(), state.getY(), state.getZ(),
                targetX, targetY, targetZ, state.getYaw(), terminal);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
