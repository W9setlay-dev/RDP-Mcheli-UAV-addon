package net.vas.rdp.uav.swarm;

import net.vas.rdp.uav.mission.MissionWaypoint;
import net.vas.rdp.uav.mission.PatrolRouteCodec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Creates a bounded serpentine route for one swarm target-loss searcher. */
public final class SwarmSearchRoutePlanner {
    private SwarmSearchRoutePlanner() { }

    public static List<MissionWaypoint> route(double centerX, double centerZ,
                                              double radius) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)
                || !Double.isFinite(radius) || radius < 4.0D || radius > 128.0D) {
            throw new IllegalArgumentException("Search center and radius are invalid");
        }
        List<MissionWaypoint> waypoints = new ArrayList<>(8);
        double laneSpacing = radius * 2.0D / 3.0D;
        for (int lane = 0; lane < 4; lane++) {
            double x = centerX - radius + laneSpacing * lane;
            double firstZ = lane % 2 == 0 ? centerZ - radius : centerZ + radius;
            double secondZ = lane % 2 == 0 ? centerZ + radius : centerZ - radius;
            waypoints.add(new MissionWaypoint(x, firstZ, 0));
            waypoints.add(new MissionWaypoint(x, secondZ, 0));
        }
        PatrolRouteCodec.validate(waypoints);
        return Collections.unmodifiableList(waypoints);
    }
}
