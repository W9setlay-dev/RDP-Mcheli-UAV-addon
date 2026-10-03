package net.vas.rdp.uav;

import net.vas.rdp.uav.swarm.SwarmTargetRegistry;
import net.vas.rdp.uav.swarm.SwarmTargetState;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class SwarmTargetRegistryTest {
    @Test
    public void sharedTargetIdentityHasExplicitLifecycle() {
        SwarmTargetRegistry registry = new SwarmTargetRegistry();
        UUID target = UUID.randomUUID();
        registry.lock(target, 12L);
        assertEquals(target, registry.getTargetId());
        assertEquals(SwarmTargetState.LOCKED, registry.getState());
        registry.markLost();
        assertEquals(SwarmTargetState.LOST, registry.getState());
        registry.clear();
        assertEquals(SwarmTargetState.CLEAR, registry.getState());
    }

    @Test
    public void sharedTargetRegistryRejectsSilentReplacement() {
        SwarmTargetRegistry registry = new SwarmTargetRegistry();
        UUID initial = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();

        assertEquals(Boolean.TRUE, registry.lock(initial, 0, 1.0D, 2.0D, 3.0D, 10L));
        assertEquals(Boolean.FALSE, registry.lock(replacement, 0, 4.0D, 5.0D, 6.0D, 11L));
        assertEquals(initial, registry.getTargetId());
        assertEquals(SwarmTargetState.LOCKED, registry.getState());
    }
}
