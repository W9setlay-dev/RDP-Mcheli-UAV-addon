package net.vas.rdp.uav;

import net.vas.rdp.uav.swarm.SwarmAgent;
import net.vas.rdp.uav.swarm.SwarmConfiguration;
import net.vas.rdp.uav.swarm.SwarmMemberState;
import net.vas.rdp.uav.swarm.SwarmRole;
import net.vas.rdp.uav.swarm.UavCapabilities;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SwarmDomainTest {
    @Test
    public void agentAcceptsOnlyCapabilitySupportedRoles() {
        SwarmAgent agent = new SwarmAgent(UUID.randomUUID(), UUID.randomUUID(),
                UavCapabilities.builder().payload(true).build());
        agent.setRole(SwarmRole.EXECUTOR);
        assertEquals(SwarmRole.EXECUTOR, agent.getRole());
        try {
            agent.setRole(SwarmRole.SEARCHER);
            fail("Unsupported role must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        agent.setState(SwarmMemberState.ASSIGNED);
        assertEquals(SwarmMemberState.ASSIGNED, agent.getState());
    }

    @Test
    public void capabilitiesAndConfigurationAreImmutableProjections() {
        UavCapabilities capabilities = UavCapabilities.builder()
                .search(true).tracking(true).capability("sensor").build();
        assertEquals(1, capabilities.getCapabilities().size());
        assertEquals(16D, SwarmConfiguration.defaults().getHorizontalSpacing(), 0.001D);
    }
}
