package net.vas.rdp.uav.path;

/** Immutable tangent point and direction for entering or leaving an orbit. */
public final class OrbitEntry {
    public enum OrbitDirection {
        LEFT,
        RIGHT
    }

    private final double entryTangentX;
    private final double entryTangentZ;
    private final double entryHeading;
    private final OrbitDirection orbitDirection;

    /** Creates a finite tangent entry; heading is Minecraft yaw degrees. */
    public OrbitEntry(double entryTangentX, double entryTangentZ,
                      double entryHeading, OrbitDirection orbitDirection) {
        if (!Double.isFinite(entryTangentX) || !Double.isFinite(entryTangentZ)
                || !Double.isFinite(entryHeading) || orbitDirection == null) {
            throw new IllegalArgumentException("Invalid orbit entry");
        }
        this.entryTangentX = entryTangentX;
        this.entryTangentZ = entryTangentZ;
        this.entryHeading = entryHeading;
        this.orbitDirection = orbitDirection;
    }

    public double getEntryTangentX() { return entryTangentX; }
    public double getEntryTangentZ() { return entryTangentZ; }
    public double getEntryHeading() { return entryHeading; }
    public OrbitDirection getOrbitDirection() { return orbitDirection; }
}
