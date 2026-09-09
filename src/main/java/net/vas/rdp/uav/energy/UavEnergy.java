package net.vas.rdp.uav.energy;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.energy.EnergyStorage;

/**
 * Forge Energy storage owned by one McHeli UAV entity.
 */
public final class UavEnergy extends EnergyStorage {
    public UavEnergy(int capacity, int maxReceive, int maxExtract, int energy) {
        super(capacity, maxReceive, maxExtract, Math.max(0, energy));
    }

    public int drain(int amount) {
        return extractEnergy(Math.max(0, amount), false);
    }

    public void read(NBTTagCompound tag) {
        energy = Math.max(0, Math.min(capacity, tag.getInteger("Energy")));
    }

    public void write(NBTTagCompound tag) {
        tag.setInteger("Energy", energy);
    }
}
