package net.vas.rdp.uav.target;

import org.junit.Test;

import java.util.Collections;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TargetClassificationPolicyTest {
    @Test
    public void protectedIdentityTakesPrecedenceOverHostileRules() {
        TargetClassificationPolicy policy = new TargetClassificationPolicy(
                Collections.singletonList("example:protected"),
                Collections.<String>emptyList(),
                Collections.singletonList("example:"),
                Collections.<String>emptyList());

        TargetClassificationResult result = policy.classify(
                new TargetIdentity("example:protected_unit", null, 0),
                Collections.<TargetEvidence>emptyList());

        assertEquals(Classification.PROTECTED, result.getClassification());
        assertTrue(result.hasConflictingEvidence());
        assertEquals(1.0D, result.getConfidence(), 0.0D);
        assertTrue(result.explain().contains("CONFIG_PREFIX:example:protected"));
    }

    @Test
    public void friendlyRuleWinsConflictingHostileEvidenceAndExplainsConflict() {
        TargetClassificationPolicy policy = new TargetClassificationPolicy(
                Collections.<String>emptyList(),
                Collections.singletonList("example:unit"),
                Collections.<String>emptyList(),
                Collections.<String>emptyList());
        TargetEvidence hostileReport = new TargetEvidence(Classification.HOSTILE,
                TargetEvidence.Source.SENSOR, 0.8D, 120L, "SIMULATED_HOSTILE_SIGNAL");

        TargetClassificationResult result = policy.classify(
                new TargetIdentity("example:unit", null, 0),
                Collections.singletonList(hostileReport));

        assertEquals(Classification.FRIENDLY, result.getClassification());
        assertTrue(result.hasConflictingEvidence());
        assertTrue(result.explain().contains("SIMULATED_HOSTILE_SIGNAL"));
    }

    @Test
    public void configuredHostilePrefixProducesBoundedConfidence() {
        TargetClassificationPolicy policy = new TargetClassificationPolicy(
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Arrays.asList("example:enemy"), Collections.<String>emptyList());
        TargetClassificationResult result = policy.classify(
                new TargetIdentity("example:enemy_drone", null, 7),
                Collections.<TargetEvidence>emptyList());

        assertEquals(Classification.HOSTILE, result.getClassification());
        assertTrue(result.getConfidence() >= 0.0D && result.getConfidence() <= 1.0D);
        assertFalse(result.hasConflictingEvidence());
    }

    @Test
    public void vehicleSelectionExceptionCannotOverrideProtectedOrFriendlyClassification() {
        TargetClassificationPolicy protectedPolicy = new TargetClassificationPolicy(
                Collections.singletonList("example:unit"),
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.<String>emptyList());
        TargetClassificationPolicy friendlyPolicy = new TargetClassificationPolicy(
                Collections.<String>emptyList(), Collections.singletonList("example:unit"),
                Collections.<String>emptyList(), Collections.<String>emptyList());

        assertFalse(TargetPolicy.allowsVehicleSelectionOverride(true, true,
                protectedPolicy.classify(new TargetIdentity("example:unit", null, 0),
                        Collections.<TargetEvidence>emptyList())));
        assertFalse(TargetPolicy.allowsVehicleSelectionOverride(true, true,
                friendlyPolicy.classify(new TargetIdentity("example:unit", null, 0),
                        Collections.<TargetEvidence>emptyList())));
        assertTrue(TargetPolicy.allowsVehicleSelectionOverride(true, true,
                TargetClassifier.classifyDetailed("example:unclassified_vehicle")));
        assertFalse(TargetPolicy.allowsVehicleSelectionOverride(true, false,
                TargetClassifier.classifyDetailed("example:unclassified_vehicle")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void evidenceRejectsConfidenceAboveOne() {
        new TargetEvidence(Classification.HOSTILE, TargetEvidence.Source.SENSOR,
                1.01D, 10L, "INVALID_CONFIDENCE");
    }

    @Test(expected = IllegalArgumentException.class)
    public void targetPolicyRejectsConfidenceThresholdOutsideBounds() {
        TargetPolicy.builder().withMinimumConfidence(1.01D);
    }

    @Test(expected = IllegalArgumentException.class)
    public void classificationPolicyRejectsExcessiveConfiguredPrefixes() {
        java.util.List<String> prefixes = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) prefixes.add("example:unit" + i);
        new TargetClassificationPolicy(Collections.<String>emptyList(),
                Collections.<String>emptyList(), prefixes,
                Collections.<String>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void targetIdentityRejectsOversizedRegistryIds() {
        char[] chars = new char[257];
        Arrays.fill(chars, 'a');
        new TargetIdentity(new String(chars), null, 0);
    }
}
