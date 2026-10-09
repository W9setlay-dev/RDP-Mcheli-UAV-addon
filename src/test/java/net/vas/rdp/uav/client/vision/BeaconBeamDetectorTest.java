package net.vas.rdp.uav.client.vision;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BeaconBeamDetectorTest {
    @Test
    public void detectsTallTintedBeamFromImagePixelsAndNormalizesBounds() {
        VisualFrame frame = frame(40, 48, true);
        List<VisualDetection> result = new BeaconBeamDetector(0.6D).detect(frame);

        assertEquals(1, result.size());
        VisualDetection detection = result.get(0);
        assertEquals(VisualDetection.BEACON_BEAM, detection.getClassId());
        assertTrue(detection.getScore() >= 0.6D);
        assertEquals(19.0D / 40.0D, detection.getX(), 0.0001D);
        assertEquals(5.0D / 48.0D, detection.getY(), 0.0001D);
        assertEquals(2.0D / 40.0D, detection.getWidth(), 0.0001D);
        assertEquals(34.0D / 48.0D, detection.getHeight(), 0.0001D);
    }

    @Test
    public void rejectsShortMagentaFeaturesAndUnrelatedPixels() {
        assertTrue(new BeaconBeamDetector(0.0D).detect(frame(40, 48, false)).isEmpty());
        assertTrue(new BeaconBeamDetector(0.0D).detect(shortFeatureFrame()).isEmpty());
    }

    @Test
    public void rejectsInvalidFramesDetectionsAndThresholds() {
        try {
            VisualFrame.copyOf(1L, 1L, "feed", "camera", 2, 2, new byte[3]);
            fail("malformed pixel payload must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("frame"));
        }
        try {
            new VisualDetection("test", Double.NaN, 0.0D, 0.0D, 1.0D, 1.0D);
            fail("non-finite detector score must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("detection"));
        }
        try {
            new BeaconBeamDetector(1.1D);
            fail("invalid threshold must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("score"));
        }
    }

    private static VisualFrame frame(int width, int height, boolean beam) {
        byte[] rgba = new byte[width * height * 4];
        for (int i = 3; i < rgba.length; i += 4) rgba[i] = (byte) 255;
        if (beam) {
            for (int y = 5; y < 39; y++) {
                set(rgba, width, height, 19, y, 220, 32, 220);
                set(rgba, width, height, 20, y, 220, 32, 220);
            }
        }
        return VisualFrame.copyOf(1L, 100L, "mcheli-uav-feed",
                "test-camera", width, height, rgba);
    }

    private static VisualFrame shortFeatureFrame() {
        int width = 40;
        int height = 48;
        byte[] rgba = new byte[width * height * 4];
        for (int i = 3; i < rgba.length; i += 4) rgba[i] = (byte) 255;
        for (int y = 10; y < 15; y++) set(rgba, width, height, 20, y, 220, 32, 220);
        return VisualFrame.copyOf(2L, 200L, "test", "test-camera",
                width, height, rgba);
    }

    private static void set(byte[] rgba, int width, int height, int x, int y,
                            int red, int green, int blue) {
        int glY = height - 1 - y;
        int offset = (glY * width + x) * 4;
        rgba[offset] = (byte) red;
        rgba[offset + 1] = (byte) green;
        rgba[offset + 2] = (byte) blue;
        rgba[offset + 3] = (byte) 255;
    }
}
