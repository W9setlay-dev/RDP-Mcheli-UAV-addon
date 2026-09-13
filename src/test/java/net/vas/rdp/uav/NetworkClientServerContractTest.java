package net.vas.rdp.uav;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.vas.rdp.uav.client.GuiMissionControl;
import net.vas.rdp.uav.network.RdpUavNetwork;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Client/server protocol tests using the real Forge message implementations.
 *
 * <p>Forge owns thread scheduling and {@code MessageContext}, so those
 * integration points require a live server. Packet serialization is the
 * deterministic contract that must hold on both sides of that boundary.</p>
 */
public class NetworkClientServerContractTest {
    @Test
    public void clientCreateRequestSurvivesUtf8EncodeDecodeRoundTrip() {
        RdpUavNetwork.CreateMission sent =
                new RdpUavNetwork.CreateMission("mcheli:目标", true, false);
        RdpUavNetwork.CreateMission received = roundTrip(sent,
                new RdpUavNetwork.CreateMission());
        assertSameWirePayload(sent, received);
    }

    @Test
    public void clientControlRequestsPreserveMissionIdentity() {
        assertSameWirePayload(new RdpUavNetwork.AbortMission("mission-42"),
                new RdpUavNetwork.AbortMission());
        assertSameWirePayload(new RdpUavNetwork.AuthorizeAttack("mission-42"),
                new RdpUavNetwork.AuthorizeAttack());
        assertSameWirePayload(new RdpUavNetwork.KamikazeAttack("mission-42"),
                new RdpUavNetwork.KamikazeAttack());
    }

    @Test
    public void serverMissionSnapshotDecodesRowsAndIdsForClientGui() {
        ByteBuf wire = Unpooled.buffer();
        try {
            writeString(wire, "mission-1");
            writeString(wire, "mission-1  TARGET_LOCKED  switchblade");
            writeString(wire, "mission-2");
            writeString(wire, "mission-2  COMPLETE  switchblade");
            ByteBuf framed = Unpooled.buffer();
            framed.writeInt(2).writeBytes(wire);

            RdpUavNetwork.MissionData snapshot = new RdpUavNetwork.MissionData();
            snapshot.fromBytes(framed);
            assertArrayEquals(new String[] {"mission-1", "mission-2"}, snapshot.getIds());
            assertArrayEquals(new String[] {
                    "mission-1  TARGET_LOCKED  switchblade",
                    "mission-2  COMPLETE  switchblade"
            }, snapshot.getRows());

            // This is the same client-side entry point used by the packet handler.
            new GuiMissionControl();
            GuiMissionControl.receiveMissionData(snapshot.getRows(), snapshot.getIds());
            assertNull(GuiMissionControl.selectedMissionId());
        } finally {
            wire.release();
        }
    }

    @Test
    public void serverRejectsOversizedMissionSnapshotBeforeAllocatingRows() {
        ByteBuf wire = Unpooled.buffer().writeInt(257);
        try {
            RdpUavNetwork.MissionData snapshot = new RdpUavNetwork.MissionData();
            snapshot.fromBytes(wire);
            fail("Mission snapshots over the protocol limit must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Invalid mission row count", expected.getMessage());
        } finally {
            wire.release();
        }
    }

    @Test
    public void emptyClientRequestsRemainValidWireMessages() {
        assertSameWirePayload(new RdpUavNetwork.RequestMissionData(),
                new RdpUavNetwork.RequestMissionData());
        assertSameWirePayload(new RdpUavNetwork.OpenMissionControl(),
                new RdpUavNetwork.OpenMissionControl());
    }

    private static <T extends net.minecraftforge.fml.common.network.simpleimpl.IMessage>
    T roundTrip(T sent, T received) {
        ByteBuf wire = Unpooled.buffer();
        try {
            sent.toBytes(wire);
            received.fromBytes(wire);
            return received;
        } finally {
            wire.release();
        }
    }

    private static void assertSameWirePayload(
            net.minecraftforge.fml.common.network.simpleimpl.IMessage sent,
            net.minecraftforge.fml.common.network.simpleimpl.IMessage received) {
        ByteBuf expected = Unpooled.buffer();
        ByteBuf actual = Unpooled.buffer();
        try {
            sent.toBytes(expected);
            byte[] expectedBytes = new byte[expected.readableBytes()];
            expected.getBytes(expected.readerIndex(), expectedBytes);
            received.fromBytes(expected);
            received.toBytes(actual);
            byte[] receivedBytes = new byte[actual.readableBytes()];
            actual.getBytes(actual.readerIndex(), receivedBytes);
            assertArrayEquals(expectedBytes, receivedBytes);
        } finally {
            expected.release();
            actual.release();
        }
    }

    private static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(bytes.length).writeBytes(bytes);
    }
}
