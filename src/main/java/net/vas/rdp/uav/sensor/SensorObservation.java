package net.vas.rdp.uav.sensor;

import java.util.UUID;

public final class SensorObservation {
    private final String source;
    private final KnowledgeKind kind;
    private final String classification;
    private final UUID entityId;
    private final String registryId;
    private final double x;
    private final double y;
    private final double z;
    private final double confidence;
    private final long tick;

    public SensorObservation(String source, KnowledgeKind kind, String classification,
                             UUID entityId, String registryId, double x, double y, double z,
                             double confidence, long tick) {
        if (source == null || source.trim().isEmpty() || kind == null
                || classification == null || classification.trim().isEmpty()
                || confidence < 0.0D || confidence > 1.0D || tick < 0L
                || Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            throw new IllegalArgumentException("Invalid sensor observation");
        }
        this.source = source;
        this.kind = kind;
        this.classification = classification;
        this.entityId = entityId;
        this.registryId = registryId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.confidence = confidence;
        this.tick = tick;
    }

    public String getSource() { return source; }
    public KnowledgeKind getKind() { return kind; }
    public String getClassification() { return classification; }
    public UUID getEntityId() { return entityId; }
    public String getRegistryId() { return registryId; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getConfidence() { return confidence; }
    public long getTick() { return tick; }
}
