package net.vas.rdp.uav.sensor;

import java.util.ArrayList;
import java.util.List;

public final class SensorFusion {
    public FusionResult fuse(List<SensorObservation> observations) {
        if (observations == null) {
            throw new IllegalArgumentException("Observations are required");
        }
        if (observations.isEmpty()) {
            return new FusionResult(null, 0.0D, false, "No observations available");
        }
        List<SensorObservation> usable = new ArrayList<>();
        for (SensorObservation observation : observations) {
            if (observation != null) usable.add(observation);
        }
        if (usable.isEmpty()) {
            return new FusionResult(null, 0.0D, false, "No usable observations available");
        }
        usable.sort((left, right) -> Double.compare(right.getConfidence(), left.getConfidence()));
        SensorObservation best = usable.get(0);
        boolean disagreement = false;
        for (int i = 1; i < usable.size(); i++) {
            SensorObservation other = usable.get(i);
            if (!best.getClassification().equals(other.getClassification())
                    || (best.getRegistryId() != null && other.getRegistryId() != null
                    && !best.getRegistryId().equals(other.getRegistryId()))) {
                disagreement = true;
                break;
            }
        }
        double confidence = best.getConfidence();
        if (disagreement) confidence *= 0.5D;
        return new FusionResult(best, confidence, disagreement,
                disagreement ? "Sensor sources disagree; retaining the highest-confidence reading"
                        : "Sensor sources agree");
    }

    public static final class FusionResult {
        private final SensorObservation observation;
        private final double confidence;
        private final boolean disagreement;
        private final String explanation;

        private FusionResult(SensorObservation observation, double confidence,
                             boolean disagreement, String explanation) {
            this.observation = observation;
            this.confidence = confidence;
            this.disagreement = disagreement;
            this.explanation = explanation;
        }

        public SensorObservation getObservation() { return observation; }
        public double getConfidence() { return confidence; }
        public boolean hasDisagreement() { return disagreement; }
        public String getExplanation() { return explanation; }
    }
}
