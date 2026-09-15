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
        CHANNEL.registerMessage(ServerAuthorizeAttackHandler.class, AuthorizeAttack.class, 4, Side.SERVER);
        CHANNEL.registerMessage(ServerCreateMissionHandler.class, CreateMission.class, 5, Side.SERVER);
        CHANNEL.registerMessage(ServerKamikazeHandler.class, KamikazeAttack.class, 6, Side.SERVER);
        CHANNEL.registerMessage(ServerSelfDestructHandler.class, FireSelfDestruct.class, 10, Side.SERVER);
        CHANNEL.registerMessage(ClientMissionDataHandler.ClientTargetTelemetryHandler.class,
                TargetTelemetry.class, 7, Side.CLIENT);
        CHANNEL.registerMessage(ServerSquareStrikeHandler.class, SquareStrike.class, 8, Side.SERVER);
        CHANNEL.registerMessage(ServerAiTargetLockHandler.class, AiTargetLock.class, 9, Side.SERVER);
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
        if (target == null || !target.matches("[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+")) return;
        CHANNEL.sendToServer(new CreateMission(target, attack, false));
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

    public static void clearAiTargetLock() {
        CHANNEL.sendToServer(new AiTargetLock(null, true));
    }

    static void sendMissionData(EntityPlayerMP player) {
        CHANNEL.sendTo(MissionData.from(player), player);
    }

    public static void syncTargetTelemetry(EntityPlayerMP player) {
        RdpUavDebug.debug("Network", "sending telemetry player={} dimension={}",
                player.getUniqueID(), player.dimension);
        CHANNEL.sendTo(TargetTelemetry.from(player), player);
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

        public CreateMission() { }
        public CreateMission(String target, boolean attack, boolean point) {
            this.target = target == null ? "" : target;
            this.attack = attack;
            this.point = point;
        }

        public void fromBytes(ByteBuf buffer) {
            target = readString(buffer);
            attack = buffer.readBoolean();
            point = buffer.readBoolean();
        }

        public void toBytes(ByteBuf buffer) {
            writeString(buffer, target);
            buffer.writeBoolean(attack);
            buffer.writeBoolean(point);
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
        public void fromBytes(ByteBuf buffer) {
            targetId = readString(buffer);
            clear = buffer.readBoolean();
        }
        public void toBytes(ByteBuf buffer) {
            writeString(buffer, targetId);
            buffer.writeBoolean(clear);
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
                            : new net.vas.rdp.uav.target.TargetResolver().findLoaded(player.world, selected.getTargetId());
                    boolean point = selected.getTargetSelectionMode()
                            == net.vas.rdp.uav.target.TargetSelectionMode.POINT;
                    boolean targetStateRequiresEntity = !point && selected.getTargetId() != null;
                    boolean lost = targetStateRequiresEntity && target == null;
                    boolean locked = selected.getState() == net.vas.rdp.uav.mission.MissionState.TARGET_LOCKED
                            || selected.getState() == net.vas.rdp.uav.mission.MissionState.ATTACK_TARGET
                            || selected.getState() == net.vas.rdp.uav.mission.MissionState.KAMIKADZE_TARGET;
                    String targetState = lost ? "TARGET_LOST"
                            : locked ? "TARGET_LOCKED"
                            : selected.getState() == net.vas.rdp.uav.mission.MissionState.SEARCHING
                            ? "SEARCHING" : target == null && !point ? "NO TARGET"
                            : target != null && net.minecraft.entity.EntityList.getKey(target) == null
                            ? "TARGET_INVALID" : "TARGET_ACQUIRED";
                    double tx = point ? selected.getTargetSpec().getX() : target == null ? 0 : target.posX;
                    double ty = point ? selected.getTargetSpec().getY() : target == null ? 0 : target.posY;
                    double tz = point ? selected.getTargetSpec().getZ() : target == null ? 0 : target.posZ;
                    double distance = aircraftEntity == null ? 0 : aircraftEntity.getDistance(tx, ty, tz);
                    String registry = point ? "rdpuav:point" : target == null ? selected.getTargetSpec().getRegistryId()
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
                                    RdpUavDebug.debug("Network", "abort rejected player={} mission={}",
                                            player.getUniqueID(), message.missionId);
                                    return;
                            }

                            mission.transitionTo(net.vas.rdp.uav.mission.MissionState.ABORTED,
                                    player.world.getTotalWorldTime());
                            for (net.minecraft.entity.Entity entity : player.world.loadedEntityList) {
                                if (entity instanceof MCH_EntityAircraft
                                        && mission.getAircraftId().equals(entity.getUniqueID())) {
                                    new net.vas.rdp.uav.flight.WingManFlightAdapter()
                                            .release((MCH_EntityAircraft) entity);
                                    break;
                                }
                            }
                            net.vas.rdp.uav.RDPUavMod.missions().release(mission);
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                            sendMissionData(player);
                            RdpUavDebug.debug("Network", "abort accepted player={} mission={}",
                                    player.getUniqueID(), message.missionId);
                        } catch (IllegalArgumentException ignored) {
                            // Invalid client input is rejected without touching mission state.
                            RdpUavDebug.debug("Network", "abort rejected invalid UUID player={} value={}",
                                    player.getUniqueID(), message.missionId);
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
            player.getServer().addScheduledTask(new Runnable() {
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
                            net.minecraft.entity.Entity target = new net.vas.rdp.uav.target.TargetResolver()
                                    .findLoaded(player.world, mission.getTargetId());
                            if (target == null) {
                                return;
                            }
                            x = target.posX;
                            y = target.posY;
                            z = target.posZ;
                        }
                        if (!new net.vas.rdp.uav.flight.WingManFlightAdapter().armAttack(
                                aircraft, x, y, z, mission.getObservationDistance(), mission.getTargetId())) {
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
            player.getServer().addScheduledTask(new Runnable() {
                public void run() {
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
                            net.minecraft.entity.Entity target = new net.vas.rdp.uav.target.TargetResolver()
                                    .findLoaded(player.world, mission.getTargetId());
                            if (target == null) return;
                            x = target.posX; y = target.posY; z = target.posZ;
                        }
                        net.vas.rdp.uav.flight.WingManFlightAdapter adapter =
                                new net.vas.rdp.uav.flight.WingManFlightAdapter();
                        if (!adapter.armKamikaze(aircraft, x, y, z,
                                mission.getObservationDistance(), mission.getTargetId())) return;
                        mission.transitionTo(net.vas.rdp.uav.mission.MissionState.KAMIKADZE_TARGET,
                                player.world.getTotalWorldTime());
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                        sendMissionData(player);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            });
            return null;
        }
    }

    public static final class ServerSelfDestructHandler
            implements IMessageHandler<FireSelfDestruct, IMessage> {
        public IMessage onMessage(final FireSelfDestruct message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServer().addScheduledTask(new Runnable() {
                public void run() {
                    MCH_EntityAircraft aircraft =
                            net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)) return;
                    net.vas.rdp.uav.flight.WingManFlightAdapter adapter =
                            new net.vas.rdp.uav.flight.WingManFlightAdapter();
                    if (!adapter.supportsKamikaze(aircraft)
                            || !adapter.hasSelfDestructWeaponWithAmmo(aircraft)) return;
                    if (adapter.fireSelfDestructWeapon(aircraft)) {
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
            player.getServer().addScheduledTask(new Runnable() {
                public void run() {
                    if (!message.point && (message.target == null
                            || !message.target.matches("[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+"))) {
                        return;
                    }

                    MCH_EntityAircraft aircraft = net.vas.rdp.uav.mission.UavControlAccess
                            .findControlledAircraft(player);
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)) return;
                    String command = message.point
                            ? "rdpuav mission attack-point"
                            : "rdpuav mission create " + message.target
                                    + (message.attack ? " attack" : "");
                    player.getServer().getCommandManager().executeCommand(player, command);
                }
            });
            return null;
        }
    }

    public static final class ServerSquareStrikeHandler
            implements IMessageHandler<SquareStrike, IMessage> {
        public IMessage onMessage(final SquareStrike message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServer().addScheduledTask(new Runnable() {
                public void run() {
                    MCH_EntityAircraft aircraft =
                            net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)
                            || message.parameters == null
                            || !message.parameters.matches("-?[0-9.]+\\s+-?[0-9.]+\\s+-?[0-9.]+(\\s+[0-9.]+)?")) {
                        return;
                    }

                    player.getServer().getCommandManager().executeCommand(player,
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
            player.getServer().addScheduledTask(new Runnable() {
                public void run() {
                    MCH_EntityAircraft aircraft =
                            net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)
                            || aircraft == null) return;
                    if (message.clear) {
                        KamikazePilotOverride.clear(aircraft.getUniqueID());
                        java.util.List<net.vas.rdp.uav.mission.RDPMission> missions =
                                net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                        .getRepository().byOwner(player.getUniqueID());
                        for (net.vas.rdp.uav.mission.RDPMission mission : missions) {
                            if (!aircraft.getUniqueID().equals(mission.getAircraftId())
                                    || mission.getState() == net.vas.rdp.uav.mission.MissionState.COMPLETE
                                    || mission.getState() == net.vas.rdp.uav.mission.MissionState.ABORTED
                                    || mission.getState() == net.vas.rdp.uav.mission.MissionState.FAILED
                                    || mission.getState() == net.vas.rdp.uav.mission.MissionState.KAMIKADZE_DETONATED) {
                                continue;
                            }
                            net.vas.rdp.uav.flight.WingManFlightAdapter adapter =
                                    new net.vas.rdp.uav.flight.WingManFlightAdapter();
                            if (mission.getState()
                                    == net.vas.rdp.uav.mission.MissionState.KAMIKADZE_TARGET) {
                                adapter.requestReturn(aircraft, "RDP_AI_TARGET_LOCK_CLEARED");
                                mission.transitionTo(net.vas.rdp.uav.mission.MissionState.RETURNING,
                                        player.world.getTotalWorldTime());
                            } else {
                                mission.transitionTo(net.vas.rdp.uav.mission.MissionState.ABORTED,
                                        player.world.getTotalWorldTime());
                                adapter.release(aircraft);
                                RDPUavMod.missions().release(mission);
                            }
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                            break;
                        }
                        RdpUavDebug.debug("Network", "AI target lock cleared player={} aircraft={}",
                                player.getUniqueID(), aircraft.getUniqueID());
                        return;
                    }
                    try {
                        java.util.UUID targetId = java.util.UUID.fromString(message.targetId);
                        net.minecraft.entity.Entity target = new net.vas.rdp.uav.target.TargetResolver()
                                .findLoaded(player.world, targetId);
                        net.vas.rdp.uav.flight.WingManFlightAdapter adapter =
                                new net.vas.rdp.uav.flight.WingManFlightAdapter();
                        if (target == null || target == aircraft || !target.canBeCollidedWith()
                                || aircraft.getAcInfo() == null
                                || !adapter.hasSelfDestructWeaponWithAmmo(aircraft)
                                || !aircraft.canLockEntity(target)) return;
                        if (RDPUavMod.missions().assignedMission(aircraft.getUniqueID()) != null) return;
                        net.minecraft.util.ResourceLocation key =
                                net.minecraft.entity.EntityList.getKey(target);
                        if (key == null) return;
                        net.vas.rdp.uav.mission.RDPMission mission =
                                new net.vas.rdp.uav.mission.RDPMission(
                                        java.util.UUID.randomUUID(), player.getUniqueID(),
                                        aircraft.getUniqueID(), aircraft.getAcInfo().name,
                                        net.vas.rdp.uav.mission.MissionType.STRIKE,
                                        net.vas.rdp.uav.target.TargetSpec.manual(
                                                key.toString(),
                                                net.vas.rdp.uav.target.TargetPriority.HIGH),
                                        true, true, false, 5000.0D, 8.0D,
                                        player.world.getTotalWorldTime());
                        mission.setDimension(player.dimension);
                        RDPUavMod.missions().create(player.world, mission);
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                        KamikazePilotOverride.lock(aircraft.getUniqueID(), targetId);
                        RdpUavDebug.debug("Network", "AI target lock accepted player={} aircraft={} target={}",
                                player.getUniqueID(), aircraft.getUniqueID(), targetId);
                    } catch (IllegalArgumentException ignored) {
                        RdpUavDebug.debug("Network", "AI target lock rejected invalid target={}",
                                message.targetId);
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

        public static final class ClientTargetTelemetryHandler
                implements IMessageHandler<RdpUavNetwork.TargetTelemetry, IMessage> {
            public IMessage onMessage(final RdpUavNetwork.TargetTelemetry message, MessageContext context) {
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    public void run() {
                        net.vas.rdp.uav.client.RdpUavTargetHud.instance().receive(
                                new net.vas.rdp.uav.client.RdpUavTargetHud.Telemetry(
                                        message.active, message.hasPosition, message.missionId,
                                        message.aircraftType, message.missionState, message.targetState,
                                        message.registryId.isEmpty() ? null : message.registryId,
                                        message.entityName,
                                        message.targetUuid.isEmpty() ? null : message.targetUuid,
                                        message.priority, message.scanState, message.deepScanState,
                                        message.failureReason, message.wingmanState, message.wingmanNode,
                                        message.dimension, message.outOfRange,
                                        message.x, message.y, message.z, message.distance));
                    }
                });
                return null;
            }
        }
    }
}
