package net.vas.rdp.uav.flight;

/**
 * The single movement decision emitted for an FPV tick.
 *
 * <p>WingMan consumes this decision through its native route API. This value
 * does not write entity position or motion and is intentionally immutable so
 * hover and pursuit cannot be merged into one tick.</p>
 */
public final class FlightCommand {
    private static final double MAX_VERTICAL_ERROR = 16.0D;
    private static final double MAX_FORWARD = 1.0D;
    private final double forward;
    private final double lateral;
    private final double vertical;
    private final double yaw;
    private final double pitch;
    private final double speed;
    private final FpvFlightMode mode;
    private final String terminalState;
    private final String avoidanceState;

    public FlightCommand(double forward, double lateral, double vertical,
                         double yaw, double pitch, double speed,
                         FpvFlightMode mode, String terminalState,
                         String avoidanceState) {
        if (mode == null || !Double.isFinite(forward) || !Double.isFinite(lateral)
                || !Double.isFinite(vertical) || !Double.isFinite(yaw)
                || !Double.isFinite(pitch) || !Double.isFinite(speed)
                || speed < 0.0D) {
            throw new IllegalArgumentException("Invalid flight command");
        }
        this.forward = forward;
        this.lateral = lateral;
        this.vertical = vertical;
        this.yaw = yaw;
        this.pitch = pitch;
        this.speed = speed;
        this.mode = mode;
        this.terminalState = terminalState == null ? "" : terminalState;
        this.avoidanceState = avoidanceState == null ? "" : avoidanceState;
    }

    public static FlightCommand hover() {
        return new FlightCommand(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                FpvFlightMode.HOVER, "NONE", "NONE");
    }

    public static FlightCommand approach(double forward, double vertical, double speed) {
        return new FlightCommand(clamp(forward, -MAX_FORWARD, MAX_FORWARD), 0.0D,
                clamp(vertical, -MAX_VERTICAL_ERROR, MAX_VERTICAL_ERROR), 0.0D, 0.0D, speed,
                FpvFlightMode.AI_APPROACH, "NONE", "NONE");
    }

    public static FlightCommand terminalApproach(double forward, double vertical,
                                                 double speed) {
        return new FlightCommand(clamp(forward, -MAX_FORWARD, MAX_FORWARD), 0.0D,
                clamp(vertical, -MAX_VERTICAL_ERROR, MAX_VERTICAL_ERROR), 0.0D, 0.0D, speed,
                FpvFlightMode.TERMINAL_APPROACH, "ARMED", "NONE");
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public double getForward() { return forward; }
    public double getLateral() { return lateral; }
    public double getVertical() { return vertical; }
    public double getYaw() { return yaw; }
    public double getPitch() { return pitch; }
    public double getSpeed() { return speed; }
    public FpvFlightMode getMode() { return mode; }
    public String getTerminalState() { return terminalState; }
    public String getAvoidanceState() { return avoidanceState; }
}
