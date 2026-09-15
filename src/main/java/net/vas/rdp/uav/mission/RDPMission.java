package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSelectionMode;
import net.vas.rdp.uav.target.TargetSpec;

import java.util.UUID;

public final class RDPMission {
    private final UUID missionId;
    private final UUID ownerId;
    private final UUID aircraftId;
    private final String aircraftType;
    private final MissionType missionType;
    private final TargetSpec targetSpec;
    private final boolean attack;
    private final boolean eliminateAllCost;
    private final boolean deepScan;
    private final double searchRadius;
    private final double observationDistance;
    private final long createdTick;
    private MissionState state;
    private UUID targetId;
    private UUID lastTargetId;
    private String lastTargetReason;
    private long lastTargetTick = -1L;
    private long lastUpdateTick;
    private String failureReason;
    private String reportId;
    private int dimension;
    private boolean queued = true;
    private String recoveryPolicy = "RETURN_TO_BASE";

    public RDPMission(UUID missionId, UUID ownerId, UUID aircraftId, String aircraftType,
                      MissionType missionType, TargetSpec targetSpec, boolean attack,
                      boolean eliminateAllCost, boolean deepScan, double searchRadius,
                      double observationDistance, long createdTick) {
        if (missionId == null || ownerId == null || aircraftId == null || aircraftType == null
                || missionType == null || targetSpec == null || searchRadius <= 0
                || observationDistance <= 0) {
            throw new IllegalArgumentException("Invalid mission configuration");
        }
        this.missionId = missionId;
        this.ownerId = ownerId;
        this.aircraftId = aircraftId;
        this.aircraftType = aircraftType;
        this.missionType = missionType;
        this.targetSpec = targetSpec;
        this.attack = attack;
        this.eliminateAllCost = eliminateAllCost;
        this.deepScan = deepScan;
        this.searchRadius = searchRadius;
        this.observationDistance = observationDistance;
        this.createdTick = createdTick;
        this.lastUpdateTick = createdTick;
        this.state = MissionState.PLANNED;
    }

    public void transitionTo(MissionState nextState, long tick) {
        if (nextState == null || tick < lastUpdateTick) {
            throw new IllegalArgumentException("Invalid mission transition");
        }
        if (!state.canTransitionTo(nextState)) {
            throw new IllegalStateException("Mission is terminal");
        }
        state = nextState;
        lastUpdateTick = tick;
        queued = false;
    }

    public void lockTarget(UUID targetId, long tick) {
        if (targetId == null) {
            throw new IllegalArgumentException("Target UUID is required");
        }
        this.targetId = targetId;
        this.lastTargetId = targetId;
        transitionTo(MissionState.TARGET_LOCKED, tick);
    }

    public void lockPoint(long tick) {
        targetId = null;
        transitionTo(MissionState.TARGET_LOCKED, tick);
    }

    /**
     * Updates a locked entity after it was unloaded and found again.  This
     * deliberately does not change the mission state: reacquisition is a
     * recovery operation.
     */
    public void reacquireTarget(UUID targetId, long tick) {
        if (targetId == null || tick < lastUpdateTick) {
            throw new IllegalArgumentException("Target UUID and tick are required");
        }
        this.targetId = targetId;
        this.lastTargetId = targetId;
        this.lastTargetTick = tick;
        this.lastUpdateTick = tick;
    }

    public void clearTarget(String reason, long tick) {
        if (reason == null || reason.trim().isEmpty() || tick < lastUpdateTick) {
            throw new IllegalArgumentException("Target clear reason and tick are required");
        }
        if (targetId != null) lastTargetId = targetId;
        targetId = null;
        lastTargetReason = reason;
        lastTargetTick = tick;
        lastUpdateTick = tick;
    }

    public void fail(String reason, long tick) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Failure reason is required");
        }

        this.failureReason = reason;
        transitionTo(MissionState.FAILED, tick);
    }

    public void markKamikazeDetonated(long tick) {
        transitionTo(MissionState.KAMIKADZE_DETONATED, tick);
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public void setDimension(int dimension) { this.dimension = dimension; }
    public void setQueued(boolean queued) { this.queued = queued; }
    public void setRecoveryPolicy(String recoveryPolicy) {
        if (recoveryPolicy == null || recoveryPolicy.trim().isEmpty()) {
            throw new IllegalArgumentException("Recovery policy is required");
        }
        this.recoveryPolicy = recoveryPolicy;
    }

    public static RDPMission restore(UUID missionId, UUID ownerId, UUID aircraftId,
                                     String aircraftType, MissionType missionType,
                                     TargetSpec targetSpec, boolean attack,
                                     boolean eliminateAllCost, boolean deepScan,
                                     double searchRadius, double observationDistance,
                                     long createdTick, MissionState state, UUID targetId,
                                     long lastUpdateTick, String failureReason,
                                     String reportId) {
        RDPMission mission = new RDPMission(missionId, ownerId, aircraftId, aircraftType,
                missionType, targetSpec, attack, eliminateAllCost, deepScan, searchRadius,
                observationDistance, createdTick);
        mission.state = state;
        mission.targetId = targetId;
        mission.lastUpdateTick = lastUpdateTick;
        mission.failureReason = failureReason;
        mission.reportId = reportId;
        return mission;
    }

    public UUID getMissionId() { return missionId; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getAircraftId() { return aircraftId; }
    public String getAircraftType() { return aircraftType; }
    public MissionType getMissionType() { return missionType; }
    public TargetSpec getTargetSpec() { return targetSpec; }
    public TargetSelectionMode getTargetSelectionMode() { return targetSpec.getMode(); }
    public TargetPriority getTargetPriority() { return targetSpec.getPriority(); }
    public boolean isAttack() { return attack; }
    public boolean isEliminateAllCost() { return eliminateAllCost; }
    public boolean isDeepScan() { return deepScan; }
    public double getSearchRadius() { return searchRadius; }
    public double getObservationDistance() { return observationDistance; }
    /**
     * Mission telemetry and validation should not confuse target acquisition
     * radius with the smaller engagement/attack radius.  Use the broader of the
     * two when reporting whether the current target remains within the UAV's
     * active mission envelope.
     */
    public double getMissionRangeLimit() {
        if (missionType == MissionType.SQUARE_STRIKE) {
            return Math.max(MissionLimits.MAX_SQUARE_STRIKE_DISTANCE,
                    Math.max(searchRadius, observationDistance));
        }
        return Math.max(searchRadius, observationDistance);
    }
    public long getCreatedTick() { return createdTick; }
    public long getLastUpdateTick() { return lastUpdateTick; }
    public MissionState getState() { return state; }
    public UUID getTargetId() { return targetId; }
    public UUID getLastTargetId() { return lastTargetId; }
    public String getLastTargetReason() { return lastTargetReason; }
    public long getLastTargetTick() { return lastTargetTick; }
    public String getFailureReason() { return failureReason; }
    public String getReportId() { return reportId; }
    public int getDimension() { return dimension; }
    public boolean isQueued() { return queued; }
    public String getRecoveryPolicy() { return recoveryPolicy; }
}
