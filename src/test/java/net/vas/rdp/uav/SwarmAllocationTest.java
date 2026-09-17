package net.vas.rdp.uav;

import net.vas.rdp.uav.swarm.SwarmMember;
import net.vas.rdp.uav.swarm.SwarmRecord;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SwarmAllocationTest {
    @Test
    public void duplicateTargetIsRejectedUnlessExplicitlyAllowed() {
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), UUID.randomUUID(), "search");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        swarm.add(new SwarmMember(first, "ONE_WAY", false, true));
        swarm.add(new SwarmMember(second, "REUSABLE_UAV", true, true));
        assertTrue(swarm.assignTarget(first, target, false));
        assertFalse(swarm.assignTarget(second, target, false));
        assertTrue(swarm.assignTarget(second, target, true));
    }
}
