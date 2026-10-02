package net.vas.rdp.uav.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.vas.rdp.uav.RDPUavMod;
import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.weapon.MCH_WeaponSet;
import com.norwood.mcheli.weapon.MCH_EntityBaseBullet;
import net.vas.rdp.uav.RdpUavDebug;
import net.vas.rdp.uav.flight.KamikazePilotOverride;
import net.vas.rdp.uav.weapon.GuidedStrikeObjective;
import net.vas.rdp.uav.weapon.GuidedWeaponAdapter;
import net.vas.rdp.uav.weapon.GuidedWeaponDecision;
import net.vas.rdp.uav.mission.MissionDefinition;
import net.vas.rdp.uav.mission.MissionValidationResult;

import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RdpUavNetwork {
    private static final Logger LOGGER = LogManager.getLogger("RDP-UAV/Network");
    private static final int MAX_PACKET_STRING_LENGTH = 1024;
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
        CHANNEL.registerMessage(ServerAbortMissionHandler.class, AbortMission.class, 3, Side.SERVER);
        CHANNEL.registerMessage(ServerAuthorizeAttackHandler.class, AuthorizeAttack.class, 4, Side.SERVER);
        CHANNEL.registerMessage(ServerCreateMissionHandler.class, CreateMission.class, 5, Side.SERVER);
        CHANNEL.registerMessage(ServerKamikazeHandler.class, KamikazeAttack.class, 6, Side.SERVER);
        CHANNEL.registerMessage(ServerSelfDestructHandler.class, FireSelfDestruct.class, 10, Side.SERVER);
        CHANNEL.registerMessage(ServerSquareStrikeHandler.class, SquareStrike.class, 8, Side.SERVER);
        CHANNEL.registerMessage(ServerAiTargetLockHandler.class, AiTargetLock.class, 9, Side.SERVER);
        CHANNEL.registerMessage(ServerContactSelectionHandler.class, ContactSelection.class, 12, Side.SERVER);
        CHANNEL.registerMessage(ServerClearMissionsHandler.class, ClearMissions.class, 13, Side.SERVER);
        CHANNEL.registerMessage(ServerReturnToBaseHandler.class, ReturnToBase.class, 14, Side.SERVER);
        CHANNEL.registerMessage(AiTargetLock.ServerAreaLockHandler.class,
                AiTargetLock.AreaLock.class, 15, Side.SERVER);
        CHANNEL.registerMessage(ClientSwarmTelemetryHandler.ServerSwarmConfigHandler.class,
                ClientSwarmTelemetryHandler.SwarmConfigUpdate.class, 16, Side.SERVER);
        CHANNEL.registerMessage(ClientSwarmTelemetryHandler.ServerSwarmTargetHandler.class,
                ClientSwarmTelemetryHandler.SwarmTargetUpdate.class, 17, Side.SERVER);
        CHANNEL.registerMessage(ServerAdoptProjectileHandler.class, AdoptProjectile.class, 18, Side.SERVER);
        CHANNEL.registerMessage(ServerReassignGuidedHandler.class, ReassignGuided.class, 19, Side.SERVER);
        CHANNEL.registerMessage(AiTargetLock.ServerRegistryTargetAssignmentHandler.class,
                AiTargetLock.RegistryTargetAssignment.class, 20, Side.SERVER);
        CHANNEL.registerMessage(ServerMissionDefinitionHandler.class,
                CreateMission.LaunchMissionDefinition.class, 21, Side.SERVER);
        CHANNEL.registerMessage(ServerCameraAimHandler.class, CameraAim.class, 22, Side.SERVER);
        CHANNEL.registerMessage(ServerOperatorTrimHandler.class, OperatorTrim.class, 23, Side.SERVER);
        CHANNEL.registerMessage(ServerLinkedRetaskHandler.class, LinkedRetask.class, 24, Side.SERVER);
        if (FMLCommonHandler.instance().getSide().isClient()) {
            CHANNEL.registerMessage(ClientMissionControlHandler.class,
                    OpenMissionControl.class, 1, Side.CLIENT);
            CHANNEL.registerMessage(ClientMissionDataHandler.class,
                    MissionData.class, 2, Side.CLIENT);
            CHANNEL.registerMessage(ClientMissionDataHandler.ClientTargetTelemetryHandler.class,
                    TargetTelemetry.class, 7, Side.CLIENT);
            CHANNEL.registerMessage(ClientSwarmTelemetryHandler.class,
                    SwarmTelemetry.class, 11, Side.CLIENT);
        }
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

    @SuppressWarnings("unused")
    public static void createMission(String target, boolean attack) {
        createMission(target, attack, 2);
    }

    public static void createMission(String target, boolean attack, int priority) {
        createMission(target, attack, priority, "");
    }

    public static void createMission(String target, boolean attack, int priority,
                                     String weapon) {
        if (target == null || !target.matches(
                "(?i)(mod=[A-Za-z0-9_.-]+|[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+)")) return;
        CHANNEL.sendToServer(new CreateMission(target, attack, false, priority, weapon));
    }

    /** @deprecated Point tasks must include a client-camera hit designation. */
    @Deprecated
    public static void createPointMission() {
        CHANNEL.sendToServer(new CreateMission("", false, true));
    }

    public static void submitPointAimBlock(BlockPos blockPos, Vec3d hitVec) {
        if (blockPos == null || hitVec == null
                || !Double.isFinite(hitVec.x) || !Double.isFinite(hitVec.y)
                || !Double.isFinite(hitVec.z)) return;
        CHANNEL.sendToServer(CameraAim.block(blockPos, hitVec));
    }

    public static void submitPointAimEntity(java.util.UUID targetId) {
        if (targetId != null) CHANNEL.sendToServer(CameraAim.entity(targetId));
    }

    /** Send only camera-relative deltas; the client never supplies aircraft attitude. */
    public static void sendOperatorTrim(java.util.UUID aircraftId, java.util.UUID missionId,
                                        long sequence, long clientTick,
                                        float yawDelta, float pitchDelta) {
        if (aircraftId == null || missionId == null || sequence < 0L || clientTick < 0L
                || !Float.isFinite(yawDelta) || !Float.isFinite(pitchDelta)) return;
        CHANNEL.sendToServer(new OperatorTrim(aircraftId, missionId, sequence,
                clientTick, yawDelta, pitchDelta));
    }

    public static void retaskLinkedStrike(String parentMissionId, java.util.UUID targetId) {
        if (parentMissionId == null || targetId == null) return;
        CHANNEL.sendToServer(new LinkedRetask(parentMissionId, targetId));
    }

    public static boolean launchMissionDefinition(MissionDefinition definition) {
        if (definition == null) return false;
        MissionValidationResult validation = MissionValidationResult.validate(definition);
        if (!validation.isValid()) return false;
        if (definition.getMissionType() == net.vas.rdp.uav.mission.MissionType.SQUARE_STRIKE) {
            if (definition.isSwarmEnabled() || !definition.getSelectedUavs().isEmpty()
                    || definition.isDefaultTargetPolicy()) return false;
            createSquareStrike(definition.getTarget(), definition.getGuidedWeapon());
            return true;
        }
        if (definition.getMissionType() != net.vas.rdp.uav.mission.MissionType.PATROL
                && isLaunchSupported(definition)) return false;
        CHANNEL.sendToServer(new CreateMission.LaunchMissionDefinition(definition));
        return true;
    }

    private static boolean isLaunchSupported(MissionDefinition definition) {
        return definition.getMissionType() == net.vas.rdp.uav.mission.MissionType.SQUARE_STRIKE
                || definition.isSwarmEnabled()
                || !definition.getSelectedUavs().isEmpty()
                || definition.isDefaultTargetPolicy()
                || !"DEFAULT".equals(definition.getSearchMode())
                || !"CONTINUOUS".equals(definition.getTrackingMode())
                || !"AI_APPROACH".equals(definition.getGuidanceMode())
                || !"MISSION_DEFINED".equals(definition.getExecutionMode())
                || !"RETURN_TO_BASE".equals(definition.getRecoveryMode());
    }

    public static void adoptLaunchedProjectile() {
        CHANNEL.sendToServer(new AdoptProjectile());
    }

    public static void reassignGuidedMission(String missionId, java.util.UUID targetId) {
        if (missionId != null && targetId != null) {
            CHANNEL.sendToServer(new ReassignGuided(missionId, targetId));
        }
    }

    @SuppressWarnings("unused")
    public static void createSquareStrike(String parameters) {
        CHANNEL.sendToServer(new SquareStrike(parameters, ""));
    }

    public static void createSquareStrike(String parameters, String weapon) {
        CHANNEL.sendToServer(new SquareStrike(parameters, weapon));
    }

    public static void lockAiTarget(java.util.UUID targetId) {
        if (targetId != null) CHANNEL.sendToServer(new AiTargetLock(targetId, false));
    }

    public static void assignRegistryTarget(String missionId, String registryId) {
        if (missionId != null && registryId != null && !missionId.trim().isEmpty()
                && !registryId.trim().isEmpty()) {
            CHANNEL.sendToServer(new AiTargetLock.RegistryTargetAssignment(missionId, registryId));
        }
    }

    /** Requests a camera-independent lock using the selected/filter fallback. */
    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public static void clearContactSelection() {
        CHANNEL.sendToServer(new ContactSelection(false, true));
    }

    public static void updateSwarmConfig(String swarmId, int maximumMembers,
                                         boolean participation, boolean allowDuplicates) {
        if (swarmId == null || swarmId.trim().isEmpty()) return;
        CHANNEL.sendToServer(new ClientSwarmTelemetryHandler.SwarmConfigUpdate(swarmId, maximumMembers,
                participation, allowDuplicates));
    }

    public static void assignSwarmTarget(String swarmId, java.util.UUID targetId,
                                         boolean allowDuplicate) {
        if (swarmId == null || swarmId.trim().isEmpty() || targetId == null) return;
        CHANNEL.sendToServer(new ClientSwarmTelemetryHandler.SwarmTargetUpdate(
                swarmId, targetId, false, allowDuplicate));
    }

    public static void releaseSwarmTarget(String swarmId) {
        if (swarmId == null || swarmId.trim().isEmpty()) return;
        CHANNEL.sendToServer(new ClientSwarmTelemetryHandler.SwarmTargetUpdate(
                swarmId, null, true, false));
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
        private String target = "", targetState = "";
        private int members, availableMembers;
        public static SwarmTelemetry from(net.vas.rdp.uav.swarm.SwarmRecord swarm) {
            SwarmTelemetry value = new SwarmTelemetry();
            value.id = swarm.getSwarmId().toString(); value.name = swarm.getName();
            value.state = swarm.getState().name(); value.formation = swarm.getFormation().name();
            value.leader = String.valueOf(swarm.referenceMember()); value.members = swarm.getMembers().size();
            net.vas.rdp.uav.swarm.SwarmStatus status =
                    net.vas.rdp.uav.swarm.SwarmStatus.from(swarm);
            value.availableMembers = status.getAvailableMembers();
            value.target = String.valueOf(status.getTargetId());
            value.targetState = status.getTargetState().name();
            return value;
        }

        public void fromBytes(ByteBuf buffer) {
            id = readString(buffer); name = readString(buffer); state = readString(buffer);
            formation = readString(buffer); leader = readString(buffer);
            target = readString(buffer); targetState = readString(buffer);
            members = buffer.readUnsignedByte(); availableMembers = buffer.readUnsignedByte();
        }

        public void toBytes(ByteBuf buffer) {
            writeString(buffer, id); writeString(buffer, name); writeString(buffer, state);
            writeString(buffer, formation); writeString(buffer, leader);
            writeString(buffer, target); writeString(buffer, targetState);
            buffer.writeByte(Math.min(255, Math.max(0, members)));
            buffer.writeByte(Math.min(255, Math.max(0, availableMembers)));
        }
    }

    public static final class ClientSwarmTelemetryHandler implements IMessageHandler<SwarmTelemetry, IMessage> {
        public IMessage onMessage(SwarmTelemetry message, MessageContext context) {
            return null;
        }

        public static final class SwarmTargetUpdate implements IMessage {
            private String swarmId = "";
            private String targetId = "";
            private boolean release;
            private boolean allowDuplicate;

            public SwarmTargetUpdate() { }

            public SwarmTargetUpdate(String swarmId, java.util.UUID targetId,
                                     boolean release, boolean allowDuplicate) {
                this.swarmId = swarmId == null ? "" : swarmId;
                this.targetId = targetId == null ? "" : targetId.toString();
                this.release = release;
                this.allowDuplicate = allowDuplicate;
            }

            public void fromBytes(ByteBuf buffer) {
                swarmId = readString(buffer);
                targetId = readString(buffer);
                release = buffer.readBoolean();
                allowDuplicate = buffer.readBoolean();
            }

            public void toBytes(ByteBuf buffer) {
                writeString(buffer, swarmId);
                writeString(buffer, targetId);
                buffer.writeBoolean(release);
                buffer.writeBoolean(allowDuplicate);
            }
        }

        public static final class ServerSwarmTargetHandler
                implements IMessageHandler<SwarmTargetUpdate, IMessage> {
            public IMessage onMessage(final SwarmTargetUpdate message, MessageContext context) {
                final EntityPlayerMP player = context.getServerHandler().player;
                Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                    try {
                        java.util.UUID swarmId = java.util.UUID.fromString(message.swarmId);
                        MCH_EntityAircraft aircraft =
                                net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                        if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)) {
                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    "Swarm target rejected: operator control is required."));
                            return;
                        }
                        boolean success;
                        if (message.release) {
                            success = RDPUavMod.swarms().releaseTarget(player.world,
                                    player.getUniqueID(), swarmId, aircraft.getUniqueID());
                        } else {
                            java.util.UUID targetId = java.util.UUID.fromString(message.targetId);
                            net.minecraft.entity.Entity target =
                                    TARGET_RESOLVER.findLoaded(player.world, targetId);
                            if (target == null || target == aircraft || target.isDead
                                    || !target.canBeCollidedWith()) {
                                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                        "Swarm target rejected: target is unavailable."));
                                return;
                            }
                            success = RDPUavMod.swarms().assignTarget(player.world,
                                    player.getUniqueID(), swarmId, aircraft.getUniqueID(),
                                    targetId, message.allowDuplicate);
                        }
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                success ? (message.release ? "Swarm target released."
                                        : "Swarm target assigned.")
                                        : "Swarm target rejected: swarm/member authorization or duplicate policy."));
                        if (success) syncSwarmTelemetry(player);
                    } catch (IllegalArgumentException invalid) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Swarm target rejected: invalid swarm or target ID."));
                    }
                });
                return null;
            }
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
                Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                    try {
                        java.util.UUID id = java.util.UUID.fromString(message.swarmId);
                        net.vas.rdp.uav.swarm.SwarmRecord record =
                                RDPUavMod.swarms().find(player.world, id);
                        if (RDPUavMod.swarms().owns(record, player.getUniqueID())) {
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
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                try {
                    java.util.UUID id = message.missionId == null
                            || message.missionId.trim().isEmpty()
                            ? null : java.util.UUID.fromString(message.missionId);
                    net.vas.rdp.uav.mission.RDPMission mission =
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                    .getRepository().find(id);
                    if (mission == null) {
                        MCH_EntityAircraft controlled =
                                net.vas.rdp.uav.mission.UavControlAccess
                                        .findControlledAircraft(player);
                        if (controlled != null) {
                            for (net.vas.rdp.uav.mission.RDPMission candidate :
                                    net.vas.rdp.uav.persistence.RDPMissionWorldData
                                            .get(player.world).getRepository()
                                            .byOwner(player.getUniqueID())) {
                                if (controlled.getUniqueID().equals(candidate.getAircraftId())
                                        && !candidate.isTerminal()) {
                                    mission = candidate;
                                    break;
                                }
                            }
                        }
                    }
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
                            || FLIGHT_ADAPTER.requestReturn(aircraft, "RDP_PLAYER_RTB")) return;
                    mission.transitionTo(net.vas.rdp.uav.mission.MissionState.RETURNING,
                            player.world.getTotalWorldTime());
                    net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                    sendMissionData(player);
                } catch (IllegalArgumentException ignored) {
                    RdpUavDebug.debug("Network", "RTB rejected invalid mission={}", message.missionId);
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
        private String weapon = "";

        public CreateMission() { }
        public CreateMission(String target, boolean attack, boolean point) {
            this(target, attack, point, 2);
        }

        public CreateMission(String target, boolean attack, boolean point, int priority) {
            this(target, attack, point, priority, "");
        }
        public CreateMission(String target, boolean attack, boolean point, int priority,
                             String weapon) {
            this.target = target == null ? "" : target;
            this.attack = attack;
            this.point = point;
            this.priority = Math.max(0, Math.min(3, priority));
            this.weapon = weapon == null ? "" : weapon;
        }

        public static final class LaunchMissionDefinition implements IMessage {
            private int version = 3;
            private String type = "";
            private String target = "";
            private boolean attack;
            private int priority = 1;
            private String weapon = "";
            private boolean swarmEnabled;
            private int swarmMaximumMembers = 1;
            private boolean unsupportedConfiguration;
            private String policyInclude = "";
            private String policyRequire = "";
            private String policyExclude = "";
            private boolean policyRejectEmbedded = true;
            private boolean policyAllowVehicles = true;
            private double policyMinimumConfidence;
            private String patrolRoute = "";

            public LaunchMissionDefinition() { }

            public LaunchMissionDefinition(MissionDefinition definition) {
                this.type = definition.getMissionType().name();
                this.target = definition.getTarget();
                this.attack = definition.isAttack();
                this.priority = definition.getPriority().ordinal();
                this.weapon = definition.getGuidedWeapon();
                this.swarmEnabled = definition.isSwarmEnabled();
                this.swarmMaximumMembers = definition.getSwarmMaximumMembers();
                this.unsupportedConfiguration = false;
                this.policyInclude = joinPolicy(definition.getTargetPolicy().getInclude());
                this.policyRequire = joinPolicy(definition.getTargetPolicy().getRequire());
                this.policyExclude = joinPolicy(definition.getTargetPolicy().getExclude());
                this.policyRejectEmbedded = definition.getTargetPolicy().isRejectEmbedded();
                this.policyAllowVehicles = definition.getTargetPolicy().isAllowVehicleTargets();
                this.policyMinimumConfidence = definition.getTargetPolicy().getMinimumConfidence();
                this.patrolRoute = net.vas.rdp.uav.mission.PatrolRouteCodec
                        .encodeOrEmpty(definition.getWaypoints());
            }

            public void fromBytes(ByteBuf buffer) {
                version = buffer.readUnsignedByte();
                type = readString(buffer);
                target = readString(buffer);
                attack = buffer.readBoolean();
                priority = buffer.readUnsignedByte();
                weapon = readString(buffer);
                swarmEnabled = buffer.readBoolean();
                swarmMaximumMembers = buffer.readUnsignedByte();
                unsupportedConfiguration = buffer.readBoolean();
                policyInclude = readString(buffer);
                policyRequire = readString(buffer);
                policyExclude = readString(buffer);
                policyRejectEmbedded = buffer.readBoolean();
                policyAllowVehicles = buffer.readBoolean();
                policyMinimumConfidence = buffer.readDouble();
                patrolRoute = readString(buffer);
            }

            public void toBytes(ByteBuf buffer) {
                buffer.writeByte(version);
                writeString(buffer, type);
                writeString(buffer, target);
                buffer.writeBoolean(attack);
                buffer.writeByte(Math.max(0, Math.min(3, priority)));
                writeString(buffer, weapon);
                buffer.writeBoolean(swarmEnabled);
                buffer.writeByte(Math.max(1, Math.min(64, swarmMaximumMembers)));
                buffer.writeBoolean(unsupportedConfiguration);
                writeString(buffer, policyInclude);
                writeString(buffer, policyRequire);
                writeString(buffer, policyExclude);
                buffer.writeBoolean(policyRejectEmbedded);
                buffer.writeBoolean(policyAllowVehicles);
                buffer.writeDouble(policyMinimumConfidence);
                writeString(buffer, patrolRoute);
            }

            private static String joinPolicy(java.util.List<String> values) {
                StringBuilder result = new StringBuilder();
                for (String value : values) {
                    if (value == null || value.indexOf('|') >= 0) continue;
                    if (result.length() > 0) result.append('|');
                    result.append(value);
                }
                return result.toString();
            }
        }

        public void fromBytes(ByteBuf buffer) {
            target = readString(buffer);
            attack = buffer.readBoolean();
            point = buffer.readBoolean();
            priority = buffer.readUnsignedByte();
            weapon = readString(buffer);
        }

        public void toBytes(ByteBuf buffer) {
            writeString(buffer, target);
            buffer.writeBoolean(attack);
            buffer.writeBoolean(point);
            buffer.writeByte(Math.max(0, Math.min(3, priority)));
            writeString(buffer, weapon);
        }

    }

    /** Explicit hit result from the active client render camera. */
    public static final class CameraAim implements IMessage {
        private static final byte BLOCK = 0;
        private static final byte ENTITY = 1;
        private byte kind = -1;
        private int blockX;
        private int blockY;
        private int blockZ;
        private double hitX;
        private double hitY;
        private double hitZ;
        private long targetMost;
        private long targetLeast;

        public CameraAim() { }

        private static CameraAim block(BlockPos pos, Vec3d hit) {
            CameraAim message = new CameraAim();
            message.kind = BLOCK;
            message.blockX = pos.getX();
            message.blockY = pos.getY();
            message.blockZ = pos.getZ();
            message.hitX = hit.x;
            message.hitY = hit.y;
            message.hitZ = hit.z;
            return message;
        }

        private static CameraAim entity(java.util.UUID targetId) {
            CameraAim message = new CameraAim();
            message.kind = ENTITY;
            message.targetMost = targetId.getMostSignificantBits();
            message.targetLeast = targetId.getLeastSignificantBits();
            return message;
        }

        public void fromBytes(ByteBuf buffer) {
            kind = buffer.readByte();
            if (kind == BLOCK) {
                blockX = buffer.readInt();
                blockY = buffer.readInt();
                blockZ = buffer.readInt();
                hitX = buffer.readDouble();
                hitY = buffer.readDouble();
                hitZ = buffer.readDouble();
            } else if (kind == ENTITY) {
                targetMost = buffer.readLong();
                targetLeast = buffer.readLong();
            }
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeByte(kind);
            if (kind == BLOCK) {
                buffer.writeInt(blockX);
                buffer.writeInt(blockY);
                buffer.writeInt(blockZ);
                buffer.writeDouble(hitX);
                buffer.writeDouble(hitY);
                buffer.writeDouble(hitZ);
            } else if (kind == ENTITY) {
                buffer.writeLong(targetMost);
                buffer.writeLong(targetLeast);
            }
        }
    }

    /** Authenticated, sequenced operator look correction. */
    public static final class OperatorTrim implements IMessage {
        private long aircraftMost, aircraftLeast, missionMost, missionLeast;
        private long sequence;
        private long clientTick;
        private float yawDelta, pitchDelta;

        public OperatorTrim() { }
        private OperatorTrim(java.util.UUID aircraftId, java.util.UUID missionId,
                             long sequence, long clientTick, float yawDelta, float pitchDelta) {
            this.aircraftMost = aircraftId.getMostSignificantBits();
            this.aircraftLeast = aircraftId.getLeastSignificantBits();
            this.missionMost = missionId.getMostSignificantBits();
            this.missionLeast = missionId.getLeastSignificantBits();
            this.sequence = sequence;
            this.clientTick = clientTick;
            this.yawDelta = yawDelta;
            this.pitchDelta = pitchDelta;
        }
        public void fromBytes(ByteBuf buffer) {
            aircraftMost = buffer.readLong(); aircraftLeast = buffer.readLong();
            missionMost = buffer.readLong(); missionLeast = buffer.readLong();
            sequence = buffer.readLong(); clientTick = buffer.readLong();
            yawDelta = buffer.readFloat(); pitchDelta = buffer.readFloat();
        }
        public void toBytes(ByteBuf buffer) {
            buffer.writeLong(aircraftMost); buffer.writeLong(aircraftLeast);
            buffer.writeLong(missionMost); buffer.writeLong(missionLeast);
            buffer.writeLong(sequence); buffer.writeLong(clientTick);
            buffer.writeFloat(yawDelta); buffer.writeFloat(pitchDelta);
        }
        private java.util.UUID aircraftId() { return new java.util.UUID(aircraftMost, aircraftLeast); }
        private java.util.UUID missionId() { return new java.util.UUID(missionMost, missionLeast); }
    }

    public static final class ServerOperatorTrimHandler
            implements IMessageHandler<OperatorTrim, IMessage> {
        @Override
        public IMessage onMessage(final OperatorTrim message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            if (player == null || player.getServer() == null) return null;
            player.getServer().addScheduledTask(() -> {
                MCH_EntityAircraft aircraft = net.vas.rdp.uav.mission.UavControlAccess
                        .findControlledAircraft(player);
                if (aircraft == null || !aircraft.getUniqueID().equals(message.aircraftId())
                        || aircraft.world != player.world
                        || !net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)
                        || RDPUavMod.missions().ensureAuthorized(aircraft, player)) return;
                net.vas.rdp.uav.mission.RDPMission mission = RDPUavMod.missions()
                        .activeMission(player.world, aircraft.getUniqueID());
                if (mission == null || mission.isTerminal()
                        || !mission.getMissionId().equals(message.missionId())
                        || !mission.getOwnerId().equals(player.getUniqueID())
                        || mission.getDimension() != player.dimension
                        || !net.vas.rdp.uav.mission.MissionMovementAuthority
                        .nativeWingManRequired(mission)
                        || RDPUavMod.missions().assignedMission(aircraft.getUniqueID()) == null) return;
                net.vas.rdp.uav.profile.UavVehicleProfile profile = aircraft.getAcInfo() == null
                        ? null : net.vas.rdp.uav.profile.UavVehicleProfiles
                        .forAircraft(aircraft.getAcInfo().name);
                boolean accepted = net.vas.rdp.uav.flight.OperatorTrimRegistry.accept(
                        aircraft.getUniqueID(), mission.getMissionId(), message.sequence,
                        message.clientTick,
                        message.yawDelta, message.pitchDelta,
                        player.world.getTotalWorldTime(), profile);
                if (accepted) {
                    RdpUavDebug.event("OperatorTrim", "CAMERA_TRIM_ACCEPTED",
                            "mission", mission.getMissionId(), "aircraft", aircraft.getUniqueID(),
                            "operator", player.getUniqueID(), "sequence", message.sequence,
                            "yawDelta", message.yawDelta, "pitchDelta", message.pitchDelta,
                            "tick", player.world.getTotalWorldTime());
                }
            });
            return null;
        }
    }

    /** Retask a completed reconnaissance hold into a linked exact-entity strike. */
    public static final class LinkedRetask implements IMessage {
        private String parentMissionId = "";
        private String targetId = "";
        public LinkedRetask() { }
        private LinkedRetask(String parentMissionId, java.util.UUID targetId) {
            this.parentMissionId = parentMissionId;
            this.targetId = targetId.toString();
        }
        public void fromBytes(ByteBuf buffer) {
            parentMissionId = readString(buffer);
            targetId = readString(buffer);
        }
        public void toBytes(ByteBuf buffer) {
            writeString(buffer, parentMissionId);
            writeString(buffer, targetId);
        }
    }

    public static final class ServerLinkedRetaskHandler
            implements IMessageHandler<LinkedRetask, IMessage> {
        private static void reject(EntityPlayerMP player, String message) {
            if (player != null) player.sendMessage(
                    new net.minecraft.util.text.TextComponentString(message));
        }

        @Override
        public IMessage onMessage(final LinkedRetask message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            if (player == null || player.getServer() == null) return null;
            player.getServer().addScheduledTask(() -> {
                try {
                    java.util.UUID parentId = java.util.UUID.fromString(message.parentMissionId);
                    java.util.UUID targetId = java.util.UUID.fromString(message.targetId);
                    MCH_EntityAircraft aircraft = net.vas.rdp.uav.mission.UavControlAccess
                            .findControlledAircraft(player);
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)
                            || RDPUavMod.missions().ensureAuthorized(aircraft, player)) {
                        reject(player, "Retask rejected: the controlled aircraft operator is not authorized.");
                        return;
                    }
                    net.vas.rdp.uav.mission.RDPMission parent =
                            RDPUavMod.missions().activeMission(player.world, aircraft.getUniqueID());
                    if (parent == null || !parent.getMissionId().equals(parentId)
                            || !parent.getOwnerId().equals(player.getUniqueID())
                            || parent.getState() != net.vas.rdp.uav.mission.MissionState.HOLDING
                            || parent.getReportId() == null || parent.getReportId().trim().isEmpty()
                            || (parent.getMissionType() != net.vas.rdp.uav.mission.MissionType.RECONNAISSANCE
                            && parent.getMissionType() != net.vas.rdp.uav.mission.MissionType.OBSERVATION)) {
                        reject(player, "Retask rejected: select this aircraft's reconnaissance mission in station hold.");
                        return;
                    }
                    net.vas.rdp.uav.profile.UavVehicleProfile profile = aircraft.getAcInfo() == null
                            ? null : net.vas.rdp.uav.profile.UavVehicleProfiles
                            .forAircraft(aircraft.getAcInfo().name);
                    net.minecraft.entity.Entity target = new net.vas.rdp.uav.target.TargetResolver()
                            .findLoaded(player.world, targetId);
                    if (profile == null || target == null || target == aircraft || target == player
                            || !target.canBeCollidedWith()
                            || target.dimension != player.dimension
                            || !profile.supportsTargetLock()
                            || target.getDistance(aircraft) > profile.getLockRange()
                            || !net.vas.rdp.uav.target.TargetPolicy.isSelectableExactContact(
                            player.world, target, player)) {
                        reject(player, "Retask rejected: target is unavailable or outside aircraft policy/range.");
                        return;
                    }
                    if (profile.isReusable()) {
                        boolean armed = false;
                        if (aircraft.getWeapons() != null) {
                            for (MCH_WeaponSet weapon : aircraft.getWeapons()) {
                                if (weapon == null || weapon.getInfo() == null
                                        || weapon.getAmmo() + weapon.getAmmoReserve() <= 0) continue;
                                net.vas.rdp.uav.weapon.GuidedWeaponCapability capability =
                                        net.vas.rdp.uav.weapon.GuidedWeaponRegistry.find(
                                                weapon.getName(), weapon.getInfo().type);
                                if (capability != null && capability.getBehavior()
                                        != net.vas.rdp.uav.weapon.GuidedNativeBehavior.UNSUPPORTED) {
                                    armed = true;
                                    break;
                                }
                            }
                        }
                        if (!armed) {
                            reject(player, "Retask rejected: reusable aircraft has no executable, loaded weapon.");
                            return;
                        }
                    } else if (!FLIGHT_ADAPTER.supportsKamikaze(aircraft)
                            && !FLIGHT_ADAPTER.hasSelfDestructWeaponWithAmmo(aircraft)) {
                        reject(player, "Retask rejected: one-way aircraft has no executable terminal payload.");
                        return;
                    }
                    net.minecraft.util.ResourceLocation registry = net.minecraft.entity.EntityList.getKey(target);
                    if (registry == null) {
                        reject(player, "Retask rejected: target has no registry identity.");
                        return;
                    }
                    long tick = player.world.getTotalWorldTime();
                    net.vas.rdp.uav.mission.RDPMission child = new net.vas.rdp.uav.mission.RDPMission(
                            java.util.UUID.randomUUID(), player.getUniqueID(), aircraft.getUniqueID(),
                            aircraft.getAcInfo().name, net.vas.rdp.uav.mission.MissionType.STRIKE,
                            net.vas.rdp.uav.target.TargetSpec.manual(registry.toString(),
                                    net.vas.rdp.uav.target.TargetPriority.HIGH),
                            true, false, false, profile.getDetectionRange(),
                            profile.getMissionExecutionRange(), tick);
                    child.setDimension(player.dimension);
                    child.reacquireTarget(targetId, tick);
                    if (!RDPUavMod.missions().transferToLinkedStrike(player.world,
                            parent, child, tick)) {
                        reject(player, "Retask rejected: assignment transfer failed; reconnaissance hold remains active.");
                        return;
                    }
                    net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "Linked strike accepted; aircraft assignment transferred atomically."));
                    RdpUavNetwork.syncTargetTelemetry(player);
                } catch (IllegalArgumentException invalid) {
                    reject(player, "Retask rejected: mission or target identity is invalid.");
                }
            });
            return null;
        }
    }

    public static final class AdoptProjectile implements IMessage {
        public void fromBytes(ByteBuf buffer) { }
        public void toBytes(ByteBuf buffer) { }
    }

    public static final class ReassignGuided implements IMessage {
        private String missionId = "";
        private String targetId = "";
        public ReassignGuided() { }
        public ReassignGuided(String missionId, java.util.UUID targetId) {
            this.missionId = missionId == null ? "" : missionId;
            this.targetId = targetId == null ? "" : targetId.toString();
        }
        public void fromBytes(ByteBuf buffer) {
            missionId = readString(buffer); targetId = readString(buffer);
        }
        public void toBytes(ByteBuf buffer) {
            writeString(buffer, missionId); writeString(buffer, targetId);
        }
    }

    public static final class SquareStrike implements IMessage {
        private String parameters = "";
        private String weapon = "";
        public SquareStrike() { }
        public SquareStrike(String parameters) {
            this(parameters, "");
        }
        public SquareStrike(String parameters, String weapon) {
            this.parameters = parameters == null ? "" : parameters;
            this.weapon = weapon == null ? "" : weapon;
        }

        public void fromBytes(ByteBuf buffer) {
            parameters = readString(buffer);
            if (buffer.isReadable()) weapon = readString(buffer);
        }
        public void toBytes(ByteBuf buffer) {
            writeString(buffer, parameters); writeString(buffer, weapon);
        }
    }

    public static final class AiTargetLock implements IMessage {
        private String targetId = "";
        private boolean clear;
        public AiTargetLock() { }
        public AiTargetLock(java.util.UUID targetId, boolean clear) {
            this.targetId = targetId == null ? "" : targetId.toString();
            this.clear = clear;
        }

        public static final class RegistryTargetAssignment implements IMessage {
            private String missionId = "";
            private String registryId = "";

            public RegistryTargetAssignment() { }

            public RegistryTargetAssignment(String missionId, String registryId) {
                this.missionId = missionId == null ? "" : missionId;
                this.registryId = registryId == null ? "" : registryId;
            }

            public void fromBytes(ByteBuf buffer) {
                missionId = readString(buffer);
                registryId = readString(buffer);
            }

            public void toBytes(ByteBuf buffer) {
                writeString(buffer, missionId);
                writeString(buffer, registryId);
            }
        }

        public static final class ServerRegistryTargetAssignmentHandler
                implements IMessageHandler<RegistryTargetAssignment, IMessage> {
            public IMessage onMessage(final RegistryTargetAssignment message, MessageContext context) {
                final EntityPlayerMP player = context.getServerHandler().player;
                Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                    MCH_EntityAircraft aircraft =
                            net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)
                            || RDPUavMod.missions().ensureAuthorized(aircraft, player)) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "UAV registry target rejected: operator authorization is required."));
                        return;
                    }
                    try {
                        java.util.UUID missionId = java.util.UUID.fromString(message.missionId);
                        net.vas.rdp.uav.mission.RDPMission mission =
                                net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                        .getRepository().find(missionId);
                        if (mission == null || mission.isTerminal()
                                || !player.getUniqueID().equals(mission.getOwnerId())
                                || !aircraft.getUniqueID().equals(mission.getAircraftId())) {
                            throw new IllegalArgumentException("Mission is not assignable");
                        }
                        net.minecraft.entity.Entity target =
                                TARGET_RESOLVER.findLoadedByRegistryId(player.world, message.registryId);
                        if (target == null || target == aircraft || target == player
                                || !target.canBeCollidedWith()
                                || !net.vas.rdp.uav.target.TargetPolicy.isSelectableContact(
                                player.world, target, player)) {
                            throw new IllegalArgumentException("Target is not selectable");
                        }
                        net.vas.rdp.uav.profile.UavVehicleProfile profile =
                                aircraft.getAcInfo() == null ? null
                                : net.vas.rdp.uav.profile.UavVehicleProfiles.forAircraft(
                                aircraft.getAcInfo().name);
                        if (profile == null || !profile.supportsTargetLock()
                                || target.getDistance(aircraft) > profile.getLockRange()) {
                            throw new IllegalArgumentException("Target is outside lock range");
                        }
                        if (mission.getState()
                                == net.vas.rdp.uav.mission.MissionState.KAMIKADZE_TARGET
                                && !target.getUniqueID().equals(mission.getTargetId())) {
                            throw new IllegalArgumentException(
                                    "Terminal target cannot be replaced");
                        }
                        mission.applyOperatorEntityLock(target.getUniqueID(),
                                player.world.getTotalWorldTime());
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "UAV registry target assigned: " + message.registryId));
                    } catch (IllegalArgumentException failure) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "UAV registry target rejected: invalid mission or target."));
                    }
                });
                return null;
            }
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
                Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                    MCH_EntityAircraft aircraft =
                            net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)
                            || RDPUavMod.missions().ensureAuthorized(aircraft, player)) {
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
        private String[] weaponRows = new String[0];
        private String[] weaponNames = new String[0];
        public MissionData() { }
        private MissionData(String[] rows, String[] ids, String[] weaponRows,
                            String[] weaponNames) {
            this.rows = rows; this.ids = ids; this.weaponRows = weaponRows;
            this.weaponNames = weaponNames;
        }
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
                        + (mission.getGuidedWeapon() == null ? ""
                        : "  WPN=" + mission.getGuidedWeapon())
                        + "  XYZ=" + formatCoordinate(mission.getTargetSpec().getX())
                        + "," + formatCoordinate(mission.getTargetSpec().getY())
                        + "," + formatCoordinate(mission.getTargetSpec().getZ())
                        + (mission.getFailureReason() == null ? ""
                        : "  [" + mission.getFailureReason() + "]");
            }
            java.util.List<String> weaponRows = new java.util.ArrayList<>();
            java.util.List<String> weaponNames = new java.util.ArrayList<>();
            MCH_EntityAircraft aircraft = net.vas.rdp.uav.mission.UavControlAccess
                    .findControlledAircraft(player);
            if (net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)
                    && aircraft.getWeapons() != null) {
                for (MCH_WeaponSet weapon : aircraft.getWeapons()) {
                    if (weapon == null || weapon.getInfo() == null) continue;
                    net.vas.rdp.uav.weapon.GuidedWeaponCapability capability =
                            net.vas.rdp.uav.weapon.GuidedWeaponRegistry.find(
                                    weapon.getName(), weapon.getInfo().type);
                    if (capability == null) continue;
                    weaponRows.add(weapon.getName() + "/" + weapon.getInfo().type
                            + " " + capability.getBehavior() + " objectives="
                            + capability.getObjectives() + " ammo="
                            + (weapon.getAmmo() + weapon.getAmmoReserve()));
                    if (capability.getBehavior()
                            != net.vas.rdp.uav.weapon.GuidedNativeBehavior.UNSUPPORTED
                            && (weapon.getAmmo() > 0 || weapon.getAmmoReserve() > 0)) {
                        // Only an executable, loaded capability belongs in the
                        // operator's weapon selector. Unsupported definitions
                        // remain visible in weaponRows with their reason/type.
                        weaponNames.add(weapon.getName());
                    }
                }
                for (net.vas.rdp.uav.weapon.MissileGuidanceController.WeaponTelemetry telemetry :
                        net.vas.rdp.uav.weapon.MissileGuidanceController.telemetry(
                                player.world, aircraft.getUniqueID())) {
                    weaponRows.add("PROJECTILE " + telemetry.getWeapon() + " "
                            + telemetry.getState() + " xyz="
                            + formatCoordinate(telemetry.getX()) + ","
                            + formatCoordinate(telemetry.getY()) + ","
                            + formatCoordinate(telemetry.getZ())
                            + " alt=" + formatCoordinate(telemetry.getY()));
                }
            }
            return new MissionData(rows, ids, weaponRows.toArray(new String[0]),
                    weaponNames.toArray(new String[0]));
        }
        public String[] getRows() { return rows; }
        public String[] getIds() { return ids; }
        public String[] getWeaponRows() { return weaponRows; }
        public String[] getWeaponNames() { return weaponNames; }
        public void fromBytes(ByteBuf buffer) {
            int count = buffer.readInt();
            if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid mission row count");
            rows = new String[count];
            ids = new String[count];
            for (int i = 0; i < count; i++) {
                ids[i] = readString(buffer);
                rows[i] = readString(buffer);
            }
            if (!buffer.isReadable()) {
                weaponRows = new String[0];
                weaponNames = new String[0];
                return;
            }
            int weaponCount = buffer.readUnsignedByte();
            if (weaponCount > 64) throw new IllegalArgumentException("Invalid weapon row count");
            weaponRows = new String[weaponCount];
            weaponNames = new String[weaponCount];
            for (int i = 0; i < weaponCount; i++) {
                weaponNames[i] = readString(buffer);
                weaponRows[i] = readString(buffer);
            }
        }
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(rows.length);
            for (int i = 0; i < rows.length; i++) {
                writeString(buffer, ids[i]);
                writeString(buffer, rows[i]);
            }
            int count = Math.min(64, weaponRows.length);
            buffer.writeByte(count);
            for (int i = 0; i < count; i++) {
                writeString(buffer, i < weaponNames.length ? weaponNames[i] : "");
                writeString(buffer, weaponRows[i]);
            }
        }
        private static String formatCoordinate(double value) {
            return String.format(java.util.Locale.ROOT, "%.1f", value);
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
                private double previousX, previousY, previousZ;
                private long lastDetectionTick, lastUpdateTick;
                private boolean visible;
                private String lifecycle = "NONE";
                private String markerState = "HIDDEN";

                public TargetTelemetry() { }

                private TargetTelemetry(int dimension) {
                    this(false, false, "", "", "NO TARGET", "NO TARGET",
                            null, null, "NORMAL", "READY", "OFF",
                            "", "NONE", "none", dimension, 0, 0, 0, 0);
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
                        return new TargetTelemetry(
                                player.dimension);
                    }
                    java.util.List<net.vas.rdp.uav.mission.RDPMission> missions =
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                    .getRepository().byOwner(player.getUniqueID());
                    net.vas.rdp.uav.mission.RDPMission selected = null;
                    for (net.vas.rdp.uav.mission.RDPMission mission : missions) {
                        if (mission.getAircraftId().equals(ridden.getUniqueID())) {
                            selected = mission;
                            break;
                        }
                    }
                    if (selected == null) return new TargetTelemetry(
                            player.dimension);

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
                    boolean terminal = selected.isTerminal();
                    boolean locked = !terminal && (selected.getState() == net.vas.rdp.uav.mission.MissionState.TARGET_LOCKED
                            || selected.getState() == net.vas.rdp.uav.mission.MissionState.ATTACK_TARGET
                            || selected.getState() == net.vas.rdp.uav.mission.MissionState.KAMIKADZE_TARGET);
                    boolean noCandidate = selected.getState()
                            == net.vas.rdp.uav.mission.MissionState.WAITING_FOR_TARGET;
                    String targetState = terminal ? "MISSION_TERMINAL"
                            : noCandidate ? "NO_CANDIDATE"
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
                    TargetTelemetry telemetry = new TargetTelemetry(true, !terminal && !lost && (point || target != null), selected.getMissionId().toString(),
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
                        telemetry.previousX = contact.getPreviousX();
                        telemetry.previousY = contact.getPreviousY();
                        telemetry.previousZ = contact.getPreviousZ();
                        telemetry.lastDetectionTick = contact.getLastDetectionTick();
                        telemetry.lastUpdateTick = contact.getLastUpdateTick();
                        telemetry.confidence = contact.getConfidence();
                        telemetry.bearing = contact.getBearing();
                        telemetry.elevation = contact.getElevation();
                        telemetry.visible = contact.isVisible();
                        telemetry.lifecycle = contact.getLifecycle().name();
                        telemetry.markerState = terminal ? "HIDDEN" : contact.getMarkerState();
                        if (aircraftEntity != null
                                && telemetry.bearing == 0.0D && telemetry.elevation == 0.0D) {
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
                    previousX = buffer.readDouble(); previousY = buffer.readDouble(); previousZ = buffer.readDouble();
                    lastDetectionTick = buffer.readLong(); lastUpdateTick = buffer.readLong();
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
                    buffer.writeDouble(previousX).writeDouble(previousY).writeDouble(previousZ);
                    buffer.writeLong(lastDetectionTick).writeLong(lastUpdateTick);
                    buffer.writeBoolean(visible); writeString(buffer, lifecycle); writeString(buffer, markerState);
                }
            }

    private static String readString(ByteBuf buffer) {
        int length = buffer.readUnsignedShort();
        if (length > MAX_PACKET_STRING_LENGTH) {
            throw new IllegalArgumentException("Packet string exceeds "
                    + MAX_PACKET_STRING_LENGTH + " bytes");
        }
        return buffer.readCharSequence(length, java.nio.charset.StandardCharsets.UTF_8).toString();
    }
    private static void writeString(ByteBuf buffer, String value) {
        if (value == null) throw new IllegalArgumentException("Packet string is required");
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_PACKET_STRING_LENGTH) {
            throw new IllegalArgumentException("Packet string exceeds "
                    + MAX_PACKET_STRING_LENGTH + " bytes");
        }
        buffer.writeShort(bytes.length).writeBytes(bytes);
    }

    public static final class ServerMissionDataHandler implements IMessageHandler<RequestMissionData, IMessage> {
        public IMessage onMessage(RequestMissionData message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> sendMissionData(player));
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
                        if (mission == null) {
                                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                        "Abort rejected: mission was not found in this world."));
                                RdpUavDebug.event("MissionControl", "ABORT_REJECTED",
                                        "player", player.getUniqueID(), "mission", id,
                                        "reason", "MISSION_NOT_FOUND");
                                return;
                        }
                        if (!player.getUniqueID().equals(mission.getOwnerId())) {
                                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                        "Abort rejected: you do not own this mission."));
                                RdpUavDebug.event("MissionControl", "ABORT_REJECTED",
                                        "player", player.getUniqueID(), "mission", id,
                                        "reason", "NOT_MISSION_OWNER");
                                return;
                        }
                        if (mission.isTerminal()) {
                                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                        "Abort rejected: mission is already " + mission.getState() + "."));
                                RdpUavDebug.event("MissionControl", "ABORT_REJECTED",
                                        "player", player.getUniqueID(), "mission", id,
                                        "reason", "MISSION_ALREADY_TERMINAL",
                                        "state", mission.getState());
                                return;
                        }
                        try {
                            mission.abort("OPERATOR_ABORT", player.world.getTotalWorldTime());
                        } catch (IllegalStateException impossible) {
                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    "Abort impossible: terminal guidance is already committed."));
                            RdpUavDebug.event("MissionControl", "ABORT_REJECTED",
                                    "player", player.getUniqueID(), "mission", id,
                                    "reason", "ABORT_IMPOSSIBLE_AFTER_TERMINAL_COMMIT",
                                    "state", mission.getState());
                            sendMissionData(player);
                            return;
                        }
                        boolean ownsAircraft = RDPUavMod.missions().ownsAssignment(mission);
                        if (ownsAircraft) {
                            net.vas.rdp.uav.weapon.MissileGuidanceController.abort(
                                    mission.getAircraftId());
                            for (net.minecraft.entity.Entity entity : player.world.loadedEntityList) {
                                if (entity instanceof MCH_EntityAircraft
                                        && mission.getAircraftId().equals(entity.getUniqueID())) {
                                    FLIGHT_ADAPTER.release((MCH_EntityAircraft) entity);
                                    break;
                                }
                            }
                        }
                        RDPUavMod.missions().release(mission);
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Mission " + id + " aborted; AI movement control released."));
                        sendMissionData(player);
                        RdpUavDebug.event("MissionControl", "ABORT_ACCEPTED",
                                "player", player.getUniqueID(), "mission", id,
                                "aircraft", mission.getAircraftId(),
                                "tick", player.world.getTotalWorldTime());
                    } catch (IllegalArgumentException invalid) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Abort rejected: invalid mission identifier."));
                        RdpUavDebug.event("MissionControl", "ABORT_REJECTED",
                                "player", player.getUniqueID(), "mission", message.missionId,
                                "reason", "INVALID_MISSION_IDENTIFIER");
                    }
                });
                return null;
            }
        }

    public static final class ServerClearMissionsHandler implements IMessageHandler<ClearMissions, IMessage> {
        public IMessage onMessage(final ClearMissions message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
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
                    if (mission.getState()
                            == net.vas.rdp.uav.mission.MissionState.KAMIKADZE_TARGET) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Archive rejected: ABORT_IMPOSSIBLE_AFTER_TERMINAL_COMMIT."));
                        continue;
                    }
                    boolean ownsAircraft = RDPUavMod.missions().ownsAssignment(mission);
                    if (ownsAircraft) {
                        for (net.minecraft.entity.Entity entity : player.world.loadedEntityList) {
                            if (entity instanceof MCH_EntityAircraft
                                    && mission.getAircraftId().equals(entity.getUniqueID())) {
                                FLIGHT_ADAPTER.release((MCH_EntityAircraft) entity);
                                break;
                            }
                        }
                    }
                    if (!mission.isTerminal()) {
                        try {
                            mission.abort("CLEAR_MISSIONS", player.world.getTotalWorldTime());
                        } catch (RuntimeException invalidState) { continue; }
                    }
                    RDPUavMod.missions().release(mission);
                    changed = true;
                }
                if (changed) {
                    net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                    sendMissionData(player);
                }
            });
            return null;
        }
    }

    public static final class ServerAuthorizeAttackHandler
            implements IMessageHandler<AuthorizeAttack, IMessage> {
        public IMessage onMessage(final AuthorizeAttack message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                try {
                    java.util.UUID id = java.util.UUID.fromString(message.missionId);
                    net.vas.rdp.uav.mission.RDPMission mission =
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                    .getRepository().find(id);
                    if (mission == null || !mission.isAttack()
                            || !player.getUniqueID().equals(mission.getOwnerId())
                            || mission.getState() != net.vas.rdp.uav.mission.MissionState.TARGET_LOCKED) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Attack authorization rejected: mission ownership or target state is invalid."));
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
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)
                            || RDPUavMod.missions().ensureAuthorized(aircraft, player)) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Attack authorization rejected: controlled-aircraft authorization is required."));
                        return;
                    }
                    if (aircraft.getAcInfo() == null) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Attack authorization rejected: aircraft metadata is unavailable."));
                        return;
                    }
                    double x = mission.getTargetSpec().getX();
                    double y = mission.getTargetSpec().getY();
                    double z = mission.getTargetSpec().getZ();
                    if (mission.getTargetId() != null) {
                        net.minecraft.entity.Entity target = TARGET_RESOLVER.findLoaded(player.world, mission.getTargetId());
                        if (target == null) {
                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    "Attack authorization rejected: locked target is no longer loaded."));
                            return;
                        }
                        x = target.posX;
                        y = target.posY;
                        z = target.posZ;
                    }
                    net.vas.rdp.uav.profile.UavVehicleProfile profile =
                            net.vas.rdp.uav.profile.UavVehicleProfiles
                                    .forAircraft(aircraft.getAcInfo().name);
                    if (profile == null
                            || profile.getExecutionProfile()
                            == net.vas.rdp.uav.profile.UavExecutionProfile.UNKNOWN) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Attack authorization rejected: aircraft profile is unsupported."));
                        return;
                    }
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
                            mission.getGuidedWeapon() == null
                                    ? profile.getAttackWeaponPriority()
                                    : mission.getGuidedWeapon());
                    if (!guided.isAccepted() && !"NO_GUIDED_WEAPON".equals(guided.getReason())) {
                        RdpUavDebug.debug("Network",
                                "mission {} guided authorize decision={} aircraft={}",
                                mission.getMissionId(), guided.getReason(), aircraft.getUniqueID());
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Attack authorization rejected: " + guided.getReason().toLowerCase()
                                        .replace('_', ' ') + "."));
                        return;
                    }
                    if (!guided.isAccepted() && !FLIGHT_ADAPTER.armAttack(
                            aircraft, x, y, z, mission.getObservationDistance(), mission.getTargetId(),
                            mission.getGuidedWeapon() == null
                                    ? profile.getAttackWeaponPriority()
                                    : mission.getGuidedWeapon())) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                        "Attack authorization rejected: native attack arming failed."));
                        return;
                    }
                    mission.transitionTo(net.vas.rdp.uav.mission.MissionState.ATTACK_TARGET,
                            player.world.getTotalWorldTime());
                    net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                    sendMissionData(player);
                } catch (IllegalArgumentException invalid) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "Attack authorization rejected: invalid mission identifier."));
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
                            || !player.getUniqueID().equals(mission.getOwnerId())) {
                        rejectKamikaze(player, "Nose-dive rejected: mission ownership or attack authorization is invalid.");
                        return;
                    }
                    if (mission.getState() != net.vas.rdp.uav.mission.MissionState.TARGET_LOCKED) {
                        rejectKamikaze(player, "Nose-dive rejected: mission must have an authoritative target lock.");
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
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)) {
                        rejectKamikaze(player, "Nose-dive rejected: controlled-aircraft authorization is required.");
                        return;
                    }
                    double x = mission.getTargetSpec().getX();
                    double y = mission.getTargetSpec().getY();
                    double z = mission.getTargetSpec().getZ();
                    if (mission.getTargetId() != null) {
                        net.minecraft.entity.Entity target = TARGET_RESOLVER.findLoaded(player.world, mission.getTargetId());
                        if (target == null) {
                            rejectKamikaze(player, "Nose-dive rejected: locked target is no longer loaded.");
                            return;
                        }
                        x = target.posX; y = target.posY; z = target.posZ;
                    }
                    if (FLIGHT_ADAPTER.armKamikaze(aircraft, x, y, z,
                            mission.getObservationDistance(), mission.getTargetId())) {
                        rejectKamikaze(player,
                                "Nose-dive rejected: this UAV profile has no supported destructive terminal path.");
                        return;
                    }
                    // Reusable and long-range airframes may only enter a
                    // destructive route through this operator-authenticated request.
                    mission.authorizeDestructiveAction();
                    KamikazePilotOverride.lock(aircraft.getUniqueID(), mission.getTargetId(),
                            mission.getMissionId());
                    mission.transitionTo(net.vas.rdp.uav.mission.MissionState.KAMIKADZE_TARGET,
                            player.world.getTotalWorldTime());
                    net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                    sendMissionData(player);
                } catch (IllegalArgumentException ignored) {
                    rejectKamikaze(player, "Nose-dive rejected: mission identifier is invalid.");
                }
            });
            return null;
        }

        private static void rejectKamikaze(EntityPlayerMP player, String reason) {
            player.sendMessage(new net.minecraft.util.text.TextComponentString(reason));
        }
    }

    public static final class ServerSelfDestructHandler
            implements IMessageHandler<FireSelfDestruct, IMessage> {
        public IMessage onMessage(final FireSelfDestruct message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                MCH_EntityAircraft aircraft =
                        net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)) return;
                if (FLIGHT_ADAPTER.supportsKamikaze(aircraft)
                        || !FLIGHT_ADAPTER.hasSelfDestructWeaponWithAmmo(aircraft)) return;
                if (FLIGHT_ADAPTER.fireSelfDestructWeapon(aircraft)) {
                    RdpUavDebug.debug("Network", "player fired SelfDestruct aircraft={} player={}",
                            aircraft.getUniqueID(), player.getUniqueID());
                }
            });
            return null;
        }
    }

    public static final class ServerCreateMissionHandler
            implements IMessageHandler<CreateMission, IMessage> {
        public IMessage onMessage(final CreateMission message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                if (message.point) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "Point task rejected: camera hit data is required. Use the updated point-task key."));
                    return;
                }
                if (!message.point && (message.target == null
                        || !message.target.matches(
                        "(?i)(mod=[A-Za-z0-9_.-]+|[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+)"))) {
                    return;
                }

                MCH_EntityAircraft aircraft = net.vas.rdp.uav.mission.UavControlAccess
                        .findControlledAircraft(player);
                if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)) return;
                if (message.attack && message.weapon != null
                        && !message.weapon.trim().isEmpty()
                        && !hasSupportedWeapon(aircraft, message.weapon)) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "Mission rejected: selected guided weapon is unavailable or unsupported."));
                    return;
                }
                String command = "rdpuav mission create " + message.target
                        + (message.attack ? " attack" : "")
                        + " " + priorityName(message.priority);
                java.util.Set<java.util.UUID> existingMissionIds = new java.util.HashSet<>();
                for (net.vas.rdp.uav.mission.RDPMission mission :
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                .getRepository().byOwner(player.getUniqueID())) {
                    existingMissionIds.add(mission.getMissionId());
                }
                Objects.requireNonNull(player.getServer()).getCommandManager().executeCommand(player, command);
                if (message.attack && message.weapon != null
                        && !message.weapon.trim().isEmpty()) {
                    java.util.List<net.vas.rdp.uav.mission.RDPMission> missions =
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                    .getRepository().byOwner(player.getUniqueID());
                    for (int i = missions.size() - 1; i >= 0; i--) {
                        net.vas.rdp.uav.mission.RDPMission mission = missions.get(i);
                        if (!existingMissionIds.contains(mission.getMissionId())
                                && mission.getAircraftId().equals(aircraft.getUniqueID())
                                && !mission.isTerminal()) {
                            mission.setGuidedWeapon(message.weapon);
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                    .markDirty();
                            break;
                        }
                    }
                }
            });
            return null;
        }

        private boolean hasSupportedWeapon(MCH_EntityAircraft aircraft, String requested) {
            if (aircraft == null || aircraft.getWeapons() == null) return false;
            for (MCH_WeaponSet weapon : aircraft.getWeapons()) {
                if (weapon == null || weapon.getInfo() == null
                        || !requested.equalsIgnoreCase(weapon.getName())) continue;
                return net.vas.rdp.uav.weapon.GuidedWeaponRegistry.find(
                        weapon.getName(), weapon.getInfo().type) != null;
            }
            // The GUI may retain a stale priority from another aircraft.
            // Accept the mission when this airframe has another verified
            // guided weapon; the adapter will select it and emit telemetry.
            for (MCH_WeaponSet weapon : aircraft.getWeapons()) {
                if (weapon == null || weapon.getInfo() == null) continue;
                net.vas.rdp.uav.weapon.GuidedWeaponCapability capability =
                        net.vas.rdp.uav.weapon.GuidedWeaponRegistry.find(
                                weapon.getName(), weapon.getInfo().type);
                if (capability != null
                        && capability.getBehavior()
                        != net.vas.rdp.uav.weapon.GuidedNativeBehavior.UNSUPPORTED) {
                    return true;
                }
            }
            return false;
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

    public static final class ServerMissionDefinitionHandler
            implements IMessageHandler<CreateMission.LaunchMissionDefinition, IMessage> {
        public IMessage onMessage(final CreateMission.LaunchMissionDefinition message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                boolean patrol = "PATROL".equals(message.type);
                if (message.version != 3 || message.unsupportedConfiguration
                        || (patrol ? message.patrolRoute == null || message.patrolRoute.trim().isEmpty()
                        : message.target == null || !message.target.matches(
                        "(?i)(mod=[A-Za-z0-9_.-]+|[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+)"))) {
                    reject(player, "Mission definition rejected: unsupported version or target.");
                    return;
                }
                MCH_EntityAircraft aircraft =
                        net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)) {
                    reject(player, "Mission definition rejected: operator authorization is required.");
                    return;
                }
                if (message.swarmMaximumMembers < 1 || message.swarmMaximumMembers > 64) {
                    reject(player, "Mission definition rejected: invalid swarm size.");
                    return;
                }
                if (patrol) {
                    createPatrol(player, message, aircraft);
                    return;
                }
                if (!message.type.equals("RECONNAISSANCE")
                        && !message.type.equals("OBSERVATION")
                        && !message.type.equals("STRIKE")
                        && !message.type.equals("DESTROYING")) {
                    reject(player, "Mission definition rejected: mission type is unsupported.");
                    return;
                }
                if (message.attack && !message.weapon.trim().isEmpty()
                        && !hasSupportedWeapon(aircraft, message.weapon)) {
                    reject(player, "Mission definition rejected: selected weapon is unavailable.");
                    return;
                }
                String command = "rdpuav mission create " + message.target
                        + (message.attack ? " attack" : "") + " "
                        + priorityName(message.priority);
                Objects.requireNonNull(player.getServer()).getCommandManager()
                        .executeCommand(player, command);
                java.util.List<net.vas.rdp.uav.mission.RDPMission> created =
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                .getRepository().byOwner(player.getUniqueID());
                for (int i = created.size() - 1; i >= 0; i--) {
                    net.vas.rdp.uav.mission.RDPMission mission = created.get(i);
                    if (mission.getAircraftId().equals(aircraft.getUniqueID())
                            && !mission.isTerminal()) {
                        net.vas.rdp.uav.target.TargetPolicy.Builder policy =
                                net.vas.rdp.uav.target.TargetPolicy.builder()
                                .include(splitPolicy(message.policyInclude))
                                .require(splitPolicy(message.policyRequire))
                                .exclude(splitPolicy(message.policyExclude))
                                .rejectDeeplyEmbedded(message.policyRejectEmbedded)
                                .allowHostileVehicles(message.policyAllowVehicles)
                                .withMinimumConfidence(message.policyMinimumConfidence);
                        mission.setTargetPolicy(policy.snapshot());
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                .markDirty();
                        break;
                    }
                }
                if (message.attack && !message.weapon.trim().isEmpty()) {
                    java.util.List<net.vas.rdp.uav.mission.RDPMission> missions =
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                    .getRepository().byOwner(player.getUniqueID());
                    for (int i = missions.size() - 1; i >= 0; i--) {
                        net.vas.rdp.uav.mission.RDPMission mission = missions.get(i);
                        if (mission.getAircraftId().equals(aircraft.getUniqueID())
                                && !mission.isTerminal()) {
                            mission.setGuidedWeapon(message.weapon);
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                    .markDirty();
                            break;
                        }
                    }
                }
                sendMissionData(player);
            });
            return null;
        }

        private static void reject(EntityPlayerMP player, String message) {
            player.sendMessage(new net.minecraft.util.text.TextComponentString(message));
        }

        private static void createPatrol(EntityPlayerMP player,
                                         CreateMission.LaunchMissionDefinition message,
                                         MCH_EntityAircraft aircraft) {
            if (message.attack || message.swarmEnabled || !message.weapon.trim().isEmpty()) {
                reject(player, "Patrol rejected: route patrol does not authorize payload execution or swarm fan-out.");
                return;
            }
            if (!aircraft.isUAV() || aircraft.getAcInfo() == null) {
                reject(player, "Patrol rejected: an autonomous UAV airframe is required.");
                return;
            }
            net.vas.rdp.uav.profile.UavVehicleProfile profile =
                    net.vas.rdp.uav.profile.UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name);
            if (!profile.isReusable()) {
                reject(player, "Patrol rejected: this profile has no reusable native recovery capability.");
                return;
            }
            java.util.List<net.vas.rdp.uav.mission.MissionWaypoint> route;
            try {
                route = net.vas.rdp.uav.mission.PatrolRouteCodec.decode(message.patrolRoute);
                net.vas.rdp.uav.mission.PatrolRouteCodec.validate(route);
            } catch (IllegalArgumentException invalid) {
                reject(player, "Patrol rejected: " + invalid.getMessage());
                return;
            }
            long tick = player.world.getTotalWorldTime();
            net.vas.rdp.uav.mission.PatrolRoutePlanner.Plan plan =
                    net.vas.rdp.uav.mission.PatrolRoutePlanner.plan(
                            player.world, aircraft, route, profile, tick);
            if (!plan.isAccepted()) {
                reject(player, "Patrol rejected: terrain/route validation failed (" + plan.getReason() + ").");
                return;
            }
            net.vas.rdp.uav.mission.MissionWaypoint first = route.get(0);
            net.vas.rdp.uav.mission.RDPMission mission =
                    new net.vas.rdp.uav.mission.RDPMission(java.util.UUID.randomUUID(),
                            player.getUniqueID(), aircraft.getUniqueID(), aircraft.getAcInfo().name,
                            net.vas.rdp.uav.mission.MissionType.PATROL,
                            net.vas.rdp.uav.target.TargetSpec.point(first.getX(), aircraft.posY,
                                    first.getZ(), net.vas.rdp.uav.target.TargetPriority.NORMAL),
                            false, false, false, Math.max(1.0D, profile.getDetectionRange()),
                            8.0D, tick);
            mission.setDimension(player.dimension);
            mission.setPatrolRoute(route);
            try {
                RDPUavMod.missions().create(player.world, mission);
            } catch (IllegalStateException unavailable) {
                reject(player, "Patrol rejected: aircraft assignment unavailable ("
                        + unavailable.getMessage() + ").");
                return;
            }
            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                    "Terrain-checked patrol accepted; native recovery will return the UAV to its configured home."));
            sendMissionData(player);
        }

        private static String[] splitPolicy(String value) {
            if (value == null || value.trim().isEmpty()) return new String[0];
            String[] values = value.split("\\|", -1);
            if (values.length > 32) throw new IllegalArgumentException("Too many policy filters");
            for (String item : values) {
                if (item.length() > 64 || !item.matches("[A-Za-z0-9_:.*/-]+")) {
                    throw new IllegalArgumentException("Invalid policy filter");
                }
            }
            return values;
        }

        private static String priorityName(int value) {
            switch (Math.max(0, Math.min(3, value))) {
                case 0: return "low";
                case 2: return "high";
                case 3: return "critical";
                default: return "normal";
            }
        }

        private static boolean hasSupportedWeapon(MCH_EntityAircraft aircraft, String requested) {
            if (aircraft == null || aircraft.getWeapons() == null) return false;
            for (MCH_WeaponSet weapon : aircraft.getWeapons()) {
                if (weapon == null || weapon.getInfo() == null
                        || !requested.equalsIgnoreCase(weapon.getName())) continue;
                return net.vas.rdp.uav.weapon.GuidedWeaponRegistry.find(
                        weapon.getName(), weapon.getInfo().type) != null;
            }
            return false;
        }
    }

    public static final class ServerCameraAimHandler
            implements IMessageHandler<CameraAim, IMessage> {
        @Override
        public IMessage onMessage(final CameraAim message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                net.vas.rdp.uav.mission.OperatorAimMissionService.Result result;
                if (message.kind == CameraAim.BLOCK) {
                    result = net.vas.rdp.uav.mission.OperatorAimMissionService
                            .createPointMission(player,
                                    new BlockPos(message.blockX, message.blockY, message.blockZ),
                                    message.hitX, message.hitY, message.hitZ);
                } else if (message.kind == CameraAim.ENTITY) {
                    java.util.UUID targetId = new java.util.UUID(
                            message.targetMost, message.targetLeast);
                    result = net.vas.rdp.uav.mission.OperatorAimMissionService
                            .createEntityMission(player, targetId);
                } else {
                    result = null;
                }

                if (result == null) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "Point task rejected: invalid camera hit type."));
                    return;
                }
                String response = result.isAccepted()
                        ? result.getMessage() + " Mission " + result.getMissionId() + "."
                        : "Point task rejected: " + result.getMessage();
                player.sendMessage(new net.minecraft.util.text.TextComponentString(response));
                if (result.isAccepted()) sendMissionData(player);
            });
            return null;
        }
    }

    public static final class ServerAdoptProjectileHandler
            implements IMessageHandler<AdoptProjectile, IMessage> {
        public IMessage onMessage(final AdoptProjectile message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                MCH_EntityAircraft aircraft =
                        net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "Missile adoption rejected: controlled-aircraft operator authorization is required."));
                    return;
                }

                for (net.minecraft.entity.Entity entity : player.world.loadedEntityList) {
                    if (!(entity instanceof MCH_EntityBaseBullet)) continue;
                    MCH_EntityBaseBullet missile = (MCH_EntityBaseBullet) entity;
                    net.minecraft.entity.Entity shooter = missile.shootingAircraft == null
                            ? missile.shootingEntity : missile.shootingAircraft;
                    if (shooter == null || !aircraft.getUniqueID().equals(shooter.getUniqueID())) {
                        continue;
                    }
                    GuidedStrikeObjective objective =
                            GuidedStrikeObjective.square(
                            java.util.UUID.randomUUID(), player.world.provider.getDimension(),
                            missile.posX, missile.posY, missile.posZ,
                            player.world.getTotalWorldTime());
                    objective.authorize();
                    objective.beginTracking();
                    if (net.vas.rdp.uav.weapon.MissileGuidanceController.adopt(
                            player.world, aircraft.getUniqueID(), missile.getUniqueID(), objective)) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Missile adopted by AI guidance: " + missile.getUniqueID()));
                    } else {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Missile adoption rejected: unsupported, already owned, or unavailable."));
                    }
                    return;
                }
                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                        "Missile adoption rejected: no compatible launched missile was found."));
            });
            return null;
        }
    }

    public static final class ServerReassignGuidedHandler
            implements IMessageHandler<ReassignGuided, IMessage> {
        public IMessage onMessage(final ReassignGuided message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                try {
                    java.util.UUID missionId = java.util.UUID.fromString(message.missionId);
                    java.util.UUID targetId = java.util.UUID.fromString(message.targetId);
                    net.vas.rdp.uav.mission.RDPMission mission =
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                            .getRepository().find(missionId);
                    MCH_EntityAircraft aircraft =
                            net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                    net.minecraft.entity.Entity target = TARGET_RESOLVER.findLoaded(
                            player.world, targetId);
                    if (mission == null || mission.isTerminal()
                            || !player.getUniqueID().equals(mission.getOwnerId())
                            || mission.getGuidedWeapon() == null
                            || !net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)
                            || target == null || target == aircraft || target.isDead) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Guided reassignment rejected: mission, operator, or target is invalid."));
                        return;
                    }
                    long tick = player.world.getTotalWorldTime();
                    GuidedStrikeObjective objective =
                            GuidedStrikeObjective.entity(
                            mission.getMissionId(), targetId,
                            player.world.provider.getDimension(), target.posX,
                            target.posY, target.posZ, tick);
                    objective.authorize();
                    objective.beginTracking();
                    if (!net.vas.rdp.uav.weapon.MissileGuidanceController.reassign(
                            aircraft.getUniqueID(), objective)) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Guided reassignment rejected: no active AI projectile assignment."));
                        return;
                    }
                    mission.applyOperatorEntityLock(targetId, tick);
                    net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "Guided mission reassigned to " + target.getName() + "."));
                    sendMissionData(player);
                } catch (IllegalArgumentException invalid) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "Guided reassignment rejected: invalid identity."));
                }
            });
            return null;
        }
    }

    public static final class ServerSquareStrikeHandler
            implements IMessageHandler<SquareStrike, IMessage> {
        public IMessage onMessage(final SquareStrike message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                MCH_EntityAircraft aircraft =
                        net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "Square strike rejected: operator aircraft is unavailable."));
                    return;
                }
                String parameters = message.parameters == null ? "" : message.parameters.trim();
                String[] values = parameters.isEmpty() ? new String[0] : parameters.split("\\s+");
                if (values.length < 3 || values.length > 4) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "Square strike rejected: use centerX centerY centerZ [size]."));
                    return;
                }
                try {
                    for (String value : values) {
                        if (!Double.isFinite(Double.parseDouble(value))) {
                            throw new NumberFormatException("non-finite");
                        }
                    }
                } catch (NumberFormatException invalid) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "Square strike rejected: coordinates and size must be finite numbers."));
                    return;
                }

                java.util.Set<java.util.UUID> existingMissionIds = new java.util.HashSet<>();
                for (net.vas.rdp.uav.mission.RDPMission mission :
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                .getRepository().byOwner(player.getUniqueID())) {
                    existingMissionIds.add(mission.getMissionId());
                }
                Objects.requireNonNull(player.getServer()).getCommandManager().executeCommand(player,
                        "rdpuav mission square " + parameters);
                if (message.weapon != null && !message.weapon.trim().isEmpty()) {
                    java.util.List<net.vas.rdp.uav.mission.RDPMission> missions =
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                    .getRepository().byOwner(player.getUniqueID());
                    for (int i = missions.size() - 1; i >= 0; i--) {
                        net.vas.rdp.uav.mission.RDPMission mission = missions.get(i);
                        if (!existingMissionIds.contains(mission.getMissionId())
                                && mission.getAircraftId().equals(aircraft.getUniqueID())
                                && !mission.isTerminal()) {
                            mission.setGuidedWeapon(message.weapon);
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                    .markDirty();
                            break;
                        }
                    }
                }
            });
            return null;
        }
    }

    public static final class ServerAiTargetLockHandler
            implements IMessageHandler<AiTargetLock, IMessage> {
        public IMessage onMessage(final AiTargetLock message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                MCH_EntityAircraft aircraft =
                        net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                RdpUavDebug.event("TargetLock", "REQUEST",
                        "player", player.getUniqueID(),
                        "aircraft", aircraft == null ? null : aircraft.getUniqueID(),
                            "aircraftDefinition", aircraft == null || aircraft.getAcInfo() == null
                                    ? null : aircraft.getAcInfo().name,
                            "target", message.targetId, "clear", message.clear,
                            "dimension", player.dimension);
                LOGGER.info("Entity lock request player={} aircraft={} target={} clear={} dimension={}",
                        player.getUniqueID(), aircraft == null ? null : aircraft.getUniqueID(),
                        message.targetId, message.clear, player.dimension);
                if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)
                        || RDPUavMod.missions().ensureAuthorized(aircraft, player)) {
                    rejectTargetLock(player, aircraft, message.targetId, "OPERATOR_AUTHORIZATION");
                    return;
                }
                if (message.clear || (message.targetId == null
                        || message.targetId.trim().isEmpty())) {
                    clearTargetLock(player, aircraft);
                    return;
                }
                try {
                    net.vas.rdp.uav.profile.UavVehicleProfile profile =
                            aircraft.getAcInfo() == null ? null
                            : net.vas.rdp.uav.profile.UavVehicleProfiles.forAircraft(
                            aircraft.getAcInfo().name);
                    if (profile == null || !profile.supportsTargetLock()) {
                        rejectTargetLock(player, aircraft, message.targetId, "PROFILE_UNSUPPORTED");
                        return;
                    }
                    java.util.UUID targetId = message.targetId == null
                            || message.targetId.trim().isEmpty() ? null
                            : java.util.UUID.fromString(message.targetId);
                    net.minecraft.entity.Entity target = targetId == null ? null
                            : TARGET_RESOLVER.findLoaded(player.world, targetId);
                    if (target == null || target == aircraft || target == player
                            || !target.canBeCollidedWith()
                            || aircraft.getAcInfo() == null) {
                        rejectTargetLock(player, aircraft, message.targetId, "TARGET_UNAVAILABLE",
                                target == null ? null : net.minecraft.entity.EntityList.getKey(target),
                                target == null ? null : (double) target.getDistance(aircraft),
                                false, "NOT_CHECKED");
                        return;
                    }
                    if (!net.vas.rdp.uav.target.TargetPolicy.isSelectableExactContact(
                            player.world, target, player)) {
                        rejectTargetLock(player, aircraft, message.targetId, "TARGET_FILTERED",
                                net.minecraft.entity.EntityList.getKey(target),
                                (double) target.getDistance(aircraft), true, "REJECTED");
                        return;
                    }
                    if (target.getDistance(aircraft) > profile.getLockRange()) {
                        rejectTargetLock(player, aircraft, message.targetId, "OUT_OF_RANGE",
                                net.minecraft.entity.EntityList.getKey(target),
                                (double) target.getDistance(aircraft), true, "ACCEPTED");
                        return;
                    }
                    ResourceLocation key =
                            net.minecraft.entity.EntityList.getKey(target);
                    if (key == null) {
                        rejectTargetLock(player, aircraft, message.targetId, "UNKNOWN_TARGET_TYPE",
                                null, (double) target.getDistance(aircraft), true, "ACCEPTED");
                        return;
                    }
                    java.util.UUID assigned = RDPUavMod.missions()
                            .assignedMission(aircraft.getUniqueID());
                    net.vas.rdp.uav.mission.RDPMission mission = assigned == null ? null
                            : net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                            .getRepository().find(assigned);
                    if (mission != null
                            && mission.getState() == net.vas.rdp.uav.mission.MissionState.KAMIKADZE_TARGET
                            && !targetId.equals(mission.getTargetId())) {
                        rejectTargetLock(player, aircraft, message.targetId, "TERMINAL_TARGET_IMMUTABLE",
                                key, (double) target.getDistance(aircraft), true, "ACCEPTED");
                        return;
                    }
                    if (mission == null) {
                        rejectTargetLock(player, aircraft, message.targetId, "NO_ACTIVE_MISSION");
                        return;
                    }
                    mission.applyOperatorEntityLock(targetId,
                            player.world.getTotalWorldTime());
                    net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "UAV target lock accepted for "
                                    + key
                                    + ". Use Clear lock to release AI control."));
                    RdpUavDebug.event("TargetLock", "ACCEPTED",
                            "player", player.getUniqueID(), "aircraft", aircraft.getUniqueID(),
                            "target", target.getUniqueID(), "registryId", key.toString(),
                            "dimension", player.dimension, "distance", target.getDistance(aircraft),
                            "mission", mission.getMissionId(), "state", mission.getState());
                    RdpUavDebug.debug("Network", "AI target lock accepted player={} aircraft={} target={}",
                            player.getUniqueID(), aircraft.getUniqueID(), targetId);
                    LOGGER.info("Entity lock accepted player={} aircraft={} target={} type={} mission={}",
                            player.getUniqueID(), aircraft.getUniqueID(), targetId,
                            key, mission.getMissionId());
                } catch (IllegalArgumentException failure) {
                    rejectTargetLock(player, aircraft, message.targetId, "INVALID_TARGET_ID",
                            null, null, false, "NOT_CHECKED");
                    RdpUavDebug.debug("Network", "AI target lock rejected invalid target={}",
                            message.targetId);
                }
            });
            return null;
        }

        private void rejectTargetLock(EntityPlayerMP player, MCH_EntityAircraft aircraft,
                                      String targetId, String reason) {
            rejectTargetLock(player, aircraft, targetId, reason, null, null, false, "NOT_CHECKED");
        }

        private void rejectTargetLock(EntityPlayerMP player, MCH_EntityAircraft aircraft,
                                      String targetId, String reason, ResourceLocation registryId,
                                      Double distance, boolean loaded, String filterResult) {
            double lockRange = aircraft == null || aircraft.getAcInfo() == null
                    || net.vas.rdp.uav.profile.UavVehicleProfiles.forAircraft(
                    aircraft.getAcInfo().name) == null ? Double.NaN
                    : net.vas.rdp.uav.profile.UavVehicleProfiles.forAircraft(
                    aircraft.getAcInfo().name).getLockRange();
            RdpUavDebug.event("TargetLock", "REJECTED",
                    "player", player.getUniqueID(),
                    "aircraft", aircraft == null ? null : aircraft.getUniqueID(),
                    "target", targetId, "reason", reason,
                    "aircraftDefinition", aircraft == null || aircraft.getAcInfo() == null
                            ? null : aircraft.getAcInfo().name,
                    "profile", aircraft == null || aircraft.getAcInfo() == null ? null
                            : net.vas.rdp.uav.profile.UavVehicleProfiles.forAircraft(
                            aircraft.getAcInfo().name) == null ? null
                            : net.vas.rdp.uav.profile.UavVehicleProfiles.forAircraft(
                            aircraft.getAcInfo().name).getExecutionProfile(),
                    "registryId", registryId, "distance", distance,
                    "dimension", player.dimension, "loaded", loaded,
                    "lockRange", lockRange,
                    "filterResult", filterResult);
            String rejection = "UAV target lock rejected: "
                    + reason.toLowerCase().replace('_', ' ');
            if ("OUT_OF_RANGE".equals(reason) && distance != null && Double.isFinite(lockRange)) {
                rejection += String.format(java.util.Locale.ROOT,
                        " (%.1f blocks; profile limit %.1f).", distance, lockRange);
            } else {
                rejection += ".";
            }
            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                    rejection));
            LOGGER.warn("Entity lock rejected player={} aircraft={} target={} reason={} registryId={} distance={} lockRange={} loaded={} filter={}",
                    player.getUniqueID(), aircraft == null ? null : aircraft.getUniqueID(),
                    targetId, reason, registryId, distance, lockRange, loaded, filterResult);
        }

        private void clearTargetLock(EntityPlayerMP player, MCH_EntityAircraft aircraft) {
            java.util.List<net.vas.rdp.uav.mission.RDPMission> missions =
                    net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                            .getRepository().byOwner(player.getUniqueID());
            for (net.vas.rdp.uav.mission.RDPMission mission : missions) {
                if (!aircraft.getUniqueID().equals(mission.getAircraftId())) continue;
                if (mission.getState()
                        == net.vas.rdp.uav.mission.MissionState.KAMIKADZE_TARGET) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "Clear lock rejected: ABORT_IMPOSSIBLE_AFTER_TERMINAL_COMMIT."));
                    sendMissionData(player);
                    return;
                }
                if (mission.isTerminal()) continue;
                mission.abort("TARGET_LOCK_CLEARED", player.world.getTotalWorldTime());
                RDPUavMod.missions().release(mission);
            }
            KamikazePilotOverride.clear(aircraft.getUniqueID());
            FLIGHT_ADAPTER.release(aircraft);
            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world).markDirty();
            RdpUavDebug.event("TargetLock", "CLEARED",
                    "player", player.getUniqueID(), "aircraft", aircraft.getUniqueID(),
                    "dimension", player.dimension);
            RdpUavDebug.debug("Network", "AI target lock cleared; player control restored player={} aircraft={}",
                    player.getUniqueID(), aircraft.getUniqueID());
            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                    "UAV target lock OFF; player control restored."));
        }
    }

    public static final class ClientMissionControlHandler implements IMessageHandler<OpenMissionControl, IMessage> {
        public IMessage onMessage(OpenMissionControl message, MessageContext context) {
            net.minecraftforge.fml.client.FMLClientHandler.instance().getClient().addScheduledTask(() -> net.minecraftforge.fml.client.FMLClientHandler.instance().getClient().displayGuiScreen(
                    new net.vas.rdp.uav.client.GuiMissionControl()));
            return null;
        }
    }

    public static final class ClientMissionDataHandler implements IMessageHandler<MissionData, IMessage> {
        public IMessage onMessage(final MissionData message, MessageContext context) {
            net.minecraftforge.fml.client.FMLClientHandler.instance().getClient().addScheduledTask(() -> net.vas.rdp.uav.client.GuiMissionControl.receiveMissionData(
                    message.getRows(), message.getIds(),
                    message.getWeaponRows(), message.getWeaponNames()));
            return null;
        }

        public static final class ClientTargetTelemetryHandler
                implements IMessageHandler<RdpUavNetwork.TargetTelemetry, IMessage> {
            public IMessage onMessage(final RdpUavNetwork.TargetTelemetry message, MessageContext context) {
                net.minecraftforge.fml.client.FMLClientHandler.instance().getClient().addScheduledTask(() -> {
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
                });
                return null;
            }
        }
    }
}
