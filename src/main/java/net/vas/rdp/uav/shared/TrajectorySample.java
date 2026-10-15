package net.vas.rdp.uav.shared;

import net.minecraft.nbt.NBTTagCompound;

/** Immutable server-produced or client-projected aircraft sample at a relative tick. */
public final class TrajectorySample {
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final float roll;
    private final long tickOffset;
    private final double confidence;

    /** Creates a validated trajectory sample. */
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

    /** Returns the sample's x coordinate. */
    public double getX() { return x; }
    /** Returns the sample's y coordinate. */
    public double getY() { return y; }
    /** Returns the sample's z coordinate. */
    public double getZ() { return z; }
    /** Returns the sample's Minecraft yaw in degrees. */
    public float getYaw() { return yaw; }
    /** Returns the sample's pitch in degrees. */
    public float getPitch() { return pitch; }
    /** Returns the sample's roll in degrees. */
    public float getRoll() { return roll; }
    /** Returns the sample tick relative to its prediction start. */
    public long getTickOffset() { return tickOffset; }
    /** Returns confidence in the inclusive interval [0, 1]. */
    public double getConfidence() { return confidence; }

    /** Serializes this value as versioned NBT; unknown tags are ignored by the reader. */
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schemaVersion", 1);
        tag.setDouble("x", x);
        tag.setDouble("y", y);
        tag.setDouble("z", z);
        tag.setFloat("yaw", yaw);
        tag.setFloat("pitch", pitch);
        tag.setFloat("roll", roll);
        tag.setLong("tickOffset", tickOffset);
        tag.setDouble("confidence", confidence);
        return tag;
    }

    /** Reads a versioned sample and ignores additional, unrecognized tags. */
    public static TrajectorySample readFromNBT(NBTTagCompound tag) {
        if (tag == null || tag.getInteger("schemaVersion") != 1
                || !tag.hasKey("x", 99) || !tag.hasKey("y", 99)
                || !tag.hasKey("z", 99) || !tag.hasKey("yaw", 99)
                || !tag.hasKey("pitch", 99) || !tag.hasKey("roll", 99)
                || !tag.hasKey("tickOffset", 99) || !tag.hasKey("confidence", 99)) {
            throw new IllegalArgumentException("Invalid trajectory sample NBT");
        }
        return new TrajectorySample(tag.getDouble("x"), tag.getDouble("y"),
                tag.getDouble("z"), tag.getFloat("yaw"), tag.getFloat("pitch"),
                tag.getFloat("roll"), tag.getLong("tickOffset"),
                tag.getDouble("confidence"));
    }
}
