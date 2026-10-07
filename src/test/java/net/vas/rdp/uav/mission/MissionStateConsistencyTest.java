package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MissionStateConsistencyTest {
    @Test
    public void missionPhaseAndControlOwnerStayConsistentAcrossAllStates() {
        for (MissionState state : MissionState.values()) {
            RDPMission mission = RDPMission.restore(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "test-aircraft",
                    MissionType.RECONNAISSANCE,
                    TargetSpec.point(0.0D, 10.0D, 20.0D, TargetPriority.NORMAL),
                    false,
                    false,
                    false,
                    100.0D,
                    120.0D,
                    1L,
                    state,
                    null,
                    2L,
                    null,
                    null);
            mission.validateStateConsistency();
            assertNotNull(mission.getPhase());
            assertNotNull(mission.getControlOwner());
        }
    }

    @Test
    public void manualControlOverridesWillNotBreakMissionStateConsistency() {
        RDPMission mission = new RDPMission(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test-aircraft",
                MissionType.RECONNAISSANCE,
                TargetSpec.point(0.0D, 10.0D, 20.0D, TargetPriority.NORMAL),
                false,
                false,
                false,
                100.0D,
                120.0D,
                1L);
        mission.transitionTo(MissionState.DISPATCHED, 2L, "dispatch");
        mission.transitionTo(MissionState.TAKEOFF, 3L, "takeoff");
        mission.enterManualControl(4L);
        mission.transitionTo(MissionState.CRUISE, 5L, "cruise");
        mission.transitionTo(MissionState.SEARCHING, 6L, "search");

        assertEquals(ControlOwner.MANUAL, mission.getControlOwner());
        mission.validateStateConsistency();

        mission.exitManualControl(7L);
        assertEquals(ControlOwner.MISSION_AI, mission.getControlOwner());
    }

    @Test
    public void recoveryStateTakesAuthorityFromOperatorOverride() {
        RDPMission mission = new RDPMission(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test-aircraft",
                MissionType.RECONNAISSANCE,
                TargetSpec.point(0.0D, 10.0D, 20.0D, TargetPriority.NORMAL),
                false,
                false,
                false,
                100.0D,
                120.0D,
                1L);
        mission.transitionTo(MissionState.DISPATCHED, 2L, "dispatch");
        mission.transitionTo(MissionState.TAKEOFF, 3L, "takeoff");
        mission.enterSupervisedOperatorMode(4L);
        mission.transitionTo(MissionState.CRUISE, 5L, "cruise");
        mission.transitionTo(MissionState.WAITING_FOR_WORLD, 6L, "recovery");

        assertEquals(ControlOwner.RECOVERY, mission.getControlOwner());
        mission.validateStateConsistency();
    }

    @Test
    public void terminalTransitionsOverrideManualControlWithSafetyOwner() {
        RDPMission mission = new RDPMission(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test-aircraft",
                MissionType.RECONNAISSANCE,
                TargetSpec.point(0.0D, 10.0D, 20.0D, TargetPriority.NORMAL),
                false,
                false,
                false,
                100.0D,
                120.0D,
                1L);
        mission.transitionTo(MissionState.DISPATCHED, 2L, "dispatch");
        mission.transitionTo(MissionState.TAKEOFF, 3L, "takeoff");
        mission.enterManualControl(4L);
        mission.transitionTo(MissionState.CRUISE, 5L, "cruise");
        mission.fail("manual override lost authority", 6L);

        assertEquals(MissionState.FAILED, mission.getState());
        assertEquals(ControlOwner.SAFETY, mission.getControlOwner());
        mission.validateStateConsistency();
    }

    @Test
    public void terminalTargetLockRejectsReplacementByUuid() {
        UUID originalTarget = UUID.randomUUID();
        UUID replacementTarget = UUID.randomUUID();
        RDPMission mission = new RDPMission(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test-aircraft",
                MissionType.RECONNAISSANCE,
                TargetSpec.point(0.0D, 10.0D, 20.0D, TargetPriority.NORMAL),
                false,
                false,
                false,
                100.0D,
                120.0D,
                1L);
        mission.transitionTo(MissionState.DISPATCHED, 2L, "dispatch");
        mission.transitionTo(MissionState.TAKEOFF, 3L, "takeoff");
        mission.transitionTo(MissionState.CRUISE, 4L, "cruise");
        mission.transitionTo(MissionState.SEARCHING, 5L, "search");
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 6L, "target");
        mission.lockTarget(originalTarget, 7L);
        mission.transitionTo(MissionState.KAMIKADZE_TARGET, 8L, "terminal");

        try {
            mission.reacquireTarget(replacementTarget, 9L);
            fail("terminal target reassignment should be rejected");
        } catch (IllegalStateException expected) {
            assertEquals("Terminal target cannot be replaced", expected.getMessage());
        }
    }
}
