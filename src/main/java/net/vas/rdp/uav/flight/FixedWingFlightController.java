package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.mission.ControlOwner;

/**
 * Fixed-wing path-following law using measured ground track, speed and climb
 * rate to shape coordinated, rate-limited MCheli attitude setpoints.
 *
 * <p>The profile values are conservative addon defaults, not flight-test
 * measurements. They must be calibrated per aircraft in the runtime matrix.</p>
 */
public final class FixedWingFlightController implements AirframeController {
    private static final long MAX_STATE_AGE_TICKS = 2L;
    private static final double ALTITUDE_RESPONSE_TICKS = 32.0D;
    private static final double VERTICAL_DAMPING = 0.55D;
    private final UavVehicleProfile profile;

    public FixedWingFlightController(UavVehicleProfile profile) {
        if (profile == null) throw new IllegalArgumentException("Profile is required");
        this.profile = profile;
    }

    @Override
    public NativeFlightControlCommand update(AircraftState state, double targetX,
                                             double targetY, double targetZ,
                                             long currentTick) {
        return update(state, targetX, targetY, targetZ, currentTick, false);
    }

    @Override
    public NativeFlightControlCommand update(AircraftState state, double targetX,
                                             double targetY, double targetZ,
                                             long currentTick, boolean absoluteAltitude) {
        if (state == null || finite(targetX, targetY, targetZ) || currentTick < 0L) {
            throw new IllegalArgumentException("Invalid fixed-wing controller inputs");
        }
        if (currentTick < state.getSampleTick()
                || currentTick - state.getSampleTick() > MAX_STATE_AGE_TICKS
                || finite(state.getX(), state.getY(), state.getZ(), state.getYaw(),
                state.getPitch(), state.getRoll(), state.getHorizontalSpeed(),
                state.getVerticalSpeed())) {
            return NativeFlightControlCommand.unsupported("STALE_OR_INVALID_AIRCRAFT_STATE");
        }

        double safeTargetY = absoluteAltitude ? targetY
                : WorkingAltitudePolicy.clampToProfile(profile, targetY);
        double dx = targetX - state.getX();
        double dy = safeTargetY - state.getY();
        double dz = targetZ - state.getZ();
        double horizontalDistance = Math.hypot(dx, dz);
        float course = horizontalDistance < 0.5D
                ? normalize(state.getYaw())
                : normalize((float) Math.toDegrees(Math.atan2(-dx, dz)));
        float headingError = NativeControlMath.angleDelta(course, state.getYaw());
        float track = Double.isFinite(state.getGroundTrackYaw())
                ? (float) state.getGroundTrackYaw() : state.getYaw();
        float trackError = NativeControlMath.angleDelta(course, track);

        double speed = Math.max(0.0D, state.getHorizontalSpeed());
        double turnRadius = Math.max(8.0D, profile.getTurnRadius());
        double nominalYawRate = Math.toDegrees(Math.min(0.12D,
                Math.max(0.01D, Math.max(speed, profile.getStallSpeed()) / turnRadius)));
        float yawLead = NativeControlMath.clamp(
                headingError * 0.35F + trackError * 0.12F, -18.0F, 18.0F);
        float desiredYaw = normalize(state.getYaw() + yawLead);
        float desiredRoll = NativeControlMath.clamp(
                headingError * 0.58F + trackError * 0.18F,
                (float) -profile.getMaximumBankDegrees(),
                (float) profile.getMaximumBankDegrees());

        double maxClimb = Math.max(0.05D, profile.getMaximumClimbRate());
        double maxDescent = Math.max(0.05D, profile.getMaximumDescentRate());
        double requestedVerticalSpeed = NativeControlMath.clamp(
                dy / ALTITUDE_RESPONSE_TICKS, -maxDescent, maxClimb);
        double desiredVerticalSpeed = NativeControlMath.clamp(
                requestedVerticalSpeed - state.getVerticalSpeed() * VERTICAL_DAMPING,
                -maxDescent, maxClimb);
        double airspeedForPitch = Math.max(profile.getStallSpeed(), speed);
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(
                desiredVerticalSpeed, Math.max(0.2D, airspeedForPitch)));
        if (state.getControlOwner() == ControlOwner.TERMINAL_GUIDANCE) {
            // Follow the terminal route's actual vertical/horizontal slope.
            // This permits the planner's steep one-way dive geometry while
            // the adapter still rate-limits how quickly attitude changes.
            double pathAngle = Math.toDegrees(Math.atan2(-dy,
                    Math.max(2.0D, horizontalDistance)));
            desiredPitch = NativeControlMath.clamp((float) pathAngle, -25.0F, 60.0F);
        } else {
            desiredPitch = NativeControlMath.clamp(desiredPitch, -20.0F, 16.0F);
        }

        double speedError = profile.getCruiseSpeed() - speed;
        double throttle = NativeControlMath.clamp(0.66D + speedError * 0.22D
                + Math.min(0.16D, Math.abs(headingError) / 180.0D * 0.16D), 0.30D, 1.0D);
        if (speed < profile.getStallSpeed()) throttle = Math.max(throttle, 0.90D);
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
