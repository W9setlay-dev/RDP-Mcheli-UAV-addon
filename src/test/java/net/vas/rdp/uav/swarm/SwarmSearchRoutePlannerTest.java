package net.vas.rdp.uav.swarm;

import net.vas.rdp.uav.mission.MissionWaypoint;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SwarmSearchRoutePlannerTest {
    @Test
    public void buildsBoundedSerpentineRouteAroundLastKnownPosition() {
        List<MissionWaypoint> route =
                SwarmSearchRoutePlanner.route(100.0D, -50.0D, 24.0D);
        assertEquals(8, route.size());
        assertEquals(76.0D, route.get(0).getX(), 0.001D);
        assertEquals(-74.0D, route.get(0).getZ(), 0.001D);
        assertEquals(-26.0D, route.get(1).getZ(), 0.001D);

        double distance = 0.0D;
        MissionWaypoint previous = null;
        for (MissionWaypoint waypoint : route) {
            assertTrue(Math.abs(waypoint.getX() - 100.0D) <= 24.0D);
            assertTrue(Math.abs(waypoint.getZ() + 50.0D) <= 24.0D);
            if (previous != null) {
                double leg = Math.hypot(waypoint.getX() - previous.getX(),
                        waypoint.getZ() - previous.getZ());
                assertTrue(leg <= 384.0D);
                distance += leg;
            }
            previous = waypoint;
        }
        assertTrue(distance <= 2048.0D);
    }

    @Test
    public void rejectsInvalidSearchRadius() {
        try {
            SwarmSearchRoutePlanner.route(0.0D, 0.0D, 129.0D);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Out-of-policy search radius was accepted");
    }

    @Test
    public void maximumConfiguredRadiusStillFitsMissionRouteLimits() {
        List<MissionWaypoint> route =
                SwarmSearchRoutePlanner.route(0.0D, 0.0D, 128.0D);
        double distance = 0.0D;
        MissionWaypoint previous = null;
        for (MissionWaypoint waypoint : route) {
            if (previous != null) {
                distance += Math.hypot(waypoint.getX() - previous.getX(),
                        waypoint.getZ() - previous.getZ());
            }
            previous = waypoint;
        }
        assertTrue(distance <= 2048.0D);
    }
}
