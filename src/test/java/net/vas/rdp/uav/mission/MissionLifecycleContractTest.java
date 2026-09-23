package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetPriority;
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
        assertFalse(mission.getState() == MissionState.KAMIKADZE_DETONATED);
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

    private static RDPMission mission(long createdTick) {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "switchblade", MissionType.RECONNAISSANCE,
                TargetSpec.point(0, 80, 0, TargetPriority.NORMAL),
                false, false, false, 64, 32, createdTick);
    }
}
