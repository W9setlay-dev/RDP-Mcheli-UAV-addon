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
}
