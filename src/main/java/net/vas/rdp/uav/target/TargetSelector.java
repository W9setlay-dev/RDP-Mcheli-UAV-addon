package net.vas.rdp.uav.target;

import java.util.List;

public final class TargetSelector<T> {
    public interface Candidate<T> {
        String getRegistryId();
        double getDistanceSquared();
        boolean isValid();
        TargetPriority getPriority();
        T getValue();
    }

    public T select(TargetSpec spec, List<Candidate<T>> candidates) {
        if (spec.getMode() == TargetSelectionMode.MANUAL) {
            for (Candidate<T> candidate : candidates) {
                if (candidate.isValid() && spec.getRegistryId().equals(candidate.getRegistryId())) {
                    return candidate.getValue();
                }
            }
            return null;
        }

        Candidate<T> best = null;
        for (Candidate<T> candidate : candidates) {
            if (!candidate.isValid() || !matchesNamespace(spec, candidate.getRegistryId())) {
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

        // Stable ordering prevents target selection from changing with
        // loaded-entity iteration order when candidates are otherwise equal.
        String candidateId = candidate.getRegistryId() == null
                ? "" : candidate.getRegistryId();
        String currentId = current.getRegistryId() == null
                ? "" : current.getRegistryId();
        return candidateId.compareTo(currentId) < 0;
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
