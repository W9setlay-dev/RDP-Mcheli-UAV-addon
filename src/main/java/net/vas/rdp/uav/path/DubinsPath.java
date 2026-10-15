package net.vas.rdp.uav.path;

import net.vas.rdp.uav.deconfliction.TrajectorySample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable sampled Dubins path in horizontal world coordinates. */
public final class DubinsPath {
    private final DubinsPathType type;
    private final List<TrajectorySample> samples;
    private final double totalLength;

    /** Creates a validated path result. */
    public DubinsPath(DubinsPathType type, List<TrajectorySample> samples,
                      double totalLength) {
        if (type == null || samples == null || samples.isEmpty()
                || !Double.isFinite(totalLength) || totalLength < 0.0D) {
            throw new IllegalArgumentException("Invalid Dubins path");
        }
        this.type = type;
        this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
        this.totalLength = totalLength;
    }

    public DubinsPathType getType() { return type; }
    public List<TrajectorySample> getSamples() { return samples; }
    public double getTotalLength() { return totalLength; }
}
