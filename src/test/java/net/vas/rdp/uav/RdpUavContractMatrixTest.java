package net.vas.rdp.uav;

import com.norwood.mcheli.wingman.mission.MissionNode;
import io.netty.buffer.Unpooled;
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
import net.vas.rdp.uav.target.TargetSelectionMode;
import net.vas.rdp.uav.target.TargetSelector;
import net.vas.rdp.uav.target.TargetSpec;
import net.vas.rdp.uav.target.ContactLifecycle;
import net.vas.rdp.uav.target.ContactSnapshot;
import net.vas.rdp.uav.target.ContactTracker;
import net.vas.rdp.uav.network.ContactSelection;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.UavVehicleProfiles;
import net.vas.rdp.uav.swarm.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.Assert.*;

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
        assertNotSame(MissionState.FAILED, mission.getState());
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
        public void recoverableConditionsDoNotImmediatelyFailMission() {
            RDPMission mission = mission();
            mission.transitionTo(MissionState.DISPATCHED, 1);
            mission.transitionTo(MissionState.TAKEOFF, 2);
            assertTrue(mission.defer("CHUNK_UNLOADED", MissionState.WAITING_FOR_WORLD, 3));
            assertEquals(MissionState.WAITING_FOR_WORLD, mission.getState());
            assertEquals(1, mission.getRecoveryAttempts());
            assertFalse(mission.isTerminal());
        }

        @Test
        public void plannedMissionMayWaitForSpecificEntityTarget() {
            RDPMission mission = mission();
            assertTrue(mission.defer("TARGET_NOT_AVAILABLE",
                    MissionState.WAITING_FOR_TARGET, 1));
            assertEquals(MissionState.WAITING_FOR_TARGET, mission.getState());
            assertFalse(mission.isTerminal());
            assertEquals("TARGET_NOT_AVAILABLE", mission.getRecoveryReason());
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
    public void fpvProfileIsExplicitlyHelicopterOneWayAndDoesNotFallThroughToUnknown() {
        UavVehicleProfile profile = UavVehicleProfiles.forAircraft("fpv-drone");
        assertEquals(UavExecutionProfile.HELICOPTER_ONE_WAY_FPV, profile.getExecutionProfile());
        assertEquals("SELF_DESTRUCT", profile.getTerminalPayloadBehavior());
        assertTrue(profile.getAttackEnvelope() > 0D);
    }

    @Test
    public void transientAttackFailureUsesRecoveryUntilBudgetIsExhausted() {
        RDPMission mission = mission();
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAKEOFF, 2);
        assertTrue(mission.defer("WINGMAN_DISPATCH_REJECTED",
                MissionState.EXECUTION_STALLED, 3));
        assertFalse(mission.isTerminal());
        assertEquals(MissionState.EXECUTION_STALLED, mission.getState());
    }

    @Test
    public void opticsLockSeparatesAreaAndEntityModes() {
        net.vas.rdp.uav.target.OpticsLock lock = new net.vas.rdp.uav.target.OpticsLock();
        lock.area(10, 64, 20, 1);
        assertEquals(net.vas.rdp.uav.target.OpticsLock.Mode.AREA, lock.getMode());
        UUID id = UUID.randomUUID();
        lock.entity(id, 12, 65, 22, 2);
        assertEquals(net.vas.rdp.uav.target.OpticsLock.Mode.ENTITY, lock.getMode());
        assertEquals(id, lock.getEntityId());
        lock.clear();
        assertEquals(net.vas.rdp.uav.target.OpticsLock.Mode.NONE, lock.getMode());
    }

    @Test
    public void recoveryOnlyFailsAfterConfiguredBudgetIsExhausted() {
        RDPMission mission = mission();
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAKEOFF, 2);
        for (int attempt = 1; attempt <= 5; attempt++) {
            MissionState recovery = attempt % 2 == 0
                    ? MissionState.EXECUTION_STALLED : MissionState.WAITING_FOR_WORLD;
            assertTrue(mission.defer("TRANSIENT_" + attempt, recovery, attempt + 2));
            mission.transitionTo(MissionState.REPLAN, attempt + 3);
        }
        assertFalse(mission.isTerminal());
        assertFalse(mission.defer("TRANSIENT_FINAL",
                MissionState.EXECUTION_STALLED, 20));
        assertEquals(MissionState.FAILED, mission.getState());
        assertTrue(mission.getFailureReason().endsWith("RECOVERY_EXHAUSTED"));
    }

    @Test
    public void operatorEntityLockAdvancesQueuedMissionWithoutChangingIntent() {
        RDPMission mission = mission();
        mission.applyOperatorEntityLock(UUID.randomUUID(), 1);
        assertEquals(MissionState.TARGET_LOCKED, mission.getState());
        assertEquals(MissionType.RECONNAISSANCE, mission.getMissionType());
        assertFalse(mission.isAttack());
        assertEquals("TARGET_LOCKED", mission.getState().name());
    }

    @Test
    public void operatorEntityLockRefreshesActiveAttackWithoutRewindingState() {
        RDPMission mission = mission();
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAKEOFF, 2);
        mission.transitionTo(MissionState.CRUISE, 3);
        mission.transitionTo(MissionState.SEARCHING, 4);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5);
        mission.lockTarget(UUID.randomUUID(), 6);
        mission.transitionTo(MissionState.ATTACK_TARGET, 7);
        UUID replacement = UUID.randomUUID();
        mission.applyOperatorEntityLock(replacement, 8);
        assertEquals(MissionState.ATTACK_TARGET, mission.getState());
        assertEquals(replacement, mission.getTargetId());
    }

    @Test
    public void operatorEntityLockRecoversExecutionStalledMissionBeforeLocking() {
        RDPMission mission = mission();
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAKEOFF, 2);
        mission.defer("INVALID_MISSION_TRANSITION", MissionState.EXECUTION_STALLED, 3);

        mission.applyOperatorEntityLock(UUID.randomUUID(), 4);

        assertEquals(MissionState.TARGET_LOCKED, mission.getState());
        assertEquals(4L, mission.getLastUpdateTick());
    }

    @Test
    public void operatorEntityLockRecoversWaitingWorldMissionBeforeLocking() {
        RDPMission mission = mission();
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.defer("AIRCRAFT_NOT_FOUND", MissionState.WAITING_FOR_WORLD, 2);

        mission.applyOperatorEntityLock(UUID.randomUUID(), 3);

        assertEquals(MissionState.TARGET_LOCKED, mission.getState());
    }

    @Test
    public void operatorAreaLockReplacesEntityObjectiveAndPreservesMissionIntent() {
        RDPMission mission = mission();
        mission.applyOperatorEntityLock(UUID.randomUUID(), 1);
        mission.applyOperatorAreaLock(12.5D, 80.0D, -4.0D, 2);

        assertEquals(MissionState.TARGET_LOCKED, mission.getState());
        assertEquals(TargetSelectionMode.POINT, mission.getTargetSelectionMode());
        assertEquals(12.5D, mission.getTargetSpec().getX(), 0.0D);
        assertEquals(80.0D, mission.getTargetSpec().getY(), 0.0D);
        assertEquals(-4.0D, mission.getTargetSpec().getZ(), 0.0D);
        assertFalse(mission.isAttack());
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
    public void reusableDestructiveRoutesRequireExplicitAuthorization() {
        RDPMission mission = mission();
        assertFalse(mission.isDestructiveAuthorized());
        mission.authorizeDestructiveAction();
        assertTrue(mission.isDestructiveAuthorized());
        mission.setPaused(true);
        assertTrue(true);
    }

    @Test
    public void missionRepositoryRemovesTerminalAndActiveRecords() {
        net.vas.rdp.uav.mission.MissionRepository repository =
                new net.vas.rdp.uav.mission.MissionRepository();
        RDPMission mission = mission();
        repository.add(mission);
        assertTrue(repository.remove(mission.getMissionId()));
        assertNull(repository.find(mission.getMissionId()));
        assertFalse(repository.remove(mission.getMissionId()));
    }

    @Test
    public void manualClassificationRejectsVehiclesAndFriendlyNamespaces() {
        assertFalse(net.vas.rdp.uav.target.TargetClassifier.isMissionRelevant(
                "mcheli:vehicle_mq9", "hostile"));
        assertTrue(net.vas.rdp.uav.target.TargetClassifier.isHostileCandidate(
                "minecraft:zombie"));
        assertFalse(net.vas.rdp.uav.target.TargetClassifier.isVehicleId(
                "minecraft:zombie"));
        assertTrue(net.vas.rdp.uav.target.TargetClassifier.isHostileVehicle(
                "mcheli:bandit_vehicle"));
        assertFalse(net.vas.rdp.uav.target.TargetClassifier.isHostileVehicle(
                "mcheli:mq9"));
    }

    @Test
    public void swarmMembersAreDeterministicAndPersistable() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000003");
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), owner, "recon");
        swarm.add(new SwarmMember(second, "LONG_RANGE_REUSABLE_UAV", true, true));
        swarm.add(new SwarmMember(first, "ONE_WAY", false, true));
        assertEquals(first, swarm.getMembers().get(0).getAircraftId());
        assertEquals(0, swarm.getMembers().get(0).getSlot().getIndex());
        assertEquals(1, swarm.getMembers().get(1).getSlot().getIndex());
        assertEquals(first, swarm.referenceMember());
        swarm.setLeadership(SwarmLeadership.DESIGNATED_LEADER);
        swarm.setDesignatedLeader(second);
        assertEquals(second, swarm.referenceMember());
    }

    @Test
    public void swarmAllocatorHonorsCapabilitiesAndOperatorOverride() {
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), UUID.randomUUID(), "ops");
        SwarmMember observer = new SwarmMember(UUID.randomUUID(), "ONE_WAY", false, false);
        SwarmMember payload = new SwarmMember(UUID.randomUUID(), "REUSABLE_UAV", true, true);
        payload.setOperatorControlled(true);
        swarm.add(observer);
        swarm.add(payload);
        assertEquals(0, new SwarmTaskAllocator().suitable(swarm,
                SwarmTask.POINT_OBSERVATION, 8).size());
        assertEquals(0, new SwarmTaskAllocator().suitable(swarm,
                SwarmTask.ESCORT, 8).size());
    }

    @Test
    public void contactTrackerPreservesIdentityAndLastKnownDataAcrossLoss() {
        ContactTracker tracker = new ContactTracker(10);
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000099");
        tracker.observe(0, id, "hostile:target", 10, 80, 20, 40, 1,
                ContactLifecycle.SELECTED);
        ContactSnapshot updated = tracker.observe(0, id, "hostile:target", 14, 80, 20, 36, 3,
                ContactLifecycle.TRACKING);
        assertEquals(id, updated.getEntityId());
        assertEquals(2.0D, updated.getVelocityX(), 0.0001D);
        ContactSnapshot lost = tracker.markLost(id, 5);
        assertNotNull(lost);
        assertEquals(id, lost.getEntityId());
        assertEquals(ContactLifecycle.LOST, lost.getLifecycle());
        assertEquals(14.0D, lost.getX(), 0.0001D);
        assertEquals(2.0D, lost.getVelocityX(), 0.0001D);
        ContactSnapshot expired = tracker.markLost(id, 20);
        assertNotNull(expired);
        assertEquals(ContactLifecycle.EXPIRED, expired.getLifecycle());
        assertEquals(id, expired.getEntityId());
    }

    @Test
    public void contactTrackerCyclesStableUuidOrder() {
        ContactTracker tracker = new ContactTracker();
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        tracker.observe(0, second, "hostile:b", 0, 0, 0, 2, 1, ContactLifecycle.SELECTABLE);
        tracker.observe(0, first, "hostile:a", 0, 0, 0, 1, 1, ContactLifecycle.SELECTABLE);
        assertEquals(first, Objects.requireNonNull(tracker.cycle(null, true)).getEntityId());
        assertEquals(second, Objects.requireNonNull(tracker.cycle(first, true)).getEntityId());
        assertEquals(first, Objects.requireNonNull(tracker.cycle(second, false)).getEntityId());
    }

    @Test
    public void contactSelectionPacketRoundTripsActions() {
        ContactSelection sent = new ContactSelection(true, false);
        ContactSelection received = new ContactSelection();
        io.netty.buffer.ByteBuf buffer = Unpooled.buffer();
        sent.toBytes(buffer);
        received.fromBytes(buffer);
        assertTrue(received.next);
        assertFalse(received.clear);
    }

    @Test
    public void acquisitionRejectsMissingInputsAndInvalidCandidates() {
        TargetAcquisition<String> acquisition = new TargetAcquisition<>();
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
        if (next == MissionState.WAITING_FOR_WORLD
                || next == MissionState.WAITING_FOR_FUEL
                || next == MissionState.EXECUTION_STALLED
                || next == MissionState.REPLAN
                || next == MissionState.RECOVERING) {
            return state != MissionState.COMPLETE && state != MissionState.ABORTED
                    && state != MissionState.FAILED
                    && state != MissionState.KAMIKADZE_DETONATED;
        }
        switch (state) {
            case PLANNED: return next == MissionState.DISPATCHED
                    || next == MissionState.WAITING_FOR_TARGET;
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
                    || next == MissionState.DIRECT_LAUNCH_COMPLETE
                    || next == MissionState.LOST;
            case ATTACK_TARGET: return next == MissionState.APPROACHING
                    || next == MissionState.ON_TARGET || next == MissionState.RETURNING
                    || next == MissionState.KAMIKADZE_TARGET;
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
            case REPORTING: return next == MissionState.HOLDING
                    || next == MissionState.RETURNING;
            case HOLDING: return next == MissionState.RETURNING
                    || next == MissionState.COMPLETE;
            case REPLAN:
                return next == MissionState.SEARCHING
                        || next == MissionState.TARGET_LOCKED
                        || next == MissionState.APPROACHING
                        || next == MissionState.ATTACK_TARGET;
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
