package net.vas.rdp.uav.swarm;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class SwarmTargetRecoveryPolicyTest {
    @Test
    public void lossSearchIsSameIdentityBoundedAndDimensionAware() {
        SwarmTargetRegistry target = new SwarmTargetRegistry();
        target.lock(UUID.randomUUID(), 7, 10.0D, 64.0D, 20.0D, 100L);
        target.markLost();
        SwarmTargetRecoveryPolicy policy = new SwarmTargetRecoveryPolicy();
        assertEquals(SwarmTargetRecoveryPolicy.Decision.SEARCH_SAME_UUID,
                policy.decide(target, 7, 899L, 800));
        assertEquals(SwarmTargetRecoveryPolicy.Decision.RESUME_PATROL,
                policy.decide(target, 7, 901L, 800));
        assertEquals(SwarmTargetRecoveryPolicy.Decision.HOLD,
                policy.decide(target, 8, 200L, 800));
        assertEquals(10.0D, target.getLastX(), 0.0D);
        assertEquals(100L, target.getLastSeenTick());
    }

    @Test
    public void cannotReplaceSharedUuidDuringObservation() {
        SwarmTargetRegistry target = new SwarmTargetRegistry();
        target.lock(UUID.randomUUID(), 7, 10.0D, 64.0D, 20.0D, 100L);
        boolean accepted = target.observe(UUID.randomUUID(), 7,
                11.0D, 64.0D, 20.0D, 101L);
        org.junit.Assert.assertFalse(accepted);
    }

    @Test
    public void observationPersistenceIsRateLimitedAndAllowsTickRollbackRecovery() {
        SwarmTargetRegistry target = new SwarmTargetRegistry();
        org.junit.Assert.assertTrue(target.recordObservationPersistence(100L, 10));
        org.junit.Assert.assertFalse(target.recordObservationPersistence(109L, 10));
        org.junit.Assert.assertTrue(target.recordObservationPersistence(110L, 10));
        org.junit.Assert.assertTrue(target.recordObservationPersistence(99L, 10));
        org.junit.Assert.assertFalse(target.recordObservationPersistence(100L, 10));
        target.clear();
        org.junit.Assert.assertTrue(target.recordObservationPersistence(101L, 10));
    }

    @Test
    public void startsANewSearchOnlyAfterASeparateLossTransition() {
        UUID id = UUID.randomUUID();
        SwarmTargetRegistry target = new SwarmTargetRegistry();
        target.lock(id, 7, 10.0D, 64.0D, 20.0D, 100L);
        target.markLost();
        target.setSearchDispatched(true);
        target.observe(id, 7, 11.0D, 64.0D, 20.0D, 101L);
        target.markLost();
        org.junit.Assert.assertFalse(target.isSearchDispatched());
    }
}
