package net.vas.rdp.uav.client.vision;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VisionProcessingWorkerTest {
    @Test
    public void replacesQueuedFrameAndAccountsForTheDrop() throws Exception {
        final PixelBufferPool buffers = new PixelBufferPool();
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        VisionProcessingWorker worker = new VisionProcessingWorker(buffers,
                new VisionProcessingWorker.FrameDetector() {
                    @Override
                    public List<VisualDetection> detect(VisualFrame frame) {
                        if (frame.getSequence() == 1L) {
                            firstStarted.countDown();
                            await(releaseFirst);
                        }
                        return Collections.emptyList();
                    }
                });
        worker.setSource("feed", "camera");
        try {
            worker.submit(frame(buffers, 1L, "camera"));
            assertTrue(firstStarted.await(2L, TimeUnit.SECONDS));
            worker.submit(frame(buffers, 2L, "camera"));
            worker.submit(frame(buffers, 3L, "camera"));
            assertEquals(1L, worker.getDroppedFrames());
            releaseFirst.countDown();
            awaitSnapshot(worker, PerceptionSnapshot.State.OPERATIONAL, 3L);
        } finally {
            releaseFirst.countDown();
            worker.shutdown();
        }
    }

    @Test
    public void cameraSourceChangeResetsTrackingBeforePublishingNewFrames() throws Exception {
        final PixelBufferPool buffers = new PixelBufferPool();
        final AtomicInteger detectorCalls = new AtomicInteger();
        VisionProcessingWorker worker = new VisionProcessingWorker(buffers,
                new VisionProcessingWorker.FrameDetector() {
                    @Override
                    public List<VisualDetection> detect(VisualFrame frame) {
                        detectorCalls.incrementAndGet();
                        return Collections.singletonList(new VisualDetection(
                                VisualDetection.BEACON_BEAM, 0.8D, 0.1D, 0.1D,
                                0.05D, 0.5D));
                    }
                });
        worker.setSource("feed", "camera-a");
        try {
            worker.submit(frame(buffers, 1L, "camera-a"));
            awaitSnapshot(worker, PerceptionSnapshot.State.OPERATIONAL, 1L);
            assertEquals(1L, worker.snapshot().getTracks().get(0).getId());

            worker.setSource("feed", "camera-b");
            assertEquals(PerceptionSnapshot.State.INITIALIZING,
                    worker.snapshot().getState());
            worker.submit(frame(buffers, 2L, "camera-b"));
            awaitSnapshot(worker, PerceptionSnapshot.State.OPERATIONAL, 2L);
            assertEquals(1L, worker.snapshot().getTracks().get(0).getId());
            assertEquals(2, detectorCalls.get());
        } finally {
            worker.shutdown();
        }
    }

    @Test
    public void publishesDegradedStateAndReasonWhenFrameProcessingFails() throws Exception {
        final PixelBufferPool buffers = new PixelBufferPool();
        VisionProcessingWorker worker = new VisionProcessingWorker(buffers,
                new VisionProcessingWorker.FrameDetector() {
                    @Override
                    public List<VisualDetection> detect(VisualFrame frame) {
                        throw new IllegalStateException("synthetic processing failure");
                    }
                });
        worker.setSource("feed", "camera");
        try {
            worker.submit(frame(buffers, 1L, "camera"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
            while (worker.snapshot().getState() != PerceptionSnapshot.State.DEGRADED
                    && System.nanoTime() < deadline) {
                Thread.sleep(5L);
            }
            assertEquals(PerceptionSnapshot.State.DEGRADED, worker.snapshot().getState());
            assertTrue(worker.snapshot().getStatusReason()
                    .contains("synthetic processing failure"));
        } finally {
            worker.shutdown();
        }
    }

    private static VisualFrame frame(PixelBufferPool buffers, long sequence, String camera) {
        byte[] pixels = buffers.acquire(4);
        assertTrue(pixels != null);
        return VisualFrame.takeOwnership(sequence, System.nanoTime(), "feed", camera,
                1, 1, pixels);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2L, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out awaiting test latch");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void awaitSnapshot(VisionProcessingWorker worker,
                                      PerceptionSnapshot.State expectedState,
                                      long expectedSequence) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (System.nanoTime() < deadline) {
            PerceptionSnapshot snapshot = worker.snapshot();
            if (snapshot.getState() == expectedState
                    && snapshot.getSequence() == expectedSequence) return;
            Thread.sleep(5L);
        }
        assertEquals(expectedState, worker.snapshot().getState());
        assertEquals(expectedSequence, worker.snapshot().getSequence());
    }
}
