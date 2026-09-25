package net.vas.rdp.uav.target;

import java.util.List;
import java.util.Locale;

public final class TargetSelector<T> {
    public interface Candidate<T> {
        String getRegistryId();
        double getDistanceSquared();
        boolean isValid();
        TargetPriority getPriority();
        T getValue();
    }

    public T select(TargetSpec spec, List<Candidate<T>> candidates) {
        if (spec == null) {
            throw new IllegalArgumentException("Target specification is required");
        }
        if (candidates == null) {
            throw new IllegalArgumentException("Target candidates are required");
        }

        if (spec.getMode() == TargetSelectionMode.MANUAL) {
            Candidate<T> best = null;
            String expectedId = normalizeRegistryId(spec.getRegistryId());
            for (Candidate<T> candidate : candidates) {
                if (candidate == null || !candidate.isValid()) {
                    continue;
                }
                if (!matchesExactRegistry(expectedId, candidate.getRegistryId())) {
                    continue;
                }
                if (best == null || isCloser(candidate, best)) {
                    best = candidate;
                }
            }
            return best == null ? null : best.getValue();
        }

        Candidate<T> best = null;
        for (Candidate<T> candidate : candidates) {
            if (candidate == null || !candidate.isValid() || !matchesNamespace(spec, candidate.getRegistryId())) {
                continue;
            }
            if (best == null || isPreferred(candidate, best)) {
                best = candidate;
            }
        }
        return best == null ? null : best.getValue();
    }

    private boolean isPreferred(Candidate<T> candidate, Candidate<T> current) {
        int priority = Integer.compare(candidate.getPriority().getWeight(),
                current.getPriority().getWeight());
        if (priority > 0) return true;
        if (priority < 0) return false;

        int score = Integer.compare(TargetClassifier.threatScore(candidate.getRegistryId()),
                TargetClassifier.threatScore(current.getRegistryId()));
        if (score > 0) return true;
        if (score < 0) return false;

        int distance = Double.compare(candidate.getDistanceSquared(),
                current.getDistanceSquared());
        if (distance != 0) return distance < 0;

        String candidateId = candidate.getRegistryId() == null
                ? "" : normalizeRegistryId(candidate.getRegistryId());
        String currentId = current.getRegistryId() == null
                ? "" : normalizeRegistryId(current.getRegistryId());
        return candidateId.compareTo(currentId) < 0;
    }

    private boolean isCloser(Candidate<T> candidate, Candidate<T> current) {
        int distance = Double.compare(candidate.getDistanceSquared(), current.getDistanceSquared());
        if (distance != 0) return distance < 0;
        String candidateId = candidate.getRegistryId() == null ? "" : normalizeRegistryId(candidate.getRegistryId());
        String currentId = current.getRegistryId() == null ? "" : normalizeRegistryId(current.getRegistryId());
        return candidateId.compareTo(currentId) < 0;
    }

    private boolean matchesExactRegistry(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return expected.equals(normalizeRegistryId(actual));
    }

    private String normalizeRegistryId(String registryId) {
        if (registryId == null) {
            return null;
        }
        String normalized = registryId.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean matchesNamespace(TargetSpec spec, String registryId) {
        if (registryId == null || spec == null) {
            return false;
        }
        if (TargetClassifier.isHostileNamespace(spec.getNamespace())) {
            return TargetClassifier.matchesHostileNamespace(registryId, spec.getNamespace());
        }
        String namespace = spec.getNamespace();
        return namespace != null && registryId.startsWith(namespace + ":");
    }
}
