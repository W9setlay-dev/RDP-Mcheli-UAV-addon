package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.ContinuousTargetTracker;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContinuousTargetTrackerTest {
    @Test
    public void updatesAreStaggeredAndReuseLastSolution() {
        ContinuousTargetTracker tracker = new ContinuousTargetTracker(
                new ContinuousTargetTracker.Parameters(3.0D, 0.45D,
                        0.35D, 0.75D, 48.0D, 2));
        ContinuousTargetTracker.Solution first =
                tracker.update(10, 0, 10, 0, 40, 10, 0, 1, 0, 0);
        ContinuousTargetTracker.Solution cached =
                tracker.update(11, 0, 10, 0, 40, 10, 20, 1, 0, 0);
        assertTrue(first.isUpdated());
        assertFalse(cached.isUpdated());
        assertTrue(first.getX() == cached.getX());
        assertTrue(first.getZ() == cached.getZ());
    }

    @Test
    public void movingTargetUsesBoundedSmoothedCorrections() {
        ContinuousTargetTracker tracker = new ContinuousTargetTracker(
                new ContinuousTargetTracker.Parameters(8.0D, 0.18D,
                        0.12D, 1.5D, 96.0D, 1));
        ContinuousTargetTracker.Solution first =
                tracker.update(1, 0, 50, 0, 100, 50, 0, 0, 0, 0);
        ContinuousTargetTracker.Solution second =
                tracker.update(2, 0, 50, 0, 0, 50, 100, 0, 0, 0);
        assertTrue(first.isUpdated());
        assertTrue(second.isUpdated());
        assertTrue(second.getX() > -96.0D && second.getX() < 96.0D);
        assertTrue(second.getZ() > -96.0D && second.getZ() < 96.0D);
    }
}
