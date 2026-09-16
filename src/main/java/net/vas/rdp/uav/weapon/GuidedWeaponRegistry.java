package net.vas.rdp.uav.weapon;

import net.minecraftforge.common.config.Configuration;
import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Server-loaded registry keyed by McHeli definition name and weapon type. */
public final class GuidedWeaponRegistry {
    private static final Map<String, GuidedWeaponCapability> CAPABILITIES =
            new HashMap<String, GuidedWeaponCapability>();

    private GuidedWeaponRegistry() { }

    public static void load(File configDir) {
        Configuration config = new Configuration(new File(configDir, "rdp-uav-guided-weapons.cfg"));
        config.load();
        CAPABILITIES.clear();
        register(config, "aamissile", "aamissile", GuidedNativeBehavior.ENTITY_SEEKER,
                "ENTITY", 0.0D, true);
        register(config, "atmissile", "atmissile", GuidedNativeBehavior.ENTITY_SEEKER,
                "ENTITY", 0.0D, true);
        register(config, "asmissile", "asmissile", GuidedNativeBehavior.COORDINATE_TV_LASER,
                "SQUARE", 0.0D, true);
        register(config, "tvmissile", "tvmissile", GuidedNativeBehavior.COORDINATE_TV_LASER,
                "SQUARE", 0.0D, true);
        for (String category : config.getCategoryNames()) {
            if (!category.startsWith("weapon.") || isBuiltIn(category.substring("weapon.".length()))) {
                continue;
            }
            registerCustom(config, category, category.substring("weapon.".length()));
        }

        if (config.hasChanged()) config.save();
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
                "ENTITY_SEEKER, COORDINATE_TV_LASER, or UNSUPPORTED");
        String objectiveNames = c.getString("objectives", category, "",
                "Comma-separated ENTITY and/or SQUARE");
        double maxSpeed = c.get(category, "maximumAircraftSpeed", 0.0D,
                "Reject release above this aircraft speed; zero disables cap.", 0.0D, 1000.0D)
                .getDouble();
        double minAltitude = c.get(category, "minimumAltitude", 0.0D,
                "Minimum terminal target altitude.", 0.0D, 4096.0D).getDouble();
        double maxAltitude = c.get(category, "maximumAltitude", 4096.0D,
                "Maximum terminal target altitude.", 0.0D, 4096.0D).getDouble();
        boolean noOrbit = c.getBoolean("noOrbit", category, true, "No terminal loiter");
        GuidedNativeBehavior behavior;
        try { behavior = GuidedNativeBehavior.valueOf(behaviorName.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { behavior = GuidedNativeBehavior.UNSUPPORTED; }
        EnumSet<GuidedObjectiveType> objectives = EnumSet.noneOf(GuidedObjectiveType.class);
        for (String value : objectiveNames.split(",")) {
            try { objectives.add(GuidedObjectiveType.valueOf(value.trim().toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException ignored) { }
        }
        register(new GuidedWeaponCapability(definition, type, behavior, objectives,
                new GuidedTerminalProfile(maxSpeed, noOrbit, minAltitude, maxAltitude)));
    }

    private static void register(Configuration c, String key, String definition,
                                 GuidedNativeBehavior defaultBehavior, String defaultObjectives,
                                 double defaultMaxSpeed, boolean defaultNoOrbit) {
        String category = "weapon." + key;
        definition = c.getString("definition", category, definition, "Exact McHeli weapon definition name");
        String type = c.getString("type", category, key, "McHeli MCH_WeaponInfo.type");
        String behaviorName = c.getString("behavior", category, defaultBehavior.name(),
                "Verified native guidance boundary");
        String objectiveNames = c.getString("objectives", category, defaultObjectives,
                "Comma-separated ENTITY and/or SQUARE");
        double maxSpeed = c.get(category, "maximumAircraftSpeed", defaultMaxSpeed,
                "Reject release above this aircraft speed; zero disables cap.", 0.0D, 1000.0D)
                .getDouble();
        double minAltitude = c.get(category, "minimumAltitude", 0.0D,
                "Minimum terminal target altitude.", 0.0D, 4096.0D).getDouble();
        double maxAltitude = c.get(category, "maximumAltitude", 4096.0D,
                "Maximum terminal target altitude.", 0.0D, 4096.0D).getDouble();
        boolean noOrbit = c.getBoolean("noOrbit", category, defaultNoOrbit,
                "Do not append a loiter node for terminal guidance.");
        GuidedNativeBehavior behavior;
        try { behavior = GuidedNativeBehavior.valueOf(behaviorName.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { behavior = GuidedNativeBehavior.UNSUPPORTED; }
        EnumSet<GuidedObjectiveType> objectives = EnumSet.noneOf(GuidedObjectiveType.class);
        for (String value : objectiveNames.split(",")) {
            try { objectives.add(GuidedObjectiveType.valueOf(value.trim().toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException ignored) { }
        }
        GuidedWeaponCapability capability = new GuidedWeaponCapability(definition, type, behavior,
                objectives, new GuidedTerminalProfile(maxSpeed, noOrbit, minAltitude, maxAltitude));
        CAPABILITIES.put(key(definition, type), capability);
    }

    public static void register(GuidedWeaponCapability capability) {
        if (capability == null) throw new IllegalArgumentException("Capability is required");
        CAPABILITIES.put(key(capability.getDefinition(), capability.getType()), capability);
    }

    public static GuidedWeaponCapability find(String definition, String type) {
        return CAPABILITIES.get(key(definition, type));
    }

    public static void clear() { CAPABILITIES.clear(); }

    private static String key(String definition, String type) {
        return normalize(definition) + "|" + normalize(type);
    }
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
