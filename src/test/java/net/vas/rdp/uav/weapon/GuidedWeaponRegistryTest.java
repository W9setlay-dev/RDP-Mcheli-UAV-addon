package net.vas.rdp.uav.weapon;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GuidedWeaponRegistryTest {
    @Test
    public void legacyTvConfigurationUsesNativeAiGuidanceContract() {
        GuidedWeaponCapability capability = GuidedWeaponRegistry.configuredCapability(
                "tvmissile", "tvmissile", "UNSUPPORTED", "",
                new GuidedTerminalProfile(0, true), false);

        assertEquals(GuidedNativeBehavior.COORDINATE_TV_LASER, capability.getBehavior());
        assertTrue(capability.supports(GuidedObjectiveType.ENTITY));
        assertTrue(capability.supports(GuidedObjectiveType.SQUARE));
    }
}
