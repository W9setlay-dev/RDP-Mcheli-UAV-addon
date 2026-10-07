package net.vas.rdp.uav.persistence;

import net.minecraft.nbt.NBTTagCompound;
import net.vas.rdp.uav.mission.ControlOwner;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class MissionNbtCodecTest {
    @Test
    public void roundTripsOperatorControlOverrides() {
        for (ControlOwner owner : new ControlOwner[] {
                ControlOwner.MANUAL, ControlOwner.SUPERVISED_OPERATOR
        }) {
            RDPMission mission = mission();
            mission.transitionTo(net.vas.rdp.uav.mission.MissionState.DISPATCHED, 2L);
            mission.setControlOwner(owner, 3L);

            NBTTagCompound encoded = MissionNbtCodec.write(mission);
            RDPMission restored = MissionNbtCodec.read(encoded);

            assertEquals(owner, restored.getControlOwner());
            restored.validateStateConsistency();
        }
    }

    private static RDPMission mission() {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "TEST_UAV", MissionType.STRIKE,
                TargetSpec.point(0.0D, 32.0D, 0.0D, TargetPriority.NORMAL),
                false, false, false, 64.0D, 32.0D, 1L);
    }
}
