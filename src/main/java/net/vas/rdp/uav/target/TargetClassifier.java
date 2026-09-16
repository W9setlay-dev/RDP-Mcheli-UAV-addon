package net.vas.rdp.uav.target;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class TargetClassifier {
    private static final Set<String> HOSTILE_PREFIXES = new HashSet<String>(Arrays.asList(
            "minecraft:creeper",
            "minecraft:skeleton",
            "minecraft:stray",
            "minecraft:spider",
            "minecraft:zombie",
            "minecraft:husk",
            "minecraft:drowned",
            "minecraft:witch",
            "minecraft:enderman",
            "minecraft:slime",
            "minecraft:blaze",
            "minecraft:ghast",
            "minecraft:wither_skeleton",
            "minecraft:evocation_illager",
            "minecraft:vindicator",
            "minecraft:pillager",
            "minecraft:ravager",
            "srparasites:",
            "lycanitesmobs:",
            "scp:",
            "techguns:",
            "mcheli:",
            "modularwarfare:",
            "cgm:",
            "immersiveengineering:",
            "advancedrocketry:",
            "minecraft:iron_golem"));

    private TargetClassifier() { }

    public static boolean isHostileNamespace(String namespace) {
        if (namespace == null) return false;
        String normalized = namespace.trim().toLowerCase(java.util.Locale.ROOT);
        return "hostile".equals(normalized) || "threat".equals(normalized)
                || "enemy".equals(normalized) || "hostiles".equals(normalized);
    }

    public static boolean matchesHostileNamespace(String registryId, String namespace) {
        if (registryId == null || registryId.trim().isEmpty()) return false;
        String normalizedId = registryId.trim().toLowerCase(java.util.Locale.ROOT);
        if (isHostileNamespace(namespace)) {
            return isHostileCandidate(normalizedId);
        }
        return namespace != null && normalizedId.startsWith(namespace.trim().toLowerCase(java.util.Locale.ROOT) + ":");
    }

    public static boolean isHostileCandidate(String registryId) {
        if (registryId == null || registryId.trim().isEmpty()) return false;
        String normalizedId = registryId.trim().toLowerCase(java.util.Locale.ROOT);
        // Aircraft, trucks and other vehicles are contacts, not hostile
        // personnel.  Keep this guard before the broad mod-prefix rules.
        if (isVehicleId(normalizedId)) return false;
        for (String prefix : HOSTILE_PREFIXES) {
            if (normalizedId.equals(prefix) || normalizedId.startsWith(prefix)) {
                return true;
            }
        }
        return normalizedId.contains("bandit")
                || normalizedId.contains("soldier")
                || normalizedId.contains("military")
                || normalizedId.contains("hostile")
                || normalizedId.contains("enemy")
                || normalizedId.contains("scout")
                || normalizedId.contains("raider")
                || normalizedId.contains("guard")
                || normalizedId.contains("drone");
    }

    public static boolean isVehicleId(String registryId) {
        if (registryId == null) return false;
        String id = registryId.trim().toLowerCase(java.util.Locale.ROOT);
        return id.contains("vehicle") || id.contains("truck") || id.contains("car")
                || id.contains("tank") || id.contains("transport") || id.contains("jeep")
                || id.contains("aircraft") || id.contains("plane") || id.contains("helicopter");
    }

    public static String classify(String registryId) {
        if (registryId == null || registryId.trim().isEmpty()) {
            return "UNKNOWN";
        }
        String normalizedId = registryId.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalizedId.contains("bandit") || normalizedId.contains("military")
                || normalizedId.contains("soldier") || normalizedId.contains("guard")) {
            return "MILITARY";
        }
        if (isVehicleId(normalizedId) || normalizedId.contains("vehicle") || normalizedId.contains("truck")
                || normalizedId.contains("car") || normalizedId.contains("tank")
                || normalizedId.contains("transport") || normalizedId.contains("jeep")) {
            return "VEHICLE";
        }
        if (normalizedId.contains("drone") || normalizedId.contains("scout")
                || normalizedId.contains("raider") || normalizedId.contains("turret")) {
            return "SCOUT";
        }
        if (isHostileCandidate(normalizedId)) {
            return "HOSTILE";
        }
        return "UNKNOWN";
    }

    public static int threatScore(String registryId) {
        String classification = classify(registryId);
        if ("MILITARY".equals(classification)) return 120;
        if ("VEHICLE".equals(classification)) return 100;
        if ("SCOUT".equals(classification)) return 85;
        if ("HOSTILE".equals(classification)) return 70;
        return 0;
    }

    public static boolean isMissionRelevant(String registryId, String namespace) {
        if (registryId == null || registryId.trim().isEmpty()) return false;
        if (namespace == null || namespace.trim().isEmpty()) return isHostileCandidate(registryId);
        String normalizedNamespace = namespace.trim().toLowerCase(java.util.Locale.ROOT);
        if (isHostileNamespace(normalizedNamespace)) {
            return isHostileCandidate(registryId);
        }
        return registryId.toLowerCase(java.util.Locale.ROOT).startsWith(normalizedNamespace + ":");
    }
}
