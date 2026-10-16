package net.vas.rdp.uav.client.vision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.vas.rdp.uav.debug.DebugChannel;
import net.vas.rdp.uav.debug.DebugContext;
import net.vas.rdp.uav.debug.DebugTimer;

/** Single client worker with one pending latest-frame slot and bounded pixel ownership. */
final class VisionProcessingWorker {
    private static final Logger LOGGER =
            Logger.getLogger(VisionProcessingWorker.class.getName());
    private final ArrayBlockingQueue<VisualFrame> pending = new ArrayBlockingQueue<>(1);
    private final PixelBufferPool buffers;
    private final BeaconBeamDetector detector;
    private final FrameDetector frameDetector;
    private final PerceptionTracker tracker = new PerceptionTracker();
    private volatile PerceptionSnapshot snapshot = PerceptionSnapshot.idle(
            PerceptionSnapshot.State.DISABLED, "Vision is disabled", 0L);
    private volatile String activeSourceKey = "";
    private volatile long droppedFrames;
    private volatile boolean running;
    private Thread thread;

    VisionProcessingWorker(PixelBufferPool buffers, double minimumScore) {
        if (buffers == null) throw new IllegalArgumentException("Pixel buffer pool is required");
        this.buffers = buffers;
        this.detector = new BeaconBeamDetector(minimumScore);
        this.frameDetector = VisionProcessingWorker.this.detector::detect;
    }

    VisionProcessingWorker(PixelBufferPool buffers, FrameDetector frameDetector) {
        if (buffers == null || frameDetector == null) {
            throw new IllegalArgumentException("Pixel pool and frame detector are required");
        }
        this.buffers = buffers;
        this.detector = null;
        this.frameDetector = frameDetector;
    }

    synchronized void setMinimumScore(double minimumScore) {
        if (detector == null) {
            throw new IllegalStateException("Injected frame detector has no score threshold");
        }
        detector.setMinimumScore(minimumScore);
    }

    void setSource(String sourceId, String cameraId) {
        String key = sourceId + "\n" + cameraId;
        if (key.equals(activeSourceKey)) return;
        activeSourceKey = key;
        discardPending();
        snapshot = PerceptionSnapshot.idle(PerceptionSnapshot.State.INITIALIZING,
                "Waiting for a fresh camera frame", droppedFrames);
    }

    void submit(VisualFrame frame) {
        if (frame == null) throw new IllegalArgumentException("Visual frame is required");
        if (!running) start();
        if (!pending.offer(frame)) {
            VisualFrame stale = pending.poll();
            if (stale != null) {
                buffers.release(stale.ownedPixels());
                droppedFrames++;
            }
            if (!pending.offer(frame)) {
                buffers.release(frame.ownedPixels());
                droppedFrames++;
            }
        }
    }

    void disable(String reason) {
        activeSourceKey = "";
        discardPending();
        snapshot = PerceptionSnapshot.idle(PerceptionSnapshot.State.DISABLED,
                reason, droppedFrames);
    }

    void unavailable(String reason) {
        activeSourceKey = "";
        discardPending();
        snapshot = PerceptionSnapshot.idle(PerceptionSnapshot.State.UNAVAILABLE,
                reason, droppedFrames);
    }

    PerceptionSnapshot snapshot() {
        return snapshot;
    }

    long getDroppedFrames() {
        return droppedFrames;
    }

    void shutdown() {
        running = false;
        Thread current = thread;
        if (current != null) current.interrupt();
        discardPending();
        disable("Client perception stopped");
    }

    void resetSource(String reason) {
        activeSourceKey = "";
        discardPending();
        snapshot = PerceptionSnapshot.idle(PerceptionSnapshot.State.INITIALIZING,
                reason, droppedFrames);
    }

    private synchronized void start() {
        if (running) return;
        if (thread != null && thread.isAlive()) {
            running = true;
            return;
        }
        running = true;
        thread = new Thread(this::processLoop, "RDP-UAV-Vision");
        thread.setDaemon(true);
        thread.start();
    }

    private void processLoop() {
        String trackerSource = "";
        while (running) {
            VisualFrame frame;
            try {
                frame = pending.take();
            } catch (InterruptedException interrupted) {
                if (!running) return;
                continue;
            }

            long startedAt = System.nanoTime();
            DebugTimer debugTimer = DebugChannel.VISION.isEnabled()
                    ? DebugTimer.start() : null;
            String key = frame.getSourceId() + "\n" + frame.getCameraId();
            try {
                if (!key.equals(trackerSource)) {
                    tracker.reset();
                    trackerSource = key;
                }
                List<VisualDetection> detections = frameDetector.detect(frame);
                List<PerceptionTracker.TrackView> tracks =
                        tracker.update(frame.getSequence(), detections);
                double processingMillis = debugTimer == null
                        ? Double.NaN : debugTimer.stopMillis();
                if (debugTimer != null && frame.getSequence() % 25L == 0L) {
                    DebugChannel.VISION.debug(DebugContext.none(),
                            "INFERENCE_COMPLETE source="
                                    + net.vas.rdp.uav.debug.DebugText.safeValue(
                                    frame.getSourceId())
                                    + " camera="
                                    + net.vas.rdp.uav.debug.DebugText.safeValue(
                                    frame.getCameraId())
                                    + " sequence=" + frame.getSequence()
                                    + " detections=" + detections.size()
                                    + " tracks=" + tracks.size()
                                    + " processing_ms="
                                    + net.vas.rdp.uav.debug.ConsoleSink.formatDurationMillis(
                                    processingMillis));
                }
                long publishedAt = System.nanoTime();
                if (running && key.equals(activeSourceKey)) {
                    snapshot = new PerceptionSnapshot(PerceptionSnapshot.State.OPERATIONAL,
                            frame.getSequence(), frame.getCapturedAtNanos(), publishedAt,
                            frame.getSourceId(), frame.getCameraId(), "",
                            publishedAt - startedAt,
                            Math.max(0L, publishedAt - frame.getCapturedAtNanos()),
                            droppedFrames, tracks);
                }
            } catch (RuntimeException failure) {
                long failedAt = System.nanoTime();
                LOGGER.log(Level.SEVERE, "Client visual perception frame failed", failure);
                if (DebugChannel.VISION.isEnabled()
                        || DebugChannel.VISION.isLevelEnabled(
                        net.vas.rdp.uav.debug.DebugLevel.ERROR)) {
                    DebugChannel.VISION.error(DebugContext.none(),
                            "FRAME_PROCESSING_FAILED source="
                                    + net.vas.rdp.uav.debug.DebugText.safeValue(
                                    frame.getSourceId())
                                    + " sequence=" + frame.getSequence()
                                    + " reason=" + failure.getClass().getSimpleName(), failure);
                }
                if (running && key.equals(activeSourceKey)) {
                    snapshot = new PerceptionSnapshot(PerceptionSnapshot.State.DEGRADED,
                            frame.getSequence(), frame.getCapturedAtNanos(), failedAt,
                            frame.getSourceId(), frame.getCameraId(),
                            failure.getClass().getSimpleName() + ": " + failure.getMessage(),
                            failedAt - startedAt,
                            Math.max(0L, failedAt - frame.getCapturedAtNanos()),
                            droppedFrames, Collections.emptyList());
                }
            } finally {
                buffers.release(frame.ownedPixels());
            }
        }
    }

    private void discardPending() {
        List<VisualFrame> removed = new ArrayList<>(1);
        pending.drainTo(removed);
        for (VisualFrame frame : removed) buffers.release(frame.ownedPixels());
    }

    interface FrameDetector {
        List<VisualDetection> detect(VisualFrame frame);
    }
}
