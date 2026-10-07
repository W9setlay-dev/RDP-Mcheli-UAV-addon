package net.vas.rdp.uav.mission;

import java.util.UUID;

public final class AircraftRecord {
    private final UUID aircraftId;
    private final UUID ownerId;
    private int dimension;
    private String type;
    private boolean loaded;
    private boolean operational;
    private AircraftStatus status;
    private UUID missionId;

    public AircraftRecord(UUID aircraftId, UUID ownerId, int dimension, String type,
                          boolean loaded, boolean operational) {
        if (aircraftId == null || ownerId == null || type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Aircraft identity is required");
        }
        this.aircraftId = aircraftId;
        this.ownerId = ownerId;
        this.dimension = dimension;
        this.type = type;
        this.loaded = loaded;
        this.operational = operational;
        this.status = operational ? AircraftStatus.READY : AircraftStatus.UNAVAILABLE;
    }

    public boolean availableFor(UUID playerId) {
        return loaded && operational && ownerId.equals(playerId) && missionId == null
                && (status == AircraftStatus.READY || status == AircraftStatus.PARKED);
    }
    public boolean refreshRuntimeState(UUID requestingOwnerId, int dimension, String type,
                                       boolean loaded, boolean operational) {
        if (!ownerId.equals(requestingOwnerId) || type == null || type.trim().isEmpty()) {
            return false;
        }
        this.dimension = dimension;
        this.type = type.trim();
        this.loaded = loaded;
        this.operational = operational;
        if (missionId == null) {
            status = operational ? AircraftStatus.READY : AircraftStatus.UNAVAILABLE;
        } else {
            status = operational ? AircraftStatus.ON_MISSION : AircraftStatus.UNAVAILABLE;
        }
        return true;
    }
    public void assign(UUID missionId) {
        if (missionId == null || this.missionId != null || !operational) {
            throw new IllegalStateException("AIRCRAFT_UNAVAILABLE");
        }
        this.missionId = missionId;
        this.status = AircraftStatus.ON_MISSION;
    }
    public void release() { missionId = null; status = operational ? AircraftStatus.READY : AircraftStatus.UNAVAILABLE; }
    public boolean transfer(UUID expectedMission, UUID replacementMission) {
        if (expectedMission == null || replacementMission == null
                || !expectedMission.equals(missionId)) return false;
        missionId = replacementMission;
        status = AircraftStatus.ON_MISSION;
        return true;
    }
    public UUID getAircraftId() { return aircraftId; }
    public UUID getOwnerId() { return ownerId; }
    public int getDimension() { return dimension; }
    public String getType() { return type; }
    public boolean isLoaded() { return loaded; }
    public boolean isOperational() { return operational; }
    public AircraftStatus getStatus() { return status; }
    public UUID getMissionId() { return missionId; }
}
