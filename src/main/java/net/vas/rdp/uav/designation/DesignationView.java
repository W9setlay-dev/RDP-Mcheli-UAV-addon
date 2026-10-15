package net.vas.rdp.uav.designation;

import java.util.UUID;

public final class DesignationView {
    private final UUID designationId;
    private final DesignationType type;
    private final DesignationSource source;
    private final String provenance;
    private final float confidence;
    private final long expiresAtTick;
    private final UUID attachedMissionId;
    private final double resolvedX;
    private final double resolvedY;
    private final double resolvedZ;
    private final UUID resolvedEntityUuid;

    public DesignationView(UUID designationId, DesignationType type, DesignationSource source,
                           String provenance, float confidence, long expiresAtTick,
                           UUID attachedMissionId, double resolvedX, double resolvedY,
                           double resolvedZ, UUID resolvedEntityUuid) {
        this.designationId = designationId;
        this.type = type;
        this.source = source;
        this.provenance = provenance;
        this.confidence = confidence;
        this.expiresAtTick = expiresAtTick;
        this.attachedMissionId = attachedMissionId;
        this.resolvedX = resolvedX;
        this.resolvedY = resolvedY;
        this.resolvedZ = resolvedZ;
        this.resolvedEntityUuid = resolvedEntityUuid;
    }

    public UUID getDesignationId() { return designationId; }
    public DesignationType getType() { return type; }
    public DesignationSource getSource() { return source; }
    public String getProvenance() { return provenance; }
    public float getConfidence() { return confidence; }
    public long getExpiresAtTick() { return expiresAtTick; }
    public UUID getAttachedMissionId() { return attachedMissionId; }
    public double getResolvedX() { return resolvedX; }
    public double getResolvedY() { return resolvedY; }
    public double getResolvedZ() { return resolvedZ; }
    public UUID getResolvedEntityUuid() { return resolvedEntityUuid; }
}
