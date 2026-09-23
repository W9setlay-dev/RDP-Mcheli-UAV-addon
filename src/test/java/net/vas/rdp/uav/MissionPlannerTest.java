package net.vas.rdp.uav;

import net.vas.rdp.uav.mission.MissionPlanDecision;
import net.vas.rdp.uav.mission.MissionPlanner;
import net.vas.rdp.uav.target.ContactLifecycle;
import net.vas.rdp.uav.target.ContactSnapshot;
import net.vas.rdp.uav.target.ContactTracker;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class MissionPlannerTest {
    @Test
    public void stationaryTargetCanProgressToExecution() {
        ContactSnapshot target = observed();
        MissionPlanner planner = new MissionPlanner();
        assertEquals(MissionPlanDecision.TRACK_TARGET, planner.decide(target, false));
        assertEquals(MissionPlanDecision.EXECUTE_OBJECTIVE, planner.decide(target, true));
    }

    @Test
    public void lostTargetRequestsReacquisition() {
        ContactTracker tracker = new ContactTracker(1);
        UUID id = UUID.randomUUID();
        tracker.observe(0, id, "minecraft:zombie", 10, 10, 10, 20, 0,
                ContactLifecycle.TRACKING);
        ContactSnapshot lost = tracker.markLost(id, 2);
        assertEquals(MissionPlanDecision.REACQUIRE_TARGET,
                new MissionPlanner().decide(lost, true));
    }

    private static ContactSnapshot observed() {
        ContactTracker tracker = new ContactTracker();
        return tracker.observe(0, UUID.randomUUID(), "minecraft:zombie",
                1, 10, 0, 20, 1, ContactLifecycle.TRACKING);
    }
}
