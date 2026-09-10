package net.vas.rdp.uav.ai;

import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;

import java.util.UUID;

public final class AiTaskRequest {
    private final UUID taskId;
    private final UUID operatorId;
    private final MissionType missionType;
    private final TargetSpec target;
    private final TargetPriority priority;
    private final boolean attack;
    private final AutonomyLevel autonomy;

    public AiTaskRequest(UUID taskId, UUID operatorId, MissionType missionType,
                         TargetSpec target, TargetPriority priority, boolean attack,
                         AutonomyLevel autonomy) {
        if (taskId == null || operatorId == null || missionType == null || target == null
                || priority == null || autonomy == null) {
            throw new IllegalArgumentException("AI task fields are required");
        }
        this.taskId = taskId;
        this.operatorId = operatorId;
        this.missionType = missionType;
        this.target = target;
        this.priority = priority;
        this.attack = attack;
        this.autonomy = autonomy;
    }

    public UUID getTaskId() { return taskId; }
    public UUID getOperatorId() { return operatorId; }
    public MissionType getMissionType() { return missionType; }
    public TargetSpec getTarget() { return target; }
    public TargetPriority getPriority() { return priority; }
    public boolean isAttack() { return attack; }
    public AutonomyLevel getAutonomy() { return autonomy; }
}
