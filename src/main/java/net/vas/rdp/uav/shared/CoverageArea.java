package net.vas.rdp.uav.shared;

/** Immutable rectangular scan-area footprint and orientation in world coordinates. */
public final class CoverageArea {
    private final double centerX;
    private final double centerZ;
    private final double halfWidth;
    private final double halfDepth;
    private final float bearing;

    /** Creates a finite coverage area with positive half extents. */
    public CoverageArea(double centerX, double centerZ, double halfWidth,
                        double halfDepth, float bearing) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)
                || !Double.isFinite(halfWidth) || halfWidth <= 0.0D
                || !Double.isFinite(halfDepth) || halfDepth <= 0.0D
                || !Float.isFinite(bearing)) {
            throw new IllegalArgumentException("Invalid coverage area");
        }
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.halfWidth = halfWidth;
        this.halfDepth = halfDepth;
        this.bearing = bearing;
    }

    /** Returns the coverage center's x coordinate. */
    public double getCenterX() { return centerX; }
    /** Returns the coverage center's z coordinate. */
    public double getCenterZ() { return centerZ; }
    /** Returns the half-width of the coverage rectangle. */
    public double getHalfWidth() { return halfWidth; }
    /** Returns the half-depth of the coverage rectangle. */
    public double getHalfDepth() { return halfDepth; }
    /** Returns the rectangle bearing in degrees. */
    public float getBearing() { return bearing; }
}
