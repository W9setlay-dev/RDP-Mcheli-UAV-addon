package net.vas.rdp.uav.target;

import java.util.UUID;

/** Server-owned camera/target reference used by both pilots and station operators. */
public final class OpticsLock {
    public enum Mode { NONE, AREA, ENTITY }

    private Mode mode = Mode.NONE;
    private UUID entityId;
    private double x;
    private double y;
    private double z;
    private long lastSeenTick = -1L;

    public void area(double x, double y, double z, long tick) {
        if (!finite(x) || !finite(y) || !finite(z)) {
            throw new IllegalArgumentException("Area lock coordinates must be finite");
        }
        mode = Mode.AREA;
        entityId = null;
        this.x = x; this.y = y; this.z = z; lastSeenTick = tick;
    }

    public void entity(UUID id, double x, double y, double z, long tick) {
        if (id == null) throw new IllegalArgumentException("Entity lock identity is required");
        area(x, y, z, tick);
        mode = Mode.ENTITY;
        entityId = id;
    }

    public void clear() {
        mode = Mode.NONE;
        entityId = null;
        lastSeenTick = -1L;
    }

    public Mode getMode() { return mode; }
    public UUID getEntityId() { return entityId; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public long getLastSeenTick() { return lastSeenTick; }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
