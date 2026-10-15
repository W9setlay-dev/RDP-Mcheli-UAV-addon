package net.vas.rdp.uav.path;

import net.vas.rdp.uav.deconfliction.TrajectorySample;

import java.util.ArrayList;
import java.util.List;

/** Server-side tangent entry/exit and bounded orbit sampling. */
public final class OrbitPlanner {
    private static final int ORBIT_SAMPLES = 64;

    /** Selects the circle tangent requiring the smallest heading change. */
    public OrbitEntry computeEntry(double currentX, double currentZ,
                                   double currentHeading, double centerX,
                                   double centerZ, double radius) {
        validate(currentX, currentZ, currentHeading, centerX, centerZ, radius);
        double dx = currentX - centerX;
        double dz = currentZ - centerZ;
        double distance = Math.hypot(dx, dz);
        if (distance <= radius) {
            throw new IllegalArgumentException("Aircraft must be outside the orbit radius");
        }
        double centerBearing = Math.atan2(dz, dx);
        double offset = Math.acos(radius / distance);
        OrbitEntry best = null;
        double bestChange = Double.POSITIVE_INFINITY;
        for (OrbitEntry.OrbitDirection direction : OrbitEntry.OrbitDirection.values()) {
            for (int sign : new int[] { -1, 1 }) {
                double tangentAngle = centerBearing + sign * offset;
                double x = centerX + radius * Math.cos(tangentAngle);
                double z = centerZ + radius * Math.sin(tangentAngle);
                double mathHeading = tangentAngle
                        + (direction == OrbitEntry.OrbitDirection.LEFT ? Math.PI / 2.0D
                        : -Math.PI / 2.0D);
                double yaw = normalizeYaw(90.0D - Math.toDegrees(mathHeading));
                double change = Math.abs(angleDelta(yaw, currentHeading));
                if (change < bestChange) {
                    bestChange = change;
                    best = new OrbitEntry(x, z, yaw, direction);
                }
            }
        }
        return best;
    }

    /** Generates one bounded loop or racetrack at a fixed altitude. */
    public List<TrajectorySample> generateOrbit(double centerX, double centerZ,
                                                double radius, double altitude,
                                                OrbitPrimitive primitive,
                                                OrbitEntry entry) {
        validate(centerX, centerZ, radius, altitude);
        if (primitive == null || entry == null || radius <= 0.0D) {
            throw new IllegalArgumentException("Orbit primitive, entry, and positive radius are required");
        }
        List<TrajectorySample> samples = new ArrayList<>();
        double startAngle = Math.atan2(entry.getEntryTangentZ() - centerZ,
                entry.getEntryTangentX() - centerX);
        double direction = entry.getOrbitDirection() == OrbitEntry.OrbitDirection.LEFT
                ? 1.0D : -1.0D;
        for (int index = 0; index <= ORBIT_SAMPLES; index++) {
            double t = TWO_PI * index / ORBIT_SAMPLES;
            double x;
            double z;
            double heading;
            if (primitive == OrbitPrimitive.ORBIT) {
                double angle = startAngle + direction * t;
                x = centerX + radius * Math.cos(angle);
                z = centerZ + radius * Math.sin(angle);
                heading = angle + direction * Math.PI / 2.0D;
            } else if (primitive == OrbitPrimitive.FIGURE_EIGHT) {
                double u = startAngle + direction * t;
                double denominator = 1.0D + Math.sin(u) * Math.sin(u);
                x = centerX + radius * Math.sqrt(2.0D) * Math.cos(u) / denominator;
                z = centerZ + radius * Math.sqrt(2.0D) * Math.cos(u)
                        * Math.sin(u) / denominator;
                double sine = Math.sin(u);
                double cosine = Math.cos(u);
                double derivativeX = Math.sqrt(2.0D) * radius
                        * (-sine * denominator - 2.0D * cosine * sine * cosine)
                        / (denominator * denominator);
                double derivativeZ = Math.sqrt(2.0D) * radius
                        * ((cosine * cosine - sine * sine) * denominator
                        - 2.0D * cosine * sine * sine * cosine)
                        / (denominator * denominator);
                heading = Math.atan2(direction * derivativeZ, direction * derivativeX);
            } else {
                double phase = t;
                if (phase <= Math.PI / 2.0D) {
                    x = centerX + radius * Math.cos(startAngle) + 4.0D * radius * phase / Math.PI;
                    z = centerZ + radius * Math.sin(startAngle);
                    heading = startAngle;
                } else if (phase <= Math.PI) {
                    double arc = phase - Math.PI / 2.0D;
                    double angle = startAngle + direction * arc;
                    x = centerX + radius * Math.cos(angle) + 2.0D * radius;
                    z = centerZ + radius * Math.sin(angle);
                    heading = angle + direction * Math.PI / 2.0D;
                } else if (phase <= 3.0D * Math.PI / 2.0D) {
                    double leg = phase - Math.PI;
                    x = centerX + radius * Math.cos(startAngle) + 2.0D * radius
                            - 4.0D * radius * leg / Math.PI;
                    z = centerZ + radius * Math.sin(startAngle);
                    heading = startAngle + Math.PI;
                } else {
                    double arc = phase - 3.0D * Math.PI / 2.0D;
                    double angle = startAngle + Math.PI + direction * arc;
                    x = centerX + radius * Math.cos(angle);
                    z = centerZ + radius * Math.sin(angle);
                    heading = angle + direction * Math.PI / 2.0D;
                }
            }
            samples.add(new TrajectorySample(x, altitude, z,
                    normalizeYaw((float) (90.0D - Math.toDegrees(heading))),
                    0.0F, 0.0F,
                    index * (long) PathConfig.orbitSampleIntervalTicks, 1.0D));
        }
        return java.util.Collections.unmodifiableList(samples);
    }

    /** Generates a segmented circular loop whose direction follows current motion. */
    public List<Waypoint> planForwardLoop(double centerX, double centerZ,
                                          double altitude, double radius,
                                          double currentX, double currentZ,
                                          double currentHeading,
                                          double motionX, double motionZ,
                                          int waypointCount) {
        validate(centerX, centerZ, altitude, radius, currentX, currentZ,
                currentHeading, motionX, motionZ);
        if (radius <= 0.0D || waypointCount < 8 || waypointCount > 64) {
            throw new IllegalArgumentException("Forward loop requires positive radius and 8 to 64 waypoints");
        }

        double dx = currentX - centerX;
        double dz = currentZ - centerZ;
        double distance = Math.hypot(dx, dz);
        double approachHeading = Math.hypot(motionX, motionZ) >= 0.05D
                ? Math.toDegrees(Math.atan2(-motionX, motionZ))
                : currentHeading;
        double startAngle;
        OrbitEntry.OrbitDirection orbitDirection;
        if (distance > radius) {
            OrbitEntry entry = computeEntry(currentX, currentZ, approachHeading,
                    centerX, centerZ, radius);
            startAngle = Math.atan2(entry.getEntryTangentZ() - centerZ,
                    entry.getEntryTangentX() - centerX);
            orbitDirection = entry.getOrbitDirection();
        } else {
            if (distance < 2.0D) {
                double heading = Math.toRadians(approachHeading);
                dx = -Math.sin(heading);
                dz = Math.cos(heading);
            }
            startAngle = Math.atan2(dz, dx);
            double tangentX = -Math.sin(startAngle);
            double tangentZ = Math.cos(startAngle);
            double tangentProjection = motionX * tangentX + motionZ * tangentZ;
            orbitDirection = Math.hypot(motionX, motionZ) < 0.05D
                    || tangentProjection >= 0.0D
                    ? OrbitEntry.OrbitDirection.LEFT
                    : OrbitEntry.OrbitDirection.RIGHT;
        }

        double direction = orbitDirection == OrbitEntry.OrbitDirection.LEFT ? 1.0D : -1.0D;
        List<Waypoint> waypoints = new ArrayList<>(waypointCount);
        for (int index = 1; index <= waypointCount; index++) {
            double angle = startAngle + direction * TWO_PI * index / waypointCount;
            double heading = angle + direction * Math.PI / 2.0D;
            waypoints.add(new Waypoint(centerX + radius * Math.cos(angle),
                    altitude, centerZ + radius * Math.sin(angle),
                    normalizeYaw(90.0D - Math.toDegrees(heading))));
        }
        return java.util.Collections.unmodifiableList(waypoints);
    }

    /** Computes the closest tangent angle compatible with the desired exit heading. */
    public OrbitEntry computeExit(double currentOrbitAngle, double desiredExitHeading,
                                  double centerX, double centerZ, double radius) {
        validate(currentOrbitAngle, desiredExitHeading, centerX, centerZ, radius);
        if (radius <= 0.0D) throw new IllegalArgumentException("Orbit radius must be positive");
        double mathHeading = Math.toRadians(90.0D - desiredExitHeading);
        OrbitEntry best = null;
        double bestChange = Double.POSITIVE_INFINITY;
        for (OrbitEntry.OrbitDirection direction : OrbitEntry.OrbitDirection.values()) {
            double tangentAngle = mathHeading
                    - (direction == OrbitEntry.OrbitDirection.LEFT ? Math.PI / 2.0D
                    : -Math.PI / 2.0D);
            double change = Math.abs(mod2pi(tangentAngle - currentOrbitAngle));
            change = Math.min(change, TWO_PI - change);
            if (change < bestChange) {
                bestChange = change;
                best = new OrbitEntry(centerX + radius * Math.cos(tangentAngle),
                        centerZ + radius * Math.sin(tangentAngle),
                        desiredExitHeading, direction);
            }
        }
        return best;
    }

    private static double angleDelta(double target, double current) {
        double value = (target - current) % 360.0D;
        if (value > 180.0D) value -= 360.0D;
        if (value < -180.0D) value += 360.0D;
        return value;
    }

    private static float normalizeYaw(double yaw) {
        float result = (float) (yaw % 360.0D);
        return result < 0.0F ? result + 360.0F : result;
    }

    private static double mod2pi(double value) {
        double result = value % TWO_PI;
        return result < 0.0D ? result + TWO_PI : result;
    }

    private static final double TWO_PI = Math.PI * 2.0D;

    private static void validate(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite orbit input");
        }
    }
}
