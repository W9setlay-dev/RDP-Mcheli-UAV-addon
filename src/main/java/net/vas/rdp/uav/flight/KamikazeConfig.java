package net.vas.rdp.uav.flight;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class KamikazeConfig {
    public static final String ARMED_KEY = "rdpuav_kamikaze_nosedive_armed";
    public static final String MIN_SPEED_KEY = "rdpuav_kamikaze_min_speed";
    public static double minimumImpactSpeed = 0.35D;
    public static double impactSweepPadding = 0.35D;
    public static double noseTriggerDistance = 2.0D;
    public static double closeFireDistance = 1.0D;
    public static double detonationRadius = 1.0D;
    public static long terminalTimeoutTicks = 600L;
    public static long doubleTapWindowMillis = 350L;

    private KamikazeConfig() { }

    public static void load(File configDir) {
        Configuration config = new Configuration(new File(configDir, "rdp-uav-kamikaze.cfg"));
        config.load();
        minimumImpactSpeed = config.get("kamikaze", "minimumImpactSpeed",
                minimumImpactSpeed,
                "Minimum McHeli crash speed required to detonate an armed warhead.",
                0.01D, 10.0D).getDouble();
        impactSweepPadding = config.get("kamikaze", "impactSweepPadding",
                impactSweepPadding,
                "Extra block/entity collision padding around the UAV nose sweep.",
                0.05D, 2.0D).getDouble();
        noseTriggerDistance = config.get("kamikaze", "noseTriggerDistance",
                noseTriggerDistance,
                "Maximum forward distance from the aircraft nose at which a block/entity/vehicle triggers a self-destruct payload.",
                0.5D, 8.0D).getDouble();
        closeFireDistance = config.get("kamikaze", "closeFireDistance",
                closeFireDistance,
                "Distance at which an AI-locked kamikaze UAV fires its self-destruct weapon.",
                1.0D, 64.0D).getDouble();
        detonationRadius = config.get("kamikaze", "detonationRadius",
                detonationRadius,
                "Distance from a point, entity, or vehicle target at which an armed warhead detonates.",
                1.0D, 64.0D).getDouble();
        terminalTimeoutTicks = config.get("kamikaze", "terminalTimeoutTicks",
                terminalTimeoutTicks,
                "Maximum ticks a committed kamikaze mission may remain in terminal guidance.",
                20.0D, 7200.0D).getInt();
        // Older Forge config writers can serialize this integer as "350.0".
        // Read it as a double so an existing config cannot crash preInit.
        doubleTapWindowMillis = Math.round(config.get("kamikaze", "doubleTapWindowMillis",
                (double) doubleTapWindowMillis,
                "Maximum interval between target-lock key presses that clears the lock.",
                150.0D, 1000.0D).getDouble());
        if (config.hasChanged()) config.save();
    }
}
