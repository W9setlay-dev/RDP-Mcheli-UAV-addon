package net.vas.rdp.uav.mission;

import org.junit.Test;

import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class MissionProgressPolicyTest {
    @Test
    public void intentionalObservationStatesDoNotTimeoutAsStalledRoutes() {
        MissionProgressPolicy.Decision decision = MissionProgressPolicy.evaluate(
                MissionState.SCANNING, true, false, false,
                500L, 10L, 200L);

        assertEquals(MissionProgressPolicy.Action.HOLD, decision.getAction());
        assertEquals("PROGRESS_NOT_EXPECTED", decision.getReason());
    }

    @Test
    public void expectedProgressStillUsesStallPolicy() {
        MissionProgressPolicy.Decision decision = MissionProgressPolicy.evaluate(
                MissionState.APPROACHING, true, false, true,
                500L, 10L, 200L);

        assertEquals(MissionProgressPolicy.Action.REACQUIRE, decision.getAction());
        assertEquals("TARGET_LOST", decision.getReason());
    }

    @Test
    public void scanningMissionDoesNotTransitionWhenDistanceIsStatic() {
        RDPMission mission = new RDPMission(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "switchblade", MissionType.RECONNAISSANCE,
                TargetSpec.point(0.0D, 80.0D, 0.0D, TargetPriority.NORMAL),
                false, false, false, 64.0D, 32.0D, 0L);
        mission.transitionTo(MissionState.DISPATCHED, 1L);
        mission.transitionTo(MissionState.TAKEOFF, 2L);
        mission.transitionTo(MissionState.CRUISE, 3L);
        mission.transitionTo(MissionState.SEARCHING, 4L);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5L);
        mission.lockPoint(6L);
        mission.transitionTo(MissionState.APPROACHING, 7L);
        mission.transitionTo(MissionState.ON_TARGET, 8L);
        mission.transitionTo(MissionState.SCANNING, 9L);

        mission.recordProgress(500L, 0.0D, 0.0D,
                20.0D, 20.0D, 10.0D, 10.0D, false);

        assertEquals(MissionState.SCANNING, mission.getState());
        assertEquals(500L, mission.getLastMeaningfulProgressTick());
    }
}
