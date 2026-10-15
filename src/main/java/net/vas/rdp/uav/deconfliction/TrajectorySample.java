package net.vas.rdp.uav.deconfliction;

/** Immutable server-side predicted aircraft state at a relative mission tick. */
public final class TrajectorySample {
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final float roll;
    private final long tickOffset;
    private final double confidence;

    /** Creates a finite sample with confidence in the inclusive interval [0, 1]. */
    public TrajectorySample(double x, double y, double z, float yaw, float pitch,
                            float roll, long tickOffset, double confidence) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)
                || tickOffset < 0L || !Double.isFinite(confidence)
                || confidence < 0.0D || confidence > 1.0D) {
            throw new IllegalArgumentException("Invalid trajectory sample");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.tickOffset = tickOffset;
        this.confidence = confidence;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public float getRoll() { return roll; }
    public long getTickOffset() { return tickOffset; }
    public double getConfidence() { return confidence; }
}
