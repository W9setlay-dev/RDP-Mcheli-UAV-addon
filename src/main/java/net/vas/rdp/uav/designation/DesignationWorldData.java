package net.vas.rdp.uav.designation;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class DesignationWorldData extends WorldSavedData {
    public static final String DATA_NAME = "rdpuav_designations";
    private final DesignationRegistry registry = new DesignationRegistry();

    public DesignationWorldData() {
        super(DATA_NAME);
    }

    public DesignationWorldData(String name) {
        super(name);
    }

    public static DesignationWorldData get(World world) {
        if (world == null) throw new IllegalArgumentException("world is required");
        MapStorage storage = world.getMapStorage();
        if (storage == null) return null;
        DesignationWorldData data = (DesignationWorldData) storage.getOrLoadData(
                DesignationWorldData.class, DATA_NAME);
        if (data == null) {
            data = new DesignationWorldData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public static DesignationWorldData getIfPresent(World world) {
        if (world == null) return null;
        MapStorage storage = world.getMapStorage();
        return storage == null ? null
                : (DesignationWorldData) storage.getOrLoadData(DesignationWorldData.class, DATA_NAME);
    }

    public DesignationRegistry getRegistry() {
        return registry;
    }

    public void add(Designation designation) {
        registry.add(designation);
        markDirty();
    }

    public DesignationRegistry.AddResult tryAdd(Designation designation, long currentTick,
                                                 int maxPerOperator, double duplicatePointRadius,
                                                 String duplicateEntityPolicy) {
        DesignationRegistry.AddResult result = registry.tryAdd(designation, currentTick,
                maxPerOperator, duplicatePointRadius, duplicateEntityPolicy);
        if (result == DesignationRegistry.AddResult.ADDED) markDirty();
        return result;
    }

    public DesignationRegistry.AddResult replace(UUID existingId, Designation replacement,
                                                  long currentTick, int maxPerOperator,
                                                  double duplicatePointRadius,
                                                  String duplicateEntityPolicy) {
        DesignationRegistry.AddResult result = registry.replace(existingId, replacement,
                currentTick, maxPerOperator, duplicatePointRadius, duplicateEntityPolicy);
        if (result == DesignationRegistry.AddResult.ADDED) markDirty();
        return result;
    }

    public Designation remove(UUID designationId) {
        Designation removed = registry.remove(designationId);
        if (removed != null) markDirty();
        return removed;
    }

    public int removeUnattached(UUID operatorId) {
        int removed = registry.removeUnattached(operatorId);
        if (removed > 0) markDirty();
        return removed;
    }

    public void expire(long currentTick) {
        int before = registry.snapshot().size();
        registry.expire(currentTick);
        if (registry.snapshot().size() != before) markDirty();
    }

    public void markRegistryDirty() {
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        registry.readFromNBT(compound);
    }

    @NotNull
    @Override
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        NBTTagCompound root = registry.writeToNBT();
        for (String key : root.getKeySet()) {
            compound.setTag(key, root.getTag(key));
        }
        return compound;
    }
}
