package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.profile.UavVehicleProfile;

/** Small deterministic helpers shared by the native attitude adapters. */
public final class NativeControlMath {
    private NativeControlMath() { }

    public static float angleDelta(float target, float current) {
        float delta = target - current;
        while (delta > 180.0F) delta -= 360.0F;
        while (delta < -180.0F) delta += 360.0F;
        return delta;
    }

    public static float approachAngle(float current, float target, float maximumStep) {
        return current + clamp(angleDelta(target, current), -maximumStep, maximumStep);
    }

    public static float approach(float current, float target, float maximumStep) {
        return current + clamp(target - current, -maximumStep, maximumStep);
    }

    public static double approach(double current, double target, double maximumStep) {
        return current + clamp(target - current, -maximumStep, maximumStep);
    }

    public static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static float maximumRollDegrees(UavVehicleProfile profile) {
        if (profile == null) throw new IllegalArgumentException("Profile is required");
        return profile.isHelicopterProfile() ? 20.0F
                : (float) Math.max(0.0D, profile.getMaximumBankDegrees());
    }
}
