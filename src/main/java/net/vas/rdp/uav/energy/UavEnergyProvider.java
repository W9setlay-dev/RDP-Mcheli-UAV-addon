package net.vas.rdp.uav.energy;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.energy.CapabilityEnergy;

public final class UavEnergyProvider implements ICapabilitySerializable<NBTTagCompound> {
    private final UavEnergy storage;

    public UavEnergyProvider(int capacity) {
        storage = new UavEnergy(capacity, capacity, capacity, capacity);
    }

    public UavEnergy getStorage() {
        return storage;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityEnergy.ENERGY;
    }

    @Override
    @Nullable
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityEnergy.ENERGY
                ? CapabilityEnergy.ENERGY.cast(storage) : null;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        storage.write(tag);
        return tag;
    }

    public void deserializeNBT(NBTTagCompound tag) {
        storage.read(tag);
    }
}
