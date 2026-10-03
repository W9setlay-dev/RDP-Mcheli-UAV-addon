package net.vas.rdp.uav.swarm;

/** Immutable mission-level swarm coordination settings. */
public final class SwarmConfiguration {
    private final int maximumMembers;
    private final double horizontalSpacing;
    private final double verticalSpacing;
    private final boolean allowDuplicateTargets;
    private final boolean allowCommanderFailover;
    private final SwarmFormation formation;
    private final SearchPattern searchPattern;
    private final SwarmLeadership leadership;

    private SwarmConfiguration(Builder builder) {
        if (builder.maximumMembers < 1 || builder.maximumMembers > 64
                || !Double.isFinite(builder.horizontalSpacing)
                || builder.horizontalSpacing < 1D || builder.horizontalSpacing > 512D
                || !Double.isFinite(builder.verticalSpacing)
                || builder.verticalSpacing < 1D || builder.verticalSpacing > 512D
                || builder.formation == null || builder.searchPattern == null
                || builder.leadership == null) {
            throw new IllegalArgumentException("Swarm configuration is outside supported bounds");
        }
        maximumMembers = builder.maximumMembers;
        horizontalSpacing = builder.horizontalSpacing;
        verticalSpacing = builder.verticalSpacing;
        allowDuplicateTargets = builder.allowDuplicateTargets;
        allowCommanderFailover = builder.allowCommanderFailover;
        formation = builder.formation;
        searchPattern = builder.searchPattern;
        leadership = builder.leadership;
    }

    public static Builder builder() { return new Builder(); }
    public static SwarmConfiguration defaults() { return builder().build(); }
    public int getMaximumMembers() { return maximumMembers; }
    public double getHorizontalSpacing() { return horizontalSpacing; }
    public double getVerticalSpacing() { return verticalSpacing; }
    public boolean isAllowDuplicateTargets() { return allowDuplicateTargets; }
    public boolean isAllowCommanderFailover() { return allowCommanderFailover; }
    public SwarmFormation getFormation() { return formation; }
    public SearchPattern getSearchPattern() { return searchPattern; }
    public SwarmLeadership getLeadership() { return leadership; }

    public Builder toBuilder() {
        return new Builder().maximumMembers(maximumMembers)
                .horizontalSpacing(horizontalSpacing)
                .verticalSpacing(verticalSpacing)
                .allowDuplicateTargets(allowDuplicateTargets)
                .allowCommanderFailover(allowCommanderFailover)
                .formation(formation).searchPattern(searchPattern).leadership(leadership);
    }

    public static final class Builder {
        private int maximumMembers = SwarmConfig.maximumSize;
        private double horizontalSpacing = SwarmConfig.horizontalSpacing;
        private double verticalSpacing = SwarmConfig.verticalSpacing;
        private boolean allowDuplicateTargets;
        private boolean allowCommanderFailover = true;
        private SwarmFormation formation = SwarmFormation.LINE;
        private SearchPattern searchPattern = SearchPattern.GRID;
        private SwarmLeadership leadership = SwarmLeadership.DYNAMIC_LEADER;

        public Builder maximumMembers(int value) { maximumMembers = value; return this; }
        public Builder horizontalSpacing(double value) { horizontalSpacing = value; return this; }
        public Builder verticalSpacing(double value) { verticalSpacing = value; return this; }
        public Builder allowDuplicateTargets(boolean value) { allowDuplicateTargets = value; return this; }
        public Builder allowCommanderFailover(boolean value) { allowCommanderFailover = value; return this; }
        public Builder formation(SwarmFormation value) { formation = value; return this; }
        public Builder searchPattern(SearchPattern value) { searchPattern = value; return this; }
        public Builder leadership(SwarmLeadership value) { leadership = value; return this; }
        public SwarmConfiguration build() { return new SwarmConfiguration(this); }
    }
}
