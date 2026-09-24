package net.vas.rdp.uav.flight;

/**
 * Pure, deterministic attitude/throttle command shaping for the native
 * McHeliCE controller.
 */
public final class NativeControlMath {
    private NativeControlMath() { }

    public static Command compute(float currentYaw, float currentPitch, float currentRoll,
                                  double currentThrottle, double dx, double dy, double dz,
                                  boolean helicopter, double forwardOverride) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yaw = approachAngle(currentYaw, desiredYaw, helicopter ? 4.0F : 3.0F);
        float desiredPitch = (float) -Math.toDegrees(
                Math.atan2(dy, Math.max(1.0D, horizontal)));
        float pitch = approach(currentPitch, clamp(desiredPitch, -25.0F, 20.0F), 3.0F);
        float desiredRoll = helicopter ? 0.0F
                : clamp(angleDelta(desiredYaw, currentYaw) * 0.8F, -35.0F, 35.0F);
        float roll = approach(currentRoll, desiredRoll, 5.0F);
        double throttle = helicopter
                ? clamp(0.55D + dy * 0.01D, 0.25D, 1.0D)
                : 0.65D + Math.min(0.25D,
                Math.abs(angleDelta(desiredYaw, currentYaw)) / 180.0D);
        if (Double.isFinite(forwardOverride)) {
            throttle = clamp(0.55D + forwardOverride * 0.35D, 0.2D, 1.0D);
        }
        return new Command(yaw, pitch, roll, approach(currentThrottle, throttle, 0.1D));
    }

    private static float approachAngle(float current, float target, float maximumDelta) {
        return current + clamp(angleDelta(target, current), -maximumDelta, maximumDelta);
    }

    private static float angleDelta(float target, float current) {
        float delta = target - current;
        while (delta > 180.0F) delta -= 360.0F;
        while (delta < -180.0F) delta += 360.0F;
        return delta;
    }

    private static float approach(float current, float target, float maximumDelta) {
        return current + clamp(target - current, -maximumDelta, maximumDelta);
    }

    private static double approach(double current, double target, double maximumDelta) {
        return current + Math.max(-maximumDelta, Math.min(maximumDelta, target - current));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class Command {
        private final float yaw;
        private final float pitch;
        private final float roll;
        private final double throttle;

        private Command(float yaw, float pitch, float roll, double throttle) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.throttle = throttle;
        }

        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public float getRoll() { return roll; }
        public double getThrottle() { return throttle; }
    }
}
