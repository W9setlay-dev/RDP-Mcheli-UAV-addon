package net.vas.rdp.uav.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MissionDataPagingContractTest {
    @Test
    public void pageRowsAndMetadataSurviveEncoding() {
        RdpUavNetwork.MissionData sent = new RdpUavNetwork.MissionData(
                new String[] {"mission-12"},
                new String[] {"mission-12"},
                new String[] {"AVAILABLE:OK agm"},
                new String[] {"agm"},
                1,
                2);
        ByteBuf wire = Unpooled.buffer();
        try {
            sent.toBytes(wire);
            RdpUavNetwork.MissionData received = new RdpUavNetwork.MissionData();
            received.fromBytes(wire);
            assertArrayEquals(new String[] {"mission-12"}, received.getIds());
            assertArrayEquals(new String[] {"AVAILABLE:OK agm"}, received.getWeaponRows());
            assertArrayEquals(new String[] {"agm"}, received.getWeaponNames());
            assertEquals(1, received.getPage());
            assertEquals(2, received.getPageCount());
        } finally {
            wire.release();
        }
    }

    @Test
    public void outgoingSnapshotCannotExceedTheDecoderPageLimit() {
        String[] rows = new String[12];
        String[] ids = new String[12];
        RdpUavNetwork.MissionData oversized = new RdpUavNetwork.MissionData(
                rows, ids, new String[0], new String[0], 0, 1);
        ByteBuf wire = Unpooled.buffer();
        try {
            oversized.toBytes(wire);
            fail("The server must not serialize more than one mission page");
        } catch (IllegalArgumentException expected) {
            assertEquals("Invalid mission snapshot", expected.getMessage());
        } finally {
            wire.release();
        }
    }

    @Test
    public void outgoingWeaponRowsRespectPacketCapacity() {
        String[] rows = new String[] {"mission-12"};
        String[] ids = new String[] {"mission-12"};
        String[] weaponRows = new String[65];
        String[] weaponNames = new String[65];
        RdpUavNetwork.MissionData oversized = new RdpUavNetwork.MissionData(
                rows, ids, weaponRows, weaponNames, 0, 1);
        ByteBuf wire = Unpooled.buffer();
        try {
            oversized.toBytes(wire);
            fail("Weapon rows must be capped before serialization");
        } catch (IllegalArgumentException expected) {
            assertEquals("Invalid mission snapshot", expected.getMessage());
        } finally {
            wire.release();
        }
    }

    @Test
    public void reusedMissionSnapshotsResetStalePageMetadataOnDecode() {
        ByteBuf legacy = Unpooled.buffer();
        legacy.writeInt(0).writeByte(0).writeInt(0).writeInt(1);
        try {
            RdpUavNetwork.MissionData decoded = new RdpUavNetwork.MissionData();
            decoded.fromBytes(legacy);
            assertEquals(0, decoded.getPage());
            assertEquals(1, decoded.getPageCount());
            assertArrayEquals(new String[0], decoded.getIds());
            assertArrayEquals(new String[0], decoded.getRows());
        } finally {
            legacy.release();
        }
    }
}
