package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.RouteProgressTracker;
import org.junit.Test;

import static org.junit.Assert.*;

public class RouteProgressTrackerTest {
    @Test
    public void advancingWaypointProgressCountsAsProgress() {
        RouteProgressTracker.Snapshot snapshot = RouteProgressTracker.evaluate(
                50L,
                0.0D,
                1.0D,
                25.0D,
                12.0D,
                40.0D,
                18.0D,
                10L,
                0.5D,
                200L);

        assertTrue(snapshot.isMeaningfulProgress());
        assertEquals(1.0D, snapshot.getRouteProgress(), 0.0D);
        assertEquals(50L, snapshot.getLastMeaningfulProgressTick());
        assertFalse(snapshot.isStalled());
    }

    @Test
    public void staleProgressEventuallyFlagsStalled() {
        RouteProgressTracker.Snapshot snapshot = RouteProgressTracker.evaluate(
                500L,
                1.0D,
                1.0D,
                20.0D,
                20.0D,
                8.0D,
                8.0D,
                100L,
                0.5D,
                200L);

        assertFalse(snapshot.isMeaningfulProgress());
        assertTrue(snapshot.isStalled());
        assertEquals(100L, snapshot.getLastMeaningfulProgressTick());
    }

    @Test
    public void missingTargetDistanceDoesNotCountAsZeroProgress() {
        RouteProgressTracker.Snapshot snapshot = RouteProgressTracker.evaluate(
                200L,
                0.5D,
                0.5D,
                Double.NaN,
                Double.NaN,
                7.0D,
                7.0D,
                50L,
                0.5D,
                200L);

        assertFalse(snapshot.isMeaningfulProgress());
        assertFalse(snapshot.isStalled());
        assertEquals(50L, snapshot.getLastMeaningfulProgressTick());
    }

    @Test
    public void partiallyMissingTargetDistanceDoesNotCountAsProgress() {
        RouteProgressTracker.Snapshot snapshot = RouteProgressTracker.evaluate(
                200L,
                0.5D,
                0.5D,
                20.0D,
                Double.NaN,
                7.0D,
                7.0D,
                50L,
                0.5D,
                200L);

        assertFalse(snapshot.isMeaningfulProgress());
        assertEquals(50L, snapshot.getLastMeaningfulProgressTick());
    }

    @Test
    public void partiallyMissingWaypointDistanceDoesNotCountAsProgress() {
        RouteProgressTracker.Snapshot snapshot = RouteProgressTracker.evaluate(
                200L,
                0.5D,
                0.5D,
                20.0D,
                20.0D,
                Double.NaN,
                1.0D,
                50L,
                0.5D,
                200L);

        assertFalse(snapshot.isMeaningfulProgress());
        assertEquals(50L, snapshot.getLastMeaningfulProgressTick());
    }
}
