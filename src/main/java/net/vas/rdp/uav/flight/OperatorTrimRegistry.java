package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.profile.UavVehicleProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Transient server session storage; trim is never persisted with a mission. */
public final class OperatorTrimRegistry {
    private static final Map<UUID, OperatorTrimState> STATES = new ConcurrentHashMap<>();

    private OperatorTrimRegistry() { }

    public static boolean accept(UUID aircraftId, UUID missionId, long sequence,
                                 long clientTick, double yawDelta, double pitchDelta, long serverTick,
                                 UavVehicleProfile profile) {
        if (aircraftId == null || missionId == null || profile == null) return false;
        OperatorTrimState state = STATES.get(aircraftId);
        if (state == null || !missionId.equals(state.getMissionId())) {
            OperatorTrimState replacement = new OperatorTrimState(missionId);
            STATES.put(aircraftId, replacement);
            state = replacement;
        }
        return state.accept(sequence, clientTick, yawDelta, pitchDelta, serverTick, profile);
    }

    public static OperatorTrimState.Snapshot snapshot(UUID aircraftId, UUID missionId,
                                                       long tick) {
        if (aircraftId == null || missionId == null) return null;
        OperatorTrimState state = STATES.get(aircraftId);
        if (state == null) return null;
        OperatorTrimState.Snapshot value = state.snapshot(tick);
        if (!missionId.equals(value.getMissionId())) return null;
        return value;
    }

    public static void clear(UUID aircraftId, UUID missionId) {
        if (aircraftId == null) return;
        OperatorTrimState state = STATES.get(aircraftId);
        if (state != null && (missionId == null || missionId.equals(state.getMissionId()))) {
            STATES.remove(aircraftId, state);
        }
    }
}
