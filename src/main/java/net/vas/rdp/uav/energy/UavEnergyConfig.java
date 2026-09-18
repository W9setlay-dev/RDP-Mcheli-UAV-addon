package net.vas.rdp.uav.energy;

import net.minecraftforge.common.config.Configuration;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Forge configuration keeps the capability contract independent of the
 * aircraft definition format used by a particular McHeli pack.
 */
public final class UavEnergyConfig {
    public static int capacity = 250000;
    public static int idleDrainPerTick = 2;
    public static int flightDrainPerTick = 8;
    public static int minimumOperatingEnergy = 1;
    private static final Map<String, Profile> profiles = new HashMap<>();

    private UavEnergyConfig() { }

    public static void load(File configDir) {
        Configuration config = new Configuration(new File(configDir, "rdp-uav-energy.cfg"));
        config.load();
        capacity = positive(config.getInt("capacity", "energy", capacity, 1, Integer.MAX_VALUE,
                "Forge Energy capacity for each McHeli UAV."));
        idleDrainPerTick = nonNegative(config.getInt("idleDrainPerTick", "energy",
                idleDrainPerTick, 0, Integer.MAX_VALUE, "Energy used per tick while powered but stationary."));
        flightDrainPerTick = nonNegative(config.getInt("flightDrainPerTick", "energy",
                flightDrainPerTick, 0, Integer.MAX_VALUE, "Energy used per tick while the aircraft is moving."));
        minimumOperatingEnergy = nonNegative(config.getInt("minimumOperatingEnergy", "energy",
                minimumOperatingEnergy, 0, Integer.MAX_VALUE,
                "Energy below which McHeli fuel availability is disabled."));
        profiles.clear();
        for (String category : config.getCategoryNames()) {
            if (!category.startsWith("aircraft.")) continue;
            String name = category.substring("aircraft.".length()).toLowerCase(Locale.ROOT);
            boolean enabled = config.getBoolean("enabled", category, true,
                    "Attach Forge Energy to this UAV.");
            int aircraftCapacity = positive(config.getInt("capacity", category, capacity, 1,
                    Integer.MAX_VALUE, "Energy capacity for this UAV."));
            int aircraftIdleDrain = nonNegative(config.getInt("idleDrainPerTick", category,
                    idleDrainPerTick, 0, Integer.MAX_VALUE, "Stationary drain per tick."));
            int aircraftFlightDrain = nonNegative(config.getInt("flightDrainPerTick", category,
                    flightDrainPerTick, 0, Integer.MAX_VALUE, "Flight drain per tick."));
            int aircraftMinimum = nonNegative(config.getInt("minimumOperatingEnergy", category,
                    minimumOperatingEnergy, 0, Integer.MAX_VALUE, "Minimum operating energy."));
            profiles.put(name, new Profile(enabled, aircraftCapacity, aircraftIdleDrain,
                    aircraftFlightDrain, aircraftMinimum));
        }
        if (config.hasChanged()) config.save();
    }

    public static Profile forAircraft(String name) {
        String aircraftName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        Profile profile = profiles.get(aircraftName);
        return profile == null ? new Profile(false, capacity, idleDrainPerTick,
                flightDrainPerTick, minimumOperatingEnergy) : profile;
    }

    public static final class Profile {
        public final boolean enabled;
        public final int capacity;
        public final int idleDrainPerTick;
        public final int flightDrainPerTick;
        public final int minimumOperatingEnergy;

        private Profile(boolean enabled, int capacity, int idleDrainPerTick,
                        int flightDrainPerTick, int minimumOperatingEnergy) {
            this.enabled = enabled;
            this.capacity = capacity;
            this.idleDrainPerTick = idleDrainPerTick;
            this.flightDrainPerTick = flightDrainPerTick;
            this.minimumOperatingEnergy = minimumOperatingEnergy;
        }
    }

    private static int positive(int value) {
        return Math.max(1, value);
    }

    private static int nonNegative(int value) {
        return Math.max(0, value);
    }
}
