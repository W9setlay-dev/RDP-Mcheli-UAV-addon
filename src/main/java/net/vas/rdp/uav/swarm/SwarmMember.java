package net.vas.rdp.uav.swarm;

import java.util.UUID;

public final class SwarmMember {
    private final UUID aircraftId;
    private final String profile;
    private final boolean longRange;
    private final boolean payload;
    private SwarmMemberState state = SwarmMemberState.IDLE;
    private FormationSlot slot;
    private boolean available = true;
    private boolean operatorControlled;

    public SwarmMember(UUID aircraftId, String profile, boolean longRange, boolean payload) {
        if (aircraftId == null || profile == null || profile.trim().isEmpty()) {
            throw new IllegalArgumentException("Swarm member identity is required");
        }
        this.aircraftId = aircraftId;
        this.profile = profile;
        this.longRange = longRange;
        this.payload = payload;
    }

    public UUID getAircraftId() { return aircraftId; }
    public String getProfile() { return profile; }
    public boolean supportsLongRange() { return longRange; }
    public boolean hasPayload() { return payload; }
    public SwarmMemberState getState() { return state; }
    public void setState(SwarmMemberState state) {
        if (state == null) throw new IllegalArgumentException("Member state is required");
        this.state = state;
    }
    public FormationSlot getSlot() { return slot; }
    public void setSlot(FormationSlot slot) { this.slot = slot; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public boolean isOperatorControlled() { return operatorControlled; }
    public void setOperatorControlled(boolean value) { operatorControlled = value; }
}
