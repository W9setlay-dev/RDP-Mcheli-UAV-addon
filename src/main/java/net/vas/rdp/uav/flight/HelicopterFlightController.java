package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.mission.ControlOwner;

/**
 * MCheli helicopter/rotorcraft attitude controller. Horizontal position error
 * maps into bounded pitch/roll; altitude and vertical rate control collective
 * throttle independently.
 */
public final class HelicopterFlightController implements AirframeController {
    private static final long MAX_STATE_AGE_TICKS = 2L;
    private final UavVehicleProfile profile;

    public HelicopterFlightController(UavVehicleProfile profile) {
        if (profile == null) throw new IllegalArgumentException("Profile is required");
        this.profile = profile;
    }

    @Override
    public NativeFlightControlCommand update(AircraftState state, double targetX,
                                             double targetY, double targetZ,
                                             long currentTick) {
        if (state == null || finite(targetX, targetY, targetZ) || currentTick < 0L) {
            throw new IllegalArgumentException("Invalid helicopter controller inputs");
        }
        if (currentTick < state.getSampleTick()
                || currentTick - state.getSampleTick() > MAX_STATE_AGE_TICKS
                || finite(state.getX(), state.getY(), state.getZ(), state.getYaw(),
                state.getPitch(), state.getRoll(), state.getHorizontalSpeed(),
                state.getVerticalSpeed())) {
            return NativeFlightControlCommand.unsupported("STALE_OR_INVALID_AIRCRAFT_STATE");
        }

        double dx = targetX - state.getX();
        double dy = targetY - state.getY();
        double dz = targetZ - state.getZ();
        double horizontalDistance = Math.hypot(dx, dz);
        float course = horizontalDistance < 0.5D
                ? normalize(state.getYaw())
                : normalize((float) Math.toDegrees(Math.atan2(-dx, dz)));
        float headingError = NativeControlMath.angleDelta(course, state.getYaw());
        float desiredYaw = normalize(state.getYaw()
                + NativeControlMath.clamp(headingError * 0.32F, -12.0F, 12.0F));

        double radians = Math.toRadians(desiredYaw);
        double forwardError = -dx * Math.sin(radians) + dz * Math.cos(radians);
        double lateralError = dx * Math.cos(radians) + dz * Math.sin(radians);
        double forwardSpeed = state.getVelocityX() * -Math.sin(radians)
                + state.getVelocityZ() * Math.cos(radians);
        double lateralSpeed = state.getVelocityX() * Math.cos(radians)
                + state.getVelocityZ() * Math.sin(radians);

        double forwardDemand = NativeControlMath.clamp(
                forwardError * 0.035D - forwardSpeed * 0.70D, -0.7D, 0.7D);
        double lateralDemand = NativeControlMath.clamp(
                lateralError * 0.035D - lateralSpeed * 0.70D, -0.7D, 0.7D);
        float desiredPitch = (float) -Math.toDegrees(Math.atan(forwardDemand));
        float desiredRoll = (float) Math.toDegrees(Math.atan(lateralDemand));
        if (state.getControlOwner() == ControlOwner.TERMINAL_GUIDANCE) {
            desiredPitch = NativeControlMath.clamp(desiredPitch, -35.0F, 45.0F);
        } else {
            desiredPitch = NativeControlMath.clamp(desiredPitch, -22.0F, 18.0F);
        }
        desiredRoll = NativeControlMath.clamp(desiredRoll, -20.0F, 20.0F);

        double maxClimb = Math.max(0.05D, profile.getMaximumClimbRate());
        double maxDescent = Math.max(0.05D, profile.getMaximumDescentRate());
        double requestedVerticalSpeed = NativeControlMath.clamp(dy * 0.08D,
                -maxDescent, maxClimb);
        double desiredVerticalSpeed = NativeControlMath.clamp(
                requestedVerticalSpeed - state.getVerticalSpeed() * 0.75D,
                -maxDescent, maxClimb);
        double speedError = profile.getCruiseSpeed() - state.getHorizontalSpeed();
        double throttle = NativeControlMath.clamp(0.56D + speedError * 0.16D
                + desiredVerticalSpeed * 0.16D, 0.25D, 0.88D);
        if (Math.abs(dy) <= Math.max(0.5D, profile.getAltitudeTolerance())
                && Math.abs(state.getVerticalSpeed()) < 0.04D) {
            throttle = NativeControlMath.clamp(throttle, 0.35D, 0.68D);
        }
        return NativeFlightControlCommand.setpoints(desiredYaw, desiredPitch,
                desiredRoll, throttle, course, headingError, desiredVerticalSpeed);
    }

    public UavVehicleProfile getProfile() { return profile; }

    private static float normalize(float angle) {
        while (angle >= 180.0F) angle -= 360.0F;
        while (angle < -180.0F) angle += 360.0F;
        return angle;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return true;
        return false;
    }
}
