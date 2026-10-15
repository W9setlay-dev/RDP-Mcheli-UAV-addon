package net.vas.rdp.uav.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Immutable server-authoritative prediction for one aircraft. */
public final class PredictedTrajectory {
    private final UUID aircraftUuid;
    private final List<TrajectorySample> samples;
    private final long predictionStartTick;

    /** Creates a prediction with strictly increasing sample ticks. */
    public PredictedTrajectory(UUID aircraftUuid, List<TrajectorySample> samples,
                               long predictionStartTick) {
        if (aircraftUuid == null || samples == null || samples.isEmpty()
                || predictionStartTick < 0L || samples.contains(null)) {
            throw new IllegalArgumentException("Invalid predicted trajectory");
        }
        List<TrajectorySample> copy = new ArrayList<>(samples);
        long previousTick = -1L;
        for (TrajectorySample sample : copy) {
            if (sample.getTickOffset() <= previousTick) {
                throw new IllegalArgumentException("Trajectory sample ticks must increase");
            }
            previousTick = sample.getTickOffset();
        }
        this.aircraftUuid = aircraftUuid;
        this.samples = Collections.unmodifiableList(copy);
        this.predictionStartTick = predictionStartTick;
    }

    /** Returns the aircraft identity for this prediction. */
    public UUID getAircraftUuid() { return aircraftUuid; }
    /** Returns the immutable, tick-ordered sample list. */
    public List<TrajectorySample> getSamples() { return samples; }
    /** Returns the world tick at which the prediction begins. */
    public long getPredictionStartTick() { return predictionStartTick; }
}
