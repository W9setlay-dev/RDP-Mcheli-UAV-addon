package net.vas.rdp.uav.designation;

import net.minecraft.nbt.NBTTagCompound;
import net.vas.rdp.uav.target.TargetPriority;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DesignationRegistryTest {
    @Test
    public void oldNbtDefaultsToActiveAndStatusRoundTrips() {
        Designation original = designation(0, 20, 1.0D, 64.0D, 1.0D);
        NBTTagCompound oldTag = original.writeToNBT();
        oldTag.removeTag("status");
        oldTag.setInteger("schemaVersion", 1);

        assertEquals(DesignationStatus.ACTIVE, Designation.readFromNBT(oldTag).getStatus());

        Designation superseded = original.withStatus(DesignationStatus.SUPERSEDED);
        assertEquals(DesignationStatus.SUPERSEDED,
                Designation.readFromNBT(superseded.writeToNBT()).getStatus());
    }

    @Test
    public void registryMigratesLegacyRootAndDesignationSchema() {
        Designation original = designation(0, 20, 1.0D, 64.0D, 1.0D);
        NBTTagCompound record = original.writeToNBT();
        record.removeTag("status");
        record.setInteger("schemaVersion", 1);
        net.minecraft.nbt.NBTTagList entries = new net.minecraft.nbt.NBTTagList();
        entries.appendTag(record);
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("schemaVersion", 1);
        root.setTag("designations", entries);

        DesignationRegistry registry = new DesignationRegistry();
        registry.readFromNBT(root);

        assertEquals(1, registry.snapshot().size());
        assertEquals(DesignationStatus.ACTIVE, registry.snapshot().get(0).getStatus());
        assertEquals(2, registry.writeToNBT().getInteger("schemaVersion"));
    }

    @Test
    public void checkedAddUsesTickExpiryCapacityAndDimensionAwarePointDeduplication() {
        DesignationRegistry registry = new DesignationRegistry();
        Designation first = designation(0, 10, 10.0D, 64.0D, 10.0D);
        assertEquals(DesignationRegistry.AddResult.ADDED,
                registry.tryAdd(first, 1L, 1, 2.0D, "STRICT"));
        assertEquals(DesignationRegistry.AddResult.DUPLICATE,
                registry.tryAdd(designation(0, 10, 11.0D, 64.0D, 10.0D),
                        1L, 1, 2.0D, "STRICT"));
        assertEquals(DesignationRegistry.AddResult.QUEUE_FULL,
                registry.tryAdd(designation(1, 10, 10.0D, 64.0D, 10.0D),
                        1L, 1, 2.0D, "STRICT"));
        assertEquals(DesignationRegistry.AddResult.ADDED,
                registry.tryAdd(designation(1, 20, 10.0D, 64.0D, 10.0D),
                        10L, 1, 2.0D, "STRICT"));
        assertEquals(1, registry.unresolvedCount(first.getOperatorId(), 10L));
    }

    @Test
    public void replacementRetainsSupersededRecordButExcludesItFromActiveQueue() {
        DesignationRegistry registry = new DesignationRegistry();
        Designation original = designation(0, 100, 10.0D, 64.0D, 10.0D);
        Designation replacement = designation(0, 100, 40.0D, 64.0D, 40.0D);
        registry.add(original);

        assertEquals(DesignationRegistry.AddResult.ADDED,
                registry.replace(original.getDesignationId(), replacement,
                        2L, 1, 2.0D, "STRICT"));
        assertEquals(DesignationStatus.SUPERSEDED,
                registry.find(original.getDesignationId()).getStatus());
        assertEquals(DesignationStatus.ACTIVE,
                registry.find(replacement.getDesignationId()).getStatus());
        assertEquals(1, registry.unresolvedCount(original.getOperatorId(), 2L));
        assertEquals(2, registry.snapshot().size());
        assertNull(registry.find(UUID.randomUUID()));
    }

    @Test
    public void logoutCleanupOnlyRemovesUnattachedDesignations() {
        DesignationRegistry registry = new DesignationRegistry();
        Designation unattached = designation(0, 100, 1.0D, 64.0D, 1.0D);
        Designation attached = Designation.builder()
                .designationId(UUID.randomUUID())
                .operatorId(unattached.getOperatorId())
                .dimension(0)
                .type(DesignationType.POINT)
                .source(DesignationSource.CROSSHAIR)
                .createdAtTick(1L)
                .expiresAtTick(100L)
                .point(new Designation.PointPayload(20.0D, 64.0D, 20.0D, false, 64))
                .priority(TargetPriority.NORMAL)
                .attachedMissionId(UUID.randomUUID())
                .build();
        registry.add(unattached);
        registry.add(attached);

        assertEquals(1, registry.removeUnattached(unattached.getOperatorId()));
        assertNull(registry.find(unattached.getDesignationId()));
        assertEquals(attached.getDesignationId(), registry.snapshot().get(0).getDesignationId());
    }

    private static Designation designation(int dimension, long expiresAtTick,
                                           double x, double y, double z) {
        return Designation.builder()
                .operatorId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .dimension(dimension)
                .type(DesignationType.POINT)
                .source(DesignationSource.CROSSHAIR)
                .createdAtTick(0L)
                .expiresAtTick(expiresAtTick)
                .point(new Designation.PointPayload(x, y, z, false, (int) y))
                .priority(TargetPriority.NORMAL)
                .build();
    }
}
