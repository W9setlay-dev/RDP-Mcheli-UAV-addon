package net.vas.rdp.uav.target;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

        List<Candidate<T>> matching = new ArrayList<Candidate<T>>();
        for (Candidate<T> candidate : candidates) {
            if (candidate.isValid() && matchesNamespace(spec, candidate.getRegistryId())) {
                matching.add(candidate);
            }
        }
        Collections.sort(matching, new Comparator<Candidate<T>>() {
            @Override
            public int compare(Candidate<T> left, Candidate<T> right) {
                int priority = Integer.compare(right.getPriority().getWeight(),
                        left.getPriority().getWeight());
                if (priority != 0) {
                    return priority;
                }
                return Double.compare(left.getDistanceSquared(), right.getDistanceSquared());
            }
        });
        return matching.isEmpty() ? null : matching.get(0).getValue();
    }

    private boolean matchesNamespace(TargetSpec spec, String registryId) {
        return registryId != null && registryId.startsWith(spec.getNamespace() + ":");
    }
}
