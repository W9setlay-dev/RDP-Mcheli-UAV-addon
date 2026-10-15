package net.vas.rdp.uav.mission;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Short-lived, single-use grants for server-side destructive arming boundaries. */
public final class DestructiveAuthorizationRegistry {
    public static final long AUTHORIZATION_WINDOW_TICKS = 40L;
    private static final int MAX_PENDING_GRANTS = 2048;
    private static final Map<UUID, Grant> PENDING = new HashMap<>();

    private DestructiveAuthorizationRegistry() { }

    public static synchronized UUID issue(UUID missionId, UUID ownerId, UUID aircraftId,
                                          UUID targetId, int dimension, long issuedTick) {
        if (missionId == null || ownerId == null || aircraftId == null || issuedTick < 0L) {
            throw new IllegalArgumentException("Invalid destructive authorization grant");
        }
        expire(dimension, issuedTick);
        if (PENDING.size() >= MAX_PENDING_GRANTS) return null;
        UUID token;
        do {
            token = UUID.randomUUID();
        } while (PENDING.containsKey(token));
        PENDING.put(token, new Grant(missionId, ownerId, aircraftId, targetId,
                dimension, issuedTick));
        return token;
    }

    public static synchronized boolean consume(UUID token, UUID missionId, UUID ownerId,
                                               UUID aircraftId, UUID targetId,
                                               int dimension, long currentTick) {
        if (token == null) return false;
        Grant grant = PENDING.remove(token);
        if (grant == null || currentTick < grant.issuedTick
                || currentTick - grant.issuedTick > AUTHORIZATION_WINDOW_TICKS) {
            return false;
        }
        return grant.matches(missionId, ownerId, aircraftId, targetId, dimension);
    }

    public static synchronized void expire(int dimension, long currentTick) {
        Iterator<Map.Entry<UUID, Grant>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Grant grant = iterator.next().getValue();
            if (grant.dimension == dimension && (currentTick < grant.issuedTick
                    || currentTick - grant.issuedTick > AUTHORIZATION_WINDOW_TICKS)) {
                iterator.remove();
            }
        }
    }

    private static final class Grant {
        private final UUID missionId;
        private final UUID ownerId;
        private final UUID aircraftId;
        private final UUID targetId;
        private final int dimension;
        private final long issuedTick;

        private Grant(UUID missionId, UUID ownerId, UUID aircraftId, UUID targetId,
                      int dimension, long issuedTick) {
            this.missionId = missionId;
            this.ownerId = ownerId;
            this.aircraftId = aircraftId;
            this.targetId = targetId;
            this.dimension = dimension;
            this.issuedTick = issuedTick;
        }

        private boolean matches(UUID expectedMission, UUID expectedOwner,
                                UUID expectedAircraft, UUID expectedTarget,
                                int expectedDimension) {
            return missionId.equals(expectedMission) && ownerId.equals(expectedOwner)
                    && aircraftId.equals(expectedAircraft)
                    && (targetId == null ? expectedTarget == null : targetId.equals(expectedTarget))
                    && dimension == expectedDimension;
        }
    }
}
