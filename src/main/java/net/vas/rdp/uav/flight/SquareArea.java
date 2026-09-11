package net.vas.rdp.uav.flight;

public final class SquareArea {
    private final double centerX;
    private final double centerZ;
    private final double halfWidth;
    private final double halfDepth;

    public SquareArea(double centerX, double centerZ, double width, double depth) {
        if (!finite(centerX) || !finite(centerZ) || !finite(width) || !finite(depth)
                || width <= 0.0D || depth <= 0.0D) {
            throw new IllegalArgumentException("Area dimensions must be finite and positive");
        }
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.halfWidth = width / 2.0D;
        this.halfDepth = depth / 2.0D;
    }

    public boolean contains(double x, double z) {
        return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
    }

    public double minX() { return centerX - halfWidth; }
    public double maxX() { return centerX + halfWidth; }
    public double minZ() { return centerZ - halfDepth; }
    public double maxZ() { return centerZ + halfDepth; }
    public double getCenterX() { return centerX; }
    public double getCenterZ() { return centerZ; }
    public double getWidth() { return halfWidth * 2.0D; }
    public double getDepth() { return halfDepth * 2.0D; }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
