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
    private final double motionX;
    private final double motionY;
    private final double motionZ;
    private final double sampleIntervalTicks;
    private final double groundTrackYaw;
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
    private final String source;
    private final String units;
    private final double confidence;
    private final String validityReason;

    private AircraftState(UUID aircraftId, int dimension, long sampleTick,
                          double x, double y, double z,
                          double previousX, double previousY, double previousZ,
                          double velocityX, double velocityY, double velocityZ,
                          double motionX, double motionY, double motionZ,
                          double sampleIntervalTicks, double groundTrackYaw,
                          float yaw, float pitch, float roll, double throttle,
                          String flightMode, UUID missionId, ControlOwner controlOwner,
                          boolean controlLeaseValid, int routeNode, double routeProgress,
                          double distanceToWaypoint, double distanceToTarget,
                          double distanceToIntercept, long lastMeaningfulProgressTick,
                          String terrainKnowledgeState, String source,
                          String units, double confidence, String validityReason) {
        if (aircraftId == null || sampleTick < 0L || finite(x, y, z, previousX, previousY,
                previousZ, velocityX, velocityY, velocityZ, motionX, motionY, motionZ,
                sampleIntervalTicks, throttle, distanceToWaypoint, distanceToTarget,
                distanceToIntercept, routeProgress)
                || (!Double.isFinite(groundTrackYaw) && !Double.isNaN(groundTrackYaw))
                || routeNode < -1 || lastMeaningfulProgressTick < 0L) {
            throw new IllegalArgumentException("Invalid aircraft state");
        }
        this.aircraftId = aircraftId;
        this.dimension = dimension;
        this.sampleTick = sampleTick;
        this.x = x; this.y = y; this.z = z;
        this.previousX = previousX; this.previousY = previousY; this.previousZ = previousZ;
        this.velocityX = velocityX; this.velocityY = velocityY; this.velocityZ = velocityZ;
        this.motionX = motionX; this.motionY = motionY; this.motionZ = motionZ;
        this.sampleIntervalTicks = sampleIntervalTicks;
        this.groundTrackYaw = groundTrackYaw;
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
        this.source = source == null || source.trim().isEmpty() ? "UNKNOWN" : source.trim();
        this.units = units == null || units.trim().isEmpty() ? "BLOCKS_PER_TICK" : units.trim();
        this.confidence = clampConfidence(confidence);
        this.validityReason = validityReason == null || validityReason.trim().isEmpty()
                ? "OK" : validityReason.trim();
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
        return observe(aircraftId, dimension, tick, x, y, z, previousX, previousY,
                previousZ, yaw, pitch, roll, throttle, flightMode, missionId,
                controlOwner, leaseValid, routeNode, routeProgress,
                distanceToWaypoint, distanceToTarget, distanceToIntercept,
                lastMeaningfulProgressTick, terrainKnowledgeState,
                "POSITION_DELTA", "BLOCKS_PER_TICK", 0.65D, "OK");
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
                                        String terrainKnowledgeState, String source,
                                        String units, double confidence, String validityReason) {
        long elapsed = 1L;
        double velocityX = (x - previousX) / elapsed;
        double velocityY = (y - previousY) / elapsed;
        double velocityZ = (z - previousZ) / elapsed;
        return new AircraftState(aircraftId, dimension, tick, x, y, z,
                previousX, previousY, previousZ, velocityX, velocityY, velocityZ,
                0.0D, 0.0D, 0.0D, 1.0D,
                groundTrackYaw(velocityX, velocityZ),
                yaw, pitch, roll, throttle, flightMode, missionId, controlOwner,
                leaseValid, routeNode, routeProgress, distanceToWaypoint,
                distanceToTarget, distanceToIntercept, lastMeaningfulProgressTick,
                terrainKnowledgeState, source, units, confidence, validityReason);
    }

    public static AircraftState observeWithPhysicalMotion(
            UUID aircraftId, int dimension, long tick,
            double x, double y, double z,
            double previousX, double previousY, double previousZ,
            double motionX, double motionY, double motionZ,
            double sampleIntervalTicks,
            float yaw, float pitch, float roll, double throttle,
            String flightMode, UUID missionId, ControlOwner controlOwner,
            boolean leaseValid, int routeNode, double routeProgress,
            double distanceToWaypoint, double distanceToTarget,
            double distanceToIntercept, long lastMeaningfulProgressTick,
            String terrainKnowledgeState) {
        return observeWithPhysicalMotion(aircraftId, dimension, tick, x, y, z,
                previousX, previousY, previousZ, motionX, motionY, motionZ,
                sampleIntervalTicks, yaw, pitch, roll, throttle, flightMode,
                missionId, controlOwner, leaseValid, routeNode, routeProgress,
                distanceToWaypoint, distanceToTarget, distanceToIntercept,
                lastMeaningfulProgressTick, terrainKnowledgeState,
                "PHYSICAL_MOTION", "BLOCKS_PER_TICK", 0.9D, "OK");
    }

    public static AircraftState observeWithPhysicalMotion(
            UUID aircraftId, int dimension, long tick,
            double x, double y, double z,
            double previousX, double previousY, double previousZ,
            double motionX, double motionY, double motionZ,
            double sampleIntervalTicks,
            float yaw, float pitch, float roll, double throttle,
            String flightMode, UUID missionId, ControlOwner controlOwner,
            boolean leaseValid, int routeNode, double routeProgress,
            double distanceToWaypoint, double distanceToTarget,
            double distanceToIntercept, long lastMeaningfulProgressTick,
            String terrainKnowledgeState, String source,
            String units, double confidence, String validityReason) {
        if (finite(motionX, motionY, motionZ, sampleIntervalTicks)
                || sampleIntervalTicks <= 0.0D) {
            throw new IllegalArgumentException("Invalid physical aircraft motion");
        }
        double velocityX = (x - previousX) / sampleIntervalTicks;
        double velocityY = (y - previousY) / sampleIntervalTicks;
        double velocityZ = (z - previousZ) / sampleIntervalTicks;
        return new AircraftState(aircraftId, dimension, tick, x, y, z,
                previousX, previousY, previousZ, velocityX, velocityY, velocityZ,
                motionX, motionY, motionZ, sampleIntervalTicks,
                groundTrackYaw(motionX, motionZ),
                yaw, pitch, roll, throttle, flightMode, missionId, controlOwner,
                leaseValid, routeNode, routeProgress, distanceToWaypoint,
                distanceToTarget, distanceToIntercept, lastMeaningfulProgressTick,
                terrainKnowledgeState, source, units, confidence, validityReason);
    }

    private static double clampConfidence(double value) {
        if (!Double.isFinite(value)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double groundTrackYaw(double velocityX, double velocityZ) {
        if (Math.abs(velocityX) < 1.0E-9D && Math.abs(velocityZ) < 1.0E-9D) {
            return Double.NaN;
        }
        double degrees = Math.toDegrees(Math.atan2(-velocityX, velocityZ));
        return degrees < 0.0D ? degrees + 360.0D : degrees;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return true;
        return false;
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
    public double getMotionX() { return motionX; }
    public double getMotionY() { return motionY; }
    public double getMotionZ() { return motionZ; }
    public double getSampleIntervalTicks() { return sampleIntervalTicks; }
    public double getGroundTrackYaw() { return groundTrackYaw; }
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
    public String getSource() { return source; }
    public String getUnits() { return units; }
    public double getConfidence() { return confidence; }
    public String getValidityReason() { return validityReason; }
}
