package net.vas.rdp.uav.swarm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic capability filter; mission creation remains MissionManager-owned. */
public final class SwarmTaskAllocator {
    public List<SwarmMember> suitable(SwarmRecord swarm, SwarmTask task, int limit) {
        if (swarm == null || task == null || limit < 0) {
            throw new IllegalArgumentException("Swarm, task, and limit are required");
        }
        List<SwarmMember> result = new ArrayList<>();
        for (SwarmMember member : swarm.getMembers()) {
            if (!member.isAvailable() || member.isOperatorControlled()) continue;
            boolean needsPayload = task == SwarmTask.POINT_OBSERVATION
                    || task == SwarmTask.ESCORT;
            if (needsPayload && !member.hasPayload()) continue;
            result.add(member);
        }
        result.sort(Comparator.comparing(SwarmMember::getProfile)
                .thenComparing(left -> left.getAircraftId()
                        .toString()));
        if (result.size() > limit) return new ArrayList<>(result.subList(0, limit));
        return result;
    }
}
