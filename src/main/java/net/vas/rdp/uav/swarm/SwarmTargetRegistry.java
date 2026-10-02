package net.vas.rdp.uav.swarm;

import java.util.UUID;

/** Shared target identity projection; TargetLock remains mission authority. */
public final class SwarmTargetRegistry {
    private UUID targetId;
    private SwarmTargetState state = SwarmTargetState.CLEAR;
    private long lastSeenTick = -1L;
    private int dimension = Integer.MIN_VALUE;
    private double lastX = Double.NaN;
    private double lastY = Double.NaN;
    private double lastZ = Double.NaN;
    private long lastPersistedObservationTick = -1L;
    private boolean searchDispatched;

    public synchronized void observe(UUID targetId, long tick) {
        if (targetId == null || tick < 0L) {
            throw new IllegalArgumentException("Target identity and observation tick are required");
        }
        if (this.targetId != null && !this.targetId.equals(targetId)) {
            throw new IllegalStateException("A shared target identity cannot be silently replaced");
        }
        this.targetId = targetId;
        this.lastSeenTick = tick;
        state = SwarmTargetState.TRACKING;
    }

    public synchronized boolean observe(UUID targetId, int dimension,
                                        double x, double y, double z, long tick) {
        if (targetId == null || tick < 0L || !Double.isFinite(x)
                || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Target identity and finite observation are required");
        }
        if (this.targetId != null && !this.targetId.equals(targetId)) return false;
        boolean changed = !targetId.equals(this.targetId) || this.dimension != dimension
                || this.lastSeenTick != tick || this.lastX != x || this.lastY != y || this.lastZ != z
                || state != SwarmTargetState.TRACKING;
        this.targetId = targetId;
        this.dimension = dimension;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.lastSeenTick = tick;
        state = SwarmTargetState.TRACKING;
        return changed;
    }

    public synchronized boolean recordObservationPersistence(long tick, int intervalTicks) {
        if (tick < 0L || intervalTicks < 1) {
            throw new IllegalArgumentException("Persistence tick and interval are required");
        }
        if (lastPersistedObservationTick >= 0L) {
            if (tick < lastPersistedObservationTick) {
                lastPersistedObservationTick = tick;
                return true;
            }
            if (tick - lastPersistedObservationTick < intervalTicks) return false;
        }
        lastPersistedObservationTick = tick;
        return true;
    }

    public synchronized void lock(UUID targetId, long tick) {
        if (targetId == null) throw new IllegalArgumentException("Target identity is required");
        if (tick < 0L) throw new IllegalArgumentException("Target observation tick is required");
        if (this.targetId != null && !this.targetId.equals(targetId)) {
            throw new IllegalStateException("A shared target identity cannot be silently replaced");
        }
        this.targetId = targetId;
        this.lastSeenTick = tick;
        state = SwarmTargetState.LOCKED;
    }

    public synchronized boolean lock(UUID targetId, int dimension,
                                     double x, double y, double z, long tick) {
        if (!observe(targetId, dimension, x, y, z, tick)
                && !targetId.equals(this.targetId)) return false;
        state = SwarmTargetState.LOCKED;
        return true;
    }

    public synchronized void markLost() {
        if (targetId != null) {
            if (state != SwarmTargetState.LOST) searchDispatched = false;
            state = SwarmTargetState.LOST;
        }
    }

    public synchronized void markLost(long tick) {
        if (tick < 0L) throw new IllegalArgumentException("Loss tick is required");
        markLost();
    }

    public synchronized boolean isSearchDispatched() { return searchDispatched; }

    public synchronized void setSearchDispatched(boolean value) {
        searchDispatched = value;
    }

    public synchronized void clear() {
        targetId = null;
        state = SwarmTargetState.CLEAR;
        lastSeenTick = -1L;
        dimension = Integer.MIN_VALUE;
        lastX = Double.NaN;
        lastY = Double.NaN;
        lastZ = Double.NaN;
        lastPersistedObservationTick = -1L;
        searchDispatched = false;
    }

    public synchronized UUID getTargetId() { return targetId; }
    public synchronized SwarmTargetState getState() { return state; }
    public synchronized long getLastSeenTick() { return lastSeenTick; }
    public synchronized int getDimension() { return dimension; }
    public synchronized double getLastX() { return lastX; }
    public synchronized double getLastY() { return lastY; }
    public synchronized double getLastZ() { return lastZ; }
}
