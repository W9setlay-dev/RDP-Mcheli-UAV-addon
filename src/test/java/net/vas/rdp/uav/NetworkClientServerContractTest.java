package net.vas.rdp.uav;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.vas.rdp.uav.client.GuiMissionControl;
import net.vas.rdp.uav.mission.MissionDefinition;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.network.RdpUavNetwork;
import net.vas.rdp.uav.swarm.SwarmConfiguration;
import net.vas.rdp.uav.swarm.SwarmFormation;
import net.vas.rdp.uav.swarm.SearchPattern;
import net.vas.rdp.uav.swarm.SwarmLeadership;
import net.vas.rdp.uav.client.SwarmGuiState;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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
    public void versionedMissionDefinitionLaunchPreservesConfiguration() {
        UUID swarmId = UUID.randomUUID();
        UUID selectedId = UUID.randomUUID();
        MissionDefinition definition = new MissionDefinition()
                .setMissionType(MissionType.STRIKE)
                .setTarget("minecraft:zombie")
                .setAttack(true)
                .setCruiseAltitudeY(192)
                .setSwarmEnabled(true)
                .setSwarmMaximumMembers(4)
                .setSwarmId(swarmId)
                .selectUav(selectedId)
                .setSwarmConfiguration(SwarmConfiguration.builder().maximumMembers(4)
                        .horizontalSpacing(30D).verticalSpacing(15D)
                        .formation(SwarmFormation.RING).searchPattern(SearchPattern.LANE)
                        .leadership(SwarmLeadership.DESIGNATED_LEADER)
                        .allowDuplicateTargets(true).allowCommanderFailover(false).build());
        RdpUavNetwork.CreateMission.LaunchMissionDefinition received = roundTrip(
                new RdpUavNetwork.CreateMission.LaunchMissionDefinition(definition),
                new RdpUavNetwork.CreateMission.LaunchMissionDefinition());
        assertEquals("minecraft:zombie", getLaunchTarget(received));
        assertFalse(getLaunchUnsupported(received));
        assertEquals(swarmId.toString(), received.getSwarmId());
        assertEquals(java.util.Collections.singletonList(selectedId), received.getSelectedUavs());
        MissionDefinition decoded = received.toMissionDefinition();
        assertEquals(Integer.valueOf(192), decoded.getCruiseAltitudeY());
        assertEquals(SwarmFormation.RING, decoded.getSwarmConfiguration().getFormation());
        assertEquals(SearchPattern.LANE, decoded.getSwarmConfiguration().getSearchPattern());
        assertEquals(30D, decoded.getSwarmConfiguration().getHorizontalSpacing(), 0D);
        assertEquals(15D, decoded.getSwarmConfiguration().getVerticalSpacing(), 0D);
        assertFalse(decoded.getSwarmConfiguration().isAllowCommanderFailover());
    }

    @Test
    public void versionThreeMissionPacketStillDecodesWithoutSwarmExtension() {
        RdpUavNetwork.CreateMission.LaunchMissionDefinition sent =
                new RdpUavNetwork.CreateMission.LaunchMissionDefinition(new MissionDefinition()
                        .setTarget("minecraft:zombie"));
        setPrivateInt(sent, "version", 3);
        RdpUavNetwork.CreateMission.LaunchMissionDefinition received = roundTrip(sent,
                new RdpUavNetwork.CreateMission.LaunchMissionDefinition());
        assertEquals("", received.getSwarmId());
        assertTrue(received.getSelectedUavs().isEmpty());
    }

    @Test
    public void versionFourMissionPacketStillDecodesWithoutCruiseAltitudeExtension() {
        RdpUavNetwork.CreateMission.LaunchMissionDefinition sent =
                new RdpUavNetwork.CreateMission.LaunchMissionDefinition(new MissionDefinition()
                        .setTarget("minecraft:zombie"));
        setPrivateInt(sent, "version", 4);
        RdpUavNetwork.CreateMission.LaunchMissionDefinition received = roundTrip(sent,
                new RdpUavNetwork.CreateMission.LaunchMissionDefinition());
        assertNull(received.toMissionDefinition().getCruiseAltitudeY());
    }

    @Test
    public void squareStrikePacketPreservesOptionalCruiseAltitudeExtension() {
        assertSameWirePayload(new RdpUavNetwork.SquareStrike("10 80 20 64",
                        "agm114", 180),
                new RdpUavNetwork.SquareStrike());
    }

    @Test
    public void dedicatedSwarmPacketsPreserveOwnerIntentAndSnapshotRows() {
        UUID swarmId = UUID.randomUUID();
        UUID aircraftId = UUID.randomUUID();
        assertSameWirePayload(new RdpUavNetwork.RequestSwarmSnapshot(swarmId.toString()),
                new RdpUavNetwork.RequestSwarmSnapshot());
        assertSameWirePayload(new RdpUavNetwork.SwarmOperatorCommand(2, swarmId.toString(),
                        aircraftId.toString(), ""),
                new RdpUavNetwork.SwarmOperatorCommand());
        assertSameWirePayload(new RdpUavNetwork.SwarmOperatorCommand(6, swarmId.toString(),
                        aircraftId.toString(), ""),
                new RdpUavNetwork.SwarmOperatorCommand());
        assertSameWirePayload(new RdpUavNetwork.SwarmSharedTargetCommand(
                        swarmId.toString(), aircraftId, false),
                new RdpUavNetwork.SwarmSharedTargetCommand());
        assertSameWirePayload(new RdpUavNetwork.SwarmConfigurationUpdate(swarmId.toString(),
                        SwarmConfiguration.builder().maximumMembers(4).horizontalSpacing(24D)
                                .verticalSpacing(12D).formation(SwarmFormation.WEDGE)
                                .searchPattern(SearchPattern.SPIRAL)
                                .leadership(SwarmLeadership.NO_SINGLE_LEADER).build(), true),
                new RdpUavNetwork.SwarmConfigurationUpdate());

        SwarmGuiState.Member member = new SwarmGuiState.Member(aircraftId,
                "REUSABLE_UAV", "SCOUT", "ASSIGNED", true, false, true);
        SwarmGuiState.Swarm swarm = new SwarmGuiState.Swarm(swarmId, "alpha", "DEPLOYING",
                "ASSIGNED", "WEDGE", "SPIRAL", "NO_SINGLE_LEADER", 4,
                24D, 12D, false, true, true, "null", "CLEAR",
                java.util.Collections.singletonList(member));
        RdpUavNetwork.SwarmSnapshot snapshot = roundTrip(
                new RdpUavNetwork.SwarmSnapshot(java.util.Collections.singletonList(swarm)),
                new RdpUavNetwork.SwarmSnapshot());
        assertEquals(swarmId, snapshot.getSwarms().get(0).id);
        assertEquals(aircraftId, snapshot.getSwarms().get(0).members.get(0).aircraftId);
        assertEquals("SPIRAL", snapshot.getSwarms().get(0).searchPattern);
        assertTrue(snapshot.getSwarms().get(0).members.get(0).designatedLeader);
    }

    private static void setPrivateInt(Object target, String name, int value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(target, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static String getLaunchTarget(RdpUavNetwork.CreateMission.LaunchMissionDefinition message) {
        try {
            java.lang.reflect.Field field = message.getClass().getDeclaredField("target");
            field.setAccessible(true);
            return (String) field.get(message);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static boolean getLaunchUnsupported(
            RdpUavNetwork.CreateMission.LaunchMissionDefinition message) {
        try {
            java.lang.reflect.Field field = message.getClass()
                    .getDeclaredField("unsupportedConfiguration");
            field.setAccessible(true);
            return field.getBoolean(message);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @Test
    public void everyMissionExecutionRequestPreservesItsWireContract() {
        assertSameWirePayload(new RdpUavNetwork.RequestMissionData(),
                new RdpUavNetwork.RequestMissionData());
        assertSameWirePayload(new RdpUavNetwork.FireSelfDestruct(),
                new RdpUavNetwork.FireSelfDestruct());
        assertSameWirePayload(new RdpUavNetwork.CreateMission("mod:target", false, false),
                new RdpUavNetwork.CreateMission());
        assertSameWirePayload(new RdpUavNetwork.CreateMission("", false, true),
                new RdpUavNetwork.CreateMission());
        assertSameWirePayload(new RdpUavNetwork.CreateMission("mod:target", true, false),
                new RdpUavNetwork.CreateMission());
        assertSameWirePayload(new RdpUavNetwork.CreateMission("mod:target", true, false, 3,
                        "agm114"),
                new RdpUavNetwork.CreateMission());
        assertSameWirePayload(new RdpUavNetwork.SquareStrike("100 140 -50 80"),
                new RdpUavNetwork.SquareStrike());
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
            assertEquals(0, snapshot.getPage());
            assertEquals(1, snapshot.getPageCount());

            // This is the same client-side entry point used by the packet handler.
            new GuiMissionControl();
            GuiMissionControl.receiveMissionData(snapshot.getRows(), snapshot.getIds());
            assertNull(GuiMissionControl.selectedMissionId());
        } finally {
            wire.release();
        }
    }

    @Test
    public void missionSnapshotRequestsPreservePageAndAcceptLegacyEmptyRequests() {
        RdpUavNetwork.RequestMissionData received = roundTrip(
                new RdpUavNetwork.RequestMissionData(7),
                new RdpUavNetwork.RequestMissionData());
        assertEquals(7, received.getPage());

        RdpUavNetwork.RequestMissionData legacy = new RdpUavNetwork.RequestMissionData();
        legacy.fromBytes(Unpooled.EMPTY_BUFFER);
        assertEquals(0, legacy.getPage());

        ByteBuf invalid = Unpooled.buffer().writeByte(1);
        try {
            legacy.fromBytes(invalid);
            fail("Partial mission page requests must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Invalid mission page request", expected.getMessage());
        } finally {
            invalid.release();
        }
    }

    @Test
    public void serverMissionSnapshotDecodesPageMetadataAndRejectsInvalidMetadata() {
        ByteBuf wire = Unpooled.buffer();
        try {
            wire.writeInt(1);
            writeString(wire, "mission-12");
            writeString(wire, "mission-12  COMPLETE  switchblade");
            wire.writeByte(0);
            wire.writeInt(1).writeInt(2);

            RdpUavNetwork.MissionData snapshot = new RdpUavNetwork.MissionData();
            snapshot.fromBytes(wire);
            assertEquals(1, snapshot.getPage());
            assertEquals(2, snapshot.getPageCount());
            assertArrayEquals(new String[] {"mission-12"}, snapshot.getIds());
        } finally {
            wire.release();
        }

        ByteBuf invalid = Unpooled.buffer();
        try {
            invalid.writeInt(0).writeByte(0).writeInt(0).writeInt(0);
            new RdpUavNetwork.MissionData().fromBytes(invalid);
            fail("Mission page count must be positive");
        } catch (IllegalArgumentException expected) {
            assertEquals("Invalid mission page metadata", expected.getMessage());
        } finally {
            invalid.release();
        }
    }

    @Test
    public void serverRejectsMissionSnapshotLargerThanOnePageBeforeAllocatingRows() {
        ByteBuf wire = Unpooled.buffer().writeInt(12);
        try {
            RdpUavNetwork.MissionData snapshot = new RdpUavNetwork.MissionData();
            snapshot.fromBytes(wire);
            fail("Mission snapshots larger than one page must be rejected");
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

    @Test
    public void aiTargetLockRequestPreservesExactTargetAndClearFlag() {
        assertSameWirePayload(
                new RdpUavNetwork.AiTargetLock(UUID.fromString("00000000-0000-0000-0000-000000000042"), false),
                new RdpUavNetwork.AiTargetLock());
        assertSameWirePayload(new RdpUavNetwork.AiTargetLock(null, true),
                new RdpUavNetwork.AiTargetLock());
    }

    @Test
    public void targetLockWireContractNeverSubstitutesOrChangesTargetIdentity() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000042");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000043");
        RdpUavNetwork.AiTargetLock decoded = roundTrip(
                new RdpUavNetwork.AiTargetLock(first, false),
                new RdpUavNetwork.AiTargetLock());
        assertEquals(first, decoded.getTargetId());
        assertFalse(decoded.isClear());
        assertEquals(second, roundTrip(
                new RdpUavNetwork.AiTargetLock(second, false),
                new RdpUavNetwork.AiTargetLock()).getTargetId());
    }

    @Test
    public void sightStabilizationWireContractPreservesGroundPoint() {
        assertSameWirePayload(
                new RdpUavNetwork.AiTargetLock.AreaLock(12.5D, 80.0D, -4.5D, false),
                new RdpUavNetwork.AiTargetLock.AreaLock());
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
