package net.vas.rdp.uav.client.vision;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PerceptionTrackerTest {
    @Test
    public void confirmsStableTrackAndPreservesIdAcrossOneMissedFrame() {
        PerceptionTracker tracker = new PerceptionTracker();
        long firstId = tracker.update(1L, detections(0.40D)).get(0).getId();
        tracker.update(2L, detections(0.405D));
        assertEquals(PerceptionTracker.TrackLifecycle.TENTATIVE,
                tracker.snapshot().get(0).getLifecycle());

        List<PerceptionTracker.TrackView> confirmed = tracker.update(3L, detections(0.41D));
        assertEquals(firstId, confirmed.get(0).getId());
        assertEquals(PerceptionTracker.TrackLifecycle.CONFIRMED,
                confirmed.get(0).getLifecycle());

        List<PerceptionTracker.TrackView> stale = tracker.update(4L,
                Collections.<VisualDetection>emptyList());
        assertEquals(firstId, stale.get(0).getId());
        assertEquals(PerceptionTracker.TrackLifecycle.STALE, stale.get(0).getLifecycle());
        assertEquals(PerceptionTracker.TrackLifecycle.LOST,
                tracker.update(7L, Collections.<VisualDetection>emptyList())
                        .get(0).getLifecycle());
        assertEquals(0, tracker.update(13L, Collections.<VisualDetection>emptyList()).size());
    }

    @Test
    public void ignoresOutOfOrderFramesAndCreatesNewIdAfterTrackExpiry() {
        PerceptionTracker tracker = new PerceptionTracker();
        long firstId = tracker.update(8L, detections(0.20D)).get(0).getId();
        tracker.update(7L, detections(0.20D));
        assertEquals(8L, tracker.snapshot().get(0).getLastProcessedSequence());

        tracker.update(17L, Collections.<VisualDetection>emptyList());
        long secondId = tracker.update(18L, detections(0.20D)).get(0).getId();
        assertNotEquals(firstId, secondId);
    }

    @Test
    public void publishedTrackListIsImmutable() {
        List<PerceptionTracker.TrackView> snapshot =
                new PerceptionTracker().update(1L, detections(0.1D));
        try {
            snapshot.clear();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("track snapshot must be immutable");
    }

    private static List<VisualDetection> detections(double x) {
        return Collections.singletonList(new VisualDetection(
                VisualDetection.BEACON_BEAM, 0.8D, x, 0.2D, 0.02D, 0.5D));
    }
}
