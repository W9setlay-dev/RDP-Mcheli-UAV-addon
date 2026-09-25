package net.vas.rdp.uav.flight;

/**
 * Absolute, bounded flight setpoints produced by an airframe controller.
 *
 * <p>The command describes desired attitude and engine throttle. It never
 * changes entity position or motion; MCheliCE remains responsible for flight
 * physics and collision response.</p>
 */
public final class NativeFlightControlCommand {
    private final boolean supported;
    private final String reason;
    private final float yaw;
    private final float pitch;
    private final float roll;
    private final double throttle;
    private final double desiredTrack;
    private final double courseError;
    private final double desiredVerticalSpeed;

    private NativeFlightControlCommand(boolean supported, String reason,
                                       float yaw, float pitch, float roll,
                                       double throttle, double desiredTrack,
                                       double courseError,
                                       double desiredVerticalSpeed) {
        if (supported && (!finite(yaw, pitch, roll, throttle, desiredTrack,
                courseError, desiredVerticalSpeed) || throttle < 0.0D || throttle > 1.0D)) {
            throw new IllegalArgumentException("Invalid native flight command");
        }
        this.supported = supported;
        this.reason = reason == null || reason.trim().isEmpty() ? "OK" : reason.trim();
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.throttle = throttle;
        this.desiredTrack = desiredTrack;
        this.courseError = courseError;
        this.desiredVerticalSpeed = desiredVerticalSpeed;
    }

    public static NativeFlightControlCommand setpoints(float yaw, float pitch, float roll,
                                                        double throttle, double desiredTrack,
                                                        double courseError,
                                                        double desiredVerticalSpeed) {
        return new NativeFlightControlCommand(true, "OK", yaw, pitch, roll,
                throttle, desiredTrack, courseError, desiredVerticalSpeed);
    }

    public static NativeFlightControlCommand unsupported(String reason) {
        return new NativeFlightControlCommand(false, reason, 0.0F, 0.0F, 0.0F,
                0.0D, Double.NaN, Double.NaN, Double.NaN);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    public boolean isSupported() { return supported; }
    public String getReason() { return reason; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public float getRoll() { return roll; }
    public double getThrottle() { return throttle; }
    public double getDesiredTrack() { return desiredTrack; }
    public double getCourseError() { return courseError; }
    public double getDesiredVerticalSpeed() { return desiredVerticalSpeed; }
}
