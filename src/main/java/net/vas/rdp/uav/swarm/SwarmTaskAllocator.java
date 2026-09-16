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
        List<SwarmMember> result = new ArrayList<SwarmMember>();
        for (SwarmMember member : swarm.getMembers()) {
            if (!member.isAvailable() || member.isOperatorControlled()) continue;
            boolean needsPayload = task == SwarmTask.POINT_OBSERVATION
                    || task == SwarmTask.ESCORT;
            if (needsPayload && !member.hasPayload()) continue;
            result.add(member);
        }
        result.sort(new Comparator<SwarmMember>() {
            public int compare(SwarmMember left, SwarmMember right) {
                int profile = left.getProfile().compareTo(right.getProfile());
                return profile != 0 ? profile
                        : left.getAircraftId().toString().compareTo(right.getAircraftId().toString());
            }
        });
        if (result.size() > limit) return new ArrayList<SwarmMember>(result.subList(0, limit));
        return result;
    }
}
