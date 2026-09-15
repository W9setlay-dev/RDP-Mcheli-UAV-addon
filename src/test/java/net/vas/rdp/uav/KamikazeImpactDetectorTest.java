package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.KamikazeConfig;
import net.vas.rdp.uav.flight.KamikazeImpactDetector;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class KamikazeImpactDetectorTest {
    @Test
    public void noseZoneDetectsForwardTargetsWithinTwoBlocks() {
        assertTrue(KamikazeImpactDetector.isAimedAtTargetInNoseZone(
                0.0D, 0.0D, 0.0D, 0.0F, 0.0F,
                0.0D, 0.0D, 1.5D,
                KamikazeConfig.noseTriggerDistance));
        assertTrue(KamikazeImpactDetector.isAimedAtTargetInNoseZone(
                0.0D, 0.0D, 0.0D, 90.0F, 0.0F,
                -1.5D, 0.0D, 0.0D,
                KamikazeConfig.noseTriggerDistance));
        assertFalse(KamikazeImpactDetector.isAimedAtTargetInNoseZone(
                0.0D, 0.0D, 0.0D, 0.0F, 0.0F,
                0.0D, 0.0D, -1.5D,
                KamikazeConfig.noseTriggerDistance));
    }
}
