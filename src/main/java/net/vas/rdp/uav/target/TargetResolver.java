package net.vas.rdp.uav.target;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class TargetResolver {
    public List<EntityCandidate> find(World world, double x, double y, double z, double radius,
                                      TargetSpec specification) {
        if (world == null || specification == null || radius <= 0) {
            throw new IllegalArgumentException("World, target specification, and radius are required");
        }
        List<EntityCandidate> result = new ArrayList<EntityCandidate>();
        double maxDistance = radius * radius;
        for (Entity entity : world.loadedEntityList) {
            if (entity == null || entity.isDead || entity.dimension != world.provider.getDimension()) continue;
            double distance = entity.getDistanceSq(x, y, z);
            if (distance > maxDistance) continue;
            ResourceLocation key = EntityList.getKey(entity);
            if (key == null) continue;
            String id = key.toString();
            boolean matches = specification.getMode() == TargetSelectionMode.MANUAL
                    ? id.equals(specification.getRegistryId())
                    : id.startsWith(specification.getNamespace() + ":");
            if (matches) result.add(new EntityCandidate(entity, id, distance));
        }
        Collections.sort(result, new Comparator<EntityCandidate>() {
            public int compare(EntityCandidate left, EntityCandidate right) {
                return Double.compare(left.distanceSquared, right.distanceSquared);
            }
        });
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
}
