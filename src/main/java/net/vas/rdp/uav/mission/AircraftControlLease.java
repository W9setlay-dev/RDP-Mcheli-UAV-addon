package net.vas.rdp.uav.mission;

import java.util.UUID;

/**
 * Server-authoritative lease for one aircraft and one mission tick window.
 *
 * <p>The lease is intentionally narrow: it only grants movement authority for a
 * single aircraft to the owning mission during a bounded tick range. It does not
 * claim target identity or replace lifecycle state.</p>
 */
public final class AircraftControlLease {
    private final UUID aircraftId;
    private final UUID missionId;
    private final ControlOwner owner;
    private final long issuedTick;
    private final long expiresAtTick;
    private final String reason;

    private AircraftControlLease(UUID aircraftId, UUID missionId, ControlOwner owner,
                                 long issuedTick, long expiresAtTick, String reason) {
        if (aircraftId == null || missionId == null || owner == null) {
            throw new IllegalArgumentException("Aircraft control lease requires aircraft, mission, and owner");
        }
        if (issuedTick < 0L || expiresAtTick < issuedTick) {
            throw new IllegalArgumentException("Invalid lease tick window");
        }
        this.aircraftId = aircraftId;
        this.missionId = missionId;
        this.owner = owner;
        this.issuedTick = issuedTick;
        this.expiresAtTick = expiresAtTick;
        this.reason = reason == null || reason.trim().isEmpty() ? "MISSION_CONTROL" : reason.trim();
    }

    public static AircraftControlLease forMission(RDPMission mission, UUID aircraftId,
                                                  long tick, long expiryTick, String reason) {
        if (mission == null) throw new IllegalArgumentException("Mission is required");
        return new AircraftControlLease(aircraftId, mission.getMissionId(),
                mission.getControlOwner() == null ? ControlOwner.MISSION_AI : mission.getControlOwner(),
                tick, expiryTick, reason);
    }

    public boolean isExpired(long tick) {
        return tick >= expiresAtTick;
    }

    public boolean matches(UUID aircraftId, UUID missionId) {
        return aircraftId != null && missionId != null
                && this.aircraftId.equals(aircraftId)
                && this.missionId.equals(missionId);
    }

    public UUID getAircraftId() { return aircraftId; }
    public UUID getMissionId() { return missionId; }
    public ControlOwner getOwner() { return owner; }
    public long getIssuedTick() { return issuedTick; }
    public long getExpiresAtTick() { return expiresAtTick; }
    public String getReason() { return reason; }
}
