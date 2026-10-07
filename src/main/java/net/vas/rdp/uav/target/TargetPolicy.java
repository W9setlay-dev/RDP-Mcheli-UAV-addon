package net.vas.rdp.uav.target;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
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

    public enum ResultCode {
        ACCEPTED,
        NO_ENTITY,
        UNSAFE_CONTACT,
        ALLY,
        INCLUDE_MISMATCH,
        REQUIRE_MISMATCH,
        EXCLUDED,
        NOT_HOSTILE,
        LOW_CONFIDENCE
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Snapshot {
        private final int version;
        private final String domain;
        private final List<String> include;
        private final List<String> require;
        private final List<String> exclude;
        private final boolean rejectEmbedded;
        private final boolean allowVehicleTargets;
        private final double minimumConfidence;

        private Snapshot(Builder builder) {
            version = 1;
            domain = "ENTITY";
            include = Collections.unmodifiableList(new ArrayList<>(builder.include));
            require = Collections.unmodifiableList(new ArrayList<>(builder.require));
            exclude = Collections.unmodifiableList(new ArrayList<>(builder.exclude));
            rejectEmbedded = builder.rejectEmbedded;
            allowVehicleTargets = builder.allowVehicleTargets;
            minimumConfidence = builder.minimumConfidence;
        }

        public int getVersion() { return version; }
        public String getDomain() { return domain; }
        public List<String> getInclude() { return include; }
        public List<String> getRequire() { return require; }
        public List<String> getExclude() { return exclude; }
        public boolean isRejectEmbedded() { return rejectEmbedded; }
        public boolean isAllowVehicleTargets() { return allowVehicleTargets; }
        public double getMinimumConfidence() { return minimumConfidence; }
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
        boolean actualVehicleHostile = entity instanceof MCH_EntityAircraft
                && TargetClassifier.isHostileVehicle(entity);
        // An operator lock is an explicit crosshair selection.  When vehicle
        // targets are enabled, a loaded McHeli aircraft is a valid exact
        // contact even if its registry id is not in the hostile classifier.
        // Automatic discovery still applies the normal hostile filters.
        if (allowHostileVehicles && actualVehicleHostile) return true;
        return TargetClassifier.isHostileCandidate(id)
                || (allowHostileVehicles && TargetClassifier.isHostileVehicle(id));
    }

    /** Server gate for an operator's explicit crosshair UUID selection. */
    public static boolean isSelectableExactContact(World world, Entity entity,
                                                   EntityPlayer operator) {
        if (world == null || entity == null || entity.isDead
                || entity instanceof MCH_EntityAircraft
                && operator != null && entity == operator.getRidingEntity()) return false;
        if (entity instanceof EntityPlayer
                && operator != null && operator.isOnSameTeam(entity)) return false;
        // Explicit crosshair selection is authoritative for McHeli vehicle
        // contacts; do not reject an airborne/landed aircraft through the
        // generic embedded-contact heuristic.
        if (entity instanceof MCH_EntityAircraft && allowHostileVehicles) return true;
        if (rejectDeeplyEmbedded && isSafeContact(world, entity)) return false;
        return isSelectableContact(world, entity, operator);
    }

    /** Server-side conservative sightline check between the observer and target bounds. */
    public static boolean hasLineOfSight(World world, Entity observer, Entity target) {
        if (world == null || observer == null || target == null
                || observer.isDead || target.isDead
                || observer.world != world || target.world != world) return false;
        AxisAlignedBB observerBounds = observer.getEntityBoundingBox();
        AxisAlignedBB targetBounds = target.getEntityBoundingBox();
        Vec3d start = new Vec3d(
                (observerBounds.minX + observerBounds.maxX) * 0.5D,
                (observerBounds.minY + observerBounds.maxY) * 0.5D,
                (observerBounds.minZ + observerBounds.maxZ) * 0.5D);
        Vec3d end = new Vec3d(
                (targetBounds.minX + targetBounds.maxX) * 0.5D,
                (targetBounds.minY + targetBounds.maxY) * 0.5D,
                (targetBounds.minZ + targetBounds.maxZ) * 0.5D);
        if (!isFinite(start) || !isFinite(end)) return false;
        RayTraceResult obstruction = world.rayTraceBlocks(start, end, false, true, false);
        return obstruction == null || obstruction.typeOfHit != RayTraceResult.Type.BLOCK;
    }

    private static boolean isFinite(Vec3d position) {
        return position != null && Double.isFinite(position.x)
                && Double.isFinite(position.y) && Double.isFinite(position.z);
    }

    public static final class Builder {
        private final List<String> include = new ArrayList<>();
        private final List<String> require = new ArrayList<>();
        private final List<String> exclude = new ArrayList<>();
        private boolean rejectEmbedded = rejectDeeplyEmbedded;
        private boolean allowVehicleTargets = allowHostileVehicles;
        private double minimumConfidence = 0D;

        public Builder include(String... values) {
            addNormalized(include, values);
            return this;
        }

        public Builder require(String... values) {
            addNormalized(require, values);
            return this;
        }

        public Builder exclude(String... values) {
            addNormalized(exclude, values);
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

        public Snapshot snapshot() {
            return new Snapshot(this);
        }

        public Decision evaluate(World world, Entity entity, EntityPlayer operator) {
            if (entity == null) return Decision.reject(ResultCode.NO_ENTITY, "NO_ENTITY");
            if (rejectEmbedded && isSafeContact(world, entity)) {
                return Decision.reject(ResultCode.UNSAFE_CONTACT, "UNSAFE_CONTACT");
            }
            if (entity instanceof EntityPlayer && operator != null && operator.isOnSameTeam(entity)) {
                return Decision.reject(ResultCode.ALLY, "ALLY");
            }
            String id = EntityList.getKey(entity) == null ? "" : Objects.requireNonNull(EntityList.getKey(entity)).toString();
            if (!include.isEmpty() && !containsAny(id, include)) {
                return Decision.reject(ResultCode.INCLUDE_MISMATCH, "INCLUDE_MISMATCH");
            }
            if (!require.isEmpty() && !containsAny(id, require)) {
                return Decision.reject(ResultCode.REQUIRE_MISMATCH, "REQUIRE_MISMATCH");
            }
            if (!exclude.isEmpty() && containsAny(id, exclude)) {
                return Decision.reject(ResultCode.EXCLUDED, "EXCLUDED");
            }
            boolean actualVehicleHostile = entity instanceof MCH_EntityAircraft
                    && TargetClassifier.isHostileVehicle(entity);
            if (allowVehicleTargets && actualVehicleHostile) return Decision.accept();
            boolean hostile = TargetClassifier.isHostileCandidate(id)
                    || (allowVehicleTargets && TargetClassifier.isHostileVehicle(id));
            if (!hostile) return Decision.reject(ResultCode.NOT_HOSTILE, "NOT_HOSTILE");
            if (minimumConfidence > 0D && TargetClassifier.threatScore(id) < minimumConfidence) {
                return Decision.reject(ResultCode.LOW_CONFIDENCE, "LOW_CONFIDENCE");
            }
            return Decision.accept();
        }

        private static void addNormalized(List<String> destination, String[] values) {
            if (values == null) return;
            for (String value : values) {
                if (value == null) continue;
                String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
                if (!normalized.isEmpty() && !destination.contains(normalized)) {
                    destination.add(normalized);
                }
            }
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
        private final ResultCode code;
        private final String reason;

        private Decision(boolean accepted, ResultCode code, String reason) {
            this.accepted = accepted;
            this.code = code == null ? (accepted ? ResultCode.ACCEPTED : ResultCode.NO_ENTITY) : code;
            this.reason = reason == null ? "" : reason;
        }

        public boolean isAccepted() { return accepted; }
        public ResultCode getCode() { return code; }
        public String getReason() { return reason; }

        public static Decision accept() {
            return new Decision(true, ResultCode.ACCEPTED, "OK");
        }

        public static Decision reject(String reason) {
            return new Decision(false, inferCode(reason), reason);
        }

        public static Decision reject(ResultCode code, String reason) {
            return new Decision(false, code, reason);
        }

        private static ResultCode inferCode(String reason) {
            if (reason == null) return ResultCode.NO_ENTITY;
            try {
                return ResultCode.valueOf(reason);
            } catch (IllegalArgumentException ignored) {
                return ResultCode.NO_ENTITY;
            }
        }
    }
}
