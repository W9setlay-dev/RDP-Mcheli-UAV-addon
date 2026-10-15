package net.vas.rdp.uav.path;

/** Immutable path-planning waypoint; heading is Minecraft yaw in degrees. */
public final class Waypoint {
    private final double x;
    private final double y;
    private final double z;
    private final double heading;

    /** Creates a finite waypoint. */
    public Waypoint(double x, double y, double z, double heading) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(heading)) {
            throw new IllegalArgumentException("Waypoint coordinates must be finite");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.heading = heading;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getHeading() { return heading; }
}
