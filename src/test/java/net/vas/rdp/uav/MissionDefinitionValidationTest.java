package net.vas.rdp.uav;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.vas.rdp.uav.mission.MissionDefinition;
import net.vas.rdp.uav.mission.MissionDefinitionCodec;
import net.vas.rdp.uav.mission.MissionType;
import org.junit.Test;

import static org.junit.Assert.*;

public class MissionDefinitionValidationTest {
    @Test
    public void squareDefinitionsRequireNumericFiniteCoordinates() {
        MissionDefinition definition = new MissionDefinition()
                .setMissionType(MissionType.SQUARE_STRIKE)
                .setTarget("10 80 20 64");
        assertTrue(net.vas.rdp.uav.mission.MissionValidationResult
                .validate(definition).isValid());

        definition.setTarget("10 nope 20 64");
        assertFalse(net.vas.rdp.uav.mission.MissionValidationResult
                .validate(definition).isValid());
        definition.setTarget("10 Infinity 20 64");
        assertFalse(net.vas.rdp.uav.mission.MissionValidationResult
                .validate(definition).isValid());
    }

    @Test
    public void malformedSelectedUavIdentityIsRejectedDuringDecode() {
        NBTTagCompound encoded = MissionDefinitionCodec.write(new MissionDefinition());
        NBTTagList selected = new NBTTagList();
        NBTTagCompound item = new NBTTagCompound();
        item.setString("id", "not-a-uuid");
        selected.appendTag(item);
        encoded.setTag("selectedUavs", selected);

        try {
            MissionDefinitionCodec.read(encoded);
            fail("Malformed selected UAV identity must not be silently discarded");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Invalid mission definition"));
        }
    }

    @Test
    public void pointAndSquareTargetsRemainDistinctMissionModes() {
        MissionDefinition point = new MissionDefinition()
                .setMissionType(MissionType.STRIKE)
                .setTarget("minecraft:stone");
        MissionDefinition square = new MissionDefinition()
                .setMissionType(MissionType.SQUARE_STRIKE)
                .setTarget("10 80 20 64");

        assertTrue(net.vas.rdp.uav.mission.MissionValidationResult
                .validate(point).isValid());
        assertTrue(net.vas.rdp.uav.mission.MissionValidationResult
                .validate(square).isValid());
        assertFalse(net.vas.rdp.uav.mission.MissionValidationResult
                .validate(new MissionDefinition().setMissionType(MissionType.STRIKE)
                        .setTarget("10 80 20 64")).isValid());
    }

}
