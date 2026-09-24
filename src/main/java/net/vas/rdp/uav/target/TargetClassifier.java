package net.vas.rdp.uav.target;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.Entity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class TargetClassifier {
    private static final Set<String> HOSTILE_PREFIXES = new HashSet<>(Arrays.asList(
            "minecraft:creeper",
            "minecraft:skeleton",
            "minecraft:spider",
            "minecraft:zombie",
            "minecraft:husk",
            "minecraft:witch",
            "minecraft:enderman",
            "minecraft:slime",
            "minecraft:blaze",
            "minecraft:ghast",
            "minecraft:wither_skeleton",
            "srparasites:",
            "srmonstress:",
            "gtfo_craft:",
            "lycanitesmobs:",
            "scp:",
            "techguns:",
            "modularwarfare:",
            "cgm:",
            "advancedrocketry:"));

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
        // Vehicle contacts are admitted by the explicit hostile-vehicle
        // policy below, not by the generic personnel filter.
        if (isVehicleId(normalizedId)) return false;
        for (String prefix : HOSTILE_PREFIXES) {
            if (normalizedId.equals(prefix) || normalizedId.startsWith(prefix)) {
                return true;
            }
        }
        return normalizedId.contains("bandit")
                || normalizedId.contains("soldier")
                || normalizedId.contains("military")
                || normalizedId.contains("attack")
                || normalizedId.contains("enemy")
                || normalizedId.contains("scout")
                || normalizedId.contains("raider")
                || normalizedId.contains("guard")
                || normalizedId.contains("drone")
                || normalizedId.contains("turret");
    }

    public static boolean isVehicleId(String registryId) {
        if (registryId == null) return false;
        String id = registryId.trim().toLowerCase(java.util.Locale.ROOT);
        return id.contains("mcheli") || id.contains("mch.") || id.contains("mch_");
    }

    public static boolean isHostileVehicle(Entity entity) {
        if (!(entity instanceof MCH_EntityAircraft)) return false;
        MCH_EntityAircraft aircraft = (MCH_EntityAircraft) entity;
        String definition = aircraft.getAcInfo() == null ? null : aircraft.getAcInfo().name;
        if (definition == null || definition.trim().isEmpty()) return false;
        String normalized = definition.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("bandit")
                || normalized.contains("enemy")
                || normalized.contains("hostile")
                || normalized.contains("military")
                || normalized.contains("raider")
                || normalized.contains("pirate")
                || normalized.contains("gunship")
                || normalized.contains("fighter")
                || normalized.contains("strike")
                || normalized.contains("attack");
    }

    public static String classify(String registryId) {
        if (registryId == null || registryId.trim().isEmpty()) {
            return "UNKNOWN";
        }
        String normalizedId = registryId.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalizedId.contains("bandit") || normalizedId.contains("military")
                || normalizedId.contains("soldier") || normalizedId.contains("guard")
                || normalizedId.contains("turret")) {
            return "MILITARY";
        }
        if (isVehicleId(normalizedId) || normalizedId.contains("mcheli")) {
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
        switch (classification) {
            case "MILITARY":
                return 120;
            case "VEHICLE":
                return 100;
            case "SCOUT":
                return 85;
            case "HOSTILE":
                return 70;
        }
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

    public static boolean isHostileVehicle(String registryId) {
        if (registryId == null) return false;
        String id = registryId.trim().toLowerCase(java.util.Locale.ROOT);
        if (!isVehicleId(id)) return false;
        if (id.equals("mcheli:mch.e.plane") || id.equals("mcheli:mch.e.heli")
                || id.equals("mcheli:mch.e.gun") || id.equals("mcheli:mch.e.bomber")) {
            return false;
        }
        return id.contains("bandit") || id.contains("military") || id.contains("enemy")
                || id.contains("hostile") || id.contains("raider") || id.contains("pirate")
                || id.contains("gunship") || id.contains("fighter") || id.contains("strike")
                || id.contains("attack");
    }
}
