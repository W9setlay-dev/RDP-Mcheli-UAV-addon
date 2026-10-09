package net.vas.rdp.uav.client.vision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable cross-thread publication of perception output and diagnostics. */
public final class PerceptionSnapshot {
    private final State state;
    private final long sequence;
    private final long capturedAtNanos;
    private final long processedAtNanos;
    private final String sourceId;
    private final String cameraId;
    private final String statusReason;
    private final long processingNanos;
    private final long frameAgeNanos;
    private final long droppedFrames;
    private final List<PerceptionTracker.TrackView> tracks;

    public PerceptionSnapshot(State state, long sequence, long capturedAtNanos,
                              long processedAtNanos, String sourceId, String cameraId,
                              String statusReason, long processingNanos, long frameAgeNanos,
                              long droppedFrames, List<PerceptionTracker.TrackView> tracks) {
        if (state == null || sequence < -1L || capturedAtNanos < 0L
                || processedAtNanos < 0L || processingNanos < 0L || frameAgeNanos < 0L
                || droppedFrames < 0L || tracks == null) {
            throw new IllegalArgumentException("Invalid perception snapshot");
        }
        this.state = state;
        this.sequence = sequence;
        this.capturedAtNanos = capturedAtNanos;
        this.processedAtNanos = processedAtNanos;
        this.sourceId = sourceId == null ? "" : sourceId;
        this.cameraId = cameraId == null ? "" : cameraId;
        this.statusReason = statusReason == null ? "" : statusReason;
        this.processingNanos = processingNanos;
        this.frameAgeNanos = frameAgeNanos;
        this.droppedFrames = droppedFrames;
        this.tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
    }

    public static PerceptionSnapshot idle(State state, String reason, long droppedFrames) {
        return new PerceptionSnapshot(state, -1L, 0L, 0L, "", "", reason,
                0L, 0L, droppedFrames,
                Collections.<PerceptionTracker.TrackView>emptyList());
    }

    public State getState() { return state; }
    public long getSequence() { return sequence; }
    public long getCapturedAtNanos() { return capturedAtNanos; }
    public long getProcessedAtNanos() { return processedAtNanos; }
    public String getSourceId() { return sourceId; }
    public String getCameraId() { return cameraId; }
    public String getStatusReason() { return statusReason; }
    public long getProcessingNanos() { return processingNanos; }
    public long getFrameAgeNanos() { return frameAgeNanos; }
    public long getDroppedFrames() { return droppedFrames; }
    public List<PerceptionTracker.TrackView> getTracks() { return tracks; }

    public enum State {
        DISABLED, INITIALIZING, OPERATIONAL, DEGRADED, UNAVAILABLE
    }
}
