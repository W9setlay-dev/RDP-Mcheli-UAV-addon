package net.vas.rdp.uav;

import net.vas.rdp.uav.ai.AiDecision;
import net.vas.rdp.uav.ai.AiDecisionEngine;
import net.vas.rdp.uav.ai.AiResource;
import net.vas.rdp.uav.ai.AiTaskRequest;
import net.vas.rdp.uav.ai.AutonomyLevel;
import net.vas.rdp.uav.mission.AircraftRecord;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AiTaskingTest {
    @Test
    public void assistedAttackRequestsApprovalAndSelectsClosestCompatibleUav() {
        UUID operator = UUID.randomUUID();
        AiTaskRequest request = new AiTaskRequest(UUID.randomUUID(), operator,
                MissionType.STRIKE, TargetSpec.point(10, 80, 10, TargetPriority.HIGH),
                TargetPriority.HIGH, true, AutonomyLevel.AI_ASSISTED);
        AiResource far = resource(operator, 100, true);
        AiResource near = resource(operator, 20, true);
        AiDecision decision = new AiDecisionEngine().recommend(request, Arrays.asList(far, near));
        assertEquals(AiDecision.Kind.REQUEST_APPROVAL, decision.getKind());
        assertEquals(near.getAircraft().getAircraftId(), decision.getAircraftId());
        assertNotNull(decision.getReason());
    }

    @Test
    public void attackRejectsResourcesWithoutAttackCapability() {
        UUID operator = UUID.randomUUID();
        AiTaskRequest request = new AiTaskRequest(UUID.randomUUID(), operator,
                MissionType.STRIKE, TargetSpec.point(0, 80, 0, TargetPriority.NORMAL),
                TargetPriority.NORMAL, true, AutonomyLevel.AUTONOMOUS);
        AiDecision decision = new AiDecisionEngine().recommend(request,
                Collections.singletonList(resource(operator, 1, false)));
        assertEquals(AiDecision.Kind.REJECT, decision.getKind());
        assertNull(decision.getAircraftId());
    }

    @Test
    public void autonomousObservationCanBeRecommendedWithoutApproval() {
        UUID operator = UUID.randomUUID();
        AiTaskRequest request = new AiTaskRequest(UUID.randomUUID(), operator,
                MissionType.OBSERVATION, TargetSpec.point(0, 80, 0, TargetPriority.LOW),
                TargetPriority.LOW, false, AutonomyLevel.AUTONOMOUS);
        AiDecision decision = new AiDecisionEngine().recommend(request,
                Collections.singletonList(resource(operator, 5, false)));
        assertEquals(AiDecision.Kind.RECOMMEND, decision.getKind());
    }

    private static AiResource resource(UUID owner, double distance, boolean attackCapable) {
        return new AiResource(new AircraftRecord(UUID.randomUUID(), owner, 0,
                "switchblade", true, true), distance, attackCapable);
    }
}
