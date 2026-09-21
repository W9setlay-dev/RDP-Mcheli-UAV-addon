package net.vas.rdp.uav.target;

import java.util.UUID;

/** Immutable server-authoritative projection of one tracked entity. */
public final class ContactSnapshot {
    private final UUID entityId;
    private final String registryId;
    private final int dimension;
    private final double minX, minY, minZ, maxX, maxY, maxZ;
    private final double x, y, z, previousX, previousY, previousZ;
    private final double velocityX, velocityY, velocityZ, distance, confidence, bearing, elevation;
    private final long lastDetectionTick, lastUpdateTick;
    private final ContactLifecycle lifecycle;
    private final boolean visible;

    ContactSnapshot(UUID entityId, String registryId, int dimension, double x, double y, double z,
                    double previousX, double previousY, double previousZ, double velocityX,
                    double velocityY, double velocityZ, double distance, double confidence,
                    double bearing, double elevation, long lastDetectionTick, long lastUpdateTick, ContactLifecycle lifecycle,
                    boolean visible) {
        this(entityId, registryId, dimension, x, y, z, x, y, z, x, y, z,
                previousX, previousY, previousZ, velocityX, velocityY, velocityZ,
                distance, confidence, bearing, elevation, lastDetectionTick,
                lastUpdateTick, lifecycle, visible);
    }

    ContactSnapshot(UUID entityId, String registryId, int dimension, double x, double y, double z,
                    double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                    double previousX, double previousY, double previousZ, double velocityX,
                    double velocityY, double velocityZ, double distance, double confidence,
                    double bearing, double elevation, long lastDetectionTick, long lastUpdateTick,
                    ContactLifecycle lifecycle, boolean visible) {
        this.entityId = entityId; this.registryId = registryId; this.dimension = dimension;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.x = x; this.y = y; this.z = z; this.previousX = previousX; this.previousY = previousY;
        this.previousZ = previousZ; this.velocityX = velocityX; this.velocityY = velocityY;
        this.velocityZ = velocityZ; this.distance = distance; this.confidence = confidence;
        this.bearing = bearing; this.elevation = elevation;
        this.lastDetectionTick = lastDetectionTick; this.lastUpdateTick = lastUpdateTick;
        this.lifecycle = lifecycle; this.visible = visible;
    }
    public UUID getEntityId() { return entityId; }
    public String getRegistryId() { return registryId; }
    public int getDimension() { return dimension; }
    public double getMinX() { return minX; }
    public double getMinY() { return minY; }
    public double getMinZ() { return minZ; }
    public double getMaxX() { return maxX; }
    public double getMaxY() { return maxY; }
    public double getMaxZ() { return maxZ; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getPreviousX() { return previousX; }
    public double getPreviousY() { return previousY; }
    public double getPreviousZ() { return previousZ; }
    public double getVelocityX() { return velocityX; }
    public double getVelocityY() { return velocityY; }
    public double getVelocityZ() { return velocityZ; }
    public double getDistance() { return distance; }
    public double getConfidence() { return confidence; }
    public double getBearing() { return bearing; }
    public double getElevation() { return elevation; }
    public long getLastDetectionTick() { return lastDetectionTick; }
    public long getLastUpdateTick() { return lastUpdateTick; }
    public ContactLifecycle getLifecycle() { return lifecycle; }
    public boolean isVisible() { return visible; }
    public String getMarkerState() {
        if (!visible || lifecycle == ContactLifecycle.LOST) return "LOST";
        if (lifecycle == ContactLifecycle.EXPIRED) return "HIDDEN";
        if (lifecycle == ContactLifecycle.SELECTED) return "SELECTED";
        if (lifecycle == ContactLifecycle.ATTACK) return "LOCKED";
        return "CONTACT";
    }
}
