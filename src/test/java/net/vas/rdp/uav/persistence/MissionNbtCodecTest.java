package net.vas.rdp.uav.persistence;

import net.minecraft.nbt.NBTTagCompound;
import net.vas.rdp.uav.mission.ControlOwner;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.MissionExecutionStage;
import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class MissionNbtCodecTest {
    @Test
    public void roundTripsOperatorControlOverrides() {
        for (ControlOwner owner : new ControlOwner[] {
                ControlOwner.MANUAL, ControlOwner.SUPERVISED_OPERATOR
        }) {
            RDPMission mission = mission();
            mission.transitionTo(net.vas.rdp.uav.mission.MissionState.DISPATCHED, 2L);
            mission.setControlOwner(owner, 3L);
            mission.setCruiseAltitudeY(192.0D);

            NBTTagCompound encoded = MissionNbtCodec.write(mission);
            RDPMission restored = MissionNbtCodec.read(encoded);

            assertEquals(owner, restored.getControlOwner());
            assertEquals(192.0D, restored.getCruiseAltitudeY(), 0.0D);
            restored.validateStateConsistency();
        }
    }

    @Test
    public void missingLegacyCruiseAltitudeRemainsUnresolvedUntilServerDispatch() {
        NBTTagCompound encoded = MissionNbtCodec.write(mission());
        encoded.setInteger("schemaVersion", 9);
        RDPMission restored = MissionNbtCodec.read(encoded);
        assertFalse(restored.hasCruiseAltitudeY());
        assertEquals(120.0D, restored.resolveCruiseAltitudeY(120.0D), 0.0D);
        assertEquals(120.0D, restored.resolveCruiseAltitudeY(180.0D), 0.0D);
    }

    @Test
    public void legacyTerminalProjectionMigratesToTargetTracking() {
        RDPMission legacy = RDPMission.restore(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "TEST_UAV", MissionType.STRIKE,
                TargetSpec.point(0, 32, 0, TargetPriority.NORMAL), true, false,
                false, 64, 32, 1, MissionState.ATTACK_TARGET, null, 2,
                null, null);
        legacy.restoreExecutionMetadata(MissionExecutionStage.TERMINAL, null, null);
        NBTTagCompound encoded = MissionNbtCodec.write(legacy);
        encoded.setInteger("schemaVersion", 9);

        RDPMission restored = MissionNbtCodec.read(encoded);

        assertEquals(MissionExecutionStage.TARGET_TRACK, restored.getExecutionStage());
    }

    private static RDPMission mission() {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "TEST_UAV", MissionType.STRIKE,
                TargetSpec.point(0.0D, 32.0D, 0.0D, TargetPriority.NORMAL),
                false, false, false, 64.0D, 32.0D, 1L);
    }
}
