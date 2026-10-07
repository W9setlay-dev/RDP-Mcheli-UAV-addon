package net.vas.rdp.uav.persistence;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.vas.rdp.uav.mission.MissionRepository;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class RDPMissionWorldDataTest {
    @Test
    public void malformedRecordIsRejectedWithoutDiscardingValidMissions() {
        RDPMission expected = mission();
        RDPMission malformed = mission();
        NBTTagCompound malformedTag = MissionNbtCodec.write(malformed);
        malformedTag.setString("missionId", "not-a-uuid");
        NBTTagCompound invalidEnumTag = MissionNbtCodec.write(mission());
        invalidEnumTag.setString("missionType", "NOT_A_MISSION_TYPE");

        NBTTagList entries = new NBTTagList();
        entries.appendTag(malformedTag);
        entries.appendTag(invalidEnumTag);
        entries.appendTag(MissionNbtCodec.write(expected));
        NBTTagCompound savedData = new NBTTagCompound();
        savedData.setTag("missions", entries);

        RDPMissionWorldData data = new RDPMissionWorldData();
        data.readFromNBT(savedData);

        MissionRepository repository = data.getRepository();
        assertEquals(1, repository.snapshot().size());
        assertNotNull(repository.find(expected.getMissionId()));
        assertNull(repository.find(malformed.getMissionId()));
        assertEquals(1, data.writeToNBT(new NBTTagCompound())
                .getTagList("missions", 10).tagCount());
    }

    private static RDPMission mission() {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "TEST_UAV", MissionType.STRIKE,
                TargetSpec.point(0.0D, 32.0D, 0.0D, TargetPriority.NORMAL),
                false, false, false, 64.0D, 32.0D, 1L);
    }
}
