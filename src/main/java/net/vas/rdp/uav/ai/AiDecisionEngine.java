package net.vas.rdp.uav.ai;

import java.util.List;

public final class AiDecisionEngine {
    public AiDecision recommend(AiTaskRequest request, List<AiResource> resources) {
        if (request == null || resources == null) {
            throw new IllegalArgumentException("AI request and resources are required");
        }
        AiResource best = null;
        int bestScore = Integer.MIN_VALUE;
        for (AiResource resource : resources) {
            if (!resource.getAircraft().availableFor(request.getOperatorId())
                    || (request.isAttack() && !resource.isAttackCapable())) {
                continue;
            }
            int score = 1000 - (int) Math.min(900, resource.getDistance());
            score += request.getPriority().getWeight() * 100;
            if (resource.getAircraft().getStatus().name().equals("PARKED")) score += 10;
            if (score > bestScore) {
                best = resource;
                bestScore = score;
            }
        }
        if (best == null) {
            return new AiDecision(AiDecision.Kind.REJECT, request.getTaskId(), null,
                    request.isAttack() ? "No compatible attack-capable UAV is available"
                            : "No compatible UAV is available", 0);
        }
        boolean approval = request.getAutonomy() == AutonomyLevel.MANUAL
                || request.getAutonomy() == AutonomyLevel.AI_ASSISTED;
        return new AiDecision(approval ? AiDecision.Kind.REQUEST_APPROVAL : AiDecision.Kind.RECOMMEND,
                request.getTaskId(), best.getAircraft().getAircraftId(),
                "Closest compatible available UAV with the required mission capability",
                bestScore);
    }
}
