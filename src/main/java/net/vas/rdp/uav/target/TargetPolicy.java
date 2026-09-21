package net.vas.rdp.uav.target;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Server-side contact safety policy. It never moves or mutates an entity. */
public final class TargetPolicy {
    public static boolean rejectDeeplyEmbedded = true;
    public static boolean allowHostileVehicles = true;
    public static double maximumEmbeddedDepth = 1.5D;

    private TargetPolicy() { }

    public static Builder builder() {
        return new Builder();
    }

    public static void load(File configDir) {
        Configuration config = new Configuration(new File(configDir, "rdp-uav-targets.cfg"));
        config.load();
        rejectDeeplyEmbedded = config.getBoolean("rejectDeeplyEmbedded", "safety",
                rejectDeeplyEmbedded, "Reject contacts whose feet are deeply inside solid ground.");
        allowHostileVehicles = config.getBoolean("allowHostileVehicles", "filter",
                allowHostileVehicles, "Allow hostile military/bandit vehicles as selectable contacts.");
        maximumEmbeddedDepth = config.getFloat("maximumEmbeddedDepth", "safety",
                (float) maximumEmbeddedDepth, 0.1F, 16F,
                "Maximum tolerated distance below the supporting block top.");
        if (config.hasChanged()) config.save();
    }

    public static boolean isSafeContact(World world, Entity entity) {
        if (world == null || entity == null || entity.isDead) return true;
        if (!rejectDeeplyEmbedded) return false;
        int y = (int) Math.floor(entity.posY);
        BlockPos support = new BlockPos(entity.posX, y, entity.posZ);
        if (!world.getBlockState(support).getMaterial().blocksMovement()) return false;
        double supportTop = support.getY() + 1.0D;
        return !(entity.posY >= supportTop - maximumEmbeddedDepth);
    }

    /** Policy used by operator crosshair selection; missions use this same gate. */
    public static boolean isSelectableContact(World world, Entity entity, EntityPlayer operator) {
        if (isSafeContact(world, entity)) return false;
        if (entity instanceof EntityPlayer
                && operator != null && operator.isOnSameTeam(entity)) return false;
        String id = EntityList.getKey(entity) == null ? "" : Objects.requireNonNull(EntityList.getKey(entity)).toString();
        return TargetClassifier.isHostileCandidate(id)
                || (allowHostileVehicles && TargetClassifier.isHostileVehicle(id));
    }

    public static final class Builder {
        private final List<String> include = new ArrayList<>();
        private final List<String> require = new ArrayList<>();
        private final List<String> exclude = new ArrayList<>();
        private boolean rejectEmbedded = rejectDeeplyEmbedded;
        private boolean allowVehicleTargets = allowHostileVehicles;
        private double minimumConfidence = 0D;

        public Builder include(String... values) {
            if (values == null) return this;
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) include.add(value.trim());
            }
            return this;
        }

        public Builder require(String... values) {
            if (values == null) return this;
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) require.add(value.trim());
            }
            return this;
        }

        public Builder exclude(String... values) {
            if (values == null) return this;
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) exclude.add(value.trim());
            }
            return this;
        }

        public Builder rejectDeeplyEmbedded(boolean value) {
            rejectEmbedded = value;
            return this;
        }

        public Builder allowHostileVehicles(boolean value) {
            allowVehicleTargets = value;
            return this;
        }

        public Builder withMinimumConfidence(double minimumConfidence) {
            this.minimumConfidence = Math.max(0D, minimumConfidence);
            return this;
        }

        public Decision evaluate(World world, Entity entity, EntityPlayer operator) {
            if (entity == null) return Decision.reject("NO_ENTITY");
            if (rejectEmbedded && isSafeContact(world, entity)) return Decision.reject("UNSAFE_CONTACT");
            if (entity instanceof EntityPlayer && operator != null && operator.isOnSameTeam(entity)) {
                return Decision.reject("ALLY");
            }
            String id = EntityList.getKey(entity) == null ? "" : Objects.requireNonNull(EntityList.getKey(entity)).toString();
            if (!include.isEmpty() && !containsAny(id, include)) return Decision.reject("INCLUDE_MISMATCH");
            if (!require.isEmpty() && !containsAny(id, require)) return Decision.reject("REQUIRE_MISMATCH");
            if (!exclude.isEmpty() && containsAny(id, exclude)) return Decision.reject("EXCLUDED");
            boolean hostile = TargetClassifier.isHostileCandidate(id)
                    || (allowVehicleTargets && TargetClassifier.isHostileVehicle(id));
            if (!hostile) return Decision.reject("NOT_HOSTILE");
            if (minimumConfidence > 0D && TargetClassifier.threatScore(id) < minimumConfidence) {
                return Decision.reject("LOW_CONFIDENCE");
            }
            return Decision.accept();
        }

        private static boolean containsAny(String value, List<String> candidates) {
            if (value == null) return false;
            for (String candidate : candidates) {
                if (candidate == null) continue;
                if (value.equalsIgnoreCase(candidate)
                        || value.toLowerCase().contains(candidate.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class Decision {
        private final boolean accepted;
        private final String reason;

        private Decision(boolean accepted, String reason) {
            this.accepted = accepted;
            this.reason = reason == null ? "" : reason;
        }

        public boolean isAccepted() { return accepted; }
        public String getReason() { return reason; }

        public static Decision accept() { return new Decision(true, "OK"); }
        public static Decision reject(String reason) { return new Decision(false, reason); }
    }
}
