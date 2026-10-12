package net.vas.rdp.uav.mission;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MissionExecutionStageTest {
    @Test
    public void genericAttackPreparationDoesNotEnableTerminalExecution() {
        assertEquals(MissionExecutionStage.TARGET_TRACK,
                MissionExecutionStage.fromState(MissionState.APPROACHING, MissionType.STRIKE));
        assertEquals(MissionExecutionStage.TARGET_TRACK,
                MissionExecutionStage.fromState(MissionState.ATTACK_TARGET, MissionType.STRIKE));
        assertEquals(MissionExecutionStage.TARGET_TRACK,
                MissionExecutionStage.fromState(MissionState.SCANNING, MissionType.STRIKE));
        assertEquals(MissionExecutionStage.TERMINAL,
                MissionExecutionStage.fromState(MissionState.KAMIKADZE_TARGET, MissionType.STRIKE));
    }
}
