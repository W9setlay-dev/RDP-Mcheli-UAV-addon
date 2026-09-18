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
import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.wingman.wingman.WingmanRegistry;
import net.vas.rdp.uav.RdpUavDebug;
import net.vas.rdp.uav.flight.KamikazePilotOverride;
import net.vas.rdp.uav.weapon.GuidedStrikeObjective;
import net.vas.rdp.uav.weapon.GuidedWeaponAdapter;
import net.vas.rdp.uav.weapon.GuidedWeaponDecision;

import java.util.Objects;

public final class RdpUavNetwork {
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(
            new ResourceLocation(RDPUavMod.MOD_ID, "control").toString());
    private static final net.vas.rdp.uav.flight.WingManFlightAdapter FLIGHT_ADAPTER =
            new net.vas.rdp.uav.flight.WingManFlightAdapter();
    private static final GuidedWeaponAdapter GUIDED_ADAPTER =
            new GuidedWeaponAdapter(FLIGHT_ADAPTER);
    private static final net.vas.rdp.uav.target.TargetResolver TARGET_RESOLVER =
            new net.vas.rdp.uav.target.TargetResolver();
    private static boolean initialized;

    private RdpUavNetwork() { }

    public static void init() {
        if (initialized) return;
        initialized = true;
        CHANNEL.registerMessage(ServerMissionDataHandler.class, RequestMissionData.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ClientMissionControlHandler.class, OpenMissionControl.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(ClientMissionDataHandler.class, MissionData.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(ServerAbortMissionHandler.class, AbortMission.class, 3, Side.SERVER);
        CHANNEL.registerMessage(ServerAuthorizeAttackHandler.class, AuthorizeAttack.class, 4, Side.SERVER);
        CHANNEL.registerMessage(ServerCreateMissionHandler.class, CreateMission.class, 5, Side.SERVER);
        CHANNEL.registerMessage(ServerKamikazeHandler.class, KamikazeAttack.class, 6, Side.SERVER);
        CHANNEL.registerMessage(ServerSelfDestructHandler.class, FireSelfDestruct.class, 10, Side.SERVER);
        CHANNEL.registerMessage(ClientMissionDataHandler.ClientTargetTelemetryHandler.class,
                TargetTelemetry.class, 7, Side.CLIENT);
        CHANNEL.registerMessage(ServerSquareStrikeHandler.class, SquareStrike.class, 8, Side.SERVER);
        CHANNEL.registerMessage(ServerAiTargetLockHandler.class, AiTargetLock.class, 9, Side.SERVER);
        CHANNEL.registerMessage(ClientSwarmTelemetryHandler.class, SwarmTelemetry.class, 11, Side.CLIENT);
        CHANNEL.registerMessage(ServerContactSelectionHandler.class, ContactSelection.class, 12, Side.SERVER);
        CHANNEL.registerMessage(ServerClearMissionsHandler.class, ClearMissions.class, 13, Side.SERVER);
        CHANNEL.registerMessage(ServerReturnToBaseHandler.class, ReturnToBase.class, 14, Side.SERVER);
        CHANNEL.registerMessage(AiTargetLock.ServerAreaLockHandler.class,
                AiTargetLock.AreaLock.class, 15, Side.SERVER);
        CHANNEL.registerMessage(ClientSwarmTelemetryHandler.ServerSwarmConfigHandler.class,
                ClientSwarmTelemetryHandler.SwarmConfigUpdate.class, 16, Side.SERVER);
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

    public static void clearSelectedMission(String missionId) {
        CHANNEL.sendToServer(new ClearMissions(missionId, false));
    }

    public static void clearAllMissions() {
        CHANNEL.sendToServer(new ClearMissions("", true));
    }

    public static void returnToBase(String missionId) {
        CHANNEL.sendToServer(new ReturnToBase(missionId));
    }

    public static void authorizeAttack(String missionId) {
        CHANNEL.sendToServer(new AuthorizeAttack(missionId));
    }

    public static void armKamikaze(String missionId) {
        CHANNEL.sendToServer(new KamikazeAttack(missionId));
    }

    public static void fireSelfDestruct() {
        CHANNEL.sendToServer(new FireSelfDestruct());
    }

    public static void createMission(String target, boolean attack) {
        createMission(target, attack, 2);
    }

    public static void createMission(String target, boolean attack, int priority) {
        if (target == null || !target.matches(
                "(?i)(mod=[A-Za-z0-9_.-]+|[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+)")) return;
        CHANNEL.sendToServer(new CreateMission(target, attack, false, priority));
    }

    public static void createPointMission() {
        CHANNEL.sendToServer(new CreateMission("", false, true));
    }

    public static void createSquareStrike(String parameters) {
        CHANNEL.sendToServer(new SquareStrike(parameters));
    }

    public static void lockAiTarget(java.util.UUID targetId) {
        if (targetId != null) CHANNEL.sendToServer(new AiTargetLock(targetId, false));
    }

    /** Requests a camera-independent lock using the selected/filter fallback. */
    public static void lockAiTarget() {
        CHANNEL.sendToServer(new AiTargetLock(null, false));
    }

    public static void clearAiTargetLock() {
        CHANNEL.sendToServer(new AiTargetLock(null, true));
    }

    public static void stabilizeArea(double x, double y, double z) {
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
            CHANNEL.sendToServer(new AiTargetLock.AreaLock(x, y, z, false));
        }
    }

    public static void cycleContact(boolean next) {
        CHANNEL.sendToServer(new ContactSelection(next, false));
    }

    public static void clearContactSelection() {
        CHANNEL.sendToServer(new ContactSelection(false, true));
    }

    public static void updateSwarmConfig(String swarmId, int maximumMembers,
                                         boolean participation, boolean allowDuplicates) {
        if (swarmId == null || swarmId.trim().isEmpty()) return;
        CHANNEL.sendToServer(new ClientSwarmTelemetryHandler.SwarmConfigUpdate(swarmId, maximumMembers,
                participation, allowDuplicates));
    }

    static void sendMissionData(EntityPlayerMP player) {
        CHANNEL.sendTo(MissionData.from(player), player);
    }

    public static void syncTargetTelemetry(EntityPlayerMP player) {
        CHANNEL.sendTo(TargetTelemetry.from(player), player);
    }

    public static void syncSwarmTelemetry(EntityPlayerMP player) {
        net.vas.rdp.uav.swarm.SwarmRecord selected = null;
        for (net.vas.rdp.uav.swarm.SwarmRecord swarm :
                net.vas.rdp.uav.persistence.RDPUavSwarmWorldData.get(player.world)
                        .getRepository().snapshot()) {
            if (player.getUniqueID().equals(swarm.getOwnerId())) { selected = swarm; break; }
        }
        if (selected != null) CHANNEL.sendTo(SwarmTelemetry.from(selected), player);
    }

    public static final class SwarmTelemetry implements IMessage {
        private String id = "", name = "", state = "", formation = "", leader = "";
        private int members;
        public static SwarmTelemetry from(net.vas.rdp.uav.swarm.SwarmRecord swarm) {
            SwarmTelemetry value = new SwarmTelemetry();
            value.id = swarm.getSwarmId().toString(); value.name = swarm.getName();
            value.state = swarm.getState().name(); value.formation = swarm.getFormation().name();
            value.leader = String.valueOf(swarm.referenceMember()); value.members = swarm.getMembers().size();
            return value;
        }

        public void fromBytes(ByteBuf buffer) {
            id = readString(buffer); name = readString(buffer); state = readString(buffer);
            formation = readString(buffer); leader = readString(buffer); members = buffer.readUnsignedByte();
        }

        public void toBytes(ByteBuf buffer) {
            writeString(buffer, id); writeString(buffer, name); writeString(buffer, state);
            writeString(buffer, formation); writeString(buffer, leader);
            buffer.writeByte(Math.min(255, Math.max(0, members)));
        }
    }

    public static final class ClientSwarmTelemetryHandler implements IMessageHandler<SwarmTelemetry, IMessage> {
        public IMessage onMessage(SwarmTelemetry message, MessageContext context) {
            return null;
        }

        public static final class SwarmConfigUpdate implements IMessage {
            private String swarmId = "";
            private int maximumMembers;
            private boolean participation;
            private boolean allowDuplicates;
            public SwarmConfigUpdate() { }
            public SwarmConfigUpdate(String swarmId, int maximumMembers,
                                     boolean participation, boolean allowDuplicates) {
                this.swarmId = swarmId == null ? "" : swarmId;
                this.maximumMembers = maximumMembers;
                this.participation = participation;
                this.allowDuplicates = allowDuplicates;
            }
            public void fromBytes(ByteBuf buffer) {
                swarmId = readString(buffer);
                maximumMembers = buffer.readUnsignedByte();
                participation = buffer.readBoolean();
                allowDuplicates = buffer.readBoolean();
            }
            public void toBytes(ByteBuf buffer) {
                writeString(buffer, swarmId);
                buffer.writeByte(Math.max(1, Math.min(255, maximumMembers)));
                buffer.writeBoolean(participation);
                buffer.writeBoolean(allowDuplicates);
            }
        }

        public static final class ServerSwarmConfigHandler
                implements IMessageHandler<SwarmConfigUpdate, IMessage> {
            public IMessage onMessage(final SwarmConfigUpdate message, MessageContext context) {
                final EntityPlayerMP player = context.getServerHandler().player;
                Objects.requireNonNull(player.getServer()).addScheduledTask(new Runnable() {
                    public void run() {
                        try {
                            java.util.UUID id = java.util.UUID.fromString(message.swarmId);
                            net.vas.rdp.uav.swarm.SwarmRecord record =
                                    RDPUavMod.swarms().find(player.world, id);
                            if (!RDPUavMod.swarms().owns(record, player.getUniqueID())) {
                                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                        "Swarm configuration rejected: owner authorization is required."));
                                return;
                            }
                            record.setMissionMaximumMembers(message.maximumMembers);
                            record.setMissionParticipation(message.participation);
                            record.setAllowDuplicateTargets(message.allowDuplicates);
                            net.vas.rdp.uav.persistence.RDPUavSwarmWorldData.get(player.world).markDirty();
                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    "Swarm configuration updated."));
                            syncSwarmTelemetry(player);
                        } catch (IllegalArgumentException failure) {
                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    "Swarm configuration rejected: invalid swarm ID."));
                        }
                    }
                });
                return null;
            }
        }
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

    public static final class ReturnToBase implements IMessage {
        private String missionId = "";
        public ReturnToBase() { }
        public ReturnToBase(String missionId) { this.missionId = missionId == null ? "" : missionId; }
        public void fromBytes(ByteBuf buffer) { missionId = readString(buffer); }
        public void toBytes(ByteBuf buffer) { writeString(buffer, missionId); }
    }

    public static final class ServerReturnToBaseHandler
            implements IMessageHandler<ReturnToBase, IMessage> {
        public IMessage onMessage(final ReturnToBase message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(new Runnable() {
                public void run() {
                    try {
                        java.util.UUID id = java.util.UUID.fromString(message.missionId);
                        net.vas.rdp.uav.mission.RDPMission mission =
                                net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                        .getRepository().find(id);
                        if (mission == null || !player.getUniqueID().equals(mission.getOwnerId())
                                || mission.isTerminal()) return;
                        MCH_EntityAircraft aircraft = null;
                        for (net.minecraft.entity.Entity entity : player.world.loadedEntityList) {
                            if (entity instanceof MCH_EntityAircraft
                                    && mission.getAircraftId().equals(entity.getUniqueID())) {
                                aircraft = (MCH_EntityAircraft) entity;
                                break;
                            }
                        }
                        if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)
                                || !FLIGHT_ADAPTER.requestReturn(aircraft, "RDP_PLAYER_RTB")) return;
                        mission.transitionTo(net.vas.rdp.uav.mission.MissionState.RETURNING,
                                player.world.getTotalWorldTime());
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                        sendMissionData(player);
                    } catch (IllegalArgumentException ignored) {
                        RdpUavDebug.debug("Network", "RTB rejected invalid mission={}", message.missionId);
                    }
                }
            });
            return null;
        }
    }

    /** Operator-only mission cleanup; false clears one id, true clears all owned missions. */
    public static final class ClearMissions implements IMessage {
        private String missionId = "";
        private boolean all;
        public ClearMissions() { }
        public ClearMissions(String missionId, boolean all) {
            this.missionId = missionId == null ? "" : missionId;
            this.all = all;
        }
        public void fromBytes(ByteBuf buffer) { missionId = readString(buffer); all = buffer.readBoolean(); }
        public void toBytes(ByteBuf buffer) { writeString(buffer, missionId); buffer.writeBoolean(all); }
    }

    public static final class AuthorizeAttack implements IMessage {
        private String missionId = "";
        public AuthorizeAttack() { }
        public AuthorizeAttack(String missionId) { this.missionId = missionId == null ? "" : missionId; }
        public void fromBytes(ByteBuf buffer) { missionId = readString(buffer); }
        public void toBytes(ByteBuf buffer) { writeString(buffer, missionId); }
    }

    public static final class KamikazeAttack implements IMessage {
        private String missionId = "";
        public KamikazeAttack() { }
        public KamikazeAttack(String missionId) { this.missionId = missionId == null ? "" : missionId; }
        public void fromBytes(ByteBuf buffer) { missionId = readString(buffer); }
        public void toBytes(ByteBuf buffer) { writeString(buffer, missionId); }
    }

    public static final class FireSelfDestruct implements IMessage {
        public FireSelfDestruct() { }
        public void fromBytes(ByteBuf buf) { }
        public void toBytes(ByteBuf buf) { }
    }

    public static final class CreateMission implements IMessage {
        private String target = "";
        private boolean attack;
        private boolean point;
        private int priority = 2;

        public CreateMission() { }
        public CreateMission(String target, boolean attack, boolean point) {
            this(target, attack, point, 2);
        }
        public CreateMission(String target, boolean attack, boolean point, int priority) {
            this.target = target == null ? "" : target;
            this.attack = attack;
            this.point = point;
            this.priority = Math.max(0, Math.min(3, priority));
        }

        public void fromBytes(ByteBuf buffer) {
            target = readString(buffer);
            attack = buffer.readBoolean();
            point = buffer.readBoolean();
            priority = buffer.readUnsignedByte();
        }

        public void toBytes(ByteBuf buffer) {
            writeString(buffer, target);
            buffer.writeBoolean(attack);
            buffer.writeBoolean(point);
            buffer.writeByte(Math.max(0, Math.min(3, priority)));
        }

    }

    public static final class SquareStrike implements IMessage {
        private String parameters = "";
        public SquareStrike() { }
        public SquareStrike(String parameters) {
            this.parameters = parameters == null ? "" : parameters;
        }

        public void fromBytes(ByteBuf buffer) { parameters = readString(buffer); }
        public void toBytes(ByteBuf buffer) { writeString(buffer, parameters); }
    }

    public static final class AiTargetLock implements IMessage {
        private String targetId = "";
        private boolean clear;
        public AiTargetLock() { }
        public AiTargetLock(java.util.UUID targetId, boolean clear) {
            this.targetId = targetId == null ? "" : targetId.toString();
            this.clear = clear;
        }

        public static final class AreaLock implements IMessage {
            private double x, y, z;
            private boolean clear;
            public AreaLock() { }
            public AreaLock(double x, double y, double z, boolean clear) {
                this.x = x; this.y = y; this.z = z; this.clear = clear;
            }
            public void fromBytes(ByteBuf buffer) {
                x = buffer.readDouble(); y = buffer.readDouble(); z = buffer.readDouble();
                clear = buffer.readBoolean();
            }
            public void toBytes(ByteBuf buffer) {
                buffer.writeDouble(x).writeDouble(y).writeDouble(z).writeBoolean(clear);
            }
        }

        public static final class ServerAreaLockHandler
                implements IMessageHandler<AreaLock, IMessage> {
            public IMessage onMessage(final AreaLock message, MessageContext context) {
                final EntityPlayerMP player = context.getServerHandler().player;
                Objects.requireNonNull(player.getServer()).addScheduledTask(new Runnable() {
                    public void run() {
                        MCH_EntityAircraft aircraft =
                                net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                        if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)
                                || !RDPUavMod.missions().ensureAuthorized(aircraft, player)) {
                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    "UAV area lock rejected: operator authorization is required."));
                            return;
                        }
                        if (message.clear || !Double.isFinite(message.x)
                                || !Double.isFinite(message.y) || !Double.isFinite(message.z)) {
                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    "UAV area lock rejected: invalid ground coordinate."));
                            return;
                        }
                        java.util.UUID assigned = RDPUavMod.missions()
                                .assignedMission(aircraft.getUniqueID());
                        net.vas.rdp.uav.mission.RDPMission mission = assigned == null ? null
                                : net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                .getRepository().find(assigned);
                        if (mission == null) {
                            net.vas.rdp.uav.profile.UavVehicleProfile profile =
                                    aircraft.getAcInfo() == null ? null
                                    : net.vas.rdp.uav.profile.UavVehicleProfiles.forAircraft(
                                    aircraft.getAcInfo().name);
                            if (profile == null) {
                                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                        "UAV area lock rejected: aircraft profile is unavailable."));
                                return;
                            }
                            mission = new net.vas.rdp.uav.mission.RDPMission(
                                    java.util.UUID.randomUUID(), player.getUniqueID(),
                                    aircraft.getUniqueID(), aircraft.getAcInfo().name,
                                    net.vas.rdp.uav.mission.MissionType.RECONNAISSANCE,
                                    net.vas.rdp.uav.target.TargetSpec.point(message.x, message.y,
                                            message.z, net.vas.rdp.uav.target.TargetPriority.HIGH),
                                    false, false, false, profile.getDetectionRange(), 8.0D,
                                    player.world.getTotalWorldTime());
                            mission.setDimension(player.dimension);
                            RDPUavMod.missions().create(player.world, mission);
                        } else {
                            mission.applyOperatorAreaLock(message.x, message.y, message.z,
                                    player.world.getTotalWorldTime());
                        }
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                String.format(java.util.Locale.ROOT,
                                        "UAV sight stabilization locked at %.1f %.1f %.1f.",
                                        message.x, message.y, message.z)));
                    }
                });
                return null;
            }
        }
        public void fromBytes(ByteBuf buffer) {
            targetId = readString(buffer);
            clear = buffer.readBoolean();
        }
        public void toBytes(ByteBuf buffer) {
            writeString(buffer, targetId);
            buffer.writeBoolean(clear);
        }
        public java.util.UUID getTargetId() {
            return targetId.isEmpty() ? null : java.util.UUID.fromString(targetId);
        }
        public boolean isClear() {
            return clear;
        }
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
                        + mission.getState().name() + "  " + mission.getAircraftType()
                        + (mission.getFailureReason() == null ? ""
                        : "  [" + mission.getFailureReason() + "]");
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

    public static final class TargetTelemetry implements IMessage {
                private boolean active;
                private boolean hasPosition;
                private String missionId = "";
                private String aircraftType = "";
                private String missionState = "NO TARGET";
                private String targetState = "NO TARGET";
                private String registryId = "";
                private String entityName = "";
                private String targetUuid = "";
                private String priority = "NORMAL";
                private String scanState = "READY";
                private String deepScanState = "OFF";
                private String failureReason = "";
                private String wingmanState = "NONE";
                private String wingmanNode = "none";
                private int dimension;
                private boolean outOfRange;
                private double x;
                private double y;
                private double z;
                private double distance;
                private double velocityX, velocityY, velocityZ, confidence, bearing, elevation;
                private boolean visible;
                private String lifecycle = "NONE";
                private String markerState = "HIDDEN";

                public TargetTelemetry() { }

                private TargetTelemetry(boolean active, boolean hasPosition, String missionId, String aircraftType,
                                        String missionState, String targetState, String registryId, String targetUuid,
                                        String priority, String scanState, String deepScanState, int dimension,
                                        double x, double y, double z, double distance) {
                    this(active, hasPosition, missionId, aircraftType, missionState, targetState,
                            registryId, targetUuid, priority, scanState, deepScanState,
                            "", "NONE", "none", dimension, x, y, z, distance);
                }

                private TargetTelemetry(boolean active, boolean hasPosition, String missionId, String aircraftType,
                                        String missionState, String targetState, String registryId, String targetUuid,
                                        String priority, String scanState, String deepScanState,
                                        String failureReason, String wingmanState, String wingmanNode,
                                        int dimension, double x, double y, double z, double distance) {
                    this.active = active; this.hasPosition = hasPosition; this.missionId = missionId;
                    this.aircraftType = aircraftType; this.missionState = missionState;
                    this.targetState = targetState; this.registryId = registryId == null ? "" : registryId;
                    this.entityName = "";
                    this.targetUuid = targetUuid == null ? "" : targetUuid; this.priority = priority;
                    this.scanState = scanState; this.deepScanState = deepScanState; this.dimension = dimension;
                    this.failureReason = failureReason == null ? "" : failureReason;
                    this.wingmanState = wingmanState == null ? "NONE" : wingmanState;
                    this.wingmanNode = wingmanNode == null ? "none" : wingmanNode;
                    this.x = x; this.y = y; this.z = z; this.distance = distance;
                }

                static TargetTelemetry from(EntityPlayerMP player) {
                    MCH_EntityAircraft ridden = net.vas.rdp.uav.mission.UavControlAccess
                            .findControlledAircraft(player);
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(ridden, player)) {
                        return new TargetTelemetry(false, false, "", "", "NO TARGET", "NO TARGET",
                                null, null, "NORMAL", "READY", "OFF", player.dimension, 0, 0, 0, 0);
                    }
                    java.util.List<net.vas.rdp.uav.mission.RDPMission> missions =
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                    .getRepository().byOwner(player.getUniqueID());
                    net.vas.rdp.uav.mission.RDPMission selected = null;
                    for (net.vas.rdp.uav.mission.RDPMission mission : missions) {
                        if (ridden != null && mission.getAircraftId().equals(ridden.getUniqueID())) {
                            selected = mission;
                            break;
                        }
                    }
                    if (selected == null) return new TargetTelemetry(false, false, "", "", "NO TARGET", "NO TARGET",
                            null, null, "NORMAL", "READY", "OFF", player.dimension, 0, 0, 0, 0);

                    net.minecraft.entity.Entity aircraftEntity = null;
                    for (net.minecraft.entity.Entity entity : player.world.loadedEntityList) {
                        if (selected.getAircraftId().equals(entity.getUniqueID())) {
                            aircraftEntity = entity;
                            break;
                        }
                    }
                    net.minecraft.entity.Entity target = selected.getTargetId() == null ? null
                            : TARGET_RESOLVER.findLoaded(player.world, selected.getTargetId());
                    net.vas.rdp.uav.target.ContactSnapshot contact =
                            net.vas.rdp.uav.RDPUavMod.contact(selected.getTargetId());
                    boolean point = selected.getTargetSelectionMode()
                            == net.vas.rdp.uav.target.TargetSelectionMode.POINT;
                    boolean targetStateRequiresEntity = !point && selected.getTargetId() != null;
                    boolean lost = targetStateRequiresEntity && target == null;
                    boolean locked = selected.getState() == net.vas.rdp.uav.mission.MissionState.TARGET_LOCKED
                            || selected.getState() == net.vas.rdp.uav.mission.MissionState.ATTACK_TARGET
                            || selected.getState() == net.vas.rdp.uav.mission.MissionState.KAMIKADZE_TARGET;
                    boolean noCandidate = selected.getState()
                            == net.vas.rdp.uav.mission.MissionState.WAITING_FOR_TARGET;
                    String targetState = noCandidate ? "NO_CANDIDATE"
                            : lost ? "TARGET_LOST"
                            : locked ? "TARGET_LOCKED"
                            : selected.getState() == net.vas.rdp.uav.mission.MissionState.SEARCHING
                            ? "SEARCHING" : target == null && !point ? "NO TARGET"
                            : target != null && net.minecraft.entity.EntityList.getKey(target) == null
                            ? "TARGET_INVALID" : "TARGET_ACQUIRED";
                    double tx = point ? selected.getTargetSpec().getX()
                            : contact != null ? contact.getX() : target == null ? 0 : target.posX;
                    double ty = point ? selected.getTargetSpec().getY()
                            : contact != null ? contact.getY() : target == null ? 0 : target.posY;
                    double tz = point ? selected.getTargetSpec().getZ()
                            : contact != null ? contact.getZ() : target == null ? 0 : target.posZ;
                    double distance = aircraftEntity == null ? 0 : aircraftEntity.getDistance(tx, ty, tz);
                    String registry = point ? "rdpuav:point" : target == null ? ""
                            : String.valueOf(net.minecraft.entity.EntityList.getKey(target));
                    String scan = selected.getState() == net.vas.rdp.uav.mission.MissionState.SCANNING
                            || selected.getState() == net.vas.rdp.uav.mission.MissionState.DEEP_SCANNING
                            ? "ACTIVE" : selected.getReportId() == null ? "READY" : "COMPLETE";
                    String deep = selected.isDeepScan()
                            ? (selected.getReportId() == null ? "ACTIVE" : "COMPLETE") : "OFF";
                    com.norwood.mcheli.wingman.wingman.WingmanEntry wingman =
                            com.norwood.mcheli.wingman.wingman.WingmanRegistry.get(selected.getAircraftId());
                    String wingmanState = wingman == null ? "MISSING" : String.valueOf(wingman.autoState);
                    String wingmanNode = wingman == null || wingman.mission == null ? "none"
                            : wingman.missionIndex + "/" + wingman.mission.size()
                            + (wingman.currentNode() == null ? "" : " " + wingman.currentNode().type.name());
                    TargetTelemetry telemetry = new TargetTelemetry(true, !lost && (point || target != null), selected.getMissionId().toString(),
                            selected.getAircraftType(), selected.getState().name(), targetState, registry,
                            selected.getTargetId() == null ? null : selected.getTargetId().toString(),
                            selected.getTargetPriority().name(), scan, deep,
                            selected.getFailureReason(), wingmanState, wingmanNode, selected.getDimension(),
                            tx, ty, tz, distance);
                    telemetry.entityName = target == null ? (point ? "POINT" : "") : target.getName();
                    if (contact != null) {
                        telemetry.velocityX = contact.getVelocityX();
                        telemetry.velocityY = contact.getVelocityY();
                        telemetry.velocityZ = contact.getVelocityZ();
                        telemetry.confidence = contact.getConfidence();
                        telemetry.visible = contact.isVisible();
                        telemetry.lifecycle = contact.getLifecycle().name();
                        telemetry.markerState = contact.getMarkerState();
                        if (aircraftEntity != null) {
                            double dx = tx - aircraftEntity.posX;
                            double dz = tz - aircraftEntity.posZ;
                            telemetry.bearing = Math.toDegrees(Math.atan2(dz, dx));
                            telemetry.elevation = Math.toDegrees(Math.atan2(ty - aircraftEntity.posY,
                                    Math.sqrt(dx * dx + dz * dz)));
                        }
                    }
                    // The mission's search radius and the weapon/lock engagement radius are not the same thing.
                    // Do not let a smaller attack radius falsely mark a valid target as out of range.
                    telemetry.outOfRange = distance > selected.getMissionRangeLimit();
                    return telemetry;
                }

                public void fromBytes(ByteBuf buffer) {
                    active = buffer.readBoolean();
                    hasPosition = buffer.readBoolean();
                    missionId = readString(buffer);
                    aircraftType = readString(buffer);
                    missionState = readString(buffer);
                    targetState = readString(buffer);
                    registryId = readString(buffer);
                    entityName = readString(buffer);
                    targetUuid = readString(buffer);
                    priority = readString(buffer);
                    scanState = readString(buffer);
                    deepScanState = readString(buffer);
                    failureReason = readString(buffer);
                    wingmanState = readString(buffer);
                    wingmanNode = readString(buffer);
                    dimension = buffer.readInt();
                    outOfRange = buffer.readBoolean();
                    x = buffer.readDouble(); y = buffer.readDouble(); z = buffer.readDouble();
                    distance = buffer.readDouble();
                    velocityX = buffer.readDouble(); velocityY = buffer.readDouble(); velocityZ = buffer.readDouble();
                    confidence = buffer.readDouble(); bearing = buffer.readDouble(); elevation = buffer.readDouble();
                    visible = buffer.readBoolean(); lifecycle = readString(buffer); markerState = readString(buffer);
                }

                public void toBytes(ByteBuf buffer) {
                    buffer.writeBoolean(active).writeBoolean(hasPosition);
                    writeString(buffer, missionId); writeString(buffer, aircraftType);
                    writeString(buffer, missionState); writeString(buffer, targetState);
                    writeString(buffer, registryId); writeString(buffer, entityName); writeString(buffer, targetUuid);
                    writeString(buffer, priority); writeString(buffer, scanState);
                    writeString(buffer, deepScanState); writeString(buffer, failureReason);
                    writeString(buffer, wingmanState); writeString(buffer, wingmanNode);
                    buffer.writeInt(dimension); buffer.writeBoolean(outOfRange);
                    buffer.writeDouble(x).writeDouble(y).writeDouble(z).writeDouble(distance);
                    buffer.writeDouble(velocityX).writeDouble(velocityY).writeDouble(velocityZ);
                    buffer.writeDouble(confidence).writeDouble(bearing).writeDouble(elevation);
                    buffer.writeBoolean(visible); writeString(buffer, lifecycle); writeString(buffer, markerState);
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
            Objects.requireNonNull(player.getServer()).addScheduledTask(new Runnable() {
                public void run() { sendMissionData(player); }
            });
            return null;
        }
    }

    public static final class ServerAbortMissionHandler implements IMessageHandler<AbortMission, IMessage> {
            public IMessage onMessage(final AbortMission message, MessageContext context) {
                final EntityPlayerMP player = context.getServerHandler().player;
                Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                    try {
                        java.util.UUID id = java.util.UUID.fromString(message.missionId);
                        net.vas.rdp.uav.mission.RDPMission mission =
                                net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                        .getRepository().find(id);
                        if (mission == null || !player.getUniqueID().equals(mission.getOwnerId())
                                || mission.getState() == net.vas.rdp.uav.mission.MissionState.COMPLETE
                                || mission.getState() == net.vas.rdp.uav.mission.MissionState.ABORTED
                                || mission.getState() == net.vas.rdp.uav.mission.MissionState.FAILED) {
                                RdpUavDebug.debug("Network", "abort rejected player={} mission={}",
                                        player.getUniqueID(), message.missionId);
                                return;
                        }
                        mission.transitionTo(net.vas.rdp.uav.mission.MissionState.ABORTED,
                                player.world.getTotalWorldTime());
                        net.vas.rdp.uav.weapon.MissileGuidanceController.abort(mission.getAircraftId());
                        for (net.minecraft.entity.Entity entity : player.world.loadedEntityList) {
                            if (entity instanceof MCH_EntityAircraft
                                    && mission.getAircraftId().equals(entity.getUniqueID())) {
                                net.vas.rdp.uav.weapon.MissileGuidanceController.abort(
                                        mission.getAircraftId());
                                FLIGHT_ADAPTER.release((MCH_EntityAircraft) entity);
                                break;
                            }
                        }
                        RDPUavMod.missions().release(mission);
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                        sendMissionData(player);
                        RdpUavDebug.debug("Network", "abort accepted player={} mission={}",
                                player.getUniqueID(), message.missionId);
                    } catch (IllegalArgumentException ignored) {
                        RdpUavDebug.debug("Network", "abort rejected invalid UUID player={} value={}",
                                player.getUniqueID(), message.missionId);
                    }
                });
                return null;
            }
        }

    public static final class ServerClearMissionsHandler implements IMessageHandler<ClearMissions, IMessage> {
        public IMessage onMessage(final ClearMissions message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(new Runnable() {
                public void run() {
                    java.util.UUID selected = null;
                    if (!message.all) {
                        try { selected = java.util.UUID.fromString(message.missionId); }
                        catch (IllegalArgumentException invalid) { return; }
                    }
                    boolean changed = false;
                    for (net.vas.rdp.uav.mission.RDPMission mission :
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                    .getRepository().byOwner(player.getUniqueID())) {
                        if (!message.all && !mission.getMissionId().equals(selected)) continue;
                        for (net.minecraft.entity.Entity entity : player.world.loadedEntityList) {
                            if (entity instanceof MCH_EntityAircraft
                                    && mission.getAircraftId().equals(entity.getUniqueID())) {
                                FLIGHT_ADAPTER.release((MCH_EntityAircraft) entity);
                                break;
                            }
                        }
                        if (!mission.isTerminal()) {
                            try {
                                mission.transitionTo(net.vas.rdp.uav.mission.MissionState.ABORTED,
                                        player.world.getTotalWorldTime());
                            } catch (RuntimeException invalidState) { continue; }
                        }
                        RDPUavMod.missions().release(mission);
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                .remove(mission.getMissionId());
                        changed = true;
                    }
                    if (changed) {
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                        sendMissionData(player);
                    }
                }
            });
            return null;
        }
    }

    public static final class ServerAuthorizeAttackHandler
            implements IMessageHandler<AuthorizeAttack, IMessage> {
        public IMessage onMessage(final AuthorizeAttack message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(new Runnable() {
                public void run() {
                    try {
                        java.util.UUID id = java.util.UUID.fromString(message.missionId);
                        net.vas.rdp.uav.mission.RDPMission mission =
                                net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                        .getRepository().find(id);
                        if (mission == null || !mission.isAttack()
                                || !player.getUniqueID().equals(mission.getOwnerId())
                                || mission.getState() != net.vas.rdp.uav.mission.MissionState.TARGET_LOCKED) {
                            return;
                        }

                        MCH_EntityAircraft aircraft = null;
                        for (net.minecraft.entity.Entity entity : player.world.loadedEntityList) {
                            if (entity instanceof MCH_EntityAircraft
                                    && mission.getAircraftId().equals(entity.getUniqueID())) {
                                aircraft = (MCH_EntityAircraft) entity;
                                break;
                            }
                        }
                        if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)) return;
                        double x = mission.getTargetSpec().getX();
                        double y = mission.getTargetSpec().getY();
                        double z = mission.getTargetSpec().getZ();
                        if (mission.getTargetId() != null) {
                            net.minecraft.entity.Entity target = TARGET_RESOLVER.findLoaded(player.world, mission.getTargetId());
                            if (target == null) {
                                return;
                            }
                            x = target.posX;
                            y = target.posY;
                            z = target.posZ;
                        }
                        assert aircraft.getAcInfo() != null;
                        net.vas.rdp.uav.profile.UavVehicleProfile profile =
                                net.vas.rdp.uav.profile.UavVehicleProfiles
                                        .forAircraft(aircraft.getAcInfo().name);
                        GuidedStrikeObjective objective = mission.getTargetId() == null
                                ? GuidedStrikeObjective.square(mission.getMissionId(),
                                player.world.provider.getDimension(), x, y, z,
                                player.world.getTotalWorldTime())
                                : GuidedStrikeObjective.entity(mission.getMissionId(),
                                mission.getTargetId(), player.world.provider.getDimension(),
                                x, y, z, player.world.getTotalWorldTime());
                        objective.authorize();
                        objective.beginTracking();
                        GuidedWeaponDecision guided = GUIDED_ADAPTER.arm(player.world, aircraft,
                                objective, Math.sqrt(aircraft.motionX * aircraft.motionX
                                        + aircraft.motionY * aircraft.motionY
                                        + aircraft.motionZ * aircraft.motionZ),
                                profile.getAttackEnvelope(), true,
                                profile.getAttackWeaponPriority());
                        if (!guided.isAccepted() && !"NO_GUIDED_WEAPON".equals(guided.getReason())) {
                            RdpUavDebug.debug("Network",
                                    "mission {} guided authorize decision={} aircraft={}",
                                    mission.getMissionId(), guided.getReason(), aircraft.getUniqueID());
                            return;
                        }
                        if (!guided.isAccepted() && !FLIGHT_ADAPTER.armAttack(
                                aircraft, x, y, z, mission.getObservationDistance(), mission.getTargetId(),
                                profile.getAttackWeaponPriority())) {
                            return;
                        }
                        mission.transitionTo(net.vas.rdp.uav.mission.MissionState.ATTACK_TARGET,
                                player.world.getTotalWorldTime());
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                        sendMissionData(player);
                    } catch (IllegalArgumentException ignored) {
                        // Invalid client input is rejected without changing mission state.
                    }
                }
            });
            return null;
        }
    }

    public static final class ServerKamikazeHandler
            implements IMessageHandler<KamikazeAttack, IMessage> {
        public IMessage onMessage(final KamikazeAttack message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                try {
                    java.util.UUID id = java.util.UUID.fromString(message.missionId);
                    net.vas.rdp.uav.mission.RDPMission mission =
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                    .getRepository().find(id);
                    if (mission == null || !mission.isAttack()
                            || !player.getUniqueID().equals(mission.getOwnerId())
                            || mission.getState() != net.vas.rdp.uav.mission.MissionState.TARGET_LOCKED) return;
                    MCH_EntityAircraft aircraft = null;
                    for (net.minecraft.entity.Entity entity : player.world.loadedEntityList) {
                        if (entity instanceof MCH_EntityAircraft
                                && mission.getAircraftId().equals(entity.getUniqueID())) {
                            aircraft = (MCH_EntityAircraft) entity;
                            break;
                        }
                    }
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)) return;
                    double x = mission.getTargetSpec().getX();
                    double y = mission.getTargetSpec().getY();
                    double z = mission.getTargetSpec().getZ();
                    if (mission.getTargetId() != null) {
                        net.minecraft.entity.Entity target = TARGET_RESOLVER.findLoaded(player.world, mission.getTargetId());
                        if (target == null) return;
                        x = target.posX; y = target.posY; z = target.posZ;
                    }
                    if (!FLIGHT_ADAPTER.armKamikaze(aircraft, x, y, z,
                            mission.getObservationDistance(), mission.getTargetId())) return;
                    // Reusable and long-range airframes may only enter a
                    // destructive route through this operator-authenticated request.
                    mission.authorizeDestructiveAction();
                    mission.transitionTo(net.vas.rdp.uav.mission.MissionState.KAMIKADZE_TARGET,
                            player.world.getTotalWorldTime());
                    net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                    sendMissionData(player);
                } catch (IllegalArgumentException ignored) {
                }
            });
            return null;
        }
    }

    public static final class ServerSelfDestructHandler
            implements IMessageHandler<FireSelfDestruct, IMessage> {
        public IMessage onMessage(final FireSelfDestruct message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(new Runnable() {
                public void run() {
                    MCH_EntityAircraft aircraft =
                            net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)) return;
                    if (!FLIGHT_ADAPTER.supportsKamikaze(aircraft)
                            || !FLIGHT_ADAPTER.hasSelfDestructWeaponWithAmmo(aircraft)) return;
                    if (FLIGHT_ADAPTER.fireSelfDestructWeapon(aircraft)) {
                        RdpUavDebug.debug("Network", "player fired SelfDestruct aircraft={} player={}",
                                aircraft.getUniqueID(), player.getUniqueID());
                    }
                }
            });
            return null;
        }
    }

    public static final class ServerCreateMissionHandler
            implements IMessageHandler<CreateMission, IMessage> {
        public IMessage onMessage(final CreateMission message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(new Runnable() {
                public void run() {
                    if (!message.point && (message.target == null
                            || !message.target.matches(
                            "(?i)(mod=[A-Za-z0-9_.-]+|[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+)"))) {
                        return;
                    }

                    MCH_EntityAircraft aircraft = net.vas.rdp.uav.mission.UavControlAccess
                            .findControlledAircraft(player);
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)) return;
                    String command = message.point
                            ? "rdpuav mission attack-point"
                            : "rdpuav mission create " + message.target
                                    + (message.attack ? " attack" : "")
                                    + " " + priorityName(message.priority);
                    Objects.requireNonNull(player.getServer()).getCommandManager().executeCommand(player, command);
                }
            });
            return null;
        }

        private String priorityName(int value) {
            switch (Math.max(0, Math.min(3, value))) {
                case 0: return "low";
                case 1: return "normal";
                case 2: return "high";
                default: return "critical";
            }
        }
    }

    public static final class ServerSquareStrikeHandler
            implements IMessageHandler<SquareStrike, IMessage> {
        public IMessage onMessage(final SquareStrike message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(new Runnable() {
                public void run() {
                    MCH_EntityAircraft aircraft =
                            net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)
                            || message.parameters == null
                            || !message.parameters.matches("-?[0-9.]+\\s+-?[0-9.]+\\s+-?[0-9.]+(\\s+[0-9.]+)?")) {
                        return;
                    }

                    Objects.requireNonNull(player.getServer()).getCommandManager().executeCommand(player,
                            "rdpuav mission square " + message.parameters);
                }
            });
            return null;
        }
    }

    public static final class ServerAiTargetLockHandler
            implements IMessageHandler<AiTargetLock, IMessage> {
        public IMessage onMessage(final AiTargetLock message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(new Runnable() {
                public void run() {
                    MCH_EntityAircraft aircraft =
                            net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)
                            || !RDPUavMod.missions().ensureAuthorized(aircraft, player)) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "UAV target lock rejected: operator authorization is required."));
                        return;
                    }
                    if (!message.clear && (message.targetId == null
                            || message.targetId.trim().isEmpty())
                            && KamikazePilotOverride.targetFor(aircraft.getUniqueID()) != null) {
                        clearTargetLock(player, aircraft);
                        return;
                    }
                    if (message.clear) {
                        clearTargetLock(player, aircraft);
                        return;
                    }
                    try {
                        net.vas.rdp.uav.profile.UavVehicleProfile profile =
                                aircraft.getAcInfo() == null ? null
                                : net.vas.rdp.uav.profile.UavVehicleProfiles.forAircraft(
                                aircraft.getAcInfo().name);
                        java.util.UUID targetId = message.targetId == null
                                || message.targetId.trim().isEmpty() ? null
                                : java.util.UUID.fromString(message.targetId);
                        net.minecraft.entity.Entity target = targetId == null ? null
                                : TARGET_RESOLVER.findLoaded(player.world, targetId);
                        if (target == null && targetId == null && profile != null) {
                            double bestDistance = Double.MAX_VALUE;
                            for (net.minecraft.entity.Entity candidate
                                    : player.world.loadedEntityList) {
                                if (candidate == null || candidate == aircraft
                                        || candidate.isDead || !candidate.canBeCollidedWith()
                                        || candidate.getDistance(aircraft) > profile.getLockRange()
                                        || !net.vas.rdp.uav.target.TargetPolicy.isSelectableContact(
                                        player.world, candidate, player)) {
                                    continue;
                                }
                                double distance = candidate.getDistanceSq(aircraft);
                                if (distance < bestDistance) {
                                    bestDistance = distance;
                                    target = candidate;
                                }
                            }
                            if (target != null) {
                                targetId = target.getUniqueID();
                                RdpUavDebug.debug("Network",
                                        "AI target lock source=FILTER_FALLBACK aircraft={} target={}",
                                        aircraft.getUniqueID(), targetId);
                            }
                        }
                        if (target == null || target == aircraft || !target.canBeCollidedWith()
                                || aircraft.getAcInfo() == null) {
                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    "UAV target lock rejected: target is unavailable."));
                            return;
                        }
                        if (!net.vas.rdp.uav.target.TargetPolicy.isSelectableContact(
                                player.world, target, player)) {
                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    "UAV target lock rejected: target filter excludes this contact."));
                            return;
                        }
                        assert profile != null;
                        if (target.getDistance(aircraft) > profile.getLockRange()) {
                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    "UAV target lock rejected: target is outside lock range."));
                            return;
                        }
                        net.minecraft.util.ResourceLocation key =
                                net.minecraft.entity.EntityList.getKey(target);
                        if (key == null) {
                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    "UAV target lock rejected: target type is unknown."));
                            return;
                        }
                        java.util.UUID assigned = RDPUavMod.missions()
                                .assignedMission(aircraft.getUniqueID());
                        net.vas.rdp.uav.mission.RDPMission mission = assigned == null ? null
                                : net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                .getRepository().find(assigned);
                        if (mission == null) {
                            mission = new net.vas.rdp.uav.mission.RDPMission(
                                    java.util.UUID.randomUUID(), player.getUniqueID(),
                                    aircraft.getUniqueID(), aircraft.getAcInfo().name,
                                    net.vas.rdp.uav.mission.MissionType.RECONNAISSANCE,
                                    net.vas.rdp.uav.target.TargetSpec.manual(
                                            key.toString(),
                                            net.vas.rdp.uav.target.TargetPriority.HIGH),
                                    false, false, false, profile.getDetectionRange(), 8.0D,
                                    player.world.getTotalWorldTime());
                            mission.setDimension(player.dimension);
                            RDPUavMod.missions().create(player.world, mission);
                            mission.applyOperatorEntityLock(targetId,
                                    player.world.getTotalWorldTime());
                        } else {
                            mission.applyOperatorEntityLock(targetId,
                                    player.world.getTotalWorldTime());
                        }
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                        KamikazePilotOverride.lock(aircraft.getUniqueID(), targetId);
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "UAV target lock accepted: AI control override active for "
                                        + key.toString()
                                        + ". Press the target-lock key again to restore player control."));
                        RdpUavDebug.debug("Network", "AI target lock accepted player={} aircraft={} target={}",
                                player.getUniqueID(), aircraft.getUniqueID(), targetId);
                    } catch (IllegalArgumentException failure) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "UAV target lock rejected: invalid target."));
                        RdpUavDebug.debug("Network", "AI target lock rejected invalid target={}",
                                message.targetId);
                    }
                }
            });
            return null;
        }

        private void clearTargetLock(EntityPlayerMP player, MCH_EntityAircraft aircraft) {
            KamikazePilotOverride.clear(aircraft.getUniqueID());
            java.util.List<net.vas.rdp.uav.mission.RDPMission> missions =
                    net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                            .getRepository().byOwner(player.getUniqueID());
            for (net.vas.rdp.uav.mission.RDPMission mission : missions) {
                if (!aircraft.getUniqueID().equals(mission.getAircraftId())
                        || mission.isTerminal()) continue;
                mission.transitionTo(net.vas.rdp.uav.mission.MissionState.ABORTED,
                        player.world.getTotalWorldTime());
                RDPUavMod.missions().release(mission);
            }
            FLIGHT_ADAPTER.release(aircraft);
            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
            RdpUavDebug.debug("Network", "AI target lock cleared; player control restored player={} aircraft={}",
                    player.getUniqueID(), aircraft.getUniqueID());
            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                    "UAV target lock OFF; player control restored."));
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

        public static final class ClientTargetTelemetryHandler
                implements IMessageHandler<RdpUavNetwork.TargetTelemetry, IMessage> {
            public IMessage onMessage(final RdpUavNetwork.TargetTelemetry message, MessageContext context) {
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    public void run() {
                        net.vas.rdp.uav.client.RdpUavTargetHud.Telemetry hud =
                                new net.vas.rdp.uav.client.RdpUavTargetHud.Telemetry(
                                        message.active, message.hasPosition, message.missionId,
                                        message.aircraftType, message.missionState, message.targetState,
                                        message.registryId.isEmpty() ? null : message.registryId,
                                        message.entityName,
                                        message.targetUuid.isEmpty() ? null : message.targetUuid,
                                        message.priority, message.scanState, message.deepScanState,
                                        message.failureReason, message.wingmanState, message.wingmanNode,
                                        message.dimension, message.outOfRange,
                                        message.x, message.y, message.z, message.distance);
                        hud.setRichTelemetry(message.velocityX, message.velocityY, message.velocityZ,
                                message.confidence, message.bearing, message.elevation,
                                message.visible, message.lifecycle, message.markerState);
                        net.vas.rdp.uav.client.RdpUavTargetHud.instance().receive(hud);
                    }
                });
                return null;
            }
        }
    }
}
