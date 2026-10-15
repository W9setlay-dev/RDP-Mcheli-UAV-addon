package net.vas.rdp.uav.mission;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DestructiveAuthorizationRegistryTest {
    private final UUID missionId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID aircraftId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @Test
    public void grantCanBeConsumedOnceWithinItsWindow() {
        UUID token = issue(100L);

        assertTrue(DestructiveAuthorizationRegistry.consume(token, missionId, ownerId,
                aircraftId, targetId, 2, 100L + DestructiveAuthorizationRegistry
                .AUTHORIZATION_WINDOW_TICKS));
        assertFalse(DestructiveAuthorizationRegistry.consume(token, missionId, ownerId,
                aircraftId, targetId, 2, 100L));
    }

    @Test
    public void grantExpiresAfterWindow() {
        UUID token = issue(100L);

        assertFalse(DestructiveAuthorizationRegistry.consume(token, missionId, ownerId,
                aircraftId, targetId, 2, 101L + DestructiveAuthorizationRegistry
                .AUTHORIZATION_WINDOW_TICKS));
    }

    @Test
    public void grantIsBoundToMissionOwnerAircraftTargetAndDimension() {
        UUID ownerMismatch = issue(100L);

        assertFalse(DestructiveAuthorizationRegistry.consume(ownerMismatch, missionId,
                UUID.randomUUID(), aircraftId, targetId, 2, 100L));
        UUID dimensionMismatch = issue(100L);
        assertFalse(DestructiveAuthorizationRegistry.consume(dimensionMismatch, missionId, ownerId,
                aircraftId, targetId, 3, 100L));
        UUID missionMismatch = issue(100L);
        assertFalse(DestructiveAuthorizationRegistry.consume(missionMismatch,
                UUID.randomUUID(), ownerId, aircraftId, targetId, 2, 100L));
        UUID aircraftMismatch = issue(100L);
        assertFalse(DestructiveAuthorizationRegistry.consume(aircraftMismatch,
                missionId, ownerId, UUID.randomUUID(), targetId, 2, 100L));
        UUID targetMismatch = issue(100L);
        assertFalse(DestructiveAuthorizationRegistry.consume(targetMismatch,
                missionId, ownerId, aircraftId, UUID.randomUUID(), 2, 100L));
    }

    @Test
    public void grantIsRejectedWhenServerTickMovesBackwards() {
        UUID token = issue(100L);

        assertFalse(DestructiveAuthorizationRegistry.consume(token, missionId, ownerId,
                aircraftId, targetId, 2, 99L));
    }

    @Test
    public void expirationSweepDoesNotCompareTicksAcrossDimensions() {
        UUID otherDimensionToken = DestructiveAuthorizationRegistry.issue(missionId,
                ownerId, aircraftId, targetId, 3, 100L);
        assertNotNull(otherDimensionToken);

        DestructiveAuthorizationRegistry.expire(2, 1000L);

        assertTrue(DestructiveAuthorizationRegistry.consume(otherDimensionToken,
                missionId, ownerId, aircraftId, targetId, 3, 100L));
    }

    private UUID issue(long tick) {
        UUID token = DestructiveAuthorizationRegistry.issue(missionId, ownerId,
                aircraftId, targetId, 2, tick);
        assertNotNull(token);
        return token;
    }
}
