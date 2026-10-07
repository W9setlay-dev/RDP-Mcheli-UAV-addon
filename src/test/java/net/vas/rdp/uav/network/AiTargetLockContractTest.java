package net.vas.rdp.uav.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class AiTargetLockContractTest {
    @Test
    public void emptyNonClearIntentIsInvalidRatherThanAnUnlock() {
        RdpUavNetwork.AiTargetLock request =
                new RdpUavNetwork.AiTargetLock(null, false);

        assertFalse(request.isClearRequest());
        assertFalse(request.hasTargetId());
        assertTrue(request.isInvalidLockRequest());
    }

    @Test
    public void onlyTheExplicitClearFlagCreatesAnUnlockRequest() {
        RdpUavNetwork.AiTargetLock clear =
                new RdpUavNetwork.AiTargetLock(null, true);
        RdpUavNetwork.AiTargetLock lock =
                new RdpUavNetwork.AiTargetLock(UUID.randomUUID(), false);

        assertTrue(clear.isClearRequest());
        assertFalse(clear.isInvalidLockRequest());
        assertFalse(lock.isClearRequest());
        assertTrue(lock.hasTargetId());
        assertFalse(lock.isInvalidLockRequest());
    }

    @Test
    public void missionSuggestionPacketPreservesExactEntityUuid() {
        String missionId = UUID.randomUUID().toString();
        UUID targetId = UUID.randomUUID();
        RdpUavNetwork.AiTargetLock.EntityTargetAssignment request =
                new RdpUavNetwork.AiTargetLock.EntityTargetAssignment(missionId, targetId);
        ByteBuf buffer = Unpooled.buffer();
        try {
            request.toBytes(buffer);
            RdpUavNetwork.AiTargetLock.EntityTargetAssignment decoded =
                    new RdpUavNetwork.AiTargetLock.EntityTargetAssignment();
            decoded.fromBytes(buffer);

            assertEquals(missionId, decoded.getMissionId());
            assertEquals(targetId, decoded.getTargetId());
        } finally {
            buffer.release();
        }
    }
}
