package net.vas.rdp.uav.persistence;

import net.minecraft.nbt.NBTTagCompound;
import net.vas.rdp.uav.swarm.SwarmRecord;
import net.vas.rdp.uav.swarm.SwarmTargetState;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
}
