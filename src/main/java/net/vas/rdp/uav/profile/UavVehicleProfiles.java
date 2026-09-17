package net.vas.rdp.uav.profile;

import net.minecraftforge.common.config.Configuration;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Explicit McHeli definition to RDP execution-policy mapping. */
public final class UavVehicleProfiles {
    private static final Map<String, UavVehicleProfile> profiles =
            new HashMap<>();
    private static final UavVehicleProfile UNKNOWN = new UavVehicleProfile(
            UavExecutionProfile.UNKNOWN, 0, 0, buildLimit(), 0, false, "NONE",
            256, 256, 64);

    private UavVehicleProfiles() { }

    public static void load(File configDir) {
        Configuration config = new Configuration(new File(configDir, "rdp-uav-vehicles.cfg"));
        config.load();
        ensureDefaults(config);
        profiles.clear();
        for (String category : config.getCategoryNames()) {
            if (!category.startsWith("vehicle.")) continue;
            String definition = normalize(category.substring("vehicle.".length()));
            String profileName = config.getString("profile", category, "UNKNOWN",
                    "RDP execution profile: ONE_WAY or REUSABLE_UAV.");
            UavExecutionProfile executionProfile = parseProfile(profileName);
            boolean reusable = executionProfile == UavExecutionProfile.REUSABLE_UAV
                    || executionProfile == UavExecutionProfile.LONG_RANGE_REUSABLE_UAV;
            double altitude = number(config.getFloat("executionAltitude", category,
                    reusable ? 250F : 0F,
                    0F, 4096F, "Preferred execution altitude."));
            double minimum = number(config.getFloat("minimumExecutionAltitude", category,
                    reusable ? 200F : 0F,
                    0F, 4096F, "Minimum reusable-UAV altitude."));
            double maximum = number(config.getFloat("maximumExecutionAltitude", category,
                    reusable ? 320F : 4096F,
                    0F, 4096F, "Maximum execution altitude."));
            double tolerance = number(config.getFloat("altitudeTolerance", category,
                    reusable ? 15F : 0F,
                    0F, 512F, "Execution-altitude tolerance."));
            double detectionRange = number(config.getFloat("detectionRange", category,
                    executionProfile == UavExecutionProfile.LONG_RANGE_REUSABLE_UAV ? 2048F : 256F,
                    1F, 4096F, "Target detection range."));
            double lockRange = number(config.getFloat("lockRange", category,
                    executionProfile == UavExecutionProfile.LONG_RANGE_REUSABLE_UAV ? 1536F : 256F,
                    1F, 4096F, "Authoritative target-lock range."));
            double missionExecutionRange = number(config.getFloat("missionExecutionRange", category,
                    64F,
                    1F, 4096F, "Mission execution range."));
            boolean holding = config.getBoolean("holdingEnabled", category,
                    reusable,
                    "Allow reusable UAV holding/orbit behavior.");
            String terminalPayload = config.getString("terminalPayloadBehavior", category,
                    executionProfile == UavExecutionProfile.ONE_WAY ? "SELF_DESTRUCT" : "NONE",
                    "Terminal payload behavior; this does not create a payload.");
            String attackWeaponPriority = config.getString("attackWeaponPriority", category, "",
                    "Comma-separated McHeli weapon names, in preferred conventional-attack order.");
            double oneWaySpeed = number(config.getFloat("oneWaySpeed", category, 0F, 0F, 64F,
                    "Optional native WingMan speed envelope; zero keeps the aircraft definition default."));
            double attackEnvelope = number(config.getFloat("attackEnvelope", category, 64F, 1F, 4096F,
                    "Maximum attack-node envelope passed to native WingMan."));
            UavVehicleProfile profile = new UavVehicleProfile(executionProfile, altitude,
                    minimum, maximum, tolerance, holding, terminalPayload,
                    detectionRange, lockRange, missionExecutionRange, attackWeaponPriority,
                    oneWaySpeed, attackEnvelope);
            profiles.put(definition, profile);
            String aliases = config.getString("aliases", category, "",
                    "Comma-separated McHeli definition names using this profile.");
            for (String alias : aliases.split(",")) {
                if (!alias.trim().isEmpty()) profiles.put(normalize(alias), profile);
            }
        }
        if (config.hasChanged()) config.save();
    }

    private static void ensureDefaults(Configuration config) {
        String[] names = {"switchblade", "shahed136", "fpvdrone", "mq9"};
        String[] profile = {"ONE_WAY", "ONE_WAY", "ONE_WAY", "LONG_RANGE_REUSABLE_UAV"};
        String[] aliases = {"switchblade", "shahed136,shahed-136", "fpv,fpv-drone,fpv_drone", "mq-9,mq_9,reaper,predator"};
        for (int i = 0; i < names.length; i++) {
            String category = "vehicle." + names[i];
            config.getString("profile", category, profile[i],
                    "RDP execution profile: ONE_WAY or REUSABLE_UAV.");
            config.getString("aliases", category, aliases[i],
                    "Comma-separated McHeli definition names using this profile.");
            config.getFloat("executionAltitude", category, i == 3 ? 250F : 0F,
                    0F, 4096F, "Preferred execution altitude.");
            config.getFloat("minimumExecutionAltitude", category, i == 3 ? 200F : 0F,
                    0F, 4096F, "Minimum reusable-UAV altitude.");
            config.getFloat("maximumExecutionAltitude", category, i == 3 ? 320F : 4096F,
                    0F, 4096F, "Maximum execution altitude.");
            config.getFloat("altitudeTolerance", category, i == 3 ? 15F : 4F,
                    0F, 512F, "Execution-altitude tolerance.");
            config.getBoolean("holdingEnabled", category, i == 3,
                    "Allow reusable UAV holding/orbit behavior.");
            config.getString("terminalPayloadBehavior", category, i == 3 ? "NONE" : "SELF_DESTRUCT",
                    "Terminal payload behavior; this does not create a payload.");
            config.getString("attackWeaponPriority", category, "",
                    "Comma-separated McHeli weapon names, in preferred conventional-attack order.");
            config.getFloat("oneWaySpeed", category, 0F, 0F, 64F,
                    "Optional native WingMan speed envelope; zero keeps the aircraft definition default.");
            config.getFloat("attackEnvelope", category, 64F, 1F, 4096F,
                    "Maximum attack-node envelope passed to native WingMan.");
            config.getFloat("detectionRange", category, i == 3 ? 2048F : 256F,
                    1F, 4096F, "Target detection range.");
            config.getFloat("lockRange", category, i == 3 ? 1536F : 256F,
                    1F, 4096F, "Authoritative target-lock range.");
            config.getFloat("missionExecutionRange", category, 64F,
                    1F, 4096F, "Mission execution range.");
        }
    }

    public static UavVehicleProfile forAircraft(String definition) {
        UavVehicleProfile profile = profiles.get(normalize(definition));
        if (profile == null && ("fpvdrone".equals(normalize(definition))
                || "fpv-drone".equals(normalize(definition))
                || "fpv".equals(normalize(definition)))) {
            return new UavVehicleProfile(UavExecutionProfile.ONE_WAY, 0, 0, 4096,
                    4, false, "SELF_DESTRUCT");
        }
        return profile == null ? UNKNOWN : profile;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static UavExecutionProfile parseProfile(String value) {
        try {
            return UavExecutionProfile.valueOf(normalize(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return UavExecutionProfile.UNKNOWN;
        }
    }

    private static double number(float value) { return Math.max(0D, value); }
    private static float buildLimit() { return 4096F; }
}
