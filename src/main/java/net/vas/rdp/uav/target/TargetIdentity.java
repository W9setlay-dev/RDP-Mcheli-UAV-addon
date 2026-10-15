package net.vas.rdp.uav.target;

import java.util.Locale;
import java.util.UUID;

public final class TargetIdentity {
    private static final int MAX_REGISTRY_ID_LENGTH = 256;
    private final String registryId;
    private final UUID entityId;
    private final int dimension;

    public TargetIdentity(String registryId, UUID entityId, int dimension) {
        if (registryId == null || registryId.trim().isEmpty()
                || registryId.trim().length() > MAX_REGISTRY_ID_LENGTH) {
            throw new IllegalArgumentException("Target registry identity is required");
        }
        this.registryId = registryId.trim().toLowerCase(Locale.ROOT);
        this.entityId = entityId;
        this.dimension = dimension;
    }

    public String getRegistryId() { return registryId; }
    public UUID getEntityId() { return entityId; }
    public int getDimension() { return dimension; }
}
