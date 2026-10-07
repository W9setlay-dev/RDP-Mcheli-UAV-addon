package net.vas.rdp.uav;

import com.norwood.mcheli.wingman.mission.MissionNode;
import net.vas.rdp.uav.energy.UavEnergy;
import net.vas.rdp.uav.flight.SquareStrikePlan;
import net.vas.rdp.uav.mission.AircraftAssignmentRegistry;
import net.vas.rdp.uav.mission.AircraftRecord;
import net.vas.rdp.uav.mission.AircraftRegistry;
import net.vas.rdp.uav.mission.AircraftStatus;
import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.MissionManager;
import net.vas.rdp.uav.mission.MissionQueue;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.mission.MissionWaypoint;
import net.vas.rdp.uav.persistence.MissionNbtCodec;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.target.TargetLock;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSelector;
import net.vas.rdp.uav.target.TargetSpec;
import net.vas.rdp.uav.flight.SearchPattern;
import net.vas.rdp.uav.flight.SquareArea;
import net.vas.rdp.uav.flight.SquareStrikeConstraints;
import net.vas.rdp.uav.flight.TargetPolicy;
import net.vas.rdp.uav.flight.WeaponPolicy;
import net.vas.rdp.uav.flight.SquareStrikeState;
import net.vas.rdp.uav.flight.SquareStrikeStateMachine;
import net.vas.rdp.uav.sensor.KnowledgeKind;
import net.vas.rdp.uav.sensor.SensorFusion;
import net.vas.rdp.uav.sensor.SensorObservation;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RdpUavCoreTest {
    @Test
    public void missionLifecycleRejectsSkippedAndBackwardsTransitions() {
        RDPMission mission = mission(TargetSpec.point(10, 80, -4, TargetPriority.HIGH));
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAXI, 2);

        try {
            mission.transitionTo(MissionState.SCANNING, 3);
            fail("A mission must not skip native flight states");
        } catch (IllegalStateException expected) {
            // Expected validation boundary.
        }

        try {
            mission.transitionTo(MissionState.TAKEOFF, 1);
            fail("Mission ticks must be monotonic");
        } catch (IllegalArgumentException expected) {
            // Expected validation boundary.
        }
    }

    @Test
    public void missionQueueMembershipUsesMissionIdentityDuringRestore() {
        RDPMission original = mission(TargetSpec.manual("mod:target", TargetPriority.HIGH));
        RDPMission restored = MissionNbtCodec.read(MissionNbtCodec.write(original));
        MissionQueue queue = new MissionQueue();

        queue.enqueue(original);
        assertTrue(queue.contains(restored));
        assertTrue(queue.remove(restored));
        assertEquals(0, queue.size());

        RDPMission other = mission(TargetSpec.manual("other:target", TargetPriority.NORMAL));
        queue.enqueue(other);
        queue.enqueue(original);
        assertTrue(queue.remove(restored));
        assertEquals(1, queue.size());
    }

    @Test
    public void missionNbtRoundTripPreservesOperationalFields() {
        UUID target = UUID.randomUUID();
        RDPMission original = mission(TargetSpec.manual("mod:target", TargetPriority.CRITICAL));
        original.transitionTo(MissionState.DISPATCHED, 4);
        original.transitionTo(MissionState.TAKEOFF, 5);
        original.transitionTo(MissionState.CRUISE, 6);
        original.transitionTo(MissionState.SEARCHING, 7);
        original.transitionTo(MissionState.TARGET_ACQUIRED, 8);
        original.lockTarget(target, 9);
        original.setDimension(-1);
        original.setQueued(false);
        original.setRecoveryPolicy("LAND_AT_BASE");
        original.setReportId("report-1");

        RDPMission restored = MissionNbtCodec.read(MissionNbtCodec.write(original));
        assertEquals(original.getMissionId(), restored.getMissionId());
        assertEquals(original.getOwnerId(), restored.getOwnerId());
        assertEquals(original.getAircraftId(), restored.getAircraftId());
        assertEquals(original.getState(), restored.getState());
        assertEquals(target, restored.getTargetId());
        assertEquals(-1, restored.getDimension());
        assertFalse(restored.isQueued());
        assertEquals("LAND_AT_BASE", restored.getRecoveryPolicy());
        assertEquals("report-1", restored.getReportId());
    }

    @Test
    public void entityTargetBindingSurvivesReacquisitionAndPersistence() {
        UUID target = UUID.randomUUID();
        RDPMission mission = mission(TargetSpec.manual("mod:target", TargetPriority.HIGH));
        mission.reacquireTarget(target, 2);
        assertEquals(target, mission.getTargetId());
        RDPMission restored = MissionNbtCodec.read(MissionNbtCodec.write(mission));
        assertEquals(target, restored.getTargetId());
    }

    @Test
    public void squareStrikeAreaSurvivesPersistence() {
        RDPMission mission = new RDPMission(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "fpv", MissionType.SQUARE_STRIKE,
                TargetSpec.point(10, 70, 20, TargetPriority.HIGH), true,
                false, false, 256, 32, 1);
        mission.setSquareArea(64, 48);

        RDPMission restored = MissionNbtCodec.read(MissionNbtCodec.write(mission));

        assertTrue(restored.hasSquareArea());
        assertEquals(64.0D, restored.getSquareWidth(), 0.0D);
        assertEquals(48.0D, restored.getSquareDepth(), 0.0D);
    }

    @Test
    public void patrolRouteSurvivesMissionPersistence() {
        RDPMission mission = new RDPMission(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "reusable", MissionType.PATROL,
                TargetSpec.point(20, 80, 30, TargetPriority.NORMAL),
                false, false, false, 64, 8, 1);
        mission.setPatrolRoute(Arrays.asList(
                new MissionWaypoint(20, 30, 200),
                new MissionWaypoint(40, 50, 0)));

        RDPMission restored = MissionNbtCodec.read(MissionNbtCodec.write(mission));

        assertEquals(2, restored.getPatrolRoute().size());
        assertEquals(20.0D, restored.getPatrolRoute().get(0).getX(), 0.0D);
        assertEquals(200, restored.getPatrolRoute().get(0).getLoiterTicks());
        assertEquals(0, restored.getPatrolRoute().get(1).getLoiterTicks());
    }

    @Test
    public void legacyAcceptedLaunchMigratesToNonTerminalOutcomeState() {
        RDPMission mission = mission(TargetSpec.point(10, 70, 20, TargetPriority.HIGH));
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAKEOFF, 2);
        mission.transitionTo(MissionState.CRUISE, 3);
        mission.transitionTo(MissionState.SEARCHING, 4);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5);
        mission.lockPoint(6);
        mission.transitionTo(MissionState.DIRECT_LAUNCH_COMPLETE, 7);

        net.minecraft.nbt.NBTTagCompound legacy = MissionNbtCodec.write(mission);
        legacy.setInteger("schemaVersion", 1);
        legacy.setString("state", "DIRECT_LAUNCH_COMPLETE");

        RDPMission restored = MissionNbtCodec.read(legacy);

        assertEquals(MissionState.LAUNCH_ACCEPTED, restored.getState());
        assertFalse(restored.isTerminal());
    }

    @Test
    public void targetSpecsNormalizeAndRejectInvalidInput() {
        assertEquals("mod:target", TargetSpec.manual(" MOD:Target ", TargetPriority.NORMAL).getRegistryId());
        assertEquals("mod", TargetSpec.automatic(" Mod ", TargetPriority.NORMAL).getNamespace());
        assertEquals("srparasites",
                TargetSpec.modNamespace(" SRParasites ", TargetPriority.HIGH).getNamespace());
        assertEquals(12.5, TargetSpec.point(12.5, 80, -2, TargetPriority.NORMAL).getX(), 0);

        assertIllegalArgument(() -> TargetSpec.manual("target", TargetPriority.NORMAL));
        assertIllegalArgument(() -> TargetSpec.automatic("mod:bad", TargetPriority.NORMAL));
        assertIllegalArgument(() -> TargetSpec.point(Double.NaN, 80, 0, TargetPriority.NORMAL));
    }

    @Test
    public void targetPolicyExposesMachineReadableDecisionCodes() {
        net.vas.rdp.uav.target.TargetPolicy.Decision missing =
                net.vas.rdp.uav.target.TargetPolicy.builder().evaluate(null, null, null);
        assertFalse(missing.isAccepted());
        assertEquals(net.vas.rdp.uav.target.TargetPolicy.ResultCode.NO_ENTITY, missing.getCode());

        net.vas.rdp.uav.target.TargetPolicy.Decision included =
                net.vas.rdp.uav.target.TargetPolicy.builder()
                .include("hostile:target")
                .evaluate(null, null, null);
        assertEquals(net.vas.rdp.uav.target.TargetPolicy.ResultCode.NO_ENTITY, included.getCode());
    }

    @Test
    public void targetPolicySnapshotNormalizesAndFreezesSupportedFields() {
        net.vas.rdp.uav.target.TargetPolicy.Snapshot snapshot =
                net.vas.rdp.uav.target.TargetPolicy.builder()
                        .include(" Hostile:Target ", "hostile:target")
                        .require("minecraft:zombie")
                        .exclude("minecraft:creeper")
                        .snapshot();

        assertEquals(1, snapshot.getVersion());
        assertEquals("ENTITY", snapshot.getDomain());
        assertEquals(1, snapshot.getInclude().size());
        assertEquals("hostile:target", snapshot.getInclude().get(0));
        try {
            //noinspection DataFlowIssue
            snapshot.getInclude().add("minecraft:player");
            fail("Policy snapshot must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void customEntityFilterAcceptsHostileAndNamespacedTargets() {
        assertTrue(net.vas.rdp.uav.target.TargetClassifier.matchesHostileNamespace(
                "minecraft:zombie", "hostile"));
        assertTrue(net.vas.rdp.uav.target.TargetClassifier.matchesHostileNamespace(
                "techguns:gunner", "hostile"));
        assertTrue(net.vas.rdp.uav.target.TargetClassifier.isMissionRelevant(
                "mod:sentinel", "mod"));
        assertFalse(net.vas.rdp.uav.target.TargetClassifier.isMissionRelevant(
                "mod:sentinel", "other"));

        net.vas.rdp.uav.target.TargetPolicy.Snapshot snapshot =
                net.vas.rdp.uav.target.TargetPolicy.builder()
                        .include("hostile")
                        .require("mod:sentinel")
                        .exclude("minecraft:creeper")
                        .allowHostileVehicles(true)
                        .snapshot();

        assertEquals("hostile", snapshot.getInclude().get(0));
        assertEquals("mod:sentinel", snapshot.getRequire().get(0));
        assertEquals("minecraft:creeper", snapshot.getExclude().get(0));
        assertTrue(snapshot.isAllowVehicleTargets());
    }

    @Test
    public void targetSelectionHonorsManualIdentityAndAutomaticPriority() {
        TargetSelector<String> selector = new TargetSelector<>();
        List<TargetSelector.Candidate<String>> candidates = Arrays.asList(
                candidate("mod:near", 1, TargetPriority.NORMAL, "near"),
                candidate("mod:critical", 100, TargetPriority.CRITICAL, "critical"),
                candidate("other:critical", 0, TargetPriority.CRITICAL, "wrong"));

        assertEquals("critical", selector.select(
                TargetSpec.automatic("mod", TargetPriority.NORMAL), candidates));
        assertEquals("near", selector.select(
                TargetSpec.manual("mod:near", TargetPriority.HIGH), candidates));
        assertNull(selector.select(
                TargetSpec.manual("mod:missing", TargetPriority.HIGH), candidates));
    }

    @Test
    public void targetSelectionIsStableWhenPriorityAndRangeTie() {
        TargetSelector<String> selector = new TargetSelector<>();
        List<TargetSelector.Candidate<String>> candidates = Arrays.asList(
                candidate("mod:zeta", 4, TargetPriority.NORMAL, "zeta"),
                candidate("mod:alpha", 4, TargetPriority.NORMAL, "alpha"));
        assertEquals("alpha", selector.select(
                TargetSpec.automatic("mod", TargetPriority.NORMAL), candidates));
    }

    @Test
    public void targetLockRejectsOutOfOrderUpdates() {
        TargetLock lock = new TargetLock(UUID.randomUUID(), "mod:target", 0, 1, 2, 3, 10);
        lock.update(4, 5, 6, 12);
        assertEquals(12, lock.getLastSeenTick());
        assertEquals(4, lock.getX(), 0);
        assertEquals(1.5D, lock.getVelocityX(), 0.0001D);
        assertEquals(7.0D, lock.predictX(14), 0.0001D);
        assertEquals(TargetLock.Validation.VALID, lock.getValidation());

        try {
            lock.update(7, 8, 9, 11);
            fail("Target telemetry must be monotonic");
        } catch (IllegalArgumentException expected) {
            // Expected validation boundary.
        }
    }

    @Test
    public void targetLockPreservesOwnerMissionAssociationAndLegacyConstructor() {
        UUID target = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID mission = UUID.randomUUID();
        TargetLock lock = new TargetLock(target, "mod:target", 0,
                1, 2, 3, 10, owner, mission);

        assertEquals(target, lock.getEntityId());
        assertEquals(owner, lock.getLockOwnerId());
        assertEquals(mission, lock.getMissionId());

        TargetLock legacy = new TargetLock(target, "mod:target", 0, 1, 2, 3, 10);
        assertNull(legacy.getLockOwnerId());
        assertNull(legacy.getMissionId());
    }

    @Test
    public void sensorFusionMakesDisagreementExplicit() {
        SensorFusion fusion = new SensorFusion();
        SensorObservation visual = new SensorObservation("visual", KnowledgeKind.KNOWN,
                "entity", null, "mod:target", 1, 2, 3, 0.9D, 10);
        SensorObservation rf = new SensorObservation("rf", KnowledgeKind.INFERRED,
                "anomaly", null, null, 1, 2, 3, 0.8D, 10);

        SensorFusion.FusionResult result = fusion.fuse(Arrays.asList(visual, rf));
        assertTrue(result.hasDisagreement());
        assertEquals(0.45D, result.getConfidence(), 0.0001D);
        assertEquals(visual, result.getObservation());
    }

    @Test
    public void aircraftRegistriesPreventConflictingAssignments() {
        UUID aircraft = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID mission = UUID.randomUUID();
        AircraftAssignmentRegistry assignments = new AircraftAssignmentRegistry();
        assertTrue(assignments.reserve(aircraft, mission));
        assertFalse(assignments.reserve(aircraft, UUID.randomUUID()));
        assignments.release(aircraft, mission);
        assertNull(assignments.missionFor(aircraft));

        AircraftRegistry registry = new AircraftRegistry();
        registry.register(new AircraftRecord(aircraft, owner, 0, "switchblade", true, true));
        assertTrue(registry.canAssign(aircraft, owner));
        registry.assign(aircraft, mission, owner);
        assertFalse(registry.canAssign(aircraft, owner));
        registry.release(aircraft);
        assertTrue(registry.canAssign(aircraft, owner));
    }

    @Test
    public void missionManagerAvailabilityPredicateNamesItsRejectionMeaning() {
        UUID aircraft = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        MissionManager manager = new MissionManager();
        manager.registerAircraft(new AircraftRecord(aircraft, owner, 0,
                "switchblade", true, true));

        assertFalse(manager.isAircraftUnavailable(aircraft, owner));
        manager.findAircraft(aircraft).assign(UUID.randomUUID());
        assertTrue(manager.isAircraftUnavailable(aircraft, owner));
    }

    @Test
    public void aircraftRuntimeRefreshRecoversLoadedStateWithoutDroppingMissionOwnership() {
        UUID aircraftId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        AircraftRegistry registry = new AircraftRegistry();
        registry.register(new AircraftRecord(
                aircraftId, ownerId, 0, "switchblade", false, true));

        assertFalse(registry.canAssign(aircraftId, ownerId));
        assertTrue(registry.restoreAssignment(aircraftId, missionId, ownerId));
        assertEquals(missionId, registry.find(aircraftId).getMissionId());
        assertFalse(registry.restoreAssignment(
                aircraftId, UUID.randomUUID(), ownerId));
        assertFalse(registry.restoreAssignment(
                UUID.randomUUID(), missionId, ownerId));
        assertFalse(registry.restoreAssignment(
                aircraftId, missionId, UUID.randomUUID()));
        assertFalse(registry.canAssign(aircraftId, ownerId));
        registry.release(aircraftId, missionId);
        assertFalse(registry.canAssign(aircraftId, ownerId));

        assertTrue(registry.refreshRuntimeState(
                aircraftId, ownerId, 2, "switchblade-v2", true, true));
        assertTrue(registry.canAssign(aircraftId, ownerId));
        assertEquals(2, registry.find(aircraftId).getDimension());
        assertEquals("switchblade-v2", registry.find(aircraftId).getType());

        registry.assign(aircraftId, missionId, ownerId);
        assertTrue(registry.refreshRuntimeState(
                aircraftId, ownerId, 3, "switchblade-v3", true, true));
        assertEquals(missionId, registry.find(aircraftId).getMissionId());
        assertEquals(AircraftStatus.ON_MISSION, registry.find(aircraftId).getStatus());
        assertFalse(registry.canAssign(aircraftId, ownerId));

        assertFalse(registry.refreshRuntimeState(
                aircraftId, UUID.randomUUID(), 4, "switchblade", true, true));
        assertEquals(ownerId, registry.find(aircraftId).getOwnerId());
        assertEquals(3, registry.find(aircraftId).getDimension());

        registry.release(aircraftId, missionId);
        assertTrue(registry.canAssign(aircraftId, ownerId));
    }

    @Test
    public void rfEnergyClampsAndPersistsWithinCapacity() {
        UavEnergy energy = new UavEnergy(1000, 250, 200, 100);
        assertEquals(250, energy.receiveEnergy(300, false));
        assertEquals(350, energy.getEnergyStored());
        assertEquals(200, energy.drain(500));
        assertEquals(150, energy.getEnergyStored());

        NBTTagCompound tag = new NBTTagCompound();
        energy.write(tag);
        UavEnergy restored = new UavEnergy(1000, 250, 200, 0);
        restored.read(tag);
        assertEquals(150, restored.getEnergyStored());
        tag.setInteger("Energy", 5000);
        restored.read(tag);
        assertEquals(1000, restored.getEnergyStored());
    }

    @Test
    public void squareStrikeProducesAlternatingNativePasses() {
        SquareStrikePlan plan = new SquareStrikePlan(
                100, 140, -50, 80, 60, 0, 20, 30, 2);
        List<MissionNode> nodes = plan.createNodes();
        assertEquals(9, nodes.size());
        assertEquals(MissionNode.Type.FLY_TO, nodes.get(0).type);
        assertEquals(MissionNode.Type.FLY_TO, nodes.get(1).type);
        assertEquals(MissionNode.Type.ATTACK, nodes.get(2).type);
        assertEquals(MissionNode.Type.FLY_TO, nodes.get(3).type);
        assertEquals(MissionNode.Type.FLY_TO, nodes.get(4).type);
        assertEquals(MissionNode.Type.ATTACK, nodes.get(5).type);
        assertEquals(MissionNode.Type.ATTACK, nodes.get(8).type);
    }

    @Test
    public void oneWaySquareStrikeKeepsConfigurableGeometryAndTerminalPayload() {
        UavVehicleProfile oneWay = new UavVehicleProfile(
                UavExecutionProfile.ONE_WAY, 0, 0, 4096, 0, false, "SELF_DESTRUCT");
        SquareStrikePlan plan = new SquareStrikePlan(
                100, 140, -50, 80, 60, 0, 20, 30, 2);

        assertFalse(oneWay.isReusable());
        assertEquals("SELF_DESTRUCT", oneWay.getTerminalPayloadBehavior());
        assertEquals(9, plan.createNodes().size());
        assertEquals(80D, plan.getMaxX() - plan.getMinX(), 0D);
        assertEquals(60D, plan.getMaxZ() - plan.getMinZ(), 0D);
    }

    @Test
    public void squareStrikeConstraintsEnforceAreaAndMissionLimits() {
        SquareArea area = new SquareArea(100, -50, 200, 120);
        SquareStrikeConstraints constraints = new SquareStrikeConstraints(area, 80, 140,
                SearchPattern.BOUSTROPHEDON, TargetPolicy.HOSTILE_ONLY,
                WeaponPolicy.CONVENTIONAL_ONLY, 5, 2000, 3, 4, true, 0.25D);

        assertTrue(area.contains(0, -100));
        assertFalse(area.contains(-1, -111));
        assertTrue(constraints.allowsAltitude(100));
        assertFalse(constraints.allowsAltitude(150));
        assertTrue(constraints.allowsEngagement(2, 3));
        assertFalse(constraints.allowsEngagement(3, 3));
        assertTrue(constraints.timedOut(10, 2010));
    }

    @Test
    public void squareStrikeStateMachineSupportsOperatorInterrupts() {
        SquareStrikeStateMachine machine = new SquareStrikeStateMachine();
        machine.transitionTo(SquareStrikeState.VALIDATING);
        machine.transitionTo(SquareStrikeState.QUEUED);
        machine.transitionTo(SquareStrikeState.ASSIGNED);
        machine.override();
        assertEquals(SquareStrikeState.OPERATOR_OVERRIDE, machine.getState());
        machine.transitionTo(SquareStrikeState.PAUSED);
        machine.transitionTo(SquareStrikeState.RESUME_SEARCH);
        try {
            machine.transitionTo(SquareStrikeState.ENGAGEMENT_ACTIVE);
            fail("Override recovery must resume through search");
        } catch (IllegalStateException expected) {
            // Expected validation boundary.
        }
    }

    private static RDPMission mission(TargetSpec target) {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "switchblade", MissionType.RECONNAISSANCE, target,
                true, false, true, 256, 64, 0);
    }

    private static TargetSelector.Candidate<String> candidate(final String id,
                                                               final double distance,
                                                               final TargetPriority priority,
                                                               final String value) {
        return new TargetSelector.Candidate<String>() {
            public String getRegistryId() { return id; }
            public double getDistanceSquared() { return distance; }
            public boolean isValid() { return true; }
            public TargetPriority getPriority() { return priority; }
            public String getValue() { return value; }
        };
    }

    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected validation boundary.
        }
    }
}
