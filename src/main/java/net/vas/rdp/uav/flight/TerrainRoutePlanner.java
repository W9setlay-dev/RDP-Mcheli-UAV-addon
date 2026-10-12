package net.vas.rdp.uav.flight;

import net.minecraft.world.World;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.world.ChunkState;
import net.vas.rdp.uav.world.AirspaceObservation;
import net.vas.rdp.uav.world.TerrainObservation;
import net.vas.rdp.uav.world.WorldPerceptionAdapter;

/** Bounded, non-loading terrain lookahead for transit setpoints. */
public final class TerrainRoutePlanner {
    private static final double SAMPLE_SPACING = 8.0D;
    private static final double MIN_LOOKAHEAD = 48.0D;
    private static final double MAX_LOOKAHEAD = 384.0D;
    private static final int MAX_SAMPLES = 49;

    private TerrainRoutePlanner() { }

    public static Plan planTransit(World world, WorldPerceptionAdapter perception,
                                   double aircraftX, double aircraftY, double aircraftZ,
                                   double aircraftSpeed, double targetX, double targetZ,
                                   UavVehicleProfile profile, long tick) {
        return planTransit(world, perception, aircraftX, aircraftY, aircraftZ,
                aircraftSpeed, targetX, aircraftY, targetZ, profile, tick, 0.0D, false);
    }

    public static Plan planTransit(World world, WorldPerceptionAdapter perception,
                                   double aircraftX, double aircraftY, double aircraftZ,
                                   double aircraftSpeed, double targetX, double targetY,
                                   double targetZ, UavVehicleProfile profile, long tick,
                                   double pitchTrimDegrees, boolean terminalCommit) {
        return planTransit(world, perception, aircraftX, aircraftY, aircraftZ,
                aircraftSpeed, targetX, targetY, targetZ, profile, tick,
                pitchTrimDegrees, terminalCommit, Double.NaN);
    }

    public static Plan planTransit(World world, WorldPerceptionAdapter perception,
                                   double aircraftX, double aircraftY, double aircraftZ,
                                   double aircraftSpeed, double targetX, double targetY,
                                   double targetZ, UavVehicleProfile profile, long tick,
                                   double pitchTrimDegrees, boolean terminalCommit,
                                   double requestedCruiseY) {
        if (world == null || perception == null || profile == null
                || !finite(aircraftX, aircraftY, aircraftZ, aircraftSpeed,
                targetX, targetY, targetZ, pitchTrimDegrees)
                || tick < 0L) return Plan.hold(aircraftX, aircraftY, aircraftZ, "INVALID_INPUT");
        double dx = targetX - aircraftX;
        double dz = targetZ - aircraftZ;
        double distance = Math.hypot(dx, dz);
        double lookahead = clamp(Math.max(MIN_LOOKAHEAD, Math.max(0.0D, aircraftSpeed) * 40.0D),
                MIN_LOOKAHEAD, MAX_LOOKAHEAD);
        double evaluatedDistance = Math.min(distance, lookahead);
        int samples = Math.max(1, Math.min(MAX_SAMPLES,
                (int) Math.ceil(evaluatedDistance / SAMPLE_SPACING)));
        double highestSurface = Double.NEGATIVE_INFINITY;
        double lastSafeX = aircraftX;
        double lastSafeZ = aircraftZ;
        boolean terminalBlocked = false;
        for (int i = 0; i <= samples; i++) {
            double along = evaluatedDistance * i / samples;
            double x = distance < 1.0E-6D ? targetX : aircraftX + dx * (along / distance);
            double z = distance < 1.0E-6D ? targetZ : aircraftZ + dz * (along / distance);
            TerrainObservation observation = perception.observeTerrain(world,
                    floor(x), floor(z), tick);
            if (observation.getLoadedState() != ChunkState.LOADED) {
                // Stop at the last known-safe point. The caller keeps current
                // altitude and reevaluates as loaded terrain becomes available.
                if (i == 0) {
                    lastSafeX = aircraftX;
                    lastSafeZ = aircraftZ;
                }
                return Plan.hold(lastSafeX, aircraftY, lastSafeZ,
                        observation.getLoadedState() == ChunkState.UNLOADED
                                ? "CORRIDOR_UNLOADED" : "CORRIDOR_UNKNOWN");
            }
            double requiredClearanceY = observation.getSurfaceY() + 1.0D
                    + (terminalCommit ? 0.5D : profile.getMinimumAgl());
            if (requiredClearanceY > world.getHeight() - 2.0D) {
                return Plan.hold(lastSafeX, aircraftY, lastSafeZ,
                        "MINIMUM_CLEARANCE_EXCEEDS_WORLD_HEIGHT");
            }
            if (i > 0 && !canReachClearance(aircraftY, requiredClearanceY, along,
                    aircraftSpeed, profile.getMaximumClimbRate())) {
                double climbHoldY = Math.min(world.getHeight() - 2.0D,
                        Math.max(aircraftY, requiredClearanceY));
                return Plan.hold(lastSafeX, climbHoldY, lastSafeZ,
                        "CLIMB_ENVELOPE_INFEASIBLE");
            }
            highestSurface = Math.max(highestSurface, observation.getSurfaceY() + 1.0D);
            if (terminalCommit && distance > 1.0D && along < distance - 1.5D) {
                double pathY = aircraftY + (targetY - aircraftY) * (along / distance);
                // Terminal dive may intentionally descend below transit AGL,
                // but the aircraft centre must remain above the local surface.
                if (pathY < observation.getSurfaceY() + 1.5D) {
                    terminalBlocked = true;
                }
            }
            lastSafeX = x;
            lastSafeZ = z;
        }
        if (!Double.isFinite(highestSurface)) {
            return Plan.hold(aircraftX, aircraftY, aircraftZ, "NO_TERRAIN_SAMPLE");
        }
        double verticalTrim = Math.tan(Math.toRadians(clamp(pitchTrimDegrees,
                -profile.getMaximumPitchCorrectionDegrees(),
                profile.getMaximumPitchCorrectionDegrees())))
                * Math.min(32.0D, Math.max(MIN_LOOKAHEAD, evaluatedDistance));
        double routeY;
        if (terminalCommit) {
            if (terminalBlocked) {
                return Plan.hold(lastSafeX, aircraftY, lastSafeZ, "TERMINAL_TERRAIN_BLOCKED");
            }
            routeY = clamp(targetY + verticalTrim, highestSurface + 0.5D,
                    world.getHeight() - 2.0D);
        } else {
            if (Double.isFinite(requestedCruiseY)) {
                if (requestedCruiseY < 1.0D || requestedCruiseY > world.getHeight() - 2.0D) {
                    return Plan.hold(aircraftX, aircraftY, aircraftZ,
                            "CRUISE_ALTITUDE_OUT_OF_WORLD_BOUNDS");
                }
                AltitudeDecision altitude = transitAltitude(highestSurface,
                        world.getHeight(), pitchTrimDegrees, profile, requestedCruiseY);
                if (!altitude.safe) {
                    return Plan.hold(aircraftX, aircraftY, aircraftZ, altitude.reason);
                }
                routeY = altitude.y;
            } else {
                AltitudeDecision altitude = transitAltitude(highestSurface,
                        world.getHeight(), pitchTrimDegrees, profile);
                if (!altitude.safe) {
                    return Plan.hold(aircraftX, aircraftY, aircraftZ, altitude.reason);
                }
                routeY = altitude.y;
            }
        }
        double routeX = distance > lookahead ? aircraftX + dx * (lookahead / distance) : targetX;
        double routeZ = distance > lookahead ? aircraftZ + dz * (lookahead / distance) : targetZ;
        double corridorDistance = Math.hypot(routeX - aircraftX, routeZ - aircraftZ);
        int airspaceSamples = Math.max(1, Math.min(MAX_SAMPLES,
                (int) Math.ceil(corridorDistance / SAMPLE_SPACING)));
        double lastAirspaceX = aircraftX;
        double lastAirspaceZ = aircraftZ;
        for (int i = 0; i <= airspaceSamples; i++) {
            double fraction = (double) i / airspaceSamples;
            double sampleX = aircraftX + (routeX - aircraftX) * fraction;
            double sampleZ = aircraftZ + (routeZ - aircraftZ) * fraction;
            double sampleY = aircraftY + (routeY - aircraftY) * fraction;
            if (terminalCommit && i > 0
                    && corridorDistance - corridorDistance * fraction < 1.5D) {
                // Permit only the final contact envelope. The entire approach
                // corridor remains collision and fluid checked.
                break;
            }
            AirspaceObservation airspace = perception.observeAirspace(world,
                    floor(sampleX), floor(sampleY), floor(sampleZ), 1, tick);
            if (airspace.getState() != ChunkState.LOADED) {
                return Plan.hold(lastAirspaceX, aircraftY, lastAirspaceZ,
                        airspace.getState() == ChunkState.UNLOADED
                                ? "AIRSPACE_UNLOADED" : "AIRSPACE_UNKNOWN");
            }
            if (airspace.hasCollision() || airspace.hasFluid()) {
                return Plan.hold(lastAirspaceX, aircraftY, lastAirspaceZ,
                        airspace.hasFluid() ? "AIRSPACE_FLUID_BLOCKED" : "AIRSPACE_COLLISION_BLOCKED");
            }
            lastAirspaceX = sampleX;
            lastAirspaceZ = sampleZ;
        }
        return Plan.safe(routeX, routeY, routeZ, highestSurface,
                aircraftY - highestSurface, terminalCommit
                        ? "TERRAIN_CHECKED_TERMINAL" : "TERRAIN_CLEAR_TRANSIT");
    }

    private static int floor(double value) { return (int) Math.floor(value); }
    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    /** Conservative climb feasibility check in blocks per simulation tick. */
    public static boolean canReachClearance(double currentY, double requiredY,
                                            double distance, double groundSpeed,
                                            double maximumClimbRate) {
        if (!finite(currentY, requiredY, distance, groundSpeed, maximumClimbRate)
                || distance < 0.0D || groundSpeed < 0.0D || maximumClimbRate < 0.0D) {
            return false;
        }
        double travelTicks = distance / Math.max(0.1D, groundSpeed);
        double reachableY = currentY + maximumClimbRate * travelTicks;
        return requiredY <= reachableY + 1.0E-6D;
    }

    /** Pure AGL/world-height boundary used by route planning and deterministic tests. */
    public static AltitudeDecision transitAltitude(double highestSurface,
                                                   double worldHeight,
                                                   double pitchTrimDegrees,
                                                   UavVehicleProfile profile) {
        return transitAltitude(highestSurface, worldHeight, pitchTrimDegrees,
                profile, Double.NaN);
    }

    public static AltitudeDecision transitAltitude(double highestSurface,
                                                   double worldHeight,
                                                   double pitchTrimDegrees,
                                                   UavVehicleProfile profile,
                                                   double requestedWorldY) {
        if (profile == null || !finite(highestSurface, worldHeight, pitchTrimDegrees)
                || worldHeight <= 1.0D) {
            return new AltitudeDecision(false, Double.NaN, "INVALID_ALTITUDE_INPUT");
        }
        double minimumY = highestSurface + profile.getMinimumAgl();
        double maximumY = Math.min(worldHeight - 2.0D,
                highestSurface + profile.getMaximumAgl());
        if (minimumY >= worldHeight - 1.0D) {
            return new AltitudeDecision(false, Double.NaN,
                    "MINIMUM_AGL_EXCEEDS_WORLD_HEIGHT");
        }
        double verticalTrim = Math.tan(Math.toRadians(clamp(pitchTrimDegrees,
                -profile.getMaximumPitchCorrectionDegrees(),
                profile.getMaximumPitchCorrectionDegrees()))) * 32.0D;
        if (Double.isFinite(requestedWorldY)) {
            if (requestedWorldY < 1.0D || requestedWorldY > worldHeight - 2.0D) {
                return new AltitudeDecision(false, Double.NaN,
                        "CRUISE_ALTITUDE_OUT_OF_WORLD_BOUNDS");
            }
            return new AltitudeDecision(true,
                    clamp(requestedWorldY + verticalTrim, minimumY, worldHeight - 2.0D),
                    requestedWorldY < minimumY ? "TERRAIN_CLEARANCE_OVERRIDES_CRUISE_ALTITUDE"
                            : "ABSOLUTE_WORLD_Y_TRANSIT");
        }
        if (maximumY < minimumY) {
            return new AltitudeDecision(false, Double.NaN,
                    "MINIMUM_AGL_EXCEEDS_WORLD_HEIGHT");
        }
        double cruiseAgl = clamp(profile.getCruiseAgl(), profile.getMinimumAgl(),
                profile.getMaximumAgl());
        double desiredY = clamp(highestSurface + cruiseAgl + verticalTrim, minimumY, maximumY);
        double y = WorkingAltitudePolicy.enforceGroundSafety(profile, desiredY,
                highestSurface, worldHeight);
        return new AltitudeDecision(true, y, "TERRAIN_CLEAR_TRANSIT");
    }

    public static final class AltitudeDecision {
        private final boolean safe;
        private final double y;
        private final String reason;
        private AltitudeDecision(boolean safe, double y, String reason) {
            this.safe = safe; this.y = y; this.reason = reason;
        }
        public boolean isSafe() { return safe; }
        public double getY() { return y; }
        public String getReason() { return reason; }
    }
    public static final class Plan {
        private final boolean safe;
        private final double x, y, z;
        private final double highestSurface;
        private final double currentAgl;
        private final String reason;

        private Plan(boolean safe, double x, double y, double z,
                     double highestSurface, double currentAgl, String reason) {
            this.safe = safe; this.x = x; this.y = y; this.z = z;
            this.highestSurface = highestSurface; this.currentAgl = currentAgl;
            this.reason = reason;
        }
        private static Plan safe(double x, double y, double z, double surface,
                                 double agl, String reason) {
            return new Plan(true, x, y, z, surface, agl, reason);
        }
        private static Plan hold(double x, double y, double z, String reason) {
            return new Plan(false, x, y, z, Double.NaN, Double.NaN, reason);
        }
        public boolean isSafe() { return safe; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public double getHighestSurface() { return highestSurface; }
        public double getCurrentAgl() { return currentAgl; }
        public String getReason() { return reason; }
    }
}
