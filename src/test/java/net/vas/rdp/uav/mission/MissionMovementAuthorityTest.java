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
    public void nullRiderDoesNotCountAsManualPilotTakeover() {
        assertFalse(UavControlAccess.isDirectEntityRider(null));
        assertFalse(MissionMovementAuthority.operatorFlightInputAllowed(mission()));
    }

    @Test
    public void directAircraftRiderDoesCountAsManualPilotTakeover() {
        assertTrue(UavControlAccess.isDirectEntityRider(new Object()));
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
    public void expiredLeaseIsDroppedOnTickBasedCleanup() {
        RDPMission mission = mission();
        UUID aircraftId = UUID.randomUUID();

        assertTrue(MissionMovementAuthority.claimControl(mission, aircraftId, 30L));
        MissionMovementAuthority.releaseControl(aircraftId, 33L);

        assertFalse(MissionMovementAuthority.canIssueNativeControl(mission, aircraftId, 33L));
        assertNull(MissionMovementAuthority.getActiveLease(aircraftId));
    }

    @Test
    public void everyTerminalMissionTransitionReleasesItsLease() {
        UUID aircraftId = UUID.randomUUID();
        RDPMission aborted = mission(aircraftId);
        assertTrue(MissionMovementAuthority.claimControl(aborted, aircraftId, 1L));
        aborted.abort("OPERATOR_ABORT", 2L);
        assertLeaseReleased(aircraftId, aborted, 2L);

        RDPMission failed = mission(aircraftId);
        assertTrue(MissionMovementAuthority.claimControl(failed, aircraftId, 3L));
        failed.fail("MISSION_FAILURE", 4L);
        assertLeaseReleased(aircraftId, failed, 4L);

        RDPMission completed = mission(aircraftId);
        advanceToParking(completed);
        assertTrue(MissionMovementAuthority.claimControl(completed, aircraftId, 14L));
        completed.complete(15L);
        assertLeaseReleased(aircraftId, completed, 15L);

        RDPMission detonated = mission(aircraftId);
        advanceToKamikazeTarget(detonated);
        assertTrue(MissionMovementAuthority.claimControl(detonated, aircraftId, 7L));
        detonated.transitionTo(MissionState.KAMIKADZE_DETONATED, 8L);
        assertLeaseReleased(aircraftId, detonated, 8L);

        RDPMission lost = mission(aircraftId);
        advanceToKamikazeTarget(lost);
        assertTrue(MissionMovementAuthority.claimControl(lost, aircraftId, 7L));
        lost.transitionTo(MissionState.AIRCRAFT_LOST, 8L);
        assertLeaseReleased(aircraftId, lost, 8L);
    }

    @Test
    public void activeHoldingAndLegacyLaunchStatesRetainMissionLease() {
        UUID aircraftId = UUID.randomUUID();
        RDPMission holding = mission(aircraftId);
        advanceToReporting(holding);
        assertTrue(MissionMovementAuthority.claimControl(holding, aircraftId, 10L));
        holding.transitionTo(MissionState.HOLDING, 11L);

        assertTrue(MissionMovementAuthority.nativeWingManRequired(holding));
        assertTrue(MissionMovementAuthority.canIssueNativeControl(holding, aircraftId, 11L));
        assertNotNull(MissionMovementAuthority.getActiveLease(aircraftId));
        MissionMovementAuthority.releaseControl(aircraftId);

        UUID launchAircraftId = UUID.randomUUID();
        RDPMission launchPending = mission(launchAircraftId);
        advanceToTargetLocked(launchPending);
        assertTrue(MissionMovementAuthority.claimControl(launchPending, launchAircraftId, 6L));
        launchPending.transitionTo(MissionState.DIRECT_LAUNCH_COMPLETE, 7L);

        assertTrue(MissionMovementAuthority.nativeWingManRequired(launchPending));
        assertTrue(MissionMovementAuthority.canIssueNativeControl(launchPending,
                launchAircraftId, 7L));
        assertNotNull(MissionMovementAuthority.getActiveLease(launchAircraftId));
        MissionMovementAuthority.releaseControl(launchAircraftId);
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

    private static void assertLeaseReleased(UUID aircraftId, RDPMission mission, long tick) {
        assertFalse(MissionMovementAuthority.canIssueNativeControl(mission, aircraftId, tick));
        assertNull(MissionMovementAuthority.getActiveLease(aircraftId));
        assertFalse(MissionMovementAuthority.nativeWingManRequired(mission));
    }

    private static void advanceToTargetLocked(RDPMission mission) {
        mission.transitionTo(MissionState.DISPATCHED, 1L);
        mission.transitionTo(MissionState.TAKEOFF, 2L);
        mission.transitionTo(MissionState.CRUISE, 3L);
        mission.transitionTo(MissionState.SEARCHING, 4L);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5L);
        mission.lockPoint(6L);
        mission.transitionTo(MissionState.TARGET_LOCKED, 6L);
    }

    private static void advanceToKamikazeTarget(RDPMission mission) {
        mission.transitionTo(MissionState.DISPATCHED, 1L);
        mission.transitionTo(MissionState.TAKEOFF, 2L);
        mission.transitionTo(MissionState.CRUISE, 3L);
        mission.transitionTo(MissionState.SEARCHING, 4L);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5L);
        mission.lockTarget(UUID.randomUUID(), 6L);
        mission.transitionTo(MissionState.KAMIKADZE_TARGET, 7L);
    }

    private static void advanceToReporting(RDPMission mission) {
        mission.transitionTo(MissionState.DISPATCHED, 1L);
        mission.transitionTo(MissionState.TAKEOFF, 2L);
        mission.transitionTo(MissionState.CRUISE, 3L);
        mission.transitionTo(MissionState.SEARCHING, 4L);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5L);
        mission.lockPoint(6L);
        mission.transitionTo(MissionState.APPROACHING, 7L);
        mission.transitionTo(MissionState.ON_TARGET, 8L);
        mission.transitionTo(MissionState.SCANNING, 9L);
        mission.transitionTo(MissionState.REPORTING, 10L);
    }

    private static void advanceToParking(RDPMission mission) {
        advanceToReporting(mission);
        mission.transitionTo(MissionState.HOLDING, 11L);
        mission.transitionTo(MissionState.RETURNING, 12L);
        mission.transitionTo(MissionState.LANDING, 13L);
        mission.transitionTo(MissionState.PARKING, 14L);
    }

    private static RDPMission mission() {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "switchblade", MissionType.RECONNAISSANCE,
                TargetSpec.point(0, 80, 0, TargetPriority.NORMAL),
                false, false, false, 64, 32, 0);
    }
}
