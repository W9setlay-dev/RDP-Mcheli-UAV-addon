package net.vas.rdp.uav;

import com.norwood.mcheli.wingman.mission.MissionNode;
import net.vas.rdp.uav.energy.UavEnergy;
import net.vas.rdp.uav.flight.SquareStrikePlan;
import net.vas.rdp.uav.mission.AircraftAssignmentRegistry;
import net.vas.rdp.uav.mission.AircraftRecord;
import net.vas.rdp.uav.mission.AircraftRegistry;
import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.persistence.MissionNbtCodec;
import net.vas.rdp.uav.target.TargetLock;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSelector;
import net.vas.rdp.uav.target.TargetSpec;
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
    public void targetSpecsNormalizeAndRejectInvalidInput() {
        assertEquals("mod:target", TargetSpec.manual(" MOD:Target ", TargetPriority.NORMAL).getRegistryId());
        assertEquals("mod", TargetSpec.automatic(" Mod ", TargetPriority.NORMAL).getNamespace());
        assertEquals(12.5, TargetSpec.point(12.5, 80, -2, TargetPriority.NORMAL).getX(), 0);

        assertIllegalArgument(new Runnable() {
            public void run() { TargetSpec.manual("target", TargetPriority.NORMAL); }
        });
        assertIllegalArgument(new Runnable() {
            public void run() { TargetSpec.automatic("mod:bad", TargetPriority.NORMAL); }
        });
        assertIllegalArgument(new Runnable() {
            public void run() { TargetSpec.point(Double.NaN, 80, 0, TargetPriority.NORMAL); }
        });
    }

    @Test
    public void targetSelectionHonorsManualIdentityAndAutomaticPriority() {
        TargetSelector<String> selector = new TargetSelector<String>();
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
