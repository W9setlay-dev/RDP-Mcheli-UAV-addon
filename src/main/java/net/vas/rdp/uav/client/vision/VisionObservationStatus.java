package net.vas.rdp.uav.client.vision;

import java.util.UUID;

/** Read-only client lifecycle status for one locally observed camera-feed session. */
public final class VisionObservationStatus {
    private static final Snapshot NO_FEED = Snapshot.waiting();
    private volatile Snapshot snapshot = NO_FEED;

    public static Snapshot waitingSnapshot() {
        return NO_FEED;
    }

    void feedStarted(String cameraId, long firstSequence) {
        Snapshot current = snapshot;
        if (current.feedActive) return;
        if (firstSequence < 0L) throw new IllegalArgumentException("Invalid first frame sequence");
        snapshot = new Snapshot(UUID.randomUUID().toString(), cameraId, firstSequence, true,
                Stage.FEED_ACTIVE, PerceptionSnapshot.State.INITIALIZING,
                "Waiting for the first camera frame", -1L, -1L, -1L,
                "", 0L, 0L, 0L);
    }

    void frameCaptured(long sequence, long capturedAtNanos) {
        Snapshot current = snapshot;
        if (!current.feedActive || sequence < current.firstSequence || capturedAtNanos <= 0L
                || sequence <= current.capturedSequence) return;
        Stage stage = current.processedSequence >= sequence
                ? current.stage : Stage.CAPTURED;
        snapshot = current.withCapture(sequence, capturedAtNanos, stage);
    }

    void observePipeline(PerceptionSnapshot result) {
        Snapshot current = snapshot;
        if (!current.feedActive || result == null) return;
        boolean matchingFrame = "mcheli-uav-feed".equals(result.getSourceId())
                && current.cameraId.equals(result.getCameraId())
                && result.getSequence() >= current.firstSequence
                && current.capturedSequence >= result.getSequence();
        if (result.getState() == PerceptionSnapshot.State.OPERATIONAL
                && matchingFrame
                && result.getSequence() > current.processedSequence) {
            snapshot = current.withResult(result);
        } else if ((result.getState() == PerceptionSnapshot.State.UNAVAILABLE
                || result.getState() == PerceptionSnapshot.State.DEGRADED)
                && (matchingFrame || result.getSequence() == -1L
                && result.getSourceId().isEmpty())) {
            snapshot = current.withPipelineState(result.getState(),
                    result.getStatusReason(), Stage.UNAVAILABLE);
        } else if (result.getState() == PerceptionSnapshot.State.INITIALIZING) {
            snapshot = current.withPipelineState(result.getState(),
                    result.getStatusReason(), current.stage);
        }
    }

    void overlayRendered(String mode, long sequence, long renderedAtNanos) {
        Snapshot current = snapshot;
        if (!current.feedActive || mode == null || mode.isEmpty()
                || renderedAtNanos <= 0L) return;
        boolean resultPresented = sequence >= 0L && sequence == current.processedSequence
                && current.pipelineState == PerceptionSnapshot.State.OPERATIONAL;
        snapshot = current.withPresentation(mode,
                resultPresented ? sequence : -1L, renderedAtNanos,
                resultPresented ? Stage.PRESENTED : Stage.STATUS_ONLY);
    }

    void unavailable(String reason) {
        Snapshot current = snapshot;
        if (!current.feedActive) return;
        snapshot = current.withPipelineState(PerceptionSnapshot.State.UNAVAILABLE,
                reason, Stage.UNAVAILABLE);
    }

    void feedStopped(String reason) {
        Snapshot current = snapshot;
        if (!current.feedActive) return;
        snapshot = current.stopped(reason);
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public enum Stage {
        WAITING_FOR_FEED, FEED_ACTIVE, CAPTURED, PROCESSED, PRESENTED,
        STATUS_ONLY, UNAVAILABLE, STOPPED
    }

    public static final class Snapshot {
        private final String sessionId;
        private final String cameraId;
        private final long firstSequence;
        private final boolean feedActive;
        private final Stage stage;
        private final PerceptionSnapshot.State pipelineState;
        private final String reason;
        private final long capturedSequence;
        private final long processedSequence;
        private final long presentedSequence;
        private final String presentationMode;
        private final long capturedAtNanos;
        private final long processedAtNanos;
        private final long presentedAtNanos;

        private Snapshot(String sessionId, String cameraId, long firstSequence,
                        boolean feedActive,
                        Stage stage, PerceptionSnapshot.State pipelineState,
                        String reason, long capturedSequence, long processedSequence,
                        long presentedSequence, String presentationMode,
                        long capturedAtNanos, long processedAtNanos,
                        long presentedAtNanos) {
            this.sessionId = sessionId;
            this.cameraId = cameraId == null ? "" : cameraId;
            this.firstSequence = firstSequence;
            this.feedActive = feedActive;
            this.stage = stage;
            this.pipelineState = pipelineState;
            this.reason = reason == null ? "" : reason;
            this.capturedSequence = capturedSequence;
            this.processedSequence = processedSequence;
            this.presentedSequence = presentedSequence;
            this.presentationMode = presentationMode == null ? "" : presentationMode;
            this.capturedAtNanos = capturedAtNanos;
            this.processedAtNanos = processedAtNanos;
            this.presentedAtNanos = presentedAtNanos;
        }

        private static Snapshot waiting() {
            return new Snapshot("", "", 0L, false, Stage.WAITING_FOR_FEED,
                    PerceptionSnapshot.State.DISABLED, "Waiting for McHeli UAV camera feed",
                    -1L, -1L, -1L, "", 0L, 0L, 0L);
        }

        private Snapshot withCapture(long sequence, long capturedAt, Stage nextStage) {
            return new Snapshot(sessionId, cameraId, firstSequence, feedActive, nextStage, pipelineState,
                    reason, sequence, processedSequence, presentedSequence,
                    presentationMode, capturedAt, processedAtNanos, presentedAtNanos);
        }

        private Snapshot withResult(PerceptionSnapshot result) {
            return new Snapshot(sessionId, cameraId, firstSequence, feedActive, Stage.PROCESSED,
                    result.getState(), result.getStatusReason(), capturedSequence,
                    result.getSequence(), presentedSequence, presentationMode,
                    capturedAtNanos, result.getProcessedAtNanos(), presentedAtNanos);
        }

        private Snapshot withPipelineState(PerceptionSnapshot.State state,
                                           String statusReason, Stage nextStage) {
            return new Snapshot(sessionId, cameraId, firstSequence, feedActive, nextStage, state,
                    statusReason, capturedSequence, processedSequence,
                    presentedSequence, presentationMode, capturedAtNanos,
                    processedAtNanos, presentedAtNanos);
        }

        private Snapshot withPresentation(String mode, long sequence, long renderedAt,
                                          Stage nextStage) {
            return new Snapshot(sessionId, cameraId, firstSequence, feedActive, nextStage,
                    pipelineState, reason, capturedSequence, processedSequence,
                    sequence < 0L ? presentedSequence : sequence, mode,
                    capturedAtNanos, processedAtNanos,
                    sequence < 0L ? presentedAtNanos : renderedAt);
        }

        private Snapshot stopped(String statusReason) {
            return new Snapshot(sessionId, cameraId, firstSequence, false, Stage.STOPPED,
                    PerceptionSnapshot.State.DISABLED, statusReason, capturedSequence,
                    processedSequence, presentedSequence, presentationMode,
                    capturedAtNanos, processedAtNanos, presentedAtNanos);
        }

        public String getSessionId() { return sessionId; }
        public String getCameraId() { return cameraId; }
        public boolean isFeedActive() { return feedActive; }
        public Stage getStage() { return stage; }
        public PerceptionSnapshot.State getPipelineState() { return pipelineState; }
        public String getReason() { return reason; }
        public long getCapturedSequence() { return capturedSequence; }
        public long getProcessedSequence() { return processedSequence; }
        public long getPresentedSequence() { return presentedSequence; }
        public String getPresentationMode() { return presentationMode; }
        public long getCapturedAtNanos() { return capturedAtNanos; }
        public long getProcessedAtNanos() { return processedAtNanos; }
        public long getPresentedAtNanos() { return presentedAtNanos; }

        public String shortSessionId() {
            return sessionId.isEmpty() ? "none" : sessionId.substring(0, 8);
        }
    }
}
