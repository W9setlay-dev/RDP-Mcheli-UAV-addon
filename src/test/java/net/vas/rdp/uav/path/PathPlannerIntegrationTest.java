package net.vas.rdp.uav.path;

import net.vas.rdp.uav.deconfliction.TrajectorySample;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PathPlannerIntegrationTest {
    @Test
    public void planRoute_waypointSequenceReturnsOrderedProfileAwareSamples() {
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.REUSABLE_UAV, 80.0D, 40.0D, 160.0D,
                8.0D, true, "NONE");
        List<TrajectorySample> route = new PathPlanner().planRoute(Arrays.asList(
                new Waypoint(40.0D, 80.0D, 10.0D, 0.0D),
                new Waypoint(80.0D, 90.0D, 40.0D, 90.0D)),
                profile, 0.0D, 0.0D, 0.0D);

        assertTrue(route.size() > 2);
        assertEquals(80.0D, route.get(route.size() - 1).getX(), 1.0E-8D);
        assertEquals(90.0D, route.get(route.size() - 1).getY(), 1.0E-8D);
        assertEquals(40.0D, route.get(route.size() - 1).getZ(), 1.0E-8D);
        long previousTick = -1L;
        for (TrajectorySample sample : route) {
            assertTrue(sample.getTickOffset() > previousTick);
            previousTick = sample.getTickOffset();
        }
    }
}
