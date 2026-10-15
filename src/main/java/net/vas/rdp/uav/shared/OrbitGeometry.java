package net.vas.rdp.uav.shared;

/** Immutable center, radius, and altitude of an orbit in world coordinates. */
public final class OrbitGeometry {
    private final double centerX;
    private final double centerZ;
    private final double radius;
    private final double altitude;

    /** Creates orbit geometry with a positive finite radius. */
    public OrbitGeometry(double centerX, double centerZ, double radius, double altitude) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)
                || !Double.isFinite(radius) || radius <= 0.0D
                || !Double.isFinite(altitude)) {
            throw new IllegalArgumentException("Invalid orbit geometry");
        }
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.altitude = altitude;
    }

    /** Returns the orbit center's x coordinate. */
    public double getCenterX() { return centerX; }
    /** Returns the orbit center's z coordinate. */
    public double getCenterZ() { return centerZ; }
    /** Returns the positive orbit radius. */
    public double getRadius() { return radius; }
    /** Returns the orbit altitude. */
    public double getAltitude() { return altitude; }
}
