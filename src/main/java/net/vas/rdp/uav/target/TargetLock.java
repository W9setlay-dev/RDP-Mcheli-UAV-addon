package net.vas.rdp.uav.target;

import java.util.UUID;

/**
 * Server-authoritative target lock for a single entity or world-space objective.
 *
 * <p>The lock tracks the UUID identity, position, and validation health of a
 * contact so mission logic can safely compare current world state with the
 * previously observed target.</p>
 */
public final class TargetLock {
    /**
     * Validation states for a target lock.
     */
    public enum Validation {
        UNVALIDATED,
        VALID,
        LOST,
        EXPIRED,
        IDENTITY_MISMATCH
    }

    private final UUID entityId;
    private String registryId;
    private int dimension;
    private UUID lockOwnerId;
    private UUID missionId;
    private double x;
    private double y;
    private double z;
    private final long acquiredTick;
    private long lastSeenTick;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private double confidence = 1.0D;
    private Validation validation = Validation.UNVALIDATED;

    /**
     * Creates a new lock for a target that has just been observed.
     *
     * @param entityId the authoritative entity UUID of the target
     * @param registryId the registry or entity identifier string for diagnostics
     * @param dimension the world dimension hosting the target
     * @param x the X coordinate at acquisition time
     * @param y the Y coordinate at acquisition time
     * @param z the Z coordinate at acquisition time
     * @param acquiredTick the server tick when the lock was established
     */
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

    /**
     * Creates a lock that is already associated with a mission owner and mission
     * lifecycle.
     *
     * @param entityId the authoritative entity UUID
     * @param registryId the registry identifier string
     * @param dimension the world dimension of the target
     * @param x the X coordinate at acquisition time
     * @param y the Y coordinate at acquisition time
     * @param z the Z coordinate at acquisition time
     * @param acquiredTick the server tick when the lock formed
     * @param lockOwnerId the owner currently controlling the lock
     * @param missionId the mission that owns the lock
     */
    public TargetLock(UUID entityId, String registryId, int dimension, double x, double y,
                      double z, long acquiredTick, UUID lockOwnerId, UUID missionId) {
        this(entityId, registryId, dimension, x, y, z, acquiredTick);
        this.lockOwnerId = lockOwnerId;
        this.missionId = missionId;
    }

    /**
     * Updates the lock with a fresh observation. The velocity estimate is derived
     * from the time delta between observations, which keeps target prediction
     * stable between server ticks without using full physics simulation.
     *
     * @param x the latest X coordinate
     * @param y the latest Y coordinate
     * @param z the latest Z coordinate
     * @param tick the server tick corresponding to the fresh observation
     */
    public void update(double x, double y, double z, long tick) {
        if (tick < lastSeenTick) {
            throw new IllegalArgumentException("Target updates must be ordered");
        }
        long elapsed = tick - lastSeenTick;
        if (elapsed > 0L) {
            // Compute velocity from the last observed tick to keep a bounded, low-cost
            // estimate for guidance and reacquisition without depending on the world physics system.
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

    /** Marks the lock as invalid because the world identity no longer matches the original target. */
    public void markIdentityMismatch() {
        validation = Validation.IDENTITY_MISMATCH;
        confidence = 0.0D;
    }

    /** Marks the target as lost while preserving the lock until recovery or expiry rules clear it. */
    public void markLost() {
        if (validation != Validation.EXPIRED
                && validation != Validation.IDENTITY_MISMATCH) {
            validation = Validation.LOST;
        }
    }

    /** Marks the target as expired and drops confidence after the lock has aged out. */
    public void markExpired() {
        if (validation != Validation.IDENTITY_MISMATCH) {
            validation = Validation.EXPIRED;
            confidence = 0.0D;
        }
    }

    /**
     * Rebinds the registry information when the current target reuses the same
     * UUID but reports a different identity string.
     *
     * @param registryId the updated registry identifier
     * @param dimension the updated world dimension for the target
     */
    public void updateIdentity(String registryId, int dimension) {
        if (registryId == null || registryId.trim().isEmpty()) {
            throw new IllegalArgumentException("Target registry identity is required");
        }
        this.registryId = registryId;
        this.dimension = dimension;
    }

    /** @param tick the tick to forecast against */
    public double predictX(long tick) { return x + velocityX * Math.max(0L, tick - lastSeenTick); }
    /** @param tick the tick to forecast against */
    public double predictY(long tick) { return y + velocityY * Math.max(0L, tick - lastSeenTick); }
    /** @param tick the tick to forecast against */
    public double predictZ(long tick) { return z + velocityZ * Math.max(0L, tick - lastSeenTick); }
    /** @return the authoritative entity UUID behind the target lock */
    public UUID getEntityId() { return entityId; }
    /** @return the registry identifier attached to the target */
    public String getRegistryId() { return registryId; }
    /** @return the world dimension associated with the target */
    public int getDimension() { return dimension; }
    /** @return the target's current X coordinate */
    public double getX() { return x; }
    /** @return the target's current Y coordinate */
    public double getY() { return y; }
    /** @return the target's current Z coordinate */
    public double getZ() { return z; }
    /** @return the server tick when the lock was first acquired */
    public long getAcquiredTick() { return acquiredTick; }
    /** @return the most recent server tick this lock was updated */
    public long getLastSeenTick() { return lastSeenTick; }
    /** @return the estimated X velocity in blocks per tick */
    public double getVelocityX() { return velocityX; }
    /** @return the estimated Y velocity in blocks per tick */
    public double getVelocityY() { return velocityY; }
    /** @return the estimated Z velocity in blocks per tick */
    public double getVelocityZ() { return velocityZ; }
    /** @return confidence value for the current lock */
    public double getConfidence() { return confidence; }
    /** @return the current validation state */
    public Validation getValidation() { return validation; }
    /** @return the UUID of the operator or system that currently owns the lock */
    public UUID getLockOwnerId() { return lockOwnerId; }
    /** @return the mission UUID that owns the lock */
    public UUID getMissionId() { return missionId; }
    /**
     * Associates this lock with the relevant mission owner and mission lifecycle.
     *
     * @param lockOwnerId the UUID of the current owner of the lock
     * @param missionId the mission UUID that owns the target lock
     */
    public void associate(UUID lockOwnerId, UUID missionId) {
        this.lockOwnerId = lockOwnerId;
        this.missionId = missionId;
    }
}
