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
    private TargetSpec targetSpec;
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
    private boolean destructiveAuthorized;
    private boolean paused;
    private int recoveryAttempts;
    private String recoveryReason;
    private static final int MAX_RECOVERY_ATTEMPTS = 5;

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
        if (nextState == state) {
            lastUpdateTick = tick;
            return;
        }
        if (!state.canTransitionTo(nextState)) {
            throw new IllegalStateException("Invalid mission transition "
                    + state + " -> " + nextState + " for " + missionId);
        }
        state = nextState;
        lastUpdateTick = tick;
        queued = false;
    }

    public boolean isTerminal() {
        return state == MissionState.COMPLETE || state == MissionState.ABORTED
                || state == MissionState.FAILED
                || state == MissionState.KAMIKADZE_DETONATED
                || state == MissionState.DIRECT_LAUNCH_COMPLETE;
    }
    public boolean isDestructiveAuthorized() { return destructiveAuthorized; }
    public void authorizeDestructiveAction() { destructiveAuthorized = true; }
    public void setDestructiveAuthorized(boolean value) { destructiveAuthorized = value; }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }

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

    public void applyOperatorEntityLock(UUID targetId, long tick) {
        if (targetId == null) throw new IllegalArgumentException("Target UUID is required");
        if (state == MissionState.SEARCHING) {
            transitionTo(MissionState.TARGET_ACQUIRED, tick);
        } else if (state == MissionState.WAITING_FOR_TARGET || state == MissionState.LOST
                || state == MissionState.REPLAN || state == MissionState.WAITING_FOR_WORLD
                || state == MissionState.WAITING_FOR_FUEL
                || state == MissionState.EXECUTION_STALLED || state == MissionState.RECOVERING) {
            if (state == MissionState.WAITING_FOR_WORLD
                    || state == MissionState.WAITING_FOR_FUEL
                    || state == MissionState.EXECUTION_STALLED
                    || state == MissionState.RECOVERING) {
                transitionTo(MissionState.REPLAN, tick);
            }

            transitionTo(MissionState.SEARCHING, tick);
            transitionTo(MissionState.TARGET_ACQUIRED, tick);
        } else if (state == MissionState.PLANNED) {
            transitionTo(MissionState.WAITING_FOR_TARGET, tick);
            transitionTo(MissionState.SEARCHING, tick);
            transitionTo(MissionState.TARGET_ACQUIRED, tick);
        }
        if (state == MissionState.ATTACK_TARGET || state == MissionState.APPROACHING
                || state == MissionState.KAMIKADZE_TARGET) {
            this.targetId = targetId;
            this.lastTargetId = targetId;
            this.lastTargetTick = tick;
            this.lastUpdateTick = tick;
        } else {
            lockTarget(targetId, tick);
        }
    }

    public void applyOperatorAreaLock(double x, double y, double z, long tick) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Area lock coordinates must be finite");
        }
        this.targetSpec = TargetSpec.point(x, y, z, targetSpec.getPriority());
        this.targetId = null;
        this.lastTargetId = null;
        this.lastTargetTick = tick;
        if (state == MissionState.PLANNED) {
            transitionTo(MissionState.WAITING_FOR_TARGET, tick);
            transitionTo(MissionState.SEARCHING, tick);
        } else if (state == MissionState.WAITING_FOR_WORLD
                || state == MissionState.WAITING_FOR_FUEL
                || state == MissionState.EXECUTION_STALLED
                || state == MissionState.RECOVERING) {
            transitionTo(MissionState.REPLAN, tick);
            transitionTo(MissionState.SEARCHING, tick);
        } else if (state == MissionState.WAITING_FOR_TARGET
                || state == MissionState.LOST
                || state == MissionState.REPLAN) {
            transitionTo(MissionState.SEARCHING, tick);
        }
        if (state == MissionState.SEARCHING) transitionTo(MissionState.TARGET_ACQUIRED, tick);
        if (state == MissionState.TARGET_ACQUIRED) transitionTo(MissionState.TARGET_LOCKED, tick);
        this.lastUpdateTick = tick;
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

        if (isTerminal()) {
            if (state == MissionState.FAILED) {
                return;
            }
            throw new IllegalStateException("Cannot fail terminal mission " + missionId
                    + " in state " + state);
        }
        this.failureReason = reason;
        transitionTo(MissionState.FAILED, tick);
    }

    /**
     * Records a recoverable condition. FAILED is reserved for exhausted
     * recovery, not for a single missing entity, chunk, or stalled route.
     */
    public boolean defer(String reason, MissionState recoveryState, long tick) {
        if (reason == null || reason.trim().isEmpty() || recoveryState == null) {
            throw new IllegalArgumentException("Recovery reason and state are required");
        }
        if (isTerminal()) return false;
        recoveryAttempts++;
        recoveryReason = reason;
        if (recoveryAttempts > MAX_RECOVERY_ATTEMPTS) {
            fail(reason + "_RECOVERY_EXHAUSTED", tick);
            return false;
        }
        transitionTo(recoveryState, tick);
        return true;
    }

    public void clearRecovery() {
        recoveryAttempts = 0;
        recoveryReason = null;
    }

    public void restoreRecovery(int attempts, String reason) {
        recoveryAttempts = Math.max(0, Math.min(MAX_RECOVERY_ATTEMPTS, attempts));
        recoveryReason = reason == null || reason.trim().isEmpty() ? null : reason;
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
    public int getRecoveryAttempts() { return recoveryAttempts; }
    public String getRecoveryReason() { return recoveryReason; }
}
