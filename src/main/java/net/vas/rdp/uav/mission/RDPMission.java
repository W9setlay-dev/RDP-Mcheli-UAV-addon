package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.RdpUavDebug;
import net.vas.rdp.uav.flight.RouteProgressTracker;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSelectionMode;
import net.vas.rdp.uav.target.TargetSpec;
import net.vas.rdp.uav.target.TargetLock;
import net.vas.rdp.uav.target.TargetPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private TargetLock targetLock;
    private UUID lastTargetId;
    private String lastTargetReason;
    private long lastTargetTick = -1L;
    private long lastUpdateTick;
    private double routeProgress = 0.0D;
    private long lastMeaningfulProgressTick;
    private double bestMeasuredTargetDistance = Double.NaN;
    private double bestMeasuredWaypointDistance = Double.NaN;
    private String failureReason;
    private String reportId;
    private int dimension;
    private boolean queued = true;
    private String recoveryPolicy = "RETURN_TO_BASE";
    private boolean destructiveAuthorized;
    private boolean kamikazePayloadLaunched;
    private boolean paused;
    private String guidedWeapon;
    private int recoveryAttempts;
    private String recoveryReason;
    private double squareWidth;
    private double squareDepth;
    private static final double PROGRESS_DISTANCE_THRESHOLD = 0.5D;
    private static final long TARGET_LOSS_REACQUIRE_TICKS = 15L * 20L;
    private MissionPhase phase = MissionPhase.PLANNING;
    private ControlOwner controlOwner = ControlOwner.MISSION_AI;
    private MissionExecutionStage executionStage = MissionExecutionStage.PLANNING;
    private UUID parentMissionId;
    private UUID childMissionId;
    private UUID swarmId;
    public static final int MAX_RECOVERY_ATTEMPTS = 5;
    private String lastTransitionReason;
    private TargetPolicy.Snapshot targetPolicy = TargetPolicy.builder().snapshot();
    private List<MissionWaypoint> patrolRoute = Collections.emptyList();

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
        this.lastMeaningfulProgressTick = createdTick;
        this.state = MissionState.PLANNED;
    }

    public void transitionTo(MissionState nextState, long tick) {
        transitionTo(nextState, tick, "STATE_TRANSITION");
    }

    public void transitionTo(MissionState nextState, long tick, String reason) {
        if (nextState == null || tick < lastUpdateTick) {
            throw new IllegalArgumentException("Invalid mission transition");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Transition reason is required");
        }
        if (nextState == state) {
            return;
        }
        if (!state.canTransitionTo(nextState)) {
            throw new IllegalStateException("Invalid mission transition "
                    + state + " -> " + nextState + " for " + missionId);
        }
        MissionState previousState = state;
        ControlOwner previousOwner = controlOwner;
        state = nextState;
        lastUpdateTick = tick;
        queued = false;
        phase = phaseFor(nextState);
        if ((previousOwner == ControlOwner.MANUAL || previousOwner == ControlOwner.SUPERVISED_OPERATOR)
                && ownerFor(nextState) == ControlOwner.MISSION_AI) {
            controlOwner = previousOwner;
        } else {
            controlOwner = ownerFor(nextState);
        }
        executionStage = MissionExecutionStage.fromState(nextState, missionType);
        lastTransitionReason = reason.trim();
        validateStateConsistency();
        if (isTerminalState(nextState)) {
            MissionMovementAuthority.releaseControl(aircraftId, missionId);
            RdpUavDebug.event("Lifecycle", "LEASE_RELEASED",
                    "mission", missionId,
                    "aircraft", aircraftId,
                    "tick", tick,
                    "reason", lastTransitionReason,
                    "state", nextState,
                    "controlOwner", controlOwner);
        }
        RdpUavDebug.event("MissionLifecycle", "MISSION_STATE_TRANSITION",
                "mission", missionId,
                "aircraft", aircraftId,
                "from", previousState,
                "to", nextState,
                "phase", phase,
                "controlOwner", controlOwner,
                "tick", tick,
                "target", targetId,
                "reason", lastTransitionReason);
    }

    public void validateStateConsistency() {
        if (state == null) {
            throw new IllegalStateException("Mission state is required for " + missionId);
        }
        MissionPhase expectedPhase = phaseFor(state);
        if (phase != expectedPhase) {
            throw new IllegalStateException("Mission phase does not match state for "
                    + missionId + ": " + phase + " != " + expectedPhase + " for " + state);
        }
        if (!isTerminal() && (controlOwner == ControlOwner.MANUAL
                || controlOwner == ControlOwner.SUPERVISED_OPERATOR)) {
            return;
        }
        ControlOwner expectedOwner = ownerFor(state);
        if (controlOwner != expectedOwner) {
            throw new IllegalStateException("Mission control owner does not match state for "
                    + missionId + ": " + controlOwner + " != " + expectedOwner + " for " + state);
        }
    }

    private static boolean isTerminalState(MissionState value) {
        return value == MissionState.COMPLETE || value == MissionState.ABORTED
                || value == MissionState.FAILED
                || value == MissionState.KAMIKADZE_DETONATED
                || value == MissionState.AIRCRAFT_LOST;
    }

    public boolean isTerminal() {
        return isTerminalState(state);
    }
    public boolean isTimedOut(long tick, long timeoutTicks) {
        if (tick < createdTick || timeoutTicks < 1L) return false;
        return tick - createdTick >= timeoutTicks;
    }
    public boolean isDestructiveAuthorized() { return destructiveAuthorized; }
    public MissionPhase getPhase() { return phase; }
    public ControlOwner getControlOwner() { return controlOwner; }

    public long getTargetLossReacquireWindowTicks() {
        return TARGET_LOSS_REACQUIRE_TICKS;
    }

    public void setControlOwner(ControlOwner owner, long tick) {
        if (owner != ControlOwner.MANUAL && owner != ControlOwner.SUPERVISED_OPERATOR)
            throw new IllegalArgumentException("Only operator control overrides may be set directly");
        if (tick < lastUpdateTick) {
            throw new IllegalArgumentException("Control owner tick must not move backwards");
        }
        if (isTerminal() || ownerFor(state) != ControlOwner.MISSION_AI) {
            throw new IllegalStateException("Operator control cannot override this mission state");
        }
        this.controlOwner = owner;
        this.lastUpdateTick = tick;
    }

    public void enterManualControl(long tick) {
        setControlOwner(ControlOwner.MANUAL, tick);
    }

    public void exitManualControl(long tick) {
        if (controlOwner == ControlOwner.MANUAL) {
            restoreMissionControl(tick);
        }
    }

    public void enterSupervisedOperatorMode(long tick) {
        setControlOwner(ControlOwner.SUPERVISED_OPERATOR, tick);
    }

    public void exitSupervisedOperatorMode(long tick) {
        if (controlOwner == ControlOwner.SUPERVISED_OPERATOR) {
            restoreMissionControl(tick);
        }
    }

    private void restoreMissionControl(long tick) {
        if (tick < lastUpdateTick) {
            throw new IllegalArgumentException("Control owner tick must not move backwards");
        }
        controlOwner = ownerFor(state);
        lastUpdateTick = tick;
    }

    public void authorizeDestructiveAction() { destructiveAuthorized = true; }
    public void setDestructiveAuthorized(boolean value) { destructiveAuthorized = value; }
    public boolean isKamikazePayloadLaunched() { return kamikazePayloadLaunched; }
    public void markKamikazePayloadLaunched(long tick) {
        if (tick < lastUpdateTick) {
            throw new IllegalArgumentException("Payload launch tick is before mission activity");
        }
        if (kamikazePayloadLaunched) return;
        kamikazePayloadLaunched = true;
        lastUpdateTick = tick;
        lastTransitionReason = "KAMIKAZE_PAYLOAD_LAUNCH_ACCEPTED";
    }
    public void restoreKamikazePayloadLaunched(boolean value) {
        kamikazePayloadLaunched = value;
    }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }
    public String getGuidedWeapon() { return guidedWeapon; }
    public TargetPolicy.Snapshot getTargetPolicy() { return targetPolicy; }
    public void setTargetPolicy(TargetPolicy.Snapshot value) {
        if (value == null) throw new IllegalArgumentException("Target policy is required");
        targetPolicy = value;
    }
    public void setGuidedWeapon(String guidedWeapon) {
        this.guidedWeapon = guidedWeapon == null || guidedWeapon.trim().isEmpty()
                ? null : guidedWeapon.trim();
    }

    public void lockTarget(UUID targetId, long tick) {
        if (targetId == null) {
            throw new IllegalArgumentException("Target UUID is required");
        }
        if (state == MissionState.KAMIKADZE_TARGET && this.targetId != null
                && !this.targetId.equals(targetId)) {
            throw new IllegalStateException("Terminal target cannot be replaced");
        }
        if (this.targetId != null && !this.targetId.equals(targetId)
                && controlOwner == ControlOwner.SUPERVISED_OPERATOR) {
            throw new IllegalStateException("Operator-assisted supervision must keep the active target lock");
        }
        this.targetId = targetId;
        this.targetLock = new TargetLock(targetId, "rdpuav:unknown", dimension,
                0.0D, 0.0D, 0.0D, tick, ownerId, missionId);
        this.lastTargetId = targetId;
        this.lastTargetTick = tick;
        transitionTo(MissionState.TARGET_LOCKED, tick);
    }

    public void lockPoint(long tick) {
        if (state == MissionState.KAMIKADZE_TARGET && targetId != null) {
            throw new IllegalStateException("Terminal target cannot be replaced");
        }
        targetId = null;
        targetLock = null;
        transitionTo(MissionState.TARGET_LOCKED, tick);
    }

    public void applyOperatorEntityLock(UUID targetId, long tick) {
        if (targetId == null) throw new IllegalArgumentException("Target UUID is required");
        if (state == MissionState.KAMIKADZE_TARGET
                && !targetId.equals(this.targetId)) {
            throw new IllegalArgumentException("Terminal target cannot be replaced");
        }
        if (this.targetId != null && !this.targetId.equals(targetId)
                && controlOwner == ControlOwner.SUPERVISED_OPERATOR) {
            throw new IllegalStateException("Operator-assisted supervision must keep the active target lock");
        }
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
        // The operator lock is authoritative in every non-terminal phase,
        // including reconnaissance/search. Do not wait for ATTACK_TARGET or
        // APPROACHING: those states are downstream consequences of retaining
        // this identity, not prerequisites for it.
        this.targetId = targetId;
        if (targetLock == null || !targetLock.getEntityId().equals(targetId)) {
            targetLock = new TargetLock(targetId, "rdpuav:unknown", dimension,
                    0.0D, 0.0D, 0.0D, tick, ownerId, missionId);
        }
        this.lastTargetId = targetId;
        this.lastTargetTick = tick;
        this.lastUpdateTick = tick;
        if (state == MissionState.TARGET_ACQUIRED) {
            transitionTo(MissionState.TARGET_LOCKED, tick);
        }
    }

    private static MissionPhase phaseFor(MissionState value) {
        if (value == null) return MissionPhase.FAILED;
        switch (value) {
            case PLANNED:
            case WAITING_FOR_TARGET:
            case REPLAN:
                return MissionPhase.PLANNING;
            case DISPATCHED:
            case TAXI:
            case TAKEOFF:
                return MissionPhase.TAKEOFF;
            case CRUISE:
                return MissionPhase.TRANSIT;
            case SEARCHING:
            case TARGET_ACQUIRED:
            case TARGET_LOCKED:
            case LOST:
                return MissionPhase.SEARCH;
            case APPROACHING:
            case ON_TARGET:
            case ORBITING:
                return MissionPhase.APPROACH;
            case ATTACK_TARGET:
                return MissionPhase.ATTACK;
            case KAMIKADZE_TARGET:
                return MissionPhase.TERMINAL_GUIDANCE;
            case KAMIKADZE_DETONATED:
            case LAUNCH_ACCEPTED:
            case PROJECTILE_TRACKED:
            case IMPACT_OBSERVED:
            case EFFECT_CONFIRMED:
            case OUTCOME_UNKNOWN:
            case DIRECT_LAUNCH_COMPLETE:
            case REPORTING:
            case SCANNING:
            case DEEP_SCANNING:
                return MissionPhase.PAYLOAD_EXECUTION;
            case RETURNING:
            case LANDING:
            case PARKING:
            case RECOVERING:
            case HOLDING:
            case WAITING_FOR_WORLD:
            case WAITING_FOR_FUEL:
            case EXECUTION_STALLED:
                return MissionPhase.RECOVERY;
            case COMPLETE:
                return MissionPhase.COMPLETE;
            case ABORTED:
            case FAILED:
            default:
                return MissionPhase.FAILED;
        }
    }

    private static ControlOwner ownerFor(MissionState value) {
        if (value == MissionState.KAMIKADZE_TARGET) {
            return ControlOwner.TERMINAL_GUIDANCE;
        }
        if (value == MissionState.WAITING_FOR_WORLD
                || value == MissionState.WAITING_FOR_FUEL
                || value == MissionState.EXECUTION_STALLED
                || value == MissionState.REPLAN
                || value == MissionState.RECOVERING
                || value == MissionState.RETURNING
                || value == MissionState.LANDING
                || value == MissionState.PARKING
                || value == MissionState.HOLDING) {
            return ControlOwner.RECOVERY;
        }
        if (value == MissionState.COMPLETE
                || value == MissionState.ABORTED
                || value == MissionState.FAILED
                || value == MissionState.KAMIKADZE_DETONATED
                || value == MissionState.AIRCRAFT_LOST) {
            return ControlOwner.SAFETY;
        }
        return ControlOwner.MISSION_AI;
    }

    public void applyOperatorAreaLock(double x, double y, double z, long tick) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Area lock coordinates must be finite");
        }
        if (state == MissionState.KAMIKADZE_TARGET && targetId != null) {
            throw new IllegalStateException("Terminal target cannot be replaced");
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
        if (state == MissionState.KAMIKADZE_TARGET
                && this.targetId != null
                && !this.targetId.equals(targetId)) {
            throw new IllegalStateException("Terminal target cannot be replaced");
        }
        if (controlOwner == ControlOwner.SUPERVISED_OPERATOR && this.targetId != null
                && !this.targetId.equals(targetId)) {
            throw new IllegalStateException("Operator-assisted supervision must preserve the current target lock");
        }
        this.targetId = targetId;
        if (targetLock == null || !targetLock.getEntityId().equals(targetId)) {
            targetLock = new TargetLock(targetId, "rdpuav:unknown", dimension,
                    0.0D, 0.0D, 0.0D, tick, ownerId, missionId);
        }
        this.lastTargetId = targetId;
        this.lastTargetTick = tick;
        this.lastUpdateTick = tick;
    }

    public void clearTarget(String reason, long tick) {
        if (reason == null || reason.trim().isEmpty() || tick < lastUpdateTick) {
            throw new IllegalArgumentException("Target clear reason and tick are required");
        }
        if (state == MissionState.KAMIKADZE_TARGET && targetId != null) {
            throw new IllegalStateException("Terminal target cannot be cleared");
        }
        if (controlOwner == ControlOwner.SUPERVISED_OPERATOR && targetId != null) {
            throw new IllegalStateException("Operator-assisted supervision must retain the current target lock");
        }
        if (targetId != null) lastTargetId = targetId;
        targetId = null;
        targetLock = null;
        lastTargetReason = reason;
        lastTargetTick = tick;
        lastUpdateTick = tick;
    }

    public void recordProgress(long tick, double previousRouteProgress,
                              double currentRouteProgress,
                              double previousDistanceToTarget,
                              double currentDistanceToTarget,
                              double previousDistanceToWaypoint,
                              double currentDistanceToWaypoint) {
        recordProgress(tick, previousRouteProgress, currentRouteProgress,
                previousDistanceToTarget, currentDistanceToTarget,
                previousDistanceToWaypoint, currentDistanceToWaypoint, true);
    }

    public void recordProgress(long tick, double previousRouteProgress,
                              double currentRouteProgress,
                              double previousDistanceToTarget,
                              double currentDistanceToTarget,
                              double previousDistanceToWaypoint,
                              double currentDistanceToWaypoint,
                              boolean progressExpected) {
        if (tick < lastUpdateTick) {
            throw new IllegalArgumentException("Invalid route progress update");
        }
        double previousRoute = Double.isFinite(previousRouteProgress) ? previousRouteProgress : 0.0D;
        double currentRoute = Double.isFinite(currentRouteProgress) ? currentRouteProgress : previousRoute;
        RouteProgressTracker.Snapshot snapshot = RouteProgressTracker.evaluate(
                tick,
                previousRoute,
                currentRoute,
                previousDistanceToTarget,
                currentDistanceToTarget,
                previousDistanceToWaypoint,
                currentDistanceToWaypoint,
                lastMeaningfulProgressTick < 0L ? createdTick : lastMeaningfulProgressTick,
                PROGRESS_DISTANCE_THRESHOLD,
                200L);
        this.routeProgress = snapshot.getRouteProgress();
        this.lastMeaningfulProgressTick = progressExpected
                ? snapshot.getLastMeaningfulProgressTick() : tick;
        if (progressExpected && snapshot.isMeasuredDistanceProgress()) {
            clearRecovery();
        }
        MissionProgressPolicy.Decision progressDecision = MissionProgressPolicy.evaluate(
                state,
                targetId != null,
                snapshot.isMeaningfulProgress(),
                progressExpected,
                tick,
                this.lastMeaningfulProgressTick,
                200L);
        if (progressDecision.getAction() == MissionProgressPolicy.Action.REACQUIRE
                && state.canTransitionTo(MissionState.LOST)) {
            transitionTo(MissionState.LOST, tick, progressDecision.getReason());
        } else if (progressDecision.getAction() == MissionProgressPolicy.Action.REPLAN
                && state.canTransitionTo(MissionState.REPLAN)) {
            defer(progressDecision.getReason(), MissionState.REPLAN, tick);
        }
        this.lastUpdateTick = tick;
    }

    /**
     * Records sequential distance samples from the server-observed aircraft
     * and its current route/target. Route assignment alone is not progress.
     */
    public boolean recordObservedProgress(long tick, double previousRouteProgress,
                                          double currentRouteProgress,
                                          double currentDistanceToTarget,
                                          double currentDistanceToWaypoint,
                                          boolean progressExpected) {
        MissionState previousState = state;
        int previousRecoveryAttempts = recoveryAttempts;
        long previousMeaningfulProgressTick = lastMeaningfulProgressTick;
        double previousProgress = routeProgress;
        double previousTargetDistance = bestMeasuredTargetDistance;
        boolean routeNodeAdvanced = Double.isFinite(previousRouteProgress)
                && Double.isFinite(currentRouteProgress)
                && currentRouteProgress > previousRouteProgress;
        double previousWaypointDistance = routeNodeAdvanced
                ? Double.NaN : bestMeasuredWaypointDistance;
        recordProgress(tick, previousRouteProgress, currentRouteProgress,
                previousTargetDistance, currentDistanceToTarget,
                previousWaypointDistance, currentDistanceToWaypoint,
                progressExpected);
        bestMeasuredTargetDistance = nextProgressBaseline(
                previousTargetDistance, currentDistanceToTarget);
        bestMeasuredWaypointDistance = nextProgressBaseline(
                previousWaypointDistance, currentDistanceToWaypoint);
        return previousState != state
                || previousRecoveryAttempts != recoveryAttempts
                || Double.compare(previousProgress, routeProgress) != 0
                || (progressExpected
                && previousMeaningfulProgressTick != lastMeaningfulProgressTick);
    }

    private static double nextProgressBaseline(double previousBest, double current) {
        if (!Double.isFinite(current)) return Double.NaN;
        if (!Double.isFinite(previousBest)
                || previousBest - current > PROGRESS_DISTANCE_THRESHOLD) return current;
        return previousBest;
    }

    public boolean isProgressStalled(long tick, long stallWindowTicks) {
        if (tick < lastMeaningfulProgressTick || stallWindowTicks < 1L) {
            return false;
        }
        return tick - lastMeaningfulProgressTick >= stallWindowTicks;
    }

    /**
     * Starts a fresh no-progress window after the server accepts a new route.
     * Dispatching a route is not counted as movement; it only prevents the
     * previous route's expired stall timer from immediately dispatching the
     * replacement route again on the next tick.
     */
    public void beginRouteProgressWindow(long tick) {
        if (tick < lastUpdateTick) {
            throw new IllegalArgumentException("Route progress baseline is before mission activity");
        }
        lastMeaningfulProgressTick = tick;
        routeProgress = 0.0D;
        bestMeasuredTargetDistance = Double.NaN;
        bestMeasuredWaypointDistance = Double.NaN;
        lastUpdateTick = tick;
    }

    public void restoreProgressState(double restoredRouteProgress,
                                     long restoredLastMeaningfulProgressTick) {
        if (!Double.isFinite(restoredRouteProgress) || restoredRouteProgress < 0.0D) {
            throw new IllegalArgumentException("Invalid persisted route progress");
        }
        routeProgress = restoredRouteProgress;
        lastMeaningfulProgressTick = Math.max(createdTick,
                Math.min(lastUpdateTick, restoredLastMeaningfulProgressTick));
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
        transitionTo(MissionState.FAILED, tick, reason);
    }

    public void abort(String reason, long tick) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Abort reason is required");
        }
        if (state == MissionState.ABORTED) return;
        if (state == MissionState.KAMIKADZE_TARGET) {
            throw new IllegalStateException("ABORT_IMPOSSIBLE_AFTER_TERMINAL_COMMIT");
        }
        if (isTerminal()) {
            throw new IllegalStateException("Cannot abort terminal mission " + missionId
                    + " in state " + state);
        }
        transitionTo(MissionState.ABORTED, tick, reason);
    }

    public void complete(long tick) {
        if (state == MissionState.COMPLETE) return;
        if (isTerminal()) {
            throw new IllegalStateException("Cannot complete terminal mission " + missionId
                    + " in state " + state);
        }
        transitionTo(MissionState.COMPLETE, tick, "MISSION_COMPLETE");
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
        transitionTo(recoveryState, tick, reason.trim());
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

    /**
     * Records a server-observed in-game effect after the native payload launch.
     * Launch acceptance alone must never call this operation.
     */
    public void confirmKamikazeEffect(long tick) {
        if (state == MissionState.KAMIKADZE_DETONATED) return;
        if (state != MissionState.KAMIKADZE_TARGET || !kamikazePayloadLaunched) {
            throw new IllegalStateException("Kamikaze effect requires observed payload launch");
        }
        transitionTo(MissionState.KAMIKADZE_DETONATED, tick, "KAMIKAZE_DETONATED");
    }

    /** @deprecated Use {@link #confirmKamikazeEffect(long)} at the native effect boundary. */
    @Deprecated
    public void markKamikazeDetonated(long tick) {
        confirmKamikazeEffect(tick);
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public void setDimension(int dimension) { this.dimension = dimension; }
    public void setSquareArea(double width, double depth) {
        if (missionType != MissionType.SQUARE_STRIKE || !Double.isFinite(width)
                || !Double.isFinite(depth) || width <= 0.0D || depth <= 0.0D) {
            throw new IllegalArgumentException("Invalid square-strike area");
        }
        squareWidth = width;
        squareDepth = depth;
    }
    public boolean hasSquareArea() {
        return missionType == MissionType.SQUARE_STRIKE && squareWidth > 0.0D && squareDepth > 0.0D;
    }
    public double getSquareWidth() { return squareWidth; }
    public double getSquareDepth() { return squareDepth; }
    public void setQueued(boolean queued) { this.queued = queued; }
    public void setRecoveryPolicy(String recoveryPolicy) {
        if (recoveryPolicy == null || recoveryPolicy.trim().isEmpty()) {
            throw new IllegalArgumentException("Recovery policy is required");
        }
        this.recoveryPolicy = recoveryPolicy;
    }
    public void setPatrolRoute(List<MissionWaypoint> route) {
        if (missionType != MissionType.PATROL) {
            throw new IllegalStateException("Only patrol missions accept route waypoints");
        }
        PatrolRouteCodec.validate(route);
        patrolRoute = Collections.unmodifiableList(new ArrayList<>(route));
    }
    public List<MissionWaypoint> getPatrolRoute() { return patrolRoute; }

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
        mission.phase = phaseFor(state);
        mission.controlOwner = ownerFor(state);
        mission.executionStage = MissionExecutionStage.fromState(state, missionType);
        mission.targetId = targetId;
        mission.lastUpdateTick = lastUpdateTick;
        mission.failureReason = failureReason;
        mission.reportId = reportId;
        mission.lastTransitionReason = failureReason == null
                ? "RESTORED_" + state.name() : failureReason;
        mission.validateStateConsistency();
        return mission;
    }

    public void restoreExecutionMetadata(MissionExecutionStage stage,
                                         UUID parentMissionId, UUID childMissionId) {
        if (stage == null || missionId.equals(parentMissionId)
                || missionId.equals(childMissionId)
                || (parentMissionId != null && parentMissionId.equals(childMissionId))) {
            throw new IllegalArgumentException("Invalid linked mission metadata");
        }
        this.executionStage = stage;
        this.parentMissionId = parentMissionId;
        this.childMissionId = childMissionId;
    }

    public void linkChildMission(RDPMission child) {
        if (child == null || child == this || !aircraftId.equals(child.aircraftId)
                || !ownerId.equals(child.ownerId)
                || (child.parentMissionId != null && !missionId.equals(child.parentMissionId))) {
            throw new IllegalArgumentException("Linked child must retain owner and aircraft");
        }
        childMissionId = child.missionId;
        child.parentMissionId = missionId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RDPMission)) return false;
        RDPMission other = (RDPMission) obj;
        return missionId.equals(other.missionId);
    }

    @Override
    public int hashCode() {
        return missionId.hashCode();
    }

    public UUID getMissionId() { return missionId; }
    public UUID getSwarmId() { return swarmId; }
    public void setSwarmId(UUID value) { swarmId = value; }
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
    public double getRouteProgress() { return routeProgress; }
    public long getLastMeaningfulProgressTick() { return lastMeaningfulProgressTick; }
    public MissionState getState() { return state; }
    public UUID getTargetId() { return targetId; }
    public TargetLock getTargetLock() { return targetLock; }

    public void updateTargetLock(String registryId, int targetDimension,
                                 double x, double y, double z, long tick) {
        if (tick < lastUpdateTick) {
            throw new IllegalArgumentException("Target observation is before mission activity");
        }
        if (targetLock == null || !targetLock.getEntityId().equals(targetId)) {
            if (targetId == null) return;
            targetLock = new TargetLock(targetId,
                    registryId == null || registryId.trim().isEmpty()
                            ? "rdpuav:unknown" : registryId,
                    targetDimension, x, y, z, tick, ownerId, missionId);
            lastTargetId = targetId;
            lastTargetTick = tick;
            return;
        }
        targetLock.updateIdentity(registryId == null || registryId.trim().isEmpty()
                ? targetLock.getRegistryId() : registryId, targetDimension);
        targetLock.update(x, y, z, tick);
        lastTargetId = targetId;
        lastTargetTick = tick;
    }
    public UUID getLastTargetId() { return lastTargetId; }
    public String getLastTargetReason() { return lastTargetReason; }
    public long getLastTargetTick() { return lastTargetTick; }
    public void restoreLastTargetTick(long tick) {
        if (tick < -1L) throw new IllegalArgumentException("Invalid target observation tick");
        lastTargetTick = tick;
    }
    public String getFailureReason() { return failureReason; }
    public String getLastTransitionReason() { return lastTransitionReason; }
    public void restoreTransitionReason(String reason) {
        lastTransitionReason = reason == null || reason.trim().isEmpty()
                ? null : reason.trim();
    }
    public String getReportId() { return reportId; }
    public int getDimension() { return dimension; }
    public boolean isQueued() { return queued; }
    public String getRecoveryPolicy() { return recoveryPolicy; }
    public int getRecoveryAttempts() { return recoveryAttempts; }
    public String getRecoveryReason() { return recoveryReason; }
    public MissionExecutionStage getExecutionStage() { return executionStage; }
    public UUID getParentMissionId() { return parentMissionId; }
    public UUID getChildMissionId() { return childMissionId; }
}
