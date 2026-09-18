package net.vas.rdp.uav.flight;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime operator locks for the AI kamikaze override.
 *
 * <p>The selected entity UUID is intentionally not persisted. A lock is an
 * active operator input and must be re-established after a restart.</p>
 */
public final class KamikazePilotOverride {
    private static final Map<UUID, UUID> LOCKS = new ConcurrentHashMap<>();

    private KamikazePilotOverride() { }

    public static void lock(UUID aircraftId, UUID targetId) {
        if (aircraftId == null || targetId == null) throw new IllegalArgumentException("Lock IDs are required");
        LOCKS.put(aircraftId, targetId);
    }

    public static UUID targetFor(UUID aircraftId) {
        return aircraftId == null ? null : LOCKS.get(aircraftId);
    }

    public static UUID clear(UUID aircraftId) {
        return aircraftId == null ? null : LOCKS.remove(aircraftId);
    }
}
