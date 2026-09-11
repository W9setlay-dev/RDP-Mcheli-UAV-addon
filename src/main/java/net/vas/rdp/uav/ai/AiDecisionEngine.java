package net.vas.rdp.uav.ai;

import java.util.List;
import net.vas.rdp.uav.sensor.SensorFusion;

public final class AiDecisionEngine {
    public AiDecision recommend(AiTaskRequest request, List<AiResource> resources) {
        return recommend(request, resources, null);
    }

    public AiDecision recommend(AiTaskRequest request, List<AiResource> resources,
                                SensorFusion.FusionResult environment) {
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
                            : "No compatible UAV is available", 0, FlightIntent.HOLD, 0.0D);
        }
        boolean approval = request.getAutonomy() == AutonomyLevel.MANUAL
                || request.getAutonomy() == AutonomyLevel.AI_ASSISTED;
        FlightIntent intent = request.isAttack() ? FlightIntent.APPROACH : FlightIntent.OBSERVE;
        double confidence = environment == null ? 0.5D : environment.getConfidence();
        String reason = environment == null
                ? "Closest compatible available UAV with the required mission capability"
                : environment.getExplanation() + "; " + (request.isAttack()
                ? "attack intent awaits target validation"
                : "observation intent selected");
        return new AiDecision(approval ? AiDecision.Kind.REQUEST_APPROVAL : AiDecision.Kind.RECOMMEND,
                request.getTaskId(), best.getAircraft().getAircraftId(),
                reason, bestScore, intent, confidence);
    }
}
