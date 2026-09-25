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
import net.vas.rdp.uav.mission.MissionCoordinator;
import net.vas.rdp.uav.mission.MissionSafety;
import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.target.TargetLock;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSelector;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

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
                Collections.singletonList(new AiResource(aircraft, 42.0D, true)));

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
        mission.transitionTo(MissionState.HOLDING, 13);
        mission.transitionTo(MissionState.RETURNING, 14);
        mission.transitionTo(MissionState.LANDING, 15);
        mission.transitionTo(MissionState.PARKING, 16);
        mission.transitionTo(MissionState.COMPLETE, 17);
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
                Collections.singletonList(resource));

        assertEquals(AiDecision.Kind.REQUEST_APPROVAL, decision.getKind());
        assertEquals(FlightIntent.APPROACH, decision.getIntent());
        assertNotNull(decision.getReason());
        assertNotSame(AiDecision.Kind.RECOMMEND, decision.getKind());
    }

    @Test
    public void terminalMissionRejectsLaterNormalTransitionWithoutChangingState() {
        RDPMission mission = mission(new AiTaskRequest(UUID.randomUUID(), UUID.randomUUID(),
                MissionType.RECONNAISSANCE, TargetSpec.point(20, 90, -4, TargetPriority.NORMAL),
                TargetPriority.NORMAL, false, AutonomyLevel.AUTONOMOUS), UUID.randomUUID());
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAKEOFF, 2);
        mission.transitionTo(MissionState.CRUISE, 3);
        mission.transitionTo(MissionState.SEARCHING, 4);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5);
        mission.transitionTo(MissionState.TARGET_LOCKED, 6);
        mission.transitionTo(MissionState.APPROACHING, 7);
        mission.transitionTo(MissionState.ON_TARGET, 8);
        mission.transitionTo(MissionState.SCANNING, 9);
        mission.transitionTo(MissionState.REPORTING, 10);
        mission.transitionTo(MissionState.HOLDING, 11);
        mission.transitionTo(MissionState.RETURNING, 12);
        mission.transitionTo(MissionState.LANDING, 13);
        mission.transitionTo(MissionState.PARKING, 14);
        mission.transitionTo(MissionState.COMPLETE, 15);
        assertTrue(mission.isTerminal());
        mission.transitionTo(MissionState.COMPLETE, 15);
        assertEquals(MissionState.COMPLETE, mission.getState());
    }

    @Test
    public void recoverableConditionsExhaustBeforeFailure() {
        RDPMission mission = mission(new AiTaskRequest(UUID.randomUUID(), UUID.randomUUID(),
                MissionType.RECONNAISSANCE, TargetSpec.point(20, 90, -4, TargetPriority.NORMAL),
                TargetPriority.NORMAL, false, AutonomyLevel.AUTONOMOUS), UUID.randomUUID());

        for (int tick = 1; tick <= 5; tick++) {
            assertTrue(mission.defer("WORLD_UNAVAILABLE",
                    MissionState.WAITING_FOR_WORLD, tick));
            assertEquals(MissionState.WAITING_FOR_WORLD, mission.getState());
        }
        assertFalse(mission.defer("WORLD_UNAVAILABLE",
                MissionState.WAITING_FOR_WORLD, 6));
        assertEquals(MissionState.FAILED, mission.getState());
        assertEquals("WORLD_UNAVAILABLE_RECOVERY_EXHAUSTED",
                mission.getFailureReason());
    }

    @Test
    public void routeProgressTracksRealMovementAndStallsAfterTimeout() {
        RDPMission mission = mission(new AiTaskRequest(UUID.randomUUID(), UUID.randomUUID(),
                MissionType.STRIKE, TargetSpec.manual("hostile:target", TargetPriority.HIGH),
                TargetPriority.HIGH, true, AutonomyLevel.AUTONOMOUS), UUID.randomUUID());
        UUID targetId = UUID.randomUUID();
        mission.transitionTo(MissionState.DISPATCHED, 1L);
        mission.transitionTo(MissionState.TAKEOFF, 2L);
        mission.transitionTo(MissionState.CRUISE, 3L);
        mission.transitionTo(MissionState.SEARCHING, 4L);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5L);
        mission.transitionTo(MissionState.TARGET_LOCKED, 6L);
        mission.reacquireTarget(targetId, 7L);

        mission.recordProgress(100L, 0.0D, 0.4D, 40.0D, 22.0D, 60.0D, 41.0D);
        assertFalse(mission.isProgressStalled(180L, 200L));
        assertEquals(0.4D, mission.getRouteProgress(), 0.0D);
        assertEquals(100L, mission.getLastMeaningfulProgressTick());

        mission.recordProgress(350L, 0.4D, 0.4D, 22.0D, 22.0D, 41.0D, 41.0D);
        assertTrue(mission.isProgressStalled(500L, 200L));
        assertEquals(MissionState.LOST, mission.getState());
    }

    @Test
    public void routeProgressAcceptsUnknownDistancesWithoutMistakingThemForZero() {
        RDPMission mission = mission(new AiTaskRequest(UUID.randomUUID(), UUID.randomUUID(),
                MissionType.RECONNAISSANCE, TargetSpec.point(0.0D, 8.0D, 0.0D, TargetPriority.NORMAL),
                TargetPriority.NORMAL, false, AutonomyLevel.AUTONOMOUS), UUID.randomUUID());
        mission.transitionTo(MissionState.DISPATCHED, 1L);
        mission.transitionTo(MissionState.TAKEOFF, 2L);
        mission.transitionTo(MissionState.CRUISE, 3L);
        mission.transitionTo(MissionState.SEARCHING, 4L);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5L);
        mission.transitionTo(MissionState.TARGET_LOCKED, 6L);

        mission.recordProgress(10L, 0.0D, 0.25D, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        assertEquals(0.25D, mission.getRouteProgress(), 0.0D);
        assertFalse(mission.isProgressStalled(30L, 200L));
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
    public void missionSafetyBlocksDangerousActionsUntilTheRouteIsClear() {
        assertEquals(MissionSafety.RouteDecision.DIRECT_INTERCEPT,
                MissionSafety.decideRoute(18.0D, 6.0D, false, 20.0D));
        assertEquals(MissionSafety.RouteDecision.OFFSET_ORBIT,
                MissionSafety.decideRoute(70.0D, 8.0D, true, 20.0D));
        assertEquals(MissionSafety.RouteDecision.SAFE_SIDE_APPROACH,
                MissionSafety.decideRoute(40.0D, 40.0D, false, 20.0D));
        assertEquals(MissionSafety.RouteDecision.HOLD_AND_REEVALUATE,
                MissionSafety.decideRoute(0.0D, 0.0D, false, 20.0D));

        MissionSafety.CorridorAssessment assessment = MissionSafety.evaluateCorridor(
                30.0D, 12.0D, false, false, false, true, true, 20.0D);
        assertTrue(assessment.isSafe());
        assertEquals(MissionSafety.RouteDecision.DIRECT_INTERCEPT, assessment.getRouteDecision());

        MissionSafety.CorridorAssessment blocked = MissionSafety.evaluateCorridor(
                80.0D, 12.0D, true, false, false, true, true, 20.0D);
        assertFalse(blocked.isSafe());
        assertEquals(MissionSafety.RouteDecision.OFFSET_ORBIT, blocked.getRouteDecision());

        assertTrue(MissionSafety.canAdvanceToDangerousAction(MissionState.TARGET_LOCKED));
        assertFalse(MissionSafety.canAdvanceToDangerousAction(MissionState.SEARCHING));
        assertTrue(MissionSafety.requiresTarget(MissionState.ATTACK_TARGET));
        assertFalse(MissionSafety.requiresTarget(MissionState.CRUISE));
    }

    @Test
    public void missionCoordinatorPreventsDuplicateTargetClaims() {
        MissionCoordinator coordinator = new MissionCoordinator();
        UUID aircraftA = UUID.randomUUID();
        UUID aircraftB = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        assertTrue(coordinator.claim(aircraftA, target));
        assertFalse(coordinator.claim(aircraftB, target));
        assertTrue(coordinator.isAssigned(aircraftA, target));
        assertTrue(coordinator.isTargetOccupied(target));

        coordinator.release(aircraftA, target);
        assertFalse(coordinator.isAssigned(aircraftA, target));
        assertFalse(coordinator.isTargetOccupied(target));

        assertTrue(coordinator.claim(aircraftB, target));
        assertEquals(1, coordinator.activeMissionCount());
    }

    @Test
    public void missionCoordinatorSupportsRoleBasedReassignmentAndCoverageMetadata() {
        MissionCoordinator coordinator = new MissionCoordinator();
        UUID aircraftA = UUID.randomUUID();
        UUID aircraftB = UUID.randomUUID();
        UUID targetA = UUID.randomUUID();
        UUID targetB = UUID.randomUUID();

        coordinator.setRole(aircraftA, "SCOUT");
        coordinator.setCoverageZone(aircraftA, "RIVER");
        assertTrue(coordinator.claim(aircraftA, targetA, "SCOUT", "RIVER"));
        assertTrue(coordinator.isAssigned(aircraftA, targetA));
        assertEquals("SCOUT", coordinator.aircraftRole(aircraftA));
        assertEquals("RIVER", coordinator.coverageZone(aircraftA));

        assertFalse(coordinator.claim(aircraftB, targetA, "ATTACK", "RIVER"));
        assertTrue(coordinator.reassign(aircraftA, targetA, targetB, false, "ATTACK", "RIVER"));
        assertFalse(coordinator.isAssigned(aircraftA, targetA));
        assertTrue(coordinator.isAssigned(aircraftA, targetB));

        coordinator.release(aircraftA);
        assertFalse(coordinator.isTargetOccupied(targetB));
        assertEquals(0, coordinator.activeMissionCount());
    }

    @Test
    public void targetSelectorPrefersHigherThreatClassificationBeforeDistance() {
        TargetSelector<String> selector = new TargetSelector<>();
        String selected = selector.select(TargetSpec.hostile(TargetPriority.HIGH),
                Arrays.asList(
                        candidate("minecraft:zombie", "zombie", 100.0D),
                        candidate("bandit:heavy_guard", "guard", 10.0D)));
        assertEquals("guard", selected);
    }

    @Test
    public void targetSelectorDoesNotSubstituteWhenManualTargetIsMissing() {
        TargetSelector<String> selector = new TargetSelector<>();
        String selected = selector.select(TargetSpec.manual("hostile:target", TargetPriority.HIGH),
                Collections.singletonList(candidate("hostile:other", "wrong")));
        assertNull(selected);
    }

    @Test
    public void targetSelectorMatchesHostileNamespaceCandidates() {
        TargetSelector<String> selector = new TargetSelector<>();
        String selected = selector.select(TargetSpec.hostile(TargetPriority.HIGH),
                Arrays.asList(
                        candidate("minecraft:zombie", "zombie"),
                        candidate("minecraft:iron_golem", "golem"),
                        candidate("farmersdelight:tomato", "tomato"),
                        candidate("minecraft:diamond_ore", "ore")));
        assertEquals("zombie", selected);
    }

    @Test
    public void targetSelectorManualSelectionUsesExactRegistryAndStableTieBreak() {
        TargetSelector<String> selector = new TargetSelector<>();
        String selected = selector.select(TargetSpec.manual("hostile:target", TargetPriority.HIGH),
                Arrays.asList(
                        candidate("hostile:other", "other", 8.0D),
                        candidate("Hostile:Target", "target", 2.0D),
                        candidate("hostile:target", "target-later", 5.0D)));
        assertEquals("target", selected);
    }

    @Test
    public void targetSelectorManualSelectionMatchesNormalizedRegistryIdsCaseInsensitively() {
        TargetSelector<String> selector = new TargetSelector<>();
        String selected = selector.select(TargetSpec.manual("MINECRAFT:ZOMBIE", TargetPriority.HIGH),
                Arrays.asList(
                        candidate("minecraft:zombie", "zombie", 3.0D),
                        candidate("minecraft:spider", "spider", 1.0D)));
        assertEquals("zombie", selected);
    }

    @Test
    public void terminalTargetCannotBeReacquiredOrCleared() {
        UUID operator = UUID.randomUUID();
        UUID aircraftId = UUID.randomUUID();
        RDPMission mission = mission(new AiTaskRequest(UUID.randomUUID(), operator,
                MissionType.STRIKE, TargetSpec.manual("hostile:target", TargetPriority.HIGH),
                TargetPriority.HIGH, true, AutonomyLevel.AUTONOMOUS), aircraftId);
        UUID safeTarget = UUID.randomUUID();
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAKEOFF, 2);
        mission.transitionTo(MissionState.CRUISE, 3);
        mission.transitionTo(MissionState.SEARCHING, 4);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5);
        mission.lockTarget(safeTarget, 5);
        mission.transitionTo(MissionState.TARGET_LOCKED, 6);
        mission.transitionTo(MissionState.KAMIKADZE_TARGET, 7);

        try {
            mission.reacquireTarget(UUID.randomUUID(), 8);
            fail("Terminal target must be immutable during kamikaze commit");
        } catch (IllegalStateException expected) {
            // Terminal target is intentionally immutable once committed.
        }

        try {
            mission.clearTarget("TARGET_LOST", 8);
            fail("Terminal target must not be cleared after commit");
        } catch (IllegalStateException expected) {
            // Terminal target remains authoritative while the payload is committed.
        }
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
        return candidate(id, value, 1.0D);
    }

    private static TargetSelector.Candidate<String> candidate(final String id, final String value,
                                                              final double distanceSquared) {
        return new TargetSelector.Candidate<String>() {
            public String getRegistryId() { return id; }
            public double getDistanceSquared() { return distanceSquared; }
            public boolean isValid() { return true; }
            public TargetPriority getPriority() { return TargetPriority.HIGH; }
            public String getValue() { return value; }
        };
    }
}
