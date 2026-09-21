package net.vas.rdp.uav.swarm;

import java.util.UUID;

/** Reconciles assignments after member loss without issuing flight commands. */
public final class SwarmReplanner {
    public boolean reassignUnavailableTarget(SwarmRecord swarm, UUID unavailableAircraft) {
        if (swarm == null || unavailableAircraft == null) return false;
        UUID target = null;
        for (SwarmMember member : swarm.getMembers()) {
            if (unavailableAircraft.equals(member.getAircraftId())) {
                target = member.getAssignedTarget();
                break;
            }
        }
        if (target == null) return false;
        swarm.releaseTarget(unavailableAircraft);
        for (SwarmMember candidate : swarm.getMembers()) {
            if (unavailableAircraft.equals(candidate.getAircraftId())
                    || !candidate.isAvailable() || candidate.isOperatorControlled()
                    || !candidate.hasPayload()) continue;
            if (swarm.assignTarget(candidate.getAircraftId(), target,
                    swarm.isAllowDuplicateTargets())) {
                return true;
            }
        }
        for (SwarmMember member : swarm.getMembers()) {
            if (unavailableAircraft.equals(member.getAircraftId())) {
                member.setAssignedTarget(target);
                break;
            }
        }
        return false;
    }
}
