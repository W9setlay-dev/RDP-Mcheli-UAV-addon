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
        return route(centerX, centerZ, radius, SearchPattern.GRID);
    }

    public static List<MissionWaypoint> route(double centerX, double centerZ,
                                              double radius, SearchPattern pattern) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)
                || !Double.isFinite(radius) || radius < 4.0D || radius > 128.0D
                || pattern == null || pattern == SearchPattern.CUSTOM) {
            throw new IllegalArgumentException("Search center and radius are invalid");
        }
        List<MissionWaypoint> waypoints = new ArrayList<>(8);
        switch (pattern) {
            case GRID:
                double laneSpacing = radius * 2.0D / 3.0D;
                for (int lane = 0; lane < 4; lane++) {
                    double x = centerX - radius + laneSpacing * lane;
                    double firstZ = lane % 2 == 0 ? centerZ - radius : centerZ + radius;
                    double secondZ = lane % 2 == 0 ? centerZ + radius : centerZ - radius;
                    waypoints.add(new MissionWaypoint(x, firstZ, 0));
                    waypoints.add(new MissionWaypoint(x, secondZ, 0));
                }
                break;
            case LANE:
                for (int lane = 0; lane < 3; lane++) {
                    double z = centerZ - radius + lane * radius;
                    waypoints.add(new MissionWaypoint(centerX - radius, z, 0));
                    waypoints.add(new MissionWaypoint(centerX + radius, z, 0));
                }
                break;
            case SPIRAL:
                waypoints.add(new MissionWaypoint(centerX, centerZ, 0));
                waypoints.add(new MissionWaypoint(centerX - radius * 0.35D,
                        centerZ - radius * 0.35D, 0));
                waypoints.add(new MissionWaypoint(centerX + radius * 0.35D,
                        centerZ - radius * 0.35D, 0));
                waypoints.add(new MissionWaypoint(centerX + radius * 0.35D,
                        centerZ + radius * 0.35D, 0));
                waypoints.add(new MissionWaypoint(centerX - radius * 0.35D,
                        centerZ + radius * 0.35D, 0));
                waypoints.add(new MissionWaypoint(centerX - radius,
                        centerZ - radius, 0));
                waypoints.add(new MissionWaypoint(centerX + radius,
                        centerZ - radius, 0));
                waypoints.add(new MissionWaypoint(centerX + radius,
                        centerZ + radius, 0));
                break;
            case RING:
                for (int point = 0; point < 8; point++) {
                    double angle = -Math.PI / 2D + point * Math.PI / 4D;
                    waypoints.add(new MissionWaypoint(centerX + Math.cos(angle) * radius,
                            centerZ + Math.sin(angle) * radius, 0));
                }
                break;
            case SECTOR:
                waypoints.add(new MissionWaypoint(centerX, centerZ, 0));
                for (int point = 0; point < 7; point++) {
                    double angle = -Math.PI / 3D + point * Math.PI / 9D;
                    waypoints.add(new MissionWaypoint(centerX + Math.cos(angle) * radius,
                            centerZ + Math.sin(angle) * radius, 0));
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported cooperative search pattern");
        }
        PatrolRouteCodec.validate(waypoints);
        return Collections.unmodifiableList(waypoints);
    }
}
