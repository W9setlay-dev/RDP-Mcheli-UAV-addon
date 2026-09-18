package net.vas.rdp.uav;

import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import net.vas.rdp.uav.weapon.GuidedNativeBehavior;
import net.vas.rdp.uav.weapon.GuidedObjectiveState;
import net.vas.rdp.uav.weapon.GuidedObjectiveType;
import net.vas.rdp.uav.weapon.GuidedStrikeObjective;
import net.vas.rdp.uav.weapon.GuidedTerminalProfile;
import net.vas.rdp.uav.weapon.GuidedWeaponCapability;
import net.vas.rdp.uav.weapon.ProjectileGuidanceMission;
import net.vas.rdp.uav.weapon.ProjectileGuidanceState;
import org.junit.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Deterministic lifecycle contracts for a guided strike.
 *
 * <p>This intentionally stops before the Forge/McHeliCE entity boundary. The
 * native launch call and projectile physics require a live server; the state
 * and compatibility contracts around that boundary are testable here.</p>
 */
public class GuidedMissileMissionLifecycleTest {
    @Test
    public void squareStrikeRunsFromPlanningThroughNativeImpact() {
        UUID missionId = UUID.randomUUID();
        UUID aircraftId = UUID.randomUUID();
        RDPMission mission = mission(missionId, aircraftId);
        GuidedStrikeObjective objective = GuidedStrikeObjective.square(missionId, 0,
                120.0D, 80.0D, -40.0D, 1);

        assertEquals(MissionState.PLANNED, mission.getState());
        objective.authorize();
        objective.beginTracking();
        assertEquals(GuidedObjectiveState.TRACKING, objective.getState());

        mission.transitionTo(MissionState.DISPATCHED, 2);
        mission.transitionTo(MissionState.TAKEOFF, 3);
        mission.transitionTo(MissionState.CRUISE, 4);
        mission.transitionTo(MissionState.SEARCHING, 5);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 6);
        mission.lockPoint(7);
        mission.transitionTo(MissionState.DIRECT_LAUNCH_COMPLETE, 8);

        ProjectileGuidanceMission projectile = new ProjectileGuidanceMission(
                UUID.randomUUID(), aircraftId, "asmissile",
                GuidedNativeBehavior.COORDINATE_BALLISTIC, objective);
        assertEquals(ProjectileGuidanceState.ASSIGNED, projectile.getState());
        projectile.nativeGuidanceActive();
        assertEquals(ProjectileGuidanceState.NATIVE_GUIDANCE_ACTIVE, projectile.getState());
        projectile.impacted();
        objective.complete();

        assertEquals(ProjectileGuidanceState.IMPACTED, projectile.getState());
        assertEquals(GuidedObjectiveState.COMPLETE, objective.getState());
        assertEquals(MissionState.DIRECT_LAUNCH_COMPLETE, mission.getState());
        assertTrue(mission.isTerminal());
    }

    @Test
    public void transientChunkHoldReturnsToWorldWaitWithoutPermanentFailure() {
        RDPMission mission = mission(UUID.randomUUID(), UUID.randomUUID());
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAKEOFF, 2);
        mission.defer("GUIDED_HOLD_CHUNK_UNLOADED",
                MissionState.WAITING_FOR_WORLD, 3);

        assertEquals(MissionState.WAITING_FOR_WORLD, mission.getState());
        assertFalse(mission.isTerminal());

        mission.transitionTo(MissionState.REPLAN, 4);
        assertEquals(MissionState.REPLAN, mission.getState());
        assertFalse(mission.isTerminal());
    }

    @Test
    public void unsupportedSquareWeaponsFailFromPlannedAndReplanStates() {
        GuidedWeaponCapability aamissile = capability("aamissile",
                GuidedNativeBehavior.ENTITY_SEEKER, GuidedObjectiveType.ENTITY);
        GuidedWeaponCapability meteor = capability("meteor",
                GuidedNativeBehavior.ENTITY_SEEKER, GuidedObjectiveType.ENTITY);
        GuidedWeaponCapability asmissile = capability("asmissile",
                GuidedNativeBehavior.COORDINATE_BALLISTIC, GuidedObjectiveType.SQUARE);

        assertFalse(aamissile.supports(GuidedObjectiveType.SQUARE));
        assertFalse(meteor.supports(GuidedObjectiveType.SQUARE));
        assertTrue(asmissile.supports(GuidedObjectiveType.SQUARE));

        RDPMission planned = mission(UUID.randomUUID(), UUID.randomUUID());
        planned.fail("UNSUPPORTED_OBJECTIVE_COMBINATION", 1);
        assertEquals(MissionState.FAILED, planned.getState());
        assertEquals("UNSUPPORTED_OBJECTIVE_COMBINATION", planned.getFailureReason());

        RDPMission replanning = mission(UUID.randomUUID(), UUID.randomUUID());
        replanning.transitionTo(MissionState.WAITING_FOR_WORLD, 1);
        replanning.transitionTo(MissionState.REPLAN, 2);
        replanning.fail("UNSUPPORTED_OBJECTIVE_COMBINATION", 3);
        assertEquals(MissionState.FAILED, replanning.getState());
        assertEquals("UNSUPPORTED_OBJECTIVE_COMBINATION", replanning.getFailureReason());
        assertFalse(replanning.getState() == MissionState.REPLAN);
    }

    @Test
    public void failedMissionCannotBeRetriedOrResurrected() {
        RDPMission mission = mission(UUID.randomUUID(), UUID.randomUUID());
        mission.transitionTo(MissionState.WAITING_FOR_WORLD, 1);
        mission.transitionTo(MissionState.REPLAN, 2);
        mission.fail("UNSUPPORTED_OBJECTIVE_COMBINATION", 3);

        try {
            mission.transitionTo(MissionState.REPLAN, 4);
            org.junit.Assert.fail("FAILED mission must reject REPLAN");
        } catch (IllegalStateException expected) {
            assertEquals(MissionState.FAILED, mission.getState());
        }
        assertFalse(mission.defer("RETRY_AFTER_FAILURE",
                MissionState.WAITING_FOR_WORLD, 5));
        assertEquals(MissionState.FAILED, mission.getState());
    }

    private static GuidedWeaponCapability capability(String type,
                                                      GuidedNativeBehavior behavior,
                                                      GuidedObjectiveType objective) {
        return new GuidedWeaponCapability(type, type, behavior,
                EnumSet.of(objective), new GuidedTerminalProfile(0, true));
    }

    private static RDPMission mission(UUID missionId, UUID aircraftId) {
        return new RDPMission(missionId, UUID.randomUUID(), aircraftId,
                "switchblade", MissionType.STRIKE,
                TargetSpec.point(120.0D, 80.0D, -40.0D, TargetPriority.HIGH),
                true, false, false, 256.0D, 32.0D, 0);
    }
}
