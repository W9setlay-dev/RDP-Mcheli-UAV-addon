package net.vas.rdp.uav.energy;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UavEnergyHandler {
    private final Map<UUID, Integer> lastFuel = new HashMap<UUID, Integer>();

    @SubscribeEvent
    public void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof MCH_EntityAircraft) {
            MCH_EntityAircraft aircraft = (MCH_EntityAircraft) event.getObject();
            UavEnergyConfig.Profile profile = profile(aircraft);
            event.addCapability(new net.minecraft.util.ResourceLocation("rdpuav", "energy"),
                    new UavEnergyProvider(profile.enabled
                            ? profile.capacity : UavEnergyConfig.capacity));
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        for (Entity entity : event.world.loadedEntityList) {
            if (!(entity instanceof MCH_EntityAircraft)) continue;
            MCH_EntityAircraft aircraft = (MCH_EntityAircraft) entity;
            if (!aircraft.isUAV()) continue;
            UavEnergyConfig.Profile profile = profile(aircraft);
            if (!profile.enabled) continue;
            IEnergyStorage energy = storage(aircraft);
            if (energy == null || !aircraft.isEntityAlive()) continue;
            synchronizeMcHeliRfFuel(aircraft, energy);
            int drain = movementDrain(aircraft);
            if (drain > 0) energy.extractEnergy(drain, false);
            writeMcHeliRfFuel(aircraft, energy);
            if (isRfFuel(aircraft)
                    && energy.getEnergyStored() < profile.minimumOperatingEnergy) {
                aircraft.setFuel(0);
            }
        }
    }

    public static boolean hasOperatingEnergy(MCH_EntityAircraft aircraft) {
        if (aircraft == null || !aircraft.isUAV()) return false;
        UavEnergyConfig.Profile profile = profile(aircraft);
        IEnergyStorage energy = storage(aircraft);
        return profile.enabled && energy != null
                && energy.getEnergyStored() >= profile.minimumOperatingEnergy;
    }

    public static IEnergyStorage storage(MCH_EntityAircraft aircraft) {
        if (aircraft == null || !aircraft.hasCapability(CapabilityEnergy.ENERGY, null)) return null;
        return aircraft.getCapability(CapabilityEnergy.ENERGY, null);
    }

    private static int movementDrain(MCH_EntityAircraft aircraft) {
        double speed = aircraft.motionX * aircraft.motionX
                + aircraft.motionY * aircraft.motionY
                + aircraft.motionZ * aircraft.motionZ;
        UavEnergyConfig.Profile profile = profile(aircraft);
        return speed > 0.0025D ? profile.flightDrainPerTick : profile.idleDrainPerTick;
    }

    private static UavEnergyConfig.Profile profile(MCH_EntityAircraft aircraft) {
        return UavEnergyConfig.forAircraft(aircraft.getAcInfo() == null
                ? "" : aircraft.getAcInfo().name);
    }

    private void synchronizeMcHeliRfFuel(MCH_EntityAircraft aircraft, IEnergyStorage energy) {
        if (!isRfFuel(aircraft)) return;
        Integer previous = lastFuel.get(aircraft.getUniqueID());
        int fuel = Math.max(0, aircraft.getFuel());
        if (previous == null || previous.intValue() != fuel) {
            int maxFuel = Math.max(1, aircraft.getAcInfo().maxFuel);
            int mapped = (int) ((long) fuel * energy.getMaxEnergyStored() / maxFuel);
            energy.extractEnergy(energy.getEnergyStored(), false);
            if (mapped > 0) {
                energy.receiveEnergy(mapped, false);
            }
        }
    }

    private void writeMcHeliRfFuel(MCH_EntityAircraft aircraft, IEnergyStorage energy) {
        if (!isRfFuel(aircraft)) return;
        int maxFuel = Math.max(1, aircraft.getAcInfo().maxFuel);
        int fuel = (int) ((long) energy.getEnergyStored() * maxFuel
                / Math.max(1, energy.getMaxEnergyStored()));
        aircraft.setFuel(Math.max(0, Math.min(maxFuel, fuel)));
        lastFuel.put(aircraft.getUniqueID(), aircraft.getFuel());
    }

    private static boolean isRfFuel(MCH_EntityAircraft aircraft) {
        return aircraft.getFuelType() != null
                && "RF".equalsIgnoreCase(aircraft.getFuelType());
    }

}
