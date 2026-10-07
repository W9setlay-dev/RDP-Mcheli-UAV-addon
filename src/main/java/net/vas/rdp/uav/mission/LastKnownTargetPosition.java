package net.vas.rdp.uav.mission;

import java.util.UUID;

public final class LastKnownTargetPosition {
    private final UUID targetId;
    private final int dimension;
    private final double x;
    private final double y;
    private final double z;
    private final long observedTick;

    public LastKnownTargetPosition(UUID targetId, int dimension,
                                   double x, double y, double z, long observedTick) {
        if (targetId == null || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z) || observedTick < 0L) {
            throw new IllegalArgumentException("Invalid last-known target position");
        }
        this.targetId = targetId;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.observedTick = observedTick;
    }

    public UUID getTargetId() { return targetId; }
    public int getDimension() { return dimension; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public long getObservedTick() { return observedTick; }
}
