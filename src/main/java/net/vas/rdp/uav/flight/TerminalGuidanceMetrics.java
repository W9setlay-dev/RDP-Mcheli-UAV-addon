package net.vas.rdp.uav.flight;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Deterministic, observation-only terminal trajectory metrics.
 *
 * <p>This class does not steer an aircraft or mutate target identity. It
 * classifies the distances observed at the native flight boundary.</p>
 */
public final class TerminalGuidanceMetrics {
    public enum Classification {
        INSUFFICIENT_DATA, CONVERGING, SLOW_CONVERGENCE, STALLED,
        OVERSHOT, DIVERGING, ORBITING, TIMEOUT
    }

    public static final int HISTORY_LIMIT = 12;
    private static final int MINIMUM_SAMPLES = 3;
    private static final double NOISE_TOLERANCE = 0.05D;
    private static final double STALL_RANGE = 0.5D;
    private static final double OVERSHOOT_DISTANCE = 1.0D;
    private static final int TREND_WINDOW = 4;
    private final UUID targetId;
    private final long entryTick;
    private final Deque<Sample> samples = new ArrayDeque<>();
    private double distanceAtEntry = Double.NaN;
    private double minimumDistance = Double.POSITIVE_INFINITY;
    private long closestApproachTick = Long.MIN_VALUE;
    private Classification classification = Classification.INSUFFICIENT_DATA;
    private boolean timedOut;

    public TerminalGuidanceMetrics(UUID targetId, long entryTick) {
        if (targetId == null) throw new IllegalArgumentException("Target ID is required");
        this.targetId = targetId;
        this.entryTick = entryTick;
    }

    public void record(long tick, double distance) {
        if (!Double.isFinite(distance) || distance < 0.0D) {
            throw new IllegalArgumentException("Distance must be finite and non-negative");
        }
        if (timedOut) return;
        if (samples.size() == HISTORY_LIMIT) samples.removeFirst();
        samples.addLast(new Sample(tick, distance));
        if (Double.isNaN(distanceAtEntry)) {
            distanceAtEntry = distance;
        }
        if (distance < minimumDistance) {
            minimumDistance = distance;
            closestApproachTick = tick;
        }
        classification = classify();
    }

    public void timeout() {
        timedOut = true;
        classification = Classification.TIMEOUT;
    }
    public UUID getTargetId() { return targetId; }
    public long getEntryTick() { return entryTick; }
    public int getSampleCount() { return samples.size(); }
    public long getClosestApproachTick() { return closestApproachTick; }
    public double getMinimumDistance() { return minimumDistance; }
    public double getDistanceAtEntry() {
        return distanceAtEntry;
    }
    public double getLatestDistance() {
        return samples.isEmpty() ? Double.NaN : samples.getLast().distance;
    }
    public Classification getClassification() { return classification; }
    public double getClosingRate() {
        if (samples.size() < 2) return 0.0D;
        Sample current = samples.getLast();
        Sample previous = previous();
        long tickDelta = current.tick - previous.tick;
        if (tickDelta <= 0L) return 0.0D;
        return (previous.distance - current.distance) / tickDelta;
    }


    private Classification classify() {
        // Precedence is intentional: overshoot, orbit, sustained divergence,
        // stall, convergence, then slow convergence.
        if (samples.size() < MINIMUM_SAMPLES) return Classification.INSUFFICIENT_DATA;
        if (hasOvershot()) return Classification.OVERSHOT;
        if (hasOrbit()) return Classification.ORBITING;
        if (hasSustainedIncrease()) return Classification.DIVERGING;
        double first = samples.getFirst().distance;
        double latest = samples.getLast().distance;
        double netDecrease = first - latest;
        if (netDecrease <= NOISE_TOLERANCE
                && samples.getLast().distance - minimumDistance <= STALL_RANGE) {
            return Classification.STALLED;
        }
        int decreasing = 0;
        int comparisons = 0;
        Sample previous = null;
        for (Sample sample : samples) {
            if (previous != null) {
                comparisons++;
                if (previous.distance - sample.distance > NOISE_TOLERANCE) decreasing++;
            }
            previous = sample;
        }
        if (netDecrease > NOISE_TOLERANCE && decreasing * 2 >= comparisons) {
            return decreasing >= TREND_WINDOW ? Classification.CONVERGING
                    : Classification.SLOW_CONVERGENCE;
        }
        return Classification.STALLED;
    }

    private boolean hasOvershot() {
        if (samples.size() < TREND_WINDOW + 1 || minimumDistance == Double.POSITIVE_INFINITY) {
            return false;
        }
        Sample latest = samples.getLast();
        int increasing = 0;
        int longestIncreasing = 0;
        Sample previous = null;
        boolean passedClosest = false;
        for (Sample sample : samples) {
            if (sample.tick == closestApproachTick) passedClosest = true;
            if (passedClosest && previous != null
                    && sample.distance - previous.distance > NOISE_TOLERANCE) {
                increasing++;
                longestIncreasing = Math.max(longestIncreasing, increasing);
            } else if (passedClosest && previous != null) {
                increasing = 0;
            }
            previous = sample;
        }
        return longestIncreasing >= 3
                && latest.distance - minimumDistance > OVERSHOOT_DISTANCE;
    }

    private boolean hasOrbit() {
        if (samples.size() < 8) return false;
        int reversals = 0;
        int previousSign = 0;
        double maximum = 0.0D;
        double minimum = Double.POSITIVE_INFINITY;
        Sample previous = null;
        for (Sample sample : samples) {
            maximum = Math.max(maximum, sample.distance);
            minimum = Math.min(minimum, sample.distance);
            if (previous != null) {
                double delta = sample.distance - previous.distance;
                int sign = Math.abs(delta) <= NOISE_TOLERANCE ? 0 : delta > 0 ? 1 : -1;
                if (sign != 0 && previousSign != 0 && sign != previousSign) reversals++;
                if (sign != 0) previousSign = sign;
            }
            previous = sample;
        }
        return reversals >= 3 && maximum - minimum >= 2.0D
                && maximum - minimum <= Math.max(12.0D, samples.getFirst().distance);
    }

    private boolean hasSustainedIncrease() {
        if (samples.size() < TREND_WINDOW) return false;
        Sample[] values = samples.toArray(new Sample[0]);
        int start = values.length - TREND_WINDOW;
        for (int i = start + 1; i < values.length; i++) {
            if (values[i].distance - values[i - 1].distance <= NOISE_TOLERANCE) return false;
        }
        return values[values.length - 1].distance - values[start].distance
                > OVERSHOOT_DISTANCE;
    }

    private Sample previous() {
        Sample latest = samples.removeLast();
        Sample previous = samples.getLast();
        samples.addLast(latest);
        return previous;
    }

    private static final class Sample {
        private final long tick;
        private final double distance;
        private Sample(long tick, double distance) {
            this.tick = tick;
            this.distance = distance;
        }
    }
}
