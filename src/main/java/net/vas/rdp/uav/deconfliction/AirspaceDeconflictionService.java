package net.vas.rdp.uav.deconfliction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-side separation-volume screening and bounded resolution proposal service. */
public final class AirspaceDeconflictionService {
    private final double minimumHorizontalSeparation;
    private final double minimumVerticalSeparation;
    private final long conflictTimeWindowTicks;
    private final int maximumResolutionAttempts;
    private final Map<AircraftPair, Integer> attempts = new HashMap<>();

    /** Creates a service with the documented default separation policy. */
    public AirspaceDeconflictionService() {
        this(24.0D, 8.0D, 60L, 3);
    }

    /** Creates a service with validated separation limits and a per-instance attempt budget. */
    public AirspaceDeconflictionService(double minimumHorizontalSeparation,
                                        double minimumVerticalSeparation,
                                        long conflictTimeWindowTicks,
                                        int maximumResolutionAttempts) {
        if (!Double.isFinite(minimumHorizontalSeparation)
                || !Double.isFinite(minimumVerticalSeparation)
                || minimumHorizontalSeparation <= 0.0D || minimumVerticalSeparation <= 0.0D
                || conflictTimeWindowTicks < 0L || maximumResolutionAttempts < 1) {
            throw new IllegalArgumentException("Invalid deconfliction policy");
        }
        this.minimumHorizontalSeparation = minimumHorizontalSeparation;
        this.minimumVerticalSeparation = minimumVerticalSeparation;
        this.conflictTimeWindowTicks = conflictTimeWindowTicks;
        this.maximumResolutionAttempts = maximumResolutionAttempts;
    }

    /** Finds simultaneous trajectory samples inside the configured separation volume. */
    public List<Conflict> detectConflicts(List<PredictedTrajectory> trajectories) {
        if (trajectories == null) throw new IllegalArgumentException("Trajectories are required");
        List<PredictedTrajectory> ordered = new ArrayList<>(trajectories);
        if (ordered.contains(null)) throw new IllegalArgumentException("Trajectory is required");
        ordered.sort(Comparator.comparing(item -> item.getAircraftUuid().toString()));
        List<Conflict> conflicts = new ArrayList<>();
        for (int first = 0; first < ordered.size(); first++) {
            for (int second = first + 1; second < ordered.size(); second++) {
                Conflict conflict = firstConflict(ordered.get(first), ordered.get(second));
                if (conflict != null) conflicts.add(conflict);
            }
        }
        return Collections.unmodifiableList(conflicts);
    }

    /** Selects the lowest-priority aircraft and advances through a three-step proposal budget. */
    public Resolution resolve(Conflict conflict, Map<UUID, Integer> priorityMap) {
        if (conflict == null || priorityMap == null) {
            throw new IllegalArgumentException("Conflict and priority map are required");
        }
        UUID selected = lowerPriority(conflict, priorityMap);
        AircraftPair pair = new AircraftPair(conflict.getAircraftA(), conflict.getAircraftB());
        int attempt = attempts.getOrDefault(pair, 0) + 1;
        if (attempt > maximumResolutionAttempts) return null;
        attempts.put(pair, attempt);
        if (attempt == 1) {
            return new Resolution(Resolution.Type.ALTITUDE_OFFSET, selected,
                    minimumVerticalSeparation + 4.0D, 0, -1);
        }
        if (attempt == 2) {
            return new Resolution(Resolution.Type.TIME_DELAY, selected, 0.0D, 10, -1);
        }
        return new Resolution(Resolution.Type.SLOT_REASSIGNMENT, selected,
                minimumHorizontalSeparation + 4.0D, 0, 1);
    }

    /** Validates a proposal by applying its bounded transform and rechecking all peers. */
    public boolean validateResolution(Resolution resolution,
                                      List<PredictedTrajectory> allTrajectories) {
        if (resolution == null || allTrajectories == null || allTrajectories.contains(null)) {
            return false;
        }
        List<PredictedTrajectory> transformed = new ArrayList<>(allTrajectories.size());
        boolean found = false;
        for (PredictedTrajectory trajectory : allTrajectories) {
            if (trajectory.getAircraftUuid().equals(resolution.getAircraftUuid())) {
                if (found) return false;
                found = true;
                transformed.add(transform(trajectory, resolution));
            } else {
                transformed.add(trajectory);
            }
        }
        if (!found) return false;
        for (Conflict conflict : detectConflicts(transformed)) {
            if (conflict.getAircraftA().equals(resolution.getAircraftUuid())
                    || conflict.getAircraftB().equals(resolution.getAircraftUuid())) return false;
        }
        return true;
    }

    /** Clears retry history when the owning mission finishes or is replanned. */
    public void resetResolutionBudget() {
        attempts.clear();
    }

    private Conflict firstConflict(PredictedTrajectory first, PredictedTrajectory second) {
        Map<Long, TrajectorySample> secondSamples = new HashMap<>();
        for (TrajectorySample sample : second.getSamples()) {
            long absoluteTick = second.getPredictionStartTick() + sample.getTickOffset();
            secondSamples.put(absoluteTick, sample);
        }
        for (TrajectorySample sample : first.getSamples()) {
            long tick = first.getPredictionStartTick() + sample.getTickOffset();
            if (tick > first.getPredictionStartTick() + conflictTimeWindowTicks
                    || tick > second.getPredictionStartTick() + conflictTimeWindowTicks) continue;
            TrajectorySample peer = secondSamples.get(tick);
            if (peer == null) continue;
            double horizontal = Math.hypot(sample.getX() - peer.getX(),
                    sample.getZ() - peer.getZ());
            double vertical = Math.abs(sample.getY() - peer.getY());
            if (horizontal < minimumHorizontalSeparation
                    && vertical < minimumVerticalSeparation) {
                double distance = Math.sqrt(horizontal * horizontal + vertical * vertical);
                return new Conflict(first.getAircraftUuid(), second.getAircraftUuid(),
                        tick, ConflictType.BOTH, distance);
            }
        }
        return null;
    }

    private UUID lowerPriority(Conflict conflict, Map<UUID, Integer> priorityMap) {
        Integer priorityA = priorityMap.get(conflict.getAircraftA());
        Integer priorityB = priorityMap.get(conflict.getAircraftB());
        int valueA = priorityA == null ? 0 : priorityA;
        int valueB = priorityB == null ? 0 : priorityB;
        if (valueA != valueB) return valueA < valueB
                ? conflict.getAircraftA() : conflict.getAircraftB();
        return conflict.getAircraftA().toString().compareTo(conflict.getAircraftB().toString()) > 0
                ? conflict.getAircraftA() : conflict.getAircraftB();
    }

    private PredictedTrajectory transform(PredictedTrajectory trajectory, Resolution resolution) {
        List<TrajectorySample> samples = new ArrayList<>();
        if (resolution.getType() == Resolution.Type.TIME_DELAY) {
            if (resolution.getDelayTicks() <= 0) return trajectory;
            TrajectorySample first = trajectory.getSamples().get(0);
            long interval = TrajectoryPredictor.SAMPLE_INTERVAL_TICKS;
            for (long tick = 0L; tick < resolution.getDelayTicks(); tick += interval) {
                samples.add(new TrajectorySample(first.getX(), first.getY(), first.getZ(),
                        first.getYaw(), first.getPitch(), first.getRoll(), tick,
                        first.getConfidence()));
            }
            long lastTick = samples.isEmpty() ? -1L
                    : samples.get(samples.size() - 1).getTickOffset();
            for (TrajectorySample sample : trajectory.getSamples()) {
                long shifted = sample.getTickOffset() + resolution.getDelayTicks();
                if (shifted <= lastTick) continue;
                samples.add(new TrajectorySample(sample.getX(), sample.getY(), sample.getZ(),
                        sample.getYaw(), sample.getPitch(), sample.getRoll(), shifted,
                        sample.getConfidence()));
                lastTick = shifted;
            }
        } else {
            boolean altitude = resolution.getType() == Resolution.Type.ALTITUDE_OFFSET;
            for (TrajectorySample sample : trajectory.getSamples()) {
                double x = sample.getX();
                double y = sample.getY();
                if (altitude) y += resolution.getOffsetBlocks();
                else x += resolution.getOffsetBlocks();
                samples.add(new TrajectorySample(x, y, sample.getZ(), sample.getYaw(),
                        sample.getPitch(), sample.getRoll(), sample.getTickOffset(),
                        sample.getConfidence()));
            }
        }
        return new PredictedTrajectory(trajectory.getAircraftUuid(), samples,
                trajectory.getPredictionStartTick());
    }

    private static final class AircraftPair {
        private final UUID first;
        private final UUID second;

        private AircraftPair(UUID first, UUID second) {
            this.first = first.toString().compareTo(second.toString()) <= 0 ? first : second;
            this.second = this.first == first ? second : first;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof AircraftPair)) return false;
            AircraftPair pair = (AircraftPair) other;
            return first.equals(pair.first) && second.equals(pair.second);
        }

        @Override
        public int hashCode() {
            return 31 * first.hashCode() + second.hashCode();
        }
    }
}
