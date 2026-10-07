package net.vas.rdp.uav.target;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class TargetResolver {
    public Entity findLoaded(World world, UUID targetId) {
        return resolveLoaded(world, targetId).getEntity();
    }

    public Resolution resolveLoaded(World world, UUID targetId) {
        if (world == null || targetId == null) {
            return new Resolution(Resolution.Code.INVALID_TARGET, null);
        }
        for (Entity entity : world.loadedEntityList) {
            if (entity == null || !targetId.equals(entity.getUniqueID())) continue;
            if (entity.isDead) return new Resolution(Resolution.Code.TARGET_DEAD, null);
            if (entity.world != world
                    || entity.dimension != world.provider.getDimension()) {
                return new Resolution(Resolution.Code.TARGET_WRONG_DIMENSION, null);
            }
            return new Resolution(Resolution.Code.LOADED, entity);
        }
        return new Resolution(Resolution.Code.TARGET_NOT_LOADED, null);
    }

    public Entity findLoadedByRegistryId(World world, String registryId) {
        if (world == null || registryId == null || registryId.trim().isEmpty()) return null;
        for (Entity entity : world.loadedEntityList) {
            if (entity == null || entity.isDead
                    || entity.dimension != world.provider.getDimension()) continue;
            ResourceLocation key = EntityList.getKey(entity);
            if (key != null && registryId.equals(key.toString())) return entity;
        }
        return null;
    }

    public List<EntityCandidate> find(World world, double x, double y, double z, double radius,
                                      TargetSpec specification) {
        return find(world, x, y, z, radius, specification, null);
    }

    /**
     * Resolves candidates through the existing discovery path. The optional
     * operator is passed to the shared policy evaluator for hostile selection.
     */
    public List<EntityCandidate> find(World world, double x, double y, double z, double radius,
                                      TargetSpec specification, EntityPlayer operator) {
        return find(world, x, y, z, radius, specification, operator,
                TargetPolicy.builder().snapshot());
    }

    public List<EntityCandidate> find(World world, double x, double y, double z, double radius,
                                      TargetSpec specification, EntityPlayer operator,
                                      TargetPolicy.Snapshot policy) {
        if (world == null || specification == null || radius <= 0) {
            throw new IllegalArgumentException("World, target specification, and radius are required");
        }
        if (policy == null) throw new IllegalArgumentException("Target policy is required");
        List<EntityCandidate> result = new ArrayList<>();
        double maxDistance = radius * radius;
        AxisAlignedBB searchBox = new AxisAlignedBB(
                x - radius, y - radius, z - radius,
                x + radius, y + radius, z + radius);
        TargetPolicy.Builder policyBuilder = TargetPolicy.builder()
                .include(policy.getInclude().toArray(new String[0]))
                .require(policy.getRequire().toArray(new String[0]))
                .exclude(policy.getExclude().toArray(new String[0]))
                .rejectDeeplyEmbedded(policy.isRejectEmbedded())
                .allowHostileVehicles(policy.isAllowVehicleTargets())
                .withMinimumConfidence(policy.getMinimumConfidence());
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, searchBox)) {
            if (entity == null || entity.isDead || entity.dimension != world.provider.getDimension()) continue;
            if (TargetPolicy.isSafeContact(world, entity)) continue;
            double distance = entity.getDistanceSq(x, y, z);
            if (distance > maxDistance) continue;
            ResourceLocation key = EntityList.getKey(entity);
            if (key == null) continue;
            String id = key.toString();
            if (!policyBuilder.evaluate(world, entity, operator).isAccepted()) continue;
            boolean matches;
            if (specification.getMode() == TargetSelectionMode.MANUAL) {
                matches = id.equals(specification.getRegistryId());
            } else if (TargetClassifier.isHostileNamespace(specification.getNamespace())) {
                matches = true;
            } else {
                matches = id.startsWith(specification.getNamespace() + ":");
            }
            if (matches) result.add(new EntityCandidate(entity, id, distance));
        }
        result.sort(Comparator.comparingDouble(left -> left.distanceSquared));
        return result;
    }

    public static final class EntityCandidate {
        private final Entity entity;
        private final String registryId;
        private final double distanceSquared;
        private EntityCandidate(Entity entity, String registryId, double distanceSquared) {
            this.entity = entity; this.registryId = registryId; this.distanceSquared = distanceSquared;
        }
        public Entity getEntity() { return entity; }
        public UUID getUuid() { return entity.getUniqueID(); }
        public String getRegistryId() { return registryId; }
        public double getDistanceSquared() { return distanceSquared; }
    }

    public static final class Resolution {
        public enum Code {
            LOADED,
            INVALID_TARGET,
            TARGET_NOT_LOADED,
            TARGET_DEAD,
            TARGET_WRONG_DIMENSION
        }

        private final Code code;
        private final Entity entity;

        private Resolution(Code code, Entity entity) {
            this.code = code;
            this.entity = entity;
        }

        public Code getCode() { return code; }
        public Entity getEntity() { return entity; }
    }
}
