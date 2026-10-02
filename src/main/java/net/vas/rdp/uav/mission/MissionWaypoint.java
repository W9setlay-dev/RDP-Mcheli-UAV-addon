package net.vas.rdp.uav.mission;

/** A ground-relative patrol point with an optional bounded loiter. */
public final class MissionWaypoint {
    public static final int MAX_LOITER_TICKS = 20 * 60 * 10;

    private final double x;
    private final double z;
    private final int loiterTicks;

    public MissionWaypoint(double x, double z, int loiterTicks) {
        if (!Double.isFinite(x) || !Double.isFinite(z)
                || loiterTicks < 0 || loiterTicks > MAX_LOITER_TICKS) {
            throw new IllegalArgumentException("Invalid patrol waypoint");
        }
        this.x = x;
        this.z = z;
        this.loiterTicks = loiterTicks;
    }

    public double getX() { return x; }
    public double getZ() { return z; }
    public int getLoiterTicks() { return loiterTicks; }
}
