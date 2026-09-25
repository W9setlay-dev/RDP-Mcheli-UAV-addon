package net.vas.rdp.uav.flight;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.wingman.wingman.WingmanEntry;
import net.vas.rdp.uav.mission.ControlOwner;
import net.vas.rdp.uav.mission.MissionMovementAuthority;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.target.TargetLock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Captures bounded, observed aircraft state after native updates. */
public final class AircraftStateEstimator {
    private final Map<UUID, AircraftState> lastStates = new HashMap<>();

    public AircraftState observe(MCH_EntityAircraft aircraft, WingmanEntry entry,
                                 RDPMission mission, long tick) {
        if (aircraft == null || tick < 0L) {
            throw new IllegalArgumentException("Aircraft and non-negative tick are required");
        }
        UUID id = aircraft.getUniqueID();
        AircraftState previous = lastStates.get(id);
        double previousX = previous == null ? aircraft.posX : previous.getX();
        double previousY = previous == null ? aircraft.posY : previous.getY();
        double previousZ = previous == null ? aircraft.posZ : previous.getZ();
        int routeNode = entry == null ? -1 : entry.missionIndex;
        String mode = entry == null || entry.autoState == null
                ? "UNKNOWN" : entry.autoState.name();
        ControlOwner owner = mission == null ? null : mission.getControlOwner();
        UUID missionId = mission == null ? null : mission.getMissionId();
        boolean leaseValid = MissionMovementAuthority.canIssueNativeControl(mission, id, tick);
        TargetLock lock = mission == null ? null : mission.getTargetLock();
        double targetDistance = lock == null ? Double.NaN : distance(aircraft.posX, aircraft.posY,
                aircraft.posZ, lock.getX(), lock.getY(), lock.getZ());
        double waypointDistance = distanceToNode(aircraft, entry);
        double routeProgress = routeNode < 0 ? (previous == null ? 0.0D : previous.getRouteProgress())
                : (double) routeNode;
        long progressTick = previous == null ? tick
                : RouteProgressTracker.evaluate(
                        tick,
                        previous.getRouteProgress(),
                        routeProgress,
                        previous.getDistanceToTarget(),
                        targetDistance,
                        previous.getDistanceToWaypoint(),
                        waypointDistance,
                        previous.getLastMeaningfulProgressTick(),
                        0.5D,
                        200L).getLastMeaningfulProgressTick();
        double sampleInterval = previous == null
                ? 1.0D : Math.max(1.0D, tick - previous.getSampleTick());
        boolean hasPhysicalMotion = Double.isFinite(aircraft.motionX)
                && Double.isFinite(aircraft.motionY)
                && Double.isFinite(aircraft.motionZ);
        String source = hasPhysicalMotion ? "PHYSICAL_MOTION" : "POSITION_DELTA";
        String validityReason = hasPhysicalMotion ? "OK" : "MOTION_UNAVAILABLE";
        double confidence = hasPhysicalMotion ? 0.92D : 0.68D;
        AircraftState current = AircraftState.observeWithPhysicalMotion(
                id,
                aircraft.dimension, tick, aircraft.posX, aircraft.posY, aircraft.posZ,
                previousX, previousY, previousZ, aircraft.motionX, aircraft.motionY,
                aircraft.motionZ, sampleInterval, aircraft.getYaw(), aircraft.getPitch(),
                aircraft.getRoll(), aircraft.getThrottle(), mode, missionId, owner,
                leaseValid, routeNode, routeProgress,
                waypointDistance, targetDistance, 0.0D, progressTick,
                "UNKNOWN", source, "BLOCKS_PER_TICK", confidence, validityReason);
        lastStates.put(id, current);
        return current;
    }

    public AircraftState get(UUID aircraftId) {
        return aircraftId == null ? null : lastStates.get(aircraftId);
    }

    public void remove(UUID aircraftId) {
        if (aircraftId != null) lastStates.remove(aircraftId);
    }

    private static double distanceToNode(MCH_EntityAircraft aircraft, WingmanEntry entry) {
        if (entry == null || entry.currentNode() == null) return Double.NaN;
        double dx = entry.currentNode().x - aircraft.posX;
        double dy = entry.currentNode().y - aircraft.posY;
        double dz = entry.currentNode().z - aircraft.posZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double distance(double x1, double y1, double z1,
                                   double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
