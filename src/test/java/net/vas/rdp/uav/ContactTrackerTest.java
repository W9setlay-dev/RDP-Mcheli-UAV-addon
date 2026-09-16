package net.vas.rdp.uav;

import net.vas.rdp.uav.target.ContactLifecycle;
import net.vas.rdp.uav.target.ContactSnapshot;
import net.vas.rdp.uav.target.ContactTracker;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class ContactTrackerTest {
    @Test
    public void preservesUuidAndComputesBoundedVelocity() {
        ContactTracker tracker = new ContactTracker(2);
        UUID id = UUID.randomUUID();
        tracker.observe(0, id, "minecraft:zombie", 0, 10, 0, 20, 1, ContactLifecycle.SELECTABLE);
        ContactSnapshot moved = tracker.observe(0, id, "minecraft:zombie", 6, 10, 8, 20, 4, ContactLifecycle.TRACKING);
        assertEquals(id, moved.getEntityId());
        assertEquals(2.0D, moved.getVelocityX(), 0.001D);
        assertEquals(8.0D / 3.0D, moved.getVelocityZ(), 0.001D);
        assertEquals(ContactLifecycle.TRACKING, moved.getLifecycle());
        assertEquals("CONTACT", moved.getMarkerState());
    }

    @Test
    public void lostContactsExpireWithoutBeingSelectable() {
        ContactTracker tracker = new ContactTracker(2);
        UUID id = UUID.randomUUID();
        tracker.observe(0, id, "minecraft:zombie", 0, 0, 0, 1, 1, ContactLifecycle.SELECTABLE);
        assertEquals(ContactLifecycle.LOST, tracker.markLost(id, 2).getLifecycle());
        assertEquals(ContactLifecycle.EXPIRED, tracker.markLost(id, 4).getLifecycle());
        assertTrue(tracker.selectable().isEmpty());
    }
}
