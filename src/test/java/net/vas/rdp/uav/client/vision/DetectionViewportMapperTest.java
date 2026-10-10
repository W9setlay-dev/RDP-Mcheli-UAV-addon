package net.vas.rdp.uav.client.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DetectionViewportMapperTest {
    @Test
    public void mapsTopLeftNormalizedDetectionIntoFeedViewport() {
        VisualDetection detection = new VisualDetection(
                VisualDetection.BEACON_BEAM, 0.8D, 0.25D, 0.20D, 0.25D, 0.40D);

        DetectionViewportMapper.ScreenRect rect =
                DetectionViewportMapper.map(detection, 100, 50, 400, 200);

        assertEquals(200, rect.getX());
        assertEquals(90, rect.getY());
        assertEquals(100, rect.getWidth());
        assertEquals(80, rect.getHeight());
    }

    @Test
    public void remapsAfterResizeAndKeepsBoundsInsideViewport() {
        VisualDetection detection = new VisualDetection(
                VisualDetection.BEACON_BEAM, 0.8D, 0.0D, 0.0D, 0.10D, 1.0D);

        DetectionViewportMapper.ScreenRect rect =
                DetectionViewportMapper.map(detection, 12, 18, 101, 77);

        assertEquals(12, rect.getX());
        assertEquals(18, rect.getY());
        assertEquals(11, rect.getWidth());
        assertEquals(77, rect.getHeight());
        assertNull(DetectionViewportMapper.map(detection, 0, 0, 0, 100));
    }
}
