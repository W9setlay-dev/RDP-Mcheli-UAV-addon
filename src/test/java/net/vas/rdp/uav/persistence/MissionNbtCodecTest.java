package net.vas.rdp.uav.persistence;

import net.minecraft.nbt.NBTTagCompound;
import net.vas.rdp.uav.mission.ControlOwner;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.MissionExecutionStage;
import net.vas.rdp.uav.mission.MissionObjective;
import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.UUID;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MissionNbtCodecTest {
    @Test
    public void roundTripsOperatorControlOverrides() {
        for (ControlOwner owner : new ControlOwner[] {
                ControlOwner.MANUAL, ControlOwner.SUPERVISED_OPERATOR
        }) {
            RDPMission mission = mission();
            mission.transitionTo(net.vas.rdp.uav.mission.MissionState.DISPATCHED, 2L);
            mission.setControlOwner(owner, 3L);
            mission.setCruiseAltitudeY(192.0D);

            NBTTagCompound encoded = MissionNbtCodec.write(mission);
            RDPMission restored = MissionNbtCodec.read(encoded);

            assertEquals(owner, restored.getControlOwner());
            assertEquals(192.0D, restored.getCruiseAltitudeY(), 0.0D);
            restored.validateStateConsistency();
        }
    }

    @Test
    public void missingLegacyCruiseAltitudeRemainsUnresolvedUntilServerDispatch() {
        NBTTagCompound encoded = MissionNbtCodec.write(mission());
        encoded.setInteger("schemaVersion", 9);
        RDPMission restored = MissionNbtCodec.read(encoded);
        assertFalse(restored.hasCruiseAltitudeY());
        assertEquals(120.0D, restored.resolveCruiseAltitudeY(120.0D), 0.0D);
        assertEquals(120.0D, restored.resolveCruiseAltitudeY(180.0D), 0.0D);
    }

    @Test
    public void legacyTerminalProjectionMigratesToTargetTracking() {
        RDPMission legacy = RDPMission.restore(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "TEST_UAV", MissionType.STRIKE,
                TargetSpec.point(0, 32, 0, TargetPriority.NORMAL), true, false,
                false, 64, 32, 1, MissionState.ATTACK_TARGET, null, 2,
                null, null);
        legacy.restoreExecutionMetadata(MissionExecutionStage.TERMINAL, null, null);
        NBTTagCompound encoded = MissionNbtCodec.write(legacy);
        encoded.setInteger("schemaVersion", 9);

        RDPMission restored = MissionNbtCodec.read(encoded);

        assertEquals(MissionExecutionStage.TARGET_TRACK, restored.getExecutionStage());
    }

    @Test
    public void operatorAuthorizationWaitDeadlinePausesAcrossPersistence() {
        RDPMission source = strikeMission();
        source.transitionTo(MissionState.DISPATCHED, 2L);
        source.transitionTo(MissionState.TAKEOFF, 3L);
        source.transitionTo(MissionState.CRUISE, 4L);
        source.transitionTo(MissionState.SEARCHING, 5L);
        source.transitionTo(MissionState.TARGET_ACQUIRED, 6L);
        source.transitionTo(MissionState.TARGET_LOCKED, 7L);

        RDPMission restored = MissionNbtCodec.read(MissionNbtCodec.write(source));
        assertTrue(restored.isMissionDeadlineExempt());
        assertFalse(restored.isTimedOut(12_007L, 12_000L));

        restored.authorizeDestructiveAction(12_007L);
        restored.transitionTo(MissionState.ATTACK_TARGET, 12_007L);
        assertFalse(restored.isTimedOut(24_000L, 12_000L));
        assertTrue(restored.isTimedOut(24_001L, 12_000L));
    }

    @Test
    public void legacyAuthorizationWaitStartsDeadlineAccountingAtRestore() {
        RDPMission source = strikeMission();
        source.transitionTo(MissionState.DISPATCHED, 2L);
        source.transitionTo(MissionState.TAKEOFF, 3L);
        source.transitionTo(MissionState.CRUISE, 4L);
        source.transitionTo(MissionState.SEARCHING, 5L);
        source.transitionTo(MissionState.TARGET_ACQUIRED, 6L);
        source.transitionTo(MissionState.TARGET_LOCKED, 7L);

        NBTTagCompound legacy = MissionNbtCodec.write(source);
        legacy.setInteger("schemaVersion", 10);
        legacy.removeTag("pausedDeadlineTicks");
        legacy.removeTag("deadlineExemptSinceTick");

        RDPMission restored = MissionNbtCodec.read(legacy);
        assertTrue(restored.isMissionDeadlineExempt());
        assertFalse(restored.isTimedOut(12_007L, 12_000L));
        restored.authorizeDestructiveAction(12_007L);
        restored.transitionTo(MissionState.ATTACK_TARGET, 12_007L);
        assertFalse(restored.isTimedOut(24_000L, 12_000L));
        assertTrue(restored.isTimedOut(24_001L, 12_000L));
    }

    @Test
    public void composedMissionObjectivesRoundTripInSelectionOrder() {
        RDPMission source = mission();
        UUID currentId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        source.setSourceDesignationId(currentId);
        source.setFollowOnObjectives(Arrays.asList(
                new MissionObjective(firstId,
                        TargetSpec.point(10.0D, 32.0D, 20.0D, TargetPriority.HIGH), null),
                new MissionObjective(secondId,
                        TargetSpec.manual("minecraft:zombie", TargetPriority.HIGH), entityId)));

        RDPMission restored = MissionNbtCodec.read(MissionNbtCodec.write(source));

        assertEquals(currentId, restored.getSourceDesignationId());
        assertEquals(2, restored.getFollowOnObjectives().size());
        assertEquals(firstId, restored.getFollowOnObjectives().get(0).getDesignationId());
        assertEquals(10.0D, restored.getFollowOnObjectives().get(0)
                .getTargetSpec().getX(), 0.0D);
        assertEquals(secondId, restored.getFollowOnObjectives().get(1).getDesignationId());
        assertEquals(entityId, restored.getFollowOnObjectives().get(1).getTargetEntityId());
    }

    @Test
    public void legacyMissionWithoutComposedObjectivesRestoresEmptySequence() {
        NBTTagCompound legacy = MissionNbtCodec.write(mission());
        legacy.setInteger("schemaVersion", 11);
        legacy.removeTag("followOnObjectives");

        assertTrue(MissionNbtCodec.read(legacy).getFollowOnObjectives().isEmpty());
    }

    private static RDPMission mission() {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "TEST_UAV", MissionType.STRIKE,
                TargetSpec.point(0.0D, 32.0D, 0.0D, TargetPriority.NORMAL),
                false, false, false, 64.0D, 32.0D, 1L);
    }

    private static RDPMission strikeMission() {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "TEST_UAV", MissionType.STRIKE,
                TargetSpec.point(0.0D, 32.0D, 0.0D, TargetPriority.HIGH),
                true, false, false, 64.0D, 32.0D, 1L);
    }
}
