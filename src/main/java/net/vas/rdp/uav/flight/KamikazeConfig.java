package net.vas.rdp.uav.flight;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class KamikazeConfig {
    public static final String ARMED_KEY = "rdpuav_kamikaze_nosedive_armed";
    public static final String MIN_SPEED_KEY = "rdpuav_kamikaze_min_speed";
    public static double minimumImpactSpeed = 0.35D;

    private KamikazeConfig() { }

    public static void load(File configDir) {
        Configuration config = new Configuration(new File(configDir, "rdp-uav-kamikaze.cfg"));
        config.load();
        minimumImpactSpeed = config.get("kamikaze", "minimumImpactSpeed",
                minimumImpactSpeed,
                "Minimum McHeli crash speed required to detonate an armed warhead.",
                0.01D, 10.0D).getDouble();
        if (config.hasChanged()) config.save();
    }
}
