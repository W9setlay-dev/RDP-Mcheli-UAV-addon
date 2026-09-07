package net.vas.rdp.uav.target;

import java.util.UUID;

public final class TargetLock {
    private final UUID entityId;
    private final String registryId;
    private final int dimension;
    private double x;
    private double y;
    private double z;
    private long acquiredTick;
    private long lastSeenTick;

    public TargetLock(UUID entityId, String registryId, int dimension, double x, double y,
                      double z, long acquiredTick) {
        if (entityId == null || registryId == null || registryId.trim().isEmpty()) {
            throw new IllegalArgumentException("Target identity is required");
        }
        this.entityId = entityId;
        this.registryId = registryId;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.acquiredTick = acquiredTick;
        this.lastSeenTick = acquiredTick;
    }

    public void update(double x, double y, double z, long tick) {
        if (tick < lastSeenTick) {
            throw new IllegalArgumentException("Target updates must be ordered");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.lastSeenTick = tick;
    }

    public UUID getEntityId() { return entityId; }
    public String getRegistryId() { return registryId; }
    public int getDimension() { return dimension; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public long getAcquiredTick() { return acquiredTick; }
    public long getLastSeenTick() { return lastSeenTick; }
}
