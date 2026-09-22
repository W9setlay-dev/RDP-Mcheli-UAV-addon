package net.vas.rdp.uav.flight;

/**
 * Lightweight pursuit solution for moving entity targets. It produces a
 * bounded desired flight direction; the native McHeli/WingMan controller
 * remains responsible for aircraft movement and attitude.
 */
public final class ContinuousTargetTracker {
    public static final class Parameters {
        private final double predictionTicks;
        private final double smoothing;
        private final double maximumDirectionChange;
        private final double deadband;
        private final double lookahead;
        private final int updateInterval;

        public Parameters(double predictionTicks, double smoothing,
                          double maximumDirectionChange, double deadband,
                          double lookahead, int updateInterval) {
            if (predictionTicks < 0.0D || smoothing <= 0.0D || smoothing > 1.0D
                    || maximumDirectionChange <= 0.0D || deadband < 0.0D
                    || lookahead <= 0.0D || updateInterval < 1) {
                throw new IllegalArgumentException("Invalid target tracking parameters");
            }
            this.predictionTicks = predictionTicks;
            this.smoothing = smoothing;
            this.maximumDirectionChange = maximumDirectionChange;
            this.deadband = deadband;
            this.lookahead = lookahead;
            this.updateInterval = updateInterval;
        }

        public double getPredictionTicks() { return predictionTicks; }
        public double getSmoothing() { return smoothing; }
        public double getMaximumDirectionChange() { return maximumDirectionChange; }
        public double getDeadband() { return deadband; }
        public double getLookahead() { return lookahead; }
        public int getUpdateInterval() { return updateInterval; }
    }

    public static final class Solution {
        private final double x;
        private final double y;
        private final double z;
        private final boolean updated;

        private Solution(double x, double y, double z, boolean updated) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.updated = updated;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public boolean isUpdated() { return updated; }
    }

    private final Parameters parameters;
    private boolean initialized;
    private long lastUpdate = Long.MIN_VALUE;
    private double directionX;
    private double directionY;
    private double directionZ;
    private double lastX;
    private double lastY;
    private double lastZ;

    public ContinuousTargetTracker(Parameters parameters) {
        if (parameters == null) throw new IllegalArgumentException("Parameters are required");
        this.parameters = parameters;
    }

    public Solution update(long tick, double aircraftX, double aircraftY, double aircraftZ,
                           double targetX, double targetY, double targetZ,
                           double targetVelocityX, double targetVelocityY,
                           double targetVelocityZ) {
        if (initialized && tick - lastUpdate < parameters.getUpdateInterval()) {
            return new Solution(lastX, lastY, lastZ, false);
        }

        double predictedX = targetX + targetVelocityX * parameters.getPredictionTicks();
        double predictedY = targetY + targetVelocityY * parameters.getPredictionTicks();
        double predictedZ = targetZ + targetVelocityZ * parameters.getPredictionTicks();
        double dx = predictedX - aircraftX;
        double dy = predictedY - aircraftY;
        double dz = predictedZ - aircraftZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= 0.001D) {
            lastUpdate = tick;
            lastX = targetX;
            lastY = targetY;
            lastZ = targetZ;
            return new Solution(lastX, lastY, lastZ, false);
        }
        if (initialized && distance <= parameters.getDeadband()) {
            lastUpdate = tick;
            return new Solution(lastX, lastY, lastZ, false);
        }

        double desiredX = dx / distance;
        double desiredY = dy / distance;
        double desiredZ = dz / distance;
        if (!initialized) {
            directionX = desiredX;
            directionY = desiredY;
            directionZ = desiredZ;
            initialized = true;
        } else {
            double dot = directionX * desiredX + directionY * desiredY + directionZ * desiredZ;
            double change = Math.max(0.0D, Math.min(1.0D,
                    parameters.getMaximumDirectionChange() / Math.max(0.001D, 1.0D - dot)));
            double alpha = Math.min(parameters.getSmoothing(), change);
            directionX += (desiredX - directionX) * alpha;
            directionY += (desiredY - directionY) * alpha;
            directionZ += (desiredZ - directionZ) * alpha;
            double length = Math.sqrt(directionX * directionX + directionY * directionY
                    + directionZ * directionZ);
            if (length > 0.001D) {
                directionX /= length;
                directionY /= length;
                directionZ /= length;
            }
        }

        lastX = aircraftX + directionX * parameters.getLookahead();
        lastY = aircraftY + directionY * parameters.getLookahead();
        lastZ = aircraftZ + directionZ * parameters.getLookahead();
        lastUpdate = tick;
        return new Solution(lastX, lastY, lastZ, true);
    }
}
