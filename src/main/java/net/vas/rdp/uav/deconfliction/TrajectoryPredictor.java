package net.vas.rdp.uav.deconfliction;

import net.vas.rdp.uav.flight.AircraftState;
import net.vas.rdp.uav.profile.UavVehicleProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-side bounded constant-velocity predictor used for conflict screening. */
public final class TrajectoryPredictor {
    public static final long HORIZON_TICKS = 60L;
    public static final long SAMPLE_INTERVAL_TICKS = 5L;

    /** Predicts an observed aircraft state with profile-specific confidence decay. */
    public PredictedTrajectory predict(UUID aircraftId, AircraftState state,
                                       UavVehicleProfile profile, long startTick) {
        if (aircraftId == null || state == null || profile == null || startTick < 0L) {
            throw new IllegalArgumentException("Aircraft, state, profile, and tick are required");
        }
        return predict(aircraftId, state.getX(), state.getY(), state.getZ(),
                state.getVelocityX(), state.getVelocityY(), state.getVelocityZ(),
                state.getYaw(), state.getPitch(), state.getRoll(),
                profile.isHelicopterProfile(), startTick);
    }

    /** Projects finite measured position and velocity at five-tick intervals. */
    public PredictedTrajectory predict(UUID aircraftId, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ,
                                       float yaw, float pitch, float roll,
                                       boolean helicopter, long startTick) {
        if (aircraftId == null || !finite(x, y, z, velocityX, velocityY, velocityZ)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)
                || startTick < 0L) {
            throw new IllegalArgumentException("Invalid aircraft prediction inputs");
        }
        List<TrajectorySample> samples = new ArrayList<>();
        double endConfidence = helicopter ? 0.7D : 0.6D;
        for (long tick = 0L; tick <= HORIZON_TICKS; tick += SAMPLE_INTERVAL_TICKS) {
            double elapsed = tick;
            double confidence = 1.0D - (1.0D - endConfidence)
                    * tick / (double) HORIZON_TICKS;
            samples.add(new TrajectorySample(x + velocityX * elapsed,
                    y + velocityY * elapsed, z + velocityZ * elapsed,
                    yaw, pitch, roll, tick, confidence));
        }
        return new PredictedTrajectory(aircraftId, samples, startTick);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
