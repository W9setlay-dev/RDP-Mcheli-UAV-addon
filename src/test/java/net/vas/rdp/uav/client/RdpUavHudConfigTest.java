package net.vas.rdp.uav.client;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RdpUavHudConfigTest {
    @Test
    public void flirDefaultsOffAndToggleUpdatesSharedPreference() {
        assertFalse(RdpUavHudConfig.flirEnabled);
        assertTrue(RdpUavHudConfig.toggleFlir());
        assertTrue(RdpUavHudConfig.setFlirEnabled(true));
        assertFalse(RdpUavHudConfig.toggleFlir());
    }
}
