package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetLock;

import java.util.UUID;

/**
 * Immutable read-only projection for GUI, HUD, and network consumers.
 *
 * <p>Consumers must not use this object to issue mission commands. The
 * persisted mission remains the sole authority.</p>
 */
public final class MissionTelemetry {
    private final UUID missionId;
    private final UUID ownerId;
    private final UUID aircraftId;
    private final String aircraftType;
    private final MissionType missionType;
    private final MissionState state;
    private final MissionPhase phase;
    private final ControlOwner controlOwner;
    private final UUID targetId;
    private final double targetConfidence;
    private final long createdTick;
    private final long lastUpdateTick;
    private final long lastTargetTick;
    private final String failureReason;
    private final String recoveryReason;
    private final String reportId;
    private final int recoveryAttempts;
    private final boolean attack;
    private final boolean queued;

    private MissionTelemetry(RDPMission mission) {
        missionId = mission.getMissionId();
        ownerId = mission.getOwnerId();
        aircraftId = mission.getAircraftId();
        aircraftType = mission.getAircraftType();
        missionType = mission.getMissionType();
        state = mission.getState();
        phase = mission.getPhase();
        controlOwner = mission.getControlOwner();
        targetId = mission.getTargetId();
        TargetLock lock = mission.getTargetLock();
        targetConfidence = lock == null ? 0.0D : lock.getConfidence();
        createdTick = mission.getCreatedTick();
        lastUpdateTick = mission.getLastUpdateTick();
        lastTargetTick = mission.getLastTargetTick();
        failureReason = mission.getFailureReason();
        recoveryReason = mission.getRecoveryReason();
        reportId = mission.getReportId();
        recoveryAttempts = mission.getRecoveryAttempts();
        attack = mission.isAttack();
        queued = mission.isQueued();
    }

    public static MissionTelemetry from(RDPMission mission) {
        if (mission == null) throw new IllegalArgumentException("Mission is required");
        return new MissionTelemetry(mission);
    }

    public UUID getMissionId() { return missionId; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getAircraftId() { return aircraftId; }
    public String getAircraftType() { return aircraftType; }
    public MissionType getMissionType() { return missionType; }
    public MissionState getState() { return state; }
    public MissionPhase getPhase() { return phase; }
    public ControlOwner getControlOwner() { return controlOwner; }
    public UUID getTargetId() { return targetId; }
    public double getTargetConfidence() { return targetConfidence; }
    public long getCreatedTick() { return createdTick; }
    public long getLastUpdateTick() { return lastUpdateTick; }
    public long getLastTargetTick() { return lastTargetTick; }
    public String getFailureReason() { return failureReason; }
    public String getRecoveryReason() { return recoveryReason; }
    public String getReportId() { return reportId; }
    public int getRecoveryAttempts() { return recoveryAttempts; }
    public boolean isAttack() { return attack; }
    public boolean isQueued() { return queued; }
}
