package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetLock;
import net.vas.rdp.uav.target.TargetSelectionMode;
import net.vas.rdp.uav.target.TargetSpec;
import net.vas.rdp.uav.persistence.MissionNbtCodec;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class MissionLifecycleContractTest {
    @Test
    public void repeatedStateTransitionIsIdempotentAndDoesNotExtendActivity() {
        RDPMission mission = mission(10);
        mission.transitionTo(MissionState.DISPATCHED, 11);
        mission.transitionTo(MissionState.DISPATCHED, 99);

        assertEquals(MissionState.DISPATCHED, mission.getState());
        assertEquals(11L, mission.getLastUpdateTick());
        assertEquals("STATE_TRANSITION", mission.getLastTransitionReason());
    }

    @Test
    public void abortIsIdempotentAndRetainsTheFirstTerminalReason() {
        RDPMission mission = mission(0);
        mission.abort("OPERATOR_ABORT", 4);
        mission.abort("DUPLICATE_ABORT", 5);

        assertEquals(MissionState.ABORTED, mission.getState());
        assertEquals("OPERATOR_ABORT", mission.getLastTransitionReason());
        assertTrue(mission.isTerminal());
    }

    @Test
    public void failureIsIdempotentButOtherTerminalOperationsAreRejected() {
        RDPMission mission = mission(0);
        mission.fail("NO_AIRCRAFT", 3);
        mission.fail("DUPLICATE_FAILURE", 4);

        assertEquals(MissionState.FAILED, mission.getState());
        assertEquals("NO_AIRCRAFT", mission.getFailureReason());
        assertEquals("NO_AIRCRAFT", mission.getLastTransitionReason());
        try {
            mission.abort("TOO_LATE", 5);
            fail("A failed mission must not be aborted");
        } catch (IllegalStateException expected) {
            // Terminal state is monotonic.
        }
    }

    @Test
    public void hardDeadlineIsIndependentOfRecoveryStateRefreshes() {
        RDPMission mission = mission(100);
        mission.transitionTo(MissionState.WAITING_FOR_WORLD, 101);
        mission.defer("AIRCRAFT_NOT_FOUND", MissionState.WAITING_FOR_WORLD, 102);
        mission.transitionTo(MissionState.REPLAN, 103);

        assertTrue(mission.isTimedOut(110, 10));
        assertFalse(mission.isTimedOut(109, 10));
    }

    @Test
    public void completeIsIdempotent() {
        RDPMission mission = mission(0);
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAKEOFF, 2);
        mission.transitionTo(MissionState.CRUISE, 3);
        mission.transitionTo(MissionState.SEARCHING, 4);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5);
        mission.lockPoint(6);
        mission.transitionTo(MissionState.APPROACHING, 7);
        mission.transitionTo(MissionState.ON_TARGET, 8);
        mission.transitionTo(MissionState.SCANNING, 9);
        mission.transitionTo(MissionState.REPORTING, 10);
        mission.transitionTo(MissionState.HOLDING, 11);
        mission.transitionTo(MissionState.RETURNING, 12);
        mission.transitionTo(MissionState.LANDING, 13);
        mission.transitionTo(MissionState.PARKING, 14);
        mission.complete(15);
        mission.complete(16);

        assertEquals(MissionState.COMPLETE, mission.getState());
        assertEquals("MISSION_COMPLETE", mission.getLastTransitionReason());
    }

    @Test
    public void lostKamikazeAircraftIsNotReportedAsDetonated() {
        RDPMission mission = mission(0);
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAKEOFF, 2);
        mission.transitionTo(MissionState.CRUISE, 3);
        mission.transitionTo(MissionState.SEARCHING, 4);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5);
        mission.lockTarget(UUID.randomUUID(), 6);
        mission.transitionTo(MissionState.ATTACK_TARGET, 7);
        mission.transitionTo(MissionState.KAMIKADZE_TARGET, 8);
        mission.transitionTo(MissionState.AIRCRAFT_LOST, 9,
                "AIRCRAFT_LOST_BEFORE_EFFECT_CONFIRMATION");

        assertTrue(mission.isTerminal());
        assertEquals(MissionState.AIRCRAFT_LOST, mission.getState());
        assertNotSame(MissionState.KAMIKADZE_DETONATED, mission.getState());
    }

    @Test
    public void terminalCommitMakesAbortImpossible() {
        RDPMission mission = mission(0);
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAKEOFF, 2);
        mission.transitionTo(MissionState.CRUISE, 3);
        mission.transitionTo(MissionState.SEARCHING, 4);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5);
        mission.lockTarget(UUID.randomUUID(), 6);
        mission.transitionTo(MissionState.KAMIKADZE_TARGET, 7);

        try {
            mission.abort("OPERATOR_ABORT", 8);
            fail("Terminal commitment must reject abort");
        } catch (IllegalStateException expected) {
            assertEquals("ABORT_IMPOSSIBLE_AFTER_TERMINAL_COMMIT",
                    expected.getMessage());
        }
        assertEquals(MissionState.KAMIKADZE_TARGET, mission.getState());
    }

    @Test
    public void terminalCommitRejectsTargetReplacementAndAreaClear() {
        RDPMission mission = mission(0);
        UUID committedTarget = UUID.randomUUID();
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAKEOFF, 2);
        mission.transitionTo(MissionState.CRUISE, 3);
        mission.transitionTo(MissionState.SEARCHING, 4);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5);
        mission.lockTarget(committedTarget, 6);
        mission.transitionTo(MissionState.KAMIKADZE_TARGET, 7);

        try {
            mission.lockTarget(UUID.randomUUID(), 8);
            fail("A terminal target must not be replaced by a different UUID");
        } catch (IllegalStateException expected) {
            assertEquals("Terminal target cannot be replaced", expected.getMessage());
        }

        try {
            mission.lockPoint(8);
            fail("A terminal target must not be cleared by a point lock");
        } catch (IllegalStateException expected) {
            assertEquals("Terminal target cannot be replaced", expected.getMessage());
        }

        try {
            mission.applyOperatorAreaLock(1.0D, 2.0D, 3.0D, 8);
            fail("A terminal target must not be cleared by an area lock");
        } catch (IllegalStateException expected) {
            assertEquals("Terminal target cannot be replaced", expected.getMessage());
        }

        assertEquals(committedTarget, mission.getTargetId());
        assertEquals(MissionState.KAMIKADZE_TARGET, mission.getState());
    }

    @Test
    public void targetClaimOwnershipIsSingleWriterAndForceReassignsTheTarget() {
        MissionCoordinator coordinator = new MissionCoordinator();
        UUID targetId = UUID.randomUUID();
        UUID firstAircraft = UUID.randomUUID();
        UUID secondAircraft = UUID.randomUUID();

        assertTrue(coordinator.claim(firstAircraft, targetId));
        assertTrue(coordinator.isAssigned(firstAircraft, targetId));
        assertFalse(coordinator.claim(secondAircraft, targetId));

        assertTrue(coordinator.claim(secondAircraft, targetId, null, null, true));
        assertTrue(coordinator.isAssigned(secondAircraft, targetId));
        assertFalse(coordinator.isAssigned(firstAircraft, targetId));
        assertFalse(coordinator.claim(firstAircraft, targetId));
    }

    @Test
    public void controlOwnerFollowsAuthoritativeMissionState() {
        RDPMission mission = mission(0);
        assertEquals(ControlOwner.MISSION_AI, mission.getControlOwner());

        mission.transitionTo(MissionState.WAITING_FOR_WORLD, 1);
        assertEquals(ControlOwner.RECOVERY, mission.getControlOwner());

        mission.transitionTo(MissionState.REPLAN, 2);
        mission.transitionTo(MissionState.SEARCHING, 3);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 4);
        mission.lockTarget(UUID.randomUUID(), 5);
        mission.transitionTo(MissionState.ATTACK_TARGET, 6);
        mission.transitionTo(MissionState.KAMIKADZE_TARGET, 7);
        assertEquals(ControlOwner.TERMINAL_GUIDANCE, mission.getControlOwner());

        mission.transitionTo(MissionState.KAMIKADZE_DETONATED, 8);
        assertEquals(ControlOwner.SAFETY, mission.getControlOwner());
    }

    @Test
    public void supervisedOperatorControlSurvivesPersistenceRoundTrip() {
        RDPMission source = mission(0);
        source.transitionTo(MissionState.WAITING_FOR_TARGET, 1);
        source.transitionTo(MissionState.SEARCHING, 2);
        source.enterSupervisedOperatorMode(3);

        RDPMission restored = MissionNbtCodec.read(MissionNbtCodec.write(source));

        assertEquals(ControlOwner.SUPERVISED_OPERATOR, restored.getControlOwner());
    }

    @Test
    public void lastKnownTargetPositionPersistsWithoutRestoringLiveLockEvidence() {
        RDPMission source = mission(0);
        UUID targetId = UUID.randomUUID();
        source.transitionTo(MissionState.DISPATCHED, 1);
        source.transitionTo(MissionState.TAKEOFF, 2);
        source.transitionTo(MissionState.CRUISE, 3);
        source.transitionTo(MissionState.SEARCHING, 4);
        source.transitionTo(MissionState.TARGET_ACQUIRED, 5);
        source.lockTarget(targetId, 6);
        source.updateTargetLock("minecraft:zombie", 2, 15.5D, 72.0D, -9.25D, 7);

        RDPMission restored = MissionNbtCodec.read(MissionNbtCodec.write(source));

        assertEquals(targetId, restored.getTargetId());
        assertNull(restored.getTargetLock());
        assertNotNull(restored.getLastKnownTargetPosition());
        assertEquals(targetId, restored.getLastKnownTargetPosition().getTargetId());
        assertEquals(2, restored.getLastKnownTargetPosition().getDimension());
        assertEquals(15.5D, restored.getLastKnownTargetPosition().getX(), 0.0D);
        assertEquals(72.0D, restored.getLastKnownTargetPosition().getY(), 0.0D);
        assertEquals(-9.25D, restored.getLastKnownTargetPosition().getZ(), 0.0D);
        assertEquals(7L, restored.getLastKnownTargetPosition().getObservedTick());
    }

    @Test
    public void targetLockInvalidationExpiresAndRecoversOnlyForTheSameIdentity() {
        RDPMission mission = mission(0);
        UUID targetId = UUID.randomUUID();
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAKEOFF, 2);
        mission.transitionTo(MissionState.CRUISE, 3);
        mission.transitionTo(MissionState.SEARCHING, 4);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5);
        mission.lockTarget(targetId, 6);

        assertTrue(mission.markTargetLockUnavailable("TARGET_NOT_LOADED", false));
        assertEquals(TargetLock.Validation.LOST,
                mission.getTargetLock().getValidation());
        assertFalse(mission.markTargetLockUnavailable("TARGET_NOT_LOADED", false));
        assertTrue(mission.markTargetLockUnavailable("TARGET_STALE", true));
        assertEquals(TargetLock.Validation.EXPIRED,
                mission.getTargetLock().getValidation());

        RDPMission restored = MissionNbtCodec.read(MissionNbtCodec.write(mission));
        assertEquals(targetId, restored.getTargetId());
        assertNull(restored.getTargetLock());
        assertEquals("TARGET_STALE", restored.getLastTargetReason());
        assertFalse(restored.markTargetLockUnavailable("TARGET_STALE", true));

        restored.updateTargetLock("minecraft:zombie", 0, 3.0D, 65.0D, 4.0D, 7);
        assertEquals(TargetLock.Validation.VALID,
                restored.getTargetLock().getValidation());
        assertNull(restored.getLastTargetReason());
    }

    @Test
    public void restoredTerminalMissionRetainsSafetyOwnership() {
        RDPMission source = mission(0);
        source.abort("OPERATOR_ABORT", 1);

        RDPMission restored = RDPMission.restore(source.getMissionId(), source.getOwnerId(),
                source.getAircraftId(), source.getAircraftType(), source.getMissionType(),
                source.getTargetSpec(), source.isAttack(), source.isEliminateAllCost(),
                source.isDeepScan(), source.getSearchRadius(), source.getObservationDistance(),
                source.getCreatedTick(), source.getState(), source.getTargetId(),
                source.getLastUpdateTick(), source.getFailureReason(), source.getReportId());

        assertEquals(ControlOwner.SAFETY, restored.getControlOwner());
    }

    @Test
    public void transitionReasonSurvivesPersistenceRoundTrip() {
        RDPMission source = mission(12);
        source.abort("OPERATOR_ABORT", 18);

        NBTTagCompound encoded = MissionNbtCodec.write(source);
        RDPMission restored = MissionNbtCodec.read(encoded);

        assertEquals(MissionState.ABORTED, restored.getState());
        assertEquals("OPERATOR_ABORT", restored.getLastTransitionReason());
    }

    @Test
    public void executionStageAndLinkedRetaskIdsSurvivePersistence() {
        UUID owner = UUID.randomUUID();
        UUID aircraft = UUID.randomUUID();
        RDPMission recon = new RDPMission(UUID.randomUUID(), owner, aircraft,
                "switchblade", MissionType.RECONNAISSANCE,
                TargetSpec.point(0, 80, 0, TargetPriority.HIGH), false,
                false, false, 256, 32, 0);
        recon.transitionTo(MissionState.DISPATCHED, 1);
        recon.transitionTo(MissionState.TAKEOFF, 2);
        recon.transitionTo(MissionState.CRUISE, 3);
        recon.transitionTo(MissionState.SEARCHING, 4);
        recon.transitionTo(MissionState.TARGET_ACQUIRED, 5);
        recon.lockPoint(6);
        recon.transitionTo(MissionState.APPROACHING, 7);
        recon.transitionTo(MissionState.ON_TARGET, 8);
        recon.transitionTo(MissionState.SCANNING, 9);
        recon.transitionTo(MissionState.REPORTING, 10);
        recon.transitionTo(MissionState.HOLDING, 11);
        RDPMission child = new RDPMission(UUID.randomUUID(), owner, aircraft,
                "switchblade", MissionType.STRIKE,
                TargetSpec.manual("minecraft:zombie", TargetPriority.HIGH), true,
                false, false, 256, 64, 12);
        recon.linkChildMission(child);

        RDPMission restoredParent = MissionNbtCodec.read(MissionNbtCodec.write(recon));
        RDPMission restoredChild = MissionNbtCodec.read(MissionNbtCodec.write(child));
        assertEquals(MissionExecutionStage.STATION_HOLD, restoredParent.getExecutionStage());
        assertEquals(child.getMissionId(), restoredParent.getChildMissionId());
        assertEquals(recon.getMissionId(), restoredChild.getParentMissionId());
    }

    @Test
    public void olderMissionWithoutExecutionStageMigratesFromItsState() {
        RDPMission source = mission(0);
        source.transitionTo(MissionState.WAITING_FOR_WORLD, 1);
        NBTTagCompound legacy = MissionNbtCodec.write(source);
        legacy.setInteger("schemaVersion", 6);
        legacy.removeTag("executionStage");
        legacy.removeTag("parentMissionId");
        legacy.removeTag("childMissionId");

        RDPMission restored = MissionNbtCodec.read(legacy);

        assertEquals(MissionExecutionStage.TRANSIT, restored.getExecutionStage());
        assertNull(restored.getParentMissionId());
        assertNull(restored.getChildMissionId());
    }

    @Test
    public void swarmAssociationSurvivesMissionPersistence() {
        RDPMission source = mission(0);
        UUID swarmId = UUID.randomUUID();
        source.setSwarmId(swarmId);

        RDPMission restored = MissionNbtCodec.read(MissionNbtCodec.write(source));

        assertEquals(swarmId, restored.getSwarmId());
    }

    @Test
    public void legacyTargetSelectionMetadataDefaultsToTheSavedIdentity() {
        RDPMission source = new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "switchblade", MissionType.RECONNAISSANCE,
                TargetSpec.manual("mod:target", TargetPriority.HIGH),
                false, false, false, 64, 32, 0L);
        NBTTagCompound legacy = MissionNbtCodec.write(source);
        legacy.removeTag("targetMode");
        legacy.removeTag("priority");

        RDPMission restored = MissionNbtCodec.read(legacy);

        assertEquals(TargetSelectionMode.MANUAL, restored.getTargetSelectionMode());
        assertEquals(TargetPriority.NORMAL, restored.getTargetPriority());
        assertEquals("mod:target", restored.getTargetSpec().getRegistryId());
    }

    @Test
    public void persistenceRetainsAndValidatesControlProjection() {
        RDPMission source = mission(0);
        source.transitionTo(MissionState.WAITING_FOR_WORLD, 1);

        NBTTagCompound encoded = MissionNbtCodec.write(source);
        RDPMission restored = MissionNbtCodec.read(encoded);

        assertEquals(MissionPhase.RECOVERY, restored.getPhase());
        assertEquals(ControlOwner.RECOVERY, restored.getControlOwner());

        encoded.setString("controlOwner", ControlOwner.MISSION_AI.name());
        try {
            MissionNbtCodec.read(encoded);
            fail("Tampered control ownership must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Mission control owner does not match state",
                    expected.getMessage());
        }
    }

    @Test
    public void malformedPersistenceRecordFailsClosed() {
        NBTTagCompound encoded = MissionNbtCodec.write(mission(0));
        encoded.setString("missionId", "not-a-uuid");

        try {
            MissionNbtCodec.read(encoded);
            fail("Malformed mission UUID must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Invalid mission NBT UUID: missionId", expected.getMessage());
        }
    }

    @Test
    public void payloadLaunchRequiresExplicitEffectConfirmation() {
        RDPMission mission = mission(0);
        mission.transitionTo(MissionState.DISPATCHED, 1);
        mission.transitionTo(MissionState.TAKEOFF, 2);
        mission.transitionTo(MissionState.CRUISE, 3);
        mission.transitionTo(MissionState.SEARCHING, 4);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 5);
        mission.lockTarget(UUID.randomUUID(), 6);
        mission.transitionTo(MissionState.KAMIKADZE_TARGET, 7);

        mission.markKamikazePayloadLaunched(8);

        assertTrue(mission.isKamikazePayloadLaunched());
        assertEquals(MissionState.KAMIKADZE_TARGET, mission.getState());
        // Native fire acceptance is not an effect observation.
        assertNotEquals(MissionState.KAMIKADZE_DETONATED, mission.getState());
        mission.confirmKamikazeEffect(9);
        assertEquals(MissionState.KAMIKADZE_DETONATED, mission.getState());
    }

    private static RDPMission mission(long createdTick) {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "switchblade", MissionType.RECONNAISSANCE,
                TargetSpec.point(0, 80, 0, TargetPriority.NORMAL),
                false, false, false, 64, 32, createdTick);
    }
}
