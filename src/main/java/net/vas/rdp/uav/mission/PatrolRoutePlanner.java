package net.vas.rdp.uav.mission;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.vas.rdp.uav.flight.TerrainRoutePlanner;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.world.AirspaceObservation;
import net.vas.rdp.uav.world.ChunkState;
import net.vas.rdp.uav.world.MinecraftWorldPerceptionAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Validates finite patrol legs without loading chunks and computes terrain-relative altitudes. */
public final class PatrolRoutePlanner {
    private PatrolRoutePlanner() { }

    public static Plan plan(World world, MCH_EntityAircraft aircraft,
                            List<MissionWaypoint> route,
                            UavVehicleProfile profile, long tick) {
        if (world == null || aircraft == null || profile == null
                || aircraft.world != world || tick < 0L) {
            return Plan.rejected("INVALID_ROUTE_CONTEXT");
        }
        try {
            PatrolRouteCodec.validate(route);
        } catch (IllegalArgumentException invalid) {
            return Plan.rejected(invalid.getMessage());
        }

        List<FlightWaypoint> result = new ArrayList<>(route.size());
        double fromX = aircraft.posX;
        double fromY = aircraft.posY;
        double fromZ = aircraft.posZ;
        double speed = Math.hypot(aircraft.motionX, aircraft.motionZ);
        double totalDistance = 0.0D;
        MinecraftWorldPerceptionAdapter perception = new MinecraftWorldPerceptionAdapter();
        for (MissionWaypoint waypoint : route) {
            double distance = Math.hypot(waypoint.getX() - fromX, waypoint.getZ() - fromZ);
            if (distance > PatrolRouteCodec.MAX_LEG_DISTANCE) {
                return Plan.rejected("PATROL_LEG_EXCEEDS_384_BLOCKS");
            }
            totalDistance += distance;
            if (totalDistance > PatrolRouteCodec.MAX_ROUTE_DISTANCE) {
                return Plan.rejected("PATROL_ROUTE_EXCEEDS_2048_BLOCKS");
            }
            BlockPos endpoint = new BlockPos(waypoint.getX(), 0, waypoint.getZ());
            if (!world.getWorldBorder().contains(endpoint)) {
                return Plan.rejected("PATROL_WAYPOINT_OUTSIDE_WORLD_BORDER");
            }
            int terrainSteps = Math.max(1, (int) Math.ceil(distance / 32.0D));
            double safeY = fromY;
            double segmentStartX = fromX;
            double segmentStartY = fromY;
            double segmentStartZ = fromZ;
            for (int i = 1; i <= terrainSteps; i++) {
                double fraction = (double) i / terrainSteps;
                double sampleX = fromX + (waypoint.getX() - fromX) * fraction;
                double sampleZ = fromZ + (waypoint.getZ() - fromZ) * fraction;
                TerrainRoutePlanner.Plan terrain = TerrainRoutePlanner.planTransit(
                        world, perception, segmentStartX, segmentStartY, segmentStartZ,
                        speed, sampleX, sampleZ, profile, tick);
                if (!terrain.isSafe()) return Plan.rejected(terrain.getReason());
                safeY = Math.max(safeY, terrain.getY());
                segmentStartX = sampleX;
                segmentStartY = safeY;
                segmentStartZ = sampleZ;
            }
            int airspaceSteps = Math.max(1, (int) Math.ceil(distance / 8.0D));
            for (int i = 0; i <= airspaceSteps; i++) {
                double fraction = (double) i / airspaceSteps;
                double sampleX = fromX + (waypoint.getX() - fromX) * fraction;
                double sampleZ = fromZ + (waypoint.getZ() - fromZ) * fraction;
                AirspaceObservation airspace = perception.observeAirspace(world,
                        (int) Math.floor(sampleX), (int) Math.floor(safeY),
                        (int) Math.floor(sampleZ), 1, tick);
                if (airspace.getState() != ChunkState.LOADED) {
                    return Plan.rejected(airspace.getState() == ChunkState.UNLOADED
                            ? "PATROL_AIRSPACE_UNLOADED" : "PATROL_AIRSPACE_UNKNOWN");
                }
                if (airspace.hasCollision() || airspace.hasFluid()) {
                    return Plan.rejected(airspace.hasFluid()
                            ? "PATROL_AIRSPACE_FLUID_BLOCKED" : "PATROL_AIRSPACE_COLLISION_BLOCKED");
                }
            }
            result.add(new FlightWaypoint(waypoint.getX(), safeY,
                    waypoint.getZ(), waypoint.getLoiterTicks()));
            fromX = waypoint.getX();
            fromY = safeY;
            fromZ = waypoint.getZ();
        }
        return Plan.accepted(result);
    }

    public static final class FlightWaypoint {
        private final double x;
        private final double y;
        private final double z;
        private final int loiterTicks;

        private FlightWaypoint(double x, double y, double z, int loiterTicks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.loiterTicks = loiterTicks;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public int getLoiterTicks() { return loiterTicks; }
    }

    public static final class Plan {
        private final boolean accepted;
        private final String reason;
        private final List<FlightWaypoint> waypoints;

        private Plan(boolean accepted, String reason, List<FlightWaypoint> waypoints) {
            this.accepted = accepted;
            this.reason = reason;
            this.waypoints = Collections.unmodifiableList(new ArrayList<>(waypoints));
        }

        private static Plan accepted(List<FlightWaypoint> waypoints) {
            return new Plan(true, "PATROL_ROUTE_TERRAIN_VALIDATED", waypoints);
        }

        private static Plan rejected(String reason) {
            return new Plan(false, reason, Collections.emptyList());
        }

        public boolean isAccepted() { return accepted; }
        public String getReason() { return reason; }
        public List<FlightWaypoint> getWaypoints() { return waypoints; }
    }
}
