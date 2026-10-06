package net.vas.rdp.uav;

import net.vas.rdp.uav.swarm.SwarmManager;
import net.vas.rdp.uav.swarm.SwarmRecord;
import net.vas.rdp.uav.swarm.SwarmRole;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class SwarmManagerContractTest {
    @Test
    public void recordSupportsExplicitCommanderAndSharedTargetContracts() {
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), UUID.randomUUID(), "ops");
        UUID aircraft = UUID.randomUUID();
        swarm.add(new net.vas.rdp.uav.swarm.SwarmMember(aircraft, "REUSABLE_UAV", true, true));
        swarm.setDesignatedLeader(aircraft);
        swarm.setCommanderId(aircraft);
        assertEquals(aircraft, swarm.getCommanderId());
        assertTrue(swarm.getAssignmentManager().assign(swarm, aircraft, SwarmRole.EXECUTOR));
        UUID target = UUID.randomUUID();
        swarm.getTargetRegistry().lock(target, 4L);
        assertEquals(target, swarm.getTargetRegistry().getTargetId());
    }

    @Test
    public void swarmOwnerCheckAllowsOnlyTheRecordOwner() {
        UUID owner = UUID.randomUUID();
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), owner, "ops");
        SwarmManager manager = new SwarmManager();

        assertTrue(manager.owns(swarm, owner));
        assertFalse(manager.owns(swarm, UUID.randomUUID()));
    }
}
