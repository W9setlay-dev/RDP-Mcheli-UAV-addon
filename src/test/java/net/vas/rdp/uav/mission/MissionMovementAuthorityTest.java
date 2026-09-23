package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class MissionMovementAuthorityTest {
    @Test
    public void activeAiMissionRequiresNativeWingmanAndRejectsOperatorFlightAuthority() {
        RDPMission mission = mission();
        mission.transitionTo(MissionState.DISPATCHED, 1);
        assertFalse(MissionMovementAuthority.operatorFlightInputAllowed(mission));
        assertTrue(MissionMovementAuthority.nativeWingManRequired(mission));
    }

    @Test
    public void manualOwnerIsTheOnlyOperatorFlightAuthority() {
        RDPMission mission = mission();
        assertEquals(ControlOwner.MISSION_AI, mission.getControlOwner());
        assertFalse(MissionMovementAuthority.operatorFlightInputAllowed(mission));
        assertTrue(MissionMovementAuthority.operatorFlightInputAllowed(null));
    }

    private static RDPMission mission() {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "switchblade", MissionType.RECONNAISSANCE,
                TargetSpec.point(0, 80, 0, TargetPriority.NORMAL),
                false, false, false, 64, 32, 0);
    }
}
