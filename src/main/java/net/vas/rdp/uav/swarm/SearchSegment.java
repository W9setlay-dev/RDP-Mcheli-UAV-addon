package net.vas.rdp.uav.swarm;

import java.util.UUID;

public final class SearchSegment {
    private final UUID segmentId;
    private final int index;
    private UUID assignedAircraft;
    public SearchSegment(UUID segmentId, int index) {
        if (segmentId == null || index < 0) throw new IllegalArgumentException("Invalid search segment");
        this.segmentId = segmentId; this.index = index;
    }
    public UUID getSegmentId() { return segmentId; }
    public int getIndex() { return index; }
    public UUID getAssignedAircraft() { return assignedAircraft; }
    public void assign(UUID aircraftId) { assignedAircraft = aircraftId; }
}
