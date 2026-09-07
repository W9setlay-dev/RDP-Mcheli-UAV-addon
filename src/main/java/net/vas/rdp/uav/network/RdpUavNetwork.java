package net.vas.rdp.uav.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.vas.rdp.uav.RDPUavMod;

public final class RdpUavNetwork {
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(
            new ResourceLocation(RDPUavMod.MOD_ID, "control").toString());
    private static boolean initialized;

    private RdpUavNetwork() { }

    public static void init() {
        if (initialized) return;
        initialized = true;
        CHANNEL.registerMessage(ServerMissionDataHandler.class, RequestMissionData.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ClientMissionControlHandler.class, OpenMissionControl.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(ClientMissionDataHandler.class, MissionData.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(ServerAbortMissionHandler.class, AbortMission.class, 3, Side.SERVER);
    }

    public static void openMissionControl(EntityPlayerMP player) {
        CHANNEL.sendTo(new OpenMissionControl(), player);
    }

    public static void requestMissionData() {
        CHANNEL.sendToServer(new RequestMissionData());
    }

    public static void abortMission(String missionId) {
        CHANNEL.sendToServer(new AbortMission(missionId));
    }

    static void sendMissionData(EntityPlayerMP player) {
        CHANNEL.sendTo(MissionData.from(player), player);
    }

    public static final class OpenMissionControl implements IMessage {
        public void fromBytes(ByteBuf buffer) { }
        public void toBytes(ByteBuf buffer) { }
    }

    public static final class RequestMissionData implements IMessage {
        public void fromBytes(ByteBuf buffer) { }
        public void toBytes(ByteBuf buffer) { }
    }

    public static final class AbortMission implements IMessage {
        private String missionId = "";
        public AbortMission() { }
        public AbortMission(String missionId) { this.missionId = missionId == null ? "" : missionId; }
        public void fromBytes(ByteBuf buffer) { missionId = readString(buffer); }
        public void toBytes(ByteBuf buffer) { writeString(buffer, missionId); }
    }

    public static final class MissionData implements IMessage {
        private String[] rows = new String[0];
        private String[] ids = new String[0];
        public MissionData() { }
        private MissionData(String[] rows, String[] ids) { this.rows = rows; this.ids = ids; }
        static MissionData from(EntityPlayerMP player) {
            java.util.List<net.vas.rdp.uav.mission.RDPMission> missions =
                    net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                            .getRepository().byOwner(player.getUniqueID());
            String[] rows = new String[missions.size()];
            String[] ids = new String[missions.size()];
            for (int i = 0; i < missions.size(); i++) {
                net.vas.rdp.uav.mission.RDPMission mission = missions.get(i);
                ids[i] = mission.getMissionId().toString();
                rows[i] = mission.getMissionId().toString().substring(0, 8) + "  "
                        + mission.getState().name() + "  " + mission.getAircraftType();
            }
            return new MissionData(rows, ids);
        }
        public String[] getRows() { return rows; }
        public String[] getIds() { return ids; }
        public void fromBytes(ByteBuf buffer) {
            int count = buffer.readInt();
            if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid mission row count");
            rows = new String[count];
            ids = new String[count];
            for (int i = 0; i < count; i++) {
                ids[i] = readString(buffer);
                rows[i] = readString(buffer);
            }
        }
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(rows.length);
            for (int i = 0; i < rows.length; i++) {
                writeString(buffer, ids[i]);
                writeString(buffer, rows[i]);
            }
        }
    }

    private static String readString(ByteBuf buffer) {
        int length = buffer.readUnsignedShort();
        return buffer.readCharSequence(length, java.nio.charset.StandardCharsets.UTF_8).toString();
    }
    private static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > 65535) throw new IllegalArgumentException("Mission row is too long");
        buffer.writeShort(bytes.length).writeBytes(bytes);
    }

    public static final class ServerMissionDataHandler implements IMessageHandler<RequestMissionData, IMessage> {
        public IMessage onMessage(RequestMissionData message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServer().addScheduledTask(new Runnable() {
                public void run() { sendMissionData(player); }
            });
            return null;
        }
    }

    public static final class ServerAbortMissionHandler implements IMessageHandler<AbortMission, IMessage> {
            public IMessage onMessage(final AbortMission message, MessageContext context) {
                final EntityPlayerMP player = context.getServerHandler().player;
                player.getServer().addScheduledTask(new Runnable() {
                    public void run() {
                        try {
                            java.util.UUID id = java.util.UUID.fromString(message.missionId);
                            net.vas.rdp.uav.mission.RDPMission mission =
                                    net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                            .getRepository().find(id);
                            if (mission == null || !player.getUniqueID().equals(mission.getOwnerId())
                                    || mission.getState() == net.vas.rdp.uav.mission.MissionState.COMPLETE
                                    || mission.getState() == net.vas.rdp.uav.mission.MissionState.ABORTED
                                    || mission.getState() == net.vas.rdp.uav.mission.MissionState.FAILED) {
                                return;
                            }
                            mission.transitionTo(net.vas.rdp.uav.mission.MissionState.ABORTED,
                                    player.world.getTotalWorldTime());
                            net.vas.rdp.uav.RDPUavMod.missions().release(mission);
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                            sendMissionData(player);
                        } catch (IllegalArgumentException ignored) {
                            // Invalid client input is rejected without touching mission state.
                    }
                }
            });
                return null;
            }
        }

    public static final class ClientMissionControlHandler implements IMessageHandler<OpenMissionControl, IMessage> {
        public IMessage onMessage(OpenMissionControl message, MessageContext context) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                public void run() {
                    net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                            new net.vas.rdp.uav.client.GuiMissionControl());
                }
            });
            return null;
        }
    }

    public static final class ClientMissionDataHandler implements IMessageHandler<MissionData, IMessage> {
        public IMessage onMessage(final MissionData message, MessageContext context) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                public void run() {
                    net.vas.rdp.uav.client.GuiMissionControl.receiveMissionData(message.getRows(), message.getIds());
                }
            });
            return null;
        }
    }
}
