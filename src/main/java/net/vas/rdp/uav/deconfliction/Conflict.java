package net.vas.rdp.uav.deconfliction;

import java.util.UUID;

/** Immutable server-detected violation of the configured separation volume. */
public final class Conflict {
    private final UUID aircraftA;
    private final UUID aircraftB;
    private final long firstConflictTick;
    private final ConflictType conflictType;
    private final double minimumDistance;

    /** Creates a conflict with distinct aircraft and finite separation distance. */
    public Conflict(UUID aircraftA, UUID aircraftB, long firstConflictTick,
                    ConflictType conflictType, double minimumDistance) {
        if (aircraftA == null || aircraftB == null || aircraftA.equals(aircraftB)
                || firstConflictTick < 0L || conflictType == null
                || !Double.isFinite(minimumDistance) || minimumDistance < 0.0D) {
            throw new IllegalArgumentException("Invalid airspace conflict");
        }
        if (aircraftA.toString().compareTo(aircraftB.toString()) <= 0) {
            this.aircraftA = aircraftA;
            this.aircraftB = aircraftB;
        } else {
            this.aircraftA = aircraftB;
            this.aircraftB = aircraftA;
        }
        this.firstConflictTick = firstConflictTick;
        this.conflictType = conflictType;
        this.minimumDistance = minimumDistance;
    }

    public UUID getAircraftA() { return aircraftA; }
    public UUID getAircraftB() { return aircraftB; }
    public long getFirstConflictTick() { return firstConflictTick; }
    public ConflictType getConflictType() { return conflictType; }
    public double getMinimumDistance() { return minimumDistance; }
}
