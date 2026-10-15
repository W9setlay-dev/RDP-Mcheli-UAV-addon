package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.profile.UavVehicleProfile;

/**
 * Pure guidance geometry used before a waypoint is handed to native WingMan.
 *
 * <p>This class does not mutate an aircraft and does not claim to be a
 * replacement for MCheliCE physics. It converts observed state and a target
 * track into a bounded, profile-aware waypoint.</p>
 *
 * @deprecated Use {@link net.vas.rdp.uav.path.PathPlanner} for newly planned
 * route trajectories; this compatibility planner remains available for legacy callers.
 */
@Deprecated
public final class FlightGuidancePlanner {
    private FlightGuidancePlanner() { }

    public static Plan plan(AircraftState aircraft, double targetX, double targetY,
                            double targetZ, double targetVelocityX,
                            double targetVelocityY, double targetVelocityZ,
                            double targetRadius, UavVehicleProfile profile,
                            boolean terminal) {
        if (aircraft == null || profile == null
                || !finite(targetX, targetY, targetZ, targetVelocityX,
                targetVelocityY, targetVelocityZ, targetRadius)
                || targetRadius < 0.0D) {
            throw new IllegalArgumentException("Invalid guidance inputs");
        }

        double dx = targetX - aircraft.getX();
        double dy = targetY - aircraft.getY();
        double dz = targetZ - aircraft.getZ();
        double distance = length(dx, dy, dz);
        double speed = length(aircraft.getVelocityX(), aircraft.getVelocityY(),
                aircraft.getVelocityZ());
        double minimumSpeed = profile.getOneWaySpeed() > 0.0D
                ? profile.getOneWaySpeed() : 0.5D;
        double leadTicks = distance / Math.max(minimumSpeed, speed);
        leadTicks = clamp(leadTicks, terminal ? 0.0D : 2.0D, terminal ? 4.0D : 20.0D);

        double predictedX = targetX + targetVelocityX * leadTicks;
        double predictedY = targetY + targetVelocityY * leadTicks;
        double predictedZ = targetZ + targetVelocityZ * leadTicks;
        double desiredX = predictedX - aircraft.getX();
        double desiredY = predictedY - aircraft.getY();
        double desiredZ = predictedZ - aircraft.getZ();
        double desiredLength = length(desiredX, desiredY, desiredZ);
        if (desiredLength < 0.001D) {
            return new Plan(targetX, targetY, targetZ, leadTicks, true, "AT_TARGET");
        }

        if (terminal) {
            TerminalApproachPlanner.Plan approach = TerminalApproachPlanner.plan(
                    aircraft.getX(), aircraft.getY(), aircraft.getZ(),
                    predictedX, predictedY, predictedZ, 0.0D,
                    profile.getTurnRadius(), targetRadius, 24.0D);
            double approachDistance = length(approach.getApproachX() - aircraft.getX(),
                    approach.getApproachY() - aircraft.getY(),
                    approach.getApproachZ() - aircraft.getZ());
            if (approach.isDescending() && distance > Math.max(12.0D, approach.getRun() * 0.5D)) {
                return new Plan(approach.getApproachX(), approach.getApproachY(),
                        approach.getApproachZ(), leadTicks, true,
                        "TERMINAL_APPROACH_" + Math.round(approach.getAngleDegrees()));
            }
        }

        double lookahead = clamp(Math.max(targetRadius * 2.0D,
                Math.max(12.0D, speed * (terminal ? 3.0D : 6.0D))),
                terminal ? 8.0D : 16.0D, terminal ? 48.0D : 96.0D);
        double waypointX = aircraft.getX() + desiredX / desiredLength * lookahead;
        double waypointZ = aircraft.getZ() + desiredZ / desiredLength * lookahead;
        double waypointY = targetY;

        boolean feasible = terminal || distance <= profile.getDetectionRange()
                || profile.supportsLongRangeLock();
        return new Plan(waypointX, waypointY, waypointZ, leadTicks, feasible,
                feasible ? "OK" : "TARGET_OUTSIDE_PROFILE_ENVELOPE");
    }

    private static double length(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    public static final class Plan {
        private final double x;
        private final double y;
        private final double z;
        private final double leadTicks;
        private final boolean feasible;
        private final String reason;

        private Plan(double x, double y, double z, double leadTicks,
                     boolean feasible, String reason) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.leadTicks = leadTicks;
            this.feasible = feasible;
            this.reason = reason;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public double getLeadTicks() { return leadTicks; }
        public boolean isFeasible() { return feasible; }
        public String getReason() { return reason; }
    }
}
