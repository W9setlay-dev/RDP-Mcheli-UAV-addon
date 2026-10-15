package net.vas.rdp.uav.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.vas.rdp.uav.formation.WingmanRole;
import net.vas.rdp.uav.path.OrbitPrimitive;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.designation.DesignationSource;
import net.vas.rdp.uav.designation.DesignationType;
import org.junit.Test;

import java.util.Arrays;
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

    @Test
    public void designationRequestAndPromotionPacketsRoundTrip() {
        UUID designationA = UUID.randomUUID();
        UUID designationB = UUID.randomUUID();
        RdpUavNetwork.PacketDesignationRequest request =
                new RdpUavNetwork.PacketDesignationRequest(
                        DesignationSource.CROSSHAIR, DesignationType.ENTITY,
                        1.0D, 2.0D, 3.0D, 0.0D, 0.0D, 1.0D,
                        128.0D, designationA, "minecraft:zombie", 1920, 1080, 77L);
        ByteBuf requestBuffer = Unpooled.buffer();
        try {
            request.toBytes(requestBuffer);
            RdpUavNetwork.PacketDesignationRequest decoded =
                    new RdpUavNetwork.PacketDesignationRequest();
            decoded.fromBytes(requestBuffer);
            assertEquals(designationA, decoded.candidateEntityUuid());
        } finally {
            requestBuffer.release();
        }

        RdpUavNetwork.PacketDesignationPromoteToMission promote =
                new RdpUavNetwork.PacketDesignationPromoteToMission(
                        Arrays.asList(designationA, designationB), MissionType.STRIKE.name());
        ByteBuf promoteBuffer = Unpooled.buffer();
        try {
            promote.toBytes(promoteBuffer);
            RdpUavNetwork.PacketDesignationPromoteToMission decoded =
                    new RdpUavNetwork.PacketDesignationPromoteToMission();
            decoded.fromBytes(promoteBuffer);
            assertEquals(Arrays.asList(designationA, designationB), decoded.designationIds());
            assertEquals(MissionType.STRIKE.name(), decoded.missionType());
        } finally {
            promoteBuffer.release();
        }
    }

    @Test
    public void targetTelemetryClassificationProjectionRoundTrips() {
        RdpUavNetwork.TargetTelemetry source = new RdpUavNetwork.TargetTelemetry();
        ByteBuf buffer = Unpooled.buffer();
        try {
            source.toBytes(buffer);
            RdpUavNetwork.TargetTelemetry decoded = new RdpUavNetwork.TargetTelemetry();
            decoded.fromBytes(buffer);

            assertEquals("UNKNOWN", decoded.getClassification());
            assertEquals(0.0D, decoded.getClassificationConfidence(), 0.0D);
            assertFalse(decoded.hasClassificationConflict());
        } finally {
            buffer.release();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void promotionPacketRejectsOversizedSelections() {
        java.util.ArrayList<UUID> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i <= net.vas.rdp.uav.mission.RDPMission.MAX_COMPOSED_OBJECTIVES;
             i++) tooMany.add(UUID.randomUUID());
        new RdpUavNetwork.PacketDesignationPromoteToMission(tooMany, MissionType.STRIKE.name());
    }

    @Test(expected = IllegalArgumentException.class)
    public void promotionPacketRejectsUnknownMissionType() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeInt(0);
            buffer.writeShort(7).writeBytes("INVALID".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            new RdpUavNetwork.PacketDesignationPromoteToMission().fromBytes(buffer);
        } finally {
            buffer.release();
        }
    }
}
