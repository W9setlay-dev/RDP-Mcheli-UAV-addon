package net.vas.rdp.uav.target;

import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public final class TargetClassificationConfig {
    private static List<String> protectedRegistryPrefixes = Arrays.asList("minecraft:player");
    private static List<String> friendlyRegistryPrefixes = Arrays.asList("minecraft:villager");
    private static List<String> hostileRegistryPrefixes = Arrays.asList(
            "minecraft:creeper", "minecraft:zombie", "minecraft:skeleton",
            "minecraft:spider", "srparasites", "lycanitesmobs");
    private static List<String> neutralRegistryPrefixes = Arrays.asList(
            "minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:chicken");
    private static volatile TargetClassificationPolicy activePolicy =
            createPolicy(protectedRegistryPrefixes, friendlyRegistryPrefixes,
                    hostileRegistryPrefixes, neutralRegistryPrefixes);

    private TargetClassificationConfig() { }

    static TargetClassificationPolicy currentPolicy() {
        return activePolicy;
    }

    public static void load(File configDir) {
        Configuration config = new Configuration(
                new File(configDir, "rdp-uav-classification.cfg"));
        config.load();
        List<String> protectedPrefixes = Arrays.asList(config.getStringList(
                "protectedRegistryPrefixes", "classification",
                protectedRegistryPrefixes.toArray(new String[0]),
                "Registry ID prefixes that always classify as protected."));
        List<String> friendlyPrefixes = Arrays.asList(config.getStringList(
                "friendlyRegistryPrefixes", "classification",
                friendlyRegistryPrefixes.toArray(new String[0]),
                "Registry ID prefixes classified as friendly."));
        List<String> hostilePrefixes = Arrays.asList(config.getStringList(
                "hostileRegistryPrefixes", "classification",
                hostileRegistryPrefixes.toArray(new String[0]),
                "Registry ID prefixes classified as hostile."));
        List<String> neutralPrefixes = Arrays.asList(config.getStringList(
                "neutralRegistryPrefixes", "classification",
                neutralRegistryPrefixes.toArray(new String[0]),
                "Registry ID prefixes classified as neutral."));
        TargetClassificationPolicy validatedPolicy = createPolicy(
                protectedPrefixes, friendlyPrefixes, hostilePrefixes, neutralPrefixes);
        protectedRegistryPrefixes = protectedPrefixes;
        friendlyRegistryPrefixes = friendlyPrefixes;
        hostileRegistryPrefixes = hostilePrefixes;
        neutralRegistryPrefixes = neutralPrefixes;
        activePolicy = validatedPolicy;
        if (config.hasChanged()) config.save();
    }

    private static TargetClassificationPolicy createPolicy(
            List<String> protectedPrefixes, List<String> friendlyPrefixes,
            List<String> hostilePrefixes, List<String> neutralPrefixes) {
        return new TargetClassificationPolicy(protectedPrefixes, friendlyPrefixes,
                hostilePrefixes, neutralPrefixes);
    }
}
