package net.vas.rdp.uav.target;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.world.World;

import java.util.*;

/** Single authoritative contact state for one server mission lifecycle. */
public final class ContactTracker {
    private static final long DEFAULT_LOST_GRACE_TICKS = 40L;
    private final Map<UUID, MutableContact> contacts = new HashMap<>();
    private final long lostGraceTicks;

    public ContactTracker() { this(DEFAULT_LOST_GRACE_TICKS); }
    public ContactTracker(long lostGraceTicks) {
        if (lostGraceTicks < 0L) throw new IllegalArgumentException("Lost grace must not be negative");
        this.lostGraceTicks = lostGraceTicks;
    }

    public ContactSnapshot observe(World world, Entity entity, double distance, long tick,
                                   ContactLifecycle requestedState) {
        if (world == null || entity == null || entity.isDead) return null;
        String registry = EntityList.getKey(entity) == null ? "" : Objects.requireNonNull(EntityList.getKey(entity)).toString();
        net.minecraft.util.math.AxisAlignedBB bounds = entity.getEntityBoundingBox();
        return observe(world.provider.getDimension(), entity.getUniqueID(), registry,
                entity.posX, entity.posY, entity.posZ, bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ, distance, tick, requestedState);
    }

    public ContactSnapshot observe(int dimension, UUID id, String registry, double x, double y,
                                   double z, double distance, long tick, ContactLifecycle requestedState) {
        return observe(dimension, id, registry, x, y, z, x, y, z, x, y, z,
                distance, tick, requestedState);
    }

    public ContactSnapshot observe(int dimension, UUID id, String registry, double x, double y,
                                   double z, double minX, double minY, double minZ,
                                   double maxX, double maxY, double maxZ, double distance,
                                   long tick, ContactLifecycle requestedState) {
        if (id == null || registry == null || registry.trim().isEmpty()
                || Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)
                || Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)
                || !validBounds(minX, minY, minZ, maxX, maxY, maxZ)) {
            throw new IllegalArgumentException("Valid contact identity and position are required");
        }
        MutableContact contact = contacts.get(id);
        if (contact == null) {
            contact = new MutableContact(id, registry, dimension, x, y, z, tick);
            contacts.put(id, contact);
        } else {
            contact.previousX = contact.x; contact.previousY = contact.y; contact.previousZ = contact.z;
            long elapsed = Math.max(1L, tick - contact.lastUpdateTick);
            contact.velocityX = (x - contact.x) / elapsed;
            contact.velocityY = (y - contact.y) / elapsed;
            contact.velocityZ = (z - contact.z) / elapsed;
            contact.x = x; contact.y = y; contact.z = z;
            contact.confidence = Math.min(1.0D, contact.confidence + 0.05D);
        }
        contact.minX = minX; contact.minY = minY; contact.minZ = minZ;
        contact.maxX = maxX; contact.maxY = maxY; contact.maxZ = maxZ;
        contact.lastDetectionTick = tick; contact.lastUpdateTick = tick;
        contact.distance = distance; contact.visible = true;
        contact.lifecycle = requestedState == null ? ContactLifecycle.TRACKING : requestedState;
        return contact.snapshot();
    }

    public ContactSnapshot markLost(UUID id, long tick) {
        MutableContact contact = contacts.get(id);
        if (contact == null) return null;
        if (tick - contact.lastDetectionTick > lostGraceTicks) {
            contact.lifecycle = ContactLifecycle.EXPIRED;
        } else {
            contact.lifecycle = ContactLifecycle.LOST;
            contact.confidence = Math.max(0.0D, contact.confidence - 0.2D);
        }
        contact.lastUpdateTick = tick; contact.visible = false;
        return contact.snapshot();
    }

    public ContactSnapshot get(UUID id) {
        MutableContact contact = id == null ? null : contacts.get(id);
        return contact == null ? null : contact.snapshot();
    }

    public List<ContactSnapshot> selectable() {
        List<ContactSnapshot> result = new ArrayList<>();
        for (MutableContact contact : contacts.values()) {
            if (contact.lifecycle != ContactLifecycle.LOST
                    && contact.lifecycle != ContactLifecycle.EXPIRED) {
                result.add(contact.snapshot());
            }
        }
        result.sort((left, right) -> left.getEntityId().toString().compareTo(right.getEntityId().toString()));
        return Collections.unmodifiableList(result);
    }

    public ContactSnapshot select(UUID id) {
        MutableContact contact = id == null ? null : contacts.get(id);
        if (contact == null || contact.lifecycle == ContactLifecycle.EXPIRED) return null;
        contact.lifecycle = ContactLifecycle.SELECTED;
        return contact.snapshot();
    }

    public ContactSnapshot cycle(UUID currentId, boolean next) {
        List<ContactSnapshot> available = selectable();
        if (available.isEmpty()) return null;
        int index = -1;
        for (int i = 0; i < available.size(); i++) {
            if (available.get(i).getEntityId().equals(currentId)) { index = i; break; }
        }
        int selected = index < 0 ? 0 : (index + (next ? 1 : available.size() - 1)) % available.size();
        return select(available.get(selected).getEntityId());
    }

    public void remove(UUID id) { if (id != null) contacts.remove(id); }

    private static boolean validBounds(double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        return Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
                && Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ)
                && maxX >= minX && maxY >= minY && maxZ >= minZ;
    }

    private static final class MutableContact {
        private final UUID id; private final String registry; private final int dimension;
        private double x, y, z, previousX, previousY, previousZ;
        private double minX, minY, minZ, maxX, maxY, maxZ;
        private double velocityX, velocityY, velocityZ, distance, confidence = 1.0D;
        private long lastDetectionTick, lastUpdateTick;
        private ContactLifecycle lifecycle = ContactLifecycle.DETECTED;
        private boolean visible = true;
        private MutableContact(UUID id, String registry, int dimension, double x, double y, double z, long tick) {
            this.id = id; this.registry = registry; this.dimension = dimension;
            this.x = x; this.y = y; this.z = z; this.previousX = x; this.previousY = y; this.previousZ = z;
            this.minX = x; this.minY = y; this.minZ = z;
            this.maxX = x; this.maxY = y; this.maxZ = z;
            this.lastDetectionTick = tick; this.lastUpdateTick = tick;
        }
        private ContactSnapshot snapshot() {
            return new ContactSnapshot(id, registry, dimension, x, y, z,
                    minX, minY, minZ, maxX, maxY, maxZ,
                    previousX, previousY, previousZ, velocityX, velocityY, velocityZ,
                    distance, confidence, 0.0D, 0.0D,
                    lastDetectionTick,
                    lastUpdateTick, lifecycle, visible);
        }
    }
}
