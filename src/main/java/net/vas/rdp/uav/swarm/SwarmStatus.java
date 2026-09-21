package net.vas.rdp.uav.swarm;

import java.util.UUID;

/** Immutable status projection suitable for HUD/network consumers. */
public final class SwarmStatus {
    private final UUID swarmId;
    private final SwarmState state;
    private final SwarmMissionState missionState;
    private final UUID commanderId;
    private final int members;
    private final int availableMembers;
    private final UUID targetId;
    private final SwarmTargetState targetState;

    private SwarmStatus(SwarmRecord swarm) {
        swarmId = swarm.getSwarmId();
        state = swarm.getState();
        missionState = swarm.getMissionState();
        commanderId = swarm.getCommanderId();
        members = swarm.getMembers().size();
        int available = 0;
        for (SwarmMember member : swarm.getMembers()) {
            if (member.isAvailable()) available++;
        }
        availableMembers = available;
        targetId = swarm.getTargetRegistry().getTargetId();
        targetState = swarm.getTargetRegistry().getState();
    }

    public static SwarmStatus from(SwarmRecord swarm) {
        if (swarm == null) throw new IllegalArgumentException("Swarm is required");
        return new SwarmStatus(swarm);
    }

    public UUID getSwarmId() { return swarmId; }
    public SwarmState getState() { return state; }
    public SwarmMissionState getMissionState() { return missionState; }
    public UUID getCommanderId() { return commanderId; }
    public int getMembers() { return members; }
    public int getAvailableMembers() { return availableMembers; }
    public UUID getTargetId() { return targetId; }
    public SwarmTargetState getTargetState() { return targetState; }
}
