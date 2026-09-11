package net.vas.rdp.uav.ai;

import net.minecraft.world.World;
import net.vas.rdp.uav.mission.AircraftRecord;
import net.vas.rdp.uav.mission.MissionManager;
import net.vas.rdp.uav.mission.RDPMission;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.vas.rdp.uav.sensor.SensorFusion;

public final class AiTaskingService {
    private final MissionManager missions;
    private final AiDecisionEngine decisions = new AiDecisionEngine();
    private final Map<UUID, AiTaskRequest> pending = new HashMap<UUID, AiTaskRequest>();

    public AiTaskingService(MissionManager missions) {
        if (missions == null) throw new IllegalArgumentException("Mission manager is required");
        this.missions = missions;
    }

    public AiDecision assess(AiTaskRequest request, List<AiResource> resources) {
        return assess(request, resources, null);
    }

    public AiDecision assess(AiTaskRequest request, List<AiResource> resources,
                             SensorFusion.FusionResult environment) {
        AiDecision decision = decisions.recommend(request, resources, environment);
        if (decision.getAircraftId() != null
                && decision.getKind() == AiDecision.Kind.REQUEST_APPROVAL) {
            pending.put(request.getTaskId(), request);
        }
        return decision;
    }

    public RDPMission approve(World world, AiTaskRequest request, UUID aircraftId, long tick) {
        if (world == null || request == null || aircraftId == null) {
            throw new IllegalArgumentException("Approval fields are required");
        }
        AiTaskRequest expected = pending.remove(request.getTaskId());
        if (expected == null || !expected.getOperatorId().equals(request.getOperatorId())) {
            throw new IllegalStateException("AI_APPROVAL_NOT_PENDING");
        }
        return createMission(world, request, aircraftId, tick);
    }

    public RDPMission executeAutonomous(World world, AiTaskRequest request,
                                        AiDecision decision, long tick) {
        if (decision == null || decision.getKind() != AiDecision.Kind.RECOMMEND
                || request == null || request.getAutonomy() == AutonomyLevel.MANUAL
                || request.getAutonomy() == AutonomyLevel.AI_ASSISTED) {
            throw new IllegalStateException("AI_OPERATOR_APPROVAL_REQUIRED");
        }
        return createMission(world, request, decision.getAircraftId(), tick);
    }

    private RDPMission createMission(World world, AiTaskRequest request,
                                      UUID aircraftId, long tick) {
        if (world == null || request == null || aircraftId == null) {
            throw new IllegalArgumentException("Mission execution fields are required");
        }
        RDPMission mission = new RDPMission(UUID.randomUUID(), request.getOperatorId(), aircraftId,
                aircraftType(aircraftId), request.getMissionType(), request.getTarget(),
                request.isAttack(), false, request.getMissionType().name().equals("RECONNAISSANCE"),
                256, 64, tick);
        mission.setDimension(world.provider.getDimension());
        missions.create(world, mission);
        return mission;
    }

    private String aircraftType(UUID aircraftId) {
        AircraftRecord record = missions.findAircraft(aircraftId);
        if (record == null) throw new IllegalStateException("UAV_NOT_REGISTERED");
        return record.getType();
    }
}
