package net.vas.rdp.uav.target;

import java.util.List;

public final class TargetAcquisition<T> {
    private final TargetSelector<T> selector = new TargetSelector<T>();

    public T acquire(TargetSpec specification, List<TargetSelector.Candidate<T>> candidates) {
        if (specification == null || candidates == null) {
            throw new IllegalArgumentException("Target specification and candidates are required");
        }
        return selector.select(specification, candidates);
    }
}
