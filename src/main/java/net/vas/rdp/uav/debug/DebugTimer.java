package net.vas.rdp.uav.debug;

public final class DebugTimer {
    private final long startedAtNanos;

    private DebugTimer(long startedAtNanos) {
        this.startedAtNanos = startedAtNanos;
    }

    public static DebugTimer start() {
        return new DebugTimer(System.nanoTime());
    }

    public double stopMillis() {
        return nanosToMillis(System.nanoTime() - startedAtNanos);
    }

    static double nanosToMillis(long elapsedNanos) {
        return elapsedNanos / 1_000_000.0;
    }
}
