package net.vas.rdp.uav.profile;

import net.minecraftforge.common.config.Configuration;
import net.vas.rdp.uav.debug.DebugChannel;
import net.vas.rdp.uav.debug.DebugContext;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Explicit McHeli definition to RDP execution-policy mapping. */
public final class UavVehicleProfiles {
    private static final Map<String, UavVehicleProfile> profiles =
            new HashMap<>();
    private static final Set<String> reportedFallbacks =
            Collections.synchronizedSet(new HashSet<String>());
    private static final UavVehicleProfile UNKNOWN = new UavVehicleProfile(
            UavExecutionProfile.UNKNOWN, 0, 0, buildLimit(), 0, false, "NONE",
            256, 256, 64);

    private UavVehicleProfiles() { }

    public static void load(File configDir) {
        File profileFile = new File(configDir, "rdp-uav-vehicles.cfg");
        Configuration config = new Configuration(profileFile);
        config.load();
        ensureDefaults(config);
        profiles.clear();
        reportedFallbacks.clear();
        for (String category : config.getCategoryNames()) {
            if (!category.startsWith("vehicle.")) continue;
            String definition = normalize(category.substring("vehicle.".length()));
            String profileName = config.getString("profile", category, "UNKNOWN",
                    "RDP execution profile: ONE_WAY or REUSABLE_UAV.");
            UavExecutionProfile executionProfile = parseProfile(profileName);
            boolean migratedFpv = definition.contains("fpv")
                    && executionProfile == UavExecutionProfile.ONE_WAY;
            if (migratedFpv) {
                executionProfile = UavExecutionProfile.HELICOPTER_ONE_WAY_FPV;
            }
            boolean reusable = executionProfile == UavExecutionProfile.REUSABLE_UAV
                    || executionProfile == UavExecutionProfile.LONG_RANGE_REUSABLE_UAV;
            boolean helicopterOneWay =
                    executionProfile == UavExecutionProfile.HELICOPTER_ONE_WAY_FPV;
            double defaultCruiseAgl = reusable ? 128F : helicopterOneWay ? 8F : 32F;
            double defaultMinimumAgl = reusable ? 64F : helicopterOneWay ? 4F : 8F;
            double defaultMaximumAgl = reusable ? 192F : helicopterOneWay ? 32F : 128F;
            double altitude = number(config.getFloat("executionAltitude", category,
                    reusable ? 250F : helicopterOneWay ? 8F : 0F,
                    0F, 4096F, "Preferred execution altitude."));
            double minimum = number(config.getFloat("minimumExecutionAltitude", category,
                    reusable ? 200F : helicopterOneWay ? 4F : 0F,
                    0F, 4096F, "Minimum reusable-UAV altitude."));
            double maximum = number(config.getFloat("maximumExecutionAltitude", category,
                    reusable ? 320F : helicopterOneWay ? 32F : 4096F,
                    0F, 4096F, "Maximum execution altitude."));
            double tolerance = number(config.getFloat("altitudeTolerance", category,
                    reusable ? 15F : helicopterOneWay ? 6F : 0F,
                    0F, 512F, "Execution-altitude tolerance."));
            if (migratedFpv && altitude <= 0.0D && minimum <= 0.0D && maximum >= 4096.0D) {
                altitude = 8.0D;
                minimum = 4.0D;
                maximum = 32.0D;
                tolerance = 6.0D;
            }
            // Migrate the exact legacy defaults to terrain-relative game defaults,
            // while preserving any values an operator has already customized.
            if (executionProfile == UavExecutionProfile.ONE_WAY
                    && altitude == 0.0D && minimum == 0.0D && maximum >= 4096.0D) {
                altitude = defaultCruiseAgl;
                minimum = defaultMinimumAgl;
                maximum = defaultMaximumAgl;
                tolerance = 4.0D;
            } else if (reusable && altitude == 250.0D && minimum == 200.0D
                    && maximum == 320.0D) {
                altitude = defaultCruiseAgl;
                minimum = defaultMinimumAgl;
                maximum = defaultMaximumAgl;
            }
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
            double stationRadius = number(config.getFloat("stationRadius", category,
                    helicopterOneWay ? 0F : reusable ? 256F : 96F, 0F, 2048F,
                    "Profile-specific station/orbit radius; zero selects native helicopter hold."));
            double maxYawCorrection = number(config.getFloat("maximumYawCorrectionDegrees",
                    category, 20F, 0F, 45F, "Maximum accumulated operator yaw trim in degrees."));
            double maxPitchCorrection = number(config.getFloat("maximumPitchCorrectionDegrees",
                    category, 10F, 0F, 20F, "Maximum accumulated operator pitch trim in degrees."));
            UavVehicleProfile profile = new UavVehicleProfile(executionProfile, altitude,
                    minimum, maximum, tolerance, holding, terminalPayload,
                    detectionRange, lockRange, missionExecutionRange, attackWeaponPriority,
                    oneWaySpeed, attackEnvelope, stationRadius,
                    maxYawCorrection, maxPitchCorrection);
            profiles.put(definition, profile);
            String aliases = config.getString("aliases", category, "",
                    "Comma-separated McHeli definition names using this profile.");
            for (String alias : aliases.split(",")) {
                if (!alias.trim().isEmpty()) profiles.put(normalize(alias), profile);
            }
        }
        if (config.hasChanged()) config.save();
        if (DebugChannel.CONFIG.isEnabled()) {
            DebugChannel.CONFIG.info(DebugContext.none(),
                    "VEHICLE_PROFILES_LOADED definitions=" + profiles.size()
                            + " config=" + profileFile.getName());
        }
        if (DebugChannel.JOURNEYMAP.isEnabled()) {
            DebugChannel.JOURNEYMAP.info(DebugContext.none(),
                    "WAYPOINT_BRIDGE_STATUS=UNAVAILABLE adapter=not_present");
        }
    }

    private static void ensureDefaults(Configuration config) {
        String[] names = {"switchblade", "shahed136", "fpvdrone", "mq9", "zala421"};
        String[] profile = {"ONE_WAY", "ONE_WAY", "HELICOPTER_ONE_WAY_FPV",
                "LONG_RANGE_REUSABLE_UAV", "ONE_WAY"};
        String[] aliases = {"switchblade", "shahed136,shahed-136", "fpv,fpv-drone,fpv_drone",
                "mq-9,mq_9,reaper,predator", "zala421"};
        for (int i = 0; i < names.length; i++) {
            String category = "vehicle." + names[i];
            config.getString("profile", category, profile[i],
                    "RDP execution profile: ONE_WAY, HELICOPTER_ONE_WAY_FPV, or REUSABLE_UAV.");
            config.getString("aliases", category, aliases[i],
                    "Comma-separated McHeli definition names using this profile.");
            config.getFloat("executionAltitude", category, i == 3 ? 128F : i == 2 ? 8F : 32F,
                    0F, 4096F, "Preferred execution altitude.");
            config.getFloat("minimumExecutionAltitude", category, i == 3 ? 64F : i == 2 ? 4F : 8F,
                    0F, 4096F, "Minimum reusable-UAV altitude.");
            config.getFloat("maximumExecutionAltitude", category, i == 3 ? 192F : i == 2 ? 32F : 128F,
                    0F, 4096F, "Maximum execution altitude.");
            config.getFloat("altitudeTolerance", category, i == 3 ? 15F : i == 2 ? 6F : 4F,
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
            config.getFloat("stationRadius", category, i == 3 ? 256F : i == 2 ? 0F : 96F,
                    0F, 2048F, "Profile-specific station/orbit radius; zero selects native helicopter hold.");
            config.getFloat("maximumYawCorrectionDegrees", category, 20F,
                    0F, 45F, "Maximum accumulated operator yaw trim in degrees.");
            config.getFloat("maximumPitchCorrectionDegrees", category, 10F,
                    0F, 20F, "Maximum accumulated operator pitch trim in degrees.");
        }
    }

    public static UavVehicleProfile forAircraft(String definition) {
        UavVehicleProfile profile = profiles.get(normalize(definition));
        String normalized = normalize(definition);
        if (profile == null && normalized.contains("fpv")) {
            if (DebugChannel.PROFILE.isEnabled() && reportedFallbacks.add(normalized)) {
                DebugChannel.PROFILE.warn(DebugContext.none(),
                        "PROFILE_FALLBACK definition="
                                + net.vas.rdp.uav.debug.DebugText.safeValue(normalized)
                                + " profile=HELICOPTER_ONE_WAY_FPV source=built_in_fpv_rule");
            }
            return new UavVehicleProfile(UavExecutionProfile.HELICOPTER_ONE_WAY_FPV, 8, 4, 32,
                    6, false, "SELF_DESTRUCT");
        }
        if (profile == null && (normalized.contains("shahed")
                || normalized.contains("switchblade") || normalized.equals("zala421"))) {
            if (DebugChannel.PROFILE.isEnabled() && reportedFallbacks.add(normalized)) {
                DebugChannel.PROFILE.warn(DebugContext.none(),
                        "PROFILE_FALLBACK definition="
                                + net.vas.rdp.uav.debug.DebugText.safeValue(normalized)
                                + " profile=ONE_WAY source=built_in_definition_rule");
            }
            return new UavVehicleProfile(UavExecutionProfile.ONE_WAY, 32, 8, 128,
                    4, false, "SELF_DESTRUCT");
        }
        if (profile == null && DebugChannel.PROFILE.isEnabled()
                && reportedFallbacks.add(normalized)) {
            DebugChannel.PROFILE.warn(DebugContext.none(),
                    "PROFILE_FALLBACK definition="
                            + net.vas.rdp.uav.debug.DebugText.safeValue(normalized)
                            + " profile=UNKNOWN");
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
