package net.vas.rdp.uav.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FlirHudStyleTest {
    @Test
    public void disabledModePreservesExistingLockedAndSearchingBracketColors() {
        assertEquals(0xF2FF1F1F, FlirHudStyle.bracketColor(false, true));
        assertEquals(0xCCFFB81F, FlirHudStyle.bracketColor(false, false));
        assertEquals(2.0F, FlirHudStyle.lineWidth(false, true), 0.0F);
        assertEquals(2.0F, FlirHudStyle.lineWidth(false, false), 0.0F);
    }

    @Test
    public void enabledModeUsesMonochromeAndRetainsLockStateContrast() {
        assertEquals(0xFFFFFFFF, FlirHudStyle.bracketColor(true, true));
        assertEquals(0xBFFFFFFF, FlirHudStyle.bracketColor(true, false));
        assertEquals(2.0F, FlirHudStyle.lineWidth(true, true), 0.0F);
        assertEquals(1.5F, FlirHudStyle.lineWidth(true, false), 0.0F);
    }
}
