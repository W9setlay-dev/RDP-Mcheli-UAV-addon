package net.vas.rdp.uav.client.vision;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PerceptionSnapshotTest {
    @Test
    public void copiesAndProtectsPublishedTracks() {
        List<PerceptionTracker.TrackView> source = new ArrayList<>();
        source.add(new PerceptionTracker().update(1L,
                java.util.Collections.singletonList(new VisualDetection(
                        VisualDetection.BEACON_BEAM, 0.8D, 0.1D, 0.1D,
                        0.05D, 0.5D))).get(0));
        PerceptionSnapshot snapshot = new PerceptionSnapshot(
                PerceptionSnapshot.State.OPERATIONAL, 1L, 10L, 20L, "feed",
                "camera", "", 5L, 10L, 0L, source);
        source.clear();

        assertEquals(1, snapshot.getTracks().size());
        try {
            snapshot.getTracks().clear();
            fail("published track list must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertEquals(1, snapshot.getTracks().size());
        }
    }
}
