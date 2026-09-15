package net.vas.rdp.uav.client;

import net.vas.rdpcore.region.Hotspot;
import net.vas.rdpcore.world.Scar;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RdpUavAnomalyAlertTest {
    @Test
    public void gravityHotspotsTriggerHighPriorityAlerts() {
        Map<String, Hotspot> hotspots = new LinkedHashMap<String, Hotspot>();
        hotspots.put("gravity-1", new Hotspot(32, 64, 48, 18.0D, 0.92D, "gravity"));

        RdpUavAnomalyAlert.Alert alert = RdpUavAnomalyAlert.evaluateAgainstRdpData(
                0.82D, 0.63D, "minecraft:desert",
                hotspots, Collections.<String, Scar>emptyMap(), "RDPV");

        assertNotNull(alert);
        assertTrue(alert.kind.contains("GRAVITY") || alert.summary.contains("GRAVITY"));
        assertTrue(alert.severity >= 0.9D);
    }

    @Test
    public void distortedBiomeHintsTriggerAWarningWithoutAHotspot() {
        RdpUavAnomalyAlert.Alert alert = RdpUavAnomalyAlert.evaluateAgainstRdpData(
                0.58D, 0.42D, "minecraft:scorched_hell",
                Collections.<String, Hotspot>emptyMap(), Collections.<String, Scar>emptyMap(), "RDPIII");

        assertNotNull(alert);
        assertTrue(alert.kind.contains("DISTORTED") || alert.summary.contains("DISTORTED")
                || alert.summary.contains("RDP"));
    }

    @Test
    public void scarRecordsAndRegionalPressureStillTriggerAWarning() {
        Map<String, Scar> scars = new LinkedHashMap<String, Scar>();
        scars.put("scar-1", new Scar("scar-1", null, 3, 4, 0.8D, "rift"));

        RdpUavAnomalyAlert.Alert alert = RdpUavAnomalyAlert.evaluateAgainstRdpData(
                0.4D, 0.62D, "minecraft:plains",
                Collections.<String, Hotspot>emptyMap(), scars, "RDPII");

        assertNotNull(alert);
        assertTrue(alert.kind.contains("SCAR") || alert.summary.contains("SCAR")
                || alert.summary.contains("RDP"));
    }
}
