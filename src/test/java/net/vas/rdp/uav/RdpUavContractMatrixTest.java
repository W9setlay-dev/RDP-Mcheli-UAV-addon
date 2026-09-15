package net.vas.rdp.uav;

import com.norwood.mcheli.wingman.mission.MissionNode;
import net.minecraft.nbt.NBTTagCompound;
import net.vas.rdp.uav.mission.AircraftAssignmentRegistry;
import net.vas.rdp.uav.mission.MissionQueue;
import net.vas.rdp.uav.mission.MissionCoordinator;
import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.MissionSafety;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.flight.SquareStrikePlan;
import net.vas.rdp.uav.scan.RDPScanReport;
import net.vas.rdp.uav.scan.ReportNbtCodec;
import net.vas.rdp.uav.target.TargetAcquisition;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSelector;
import net.vas.rdp.uav.target.TargetSpec;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.UavVehicleProfiles;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RdpUavContractMatrixTest {
    @Test
    public void everyMissionStateHasOnlyItsDeclaredSuccessors() {
        for (MissionState state : MissionState.values()) {
            for (MissionState next : MissionState.values()) {
                boolean terminal = state == MissionState.COMPLETE
                        || state == MissionState.ABORTED
                        || state == MissionState.FAILED
                        || state == MissionState.KAMIKADZE_DETONATED;
                boolean expected = !terminal && (next == MissionState.FAILED
                        || next == MissionState.ABORTED
                        || declaredSuccessor(state, next));
                if (next == state) {
                    expected = false;
                }
                assertEquals(state + " -> " + next, expected, state.canTransitionTo(next));
            }
        }
    }

    @Test
    public void terminalMissionStatesCannotBeResurrected() {
        for (MissionState terminal : new MissionState[] {
                MissionState.COMPLETE, MissionState.ABORTED, MissionState.FAILED,
                MissionState.KAMIKADZE_DETONATED }) {
            assertFalse(terminal.canTransitionTo(MissionState.RETURNING));
            assertFalse(terminal.canTransitionTo(MissionState.COMPLETE));
        }
    }

    @Test
    public void noCandidateWaitsWithoutFailingOrRetainingCurrentTarget() {
            RDPMission mission = mission();
            UUID target = UUID.randomUUID();
            mission.transitionTo(MissionState.DISPATCHED, 1);
            mission.transitionTo(MissionState.TAKEOFF, 2);
            mission.transitionTo(MissionState.CRUISE, 3);
            mission.transitionTo(MissionState.SEARCHING, 4);
            mission.transitionTo(MissionState.TARGET_ACQUIRED, 5);
            mission.lockTarget(target, 5);
            mission.clearTarget("NO_CANDIDATE", 6);
            mission.transitionTo(MissionState.LOST, 6);
            mission.transitionTo(MissionState.WAITING_FOR_TARGET, 7);

            assertNull(mission.getTargetId());
            assertEquals(target, mission.getLastTargetId());
            assertEquals("NO_CANDIDATE", mission.getLastTargetReason());
            assertEquals(MissionState.WAITING_FOR_TARGET, mission.getState());
            assertFalse(mission.getState() == MissionState.FAILED);
        }

    @Test
    public void noCandidateCanReenterSearchWithoutInventingTarget() {
            RDPMission mission = mission();
            mission.transitionTo(MissionState.DISPATCHED, 1);
            mission.transitionTo(MissionState.TAKEOFF, 2);
            mission.transitionTo(MissionState.CRUISE, 3);
            mission.transitionTo(MissionState.SEARCHING, 4);
            mission.transitionTo(MissionState.WAITING_FOR_TARGET, 5);
            mission.transitionTo(MissionState.SEARCHING, 6);
            assertNull(mission.getTargetId());
            assertEquals(MissionState.SEARCHING, mission.getState());
        }

    @Test
    public void assignmentReleaseCannotBeHijackedByAnotherMission() {
        UUID aircraft = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        AircraftAssignmentRegistry assignments = new AircraftAssignmentRegistry();
        assertTrue(assignments.reserve(aircraft, first));
        assignments.release(aircraft, second);
        assertEquals(first, assignments.missionFor(aircraft));
        assignments.release(aircraft, first);
        assertNull(assignments.missionFor(aircraft));
    }

    @Test
    public void missionQueueIsFifoAndRejectsNull() {
        MissionQueue queue = new MissionQueue();
        RDPMission first = mission();
        RDPMission second = mission();
        queue.enqueue(first);
        queue.enqueue(second);
        assertEquals(first, queue.poll());
        assertEquals(second, queue.poll());
        assertNull(queue.poll());
        try {
            queue.enqueue(null);
            fail("Null missions must not enter the queue");
        } catch (IllegalArgumentException expected) {
            // Expected validation boundary.
        }
    }

    @Test
    public void vehicleProfilesKeepReusableAltitudeSeparateFromOneWayPayloads() {
        UavVehicleProfile reusable = new UavVehicleProfile(
                UavExecutionProfile.REUSABLE_UAV, 250, 200, 320, 15, true, "NONE");
        UavVehicleProfile oneWay = new UavVehicleProfile(
                UavExecutionProfile.ONE_WAY, 0, 0, 4096, 0, false, "SELF_DESTRUCT");
        assertTrue(reusable.isReusable());
        assertEquals(250D, reusable.getExecutionAltitude(), 0D);
        assertEquals("NONE", reusable.getTerminalPayloadBehavior());
        assertFalse(oneWay.isReusable());
        assertEquals("SELF_DESTRUCT", oneWay.getTerminalPayloadBehavior());
        assertEquals(UavExecutionProfile.UNKNOWN,
                UavVehicleProfiles.forAircraft("unconfigured-definition").getExecutionProfile());
    }

    @Test
    public void longRangeProfileSeparatesDetectionLockAndExecutionRanges() {
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.LONG_RANGE_REUSABLE_UAV, 250, 200, 320,
                15, true, "NONE", 2048, 1536, 64);
        assertTrue(profile.isReusable());
        assertTrue(profile.supportsLongRangeLock());
        assertTrue(profile.getDetectionRange() > profile.getLockRange());
        assertTrue(profile.getLockRange() > profile.getMissionExecutionRange());
    }

    @Test
    public void acquisitionRejectsMissingInputsAndInvalidCandidates() {
        TargetAcquisition<String> acquisition = new TargetAcquisition<String>();
        try {
            acquisition.acquire(null, null);
            fail("Missing target input must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected validation boundary.
        }
        List<TargetSelector.Candidate<String>> candidates = Arrays.asList(
                candidate("mod:invalid", 1, TargetPriority.CRITICAL, false, "invalid"),
                candidate("mod:valid", 10, TargetPriority.NORMAL, true, "valid"));
        assertEquals("valid", acquisition.acquire(
                TargetSpec.automatic("mod", TargetPriority.LOW), candidates));
    }

    @Test
    public void squarePlanIsDeterministicAndStaysInsideRequestedEnvelope() {
        SquareStrikePlan plan = new SquareStrikePlan(
                100, 140, -50, 80, 60, 90, 20, 30, 1);
        List<MissionNode> first = plan.createNodes();
        List<MissionNode> second = plan.createNodes();
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).serialize(), second.get(i).serialize());
            if (first.get(i).type == MissionNode.Type.FLY_TO) {
                assertEquals(140, first.get(i).y, 0);
                assertTrue(first.get(i).x >= 70 && first.get(i).x <= 130);
                assertTrue(first.get(i).z >= -90 && first.get(i).z <= -10);
            } else {
                assertEquals(30, first.get(i).attackRadius, 0);
            }
        }
    }

    @Test
    public void scanReportNbtRoundTripPreservesIdentityAndCoordinates() {
        RDPScanReport report = new RDPScanReport("report", UUID.randomUUID(),
                UUID.randomUUID(), "mod:target", -1, 1.5, 80, -2.5, true,
                TargetPriority.HIGH, 40, 1234);
        NBTTagCompound tag = ReportNbtCodec.write(report);
        RDPScanReport restored = ReportNbtCodec.read(tag);
        assertEquals(report.getReportId(), restored.getReportId());
        assertEquals(report.getMissionId(), restored.getMissionId());
        assertEquals(report.getTargetId(), restored.getTargetId());
        assertEquals(report.getTargetRegistryId(), restored.getTargetRegistryId());
        assertEquals(report.getDimension(), restored.getDimension());
        assertEquals(report.getX(), restored.getX(), 0);
        assertEquals(report.getY(), restored.getY(), 0);
        assertEquals(report.getZ(), restored.getZ(), 0);
        assertEquals(report.isDeepScan(), restored.isDeepScan());
        assertEquals(report.getPriority(), restored.getPriority());
        assertEquals(report.getDurationTicks(), restored.getDurationTicks());
        assertEquals(report.getTimestamp(), restored.getTimestamp());
    }

    @Test
    public void corridorSafetyRejectsInvalidAndUnsafeInputsWithoutCommitment() {
        MissionSafety.CorridorAssessment invalid = MissionSafety.evaluateCorridor(
                Double.NaN, 0, false, false, false, true, true, 20);
        assertEquals(MissionSafety.RouteDecision.HOLD_AND_REEVALUATE,
                invalid.getRouteDecision());
        assertFalse(invalid.isSafe());

        MissionSafety.CorridorAssessment fuelBlocked = MissionSafety.evaluateCorridor(
                40, 4, false, false, false, false, true, 20);
        assertEquals(MissionSafety.RouteDecision.HOLD_AND_REEVALUATE,
                fuelBlocked.getRouteDecision());
        assertFalse(fuelBlocked.isFuelReady());
        assertFalse(fuelBlocked.isSafe());
    }

    @Test
    public void coordinatorReassignmentDoesNotStealAnOccupiedTarget() {
        MissionCoordinator coordinator = new MissionCoordinator();
        UUID firstAircraft = UUID.randomUUID();
        UUID secondAircraft = UUID.randomUUID();
        UUID currentTarget = UUID.randomUUID();
        UUID occupiedTarget = UUID.randomUUID();

        assertTrue(coordinator.claim(firstAircraft, currentTarget));
        assertTrue(coordinator.claim(secondAircraft, occupiedTarget));
        assertFalse(coordinator.reassign(firstAircraft, currentTarget, occupiedTarget, false));
        assertTrue(coordinator.isAssigned(firstAircraft, currentTarget));
        assertTrue(coordinator.isAssigned(secondAircraft, occupiedTarget));
    }

    private static boolean declaredSuccessor(MissionState state, MissionState next) {
        switch (state) {
            case PLANNED: return next == MissionState.DISPATCHED;
            case DISPATCHED: return next == MissionState.TAXI || next == MissionState.TAKEOFF;
            case TAXI: return next == MissionState.TAKEOFF;
            case TAKEOFF: return next == MissionState.CRUISE;
            case CRUISE: return next == MissionState.SEARCHING;
            case SEARCHING: return next == MissionState.TARGET_ACQUIRED
                    || next == MissionState.WAITING_FOR_TARGET;
            case WAITING_FOR_TARGET: return next == MissionState.SEARCHING
                    || next == MissionState.TARGET_ACQUIRED;
            case LOST: return next == MissionState.SEARCHING
                    || next == MissionState.WAITING_FOR_TARGET;
            case TARGET_ACQUIRED: return next == MissionState.TARGET_LOCKED
                    || next == MissionState.LOST;
            case TARGET_LOCKED: return next == MissionState.APPROACHING
                    || next == MissionState.ATTACK_TARGET
                    || next == MissionState.KAMIKADZE_TARGET
                    || next == MissionState.LOST;
            case ATTACK_TARGET: return next == MissionState.APPROACHING
                    || next == MissionState.ON_TARGET || next == MissionState.RETURNING;
            case KAMIKADZE_TARGET: return next == MissionState.ON_TARGET
                    || next == MissionState.RETURNING
                    || next == MissionState.KAMIKADZE_DETONATED;
            case APPROACHING: return next == MissionState.ON_TARGET
                    || next == MissionState.LOST;
            case ON_TARGET: return next == MissionState.ORBITING
                    || next == MissionState.SCANNING;
            case ORBITING: return next == MissionState.SCANNING;
            case SCANNING: return next == MissionState.DEEP_SCANNING
                    || next == MissionState.REPORTING;
            case DEEP_SCANNING: return next == MissionState.REPORTING;
            case REPORTING: return next == MissionState.RETURNING;
            case RETURNING: return next == MissionState.LANDING;
            case LANDING: return next == MissionState.PARKING;
            case PARKING: return next == MissionState.COMPLETE;
            default: return false;
        }
    }

    private static RDPMission mission() {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "switchblade", MissionType.RECONNAISSANCE,
                TargetSpec.point(0, 80, 0, TargetPriority.NORMAL),
                false, false, false, 64, 32, 0);
    }

    private static TargetSelector.Candidate<String> candidate(final String id,
                                                               final double distance,
                                                               final TargetPriority priority,
                                                               final boolean valid,
                                                               final String value) {
        return new TargetSelector.Candidate<String>() {
            public String getRegistryId() { return id; }
            public double getDistanceSquared() { return distance; }
            public boolean isValid() { return valid; }
            public TargetPriority getPriority() { return priority; }
            public String getValue() { return value; }
        };
    }
}
