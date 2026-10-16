package net.vas.rdp.uav.debug;

public final class DebugRateLimiter {
    private final int maxPerTick;
    private final int maxPerSecond;
    private final int burstThreshold;
    private long currentTick = Long.MIN_VALUE;
    private int tickCount;
    private long secondWindowStartNanos;
    private int secondCount;
    private int droppedCount;

    public DebugRateLimiter(int maxPerTick, int maxPerSecond, int burstThreshold) {
        if (maxPerTick < 0) throw new IllegalArgumentException("maxPerTick must not be negative");
        if (maxPerSecond < 0) throw new IllegalArgumentException("maxPerSecond must not be negative");
        if (burstThreshold < 0) {
            throw new IllegalArgumentException("burstThreshold must not be negative");
        }
        this.maxPerTick = maxPerTick;
        this.maxPerSecond = maxPerSecond;
        this.burstThreshold = burstThreshold;
        this.secondWindowStartNanos = System.nanoTime();
    }

    public synchronized boolean allow(long tick) {
        long now = System.nanoTime();
        if (tick != currentTick) {
            currentTick = tick;
            tickCount = 0;
        }
        if (now - secondWindowStartNanos >= 1_000_000_000L) {
            secondWindowStartNanos = now;
            secondCount = 0;
        }
        if (tickCount >= maxPerTick || secondCount >= maxPerSecond) {
            if (droppedCount < Integer.MAX_VALUE) droppedCount++;
            return false;
        }
        tickCount++;
        secondCount++;
        return true;
    }

    public synchronized int droppedCount() {
        return droppedCount;
    }

    public synchronized void resetDropped() {
        droppedCount = 0;
    }

    public int getMaxPerTick() {
        return maxPerTick;
    }

    public int getMaxPerSecond() {
        return maxPerSecond;
    }

    public int getBurstThreshold() {
        return burstThreshold;
    }
}
