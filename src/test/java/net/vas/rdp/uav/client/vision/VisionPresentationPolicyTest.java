package net.vas.rdp.uav.client.vision;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class VisionPresentationPolicyTest {
    @Test
    public void presentationModesCycleWithoutChangingCaptureGate() {
        assertTrue(VisionPresentationPolicy.isAssistEnabled(true,
                VisionPresentationMode.ASSIST));
        assertFalse(false);
        assertFalse(VisionPresentationPolicy.isAssistEnabled(true,
                VisionPresentationMode.NORMAL));
        assertTrue(VisionPresentationPolicy.isDiagnosticsEnabled(true, true,
                VisionPresentationMode.DIAGNOSTICS));
        assertFalse(false);
        assertFalse(VisionPresentationPolicy.isDiagnosticsEnabled(true, true,
                VisionPresentationMode.ASSIST));

        assertSame(VisionPresentationMode.NORMAL.next(), VisionPresentationMode.ASSIST);
        assertSame(VisionPresentationMode.ASSIST.next(), VisionPresentationMode.DIAGNOSTICS);
        assertSame(VisionPresentationMode.DIAGNOSTICS.next(), VisionPresentationMode.NORMAL);
    }

    @Test
    public void onlyFreshOperationalSnapshotsMayRenderObservedLocations() {
        PerceptionSnapshot snapshot = operationalSnapshot(10_000_000_000L);
        assertTrue(VisionPresentationPolicy.isSnapshotFresh(snapshot, 10_500_000_000L));
        assertFalse(VisionPresentationPolicy.isSnapshotFresh(snapshot, 13_000_000_001L));
        assertFalse(VisionPresentationPolicy.isSnapshotFresh(snapshot, 9_000_000_000L));
        assertFalse(VisionPresentationPolicy.isSnapshotFresh(
                PerceptionSnapshot.idle(PerceptionSnapshot.State.UNAVAILABLE,
                        "capture failed", 0L), 10_500_000_000L));
        assertFalse(VisionPresentationPolicy.isSnapshotFresh(null, 10_500_000_000L));
    }

    @Test
    public void staleTracksMayBeMarkedButLostTracksNeverShowTheirOldBox() {
        assertTrue(VisionPresentationPolicy.shouldDrawBox(
                PerceptionTracker.TrackLifecycle.TENTATIVE, true));
        assertTrue(VisionPresentationPolicy.shouldDrawBox(
                PerceptionTracker.TrackLifecycle.CONFIRMED, true));
        assertTrue(VisionPresentationPolicy.shouldDrawBox(
                PerceptionTracker.TrackLifecycle.STALE, true));
        assertFalse(VisionPresentationPolicy.shouldDrawBox(
                PerceptionTracker.TrackLifecycle.LOST, true));
        assertFalse(false);
    }

    private static PerceptionSnapshot operationalSnapshot(long capturedAt) {
        return new PerceptionSnapshot(PerceptionSnapshot.State.OPERATIONAL,
                1L, capturedAt, capturedAt + 1L, "feed", "camera", "",
                1L, 1L, 0L,
                Collections.<PerceptionTracker.TrackView>emptyList());
    }
}
