package net.vas.rdp.uav.target;

import java.util.UUID;

public final class TargetLock {
    public enum Validation {
        UNVALIDATED,
        VALID,
        LOST,
        IDENTITY_MISMATCH
    }

    private final UUID entityId;
    private final String registryId;
    private final int dimension;
    private double x;
    private double y;
    private double z;
    private long acquiredTick;
    private long lastSeenTick;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private double confidence = 1.0D;
    private Validation validation = Validation.UNVALIDATED;

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
        long elapsed = tick - lastSeenTick;
        if (elapsed > 0L) {
            this.velocityX = (x - this.x) / elapsed;
            this.velocityY = (y - this.y) / elapsed;
            this.velocityZ = (z - this.z) / elapsed;
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.lastSeenTick = tick;
        this.validation = Validation.VALID;
        this.confidence = Math.min(1.0D, confidence + 0.05D);
    }

    public void markLost() {
        validation = Validation.LOST;
        confidence = Math.max(0.0D, confidence - 0.2D);
    }

    public void markIdentityMismatch() {
        validation = Validation.IDENTITY_MISMATCH;
        confidence = 0.0D;
    }

    public double predictX(long tick) { return x + velocityX * Math.max(0L, tick - lastSeenTick); }
    public double predictY(long tick) { return y + velocityY * Math.max(0L, tick - lastSeenTick); }
    public double predictZ(long tick) { return z + velocityZ * Math.max(0L, tick - lastSeenTick); }
    public UUID getEntityId() { return entityId; }
    public String getRegistryId() { return registryId; }
    public int getDimension() { return dimension; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public long getAcquiredTick() { return acquiredTick; }
    public long getLastSeenTick() { return lastSeenTick; }
    public double getVelocityX() { return velocityX; }
    public double getVelocityY() { return velocityY; }
    public double getVelocityZ() { return velocityZ; }
    public double getConfidence() { return confidence; }
    public Validation getValidation() { return validation; }
}
