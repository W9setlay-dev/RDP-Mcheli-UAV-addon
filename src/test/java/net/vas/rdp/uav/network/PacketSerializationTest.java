package net.vas.rdp.uav.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.vas.rdp.uav.formation.WingmanRole;
import net.vas.rdp.uav.path.OrbitPrimitive;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PacketSerializationTest {
    @Test
    public void formationSnapshotRoundTrips() {
        UUID aircraft = UUID.fromString("00000000-0000-0000-0000-000000000011");
        PacketFormationSync source = new PacketFormationSync(Collections.singletonList(
                new PacketFormationSync.FormationSlotView(aircraft, 2,
                        WingmanRole.WINGMAN, 12.0D, 4.0D, 16.0D)));
        ByteBuf buffer = Unpooled.buffer();
        try {
            source.toBytes(buffer);
            PacketFormationSync decoded = new PacketFormationSync();
            decoded.fromBytes(buffer);

            assertEquals(1, decoded.getSlots().size());
            PacketFormationSync.FormationSlotView slot = decoded.getSlots().get(0);
            assertEquals(aircraft, slot.getAircraftUuid());
            assertEquals(2, slot.getSlotIndex());
            assertEquals(WingmanRole.WINGMAN, slot.getRole());
            assertEquals(12.0D, slot.getSideOffset(), 0.0D);
            assertEquals(4.0D, slot.getAltitudeOffset(), 0.0D);
            assertEquals(16.0D, slot.getRearOffset(), 0.0D);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void deconflictionAlertRoundTrips() {
        UUID aircraftA = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID aircraftB = UUID.fromString("00000000-0000-0000-0000-000000000022");
        PacketDeconflictionAlert source = new PacketDeconflictionAlert(
                aircraftA, aircraftB, "PREDICTED_SEPARATION_VIOLATION", 0);
        ByteBuf buffer = Unpooled.buffer();
        try {
            source.toBytes(buffer);
            PacketDeconflictionAlert decoded = new PacketDeconflictionAlert();
            decoded.fromBytes(buffer);

            assertEquals(aircraftA, decoded.getAircraftA());
            assertEquals(aircraftB, decoded.getAircraftB());
            assertEquals("PREDICTED_SEPARATION_VIOLATION", decoded.getReason());
            assertEquals(0, decoded.getResolutionAttempts());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void orbitStatusRoundTrips() {
        UUID aircraft = UUID.fromString("00000000-0000-0000-0000-000000000011");
        PacketOrbitStatus source = new PacketOrbitStatus(
                aircraft, OrbitPrimitive.RACETRACK, true, 123L);
        ByteBuf buffer = Unpooled.buffer();
        try {
            source.toBytes(buffer);
            PacketOrbitStatus decoded = new PacketOrbitStatus();
            decoded.fromBytes(buffer);

            assertEquals(aircraft, decoded.getAircraftUuid());
            assertEquals(OrbitPrimitive.RACETRACK, decoded.getPrimitive());
            assertTrue(decoded.isEntering());
            assertEquals(123L, decoded.getServerTick());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void orbitStatusAllowsExitTransition() {
        PacketOrbitStatus status = new PacketOrbitStatus(
                UUID.randomUUID(), OrbitPrimitive.ORBIT, false, 0L);

        assertFalse(status.isEntering());
    }
}
