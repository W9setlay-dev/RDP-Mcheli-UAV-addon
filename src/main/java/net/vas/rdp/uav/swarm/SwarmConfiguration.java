package net.vas.rdp.uav.swarm;

/** Immutable mission-level swarm coordination settings. */
public final class SwarmConfiguration {
    private final int maximumMembers;
    private final double horizontalSpacing;
    private final double verticalSpacing;
    private final boolean allowDuplicateTargets;
    private final boolean allowCommanderFailover;

    private SwarmConfiguration(Builder builder) {
        maximumMembers = Math.max(1, Math.min(SwarmConfig.maximumSize, builder.maximumMembers));
        horizontalSpacing = Math.max(1D, builder.horizontalSpacing);
        verticalSpacing = Math.max(1D, builder.verticalSpacing);
        allowDuplicateTargets = builder.allowDuplicateTargets;
        allowCommanderFailover = builder.allowCommanderFailover;
    }

    public static Builder builder() { return new Builder(); }
    public static SwarmConfiguration defaults() { return builder().build(); }
    public int getMaximumMembers() { return maximumMembers; }
    public double getHorizontalSpacing() { return horizontalSpacing; }
    public double getVerticalSpacing() { return verticalSpacing; }
    public boolean isAllowDuplicateTargets() { return allowDuplicateTargets; }
    public boolean isAllowCommanderFailover() { return allowCommanderFailover; }

    public static final class Builder {
        private int maximumMembers = SwarmConfig.maximumSize;
        private double horizontalSpacing = SwarmConfig.horizontalSpacing;
        private double verticalSpacing = SwarmConfig.verticalSpacing;
        private boolean allowDuplicateTargets;
        private boolean allowCommanderFailover = true;

        public Builder maximumMembers(int value) { maximumMembers = value; return this; }
        public Builder horizontalSpacing(double value) { horizontalSpacing = value; return this; }
        public Builder verticalSpacing(double value) { verticalSpacing = value; return this; }
        public Builder allowDuplicateTargets(boolean value) { allowDuplicateTargets = value; return this; }
        public Builder allowCommanderFailover(boolean value) { allowCommanderFailover = value; return this; }
        public SwarmConfiguration build() { return new SwarmConfiguration(this); }
    }
}
