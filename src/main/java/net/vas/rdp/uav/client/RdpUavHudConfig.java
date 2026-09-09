package net.vas.rdp.uav.client;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class RdpUavHudConfig {
    public static boolean enabled = true;
    public static boolean markerVisible = true;
    public static boolean telemetryVisible = true;
    public static float scale = 1.0F;
    public static int xOffset;
    public static int yOffset;

    private RdpUavHudConfig() { }

    public static void load(File configDir) {
        Configuration config = new Configuration(new File(configDir, "rdp-uav-hud.cfg"));
        try {
            enabled = config.getBoolean("enabled", "hud", true, "Show the authoritative UAV target HUD.");
            markerVisible = config.getBoolean("markerVisible", "hud", true,
                    "Show the target reticle when a target position is available.");
            telemetryVisible = config.getBoolean("telemetryVisible", "hud", true,
                    "Show target and mission telemetry.");
            scale = (float) config.getFloat("scale", "hud", 1.0F, 0.5F, 2.0F,
                    "HUD scale.");
            xOffset = config.getInt("xOffset", "hud", 12, -500, 500,
                    "Horizontal HUD offset from the left edge.");
            yOffset = config.getInt("yOffset", "hud", 12, -500, 500,
                    "Vertical HUD offset from the top edge.");
        } finally {
            if (config.hasChanged()) config.save();
        }
    }
}
