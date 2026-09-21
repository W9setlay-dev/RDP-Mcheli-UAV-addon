package net.vas.rdp.uav;

import net.vas.rdp.uav.swarm.SwarmMember;
import net.vas.rdp.uav.swarm.SwarmRecord;
import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class SwarmLeadershipTest {
    @Test
    public void commanderFailsOverToAvailableMember() {
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), UUID.randomUUID(), "recon");
        UUID commander = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();
        swarm.add(new SwarmMember(commander, "REUSABLE_UAV", true, true));
        swarm.add(new SwarmMember(replacement, "REUSABLE_UAV", true, true));

        UUID elected = swarm.referenceMember();
        Assert.assertNotNull(elected);
        UUID replacementId = elected.equals(commander) ? replacement : commander;
        for (SwarmMember member : swarm.getMembers()) {
            if (elected.equals(member.getAircraftId())) member.setAvailable(false);
        }
        assertEquals(replacementId, swarm.referenceMember());
        assertEquals(replacementId, swarm.getCommanderId());
    }
}
