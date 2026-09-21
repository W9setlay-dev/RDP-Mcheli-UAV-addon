package net.vas.rdp.uav.swarm;

import java.util.UUID;

/** Read/write swarm-side state for one UAV; movement remains mission-owned. */
public final class SwarmAgent {
    private final UUID uavId;
    private final UUID swarmId;
    private final UavCapabilities capabilities;
    private SwarmRole role = SwarmRole.RESERVE;
    private SwarmMemberState state = SwarmMemberState.IDLE;
    private boolean available = true;

    public SwarmAgent(UUID uavId, UUID swarmId, UavCapabilities capabilities) {
        if (uavId == null || swarmId == null || capabilities == null) {
            throw new IllegalArgumentException("Swarm agent identity and capabilities are required");
        }
        this.uavId = uavId;
        this.swarmId = swarmId;
        this.capabilities = capabilities;
    }

    public UUID getUavId() { return uavId; }
    public UUID getSwarmId() { return swarmId; }
    public UavCapabilities getCapabilities() { return capabilities; }
    public SwarmRole getRole() { return role; }
    public SwarmMemberState getState() { return state; }
    public boolean isAvailable() { return available; }

    public void setRole(SwarmRole value) {
        if (!capabilities.supports(value)) {
            throw new IllegalArgumentException("UAV capabilities do not support swarm role");
        }
        role = value;
    }

    public void setState(SwarmMemberState value) {
        if (value == null) throw new IllegalArgumentException("Agent state is required");
        state = value;
    }

    public void setAvailable(boolean value) { available = value; }
}
