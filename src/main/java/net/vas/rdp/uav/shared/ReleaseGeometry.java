package net.vas.rdp.uav.shared;

import net.minecraft.nbt.NBTTagCompound;

/** Immutable server-side proposed release and egress geometry for simulation gates. */
public final class ReleaseGeometry {
    private final float bearing;
    private final double releaseX;
    private final double releaseY;
    private final double releaseZ;
    private final float egressBearing;
    private final double releaseDistance;

    /** Creates finite release geometry with nonnegative target distance. */
    public ReleaseGeometry(float bearing, double releaseX, double releaseY, double releaseZ,
                           float egressBearing, double releaseDistance) {
        if (!Float.isFinite(bearing) || !Double.isFinite(releaseX)
                || !Double.isFinite(releaseY) || !Double.isFinite(releaseZ)
                || !Float.isFinite(egressBearing) || !Double.isFinite(releaseDistance)
                || releaseDistance < 0.0D) {
            throw new IllegalArgumentException("Invalid release geometry");
        }
        this.bearing = bearing;
        this.releaseX = releaseX;
        this.releaseY = releaseY;
        this.releaseZ = releaseZ;
        this.egressBearing = egressBearing;
        this.releaseDistance = releaseDistance;
    }

    /** Returns the inbound bearing in degrees. */
    public float getBearing() { return bearing; }
    /** Returns the proposed release x coordinate. */
    public double getReleaseX() { return releaseX; }
    /** Returns the proposed release y coordinate. */
    public double getReleaseY() { return releaseY; }
    /** Returns the proposed release z coordinate. */
    public double getReleaseZ() { return releaseZ; }
    /** Returns the egress bearing in degrees. */
    public float getEgressBearing() { return egressBearing; }
    /** Returns the proposed release distance. */
    public double getReleaseDistance() { return releaseDistance; }

    /** Serializes this value as versioned NBT. */
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schemaVersion", 1);
        tag.setFloat("bearing", bearing);
        tag.setDouble("releaseX", releaseX);
        tag.setDouble("releaseY", releaseY);
        tag.setDouble("releaseZ", releaseZ);
        tag.setFloat("egressBearing", egressBearing);
        tag.setDouble("releaseDistance", releaseDistance);
        return tag;
    }

    /** Reads versioned NBT and ignores additional, unrecognized tags. */
    public static ReleaseGeometry readFromNBT(NBTTagCompound tag) {
        if (tag == null || tag.getInteger("schemaVersion") != 1
                || !tag.hasKey("bearing", 99) || !tag.hasKey("releaseX", 99)
                || !tag.hasKey("releaseY", 99) || !tag.hasKey("releaseZ", 99)
                || !tag.hasKey("egressBearing", 99) || !tag.hasKey("releaseDistance", 99)) {
            throw new IllegalArgumentException("Invalid release geometry NBT");
        }
        return new ReleaseGeometry(tag.getFloat("bearing"), tag.getDouble("releaseX"),
                tag.getDouble("releaseY"), tag.getDouble("releaseZ"),
                tag.getFloat("egressBearing"), tag.getDouble("releaseDistance"));
    }
}
