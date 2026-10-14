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
    private final double stationRadius;
    private final double maximumYawCorrectionDegrees;
    private final double maximumPitchCorrectionDegrees;

    public UavVehicleProfile(UavExecutionProfile executionProfile, double executionAltitude,
                             double minimumExecutionAltitude, double maximumExecutionAltitude,
                             double altitudeTolerance, boolean holdingEnabled,
                             String terminalPayloadBehavior) {
        this(executionProfile, executionAltitude, minimumExecutionAltitude,
                maximumExecutionAltitude, altitudeTolerance, holdingEnabled,
                terminalPayloadBehavior, 256, 256, 64, "", 0, 64,
                defaultStationRadius(executionProfile), 20, 10);
    }

    public UavVehicleProfile(UavExecutionProfile executionProfile, double executionAltitude,
                             double minimumExecutionAltitude, double maximumExecutionAltitude,
                             double altitudeTolerance, boolean holdingEnabled,
                             String terminalPayloadBehavior, double detectionRange,
                             double lockRange, double missionExecutionRange) {
        this(executionProfile, executionAltitude, minimumExecutionAltitude,
                maximumExecutionAltitude, altitudeTolerance, holdingEnabled,
                terminalPayloadBehavior, detectionRange, lockRange,
                missionExecutionRange, "", 0, 64,
                defaultStationRadius(executionProfile), 20, 10);
    }

    public UavVehicleProfile(UavExecutionProfile executionProfile, double executionAltitude,
                             double minimumExecutionAltitude, double maximumExecutionAltitude,
                             double altitudeTolerance, boolean holdingEnabled,
                             String terminalPayloadBehavior, double detectionRange,
                             double lockRange, double missionExecutionRange,
                             String attackWeaponPriority) {
        this(executionProfile, executionAltitude, minimumExecutionAltitude, maximumExecutionAltitude,
                altitudeTolerance, holdingEnabled, terminalPayloadBehavior, detectionRange, lockRange,
                missionExecutionRange, attackWeaponPriority, 0, 64,
                defaultStationRadius(executionProfile), 20, 10);
    }

    public UavVehicleProfile(UavExecutionProfile executionProfile, double executionAltitude,
                             double minimumExecutionAltitude, double maximumExecutionAltitude,
                             double altitudeTolerance, boolean holdingEnabled,
                             String terminalPayloadBehavior, double detectionRange,
                             double lockRange, double missionExecutionRange,
                             String attackWeaponPriority, double oneWaySpeed,
                             double attackEnvelope) {
        this(executionProfile, executionAltitude, minimumExecutionAltitude,
                maximumExecutionAltitude, altitudeTolerance, holdingEnabled,
                terminalPayloadBehavior, detectionRange, lockRange, missionExecutionRange,
                attackWeaponPriority, oneWaySpeed, attackEnvelope,
                defaultStationRadius(executionProfile), 20, 10);
    }

    public UavVehicleProfile(UavExecutionProfile executionProfile, double executionAltitude,
                             double minimumExecutionAltitude, double maximumExecutionAltitude,
                             double altitudeTolerance, boolean holdingEnabled,
                             String terminalPayloadBehavior, double detectionRange,
                             double lockRange, double missionExecutionRange,
                             String attackWeaponPriority, double oneWaySpeed,
                             double attackEnvelope, double stationRadius,
                             double maximumYawCorrectionDegrees,
                             double maximumPitchCorrectionDegrees) {
        if (executionProfile == null || !Double.isFinite(executionAltitude)
                || !Double.isFinite(minimumExecutionAltitude)
                || !Double.isFinite(maximumExecutionAltitude)
                || !Double.isFinite(altitudeTolerance)
                || executionAltitude < 0 || minimumExecutionAltitude < 0
                || maximumExecutionAltitude < minimumExecutionAltitude
                || executionAltitude < minimumExecutionAltitude
                || executionAltitude > maximumExecutionAltitude
                || altitudeTolerance < 0 || detectionRange <= 0 || lockRange <= 0
                || missionExecutionRange <= 0 || lockRange > detectionRange
                || !Double.isFinite(stationRadius) || stationRadius < 0.0D
                || !Double.isFinite(maximumYawCorrectionDegrees)
                || maximumYawCorrectionDegrees < 0.0D || maximumYawCorrectionDegrees > 45.0D
                || !Double.isFinite(maximumPitchCorrectionDegrees)
                || maximumPitchCorrectionDegrees < 0.0D || maximumPitchCorrectionDegrees > 20.0D) {
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
        this.stationRadius = stationRadius;
        this.maximumYawCorrectionDegrees = maximumYawCorrectionDegrees;
        this.maximumPitchCorrectionDegrees = maximumPitchCorrectionDegrees;
    }

    public boolean isReusable() {
        return executionProfile == UavExecutionProfile.REUSABLE_UAV
                || executionProfile == UavExecutionProfile.LONG_RANGE_REUSABLE_UAV;
    }
    public boolean supportsStationHold() {
        return holdingEnabled || isHelicopterProfile();
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
    /** These AGL defaults are editable game tuning values, not measured performance data. */
    public double getCruiseAgl() { return executionAltitude; }
    public double getMinimumAgl() { return minimumExecutionAltitude; }
    public double getMaximumAgl() { return maximumExecutionAltitude; }
    public double getStationRadius() { return stationRadius; }
    public double getMaximumYawCorrectionDegrees() { return maximumYawCorrectionDegrees; }
    public double getMaximumPitchCorrectionDegrees() { return maximumPitchCorrectionDegrees; }

    private static double defaultStationRadius(UavExecutionProfile profile) {
        if (profile == UavExecutionProfile.LONG_RANGE_REUSABLE_UAV
                || profile == UavExecutionProfile.REUSABLE_UAV) return 256.0D;
        if (profile == UavExecutionProfile.HELICOPTER_ONE_WAY_FPV) return 0.0D;
        return 96.0D;
    }

    /** Conservative performance envelope used by the addon controller. */
    public double getCruiseSpeed() {
        if (oneWaySpeed > 0.0D) return oneWaySpeed;
        switch (executionProfile) {
            case LONG_RANGE_REUSABLE_UAV: return 1.35D;
            case REUSABLE_UAV: return 1.10D;
            case HELICOPTER_ONE_WAY_FPV: return 0.75D;
            case ONE_WAY: return 1.25D;
            default: return 0.8D;
        }
    }

    public double getStallSpeed() {
        return isHelicopterProfile() ? 0.15D : Math.max(0.35D, getCruiseSpeed() * 0.45D);
    }

    public double getMaximumClimbRate() {
        return isHelicopterProfile() ? 0.8D : 0.45D;
    }

    public double getMaximumDescentRate() {
        return isHelicopterProfile() ? 0.9D : 0.7D;
    }

    public double getMaximumBankDegrees() {
        return isHelicopterProfile() ? 0.0D : 35.0D;
    }

    public double getTurnRadius() {
        return isHelicopterProfile() ? 6.0D
                : Math.max(12.0D, getCruiseSpeed() * 18.0D);
    }
}
