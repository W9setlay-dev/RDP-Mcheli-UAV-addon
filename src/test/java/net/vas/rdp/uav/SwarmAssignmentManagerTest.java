package net.vas.rdp.uav;

import net.vas.rdp.uav.swarm.SwarmAssignmentManager;
import net.vas.rdp.uav.swarm.SwarmMember;
import net.vas.rdp.uav.swarm.SwarmRecord;
import net.vas.rdp.uav.swarm.SwarmRole;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SwarmAssignmentManagerTest {
    @Test
    public void roleAssignmentUsesExistingMemberCapabilities() {
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), UUID.randomUUID(), "ops");
        UUID observer = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        swarm.add(new SwarmMember(observer, "ONE_WAY", false, false));
        swarm.add(new SwarmMember(executor, "REUSABLE_UAV", true, true));

        SwarmAssignmentManager assignments = new SwarmAssignmentManager();
        assertFalse(assignments.assign(swarm, observer, SwarmRole.EXECUTOR));
        assertTrue(assignments.assign(swarm, executor, SwarmRole.EXECUTOR));
        assertEquals(SwarmRole.EXECUTOR, assignments.role(executor));
        assertEquals(SwarmRole.EXECUTOR, swarm.getMembers().get(0).getRole()
                == SwarmRole.EXECUTOR ? swarm.getMembers().get(0).getRole()
                : swarm.getMembers().get(1).getRole());
    }

    @Test
    public void operationalRoleAssignmentDoesNotChangeVehicleProfileOrCapabilities() {
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), UUID.randomUUID(), "ops");
        UUID aircraft = UUID.randomUUID();
        SwarmMember member = new SwarmMember(aircraft, "LONG_RANGE_REUSABLE_UAV",
                true, true);
        swarm.add(member);

        assertTrue(new SwarmAssignmentManager().assign(swarm, aircraft, SwarmRole.EXECUTOR));

        assertEquals(SwarmRole.EXECUTOR, member.getRole());
        assertEquals("LONG_RANGE_REUSABLE_UAV", member.getProfile());
        assertTrue(member.supportsLongRange());
        assertTrue(member.hasPayload());
    }
}
