package net.vas.rdp.uav.designation;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class DesignationConfig {
    public static long designationTtlTicks = 24000L;
    public static int maxDesignationsPerOperator = 16;
    public static int designationEnvelopeBlocks = 5000;
    public static double duplicatePointRadius = 2.0D;
    public static String duplicateEntityPolicy = "STRICT";
    public static int requestsPerSecond = 4;
    public static int requestsPerMinute = 32;

    private DesignationConfig() { }

    public static void load(File configDir) {
        Configuration config = new Configuration(new File(configDir, "rdp-uav-designation.cfg"));
        config.load();
        designationTtlTicks = config.getInt("designationTtlTicks", "designation",
                (int) designationTtlTicks, 0, Integer.MAX_VALUE,
                "Designation TTL in ticks; 0 disables expiry.");
        maxDesignationsPerOperator = config.getInt("maxDesignationsPerOperator", "designation",
                maxDesignationsPerOperator, 1, 256,
                "Maximum unresolved designations per operator.");
        designationEnvelopeBlocks = config.getInt("designationEnvelopeBlocks", "designation",
                designationEnvelopeBlocks, 1, 32768,
                "Maximum designation envelope in blocks.");
        duplicatePointRadius = config.getFloat("duplicatePointRadius", "designation",
                (float) duplicatePointRadius, 0.1F, 64.0F,
                "Duplicate point radius in blocks.");
        duplicateEntityPolicy = config.getString("duplicateEntityPolicy", "designation",
                duplicateEntityPolicy,
                "Duplicate entity policy: STRICT or LOOSE.");
        requestsPerSecond = config.getInt("requestsPerSecond", "rateLimit",
                requestsPerSecond, 1, 256,
                "Maximum designation requests per second per operator.");
        requestsPerMinute = config.getInt("requestsPerMinute", "rateLimit",
                requestsPerMinute, 1, 1024,
                "Maximum designation requests per minute per operator.");
        if (config.hasChanged()) config.save();
    }
}
