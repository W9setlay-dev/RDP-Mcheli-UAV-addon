package net.vas.rdp.uav.path;

import net.vas.rdp.uav.deconfliction.TrajectorySample;
import net.vas.rdp.uav.profile.UavVehicleProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server-side route coordinator separating smooth cruise paths from straight terminal paths. */
public final class PathPlanner {
    private static final double MINIMUM_TERMINAL_RUN = 48.0D;
    private static final double MAXIMUM_TERMINAL_RUN = 192.0D;
    private final DubinsPlanner dubinsPlanner;

    /** Creates a route planner with the canonical Dubins implementation. */
    public PathPlanner() {
        this(new DubinsPlanner());
    }

    /** Creates a route planner with an injected pure geometry planner. */
    public PathPlanner(DubinsPlanner dubinsPlanner) {
        if (dubinsPlanner == null) throw new IllegalArgumentException("Dubins planner is required");
        this.dubinsPlanner = dubinsPlanner;
    }

    /** Plans each waypoint leg with profile-derived turn radius and cruise speed. */
    public List<TrajectorySample> planRoute(List<Waypoint> waypoints,
                                            UavVehicleProfile profile,
                                            double currentX, double currentZ,
                                            double currentHeading) {
        if (profile == null) throw new IllegalArgumentException("Vehicle profile is required");
        if (waypoints == null || waypoints.isEmpty()) return Collections.emptyList();
        return planRoute(waypoints, profile, currentX, waypoints.get(0).getY(),
                currentZ, currentHeading);
    }

    /** Plans a 3D waypoint sequence while smoothing only its horizontal geometry. */
    public List<TrajectorySample> planRoute(List<Waypoint> waypoints,
                                            UavVehicleProfile profile,
                                            double currentX, double currentY,
                                            double currentZ, double currentHeading) {
        if (waypoints == null || profile == null
                || !finite(currentX, currentY, currentZ, currentHeading)
                || waypoints.contains(null)) {
            throw new IllegalArgumentException("Invalid route-planning inputs");
        }
        if (waypoints.isEmpty()) return Collections.emptyList();
        List<TrajectorySample> result = new ArrayList<>();
        double x = currentX;
        double y = currentY;
        double z = currentZ;
        double heading = currentHeading;
        long tickOffset = 0L;
        double speed = Math.max(0.1D, profile.getCruiseSpeed());
        double radius = turnRadius(profile);
        for (Waypoint waypoint : waypoints) {
            DubinsPath leg = dubinsPlanner.plan(x, y, z, heading,
                    waypoint.getX(), waypoint.getY(), waypoint.getZ(),
                    waypoint.getHeading(), radius, speed);
            for (int index = 0; index < leg.getSamples().size(); index++) {
                if (!result.isEmpty() && index == 0) continue;
                TrajectorySample sample = leg.getSamples().get(index);
                long shiftedTick = tickOffset + sample.getTickOffset();
                if (!result.isEmpty()
                        && shiftedTick <= result.get(result.size() - 1).getTickOffset()) continue;
                result.add(new TrajectorySample(sample.getX(), sample.getY(), sample.getZ(),
                        sample.getYaw(), sample.getPitch(), sample.getRoll(),
                        shiftedTick, sample.getConfidence()));
            }
            tickOffset += Math.max(1L, Math.round(leg.getTotalLength() / speed));
            x = waypoint.getX();
            y = waypoint.getY();
            z = waypoint.getZ();
            heading = waypoint.getHeading();
        }
        return Collections.unmodifiableList(result);
    }

    /** Plans one orbit using the profile turn radius and configured radius multiplier. */
    public List<TrajectorySample> planOrbit(double centerX, double centerZ,
                                            double altitude, OrbitPrimitive primitive,
                                            OrbitEntry entry,
                                            UavVehicleProfile profile) {
        if (profile == null) throw new IllegalArgumentException("Vehicle profile is required");
        double radius = turnRadius(profile) * PathConfig.orbitRadiusMultiplier;
        return new OrbitPlanner().generateOrbit(centerX, centerZ, radius,
                altitude, primitive, entry);
    }

    /** Builds a straight terminal segment; it performs no Dubins smoothing or terrain sampling. */
    public List<TrajectorySample> planTerminalApproach(double targetX, double targetY,
                                                       double targetZ, double diveAngle,
                                                       UavVehicleProfile profile) {
        if (profile == null || !finite(targetX, targetY, targetZ, diveAngle)) {
            throw new IllegalArgumentException("Invalid terminal approach inputs");
        }
        double angle = Math.max(-25.0D, Math.min(60.0D, diveAngle));
        double minimumRun = PathConfig.terminalMinimumRun > 0.0D
                ? PathConfig.terminalMinimumRun : MINIMUM_TERMINAL_RUN;
        double maximumRun = Math.max(minimumRun,
                PathConfig.terminalMaximumRun > 0.0D
                        ? PathConfig.terminalMaximumRun : MAXIMUM_TERMINAL_RUN);
        double run = Math.max(minimumRun,
                Math.min(maximumRun, turnRadius(profile) * 4.0D));
        double radians = Math.toRadians(angle);
        double startX = targetX;
        double startY = targetY + run * Math.sin(radians);
        double startZ = targetZ - run * Math.cos(radians);
        double speed = Math.max(0.1D, profile.getCruiseSpeed());
        double length = Math.hypot(run, startY - targetY);
        long ticks = Math.max(1L, (long) Math.ceil(length / speed));
        List<TrajectorySample> result = new ArrayList<>();
        for (long tick = 0L; tick <= ticks; tick += 5L) {
            double fraction = Math.min(1.0D, tick / (double) ticks);
            result.add(terminalSample(startX, startY, startZ, targetX, targetY,
                    targetZ, angle, tick, fraction));
        }
        if (result.get(result.size() - 1).getTickOffset() < ticks) {
            result.add(terminalSample(startX, startY, startZ, targetX, targetY,
                    targetZ, angle, ticks, 1.0D));
        }
        return Collections.unmodifiableList(result);
    }

    private static TrajectorySample terminalSample(double startX, double startY,
                                                   double startZ, double targetX,
                                                   double targetY, double targetZ,
                                                   double angle, long tick,
                                                   double fraction) {
        double x = startX + (targetX - startX) * fraction;
        double y = startY + (targetY - startY) * fraction;
        double z = startZ + (targetZ - startZ) * fraction;
        return new TrajectorySample(x, y, z, 0.0F, (float) angle,
                0.0F, tick, 1.0D);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static double turnRadius(UavVehicleProfile profile) {
        double override = net.vas.rdp.uav.formation.FormationConfig.minimumTurnRadiusOverride;
        return Math.max(1.0D, override > 0.0D ? override : profile.getTurnRadius());
    }
}
