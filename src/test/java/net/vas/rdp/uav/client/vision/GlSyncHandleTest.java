package net.vas.rdp.uav.client.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GlSyncHandleTest {
    @Test
    public void zeroMeansNoFenceAndCreationFailureDoesNotOccupySlot() {
        GlSyncHandle fence = new GlSyncHandle();

        assertFalse(fence.isPresent());
        assertFalse(fence.setCreated(0L));
        assertFalse(fence.isPresent());
        assertEquals(0L, fence.value());
    }

    @Test
    public void liveFenceCanBeTakenOnceAndSlotCanBeReused() {
        GlSyncHandle fence = new GlSyncHandle();

        assertTrue(fence.setCreated(37L));
        assertTrue(fence.isPresent());
        assertEquals(37L, fence.value());

        assertEquals(37L, fence.take());
        assertEquals(0L, fence.take());
        assertFalse(fence.isPresent());
        assertEquals(0L, fence.value());

        assertTrue(fence.setCreated(91L));
        assertEquals(91L, fence.value());
    }
}
