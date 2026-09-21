package net.vas.rdp.uav.swarm;

public final class SearchSector {
    private final int index;
    private final double minX;
    private final double maxX;
    private final double minZ;
    private final double maxZ;

    public SearchSector(int index, double minX, double maxX, double minZ, double maxZ) {
        if (index < 0 || !Double.isFinite(minX) || !Double.isFinite(maxX)
                || !Double.isFinite(minZ) || !Double.isFinite(maxZ)
                || maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("Invalid search sector");
        }
        this.index = index;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    public int getIndex() { return index; }
    public double getMinX() { return minX; }
    public double getMaxX() { return maxX; }
    public double getMinZ() { return minZ; }
    public double getMaxZ() { return maxZ; }
}
