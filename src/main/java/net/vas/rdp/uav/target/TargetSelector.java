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
        return priority > 0 || (priority == 0
                && candidate.getDistanceSquared() < current.getDistanceSquared());
    }

    private boolean matchesNamespace(TargetSpec spec, String registryId) {
        return registryId != null && registryId.startsWith(spec.getNamespace() + ":");
    }
}
