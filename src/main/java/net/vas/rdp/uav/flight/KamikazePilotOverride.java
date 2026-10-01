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
    private static final Map<UUID, OverrideLock> LOCKS = new ConcurrentHashMap<>();

    private KamikazePilotOverride() { }

    public static void lock(UUID aircraftId, UUID targetId) {
        lock(aircraftId, targetId, null);
    }

    public static void lock(UUID aircraftId, UUID targetId, UUID missionId) {
        if (aircraftId == null || targetId == null) throw new IllegalArgumentException("Lock IDs are required");
        LOCKS.put(aircraftId, new OverrideLock(targetId, missionId));
    }

    public static UUID targetFor(UUID aircraftId) {
        OverrideLock lock = aircraftId == null ? null : LOCKS.get(aircraftId);
        return lock == null ? null : lock.targetId;
    }

    public static UUID clear(UUID aircraftId) {
        OverrideLock lock = aircraftId == null ? null : LOCKS.remove(aircraftId);
        return lock == null ? null : lock.targetId;
    }

    /** Clear only the lock created by this mission; preserve a newer mission's lock. */
    public static UUID clear(UUID aircraftId, UUID missionId) {
        if (aircraftId == null || missionId == null) return null;
        OverrideLock lock = LOCKS.get(aircraftId);
        if (lock == null || !missionId.equals(lock.missionId)
                || !LOCKS.remove(aircraftId, lock)) return null;
        return lock.targetId;
    }

    private static final class OverrideLock {
        private final UUID targetId;
        private final UUID missionId;

        private OverrideLock(UUID targetId, UUID missionId) {
            this.targetId = targetId;
            this.missionId = missionId;
        }
    }
}
