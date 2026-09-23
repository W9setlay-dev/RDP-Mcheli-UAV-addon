package net.vas.rdp.uav.weapon;

import net.minecraftforge.common.config.Configuration;
import net.vas.rdp.uav.RdpUavDebug;
import java.io.File;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Server-loaded registry keyed by McHeli definition name and weapon type. */
public final class GuidedWeaponRegistry {
    private static final Map<String, GuidedWeaponCapability> CAPABILITIES =
            new HashMap<>();

    private GuidedWeaponRegistry() { }

    public static void load(File configDir) {
        Configuration config = new Configuration(new File(configDir, "rdp-uav-guided-weapons.cfg"));
        config.load();
        CAPABILITIES.clear();
        register(config, "aamissile", "aamissile", GuidedNativeBehavior.ENTITY_SEEKER,
                "ENTITY");
        register(config, "atmissile", "atmissile", GuidedNativeBehavior.ENTITY_SEEKER,
                "ENTITY");
        register(config, "asmissile", "asmissile", GuidedNativeBehavior.COORDINATE_BALLISTIC,
                "SQUARE");
        // McHeli TV/wire guidance is not an AI coordinate contract. Keep it
        // explicitly unsupported until a native laser/control handoff is
        // proven for the active weapon definition.
        register(config, "tvmissile", "tvmissile", GuidedNativeBehavior.UNSUPPORTED,
                "");
        for (String category : config.getCategoryNames()) {
            if (!category.startsWith("weapon.") || isBuiltIn(category.substring("weapon.".length()))) {
                continue;
            }
            registerCustom(config, category, category.substring("weapon.".length()));
        }

        if (config.hasChanged()) config.save();
        for (GuidedWeaponCapability capability : CAPABILITIES.values()) {
            GuidedTerminalProfile profile = capability.getTerminalProfile();
            RdpUavDebug.event("Guidance", "capability_loaded",
                    "definition", capability.getDefinition(), "type", capability.getType(),
                    "behavior", capability.getBehavior(), "objectives", capability.getObjectives(),
                    "maxCorrection", profile.getMaximumCoordinateCorrection(),
                    "correctionInterval", profile.getCoordinateCorrectionInterval());
        }
    }

    private static boolean isBuiltIn(String key) {
        return "aamissile".equals(key) || "atmissile".equals(key)
                || "asmissile".equals(key) || "tvmissile".equals(key);
    }

    private static void registerCustom(Configuration c, String category, String defaultDefinition) {
        String definition = c.getString("definition", category, defaultDefinition,
                "Exact McHeli weapon definition name");
        String type = c.getString("type", category, defaultDefinition,
                "Exact MCH_WeaponInfo.type");
        String behaviorName = c.getString("behavior", category, GuidedNativeBehavior.UNSUPPORTED.name(),
                "ENTITY_SEEKER, COORDINATE_BALLISTIC, COORDINATE_TV_LASER, or UNSUPPORTED");
        String objectiveNames = c.getString("objectives", category, "",
                "Comma-separated ENTITY and/or SQUARE");
        double maxSpeed = c.get(category, "maximumAircraftSpeed", 0.0D,
                "Reject release above this aircraft speed; zero disables cap.", 0.0D, 1000.0D)
                .getDouble();
        double minAltitude = c.get(category, "minimumAltitude", 0.0D,
                "Minimum terminal target altitude.", 0.0D, 4096.0D).getDouble();
        double maxAltitude = c.get(category, "maximumAltitude", 4096.0D,
                "Maximum terminal target altitude.", 0.0D, 4096.0D).getDouble();
        double maxCorrection = c.get(category, "maximumCoordinateCorrection",
                "asmissile".equalsIgnoreCase(type) ? 6.0D : 0.0D,
                "Maximum per-update native coordinate correction; zero disables clamping.",
                0.0D, 256.0D).getDouble();
        long correctionInterval = c.get(category, "coordinateCorrectionInterval",
                2, "Minimum ticks between coordinate corrections.", 1, 40)
                .getInt();
        boolean noOrbit = c.getBoolean("noOrbit", category, true, "No terminal loiter");
        boolean unloadedTargetExecution = c.getBoolean("supportsUnloadedTargetExecution",
                category, false, "Native projectile can execute against an unloaded target region.");
        GuidedNativeBehavior behavior;
        try { behavior = GuidedNativeBehavior.valueOf(behaviorName.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { behavior = GuidedNativeBehavior.UNSUPPORTED; }
        if ("tvmissile".equalsIgnoreCase(type)) {
            // Older configs advertised coordinate TV guidance without proving
            // that the concrete McHeli implementation consumes AI updates.
            behavior = GuidedNativeBehavior.UNSUPPORTED;
            objectiveNames = "";
        }
        EnumSet<GuidedObjectiveType> objectives = EnumSet.noneOf(GuidedObjectiveType.class);
        for (String value : objectiveNames.split(",")) {
            try { objectives.add(GuidedObjectiveType.valueOf(value.trim().toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException ignored) { }
        }
        register(new GuidedWeaponCapability(definition, type, behavior, objectives,
                new GuidedTerminalProfile(maxSpeed, minAltitude, maxAltitude,
                        maxCorrection, correctionInterval, noOrbit), unloadedTargetExecution));
    }

    private static void register(Configuration c, String key, String definition,
                                 GuidedNativeBehavior defaultBehavior, String defaultObjectives) {
        String category = "weapon." + key;
        definition = c.getString("definition", category, definition, "Exact McHeli weapon definition name");
        String type = c.getString("type", category, key, "McHeli MCH_WeaponInfo.type");
        String behaviorName = c.getString("behavior", category, defaultBehavior.name(),
                "Verified native guidance boundary");
        String objectiveNames = c.getString("objectives", category, defaultObjectives,
                "Comma-separated ENTITY and/or SQUARE");
        double maxSpeed = c.get(category, "maximumAircraftSpeed", 0.0,
                "Reject release above this aircraft speed; zero disables cap.", 0.0D, 1000.0D)
                .getDouble();
        double minAltitude = c.get(category, "minimumAltitude", 0.0D,
                "Minimum terminal target altitude.", 0.0D, 4096.0D).getDouble();
        double maxAltitude = c.get(category, "maximumAltitude", 4096.0D,
                "Maximum terminal target altitude.", 0.0D, 4096.0D).getDouble();
        double maxCorrection = c.get(category, "maximumCoordinateCorrection",
                "asmissile".equalsIgnoreCase(key) ? 6.0D : 0.0D,
                "Maximum per-update native coordinate correction; zero disables clamping.",
                0.0D, 256.0D).getDouble();
        long correctionInterval = c.get(category, "coordinateCorrectionInterval",
                2, "Minimum ticks between coordinate corrections.", 1, 40)
                .getInt();
        boolean noOrbit = c.getBoolean("noOrbit", category, true,
                "Do not append a loiter node for terminal guidance.");
        boolean unloadedTargetExecution = c.getBoolean("supportsUnloadedTargetExecution",
                category, false, "Native projectile can execute against an unloaded target region.");
        GuidedNativeBehavior behavior;
        try { behavior = GuidedNativeBehavior.valueOf(behaviorName.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { behavior = GuidedNativeBehavior.UNSUPPORTED; }
        if ("tvmissile".equalsIgnoreCase(type)) {
            behavior = GuidedNativeBehavior.UNSUPPORTED;
            objectiveNames = "";
        }
        EnumSet<GuidedObjectiveType> objectives = EnumSet.noneOf(GuidedObjectiveType.class);
        for (String value : objectiveNames.split(",")) {
            try { objectives.add(GuidedObjectiveType.valueOf(value.trim().toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException ignored) { }
        }
        GuidedWeaponCapability capability = new GuidedWeaponCapability(definition, type, behavior,
                objectives, new GuidedTerminalProfile(maxSpeed, minAltitude, maxAltitude,
                        maxCorrection, correctionInterval, noOrbit), unloadedTargetExecution);
        CAPABILITIES.put(key(definition, type), capability);
    }

    public static void register(GuidedWeaponCapability capability) {
        if (capability == null) throw new IllegalArgumentException("Capability is required");
        CAPABILITIES.put(key(capability.getDefinition(), capability.getType()), capability);
    }

    public static GuidedWeaponCapability find(String definition, String type) {
        String normalizedDefinition = normalize(definition);
        String normalizedType = normalize(type);

        GuidedWeaponCapability exact = CAPABILITIES.get(key(normalizedDefinition, normalizedType));
        if (exact != null) return exact;

        // McHeli weapon definitions commonly use names such as agm114 while
        // the native type remains atmissile. Compatible type-based lookup must
        // therefore accept either a matching native type or a definition name
        // that resolves to the same capability, even when those identifiers differ.
        if (!normalizedType.isEmpty()) {
            for (GuidedWeaponCapability capability : orderedCapabilities()) {
                if (normalizedType.equals(capability.getType())) {
                    return capability;
                }
                if (normalizedType.equals(capability.getDefinition())) {
                    return capability;
                }
            }
        }
        if (!normalizedDefinition.isEmpty()) {
            for (GuidedWeaponCapability capability : orderedCapabilities()) {
                if (normalizedDefinition.equals(capability.getType())) {
                    return capability;
                }
                if (normalizedDefinition.equals(capability.getDefinition())) {
                    return capability;
                }
            }
        }
        return null;
    }

    private static ArrayList<GuidedWeaponCapability> orderedCapabilities() {
        ArrayList<GuidedWeaponCapability> result =
                new ArrayList<>(CAPABILITIES.values());
        Collections.sort(result, (left, right) -> {
            int definition = left.getDefinition().compareTo(right.getDefinition());
            return definition != 0 ? definition : left.getType().compareTo(right.getType());
        });
        return result;
    }

    public static void clear() { CAPABILITIES.clear(); }

    private static String key(String definition, String type) {
        return normalize(definition) + "|" + normalize(type);
    }
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
