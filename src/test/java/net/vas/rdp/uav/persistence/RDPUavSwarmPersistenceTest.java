package net.vas.rdp.uav.persistence;

import net.minecraft.nbt.NBTTagCompound;
import net.vas.rdp.uav.swarm.SwarmRecord;
import net.vas.rdp.uav.swarm.SwarmTargetState;
import net.vas.rdp.uav.swarm.SwarmConfiguration;
import net.vas.rdp.uav.swarm.SwarmFormation;
import net.vas.rdp.uav.swarm.SearchPattern;
import net.vas.rdp.uav.swarm.SwarmLeadership;
import org.junit.Test;
import java.util.UUID;

import static org.junit.Assert.*;

public class RDPUavSwarmPersistenceTest {
    @Test
    public void persistsLostTargetIdentityAndSearchDispatchState() {
        UUID targetId = UUID.randomUUID();
        RDPUavSwarmWorldData original = new RDPUavSwarmWorldData();
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), UUID.randomUUID(), "search");
        swarm.getTargetRegistry().lock(targetId, 7, 10.0D, 64.0D, 20.0D, 100L);
        swarm.getTargetRegistry().markLost();
        swarm.getTargetRegistry().setSearchDispatched(true);
        original.put(swarm);

        RDPUavSwarmWorldData restored = new RDPUavSwarmWorldData();
        restored.readFromNBT(original.writeToNBT(new NBTTagCompound()));

        SwarmRecord loaded = restored.getRepository().find(swarm.getSwarmId());
        assertEquals(targetId, loaded.getTargetRegistry().getTargetId());
        assertEquals(SwarmTargetState.LOST, loaded.getTargetRegistry().getState());
        assertTrue(loaded.getTargetRegistry().isSearchDispatched());
        assertEquals(10.0D, loaded.getTargetRegistry().getLastX(), 0.0D);
    }

    @Test
    public void persistsFullMissionConfigurationAndLegacyFallbackFields() {
        RDPUavSwarmWorldData original = new RDPUavSwarmWorldData();
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), UUID.randomUUID(), "config");
        swarm.setConfiguration(SwarmConfiguration.builder().maximumMembers(5)
                .horizontalSpacing(29D).verticalSpacing(14D)
                .formation(SwarmFormation.WEDGE).searchPattern(SearchPattern.SECTOR)
                .leadership(SwarmLeadership.DESIGNATED_LEADER)
                .allowDuplicateTargets(true).allowCommanderFailover(false).build());
        swarm.setMissionParticipation(false);
        original.put(swarm);

        RDPUavSwarmWorldData restored = new RDPUavSwarmWorldData();
        restored.readFromNBT(original.writeToNBT(new NBTTagCompound()));

        SwarmRecord loaded = restored.getRepository().find(swarm.getSwarmId());
        assertEquals(5, loaded.getConfiguration().getMaximumMembers());
        assertEquals(29D, loaded.getConfiguration().getHorizontalSpacing(), 0D);
        assertEquals(14D, loaded.getConfiguration().getVerticalSpacing(), 0D);
        assertEquals(SwarmFormation.WEDGE, loaded.getConfiguration().getFormation());
        assertEquals(SearchPattern.SECTOR, loaded.getConfiguration().getSearchPattern());
        assertEquals(SwarmLeadership.DESIGNATED_LEADER,
                loaded.getConfiguration().getLeadership());
        assertTrue(loaded.getConfiguration().isAllowDuplicateTargets());
        assertFalse(loaded.getConfiguration().isAllowCommanderFailover());
        assertFalse(loaded.isMissionParticipation());
    }
}
