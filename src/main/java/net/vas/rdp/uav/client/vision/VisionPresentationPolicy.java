package net.vas.rdp.uav.client.vision;

import java.util.concurrent.TimeUnit;

/** Pure freshness and mode rules for the optional client presentation. */
public final class VisionPresentationPolicy {
    public static final long MAX_RESULT_AGE_NANOS = TimeUnit.SECONDS.toNanos(2L);

    private VisionPresentationPolicy() { }

    public static boolean isAssistEnabled(boolean captureEnabled,
                                          VisionPresentationMode mode) {
        return captureEnabled && mode == VisionPresentationMode.ASSIST;
    }

    public static boolean isDiagnosticsEnabled(boolean captureEnabled,
                                               boolean diagnosticsEnabled,
                                               VisionPresentationMode mode) {
        return captureEnabled && diagnosticsEnabled
                && mode == VisionPresentationMode.DIAGNOSTICS;
    }

    public static boolean isSnapshotFresh(PerceptionSnapshot snapshot, long nowNanos) {
        if (snapshot == null || snapshot.getState() != PerceptionSnapshot.State.OPERATIONAL
                || snapshot.getCapturedAtNanos() <= 0L
                || nowNanos < snapshot.getCapturedAtNanos()) return false;
        return nowNanos - snapshot.getCapturedAtNanos() <= MAX_RESULT_AGE_NANOS;
    }

    public static boolean shouldDrawBox(PerceptionTracker.TrackLifecycle lifecycle,
                                        boolean snapshotFresh) {
        return snapshotFresh && lifecycle != null
                && lifecycle != PerceptionTracker.TrackLifecycle.LOST;
    }
}
