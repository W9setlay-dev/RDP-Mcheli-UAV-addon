package net.vas.rdp.uav.client.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VisualFrameTest {
    @Test
    public void copiesCallerPixelsAndExposesTopLeftImageCoordinates() {
        byte[] rgba = new byte[2 * 2 * 4];
        rgba[0] = 10;
        rgba[1] = 20;
        rgba[2] = 30;
        rgba[3] = (byte) 255;
        VisualFrame frame = VisualFrame.copyOf(1L, 10L, "feed", "camera",
                2, 2, rgba);
        rgba[0] = 99;

        assertEquals(10, frame.red(0, 1));
        assertEquals(20, frame.green(0, 1));
        assertEquals(255, frame.alpha(0, 1));
    }
}
