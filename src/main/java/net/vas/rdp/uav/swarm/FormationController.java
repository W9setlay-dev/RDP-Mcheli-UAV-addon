package net.vas.rdp.uav.swarm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Computes coordination slots only; it never writes aircraft movement. */
public final class FormationController {
    public Map<UUID, FormationSlot> slots(SwarmRecord swarm) {
        if (swarm == null) throw new IllegalArgumentException("Swarm is required");
        Map<UUID, FormationSlot> result = new LinkedHashMap<>();
        for (SwarmMember member : swarm.getMembers()) {
            if (member.getSlot() != null) result.put(member.getAircraftId(), member.getSlot());
        }
        return Collections.unmodifiableMap(result);
    }

    public boolean violatesSpacing(FormationSlot first, FormationSlot second,
                                   double minimumHorizontal, double minimumVertical) {
        if (first == null || second == null || minimumHorizontal < 0 || minimumVertical < 0) {
            throw new IllegalArgumentException("Formation slots and spacing are required");
        }
        double horizontal = Math.hypot(first.getX() - second.getX(), first.getZ() - second.getZ());
        return horizontal < minimumHorizontal
                || Math.abs(first.getY() - second.getY()) < minimumVertical;
    }
}
