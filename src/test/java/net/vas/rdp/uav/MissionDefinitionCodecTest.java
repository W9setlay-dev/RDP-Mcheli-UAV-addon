package net.vas.rdp.uav;

import net.minecraft.nbt.NBTTagCompound;
import net.vas.rdp.uav.mission.MissionDefinition;
import net.vas.rdp.uav.mission.MissionDefinitionCodec;
import net.vas.rdp.uav.mission.MissionPresets;
import net.vas.rdp.uav.mission.MissionWaypoint;
import org.junit.Test;

import java.util.UUID;
import net.vas.rdp.uav.target.TargetPolicy;
import net.vas.rdp.uav.swarm.SwarmConfiguration;
import net.vas.rdp.uav.swarm.SwarmFormation;
import net.vas.rdp.uav.swarm.SearchPattern;
import net.vas.rdp.uav.swarm.SwarmLeadership;

import static org.junit.Assert.*;

public class MissionDefinitionCodecTest {
    @Test
    public void definitionRoundTripsConfigurationOnlyFields() {
        UUID swarmId = UUID.randomUUID();
        MissionDefinition source = new MissionDefinition()
                .setTarget("minecraft:zombie")
                .setSwarmEnabled(true)
                .setSwarmMaximumMembers(3)
                .setSwarmId(swarmId)
                .setSwarmConfiguration(SwarmConfiguration.builder().maximumMembers(3)
                        .horizontalSpacing(27D).verticalSpacing(13D)
                        .formation(SwarmFormation.WEDGE).searchPattern(SearchPattern.SPIRAL)
                        .leadership(SwarmLeadership.DESIGNATED_LEADER)
                        .allowDuplicateTargets(true).allowCommanderFailover(false).build())
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
        assertEquals(swarmId, decoded.getSwarmId());
        assertEquals(SwarmFormation.WEDGE, decoded.getSwarmConfiguration().getFormation());
        assertEquals(SearchPattern.SPIRAL, decoded.getSwarmConfiguration().getSearchPattern());
        assertEquals(SwarmLeadership.DESIGNATED_LEADER,
                decoded.getSwarmConfiguration().getLeadership());
        assertEquals(27D, decoded.getSwarmConfiguration().getHorizontalSpacing(), 0D);
        assertEquals(13D, decoded.getSwarmConfiguration().getVerticalSpacing(), 0D);
        assertTrue(decoded.getSwarmConfiguration().isAllowDuplicateTargets());
        assertFalse(decoded.getSwarmConfiguration().isAllowCommanderFailover());
        assertEquals(source.getTargetPolicy().getInclude(), decoded.getTargetPolicy().getInclude());
        assertEquals(source.getTargetPolicy().isRejectEmbedded(),
                decoded.getTargetPolicy().isRejectEmbedded());
        assertEquals(source.getTargetPolicy().isAllowVehicleTargets(),
                decoded.getTargetPolicy().isAllowVehicleTargets());
        assertEquals(source.getTargetPolicy().getMinimumConfidence(),
                decoded.getTargetPolicy().getMinimumConfidence(), 0.0D);
    }

    @Test
    public void patrolRouteRoundTripsGuiConfiguration() {
        MissionDefinition definition = new MissionDefinition()
                .setMissionType(net.vas.rdp.uav.mission.MissionType.PATROL)
                .setTarget("")
                .setWaypoints(java.util.Arrays.asList(
                        new MissionWaypoint(12.0D, 34.0D, 40),
                        new MissionWaypoint(-56.0D, 78.0D, 0)));

        MissionDefinition restored = MissionDefinitionCodec.read(MissionDefinitionCodec.write(definition));

        assertEquals(2, restored.getWaypoints().size());
        assertEquals(12.0D, restored.getWaypoints().get(0).getX(), 0.0D);
        assertEquals(40, restored.getWaypoints().get(0).getLoiterTicks());
    }

    @Test
    public void presetsRemainEditableDefinitions() {
        assertEquals(4, MissionPresets.SMALL_SWARM.create().getSwarmMaximumMembers());
    }
}
