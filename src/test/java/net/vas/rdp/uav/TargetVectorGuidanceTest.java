package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.GuidanceVector;
import net.vas.rdp.uav.flight.TargetVectorGuidance;
import net.vas.rdp.uav.target.ContactLifecycle;
import net.vas.rdp.uav.target.ContactSnapshot;
import net.vas.rdp.uav.target.ContactTracker;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class TargetVectorGuidanceTest {
    @Test
    public void stationaryTargetProducesDirectVector() {
        ContactSnapshot target = observe();
        GuidanceVector vector = new TargetVectorGuidance().calculate(
                0, 0, 0, target, 0);
        assertEquals(10D, vector.getX(), 0D);
        assertEquals(20D, vector.getY(), 0D);
        assertEquals(30D, vector.getZ(), 0D);
    }

    @Test
    public void movingTargetUsesExplicitPrediction() {
        ContactTracker tracker = new ContactTracker();
        UUID id = UUID.randomUUID();
        tracker.observe(0, id, "minecraft:zombie", 0, 0, 0, 10, 1,
                ContactLifecycle.TRACKING);
        ContactSnapshot moved = tracker.observe(0, id, "minecraft:zombie",
                4, 0, 0, 10, 3, ContactLifecycle.TRACKING);
        GuidanceVector vector = new TargetVectorGuidance().calculate(
                0, 0, 0, moved, 2);
        assertEquals(8D, vector.getX(), 0.001D);
    }

    private static ContactSnapshot observe() {
        return new ContactTracker().observe(0, UUID.randomUUID(),
                "minecraft:zombie", 10, 20, 30, 10, 1, ContactLifecycle.TRACKING);
    }
}
