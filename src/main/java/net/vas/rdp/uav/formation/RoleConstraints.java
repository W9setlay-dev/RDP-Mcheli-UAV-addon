package net.vas.rdp.uav.formation;

/** Immutable, server-produced mission-state constraints for one swarm member. */
public final class RoleConstraints {
    private final double detectionRangeMultiplier;
    private final boolean engagementAuthorized;
    private final int formationPositionPriority;
    private final boolean holdFormation;

    /** Creates a validated tasking result. */
    public RoleConstraints(double detectionRangeMultiplier,
                           boolean engagementAuthorized,
                           int formationPositionPriority,
                           boolean holdFormation) {
        if (!Double.isFinite(detectionRangeMultiplier) || detectionRangeMultiplier < 0.0D
                || formationPositionPriority < 0) {
            throw new IllegalArgumentException("Invalid role constraints");
        }
        this.detectionRangeMultiplier = detectionRangeMultiplier;
        this.engagementAuthorized = engagementAuthorized;
        this.formationPositionPriority = formationPositionPriority;
        this.holdFormation = holdFormation;
    }

    public double getDetectionRangeMultiplier() { return detectionRangeMultiplier; }
    public boolean isEngagementAuthorized() { return engagementAuthorized; }
    public int getFormationPositionPriority() { return formationPositionPriority; }
    public boolean isHoldFormation() { return holdFormation; }
}
