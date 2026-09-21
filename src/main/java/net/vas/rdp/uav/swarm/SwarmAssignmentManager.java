package net.vas.rdp.uav.swarm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side role assignment state; it does not issue movement commands. */
public final class SwarmAssignmentManager {
    private final Map<UUID, SwarmRole> assignments = new LinkedHashMap<>();

    public boolean assign(SwarmRecord swarm, UUID aircraftId, SwarmRole role) {
        if (swarm == null || aircraftId == null || role == null) return false;
        SwarmMember member = member(swarm, aircraftId);
        if (member == null || supports(member, role)) return false;
        assignments.put(aircraftId, role);
        member.setRole(role);
        return true;
    }

    public void restore(SwarmRecord swarm, UUID aircraftId, SwarmRole role) {
        if (swarm == null || aircraftId == null || role == null) return;
        SwarmMember member = member(swarm, aircraftId);
        if (member == null || supports(member, role)) return;
        assignments.put(aircraftId, role);
        member.setRole(role);
    }

    public void release(UUID aircraftId) {
        if (aircraftId != null) assignments.remove(aircraftId);
    }

    public SwarmRole role(UUID aircraftId) {
        return aircraftId == null ? null : assignments.get(aircraftId);
    }

    public Map<UUID, SwarmRole> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(assignments));
    }

    private static SwarmMember member(SwarmRecord swarm, UUID aircraftId) {
        for (SwarmMember member : swarm.getMembers()) {
            if (aircraftId.equals(member.getAircraftId())) return member;
        }
        return null;
    }

    private static boolean supports(SwarmMember member, SwarmRole role) {
        switch (role) {
            case COMMANDER:
            case SEARCHER:
            case RESERVE:
                return false;
            case TRACKER:
            case SUPPORT:
                return !member.supportsLongRange();
            case EXECUTOR:
                return !member.hasPayload();
            default:
                return true;
        }
    }
}
