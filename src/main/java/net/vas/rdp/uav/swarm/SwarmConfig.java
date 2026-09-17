package net.vas.rdp.uav.swarm;

import net.minecraftforge.common.config.Configuration;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class SwarmConfig {
    public static int maximumSize = 8;
    public static int updateIntervalTicks = 10;
    public static double horizontalSpacing = 16D;
    public static double verticalSpacing = 10D;
    public static double minimumHorizontalSeparation = 8D;
    public static double minimumVerticalSeparation = 6D;
    public static int memberTimeoutTicks = 100;
    public static int degradedThreshold = 1;
    private static final Map<String, Integer> profileLimits = new HashMap<String, Integer>();
    private SwarmConfig() { }
    public static void load(File dir) {
        Configuration c = new Configuration(new File(dir, "rdp-uav-swarm.cfg"));
        c.load();
        maximumSize = c.getInt("maximumSize", "swarm", maximumSize, 1, 64, "Maximum members.");
        updateIntervalTicks = c.getInt("updateIntervalTicks", "swarm", updateIntervalTicks, 1, 200, "Server update interval.");
        horizontalSpacing = c.getFloat("horizontalSpacing", "formation", (float) horizontalSpacing, 1, 512, "Formation spacing.");
        verticalSpacing = c.getFloat("verticalSpacing", "formation", (float) verticalSpacing, 1, 512, "Formation altitude spacing.");
        minimumHorizontalSeparation = c.getFloat("minimumHorizontalSeparation", "safety", (float) minimumHorizontalSeparation, 1, 512, "Minimum horizontal separation.");
        minimumVerticalSeparation = c.getFloat("minimumVerticalSeparation", "safety", (float) minimumVerticalSeparation, 1, 512, "Minimum vertical separation.");
        memberTimeoutTicks = c.getInt("memberTimeoutTicks", "swarm", memberTimeoutTicks, 20, 1200, "Member timeout.");
        degradedThreshold = c.getInt("degradedThreshold", "swarm", degradedThreshold, 0, 64, "Unavailable members before degraded state.");
        profileLimits.clear();
        for (String profile : new String[] {"ONE_WAY", "REUSABLE_UAV", "LONG_RANGE_REUSABLE_UAV"}) {
            profileLimits.put(profile, c.getInt(profile + "MaximumSize", "profiles",
                    maximumSize, 1, 64, "Maximum swarm members for this UAV profile."));
        }
        if (c.hasChanged()) c.save();
    }

    public static int maximumForProfile(String profile) {
        Integer value = profileLimits.get(profile == null ? "" : profile.toUpperCase());
        return value == null ? maximumSize : value;
    }
}
