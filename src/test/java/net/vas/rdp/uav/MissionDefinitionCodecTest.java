package net.vas.rdp.uav;

import net.minecraft.nbt.NBTTagCompound;
import net.vas.rdp.uav.mission.MissionDefinition;
import net.vas.rdp.uav.mission.MissionDefinitionCodec;
import net.vas.rdp.uav.mission.MissionPresets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import java.util.UUID;
import net.vas.rdp.uav.target.TargetPolicy;

public class MissionDefinitionCodecTest {
    @Test
    public void definitionRoundTripsConfigurationOnlyFields() {
        MissionDefinition source = new MissionDefinition()
                .setTarget("minecraft:zombie")
                .setSwarmEnabled(true)
                .setSwarmMaximumMembers(3)
                .setSearchMode("sector")
                .selectUav(UUID.randomUUID())
                .setTargetPolicy(TargetPolicy.builder()
                        .include("minecraft:zombie")
                        .rejectDeeplyEmbedded(false)
                        .allowHostileVehicles(false)
                        .withMinimumConfidence(0.75D)
                        .snapshot());
        NBTTagCompound encoded = MissionDefinitionCodec.write(source);
        MissionDefinition decoded = MissionDefinitionCodec.read(encoded);
        assertEquals(source.getTarget(), decoded.getTarget());
        assertEquals(source.getSwarmMaximumMembers(), decoded.getSwarmMaximumMembers());
        assertEquals(source.isSwarmEnabled(), decoded.isSwarmEnabled());
        assertEquals(source.getSearchMode(), decoded.getSearchMode());
        assertEquals(source.getSelectedUavs(), decoded.getSelectedUavs());
        assertEquals(source.getTargetPolicy().getInclude(), decoded.getTargetPolicy().getInclude());
        assertEquals(source.getTargetPolicy().isRejectEmbedded(),
                decoded.getTargetPolicy().isRejectEmbedded());
        assertEquals(source.getTargetPolicy().isAllowVehicleTargets(),
                decoded.getTargetPolicy().isAllowVehicleTargets());
        assertEquals(source.getTargetPolicy().getMinimumConfidence(),
                decoded.getTargetPolicy().getMinimumConfidence(), 0.0D);
    }

    @Test
    public void presetsRemainEditableDefinitions() {
        assertEquals(4, MissionPresets.SMALL_SWARM.create().getSwarmMaximumMembers());
    }
}
