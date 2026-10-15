package net.vas.rdp.uav.formation;

import net.vas.rdp.uav.mission.MissionState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Server-side role policy; outputs constraints and never mutates mission state. */
public final class WingmanTaskingService {
    /** Produces deterministic per-aircraft constraints for the current mission state. */
    public Map<UUID, RoleConstraints> generateRoleConstraints(
            MissionState state, Map<UUID, WingmanRole> roles) {
        if (state == null || roles == null) {
            throw new IllegalArgumentException("Mission state and swarm roles are required");
        }
        TreeMap<UUID, WingmanRole> ordered = new TreeMap<>(
                (left, right) -> left.toString().compareTo(right.toString()));
        ordered.putAll(roles);
        LinkedHashMap<UUID, RoleConstraints> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, WingmanRole> entry : ordered.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Aircraft UUID and wingman role are required");
            }
            result.put(entry.getKey(), constraints(state, entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static RoleConstraints constraints(MissionState state, WingmanRole role) {
        if (state == MissionState.WAITING_FOR_TARGET || state == MissionState.SEARCHING) {
            return new RoleConstraints(role == WingmanRole.SCOUT ? 1.5D : 1.0D,
                    false, role == WingmanRole.LEADER ? 0 : 1,
                    role == WingmanRole.WINGMAN || role == WingmanRole.STRIKER
                            || role == WingmanRole.SUPPORT);
        }
        if (state == MissionState.TARGET_LOCKED) {
            return new RoleConstraints(1.0D, role == WingmanRole.STRIKER,
                    role == WingmanRole.DECOY ? 0 : 1, role != WingmanRole.DECOY);
        }
        if (state == MissionState.HOLDING || state == MissionState.REPLAN) {
            return new RoleConstraints(1.0D, false, 1, true);
        }
        return new RoleConstraints(1.0D, false,
                role == WingmanRole.LEADER ? 0 : 1, false);
    }
}
