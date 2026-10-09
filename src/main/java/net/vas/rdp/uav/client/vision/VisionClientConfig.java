package net.vas.rdp.uav.client.vision;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class VisionClientConfig {
    public static boolean enabled;
    public static boolean overlayEnabled;
    public static int captureRateHz = 5;
    public static int inputWidth = 320;
    public static int inputHeight = 180;
    public static double minimumFeatureScore = 0.6D;

    private VisionClientConfig() { }

    public static void load(File configDir) {
        Configuration configuration = new Configuration(
                new File(configDir, "rdp-uav-vision.cfg"));
        try {
            enabled = configuration.getBoolean("enabled", "perception", false,
                    "Capture the McHeli UAV station's rendered camera feed for local pixel perception.");
            overlayEnabled = configuration.getBoolean("overlayEnabled", "perception", false,
                    "Show local perception diagnostics; overlay pixels are not detector input.");
            captureRateHz = configuration.getInt("captureRateHz", "perception", 5, 1, 15,
                    "Maximum UAV-feed frame capture frequency.");
            inputWidth = configuration.getInt("inputWidth", "perception", 320, 64, 1024,
                    "Width of the GPU-downscaled perception frame.");
            inputHeight = configuration.getInt("inputHeight", "perception", 180, 36, 576,
                    "Height of the GPU-downscaled perception frame.");
            minimumFeatureScore = configuration.getFloat("minimumFeatureScore",
                    "perception", 0.6F, 0.0F, 1.0F,
                    "Minimum magenta beacon-feature match score; this is not a probability.");
        } finally {
            if (configuration.hasChanged()) configuration.save();
        }
    }
}
