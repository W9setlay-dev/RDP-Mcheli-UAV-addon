package net.vas.rdp.uav.target;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * Server-side target authority facade.
 *
 * <p>This preserves the existing resolver and contact-tracker behavior while
 * giving mission orchestration one boundary for target discovery, observation,
 * selection, and loss tracking. It does not replace authoritative mission
 * identity, which remains owned by {@code RDPMission.targetId}.</p>
 */
public final class TargetAuthority {
    private final TargetResolver resolver;
    private final ContactTracker contacts;

    public TargetAuthority() {
        this(new TargetResolver(), new ContactTracker());
    }

    public TargetAuthority(TargetResolver resolver, ContactTracker contacts) {
        if (resolver == null || contacts == null) {
            throw new IllegalArgumentException("Target resolver and tracker are required");
        }
        this.resolver = resolver;
        this.contacts = contacts;
    }

    public Entity findLoaded(World world, UUID targetId) {
        return resolver.findLoaded(world, targetId);
    }

    public Entity findLoadedByRegistryId(World world, String registryId) {
        return resolver.findLoadedByRegistryId(world, registryId);
    }

    public List<TargetResolver.EntityCandidate> find(World world, double x, double y, double z,
                                                      double radius, TargetSpec specification) {
        return resolver.find(world, x, y, z, radius, specification);
    }

    public List<TargetResolver.EntityCandidate> find(World world, double x, double y, double z,
                                                      double radius, TargetSpec specification,
                                                      EntityPlayer operator) {
        return resolver.find(world, x, y, z, radius, specification, operator);
    }

    public void observe(World world, Entity entity, double distance, long tick,
                        ContactLifecycle lifecycle) {
        contacts.observe(world, entity, distance, tick, lifecycle);
    }

    public void markLost(UUID targetId, long tick) {
        contacts.markLost(targetId, tick);
    }

    public ContactSnapshot get(UUID targetId) {
        return contacts.get(targetId);
    }

    public List<ContactSnapshot> selectable() {
        return contacts.selectable();
    }

    public void select(UUID targetId) {
        contacts.select(targetId);
    }
}
