package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.profile.UavVehicleProfile;

/** Small deterministic helpers shared by the native attitude adapters. */
public final class NativeControlMath {
    private static final double OPERATOR_DEADBAND = 0.15D;
    private static final double OPERATOR_DEFAULT_MAX_DELTA = 1.0D;

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

    /**
     * Blends AI guidance with operator assistance on a single axis without
     * disabling the base AI controller. A zero or deadband operator input is
     * treated as no override; the final command stays on the AI baseline.
     */
    public static float blendAxis(float aiValue, float operatorDelta, float deadband,
                                  float maximumOperatorDelta) {
        return blendAxis(aiValue, operatorDelta, deadband, maximumOperatorDelta, 1.0F);
    }

    public static float blendAxis(float aiValue, float operatorDelta, float deadband,
                                  float maximumOperatorDelta, float decayFactor) {
        float effectiveDelta = decayToZero(operatorDelta, decayFactor);
        float clampedDelta = Math.abs(effectiveDelta) <= Math.abs(deadband)
                ? 0.0F
                : clamp(effectiveDelta, -Math.abs(maximumOperatorDelta), Math.abs(maximumOperatorDelta));
        return aiValue + clampedDelta;
    }

    /**
     * Blends AI guidance with operator assistance on a single axis, preserving
     * the same semantics for double precision values used by the flight command.
     */
    public static double blendAxis(double aiValue, double operatorDelta, double deadband,
                                   double maximumOperatorDelta) {
        return blendAxis(aiValue, operatorDelta, deadband, maximumOperatorDelta, 1.0D);
    }

    public static double blendAxis(double aiValue, double operatorDelta, double deadband,
                                   double maximumOperatorDelta, double decayFactor) {
        double effectiveDelta = decayToZero(operatorDelta, decayFactor);
        double clampedDelta = Math.abs(effectiveDelta) <= Math.abs(deadband)
                ? 0.0D
                : clamp(effectiveDelta, -Math.abs(maximumOperatorDelta), Math.abs(maximumOperatorDelta));
        return aiValue + clampedDelta;
    }

    /**
     * Smoothly decays a transient operator offset to zero instead of instantly
     * dropping the manual contribution. This prevents discontinuous motion when
     * operator input returns to neutral while AI guidance remains active.
     */
    public static float decayToZero(float value, float decayFactor) {
        if (!Float.isFinite(value) || !Float.isFinite(decayFactor)) {
            throw new IllegalArgumentException("Invalid decay parameters");
        }
        float normalized = clamp(decayFactor, 0.0F, 1.0F);
        float result = value * normalized;
        return Math.abs(result) < 0.0001F ? 0.0F : result;
    }

    public static double decayToZero(double value, double decayFactor) {
        if (!Double.isFinite(value) || !Double.isFinite(decayFactor)) {
            throw new IllegalArgumentException("Invalid decay parameters");
        }
        double normalized = clamp(decayFactor, 0.0D, 1.0D);
        double result = value * normalized;
        return Math.abs(result) < 0.0001D ? 0.0D : result;
    }

    public static float slewLimit(float current, float target, float maximumStep) {
        return approach(current, target, maximumStep);
    }

    public static double slewLimit(double current, double target, double maximumStep) {
        return approach(current, target, maximumStep);
    }

    /** Operator assistance is intentionally bounded and deadbanded. */
    public static double operatorDeadband() {
        return OPERATOR_DEADBAND;
    }

    /** Default maximum offset used when no explicit operator assist limit is supplied. */
    public static double defaultOperatorMaxDelta() {
        return OPERATOR_DEFAULT_MAX_DELTA;
    }

    public static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static float maximumRollDegrees(UavVehicleProfile profile) {
        return maximumRollDegrees(profile, profile != null && profile.isHelicopterProfile());
    }

    public static float maximumRollDegrees(UavVehicleProfile profile,
                                          boolean nativeHelicopterAirframe) {
        if (profile == null) throw new IllegalArgumentException("Profile is required");
        return nativeHelicopterAirframe || profile.isHelicopterProfile() ? 20.0F
                : (float) Math.max(0.0D, profile.getMaximumBankDegrees());
    }
}
