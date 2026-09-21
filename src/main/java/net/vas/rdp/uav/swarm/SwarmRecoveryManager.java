package net.vas.rdp.uav.swarm;

import java.util.UUID;

/** Applies recoverable coordination transitions without aborting individual missions. */
public final class SwarmRecoveryManager {
    public void reconcile(SwarmRecord swarm) {
        if (swarm == null) throw new IllegalArgumentException("Swarm is required");
        int available = 0;
        for (SwarmMember member : swarm.getMembers()) {
            if (member.isAvailable() && member.getState() != SwarmMemberState.DESTROYED) available++;
        }
        if (available == 0 && !swarm.getMembers().isEmpty()) {
            swarm.setState(SwarmState.DEGRADED);
        } else if (available < swarm.getMembers().size()) {
            swarm.setState(SwarmState.PARTIAL);
        } else if (swarm.getState() == SwarmState.DEGRADED
                || swarm.getState() == SwarmState.PARTIAL) {
            swarm.setState(SwarmState.READY);
        }
        swarm.referenceMember();
    }

    public boolean canReassign(SwarmRecord swarm, UUID aircraftId) {
        if (swarm == null || aircraftId == null) return false;
        for (SwarmMember member : swarm.getMembers()) {
            if (aircraftId.equals(member.getAircraftId())) {
                return !member.isAvailable() || member.getState() == SwarmMemberState.DESTROYED;
            }
        }
        return false;
    }
}
