package net.vas.rdp.uav.profile;

/**
 * Immutable capability projection over the existing vehicle profile.
 *
 * <p>This is a policy query surface, not a second profile registry. Existing
 * profile values remain authoritative until a later approved phase wires this
 * projection into mission decisions.</p>
 */
public final class VehicleCapability {
    private final UavExecutionProfile executionProfile;
    private final double minimumAltitude;
    private final double maximumAltitude;
    private final boolean hover;
    private final boolean orbit;
    private final boolean landing;
    private final boolean returnToBase;
    private final boolean movingTarget;
    private final boolean positionTarget;
    private final boolean terminalGuidance;
    private final MissionDoctrine doctrine;

    private VehicleCapability(UavExecutionProfile executionProfile,
                              double minimumAltitude, double maximumAltitude,
                              boolean hover, boolean orbit, boolean landing,
                              boolean returnToBase, boolean movingTarget,
                              boolean positionTarget, boolean terminalGuidance,
                              MissionDoctrine doctrine) {
        this.executionProfile = executionProfile;
        this.minimumAltitude = minimumAltitude;
        this.maximumAltitude = maximumAltitude;
        this.hover = hover;
        this.orbit = orbit;
        this.landing = landing;
        this.returnToBase = returnToBase;
        this.movingTarget = movingTarget;
        this.positionTarget = positionTarget;
        this.terminalGuidance = terminalGuidance;
        this.doctrine = doctrine;
    }

    public static VehicleCapability fromProfile(UavVehicleProfile profile) {
        if (profile == null) throw new IllegalArgumentException("Vehicle profile is required");
        UavExecutionProfile execution = profile.getExecutionProfile();
        boolean helicopter = profile.isHelicopterProfile();
        boolean reusable = profile.isReusable();
        MissionDoctrine doctrine = reusable
                ? MissionDoctrine.REUSABLE_STRIKE
                : helicopter || execution == UavExecutionProfile.ONE_WAY
                ? MissionDoctrine.EXPENDABLE_ATTACK
                : execution == UavExecutionProfile.UNKNOWN
                ? MissionDoctrine.SEARCH : MissionDoctrine.RECONNAISSANCE;
        return new VehicleCapability(execution,
                profile.getMinimumExecutionAltitude(),
                profile.getMaximumExecutionAltitude(),
                helicopter, reusable, reusable, reusable,
                execution != UavExecutionProfile.UNKNOWN,
                execution != UavExecutionProfile.UNKNOWN,
                helicopter || execution == UavExecutionProfile.ONE_WAY,
                doctrine);
    }

    public UavExecutionProfile getExecutionProfile() { return executionProfile; }
    public double getMinimumAltitude() { return minimumAltitude; }
    public double getMaximumAltitude() { return maximumAltitude; }
    public boolean canHover() { return hover; }
    public boolean canOrbit() { return orbit; }
    public boolean canLand() { return landing; }
    public boolean canReturnToBase() { return returnToBase; }
    public boolean supportsMovingTarget() { return movingTarget; }
    public boolean supportsPositionTarget() { return positionTarget; }
    public boolean supportsTerminalGuidance() { return terminalGuidance; }
    public MissionDoctrine getDoctrine() { return doctrine; }
}
