package net.vas.rdp.uav;

import com.norwood.mcheli.wingman.mission.MissionNode;
import net.vas.rdp.uav.ai.AiDecision;
import net.vas.rdp.uav.ai.AiDecisionEngine;
import net.vas.rdp.uav.ai.AiResource;
import net.vas.rdp.uav.ai.AiTaskRequest;
import net.vas.rdp.uav.ai.AutonomyLevel;
import net.vas.rdp.uav.ai.FlightIntent;
import net.vas.rdp.uav.flight.SquareStrikePlan;
import net.vas.rdp.uav.mission.AircraftRecord;
import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.target.TargetLock;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSelector;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Contract tests for the operator-facing autonomous mission workflows.
 *
 * <p>These tests deliberately stop at the McHeli/WingMan boundary: node
 * creation and mission state are deterministic here, while actual steering,
 * weapon release, damage, and landing require a live Forge server.</p>
 */
public class AutonomousMissionTest {
    @Test
    public void autonomousAiTaskSelectsAttackUavAndRunsFullMission() {
        UUID operator = UUID.randomUUID();
        UUID aircraftId = UUID.randomUUID();
        AiTaskRequest request = new AiTaskRequest(UUID.randomUUID(), operator,
                MissionType.STRIKE,
                TargetSpec.manual("hostile:target", TargetPriority.CRITICAL),
                TargetPriority.CRITICAL, true, AutonomyLevel.AUTONOMOUS);
        AircraftRecord aircraft = new AircraftRecord(aircraftId, operator, 0,
                "switchblade", true, true);

        AiDecision decision = new AiDecisionEngine().recommend(request,
                Arrays.asList(new AiResource(aircraft, 42.0D, true)));

        assertEquals(AiDecision.Kind.RECOMMEND, decision.getKind());
        assertEquals(aircraftId, decision.getAircraftId());
        assertEquals(FlightIntent.APPROACH, decision.getIntent());
        assertTrue(decision.getConfidence() >= 0.0D);

        RDPMission mission = mission(request, aircraftId);
        assertEquals(MissionState.PLANNED, mission.getState());
        progressToTargetLock(mission, 1, UUID.randomUUID());
        mission.transitionTo(MissionState.ATTACK_TARGET, 9);
        mission.transitionTo(MissionState.ON_TARGET, 10);
        mission.transitionTo(MissionState.SCANNING, 11);
        mission.transitionTo(MissionState.REPORTING, 12);
        mission.transitionTo(MissionState.RETURNING, 13);
        mission.transitionTo(MissionState.LANDING, 14);
        mission.transitionTo(MissionState.PARKING, 15);
        mission.transitionTo(MissionState.COMPLETE, 16);
        assertEquals(MissionState.COMPLETE, mission.getState());
    }

    @Test
    public void aiHelpForPlayerRequiresApprovalAndNeverAutoExecutesAttack() {
        UUID operator = UUID.randomUUID();
        AiTaskRequest request = new AiTaskRequest(UUID.randomUUID(), operator,
                MissionType.STRIKE, TargetSpec.point(20, 90, -4, TargetPriority.HIGH),
                TargetPriority.HIGH, true, AutonomyLevel.AI_ASSISTED);
        AiResource resource = new AiResource(new AircraftRecord(UUID.randomUUID(), operator, 0,
                "switchblade", true, true), 8.0D, true);

        AiDecision decision = new AiDecisionEngine().recommend(request,
                Arrays.asList(resource));

        assertEquals(AiDecision.Kind.REQUEST_APPROVAL, decision.getKind());
        assertEquals(FlightIntent.APPROACH, decision.getIntent());
        assertNotNull(decision.getReason());
        assertFalse(decision.getKind() == AiDecision.Kind.RECOMMEND);
    }

    @Test
    public void wingmanFlightPlanAlternatesFlyAndAttackPasses() {
        SquareStrikePlan plan = new SquareStrikePlan(
                100.0D, 140.0D, -50.0D, 60.0D, 90.0D, 0.0D,
                30.0D, 24.0D, 3);
        List<MissionNode> nodes = plan.createNodes();

        assertEquals(9, nodes.size());
        for (int i = 0; i < nodes.size(); i += 3) {
            assertEquals(MissionNode.Type.FLY_TO, nodes.get(i).type);
            assertEquals(MissionNode.Type.FLY_TO, nodes.get(i + 1).type);
            assertEquals(MissionNode.Type.ATTACK, nodes.get(i + 2).type);
            assertEquals(24.0D, nodes.get(i + 2).attackRadius, 0.0D);
            assertEquals(140.0D, nodes.get(i).y, 0.0D);
            assertTrue(plan.contains(nodes.get(i).x, nodes.get(i).z));
            assertTrue(plan.contains(nodes.get(i + 1).x, nodes.get(i + 1).z));
        }
    }

    @Test
    public void targetLockTracksMovementAndOnlyLockedTargetMayAttack() {
        UUID targetId = UUID.randomUUID();
        TargetLock lock = new TargetLock(targetId, "hostile:target", 0,
                10.0D, 80.0D, 10.0D, 20);
        lock.update(14.0D, 80.0D, 10.0D, 22);
        assertEquals(TargetLock.Validation.VALID, lock.getValidation());
        assertEquals(2.0D, lock.getVelocityX(), 0.0001D);
        assertEquals(18.0D, lock.predictX(24), 0.0001D);

        RDPMission mission = mission(new AiTaskRequest(UUID.randomUUID(), UUID.randomUUID(),
                MissionType.STRIKE, TargetSpec.manual("hostile:target", TargetPriority.HIGH),
                TargetPriority.HIGH, true, AutonomyLevel.AUTONOMOUS), UUID.randomUUID());
        try {
            mission.transitionTo(MissionState.ATTACK_TARGET, 1);
            fail("An attack must not start before target acquisition and lock");
        } catch (IllegalStateException expected) {
            // Expected safety boundary.
        }
        progressToTargetLock(mission, 21, targetId);
        assertEquals(targetId, mission.getTargetId());
        assertEquals(MissionState.TARGET_LOCKED, mission.getState());
        mission.transitionTo(MissionState.ATTACK_TARGET, 27);

        lock.markIdentityMismatch();
        assertEquals(TargetLock.Validation.IDENTITY_MISMATCH, lock.getValidation());
        UUID replacement = UUID.randomUUID();
        mission.reacquireTarget(replacement, 28);
        assertEquals(replacement, mission.getTargetId());
        assertEquals(MissionState.ATTACK_TARGET, mission.getState());
    }

    @Test
    public void targetSelectorDoesNotSubstituteWhenManualTargetIsMissing() {
        TargetSelector<String> selector = new TargetSelector<String>();
        String selected = selector.select(TargetSpec.manual("hostile:target", TargetPriority.HIGH),
                Arrays.asList(candidate("hostile:other", "wrong")));
        assertNull(selected);
    }

    private static RDPMission mission(AiTaskRequest request, UUID aircraftId) {
        return new RDPMission(UUID.randomUUID(), request.getOperatorId(), aircraftId,
                "switchblade", request.getMissionType(), request.getTarget(), request.isAttack(),
                false, false, 256.0D, 64.0D, 0);
    }

    private static void progressToTargetLock(RDPMission mission, long startTick, UUID targetId) {
        mission.transitionTo(MissionState.DISPATCHED, startTick);
        mission.transitionTo(MissionState.TAKEOFF, startTick + 1);
        mission.transitionTo(MissionState.CRUISE, startTick + 2);
        mission.transitionTo(MissionState.SEARCHING, startTick + 3);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, startTick + 4);
        mission.lockTarget(targetId, startTick + 5);
    }

    private static TargetSelector.Candidate<String> candidate(final String id, final String value) {
        return new TargetSelector.Candidate<String>() {
            public String getRegistryId() { return id; }
            public double getDistanceSquared() { return 1.0D; }
            public boolean isValid() { return true; }
            public TargetPriority getPriority() { return TargetPriority.HIGH; }
            public String getValue() { return value; }
        };
    }
}
