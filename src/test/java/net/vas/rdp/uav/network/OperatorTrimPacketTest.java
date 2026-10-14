package net.vas.rdp.uav.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OperatorTrimPacketTest {
    @Test
    public void operatorTrimPacketRoundTripsRollCorrection() {
        ByteBuf payload = Unpooled.buffer();
        payload.writeLong(1L);
        payload.writeLong(2L);
        payload.writeLong(3L);
        payload.writeLong(4L);
        payload.writeLong(5L);
        payload.writeLong(6L);
        payload.writeFloat(1.25F);
        payload.writeFloat(-2.5F);
        payload.writeFloat(0.75F);

        RdpUavNetwork.OperatorTrim message = new RdpUavNetwork.OperatorTrim();
        message.fromBytes(payload);
        assertEquals(0.75F, message.getRollDelta(), 0.0F);

        ByteBuf encoded = Unpooled.buffer();
        message.toBytes(encoded);
        assertEquals(60, encoded.readableBytes());
        assertEquals(1.25F, encoded.getFloat(48), 0.0F);
        assertEquals(-2.5F, encoded.getFloat(52), 0.0F);
        assertEquals(0.75F, encoded.getFloat(56), 0.0F);
        payload.release();
        encoded.release();
    }

    @Test
    public void operatorTrimPacketDefaultsMissingLegacyRollToNeutral() {
        ByteBuf legacyPayload = Unpooled.buffer();
        legacyPayload.writeLong(1L);
        legacyPayload.writeLong(2L);
        legacyPayload.writeLong(3L);
        legacyPayload.writeLong(4L);
        legacyPayload.writeLong(5L);
        legacyPayload.writeLong(6L);
        legacyPayload.writeFloat(1.25F);
        legacyPayload.writeFloat(-2.5F);

        RdpUavNetwork.OperatorTrim message = new RdpUavNetwork.OperatorTrim();
        message.fromBytes(legacyPayload);

        assertEquals(0.0F, message.getRollDelta(), 0.0F);
        legacyPayload.release();
    }
}
