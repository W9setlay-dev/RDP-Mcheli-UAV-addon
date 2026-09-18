package net.vas.rdp.uav.weapon;

import java.util.UUID;

/**
 * Authoritative server objective. Entity identity is UUID-based and survives
 * unloaded/reloaded contact instances; coordinates are only the latest fix.
 */
public final class GuidedStrikeObjective {
    private final UUID objectiveId;
    private final GuidedObjectiveType type;
    private UUID contactId;
    private final int dimension;
    private double x;
    private double y;
    private double z;
    private long lastContactTick;
    private GuidedObjectiveState state = GuidedObjectiveState.CREATED;

    private GuidedStrikeObjective(UUID objectiveId, GuidedObjectiveType type, UUID contactId,
                                  int dimension, double x, double y, double z, long tick) {
        if (objectiveId == null || type == null || type == GuidedObjectiveType.ENTITY && contactId == null
                || finite(x) || finite(y) || finite(z)) {
            throw new IllegalArgumentException("Invalid guided objective");
        }
        this.objectiveId = objectiveId;
        this.type = type;
        this.contactId = contactId;
        this.dimension = dimension;
        this.x = x; this.y = y; this.z = z;
        this.lastContactTick = tick;
    }

    public static GuidedStrikeObjective entity(UUID objectiveId, UUID contactId, int dimension,
                                                double x, double y, double z, long tick) {
        return new GuidedStrikeObjective(objectiveId, GuidedObjectiveType.ENTITY, contactId,
                dimension, x, y, z, tick);
    }

    public static GuidedStrikeObjective square(UUID objectiveId, int dimension,
                                               double x, double y, double z, long tick) {
        return new GuidedStrikeObjective(objectiveId, GuidedObjectiveType.SQUARE, null,
                dimension, x, y, z, tick);
    }

    public void authorize() { transition(GuidedObjectiveState.AUTHORIZED); }
    public void beginTracking() { transition(GuidedObjectiveState.TRACKING); }
    public void release() { transition(GuidedObjectiveState.RELEASED); }
    public void complete() { transition(GuidedObjectiveState.COMPLETE); }
    public void abort() { if (state != GuidedObjectiveState.COMPLETE) state = GuidedObjectiveState.ABORTED; }
    public void loseContact() {
        if (type == GuidedObjectiveType.ENTITY) state = GuidedObjectiveState.LOST;
    }
    public void updateContact(double x, double y, double z, long tick) {
        if (type != GuidedObjectiveType.ENTITY || finite(x) || finite(y) || finite(z)
                || tick < lastContactTick || state == GuidedObjectiveState.COMPLETE
                || state == GuidedObjectiveState.ABORTED) {
            throw new IllegalArgumentException("Invalid contact update");
        }
        this.x = x; this.y = y; this.z = z; this.lastContactTick = tick;
        if (state == GuidedObjectiveState.LOST) state = GuidedObjectiveState.TRACKING;
    }
    public void switchContact(UUID contactId, double x, double y, double z, long tick) {
        if (type != GuidedObjectiveType.ENTITY || contactId == null || finite(x) || finite(y)
                || finite(z) || tick < lastContactTick || state == GuidedObjectiveState.COMPLETE
                || state == GuidedObjectiveState.ABORTED) {
            throw new IllegalArgumentException("Invalid contact switch");
        }
        this.contactId = contactId;
        this.x = x; this.y = y; this.z = z; this.lastContactTick = tick;
        if (state == GuidedObjectiveState.LOST) state = GuidedObjectiveState.TRACKING;
    }
    private void transition(GuidedObjectiveState next) {
        if (state == GuidedObjectiveState.COMPLETE || state == GuidedObjectiveState.ABORTED) {
            throw new IllegalStateException("Objective is terminal");
        }
        state = next;
    }
    private static boolean finite(double v) { return Double.isNaN(v) || Double.isInfinite(v); }
    public UUID getObjectiveId() { return objectiveId; }
    public GuidedObjectiveType getType() { return type; }
    public UUID getContactId() { return contactId; }
    public int getDimension() { return dimension; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public long getLastContactTick() { return lastContactTick; }
    public GuidedObjectiveState getState() { return state; }
}
