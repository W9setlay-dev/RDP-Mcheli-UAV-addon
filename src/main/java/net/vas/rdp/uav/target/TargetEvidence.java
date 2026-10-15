package net.vas.rdp.uav.target;

public final class TargetEvidence {
    public enum Source {
        CONFIG_RULE,
        BUILTIN_RULE,
        OPERATOR,
        TEAM_RELATION,
        SENSOR,
        NATIVE_DEFINITION
    }

    private final Classification classification;
    private final Source source;
    private final double confidence;
    private final long observedTick;
    private final String reason;

    public TargetEvidence(Classification classification, Source source,
                         double confidence, long observedTick, String reason) {
        if (classification == null || source == null || !Double.isFinite(confidence)
                || confidence < 0.0D || confidence > 1.0D || observedTick < -1L
                || reason == null || reason.trim().isEmpty()
                || reason.length() > 160) {
            throw new IllegalArgumentException("Invalid target evidence");
        }
        this.classification = classification;
        this.source = source;
        this.confidence = confidence;
        this.observedTick = observedTick;
        this.reason = reason.trim();
    }

    public Classification getClassification() { return classification; }
    public Source getSource() { return source; }
    public double getConfidence() { return confidence; }
    public long getObservedTick() { return observedTick; }
    public String getReason() { return reason; }
}
