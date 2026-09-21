package net.vas.rdp.uav;

import net.vas.rdp.uav.mission.MissionDefinition;
import net.vas.rdp.uav.mission.MissionValidationResult;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class MissionDefinitionTest {
    @Test
    public void definitionIsIndependentFromGuiAndValidatesRequiredTarget() {
        MissionDefinition definition = new MissionDefinition();
        assertFalse(MissionValidationResult.validate(definition).isValid());
        definition.setTarget("minecraft:zombie");
        assertTrue(MissionValidationResult.validate(definition).isValid());
    }

    @Test
    public void definitionNormalizesEditableText() {
        MissionDefinition definition = new MissionDefinition().setTarget("  minecraft:zombie  ");
        assertEquals("minecraft:zombie", definition.getTarget());
    }

    @Test
    public void definitionCarriesWorkflowIntentWithoutRuntimeAuthority() {
        UUID aircraft = UUID.randomUUID();
        MissionDefinition definition = new MissionDefinition()
                .setTarget("minecraft:zombie")
                .selectUav(aircraft)
                .setSearchMode("sector")
                .setTrackingMode("continuous");
        assertTrue(definition.getSelectedUavs().contains(aircraft));
        assertEquals("SECTOR", definition.getSearchMode());
        assertEquals("CONTINUOUS", definition.getTrackingMode());
    }
}
