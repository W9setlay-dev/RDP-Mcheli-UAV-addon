package net.vas.rdp.uav.client;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class RdpUavHudConfig {
    public static boolean enabled = true;
    public static boolean markerVisible = true;
    public static boolean telemetryVisible = true;
    public static boolean flirEnabled;
    public static boolean anomalyAlertEnabled = true;
    public static float scale = 1.0F;
    public static double anomalyAlertThreshold = 0.55D;
    public static double anomalyAlertRegionalThreshold = 0.45D;
    public static int anomalyAlertCooldownMs = 5000;
    public static int anomalyAlertDisplayMs = 12000;
    public static String[] anomalyBiomeHints = new String[] {
            "void", "scar", "rift", "distorted", "mutated", "dark", "corrupt", "crimson", "gravit"
    };
    public static int xOffset;
    public static int yOffset;
    private static Configuration configuration;

    private RdpUavHudConfig() { }

    public static void load(File configDir) {
        configuration = new Configuration(new File(configDir, "rdp-uav-hud.cfg"));
        try {
            Configuration config = configuration;
            enabled = config.getBoolean("enabled", "hud", true, "Show the authoritative UAV target HUD.");
            markerVisible = config.getBoolean("markerVisible", "hud", true,
                    "Show world-space target bounds or a world-space point marker.");
            telemetryVisible = config.getBoolean("telemetryVisible", "hud", true,
                    "Show target and mission telemetry.");
            flirEnabled = config.getBoolean("flirEnabled", "hud", false,
                    "Use a monochrome presentation for the existing mission target bracket only.");
            anomalyAlertEnabled = config.getBoolean("anomalyAlertEnabled", "hud", true,
                    "Show a compact red RDP anomaly warning when the environment becomes unsafe.");
            anomalyAlertThreshold = config.getFloat("anomalyAlertThreshold", "hud", 0.55F, 0.0F, 1.0F,
                    "Minimum global RDP reading needed to trigger an anomaly alert.");
            anomalyAlertRegionalThreshold = config.getFloat("anomalyAlertRegionalThreshold", "hud", 0.45F, 0.0F, 1.0F,
                    "Minimum regional RDP reading needed to trigger a local anomaly warning.");
            anomalyAlertCooldownMs = config.getInt("anomalyAlertCooldownMs", "hud", 5000, 1000, 60000,
                    "Minimum time between repeated anomaly HUD warnings.");
            anomalyAlertDisplayMs = config.getInt("anomalyAlertDisplayMs", "hud", 12000, 2000, 60000,
                    "How long a warning stays visible before it can refresh.");
            anomalyBiomeHints = config.getStringList("anomalyBiomeHints", "hud", anomalyBiomeHints,
                    "Biome or region names treated as distorted or special RDP terrain.");
            scale = config.getFloat("scale", "hud", 1.0F, 0.5F, 2.0F,
                    "HUD scale.");
            xOffset = config.getInt("xOffset", "hud", 12, -500, 500,
                    "Horizontal HUD offset from the left edge.");
            yOffset = config.getInt("yOffset", "hud", 12, -500, 500,
                    "Vertical HUD offset from the top edge.");
        } finally {
            if (configuration.hasChanged()) configuration.save();
        }
    }

    public static boolean setFlirEnabled(boolean value) {
        if (flirEnabled == value) return flirEnabled;
        flirEnabled = value;
        if (configuration != null) {
            configuration.get("hud", "flirEnabled", false,
                    "Use a monochrome presentation for the existing mission target bracket only.")
                    .set(value);
            configuration.save();
        }
        return flirEnabled;
    }

    public static boolean toggleFlir() {
        return setFlirEnabled(!flirEnabled);
    }
}
