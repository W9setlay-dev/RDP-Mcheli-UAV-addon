package net.vas.rdp.uav.shared;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SharedGeometryNbtTest {
    @Test
    public void trajectorySampleRoundTrip_preservesAllFields() {
        TrajectorySample sample = new TrajectorySample(12.5D, 80.0D, -9.0D,
                45.0F, -3.0F, 2.0F, 25L, 0.75D);

        TrajectorySample restored = TrajectorySample.readFromNBT(sample.writeToNBT());

        assertEquals(sample.getX(), restored.getX(), 0.0D);
        assertEquals(sample.getY(), restored.getY(), 0.0D);
        assertEquals(sample.getZ(), restored.getZ(), 0.0D);
        assertEquals(sample.getYaw(), restored.getYaw(), 0.0F);
        assertEquals(sample.getPitch(), restored.getPitch(), 0.0F);
        assertEquals(sample.getRoll(), restored.getRoll(), 0.0F);
        assertEquals(sample.getTickOffset(), restored.getTickOffset());
        assertEquals(sample.getConfidence(), restored.getConfidence(), 0.0D);
    }

    @Test
    public void trajectorySampleRoundTrip_ignoresUnknownTags() {
        NBTTagCompound tag = new TrajectorySample(1.0D, 2.0D, 3.0D,
                4.0F, 5.0F, 6.0F, 7L, 0.8D).writeToNBT();
        tag.setString("futureField", "ignored");

        TrajectorySample restored = TrajectorySample.readFromNBT(tag);

        assertEquals(1.0D, restored.getX(), 0.0D);
    }

    @Test
    public void releaseGeometryRoundTrip_preservesAllFields() {
        ReleaseGeometry geometry = new ReleaseGeometry(37.0F, 10.0D,
                75.0D, -4.0D, 210.0F, 250.0D);

        ReleaseGeometry restored = ReleaseGeometry.readFromNBT(geometry.writeToNBT());

        assertEquals(geometry.getBearing(), restored.getBearing(), 0.0F);
        assertEquals(geometry.getReleaseX(), restored.getReleaseX(), 0.0D);
        assertEquals(geometry.getReleaseY(), restored.getReleaseY(), 0.0D);
        assertEquals(geometry.getReleaseZ(), restored.getReleaseZ(), 0.0D);
        assertEquals(geometry.getEgressBearing(), restored.getEgressBearing(), 0.0F);
        assertEquals(geometry.getReleaseDistance(), restored.getReleaseDistance(), 0.0D);
    }

    @Test
    public void releaseGeometryRoundTrip_ignoresUnknownTags() {
        NBTTagCompound tag = new ReleaseGeometry(1.0F, 2.0D, 3.0D,
                4.0D, 5.0F, 6.0D).writeToNBT();
        tag.setInteger("futureVersionedField", 42);

        ReleaseGeometry restored = ReleaseGeometry.readFromNBT(tag);

        assertEquals(6.0D, restored.getReleaseDistance(), 0.0D);
    }
}
