package net.vas.rdp.uav.deconfliction;

import java.util.UUID;

/** Immutable server decision proposing one bounded conflict-resolution action. */
public final class Resolution {
    public enum Type {
        ALTITUDE_OFFSET,
        TIME_DELAY,
        SLOT_REASSIGNMENT
    }

    private final Type type;
    private final UUID aircraftUuid;
    private final double offsetBlocks;
    private final int delayTicks;
    private final int newSlotIndex;

    /** Creates a typed resolution proposal; unused fields must be zero or -1. */
    public Resolution(Type type, UUID aircraftUuid, double offsetBlocks,
                      int delayTicks, int newSlotIndex) {
        if (type == null || aircraftUuid == null || !Double.isFinite(offsetBlocks)
                || offsetBlocks < 0.0D || delayTicks < 0 || newSlotIndex < -1) {
            throw new IllegalArgumentException("Invalid deconfliction resolution");
        }
        this.type = type;
        this.aircraftUuid = aircraftUuid;
        this.offsetBlocks = offsetBlocks;
        this.delayTicks = delayTicks;
        this.newSlotIndex = newSlotIndex;
    }

    public Type getType() { return type; }
    public UUID getAircraftUuid() { return aircraftUuid; }
    public double getOffsetBlocks() { return offsetBlocks; }
    public int getDelayTicks() { return delayTicks; }
    public int getNewSlotIndex() { return newSlotIndex; }
}
