package net.vas.rdp.uav.weapon;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded, server-side terrain corridor analysis. Results are intentionally
 * coarse and short-lived: native WingMan and McHeli remain responsible for
 * flight and projectile motion.
 */
public final class GuidedTerrainAnalyzer {
    private static final int MAX_CACHE = 256;
    private static final long CACHE_TICKS = 10L;
    private static final int MAX_SAMPLES = 32;
    private static final Map<Key, Analysis> CACHE = new LinkedHashMap<Key, Analysis>(MAX_CACHE, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Analysis> eldest) {
            return size() > MAX_CACHE;
        }

    };

    private GuidedTerrainAnalyzer() { }

    public static synchronized Analysis analyzeDetailed(World world, Entity aircraft,
                                                        GuidedStrikeObjective objective,
                                                        GuidedTerminalProfile profile) {
        if (world == null || aircraft == null || objective == null || profile == null) {
            return Analysis.rejected();
        }
        long bucket = world.getTotalWorldTime() / CACHE_TICKS;
        Key key = new Key(world.provider.getDimension(), bucket,
                floor(aircraft.posX), floor(aircraft.posY), floor(aircraft.posZ),
                floor(objective.getX()), floor(objective.getY()), floor(objective.getZ()),
                profile.getMinimumAltitude(), profile.getMaximumAltitude());
        Analysis cached = CACHE.get(key);
        if (cached != null) return cached;

        double dx = objective.getX() - aircraft.posX;
        double dz = objective.getZ() - aircraft.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        int samples = Math.max(1, Math.min(MAX_SAMPLES, (int) Math.ceil(horizontal / 16.0D)));
        for (int i = 0; i <= samples; i++) {
            double fraction = (double) i / (double) samples;
            double x = aircraft.posX + dx * fraction;
            double z = aircraft.posZ + dz * fraction;
            double routeY = aircraft.posY + (objective.getY() - aircraft.posY) * fraction;
            BlockPos column = new BlockPos(x, objective.getY(), z);
            if (!world.isBlockLoaded(column)) {
                Analysis result = new Analysis(Result.UNLOADED, "COLUMN_UNLOADED",
                        i, samples, x, z, routeY, Double.NaN, Double.NaN);
                CACHE.put(key, result);
                return result;
            }
            int top = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)).getY();
            double clearance = routeY - top;
            boolean terminal = i == samples;
            if (objective.getY() < profile.getMinimumAltitude()
                    || objective.getY() > profile.getMaximumAltitude()) {
                Analysis result = new Analysis(Result.BLOCKED, "ALTITUDE_ENVELOPE",
                        i, samples, x, z, routeY, top, clearance);
                CACHE.put(key, result);
                return result;
            }
            if (!terminal && clearance < 2.0D) {
                Analysis result = new Analysis(Result.BLOCKED, "INSUFFICIENT_CLEARANCE",
                        i, samples, x, z, routeY, top, clearance);
                CACHE.put(key, result);
                return result;
            }
        }
        Analysis result = new Analysis(Result.CLEAR, "CLEAR",
                samples, samples, objective.getX(), objective.getZ(),
                objective.getY(), Double.NaN, Double.NaN);
        CACHE.put(key, result);
        return result;
    }

    public static synchronized Result analyze(World world, Entity aircraft,
                                               GuidedStrikeObjective objective,
                                               GuidedTerminalProfile profile) {
        return analyzeDetailed(world, aircraft, objective, profile).getResult();
    }

    public static synchronized void clear() { CACHE.clear(); }

    private static int floor(double value) { return (int) Math.floor(value); }

    public enum Result {
        CLEAR, BLOCKED, UNLOADED, REJECTED
    }

    public static final class Analysis {
        private final Result result;
        private final String reason;
        private final int sample;
        private final int sampleCount;
        private final double x, z, routeY, terrainTop, clearance;

        private Analysis(Result result, String reason, int sample, int sampleCount,
                         double x, double z, double routeY, double terrainTop, double clearance) {
            this.result = result;
            this.reason = reason;
            this.sample = sample;
            this.sampleCount = sampleCount;
            this.x = x;
            this.z = z;
            this.routeY = routeY;
            this.terrainTop = terrainTop;
            this.clearance = clearance;
        }

        private static Analysis rejected() {
            return new Analysis(Result.REJECTED, "INVALID_INPUT", 0, 0,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }

        public Result getResult() { return result; }
        public String getReason() { return reason; }
        public int getSample() { return sample; }
        public int getSampleCount() { return sampleCount; }
        public double getX() { return x; }
        public double getZ() { return z; }
        public double getRouteY() { return routeY; }
        public double getTerrainTop() { return terrainTop; }
        public double getClearance() { return clearance; }
    }

    private static final class Key {
        private final int dimension, ax, ay, az, tx, ty, tz;
        private final long bucket;
        private final double minAltitude, maxAltitude;
        private Key(int dimension, long bucket, int ax, int ay, int az, int tx, int ty, int tz,
                    double minAltitude, double maxAltitude) {
            this.dimension = dimension; this.bucket = bucket;
            this.ax = ax; this.ay = ay; this.az = az;
            this.tx = tx; this.ty = ty; this.tz = tz;
            this.minAltitude = minAltitude; this.maxAltitude = maxAltitude;
        }
        @Override public int hashCode() {
            int result = dimension;
            result = 31 * result + Long.hashCode(bucket);
            result = 31 * result + ax; result = 31 * result + ay; result = 31 * result + az;
            result = 31 * result + tx; result = 31 * result + ty; result = 31 * result + tz;
            result = 31 * result + Double.valueOf(minAltitude).hashCode();
            return 31 * result + Double.valueOf(maxAltitude).hashCode();
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return dimension == key.dimension && bucket == key.bucket
                    && ax == key.ax && ay == key.ay && az == key.az
                    && tx == key.tx && ty == key.ty && tz == key.tz
                    && minAltitude == key.minAltitude && maxAltitude == key.maxAltitude;
        }
    }
}
