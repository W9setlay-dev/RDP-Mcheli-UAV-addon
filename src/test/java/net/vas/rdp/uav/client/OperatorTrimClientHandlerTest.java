package net.vas.rdp.uav.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OperatorTrimClientHandlerTest {
    @Test
    public void lateralKeysProduceSignedRollCorrectionAndNeutralizeTogether() {
        assertEquals(-1.0F, OperatorTrimClientHandler.lateralRollCorrection(true, false), 0.0F);
        assertEquals(1.0F, OperatorTrimClientHandler.lateralRollCorrection(false, true), 0.0F);
        assertEquals(0.0F, OperatorTrimClientHandler.lateralRollCorrection(false, false), 0.0F);
        assertEquals(0.0F, OperatorTrimClientHandler.lateralRollCorrection(true, true), 0.0F);
    }
}
