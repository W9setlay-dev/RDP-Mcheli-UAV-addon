package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import net.vas.rdp.uav.flight.KamikazePilotOverride;
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
        assertTrue("the active mission's native controller remains required",
                MissionMovementAuthority.nativeWingManRequired(mission));
    }

    @Test
    public void missionLeaseRemainsTheMovementAuthorityWithAnOperatorInStation() {
        RDPMission mission = mission();
        UUID aircraftId = mission.getAircraftId();
        try {
            assertTrue(MissionMovementAuthority.claimControl(mission, aircraftId, 40L));
            assertFalse("raw operator axes stay filtered while AI owns movement",
                    MissionMovementAuthority.operatorFlightInputAllowed(mission));
            assertTrue("the same AI lease can apply while the station remains occupied",
                    MissionMovementAuthority.canIssueNativeControl(mission, aircraftId, 40L));
        } finally {
            MissionMovementAuthority.releaseControl(aircraftId, mission.getMissionId());
        }
    }

    @Test
    public void manualControlStateIsExplicitlyReturnedWhenStationOperatorIsPresent() {
        RDPMission mission = mission();
        MissionMovementAuthority.applyManualOperatorState(mission, true, 5L);
        assertEquals(ControlOwner.MANUAL, mission.getControlOwner());
        assertTrue(MissionMovementAuthority.operatorFlightInputAllowed(mission));
        assertFalse(MissionMovementAuthority.nativeWingManRequired(mission));

        MissionMovementAuthority.applyManualOperatorState(mission, false, 6L);
        assertEquals(ControlOwner.MISSION_AI, mission.getControlOwner());
        assertFalse(MissionMovementAuthority.operatorFlightInputAllowed(mission));
        assertTrue(MissionMovementAuthority.nativeWingManRequired(mission));
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

    @Test
    public void leaseMatchingRejectsMissingIdentitiesWithoutThrowing() {
        RDPMission mission = mission();
        UUID aircraftId = UUID.randomUUID();
        AircraftControlLease lease = AircraftControlLease.forMission(
                mission, aircraftId, 10L, 20L, "TEST");

        assertFalse(lease.matches(null, mission.getMissionId()));
        assertFalse(lease.matches(aircraftId, null));
        assertTrue(lease.matches(aircraftId, mission.getMissionId()));
    }

    @Test
    public void releasingOlderMissionDoesNotReleaseNewerAircraftLease() {
        UUID aircraftId = UUID.randomUUID();
        RDPMission older = mission(aircraftId);
        RDPMission newer = mission(aircraftId);

        try {
            assertTrue(MissionMovementAuthority.claimControl(older, aircraftId, 1L));
            // The first lease expires at tick 3, allowing the later mission to take control.
            assertTrue(MissionMovementAuthority.claimControl(newer, aircraftId, 4L));

            MissionMovementAuthority.releaseControl(aircraftId, older.getMissionId());

            assertTrue(MissionMovementAuthority.canIssueNativeControl(newer, aircraftId, 4L));
        } finally {
            MissionMovementAuthority.releaseControl(aircraftId);
        }
    }

    @Test
    public void clearingOlderMissionDoesNotClearNewerKamikazeOverride() {
        UUID aircraftId = UUID.randomUUID();
        UUID olderMissionId = UUID.randomUUID();
        UUID newerMissionId = UUID.randomUUID();
        UUID olderTargetId = UUID.randomUUID();
        UUID newerTargetId = UUID.randomUUID();

        try {
            KamikazePilotOverride.lock(aircraftId, olderTargetId, olderMissionId);
            KamikazePilotOverride.lock(aircraftId, newerTargetId, newerMissionId);

            KamikazePilotOverride.clear(aircraftId, olderMissionId);

            org.junit.Assert.assertEquals(newerTargetId,
                    KamikazePilotOverride.targetFor(aircraftId));
        } finally {
            KamikazePilotOverride.clear(aircraftId);
        }
    }

    private static RDPMission mission(UUID aircraftId) {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), aircraftId,
                "switchblade", MissionType.RECONNAISSANCE,
                TargetSpec.point(0.0D, 80.0D, 0.0D, TargetPriority.NORMAL),
                false, false, false, 64.0D, 32.0D, 0L);
    }

    private static RDPMission mission() {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "switchblade", MissionType.RECONNAISSANCE,
                TargetSpec.point(0, 80, 0, TargetPriority.NORMAL),
                false, false, false, 64, 32, 0);
    }
}
