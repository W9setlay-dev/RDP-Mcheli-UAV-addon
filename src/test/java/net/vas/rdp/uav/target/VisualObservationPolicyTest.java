package net.vas.rdp.uav.target;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VisualObservationPolicyTest {
    @Test
    public void acceptsTargetInsideRangeAndForwardFieldOfView() {
        assertTrue(VisualObservationPolicy.isWithinFieldOfView(
                0, 0, 0, 0, 0, 1, 0, 0, 20, 20, 90));
    }

    @Test
    public void rejectsTargetOutsideRange() {
        assertFalse(VisualObservationPolicy.isWithinFieldOfView(
                0, 0, 0, 0, 0, 1, 0, 0, 20.01, 20, 90));
    }

    @Test
    public void rejectsTargetOutsideConfiguredCone() {
        assertFalse(VisualObservationPolicy.isWithinFieldOfView(
                0, 0, 0, 0, 0, 1, 11, 0, 10, 20, 90));
    }

    @Test
    public void acceptsTargetOnFieldOfViewBoundary() {
        assertTrue(VisualObservationPolicy.isWithinFieldOfView(
                0, 0, 0, 0, 0, 1, 10, 0, 10, 20, 90));
    }

    @Test
    public void rejectsInvalidVectorsAndConfiguration() {
        assertFalse(VisualObservationPolicy.isWithinFieldOfView(
                0, 0, 0, 0, 0, 0, 0, 0, 1, 20, 90));
        assertFalse(VisualObservationPolicy.isWithinFieldOfView(
                0, 0, 0, Double.NaN, 0, 1, 0, 0, 1, 20, 90));
        assertFalse(VisualObservationPolicy.isWithinFieldOfView(
                0, 0, 0, 0, 0, 1, 0, 0, 1, 20, 0));
    }
}
