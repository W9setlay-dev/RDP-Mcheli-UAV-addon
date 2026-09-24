package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.mission.ControlOwner;

import java.util.UUID;

/**
 * Immutable server observation captured after the native aircraft update.
 * This is telemetry and controller input, never a movement command.
 */
public final class AircraftState {
    private final UUID aircraftId;
    private final int dimension;
    private final long sampleTick;
    private final double x;
    private final double y;
    private final double z;
    private final double previousX;
    private final double previousY;
    private final double previousZ;
    private final double velocityX;
    private final double velocityY;
    private final double velocityZ;
    private final double horizontalSpeed;
    private final double verticalSpeed;
    private final float yaw;
    private final float pitch;
    private final float roll;
    private final double throttle;
    private final String flightMode;
    private final UUID missionId;
    private final ControlOwner controlOwner;
    private final boolean controlLeaseValid;
    private final int routeNode;
    private final double routeProgress;
    private final double distanceToWaypoint;
    private final double distanceToTarget;
    private final double distanceToIntercept;
    private final long lastMeaningfulProgressTick;
    private final String terrainKnowledgeState;

    private AircraftState(UUID aircraftId, int dimension, long sampleTick,
                          double x, double y, double z,
                          double previousX, double previousY, double previousZ,
                          double velocityX, double velocityY, double velocityZ,
                          float yaw, float pitch, float roll, double throttle,
                          String flightMode, UUID missionId, ControlOwner controlOwner,
                          boolean controlLeaseValid, int routeNode, double routeProgress,
                          double distanceToWaypoint, double distanceToTarget,
                          double distanceToIntercept, long lastMeaningfulProgressTick,
                          String terrainKnowledgeState) {
        if (aircraftId == null || sampleTick < 0L || !finite(x, y, z, previousX, previousY,
                previousZ, velocityX, velocityY, velocityZ, throttle, distanceToWaypoint,
                distanceToTarget, distanceToIntercept, routeProgress)
                || routeNode < -1 || lastMeaningfulProgressTick < 0L) {
            throw new IllegalArgumentException("Invalid aircraft state");
        }
        this.aircraftId = aircraftId;
        this.dimension = dimension;
        this.sampleTick = sampleTick;
        this.x = x; this.y = y; this.z = z;
        this.previousX = previousX; this.previousY = previousY; this.previousZ = previousZ;
        this.velocityX = velocityX; this.velocityY = velocityY; this.velocityZ = velocityZ;
        this.horizontalSpeed = Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        this.verticalSpeed = velocityY;
        this.yaw = yaw; this.pitch = pitch; this.roll = roll;
        this.throttle = throttle;
        this.flightMode = flightMode == null ? "UNKNOWN" : flightMode;
        this.missionId = missionId;
        this.controlOwner = controlOwner;
        this.controlLeaseValid = controlLeaseValid;
        this.routeNode = routeNode;
        this.routeProgress = routeProgress;
        this.distanceToWaypoint = distanceToWaypoint;
        this.distanceToTarget = distanceToTarget;
        this.distanceToIntercept = distanceToIntercept;
        this.lastMeaningfulProgressTick = lastMeaningfulProgressTick;
        this.terrainKnowledgeState = terrainKnowledgeState == null
                ? "UNKNOWN" : terrainKnowledgeState;
    }

    public static AircraftState observe(UUID aircraftId, int dimension, long tick,
                                        double x, double y, double z,
                                        double previousX, double previousY, double previousZ,
                                        float yaw, float pitch, float roll, double throttle,
                                        String flightMode, UUID missionId,
                                        ControlOwner controlOwner, boolean leaseValid,
                                        int routeNode, double routeProgress,
                                        double distanceToWaypoint, double distanceToTarget,
                                        double distanceToIntercept,
                                        long lastMeaningfulProgressTick,
                                        String terrainKnowledgeState) {
        long elapsed = 1L;
        double velocityX = (x - previousX) / elapsed;
        double velocityY = (y - previousY) / elapsed;
        double velocityZ = (z - previousZ) / elapsed;
        return new AircraftState(aircraftId, dimension, tick, x, y, z,
                previousX, previousY, previousZ, velocityX, velocityY, velocityZ,
                yaw, pitch, roll, throttle, flightMode, missionId, controlOwner,
                leaseValid, routeNode, routeProgress, distanceToWaypoint,
                distanceToTarget, distanceToIntercept, lastMeaningfulProgressTick,
                terrainKnowledgeState);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    public UUID getAircraftId() { return aircraftId; }
    public int getDimension() { return dimension; }
    public long getSampleTick() { return sampleTick; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getPreviousX() { return previousX; }
    public double getPreviousY() { return previousY; }
    public double getPreviousZ() { return previousZ; }
    public double getVelocityX() { return velocityX; }
    public double getVelocityY() { return velocityY; }
    public double getVelocityZ() { return velocityZ; }
    public double getHorizontalSpeed() { return horizontalSpeed; }
    public double getVerticalSpeed() { return verticalSpeed; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public float getRoll() { return roll; }
    public double getThrottle() { return throttle; }
    public String getFlightMode() { return flightMode; }
    public UUID getMissionId() { return missionId; }
    public ControlOwner getControlOwner() { return controlOwner; }
    public boolean isControlLeaseValid() { return controlLeaseValid; }
    public int getRouteNode() { return routeNode; }
    public double getRouteProgress() { return routeProgress; }
    public double getDistanceToWaypoint() { return distanceToWaypoint; }
    public double getDistanceToTarget() { return distanceToTarget; }
    public double getDistanceToIntercept() { return distanceToIntercept; }
    public long getLastMeaningfulProgressTick() { return lastMeaningfulProgressTick; }
    public String getTerrainKnowledgeState() { return terrainKnowledgeState; }
}
