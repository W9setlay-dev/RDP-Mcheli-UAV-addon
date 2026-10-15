package net.vas.rdp.uav.deconfliction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Immutable server-side predicted trajectory for one aircraft. */
public final class PredictedTrajectory {
    private final UUID aircraftUuid;
    private final List<TrajectorySample> samples;
    private final long predictionStartTick;

    /** Creates a trajectory whose samples are ordered by relative tick. */
    public PredictedTrajectory(UUID aircraftUuid, List<TrajectorySample> samples,
                               long predictionStartTick) {
        if (aircraftUuid == null || samples == null || samples.isEmpty()
                || predictionStartTick < 0L) {
            throw new IllegalArgumentException("Invalid predicted trajectory");
        }
        List<TrajectorySample> copy = new ArrayList<>(samples);
        long lastTick = -1L;
        for (TrajectorySample sample : copy) {
            if (sample == null || sample.getTickOffset() <= lastTick) {
                throw new IllegalArgumentException("Trajectory samples must have increasing ticks");
            }
            lastTick = sample.getTickOffset();
        }
        this.aircraftUuid = aircraftUuid;
        this.samples = Collections.unmodifiableList(copy);
        this.predictionStartTick = predictionStartTick;
    }

    public UUID getAircraftUuid() { return aircraftUuid; }
    public List<TrajectorySample> getSamples() { return samples; }
    public long getPredictionStartTick() { return predictionStartTick; }
}
