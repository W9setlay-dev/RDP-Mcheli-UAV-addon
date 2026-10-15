package net.vas.rdp.uav.target;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/** Deterministic, explainable classification; it does not authorize actions. */
public final class TargetClassificationPolicy {
    private static final int MAX_PREFIXES_PER_CATEGORY = 64;
    private static final int MAX_TOTAL_CONFIGURED_PREFIXES = 24;
    private static final int MAX_EVIDENCE_ENTRIES = 32;
    private static final int MAX_PREFIX_LENGTH = 128;
    private final List<String> protectedPrefixes;
    private final List<String> friendlyPrefixes;
    private final List<String> hostilePrefixes;
    private final List<String> neutralPrefixes;

    public TargetClassificationPolicy(List<String> protectedPrefixes,
                                      List<String> friendlyPrefixes,
                                      List<String> hostilePrefixes,
                                      List<String> neutralPrefixes) {
        this.protectedPrefixes = normalize(protectedPrefixes);
        this.friendlyPrefixes = normalize(friendlyPrefixes);
        this.hostilePrefixes = normalize(hostilePrefixes);
        this.neutralPrefixes = normalize(neutralPrefixes);
        if (this.protectedPrefixes.size() + this.friendlyPrefixes.size()
                + this.hostilePrefixes.size() + this.neutralPrefixes.size()
                > MAX_TOTAL_CONFIGURED_PREFIXES) {
            throw new IllegalArgumentException("Too many configured classification prefixes");
        }
    }

    public static TargetClassificationPolicy defaults() {
        return TargetClassificationConfig.currentPolicy();
    }

    public TargetClassificationResult classify(TargetIdentity identity,
                                               List<TargetEvidence> additionalEvidence) {
        if (identity == null) throw new IllegalArgumentException("Target identity is required");
        List<TargetEvidence> evidence = new ArrayList<>();
        addRule(evidence, identity, Classification.PROTECTED, protectedPrefixes, 1.0D);
        addRule(evidence, identity, Classification.FRIENDLY, friendlyPrefixes, 0.98D);
        addRule(evidence, identity, Classification.HOSTILE, hostilePrefixes, 0.95D);
        addRule(evidence, identity, Classification.NEUTRAL, neutralPrefixes, 0.9D);
        String registryId = identity.getRegistryId();
        if (matches(registryId, hostilePrefixes).isEmpty()) {
            if (TargetClassifier.isHostileCandidate(registryId)
                    || TargetClassifier.isHostileVehicle(registryId)) {
                evidence.add(new TargetEvidence(Classification.HOSTILE,
                        TargetEvidence.Source.BUILTIN_RULE,
                        TargetClassifier.isHostileVehicle(registryId) ? 0.82D : 0.9D,
                        -1L, "BUILTIN_HOSTILE_REGISTRY_RULE"));
            } else if (TargetClassifier.isVehicleId(registryId)) {
                evidence.add(new TargetEvidence(Classification.NEUTRAL,
                        TargetEvidence.Source.BUILTIN_RULE, 0.65D, -1L,
                        "UNCLASSIFIED_VEHICLE"));
            }
        }
        if (additionalEvidence != null) {
            if (additionalEvidence.size() > MAX_EVIDENCE_ENTRIES
                    || evidence.size() + additionalEvidence.size() > MAX_EVIDENCE_ENTRIES) {
                throw new IllegalArgumentException("Too many target evidence entries");
            }
            for (TargetEvidence item : additionalEvidence) {
                if (item == null) {
                    throw new IllegalArgumentException("Classification evidence is required");
                }
            }
            evidence.addAll(additionalEvidence);
        }
        if (evidence.isEmpty()) {
            return new TargetClassificationResult(Classification.UNKNOWN, 0.0D,
                    false, Collections.emptyList());
        }

        EnumSet<Classification> categories = EnumSet.noneOf(Classification.class);
        for (TargetEvidence item : evidence) categories.add(item.getClassification());
        boolean conflict = categories.size() > 1;
        Classification selected = precedence(evidence);
        double confidence = 0.0D;
        for (TargetEvidence item : evidence) {
            if (item.getClassification() == selected) {
                confidence = Math.max(confidence, item.getConfidence());
            }
        }
        if (conflict && selected == Classification.UNKNOWN) confidence = Math.min(confidence, 0.5D);
        return new TargetClassificationResult(selected, confidence, conflict, evidence);
    }

    private static Classification precedence(List<TargetEvidence> evidence) {
        for (Classification preferred : Arrays.asList(Classification.PROTECTED,
                Classification.FRIENDLY, Classification.HOSTILE,
                Classification.NEUTRAL, Classification.UNKNOWN)) {
            for (TargetEvidence item : evidence) {
                if (item.getClassification() == preferred) return preferred;
            }
        }
        return Classification.UNKNOWN;
    }

    private static void addRule(List<TargetEvidence> evidence, TargetIdentity identity,
                                Classification classification, List<String> rules,
                                double confidence) {
        for (String matched : matches(identity.getRegistryId(), rules)) {
            evidence.add(new TargetEvidence(classification, TargetEvidence.Source.CONFIG_RULE,
                    confidence, -1L, "CONFIG_PREFIX:" + matched));
        }
    }

    private static List<String> matches(String registryId, List<String> prefixes) {
        List<String> result = new ArrayList<>();
        for (String prefix : prefixes) {
            if (registryId.equals(prefix) || registryId.startsWith(prefix + ":")
                    || registryId.startsWith(prefix)) {
                result.add(prefix);
            }
        }
        return result;
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        if (values.size() > MAX_PREFIXES_PER_CATEGORY) {
            throw new IllegalArgumentException("Too many registry prefixes in classification category");
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null) continue;
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.length() > MAX_PREFIX_LENGTH) {
                throw new IllegalArgumentException("Classification registry prefix is too long");
            }
            if (!normalized.isEmpty() && !result.contains(normalized)) result.add(normalized);
        }
        return Collections.unmodifiableList(result);
    }
}
