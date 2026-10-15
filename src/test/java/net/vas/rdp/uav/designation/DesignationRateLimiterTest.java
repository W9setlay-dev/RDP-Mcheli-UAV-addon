package net.vas.rdp.uav.designation;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DesignationRateLimiterTest {
    @Test
    public void appliesSecondAndMinuteWindowsUsingServerTicks() {
        DesignationRateLimiter limiter = new DesignationRateLimiter();
        UUID operator = UUID.randomUUID();

        assertTrue(limiter.allow(operator, 100L, 2, 3));
        assertTrue(limiter.allow(operator, 100L, 2, 3));
        assertFalse(limiter.allow(operator, 101L, 2, 3));
        assertTrue(limiter.allow(operator, 120L, 2, 3));
        assertFalse(limiter.allow(operator, 1200L, 2, 1));
        assertTrue(limiter.allow(operator, 1300L, 2, 3));
    }

    @Test
    public void tickRewindAndLogoutClearLimiterState() {
        DesignationRateLimiter limiter = new DesignationRateLimiter();
        UUID operator = UUID.randomUUID();
        assertTrue(limiter.allow(operator, 500L, 1, 1));
        assertTrue(limiter.allow(operator, 10L, 1, 1));
        assertFalse(limiter.allow(operator, 11L, 1, 1));
        limiter.clear(operator);
        assertTrue(limiter.allow(operator, 11L, 1, 1));
    }
}
