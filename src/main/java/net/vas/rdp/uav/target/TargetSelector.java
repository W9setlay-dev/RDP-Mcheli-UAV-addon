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

        return candidate.getDistanceSquared() < current.getDistanceSquared();
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
