package net.vas.rdp.uav.flight;

/**
 * Immutable geometric guidance result. It is not a native actuator command.
 */
public final class GuidanceVector {
    private final double x;
    private final double y;
    private final double z;
    private final double distance;

    public GuidanceVector(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Guidance vector must be finite");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.distance = Math.sqrt(x * x + y * y + z * z);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getDistance() { return distance; }
}
