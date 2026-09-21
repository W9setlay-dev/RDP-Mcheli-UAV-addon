package net.vas.rdp.uav;

import net.vas.rdp.uav.swarm.SwarmMember;
import net.vas.rdp.uav.swarm.SwarmRecord;
import net.vas.rdp.uav.swarm.SwarmReplanner;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class SwarmReplannerTest {
    @Test
    public void unavailableExecutorTargetIsReassignedToAvailableExecutor() {
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), UUID.randomUUID(), "strike");
        UUID lost = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        SwarmMember lostMember = new SwarmMember(lost, "ONE_WAY", false, true);
        SwarmMember replacementMember = new SwarmMember(replacement, "REUSABLE_UAV", true, true);
        swarm.add(lostMember);
        swarm.add(replacementMember);
        swarm.assignTarget(lost, target, false);
        lostMember.setAvailable(false);
        assertTrue(new SwarmReplanner().reassignUnavailableTarget(swarm, lost));
        assertEquals(target, replacementMember.getAssignedTarget());
        assertNull(lostMember.getAssignedTarget());
    }
}
