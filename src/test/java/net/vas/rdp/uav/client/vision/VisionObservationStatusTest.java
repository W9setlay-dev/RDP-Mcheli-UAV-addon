package net.vas.rdp.uav.client.vision;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class VisionObservationStatusTest {
    @Test
    public void tracksFeedCaptureProcessingAndPresentationStages() {
        VisionObservationStatus status = new VisionObservationStatus();
        status.feedStarted("camera-a", 4L);
        VisionObservationStatus.Snapshot started = status.snapshot();

        assertTrue(started.isFeedActive());
        assertEquals(VisionObservationStatus.Stage.FEED_ACTIVE, started.getStage());
        assertEquals(8, started.shortSessionId().length());

        status.frameCaptured(4L, 100L);
        assertEquals(VisionObservationStatus.Stage.CAPTURED, status.snapshot().getStage());

        status.observePipeline(new PerceptionSnapshot(
                PerceptionSnapshot.State.OPERATIONAL, 4L, 100L, 150L,
                "mcheli-uav-feed", "camera-a", "", 20L, 50L, 0L,
                Collections.emptyList()));
        assertEquals(VisionObservationStatus.Stage.PROCESSED, status.snapshot().getStage());
        assertEquals(4L, status.snapshot().getProcessedSequence());

        status.overlayRendered("ASSIST", 4L, 200L);
        assertEquals(VisionObservationStatus.Stage.PRESENTED, status.snapshot().getStage());
        assertEquals(4L, status.snapshot().getPresentedSequence());
        assertEquals("ASSIST", status.snapshot().getPresentationMode());

        status.overlayRendered("ASSIST_STATUS", -1L, 300L);
        assertEquals(VisionObservationStatus.Stage.STATUS_ONLY, status.snapshot().getStage());
        assertEquals(4L, status.snapshot().getPresentedSequence());
    }

    @Test
    public void rejectsResultsFromOtherCamerasOrUncapturedSequences() {
        VisionObservationStatus status = new VisionObservationStatus();
        status.feedStarted("camera-a", 2L);
        status.frameCaptured(2L, 100L);

        status.observePipeline(new PerceptionSnapshot(
                PerceptionSnapshot.State.OPERATIONAL, 2L, 100L, 150L,
                "mcheli-uav-feed", "camera-b", "", 20L, 50L, 0L,
                Collections.emptyList()));
        status.observePipeline(new PerceptionSnapshot(
                PerceptionSnapshot.State.OPERATIONAL, 3L, 100L, 150L,
                "mcheli-uav-feed", "camera-a", "", 20L, 50L, 0L,
                Collections.emptyList()));
        status.observePipeline(new PerceptionSnapshot(
                PerceptionSnapshot.State.DEGRADED, 1L, 80L, 90L,
                "mcheli-uav-feed", "camera-a", "stale failure", 10L, 10L, 0L,
                Collections.emptyList()));

        assertEquals(-1L, status.snapshot().getProcessedSequence());
        assertEquals(VisionObservationStatus.Stage.CAPTURED, status.snapshot().getStage());
    }

    @Test
    public void exposesFailureAndStopsSessionWithoutInventingPresentation() {
        VisionObservationStatus status = new VisionObservationStatus();
        status.feedStarted("camera-a", 0L);
        status.unavailable("readback failed");
        assertEquals(VisionObservationStatus.Stage.UNAVAILABLE, status.snapshot().getStage());
        assertEquals("readback failed", status.snapshot().getReason());
        assertEquals(-1L, status.snapshot().getPresentedSequence());

        status.feedStopped("world unloaded");
        assertFalse(status.snapshot().isFeedActive());
        assertEquals(VisionObservationStatus.Stage.STOPPED, status.snapshot().getStage());
        assertEquals("world unloaded", status.snapshot().getReason());

        String previousSession = status.snapshot().getSessionId();
        status.feedStarted("camera-a", 5L);
        assertTrue(status.snapshot().isFeedActive());
        assertNotEquals(previousSession, status.snapshot().getSessionId());
        assertEquals(-1L, status.snapshot().getCapturedSequence());
        assertEquals(-1L, status.snapshot().getProcessedSequence());
        status.frameCaptured(5L, 400L);
        status.observePipeline(new PerceptionSnapshot(
                PerceptionSnapshot.State.OPERATIONAL, 4L, 100L, 150L,
                "mcheli-uav-feed", "camera-a", "", 20L, 50L, 0L,
                Collections.emptyList()));
        assertEquals(-1L, status.snapshot().getProcessedSequence());
    }
}
