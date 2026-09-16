package net.vas.rdp.uav.swarm;

public final class FormationSlot {
    private final int index;
    private final double x;
    private final double y;
    private final double z;
    private final int priority;

    public FormationSlot(int index, double x, double y, double z, int priority) {
        if (index < 0 || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Invalid formation slot");
        }
        this.index = index;
        this.x = x;
        this.y = y;
        this.z = z;
        this.priority = priority;
    }

    public int getIndex() { return index; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public int getPriority() { return priority; }
}
