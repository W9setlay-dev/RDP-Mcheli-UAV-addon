package net.vas.rdp.uav.client;

import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TargetOverlayGeometryTest {
    @Test
    public void projectsBoundsIntoScaledScreenCoordinates() {
        TargetOverlayGeometry.ScreenBox box = TargetOverlayGeometry.projectBounds(
                UUID.randomUUID(), "HOSTILE",
                -0.5D, -0.5D, -0.5D, 0.5D, 0.5D, 0.5D,
                identity(), identity(), 0, 0, 100, 100, 100, 100);

        assertEquals(25, box.getLeft());
        assertEquals(25, box.getTop());
        assertEquals(75, box.getRight());
        assertEquals(75, box.getBottom());
    }

    @Test
    public void rejectsBoundsOutsideTheClipDepthRange() {
        TargetOverlayGeometry.ScreenBox box = TargetOverlayGeometry.projectBounds(
                UUID.randomUUID(), "HOSTILE",
                -0.5D, -0.5D, 2.0D, 0.5D, 0.5D, 3.0D,
                identity(), identity(), 0, 0, 100, 100, 100, 100);

        assertNull(box);
    }

    @Test
    public void overlappingBoxesChooseSmallestThenNearestThenStableId() {
        UUID smallerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID largerId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        TargetOverlayGeometry.ScreenBox large =
                new TargetOverlayGeometry.ScreenBox(largerId, "large", 0, 0, 40, 40, 0.1F);
        TargetOverlayGeometry.ScreenBox smallFar =
                new TargetOverlayGeometry.ScreenBox(largerId, "small-far", 10, 10, 20, 20, 0.8F);
        TargetOverlayGeometry.ScreenBox smallNear =
                new TargetOverlayGeometry.ScreenBox(smallerId, "small-near", 10, 10, 20, 20, 0.2F);

        assertEquals(smallerId, TargetOverlayGeometry.selectAt(
                Arrays.asList(large, smallFar, smallNear), 15, 15).getTargetId());
        assertEquals(large.getTargetId(), TargetOverlayGeometry.selectAt(
                Arrays.asList(large), 5, 5).getTargetId());
        assertNull(TargetOverlayGeometry.selectAt(Arrays.asList(large), 50, 50));
    }

    @Test
    public void equalAreaAndDepthUseStableUuidOrdering() {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        TargetOverlayGeometry.ScreenBox first =
                new TargetOverlayGeometry.ScreenBox(firstId, "first", 0, 0, 20, 20, 0.5F);
        TargetOverlayGeometry.ScreenBox second =
                new TargetOverlayGeometry.ScreenBox(secondId, "second", 0, 0, 20, 20, 0.5F);

        assertEquals(firstId, TargetOverlayGeometry.selectAt(
                Arrays.asList(second, first), 10, 10).getTargetId());
    }

    private static float[] identity() {
        float[] matrix = new float[16];
        matrix[0] = matrix[5] = matrix[10] = matrix[15] = 1.0F;
        return matrix;
    }
}
