package net.vas.rdp.uav.profile;

public final class UavVehicleProfile {
    private final UavExecutionProfile executionProfile;
    private final double executionAltitude;
    private final double minimumExecutionAltitude;
    private final double maximumExecutionAltitude;
    private final double altitudeTolerance;
    private final boolean holdingEnabled;
    private final String terminalPayloadBehavior;
    private final double detectionRange;
    private final double lockRange;
    private final double missionExecutionRange;
    private final String attackWeaponPriority;
    private final double oneWaySpeed;
    private final double attackEnvelope;

    public UavVehicleProfile(UavExecutionProfile executionProfile, double executionAltitude,
                             double minimumExecutionAltitude, double maximumExecutionAltitude,
                             double altitudeTolerance, boolean holdingEnabled,
                             String terminalPayloadBehavior) {
        this(executionProfile, executionAltitude, minimumExecutionAltitude,
                maximumExecutionAltitude, altitudeTolerance, holdingEnabled,
                terminalPayloadBehavior, 256, 256, 64);
    }

    public UavVehicleProfile(UavExecutionProfile executionProfile, double executionAltitude,
                             double minimumExecutionAltitude, double maximumExecutionAltitude,
                             double altitudeTolerance, boolean holdingEnabled,
                             String terminalPayloadBehavior, double detectionRange,
                             double lockRange, double missionExecutionRange) {
        this(executionProfile, executionAltitude, minimumExecutionAltitude,
                maximumExecutionAltitude, altitudeTolerance, holdingEnabled,
                terminalPayloadBehavior, detectionRange, lockRange,
                missionExecutionRange, "", 0, 64);
    }

    public UavVehicleProfile(UavExecutionProfile executionProfile, double executionAltitude,
                             double minimumExecutionAltitude, double maximumExecutionAltitude,
                             double altitudeTolerance, boolean holdingEnabled,
                             String terminalPayloadBehavior, double detectionRange,
                             double lockRange, double missionExecutionRange,
                             String attackWeaponPriority) {
        this(executionProfile, executionAltitude, minimumExecutionAltitude, maximumExecutionAltitude,
                altitudeTolerance, holdingEnabled, terminalPayloadBehavior, detectionRange, lockRange,
                missionExecutionRange, attackWeaponPriority, 0, 64);
    }

    public UavVehicleProfile(UavExecutionProfile executionProfile, double executionAltitude,
                             double minimumExecutionAltitude, double maximumExecutionAltitude,
                             double altitudeTolerance, boolean holdingEnabled,
                             String terminalPayloadBehavior, double detectionRange,
                             double lockRange, double missionExecutionRange,
                             String attackWeaponPriority, double oneWaySpeed,
                             double attackEnvelope) {
        if (executionProfile == null || !Double.isFinite(executionAltitude)
                || !Double.isFinite(minimumExecutionAltitude)
                || !Double.isFinite(maximumExecutionAltitude)
                || !Double.isFinite(altitudeTolerance)
                || executionAltitude < 0 || minimumExecutionAltitude < 0
                || maximumExecutionAltitude < minimumExecutionAltitude
                || executionAltitude < minimumExecutionAltitude
                || executionAltitude > maximumExecutionAltitude
                || altitudeTolerance < 0 || detectionRange <= 0 || lockRange <= 0
                || missionExecutionRange <= 0 || lockRange > detectionRange) {
            throw new IllegalArgumentException("Invalid UAV vehicle profile");
        }
        this.executionProfile = executionProfile;
        this.executionAltitude = executionAltitude;
        this.minimumExecutionAltitude = minimumExecutionAltitude;
        this.maximumExecutionAltitude = maximumExecutionAltitude;
        this.altitudeTolerance = altitudeTolerance;
        this.holdingEnabled = holdingEnabled;
        this.terminalPayloadBehavior = terminalPayloadBehavior == null
                ? "NONE" : terminalPayloadBehavior;
        this.detectionRange = detectionRange;
        this.lockRange = lockRange;
        this.missionExecutionRange = missionExecutionRange;
        this.attackWeaponPriority = attackWeaponPriority == null ? "" : attackWeaponPriority.trim();
        if (oneWaySpeed < 0 || attackEnvelope <= 0) throw new IllegalArgumentException("Invalid UAV envelope");
        this.oneWaySpeed = oneWaySpeed;
        this.attackEnvelope = attackEnvelope;
    }

    public boolean isReusable() {
        return executionProfile == UavExecutionProfile.REUSABLE_UAV
                || executionProfile == UavExecutionProfile.LONG_RANGE_REUSABLE_UAV;
    }
    public boolean isHelicopterProfile() {
        return executionProfile == UavExecutionProfile.HELICOPTER_ONE_WAY_FPV;
    }
    public boolean requiresHelicopterApproachStandoff() {
        return isHelicopterProfile();
    }
    public boolean supportsLongRangeLock() {
        return executionProfile == UavExecutionProfile.LONG_RANGE_REUSABLE_UAV;
    }
    /** Only configured or explicitly recognized airframes may acquire entity locks. */
    public boolean supportsTargetLock() {
        return executionProfile != UavExecutionProfile.UNKNOWN;
    }
    public UavExecutionProfile getExecutionProfile() { return executionProfile; }
    public double getExecutionAltitude() { return executionAltitude; }
    public double getMinimumExecutionAltitude() { return minimumExecutionAltitude; }
    public double getMaximumExecutionAltitude() { return maximumExecutionAltitude; }
    public double getAltitudeTolerance() { return altitudeTolerance; }
    @SuppressWarnings("unused")
    public boolean isHoldingEnabled() { return holdingEnabled; }
    public String getTerminalPayloadBehavior() { return terminalPayloadBehavior; }
    public double getDetectionRange() { return detectionRange; }
    public double getLockRange() { return lockRange; }
    public double getMissionExecutionRange() { return missionExecutionRange; }
    public String getAttackWeaponPriority() { return attackWeaponPriority; }
    /** Zero means use the native WingMan/default speed for this airframe. */
    @SuppressWarnings("unused")
    public double getOneWaySpeed() { return oneWaySpeed; }
    public double getAttackEnvelope() { return attackEnvelope; }
}
