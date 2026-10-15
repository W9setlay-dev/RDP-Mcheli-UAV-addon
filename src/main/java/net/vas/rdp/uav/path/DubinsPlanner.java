package net.vas.rdp.uav.path;

import net.vas.rdp.uav.deconfliction.TrajectorySample;

import java.util.ArrayList;
import java.util.List;

/** Server-side horizontal Dubins planner; headings are Minecraft yaw degrees. */
public final class DubinsPlanner {
    private static final double TWO_PI = Math.PI * 2.0D;
    private static final long SAMPLE_INTERVAL_TICKS = 5L;

    /** Selects the shortest feasible path using one block per tick for timing. */
    public DubinsPath plan(double startX, double startY, double startZ, double startHeading,
                           double endX, double endY, double endZ, double endHeading,
                           double turnRadius) {
        return plan(startX, startY, startZ, startHeading, endX, endY, endZ,
                endHeading, turnRadius, 1.0D);
    }

    /** Selects and samples the shortest feasible path at the requested speed. */
    public DubinsPath plan(double startX, double startY, double startZ, double startHeading,
                           double endX, double endY, double endZ, double endHeading,
                           double turnRadius, double speedBlocksPerTick) {
        validate(startX, startY, startZ, startHeading, endX, endY, endZ,
                endHeading, turnRadius, speedBlocksPerTick);
        DubinsPath best = null;
        for (DubinsPathType type : DubinsPathType.values()) {
            DubinsPath candidate = planCandidate(startX, startY, startZ, startHeading,
                    endX, endY, endZ, endHeading, turnRadius, speedBlocksPerTick, type);
            if (candidate != null && (best == null
                    || candidate.getTotalLength() < best.getTotalLength())) best = candidate;
        }
        if (best == null) throw new IllegalStateException("No feasible Dubins path was generated");
        return best;
    }

    /** Generates one feasible primitive family, or returns null when its geometry is impossible. */
    public DubinsPath planCandidate(double startX, double startY, double startZ,
                                    double startHeading, double endX, double endY,
                                    double endZ, double endHeading, double turnRadius,
                                    double speedBlocksPerTick, DubinsPathType type) {
        validate(startX, startY, startZ, startHeading, endX, endY, endZ,
                endHeading, turnRadius, speedBlocksPerTick);
        if (type == null) throw new IllegalArgumentException("Dubins type is required");
        double dx = (endX - startX) / turnRadius;
        double dz = (endZ - startZ) / turnRadius;
        double distance = Math.hypot(dx, dz);
        double lineHeading = Math.atan2(dz, dx);
        double alpha = mod2pi(headingRadians(startHeading) - lineHeading);
        double beta = mod2pi(headingRadians(endHeading) - lineHeading);
        double[] parameters = parameters(type, alpha, beta, distance);
        if (parameters == null) return null;
        double length = (parameters[0] + parameters[1] + parameters[2]) * turnRadius;
        List<TrajectorySample> samples = samplePath(type, parameters, startX, startY,
                startZ, headingRadians(startHeading), endX, endY, endZ, endHeading,
                length, turnRadius, speedBlocksPerTick);
        return new DubinsPath(type, samples, length);
    }

    private static double[] parameters(DubinsPathType type, double alpha,
                                       double beta, double d) {
        double sinAlpha = Math.sin(alpha);
        double sinBeta = Math.sin(beta);
        double cosAlpha = Math.cos(alpha);
        double cosBeta = Math.cos(beta);
        double cosAlphaBeta = Math.cos(alpha - beta);
        double pSquared;
        double tmp;
        double t;
        double p;
        double q;
        switch (type) {
            case LSL:
                pSquared = 2.0D + d * d - 2.0D * cosAlphaBeta
                        + 2.0D * d * (sinAlpha - sinBeta);
                if (pSquared < 0.0D) return null;
                tmp = Math.atan2(cosBeta - cosAlpha,
                        d + sinAlpha - sinBeta);
                t = mod2pi(-alpha + tmp);
                p = Math.sqrt(pSquared);
                q = mod2pi(beta - tmp);
                break;
            case RSR:
                pSquared = 2.0D + d * d - 2.0D * cosAlphaBeta
                        + 2.0D * d * (-sinAlpha + sinBeta);
                if (pSquared < 0.0D) return null;
                tmp = Math.atan2(cosAlpha - cosBeta,
                        d - sinAlpha + sinBeta);
                t = mod2pi(alpha - tmp);
                p = Math.sqrt(pSquared);
                q = mod2pi(-beta + tmp);
                break;
            case LSR:
                pSquared = -2.0D + d * d + 2.0D * cosAlphaBeta
                        + 2.0D * d * (sinAlpha + sinBeta);
                if (pSquared < 0.0D) return null;
                p = Math.sqrt(pSquared);
                tmp = Math.atan2(-cosAlpha - cosBeta,
                        d + sinAlpha + sinBeta) - Math.atan2(-2.0D, p);
                t = mod2pi(-alpha + tmp);
                q = mod2pi(-beta + t);
                break;
            case RSL:
                pSquared = d * d - 2.0D + 2.0D * cosAlphaBeta
                        - 2.0D * d * (sinAlpha + sinBeta);
                if (pSquared < 0.0D) return null;
                p = Math.sqrt(pSquared);
                tmp = Math.atan2(cosAlpha + cosBeta,
                        d - sinAlpha - sinBeta) - Math.atan2(2.0D, p);
                t = mod2pi(alpha - tmp);
                q = mod2pi(beta - tmp);
                break;
            case RLR:
                tmp = (6.0D - d * d + 2.0D * cosAlphaBeta
                        + 2.0D * d * (sinAlpha - sinBeta)) / 8.0D;
                if (Math.abs(tmp) > 1.0D) return null;
                p = mod2pi(TWO_PI - Math.acos(tmp));
                t = mod2pi(alpha - Math.atan2(cosAlpha - cosBeta,
                        d - sinAlpha + sinBeta) + p / 2.0D);
                q = mod2pi(alpha - beta - t + p);
                break;
            case LRL:
                tmp = (6.0D - d * d + 2.0D * cosAlphaBeta
                        + 2.0D * d * (-sinAlpha + sinBeta)) / 8.0D;
                if (Math.abs(tmp) > 1.0D) return null;
                p = mod2pi(TWO_PI - Math.acos(tmp));
                t = mod2pi(-alpha - Math.atan2(cosAlpha - cosBeta,
                        d + sinAlpha - sinBeta) + p / 2.0D);
                q = mod2pi(beta - alpha - t + p);
                break;
            default:
                return null;
        }
        return new double[] { t, p, q };
    }

    private static List<TrajectorySample> samplePath(
            DubinsPathType type, double[] parameters, double startX, double startY,
            double startZ, double startAngle, double endX, double endY, double endZ,
            double endHeading,
            double length, double radius, double speed) {
        List<TrajectorySample> samples = new ArrayList<>();
        double step = speed * SAMPLE_INTERVAL_TICKS;
        long previousTick = -1L;
        for (double distance = 0.0D; distance < length; distance += step) {
            Pose pose = poseAt(type, parameters, startX, startZ, startAngle,
                    distance, radius);
            long tick = distance == 0.0D ? 0L
                    : Math.max(previousTick + 1L, Math.round(distance / speed));
            addSample(samples, pose, startY, endY, distance, length, tick);
            previousTick = tick;
        }
        Pose endPose = new Pose(endX, endZ, headingRadians(endHeading));
        long endTick = Math.max(previousTick + 1L, Math.round(length / speed));
        addSample(samples, endPose, startY, endY, length, length, endTick);
        return samples;
    }

    private static void addSample(List<TrajectorySample> samples, Pose pose,
                                  double startY, double endY, double traveled,
                                  double length, long tick) {
        double fraction = length < 1.0E-9D ? 1.0D
                : Math.min(1.0D, traveled / length);
        double y = startY + (endY - startY) * fraction;
        float yaw = normalizeYaw((float) (90.0D - Math.toDegrees(pose.heading)));
        float pitch = (float) Math.toDegrees(Math.atan2(endY - startY,
                Math.max(length, 1.0E-9D)));
        samples.add(new TrajectorySample(pose.x, y, pose.z, yaw, pitch,
                0.0F, tick, 1.0D));
    }

    private static Pose poseAt(DubinsPathType type, double[] parameters,
                               double x, double z, double angle,
                               double distance, double radius) {
        char[] segments = type.name().toCharArray();
        double remaining = distance / radius;
        for (int index = 0; index < segments.length && remaining > 0.0D; index++) {
            double amount = Math.min(parameters[index], remaining);
            if (segments[index] == 'S') {
                x += amount * radius * Math.cos(angle);
                z += amount * radius * Math.sin(angle);
            } else {
                double direction = segments[index] == 'L' ? 1.0D : -1.0D;
                double nextAngle = angle + direction * amount;
                x += direction * radius * (Math.sin(nextAngle) - Math.sin(angle));
                z -= direction * radius * (Math.cos(nextAngle) - Math.cos(angle));
                angle = nextAngle;
            }
            remaining -= amount;
        }
        return new Pose(x, z, angle);
    }

    private static double headingRadians(double minecraftYawDegrees) {
        return Math.toRadians(90.0D - minecraftYawDegrees);
    }

    private static float normalizeYaw(float yaw) {
        float result = yaw % 360.0F;
        return result < 0.0F ? result + 360.0F : result;
    }

    private static double mod2pi(double value) {
        double result = value % TWO_PI;
        return result < 0.0D ? result + TWO_PI : result;
    }

    private static void validate(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite Dubins input");
        }
        if (values[8] <= 0.0D || values[9] <= 0.0D) {
            throw new IllegalArgumentException("Turn radius and speed must be positive");
        }
    }

    private static final class Pose {
        private final double x;
        private final double z;
        private final double heading;

        private Pose(double x, double z, double heading) {
            this.x = x;
            this.z = z;
            this.heading = heading;
        }
    }
}
