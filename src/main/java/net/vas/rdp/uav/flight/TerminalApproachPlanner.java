package net.vas.rdp.uav.flight;

/**
 * Calculates a finite terminal leg instead of steering directly at the
 * target forever. The selected angle is constrained by the available
 * altitude and by bounded native WingMan waypoint distances.
 */
public final class TerminalApproachPlanner {
    private static final double[] SUPPORTED_ANGLES = {20.0D, 30.0D, 40.0D, 50.0D, 60.0D};
    private TerminalApproachPlanner() { }

    public static Plan plan(double aircraftX, double aircraftY, double aircraftZ,
                            double targetX, double targetY, double targetZ,
                            double preferredBearingDegrees) {
        return plan(aircraftX, aircraftY, aircraftZ, targetX, targetY, targetZ,
                preferredBearingDegrees, 12.0D, 0.0D, 24.0D);
    }

    public static Plan plan(double aircraftX, double aircraftY, double aircraftZ,
                            double targetX, double targetY, double targetZ,
                            double preferredBearingDegrees, double turnRadius,
                            double targetRadius, double minimumRun) {
        requireFinite(aircraftX, aircraftY, aircraftZ, targetX, targetY, targetZ);
        requireFinite(turnRadius, targetRadius, minimumRun);
        if (turnRadius <= 0.0D || targetRadius < 0.0D || minimumRun <= 0.0D) {
            throw new IllegalArgumentException("Invalid terminal limits");
        }
        double dx = aircraftX - targetX;
        double dz = aircraftZ - targetZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.001D) {
            double bearing = Math.toRadians(preferredBearingDegrees);
            dx = Math.cos(bearing);
            dz = Math.sin(bearing);
            horizontal = 1.0D;
        }
        double altitude = aircraftY - targetY;
        double angle = chooseAngle(Math.abs(altitude), horizontal);
        double geometryRun = altitude > 0.0D
                ? altitude / Math.tan(Math.toRadians(angle))
                : horizontal * 0.35D;
        double run = clamp(Math.max(geometryRun, turnRadius + targetRadius + minimumRun),
                minimumRun, altitude > 0.0D ? 192.0D : 96.0D);
        double approachX = targetX + dx / horizontal * run;
        double approachZ = targetZ + dz / horizontal * run;
        double approachY = altitude > 0.0D
                ? targetY + Math.tan(Math.toRadians(angle)) * run
                : targetY + Math.min(24.0D, Math.max(8.0D, -altitude * 0.5D));
        return new Plan(approachX, approachY, approachZ, targetX, targetY, targetZ,
                angle, run, altitude > 0.0D);
    }

    public static double chooseAngle(double altitude, double horizontalDistance) {
        if (!Double.isFinite(altitude) || !Double.isFinite(horizontalDistance)
                || altitude < 0.0D || horizontalDistance < 0.0D) {
            throw new IllegalArgumentException("Invalid terminal geometry");
        }
        if (altitude < 1.0D) return 20.0D;
        double ideal = Math.toDegrees(Math.atan2(altitude,
                Math.max(24.0D, horizontalDistance)));
        double selected = SUPPORTED_ANGLES[0];
        double error = Double.POSITIVE_INFINITY;
        for (double candidate : SUPPORTED_ANGLES) {
            double candidateError = Math.abs(candidate - ideal);
            if (candidateError < error) {
                selected = candidate;
                error = candidateError;
            }
        }
        return selected;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void requireFinite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Terminal geometry must be finite");
        }
    }

    public static final class Plan {
        private final double approachX, approachY, approachZ;
        private final double targetX, targetY, targetZ;
        private final double angle, run;
        private final boolean descending;

        private Plan(double approachX, double approachY, double approachZ,
                     double targetX, double targetY, double targetZ,
                     double angle, double run, boolean descending) {
            this.approachX = approachX;
            this.approachY = approachY;
            this.approachZ = approachZ;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.angle = angle;
            this.run = run;
            this.descending = descending;
        }

        public double getApproachX() { return approachX; }
        public double getApproachY() { return approachY; }
        public double getApproachZ() { return approachZ; }
        public double getTargetX() { return targetX; }
        public double getTargetY() { return targetY; }
        public double getTargetZ() { return targetZ; }
        public double getAngleDegrees() { return angle; }
        public double getRun() { return run; }
        public boolean isDescending() { return descending; }
    }
}
