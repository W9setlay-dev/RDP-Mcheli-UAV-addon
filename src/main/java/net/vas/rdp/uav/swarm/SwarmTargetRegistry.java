package net.vas.rdp.uav.swarm;

import java.util.UUID;

/** Shared target identity projection; TargetLock remains mission authority. */
public final class SwarmTargetRegistry {
    private UUID targetId;
    private SwarmTargetState state = SwarmTargetState.CLEAR;
    private long lastSeenTick = -1L;

    public synchronized void observe(UUID targetId, long tick) {
        if (targetId == null) throw new IllegalArgumentException("Target identity is required");
        this.targetId = targetId;
        this.lastSeenTick = tick;
        state = SwarmTargetState.TRACKING;
    }

    public synchronized void lock(UUID targetId, long tick) {
        if (targetId == null) throw new IllegalArgumentException("Target identity is required");
        this.targetId = targetId;
        this.lastSeenTick = tick;
        state = SwarmTargetState.LOCKED;
    }

    public synchronized void markLost() {
        if (targetId != null) state = SwarmTargetState.LOST;
    }

    public synchronized void clear() {
        targetId = null;
        state = SwarmTargetState.CLEAR;
        lastSeenTick = -1L;
    }

    public synchronized UUID getTargetId() { return targetId; }
    public synchronized SwarmTargetState getState() { return state; }
    public synchronized long getLastSeenTick() { return lastSeenTick; }
}
