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

    @Test
    public void missionControlLeaseRejectsDuplicateAircraftClaim() {
        RDPMission first = mission();
        RDPMission second = mission();
        UUID aircraftId = UUID.randomUUID();

        assertTrue(MissionMovementAuthority.claimControl(first, aircraftId, 10L));
        assertFalse(MissionMovementAuthority.claimControl(second, aircraftId, 11L));
        assertTrue(MissionMovementAuthority.canIssueNativeControl(first, aircraftId, 11L));
        assertFalse(MissionMovementAuthority.canIssueNativeControl(second, aircraftId, 11L));

        MissionMovementAuthority.releaseControl(aircraftId);
        assertTrue(MissionMovementAuthority.claimControl(second, aircraftId, 12L));
    }

    @Test
    public void staleLeaseExpiresAndAllowsFreshClaim() {
        RDPMission mission = mission();
        UUID aircraftId = UUID.randomUUID();

        assertTrue(MissionMovementAuthority.claimControl(mission, aircraftId, 100L));
        assertTrue(MissionMovementAuthority.claimControl(mission, aircraftId, 103L));
        assertTrue(MissionMovementAuthority.canIssueNativeControl(mission, aircraftId, 101L));
        assertTrue(MissionMovementAuthority.canIssueNativeControl(mission, aircraftId, 103L));
        MissionMovementAuthority.releaseControl(aircraftId);
    }

    @Test
    public void terminalMissionCannotClaimNativeControl() {
        RDPMission mission = mission();
        UUID aircraftId = UUID.randomUUID();
        mission.fail("TEST_FAILURE", 1L);

        assertFalse(MissionMovementAuthority.claimControl(mission, aircraftId, 1L));
        assertFalse(MissionMovementAuthority.canIssueNativeControl(mission, aircraftId, 1L));
    }

    @Test
    public void staleLeaseCleanupLeavesTheAircraftAvailableForFreshClaim() {
        RDPMission mission = mission();
        UUID aircraftId = UUID.randomUUID();

        assertTrue(MissionMovementAuthority.claimControl(mission, aircraftId, 20L));
        MissionMovementAuthority.releaseExpiredControls(23L);
        assertTrue(MissionMovementAuthority.claimControl(mission, aircraftId, 24L));
        assertTrue(MissionMovementAuthority.canIssueNativeControl(mission, aircraftId, 24L));
    }

    private static RDPMission mission() {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "switchblade", MissionType.RECONNAISSANCE,
                TargetSpec.point(0, 80, 0, TargetPriority.NORMAL),
                false, false, false, 64, 32, 0);
    }
}
