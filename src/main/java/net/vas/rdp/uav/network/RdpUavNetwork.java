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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RdpUavNetwork {
    private static final Logger LOGGER = LogManager.getLogger("RDP-UAV/Network");
    private static final int MAX_PACKET_STRING_LENGTH = 1024;
    private static final int MISSION_PAGE_SIZE = 11;
    private static final int MAX_MISSION_PAGE_COUNT =
            Integer.MAX_VALUE / MISSION_PAGE_SIZE + 1;
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(
            new ResourceLocation(RDPUavMod.MOD_ID, "control").toString());
    private static final net.vas.rdp.uav.flight.WingManFlightAdapter FLIGHT_ADAPTER =
            new net.vas.rdp.uav.flight.WingManFlightAdapter();
    private static final GuidedWeaponAdapter GUIDED_ADAPTER =
            new GuidedWeaponAdapter(FLIGHT_ADAPTER);
    private static final net.vas.rdp.uav.target.TargetResolver TARGET_RESOLVER =
            new net.vas.rdp.uav.target.TargetResolver();
    private static final Map<java.util.UUID, Deque<Long>> DESIGNATION_REQUESTS_PER_SECOND =
            new HashMap<>();
    private static final Map<java.util.UUID, Deque<Long>> DESIGNATION_REQUESTS_PER_MINUTE =
            new HashMap<>();
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
        CHANNEL.registerMessage(AiTargetLock.ServerEntityTargetAssignmentHandler.class,
                AiTargetLock.EntityTargetAssignment.class, 20, Side.SERVER);
        CHANNEL.registerMessage(ServerMissionDefinitionHandler.class,
                CreateMission.LaunchMissionDefinition.class, 21, Side.SERVER);
        CHANNEL.registerMessage(ServerCameraAimHandler.class, CameraAim.class, 22, Side.SERVER);
        CHANNEL.registerMessage(ServerOperatorTrimHandler.class, OperatorTrim.class, 23, Side.SERVER);
        CHANNEL.registerMessage(ServerLinkedRetaskHandler.class, LinkedRetask.class, 24, Side.SERVER);
        CHANNEL.registerMessage(ServerSwarmSnapshotHandler.class,
                RequestSwarmSnapshot.class, 25, Side.SERVER);
        CHANNEL.registerMessage(ServerSwarmOperatorCommandHandler.class,
                SwarmOperatorCommand.class, 27, Side.SERVER);
        CHANNEL.registerMessage(ServerSwarmConfigurationHandler.class,
                SwarmConfigurationUpdate.class, 28, Side.SERVER);
        CHANNEL.registerMessage(ServerSwarmSharedTargetHandler.class,
                SwarmSharedTargetCommand.class, 29, Side.SERVER);
        CHANNEL.registerMessage(ServerDesignationRequestHandler.class,
                PacketDesignationRequest.class, 30, Side.SERVER);
        CHANNEL.registerMessage(ServerDesignationRemoveRequestHandler.class,
                PacketDesignationRemoveRequest.class, 31, Side.SERVER);
        CHANNEL.registerMessage(ServerDesignationPromoteToMissionHandler.class,
                PacketDesignationPromoteToMission.class, 32, Side.SERVER);
        if (FMLCommonHandler.instance().getSide().isClient()) {
            CHANNEL.registerMessage(ClientSwarmSnapshotHandler.class,
                    SwarmSnapshot.class, 26, Side.CLIENT);
            CHANNEL.registerMessage(ClientMissionControlHandler.class,
                    OpenMissionControl.class, 1, Side.CLIENT);
            CHANNEL.registerMessage(ClientMissionDataHandler.class,
                    MissionData.class, 2, Side.CLIENT);
            CHANNEL.registerMessage(ClientMissionDataHandler.ClientTargetTelemetryHandler.class,
                    TargetTelemetry.class, 7, Side.CLIENT);
            CHANNEL.registerMessage(ClientSwarmTelemetryHandler.class,
                    SwarmTelemetry.class, 11, Side.CLIENT);
            CHANNEL.registerMessage(ClientDesignationConfirmHandler.class,
                    PacketDesignationConfirm.class, 33, Side.CLIENT);
            CHANNEL.registerMessage(ClientDesignationRejectHandler.class,
                    PacketDesignationReject.class, 34, Side.CLIENT);
            CHANNEL.registerMessage(ClientDesignationQueueSyncHandler.class,
                    PacketDesignationQueueSync.class, 35, Side.CLIENT);
        }
    }

    public static void openMissionControl(EntityPlayerMP player) {
        CHANNEL.sendTo(new OpenMissionControl(), player);
    }

    public static void syncDesignationQueue(EntityPlayerMP player) {
        if (player == null || player.world == null || player.world.isRemote) return;
        java.util.List<net.vas.rdp.uav.designation.DesignationView> views =
                net.vas.rdp.uav.designation.DesignationManager.forWorld(player.world)
                        .snapshotForOperator(player.getUniqueID());
        CHANNEL.sendTo(new PacketDesignationQueueSync(views), player);
    }

    public static void notifyDesignationReject(EntityPlayerMP player,
                                              net.vas.rdp.uav.designation.DesignationSource source,
                                              net.vas.rdp.uav.designation.RejectReason reason,
                                              String detail) {
        if (player == null) return;
        CHANNEL.sendTo(new PacketDesignationReject(source, reason, detail,
                player.world == null ? 0L : player.world.getTotalWorldTime()), player);
    }

    public static void notifyDesignationConfirm(EntityPlayerMP player,
                                               net.vas.rdp.uav.designation.Designation designation) {
        if (player == null || designation == null) return;
        CHANNEL.sendTo(new PacketDesignationConfirm(designation.getDesignationId(),
                        designation.getType(), designation.getSource(),
                        designation.getPoint() != null ? designation.getPoint().x : 0D,
                        designation.getPoint() != null ? designation.getPoint().y : 0D,
                        designation.getPoint() != null ? designation.getPoint().z : 0D,
                        designation.getEntity() != null ? designation.getEntity().entityUuid : null,
                        designation.getProvenance(), designation.getConfidence(),
                        designation.getExpiresAtTick()), player);
    }

    private static boolean allowDesignationRequest(java.util.UUID operatorId) {
        long now = System.currentTimeMillis();
        Deque<Long> secondWindow = DESIGNATION_REQUESTS_PER_SECOND.get(operatorId);
        if (secondWindow == null) {
            secondWindow = new ArrayDeque<>();
            DESIGNATION_REQUESTS_PER_SECOND.put(operatorId, secondWindow);
        }
        while (!secondWindow.isEmpty() && now - secondWindow.peekFirst() > 1000L) {
            secondWindow.removeFirst();
        }
        Deque<Long> minuteWindow = DESIGNATION_REQUESTS_PER_MINUTE.get(operatorId);
        if (minuteWindow == null) {
            minuteWindow = new ArrayDeque<>();
            DESIGNATION_REQUESTS_PER_MINUTE.put(operatorId, minuteWindow);
        }
        while (!minuteWindow.isEmpty() && now - minuteWindow.peekFirst() > 60000L) {
            minuteWindow.removeFirst();
        }
        if (secondWindow.size() >= net.vas.rdp.uav.designation.DesignationConfig.requestsPerSecond
                || minuteWindow.size() >= net.vas.rdp.uav.designation.DesignationConfig.requestsPerMinute) {
            return false;
        }
        secondWindow.addLast(now);
        minuteWindow.addLast(now);
        return true;
    }

    private static boolean isDuplicateDesignation(net.vas.rdp.uav.designation.DesignationManager manager,
                                                EntityPlayerMP player,
                                                net.vas.rdp.uav.designation.Designation candidate) {
        if (manager == null || candidate == null) return false;
        for (net.vas.rdp.uav.designation.Designation existing : manager.byOwner(player.getUniqueID())) {
            if (existing == null || existing.isExpired(player.world.getTotalWorldTime())
                    || existing.getAttachedMissionId() != null) continue;
            if (existing.getEntity() != null && candidate.getEntity() != null
                    && existing.getEntity().entityUuid.equals(candidate.getEntity().entityUuid)) {
                return true;
            }
            if (existing.getPoint() != null && candidate.getPoint() != null) {
                double dx = existing.getPoint().x - candidate.getPoint().x;
                double dy = existing.getPoint().y - candidate.getPoint().y;
                double dz = existing.getPoint().z - candidate.getPoint().z;
                double radiusSq = net.vas.rdp.uav.designation.DesignationConfig.duplicatePointRadius;
                if (dx * dx + dy * dy + dz * dz <= radiusSq * radiusSq) return true;
            }
        }
        return false;
    }

    public static void requestMissionData() {
        requestMissionData(0);
    }

    public static void requestMissionData(int page) {
        CHANNEL.sendToServer(new RequestMissionData(page));
    }

    public static void requestSwarmSnapshot(String selectedSwarmId) {
        CHANNEL.sendToServer(new RequestSwarmSnapshot(selectedSwarmId));
    }

    public static void sendSwarmOperatorCommand(int operation, String swarmId,
                                               String memberId, String name) {
        CHANNEL.sendToServer(new SwarmOperatorCommand(operation, swarmId, memberId, name));
    }

    public static void updateSwarmConfiguration(String swarmId,
                                                net.vas.rdp.uav.swarm.SwarmConfiguration configuration,
                                                boolean participation) {
        if (swarmId == null || configuration == null) return;
        CHANNEL.sendToServer(new SwarmConfigurationUpdate(swarmId, configuration, participation));
    }

    public static void lockSharedSwarmTarget(String swarmId, java.util.UUID targetId) {
        if (swarmId == null || targetId == null) return;
        CHANNEL.sendToServer(new SwarmSharedTargetCommand(swarmId, targetId, false));
    }

    public static void clearSharedSwarmTarget(String swarmId) {
        if (swarmId == null) return;
        CHANNEL.sendToServer(new SwarmSharedTargetCommand(swarmId, null, true));
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
        sendOperatorTrim(aircraftId, missionId, sequence, clientTick,
                yawDelta, pitchDelta, 0.0F);
    }

    /** Send bounded camera/key corrections; the client never supplies setpoints. */
    public static void sendOperatorTrim(java.util.UUID aircraftId, java.util.UUID missionId,
                                        long sequence, long clientTick,
                                        float yawDelta, float pitchDelta, float rollDelta) {
        if (aircraftId == null || missionId == null || sequence < 0L || clientTick < 0L
                || !Float.isFinite(yawDelta) || !Float.isFinite(pitchDelta)
                || !Float.isFinite(rollDelta)) return;
        CHANNEL.sendToServer(new OperatorTrim(aircraftId, missionId, sequence,
                clientTick, yawDelta, pitchDelta, rollDelta));
    }

    public static void retaskLinkedStrike(String parentMissionId, java.util.UUID targetId) {
        if (parentMissionId == null || targetId == null) return;
        CHANNEL.sendToServer(new LinkedRetask(parentMissionId, targetId));
    }

    public static boolean launchMissionDefinition(MissionDefinition definition) {
        if (definition == null) return false;
        MissionValidationResult validation = MissionValidationResult.validate(definition);
        if (!validation.isValid()) return false;
        if (definition.isSwarmEnabled()) {
            if (definition.getMissionType() == net.vas.rdp.uav.mission.MissionType.PATROL
                    || definition.getSwarmId() == null) return false;
            CHANNEL.sendToServer(new CreateMission.LaunchMissionDefinition(definition));
            return true;
        }
        if (definition.getMissionType() == net.vas.rdp.uav.mission.MissionType.SQUARE_STRIKE) {
            if (!definition.getSelectedUavs().isEmpty() || definition.isDefaultTargetPolicy()
                    || definition.getCruiseAltitudeY() != null) return false;
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

    public static void createSquareStrike(String parameters, String weapon, Integer cruiseAltitudeY) {
        CHANNEL.sendToServer(new SquareStrike(parameters, weapon, cruiseAltitudeY));
    }

    public static void lockAiTarget(java.util.UUID targetId) {
        if (targetId != null) CHANNEL.sendToServer(new AiTargetLock(targetId, false));
    }

    public static void assignEntityTarget(String missionId, java.util.UUID targetId) {
        if (missionId != null && targetId != null && !missionId.trim().isEmpty()) {
            CHANNEL.sendToServer(new AiTargetLock.EntityTargetAssignment(missionId, targetId));
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
        sendMissionData(player, 0);
    }

    static void sendMissionData(EntityPlayerMP player, int page) {
        CHANNEL.sendTo(MissionData.from(player, page), player);
    }

    private static void reject(EntityPlayerMP player, String message) {
        player.sendMessage(new net.minecraft.util.text.TextComponentString(message));
    }

    public static void syncTargetTelemetry(EntityPlayerMP player) {
        CHANNEL.sendTo(TargetTelemetry.from(player), player);
    }

    public static void syncSwarmTelemetry(EntityPlayerMP player) {
        sendSwarmSnapshot(player, "");
    }

    private static void sendSwarmSnapshot(EntityPlayerMP player, String selectedId) {
        java.util.List<net.vas.rdp.uav.swarm.SwarmRecord> owned = new java.util.ArrayList<>();
        for (net.vas.rdp.uav.swarm.SwarmRecord swarm :
                net.vas.rdp.uav.persistence.RDPUavSwarmWorldData.get(player.world)
                        .getRepository().snapshot()) {
            if (player.getUniqueID().equals(swarm.getOwnerId())) owned.add(swarm);
        }
        owned.sort(java.util.Comparator.comparing(swarm -> swarm.getName().toLowerCase(
                java.util.Locale.ROOT) + swarm.getSwarmId().toString()));
        CHANNEL.sendTo(SwarmSnapshot.from(owned, selectedId), player);
    }

    public static final class RequestSwarmSnapshot implements IMessage {
        private String selectedSwarmId = "";
        public RequestSwarmSnapshot() { }
        public RequestSwarmSnapshot(String selectedSwarmId) {
            this.selectedSwarmId = selectedSwarmId == null ? "" : selectedSwarmId;
        }
        public void fromBytes(ByteBuf buffer) { selectedSwarmId = readString(buffer); }
        public void toBytes(ByteBuf buffer) { writeString(buffer, selectedSwarmId); }
    }

    public static final class SwarmSnapshot implements IMessage {
        private final java.util.List<net.vas.rdp.uav.client.SwarmGuiState.Swarm> swarms =
                new java.util.ArrayList<>();
        public SwarmSnapshot() { }
        public SwarmSnapshot(java.util.List<net.vas.rdp.uav.client.SwarmGuiState.Swarm> entries) {
            if (entries != null) swarms.addAll(entries);
        }
        public java.util.List<net.vas.rdp.uav.client.SwarmGuiState.Swarm> getSwarms() {
            return java.util.Collections.unmodifiableList(swarms);
        }

        private static SwarmSnapshot from(
                java.util.List<net.vas.rdp.uav.swarm.SwarmRecord> owned, String selectedId) {
            SwarmSnapshot packet = new SwarmSnapshot();
            java.util.UUID selected;
            try { selected = selectedId == null || selectedId.trim().isEmpty()
                    ? null : java.util.UUID.fromString(selectedId); }
            catch (IllegalArgumentException invalid) { selected = null; }
            for (int i = 0; i < owned.size() && i < 64; i++) {
                net.vas.rdp.uav.swarm.SwarmRecord record = owned.get(i);
                java.util.List<net.vas.rdp.uav.client.SwarmGuiState.Member> members =
                        new java.util.ArrayList<>();
                if (record.getSwarmId().equals(selected)) {
                    for (net.vas.rdp.uav.swarm.SwarmMember member : record.getMembers()) {
                        if (members.size() >= 64) break;
                        members.add(new net.vas.rdp.uav.client.SwarmGuiState.Member(
                                member.getAircraftId(), member.getProfile(), member.getRole().name(),
                                member.getState().name(), member.isAvailable(),
                                member.isOperatorControlled(), member.getAircraftId().equals(
                                record.getDesignatedLeader())));
                    }
                }
                net.vas.rdp.uav.swarm.SwarmConfiguration configuration = record.getConfiguration();
                net.vas.rdp.uav.swarm.SwarmStatus status =
                        net.vas.rdp.uav.swarm.SwarmStatus.from(record);
                packet.swarms.add(new net.vas.rdp.uav.client.SwarmGuiState.Swarm(
                        record.getSwarmId(), record.getName(), record.getState().name(),
                        record.getMissionState().name(), configuration.getFormation().name(),
                        configuration.getSearchPattern().name(), configuration.getLeadership().name(),
                        configuration.getMaximumMembers(), configuration.getHorizontalSpacing(),
                        configuration.getVerticalSpacing(), configuration.isAllowDuplicateTargets(),
                        configuration.isAllowCommanderFailover(), record.isMissionParticipation(),
                        String.valueOf(status.getTargetId()), status.getTargetState().name(), members));
            }
            return packet;
        }

        public void fromBytes(ByteBuf buffer) {
            swarms.clear();
            int count = buffer.readUnsignedByte();
            if (count > 64) throw new IllegalArgumentException("Too many swarms in snapshot");
            for (int i = 0; i < count; i++) {
                java.util.UUID id = new java.util.UUID(buffer.readLong(), buffer.readLong());
                String name = readString(buffer);
                String state = readString(buffer);
                String missionState = readString(buffer);
                String formation = readString(buffer);
                String search = readString(buffer);
                String leadership = readString(buffer);
                int maximum = buffer.readUnsignedByte();
                double horizontal = buffer.readDouble();
                double vertical = buffer.readDouble();
                boolean duplicates = buffer.readBoolean();
                boolean failover = buffer.readBoolean();
                boolean participation = buffer.readBoolean();
                String target = readString(buffer);
                String targetState = readString(buffer);
                int membersCount = buffer.readUnsignedByte();
                if (membersCount > 64) throw new IllegalArgumentException("Too many swarm members");
                java.util.List<net.vas.rdp.uav.client.SwarmGuiState.Member> members =
                        new java.util.ArrayList<>();
                for (int memberIndex = 0; memberIndex < membersCount; memberIndex++) {
                    java.util.UUID aircraft = new java.util.UUID(buffer.readLong(), buffer.readLong());
                    members.add(new net.vas.rdp.uav.client.SwarmGuiState.Member(aircraft,
                            readString(buffer), readString(buffer), readString(buffer),
                            buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()));
                }
                swarms.add(new net.vas.rdp.uav.client.SwarmGuiState.Swarm(id, name, state,
                        missionState, formation, search, leadership, maximum, horizontal, vertical,
                        duplicates, failover, participation, target, targetState, members));
            }
        }

        public void toBytes(ByteBuf buffer) {
            int count = Math.min(64, swarms.size());
            buffer.writeByte(count);
            for (int i = 0; i < count; i++) {
                net.vas.rdp.uav.client.SwarmGuiState.Swarm swarm = swarms.get(i);
                buffer.writeLong(swarm.id.getMostSignificantBits());
                buffer.writeLong(swarm.id.getLeastSignificantBits());
                writeString(buffer, swarm.name);
                writeString(buffer, swarm.state);
                writeString(buffer, swarm.missionState);
                writeString(buffer, swarm.formation);
                writeString(buffer, swarm.searchPattern);
                writeString(buffer, swarm.leadership);
                buffer.writeByte(Math.max(1, Math.min(64, swarm.maximumMembers)));
                buffer.writeDouble(swarm.horizontalSpacing);
                buffer.writeDouble(swarm.verticalSpacing);
                buffer.writeBoolean(swarm.allowDuplicates);
                buffer.writeBoolean(swarm.commanderFailover);
                buffer.writeBoolean(swarm.participation);
                writeString(buffer, swarm.target);
                writeString(buffer, swarm.targetState);
                int membersCount = Math.min(64, swarm.members.size());
                buffer.writeByte(membersCount);
                for (int memberIndex = 0; memberIndex < membersCount; memberIndex++) {
                    net.vas.rdp.uav.client.SwarmGuiState.Member member = swarm.members.get(memberIndex);
                    buffer.writeLong(member.aircraftId.getMostSignificantBits());
                    buffer.writeLong(member.aircraftId.getLeastSignificantBits());
                    writeString(buffer, member.profile);
                    writeString(buffer, member.role);
                    writeString(buffer, member.state);
                    buffer.writeBoolean(member.available);
                    buffer.writeBoolean(member.operatorControlled);
                    buffer.writeBoolean(member.designatedLeader);
                }
            }
        }
    }

    public static final class ServerSwarmSnapshotHandler
            implements IMessageHandler<RequestSwarmSnapshot, IMessage> {
        public IMessage onMessage(final RequestSwarmSnapshot message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(
                    () -> sendSwarmSnapshot(player, message.selectedSwarmId));
            return null;
        }
    }

    public static final class ClientSwarmSnapshotHandler
            implements IMessageHandler<SwarmSnapshot, IMessage> {
        public IMessage onMessage(final SwarmSnapshot message, MessageContext context) {
            net.minecraftforge.fml.client.FMLClientHandler.instance().getClient()
                    .addScheduledTask(() -> net.vas.rdp.uav.client.SwarmGuiState
                            .replace(message.swarms));
            return null;
        }
    }

    public static final class SwarmOperatorCommand implements IMessage {
        public static final int CREATE = 0;
        public static final int ADD_OPERATED_AIRCRAFT = 1;
        public static final int REMOVE_MEMBER = 2;
        public static final int PAUSE = 3;
        public static final int RESUME = 4;
        public static final int DISBAND = 5;
        public static final int SET_DESIGNATED_LEADER = 6;
        private int operation;
        private String swarmId = "";
        private String memberId = "";
        private String name = "";

        public SwarmOperatorCommand() { }
        public SwarmOperatorCommand(int operation, String swarmId,
                                    String memberId, String name) {
            this.operation = operation;
            this.swarmId = swarmId == null ? "" : swarmId;
            this.memberId = memberId == null ? "" : memberId;
            this.name = name == null ? "" : name;
        }
        public void fromBytes(ByteBuf buffer) {
            operation = buffer.readUnsignedByte();
            swarmId = readString(buffer);
            memberId = readString(buffer);
            name = readString(buffer);
        }
        public void toBytes(ByteBuf buffer) {
            buffer.writeByte(Math.max(0, Math.min(6, operation)));
            writeString(buffer, swarmId);
            writeString(buffer, memberId);
            writeString(buffer, name);
        }
    }

    public static final class ServerSwarmOperatorCommandHandler
            implements IMessageHandler<SwarmOperatorCommand, IMessage> {
        public IMessage onMessage(final SwarmOperatorCommand message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                String result;
                MCH_EntityAircraft operated =
                        net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(operated, player)) {
                    reject(player, "Swarm command rejected: operator control is required.");
                    return;
                }
                try {
                    net.vas.rdp.uav.swarm.SwarmManager swarms = RDPUavMod.swarms();
                    if (message.operation == SwarmOperatorCommand.CREATE) {
                        int count = 0;
                        for (net.vas.rdp.uav.swarm.SwarmRecord item :
                                net.vas.rdp.uav.persistence.RDPUavSwarmWorldData.get(player.world)
                                        .getRepository().snapshot()) {
                            if (player.getUniqueID().equals(item.getOwnerId())) count++;
                        }
                        if (count >= 64) throw new IllegalArgumentException("Owner swarm limit reached");
                        String name = message.name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
                        if (name.isEmpty()) name = "UAV swarm " + (count + 1);
                        if (name.length() > 24) name = name.substring(0, 24);
                        net.vas.rdp.uav.swarm.SwarmRecord created =
                                swarms.createForOperatedAircraft(player.world,
                                        player.getUniqueID(), name, operated);
                        result = "Created swarm " + created.getName() + " with the operated UAV.";
                        sendSwarmSnapshot(player, created.getSwarmId().toString());
                    } else {
                        java.util.UUID id = java.util.UUID.fromString(message.swarmId);
                        boolean accepted;
                        switch (message.operation) {
                            case SwarmOperatorCommand.ADD_OPERATED_AIRCRAFT:
                                accepted = swarms.addOperatedAircraft(player.world,
                                        player.getUniqueID(), id, operated);
                                result = accepted ? "Operated UAV added to swarm."
                                        : "Swarm add rejected: UAV is unsupported, unavailable, or already enrolled.";
                                break;
                            case SwarmOperatorCommand.REMOVE_MEMBER:
                                accepted = swarms.removeMember(player.world, player.getUniqueID(),
                                        id, java.util.UUID.fromString(message.memberId));
                                result = accepted ? "Swarm member removed."
                                        : "Swarm removal rejected: member authorization failed.";
                                break;
                            case SwarmOperatorCommand.PAUSE:
                                accepted = swarms.setState(player.world, player.getUniqueID(), id,
                                        net.vas.rdp.uav.swarm.SwarmState.AWAITING_OPERATOR);
                                result = accepted ? "Swarm missions paused." : "Swarm pause rejected.";
                                break;
                            case SwarmOperatorCommand.RESUME:
                                accepted = swarms.setState(player.world, player.getUniqueID(), id,
                                        net.vas.rdp.uav.swarm.SwarmState.READY);
                                result = accepted ? "Swarm missions resumed." : "Swarm resume rejected.";
                                break;
                            case SwarmOperatorCommand.DISBAND:
                                accepted = swarms.setState(player.world, player.getUniqueID(), id,
                                        net.vas.rdp.uav.swarm.SwarmState.DISBANDED);
                                result = accepted ? "Swarm disbanded and its active member missions were aborted."
                                        : "Swarm disband rejected.";
                                break;
                            case SwarmOperatorCommand.SET_DESIGNATED_LEADER:
                                accepted = swarms.setDesignatedLeader(player.world,
                                        player.getUniqueID(), id,
                                        java.util.UUID.fromString(message.memberId));
                                result = accepted ? "Designated swarm leader updated."
                                        : "Leader selection rejected: member authorization failed.";
                                break;
                            default:
                                throw new IllegalArgumentException("Unknown swarm operation");
                        }
                        sendSwarmSnapshot(player, id.toString());
                    }
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(result));
                    sendMissionData(player);
                } catch (IllegalArgumentException failure) {
                    reject(player, "Swarm command rejected: "
                            + (failure.getMessage() == null ? "invalid request" : failure.getMessage()));
                    sendSwarmSnapshot(player, "");
                }
            });
            return null;
        }
    }

    public static final class SwarmConfigurationUpdate implements IMessage {
        private String swarmId = "";
        private int maximumMembers = 1;
        private double horizontalSpacing = 16D;
        private double verticalSpacing = 10D;
        private String formation = net.vas.rdp.uav.swarm.SwarmFormation.LINE.name();
        private String searchPattern = net.vas.rdp.uav.swarm.SearchPattern.GRID.name();
        private String leadership = net.vas.rdp.uav.swarm.SwarmLeadership.DYNAMIC_LEADER.name();
        private boolean allowDuplicates;
        private boolean commanderFailover = true;
        private boolean participation = true;

        public SwarmConfigurationUpdate() { }
        public SwarmConfigurationUpdate(String swarmId,
                net.vas.rdp.uav.swarm.SwarmConfiguration configuration, boolean participation) {
            this.swarmId = swarmId;
            maximumMembers = configuration.getMaximumMembers();
            horizontalSpacing = configuration.getHorizontalSpacing();
            verticalSpacing = configuration.getVerticalSpacing();
            formation = configuration.getFormation().name();
            searchPattern = configuration.getSearchPattern().name();
            leadership = configuration.getLeadership().name();
            allowDuplicates = configuration.isAllowDuplicateTargets();
            commanderFailover = configuration.isAllowCommanderFailover();
            this.participation = participation;
        }
        public void fromBytes(ByteBuf buffer) {
            swarmId = readString(buffer);
            maximumMembers = buffer.readUnsignedByte();
            horizontalSpacing = buffer.readDouble();
            verticalSpacing = buffer.readDouble();
            formation = readString(buffer);
            searchPattern = readString(buffer);
            leadership = readString(buffer);
            allowDuplicates = buffer.readBoolean();
            commanderFailover = buffer.readBoolean();
            participation = buffer.readBoolean();
        }
        public void toBytes(ByteBuf buffer) {
            writeString(buffer, swarmId);
            buffer.writeByte(Math.max(1, Math.min(64, maximumMembers)));
            buffer.writeDouble(horizontalSpacing);
            buffer.writeDouble(verticalSpacing);
            writeString(buffer, formation);
            writeString(buffer, searchPattern);
            writeString(buffer, leadership);
            buffer.writeBoolean(allowDuplicates);
            buffer.writeBoolean(commanderFailover);
            buffer.writeBoolean(participation);
        }
    }

    public static final class ServerSwarmConfigurationHandler
            implements IMessageHandler<SwarmConfigurationUpdate, IMessage> {
        public IMessage onMessage(final SwarmConfigurationUpdate message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                try {
                    java.util.UUID id = java.util.UUID.fromString(message.swarmId);
                    if (message.maximumMembers < 1
                            || message.maximumMembers > net.vas.rdp.uav.swarm.SwarmConfig.maximumSize) {
                        throw new IllegalArgumentException("Member limit exceeds server policy");
                    }
                    net.vas.rdp.uav.swarm.SwarmConfiguration configuration =
                            net.vas.rdp.uav.swarm.SwarmConfiguration.builder()
                                    .maximumMembers(message.maximumMembers)
                                    .horizontalSpacing(message.horizontalSpacing)
                                    .verticalSpacing(message.verticalSpacing)
                                    .formation(net.vas.rdp.uav.swarm.SwarmFormation.valueOf(message.formation))
                                    .searchPattern(net.vas.rdp.uav.swarm.SearchPattern.valueOf(message.searchPattern))
                                    .leadership(net.vas.rdp.uav.swarm.SwarmLeadership.valueOf(message.leadership))
                                    .allowDuplicateTargets(message.allowDuplicates)
                                    .allowCommanderFailover(message.commanderFailover).build();
                    boolean accepted = RDPUavMod.swarms().updateConfiguration(player.world,
                            player.getUniqueID(), id, configuration, message.participation);
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(accepted
                            ? "Swarm configuration updated."
                            : "Swarm configuration rejected: owner or server-bound validation failed."));
                    sendSwarmSnapshot(player, id.toString());
                } catch (IllegalArgumentException invalid) {
                    reject(player, "Swarm configuration rejected: invalid swarm or settings.");
                    sendSwarmSnapshot(player, "");
                }
            });
            return null;
        }
    }

    public static final class SwarmSharedTargetCommand implements IMessage {
        private String swarmId = "";
        private String targetId = "";
        private boolean clear;
        public SwarmSharedTargetCommand() { }
        public SwarmSharedTargetCommand(String swarmId, java.util.UUID targetId, boolean clear) {
            this.swarmId = swarmId == null ? "" : swarmId;
            this.targetId = targetId == null ? "" : targetId.toString();
            this.clear = clear;
        }
        public void fromBytes(ByteBuf buffer) {
            swarmId = readString(buffer);
            targetId = readString(buffer);
            clear = buffer.readBoolean();
        }
        public void toBytes(ByteBuf buffer) {
            writeString(buffer, swarmId);
            writeString(buffer, targetId);
            buffer.writeBoolean(clear);
        }
    }

    public static final class ServerSwarmSharedTargetHandler
            implements IMessageHandler<SwarmSharedTargetCommand, IMessage> {
        public IMessage onMessage(final SwarmSharedTargetCommand message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                MCH_EntityAircraft aircraft =
                        net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player);
                if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(aircraft, player)) {
                    reject(player, "Shared swarm target rejected: operator control is required.");
                    return;
                }
                try {
                    java.util.UUID swarmId = java.util.UUID.fromString(message.swarmId);
                    boolean accepted = message.clear
                            ? RDPUavMod.swarms().clearSharedTarget(player.world,
                                    player.getUniqueID(), swarmId)
                            : RDPUavMod.swarms().lockSharedTarget(player.world,
                                    player.getUniqueID(), swarmId,
                                    java.util.UUID.fromString(message.targetId),
                                    player.world.getTotalWorldTime());
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(accepted
                            ? message.clear ? "Shared swarm target cleared." : "Shared swarm target locked."
                            : "Shared swarm target rejected by ownership or target policy."));
                    sendSwarmSnapshot(player, swarmId.toString());
                } catch (IllegalArgumentException invalid) {
                    reject(player, "Shared swarm target rejected: invalid identity.");
                }
            });
            return null;
        }
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
                        if (!RDPUavMod.swarms().owns(record, player.getUniqueID())
                                || message.maximumMembers < 1
                                || message.maximumMembers > net.vas.rdp.uav.swarm.SwarmConfig.maximumSize) {
                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    "Swarm configuration rejected: owner authorization or size validation failed."));
                            return;
                        }
                        net.vas.rdp.uav.swarm.SwarmConfiguration configuration = record.getConfiguration()
                                .toBuilder().maximumMembers(message.maximumMembers)
                                .allowDuplicateTargets(message.allowDuplicates).build();
                        if (!RDPUavMod.swarms().updateConfiguration(player.world,
                                player.getUniqueID(), id, configuration, message.participation)) {
                            throw new IllegalArgumentException("Swarm configuration failed server validation");
                        }
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
        private int page;

        public RequestMissionData() { }

        public RequestMissionData(int page) {
            if (page < 0) throw new IllegalArgumentException("Mission page cannot be negative");
            this.page = page;
        }

        public int getPage() { return page; }

        public void fromBytes(ByteBuf buffer) {
            if (buffer.readableBytes() != 0 && buffer.readableBytes() != 4) {
                throw new IllegalArgumentException("Invalid mission page request");
            }
            page = buffer.isReadable() ? buffer.readInt() : 0;
            if (page < 0) throw new IllegalArgumentException("Mission page cannot be negative");
        }

        public void toBytes(ByteBuf buffer) { buffer.writeInt(page); }
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
            private int version = 5;
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
            private String swarmId = "";
            private final java.util.List<java.util.UUID> selectedUavs = new java.util.ArrayList<>();
            private String swarmFormation = net.vas.rdp.uav.swarm.SwarmFormation.LINE.name();
            private String swarmSearchPattern = net.vas.rdp.uav.swarm.SearchPattern.GRID.name();
            private String swarmLeadership = net.vas.rdp.uav.swarm.SwarmLeadership.DYNAMIC_LEADER.name();
            private double swarmHorizontalSpacing = 16D;
            private double swarmVerticalSpacing = 10D;
            private boolean swarmAllowDuplicates;
            private boolean swarmCommanderFailover = true;
            private boolean hasCruiseAltitudeY;
            private int cruiseAltitudeY;

            public LaunchMissionDefinition() { }

            public LaunchMissionDefinition(MissionDefinition definition) {
                this.type = definition.getMissionType().name();
                this.target = definition.getTarget();
                this.attack = definition.isAttack();
                this.priority = definition.getPriority().ordinal();
                this.weapon = definition.getGuidedWeapon();
                this.swarmEnabled = definition.isSwarmEnabled();
                this.swarmMaximumMembers = definition.getSwarmMaximumMembers();
                this.swarmId = definition.getSwarmId() == null
                        ? "" : definition.getSwarmId().toString();
                this.selectedUavs.addAll(definition.getSelectedUavs());
                net.vas.rdp.uav.swarm.SwarmConfiguration swarm = definition.getSwarmConfiguration();
                this.swarmFormation = swarm.getFormation().name();
                this.swarmSearchPattern = swarm.getSearchPattern().name();
                this.swarmLeadership = swarm.getLeadership().name();
                this.swarmHorizontalSpacing = swarm.getHorizontalSpacing();
                this.swarmVerticalSpacing = swarm.getVerticalSpacing();
                this.swarmAllowDuplicates = swarm.isAllowDuplicateTargets();
                this.swarmCommanderFailover = swarm.isAllowCommanderFailover();
                this.unsupportedConfiguration = false;
                this.policyInclude = joinPolicy(definition.getTargetPolicy().getInclude());
                this.policyRequire = joinPolicy(definition.getTargetPolicy().getRequire());
                this.policyExclude = joinPolicy(definition.getTargetPolicy().getExclude());
                this.policyRejectEmbedded = definition.getTargetPolicy().isRejectEmbedded();
                this.policyAllowVehicles = definition.getTargetPolicy().isAllowVehicleTargets();
                this.policyMinimumConfidence = definition.getTargetPolicy().getMinimumConfidence();
                this.patrolRoute = net.vas.rdp.uav.mission.PatrolRouteCodec
                        .encodeOrEmpty(definition.getWaypoints());
                if (definition.getCruiseAltitudeY() != null) {
                    this.hasCruiseAltitudeY = true;
                    this.cruiseAltitudeY = definition.getCruiseAltitudeY();
                }
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
                if (version >= 4) {
                    swarmId = readString(buffer);
                    int count = buffer.readUnsignedByte();
                    if (count > 64) throw new IllegalArgumentException("Too many selected UAVs");
                    selectedUavs.clear();
                    for (int i = 0; i < count; i++) {
                        selectedUavs.add(new java.util.UUID(buffer.readLong(), buffer.readLong()));
                    }
                    swarmFormation = readString(buffer);
                    swarmSearchPattern = readString(buffer);
                    swarmLeadership = readString(buffer);
                    swarmHorizontalSpacing = buffer.readDouble();
                    swarmVerticalSpacing = buffer.readDouble();
                    swarmAllowDuplicates = buffer.readBoolean();
                    swarmCommanderFailover = buffer.readBoolean();
                }
                if (version >= 5) {
                    hasCruiseAltitudeY = buffer.readBoolean();
                    if (hasCruiseAltitudeY) cruiseAltitudeY = buffer.readInt();
                }
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
                if (version >= 4) {
                    writeString(buffer, swarmId);
                    int count = Math.min(64, selectedUavs.size());
                    buffer.writeByte(count);
                    for (int i = 0; i < count; i++) {
                        java.util.UUID id = selectedUavs.get(i);
                        buffer.writeLong(id.getMostSignificantBits());
                        buffer.writeLong(id.getLeastSignificantBits());
                    }
                    writeString(buffer, swarmFormation);
                    writeString(buffer, swarmSearchPattern);
                    writeString(buffer, swarmLeadership);
                    buffer.writeDouble(swarmHorizontalSpacing);
                    buffer.writeDouble(swarmVerticalSpacing);
                    buffer.writeBoolean(swarmAllowDuplicates);
                    buffer.writeBoolean(swarmCommanderFailover);
                }
                if (version >= 5) {
                    buffer.writeBoolean(hasCruiseAltitudeY);
                    if (hasCruiseAltitudeY) buffer.writeInt(cruiseAltitudeY);
                }
            }

            public MissionDefinition toMissionDefinition() {
                net.vas.rdp.uav.mission.MissionDefinition definition = new MissionDefinition()
                        .setMissionType(net.vas.rdp.uav.mission.MissionType.valueOf(type))
                        .setTarget(target).setGuidedWeapon(weapon).setAttack(attack)
                        .setPriority(net.vas.rdp.uav.target.TargetPriority.values()[
                                Math.max(0, Math.min(3, priority))])
                        .setSwarmEnabled(swarmEnabled).setSwarmMaximumMembers(swarmMaximumMembers);
                if (swarmId != null && !swarmId.trim().isEmpty()) {
                    definition.setSwarmId(java.util.UUID.fromString(swarmId));
                }
                for (java.util.UUID id : selectedUavs) definition.selectUav(id);
                definition.setSwarmConfiguration(net.vas.rdp.uav.swarm.SwarmConfiguration.builder()
                        .maximumMembers(swarmMaximumMembers)
                        .horizontalSpacing(swarmHorizontalSpacing)
                        .verticalSpacing(swarmVerticalSpacing)
                        .allowDuplicateTargets(swarmAllowDuplicates)
                        .allowCommanderFailover(swarmCommanderFailover)
                        .formation(net.vas.rdp.uav.swarm.SwarmFormation.valueOf(swarmFormation))
                        .searchPattern(net.vas.rdp.uav.swarm.SearchPattern.valueOf(swarmSearchPattern))
                        .leadership(net.vas.rdp.uav.swarm.SwarmLeadership.valueOf(swarmLeadership))
                        .build());
                net.vas.rdp.uav.target.TargetPolicy.Builder policy =
                        net.vas.rdp.uav.target.TargetPolicy.builder()
                                .include(policyValues(policyInclude))
                                .require(policyValues(policyRequire))
                                .exclude(policyValues(policyExclude))
                                .rejectDeeplyEmbedded(policyRejectEmbedded)
                                .allowHostileVehicles(policyAllowVehicles)
                                .withMinimumConfidence(policyMinimumConfidence);
                definition.setTargetPolicy(policy.snapshot());
                if (hasCruiseAltitudeY) definition.setCruiseAltitudeY(cruiseAltitudeY);
                return definition;
            }

            private static String[] policyValues(String encoded) {
                if (encoded == null || encoded.trim().isEmpty()) return new String[0];
                return encoded.split("\\|", -1);
            }

            public String getSwarmId() { return swarmId; }
            public java.util.List<java.util.UUID> getSelectedUavs() {
                return java.util.Collections.unmodifiableList(selectedUavs);
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

    public static final class PacketDesignationRequest implements IMessage {
        private net.vas.rdp.uav.designation.DesignationSource source;
        private net.vas.rdp.uav.designation.DesignationType hintedType;
        private double rayOriginX, rayOriginY, rayOriginZ;
        private double rayDirX, rayDirY, rayDirZ;
        private double rayMaxDistance;
        private long candidateEntityMost, candidateEntityLeast;
        private String candidateRegistryId = "";
        private int clientScreenWidth, clientScreenHeight;
        private long clientFrameSequence;

        public PacketDesignationRequest() { }

        public PacketDesignationRequest(net.vas.rdp.uav.designation.DesignationSource source,
                                      net.vas.rdp.uav.designation.DesignationType hintedType,
                                      double rayOriginX, double rayOriginY, double rayOriginZ,
                                      double rayDirX, double rayDirY, double rayDirZ,
                                      double rayMaxDistance, java.util.UUID candidateEntityUuid,
                                      String candidateRegistryId, int clientScreenWidth,
                                      int clientScreenHeight, long clientFrameSequence) {
            this.source = source;
            this.hintedType = hintedType;
            this.rayOriginX = rayOriginX; this.rayOriginY = rayOriginY; this.rayOriginZ = rayOriginZ;
            this.rayDirX = rayDirX; this.rayDirY = rayDirY; this.rayDirZ = rayDirZ;
            this.rayMaxDistance = rayMaxDistance;
            if (candidateEntityUuid != null) {
                candidateEntityMost = candidateEntityUuid.getMostSignificantBits();
                candidateEntityLeast = candidateEntityUuid.getLeastSignificantBits();
            }
            this.candidateRegistryId = candidateRegistryId == null ? "" : candidateRegistryId;
            this.clientScreenWidth = clientScreenWidth; this.clientScreenHeight = clientScreenHeight;
            this.clientFrameSequence = clientFrameSequence;
        }

        public void fromBytes(ByteBuf buffer) {
            source = net.vas.rdp.uav.designation.DesignationSource.values()[buffer.readByte()];
            hintedType = net.vas.rdp.uav.designation.DesignationType.values()[buffer.readByte()];
            rayOriginX = buffer.readDouble(); rayOriginY = buffer.readDouble(); rayOriginZ = buffer.readDouble();
            rayDirX = buffer.readDouble(); rayDirY = buffer.readDouble(); rayDirZ = buffer.readDouble();
            rayMaxDistance = buffer.readDouble();
            candidateEntityMost = buffer.readLong(); candidateEntityLeast = buffer.readLong();
            candidateRegistryId = readString(buffer);
            clientScreenWidth = buffer.readInt(); clientScreenHeight = buffer.readInt();
            clientFrameSequence = buffer.readLong();
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeByte(source == null ? 0 : source.ordinal());
            buffer.writeByte(hintedType == null ? 0 : hintedType.ordinal());
            buffer.writeDouble(rayOriginX); buffer.writeDouble(rayOriginY); buffer.writeDouble(rayOriginZ);
            buffer.writeDouble(rayDirX); buffer.writeDouble(rayDirY); buffer.writeDouble(rayDirZ);
            buffer.writeDouble(rayMaxDistance);
            buffer.writeLong(candidateEntityMost); buffer.writeLong(candidateEntityLeast);
            writeString(buffer, candidateRegistryId == null ? "" : candidateRegistryId);
            buffer.writeInt(clientScreenWidth); buffer.writeInt(clientScreenHeight);
            buffer.writeLong(clientFrameSequence);
        }

        public java.util.UUID candidateEntityUuid() {
            return candidateEntityMost == 0L && candidateEntityLeast == 0L ? null : new java.util.UUID(candidateEntityMost, candidateEntityLeast);
        }
    }

    public static final class PacketDesignationRemoveRequest implements IMessage {
        private long designationMost, designationLeast;
        public PacketDesignationRemoveRequest() { }
        public PacketDesignationRemoveRequest(java.util.UUID designationId) {
            designationMost = designationId.getMostSignificantBits();
            designationLeast = designationId.getLeastSignificantBits();
        }
        public void fromBytes(ByteBuf buffer) { designationMost = buffer.readLong(); designationLeast = buffer.readLong(); }
        public void toBytes(ByteBuf buffer) { buffer.writeLong(designationMost); buffer.writeLong(designationLeast); }
        public java.util.UUID designationId() { return new java.util.UUID(designationMost, designationLeast); }
    }

    public static final class PacketDesignationPromoteToMission implements IMessage {
        private java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        private String missionType = "STRIKE";
        public PacketDesignationPromoteToMission() { }
        public PacketDesignationPromoteToMission(java.util.List<java.util.UUID> designationIds, String missionType) {
            if (designationIds != null) {
                for (java.util.UUID designationId : designationIds) {
                    ids.add(designationId.getMostSignificantBits());
                    ids.add(designationId.getLeastSignificantBits());
                }
            }
            this.missionType = missionType == null ? "STRIKE" : missionType;
        }
        public void fromBytes(ByteBuf buffer) {
            int count = buffer.readInt();
            ids.clear();
            for (int i=0; i<count; i++) ids.add(buffer.readLong());
            missionType = readString(buffer);
        }
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(ids.size() / 2);
            for (long value : ids) buffer.writeLong(value);
            writeString(buffer, missionType);
        }
        public java.util.List<java.util.UUID> designationIds() {
            java.util.ArrayList<java.util.UUID> result = new java.util.ArrayList<>();
            for (int i = 0; i + 1 < ids.size(); i += 2) {
                result.add(new java.util.UUID(ids.get(i), ids.get(i + 1)));
            }
            return result;
        }
        public String missionType() { return missionType; }
    }

    public static final class PacketDesignationConfirm implements IMessage {
        private long designationMost, designationLeast;
        private byte type;
        private byte source;
        private double resolvedX, resolvedY, resolvedZ;
        private long resolvedEntityMost, resolvedEntityLeast;
        private String provenance = "";
        private float confidence;
        private long expiresAtTick;
        public PacketDesignationConfirm() { }
        public PacketDesignationConfirm(java.util.UUID designationId, net.vas.rdp.uav.designation.DesignationType type,
                                      net.vas.rdp.uav.designation.DesignationSource source,
                                      double resolvedX, double resolvedY, double resolvedZ,
                                      java.util.UUID resolvedEntityUuid, String provenance,
                                      float confidence, long expiresAtTick) {
            designationMost = designationId.getMostSignificantBits(); designationLeast = designationId.getLeastSignificantBits();
            this.type = (byte) type.ordinal(); this.source = (byte) source.ordinal();
            this.resolvedX = resolvedX; this.resolvedY = resolvedY; this.resolvedZ = resolvedZ;
            if (resolvedEntityUuid != null) {
                resolvedEntityMost = resolvedEntityUuid.getMostSignificantBits();
                resolvedEntityLeast = resolvedEntityUuid.getLeastSignificantBits();
            }
            this.provenance = provenance == null ? "" : provenance; this.confidence = confidence; this.expiresAtTick = expiresAtTick;
        }
        public void fromBytes(ByteBuf buffer) {
            designationMost = buffer.readLong(); designationLeast = buffer.readLong();
            type = buffer.readByte(); source = buffer.readByte();
            resolvedX = buffer.readDouble(); resolvedY = buffer.readDouble(); resolvedZ = buffer.readDouble();
            resolvedEntityMost = buffer.readLong(); resolvedEntityLeast = buffer.readLong();
            provenance = readString(buffer); confidence = buffer.readFloat(); expiresAtTick = buffer.readLong();
        }
        public void toBytes(ByteBuf buffer) {
            buffer.writeLong(designationMost); buffer.writeLong(designationLeast);
            buffer.writeByte(type); buffer.writeByte(source);
            buffer.writeDouble(resolvedX); buffer.writeDouble(resolvedY); buffer.writeDouble(resolvedZ);
            buffer.writeLong(resolvedEntityMost); buffer.writeLong(resolvedEntityLeast);
            writeString(buffer, provenance); buffer.writeFloat(confidence); buffer.writeLong(expiresAtTick);
        }
        public java.util.UUID designationId() { return new java.util.UUID(designationMost, designationLeast); }
        public net.vas.rdp.uav.designation.DesignationType type() { return net.vas.rdp.uav.designation.DesignationType.values()[type]; }
        public net.vas.rdp.uav.designation.DesignationSource source() { return net.vas.rdp.uav.designation.DesignationSource.values()[source]; }
        public java.util.UUID resolvedEntityUuid() { return resolvedEntityMost == 0L && resolvedEntityLeast == 0L ? null : new java.util.UUID(resolvedEntityMost, resolvedEntityLeast); }
        public double resolvedX() { return resolvedX; } public double resolvedY() { return resolvedY; } public double resolvedZ() { return resolvedZ; }
        public String provenance() { return provenance; } public float confidence() { return confidence; } public long expiresAtTick() { return expiresAtTick; }
    }

    public static final class PacketDesignationReject implements IMessage {
        private byte source;
        private byte reason;
        private String detail = "";
        private long serverTick;
        public PacketDesignationReject() { }
        public PacketDesignationReject(net.vas.rdp.uav.designation.DesignationSource source,
                                      net.vas.rdp.uav.designation.RejectReason reason, String detail,
                                      long serverTick) {
            this.source = (byte) source.ordinal(); this.reason = (byte) reason.ordinal(); this.detail = detail == null ? "" : detail; this.serverTick = serverTick;
        }
        public void fromBytes(ByteBuf buffer) { source = buffer.readByte(); reason = buffer.readByte(); detail = readString(buffer); serverTick = buffer.readLong(); }
        public void toBytes(ByteBuf buffer) { buffer.writeByte(source); buffer.writeByte(reason); writeString(buffer, detail); buffer.writeLong(serverTick); }
        public net.vas.rdp.uav.designation.DesignationSource source() { return net.vas.rdp.uav.designation.DesignationSource.values()[source]; }
        public net.vas.rdp.uav.designation.RejectReason reason() { return net.vas.rdp.uav.designation.RejectReason.values()[reason]; }
        public String detail() { return detail; } public long serverTick() { return serverTick; }
    }

    public static final class PacketDesignationQueueSync implements IMessage {
        private java.util.ArrayList<String> entries = new java.util.ArrayList<>();
        public PacketDesignationQueueSync() { }
        public PacketDesignationQueueSync(java.util.List<net.vas.rdp.uav.designation.DesignationView> designations) {
            if (designations != null) {
                for (net.vas.rdp.uav.designation.DesignationView designation : designations) {
                    StringBuilder builder = new StringBuilder();
                    builder.append(designation.getDesignationId().toString()).append('|');
                    builder.append(designation.getType().name()).append('|');
                    builder.append(designation.getSource().name()).append('|');
                    builder.append(designation.getProvenance() == null ? "" : designation.getProvenance()).append('|');
                    builder.append(designation.getConfidence()).append('|');
                    builder.append(designation.getExpiresAtTick()).append('|');
                    builder.append(designation.getAttachedMissionId() == null ? "" : designation.getAttachedMissionId().toString()).append('|');
                    builder.append(designation.getResolvedX()).append('|');
                    builder.append(designation.getResolvedY()).append('|');
                    builder.append(designation.getResolvedZ()).append('|');
                    builder.append(designation.getResolvedEntityUuid() == null ? "" : designation.getResolvedEntityUuid().toString());
                    entries.add(builder.toString());
                }
            }
        }
        public void fromBytes(ByteBuf buffer) {
            int count = buffer.readInt();
            entries.clear();
            for (int i = 0; i < count; i++) entries.add(readString(buffer));
        }
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(entries.size());
            for (String entry : entries) writeString(buffer, entry);
        }
        public java.util.List<net.vas.rdp.uav.designation.DesignationView> designations() {
            java.util.ArrayList<net.vas.rdp.uav.designation.DesignationView> result = new java.util.ArrayList<>();
            for (String entry : entries) {
                String[] parts = entry.split("\\|");
                if (parts.length < 11) continue;
                java.util.UUID designationId = java.util.UUID.fromString(parts[0]);
                net.vas.rdp.uav.designation.DesignationType type = net.vas.rdp.uav.designation.DesignationType.valueOf(parts[1]);
                net.vas.rdp.uav.designation.DesignationSource source = net.vas.rdp.uav.designation.DesignationSource.valueOf(parts[2]);
                java.util.UUID attachedMissionId = parts[6].isEmpty() ? null : java.util.UUID.fromString(parts[6]);
                java.util.UUID resolvedEntityUuid = parts[10].isEmpty() ? null : java.util.UUID.fromString(parts[10]);
                result.add(new net.vas.rdp.uav.designation.DesignationView(designationId, type, source, parts[3],
                        Float.parseFloat(parts[4]), Long.parseLong(parts[5]), attachedMissionId,
                        Double.parseDouble(parts[7]), Double.parseDouble(parts[8]), Double.parseDouble(parts[9]), resolvedEntityUuid));
            }
            return result;
        }
    }

    public static final class ServerDesignationRequestHandler implements IMessageHandler<PacketDesignationRequest, IMessage> {
        @Override
        public IMessage onMessage(final PacketDesignationRequest message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                try {
                    if (message == null || player == null || player.world == null) {
                        notifyDesignationReject(player,
                                net.vas.rdp.uav.designation.DesignationSource.CROSSHAIR,
                                net.vas.rdp.uav.designation.RejectReason.INTERNAL_ERROR,
                                "Null designation request");
                        return;
                    }
                    net.vas.rdp.uav.designation.DesignationManager manager =
                            net.vas.rdp.uav.designation.DesignationManager.forWorld(player.world);
                    if (!net.vas.rdp.uav.mission.UavControlAccess.isOperator(
                            net.vas.rdp.uav.mission.UavControlAccess.findControlledAircraft(player), player)) {
                        notifyDesignationReject(player,
                                message.source == null ? net.vas.rdp.uav.designation.DesignationSource.CROSSHAIR : message.source,
                                net.vas.rdp.uav.designation.RejectReason.OPERATOR_NOT_AUTHORIZED,
                                "Operator is not the active aircraft owner or controller");
                        return;
                    }
                    if (!allowDesignationRequest(player.getUniqueID())) {
                        notifyDesignationReject(player,
                                message.source == null ? net.vas.rdp.uav.designation.DesignationSource.CROSSHAIR : message.source,
                                net.vas.rdp.uav.designation.RejectReason.INTERNAL_ERROR,
                                "Designation request limit exceeded");
                        return;
                    }
                    if (message.rayMaxDistance <= 0D
                            || message.rayMaxDistance > net.vas.rdp.uav.designation.DesignationConfig.designationEnvelopeBlocks) {
                        notifyDesignationReject(player,
                                message.source == null ? net.vas.rdp.uav.designation.DesignationSource.CROSSHAIR : message.source,
                                net.vas.rdp.uav.designation.RejectReason.OUT_OF_ENVELOPE,
                                "Requested ray exceeds configured designation envelope");
                        return;
                    }
                    if (manager.countPending(player.getUniqueID()) >=
                            net.vas.rdp.uav.designation.DesignationConfig.maxDesignationsPerOperator) {
                        notifyDesignationReject(player,
                                message.source == null ? net.vas.rdp.uav.designation.DesignationSource.CROSSHAIR : message.source,
                                net.vas.rdp.uav.designation.RejectReason.QUEUE_FULL,
                                "Operator designation queue is full");
                        return;
                    }

                    java.util.UUID candidateEntityUuid = message.candidateEntityUuid();
                    net.minecraft.entity.Entity candidateEntity = null;
                    if (candidateEntityUuid != null) {
                        for (net.minecraft.entity.Entity loaded : player.world.loadedEntityList) {
                            if (loaded.getUniqueID().equals(candidateEntityUuid)) {
                                candidateEntity = loaded;
                                break;
                            }
                        }
                    }
                    if (candidateEntityUuid != null && (candidateEntity == null || candidateEntity.isDead
                            || candidateEntity.dimension != player.world.provider.getDimension())) {
                        notifyDesignationReject(player,
                                message.source == null ? net.vas.rdp.uav.designation.DesignationSource.CROSSHAIR : message.source,
                                net.vas.rdp.uav.designation.RejectReason.ENTITY_NOT_LOADED,
                                "Candidate entity is not loaded in this dimension");
                        return;
                    }
                    if (candidateEntity != null
                            && !net.vas.rdp.uav.target.TargetPolicy.isSelectableExactContact(
                                    player.world, candidateEntity, player)) {
                        notifyDesignationReject(player,
                                message.source == null ? net.vas.rdp.uav.designation.DesignationSource.CROSSHAIR : message.source,
                                net.vas.rdp.uav.designation.RejectReason.ENTITY_NOT_ELIGIBLE,
                                "Candidate entity is not eligible for designation");
                        return;
                    }

                    final net.vas.rdp.uav.designation.DesignationSource source =
                            message.source == null ? net.vas.rdp.uav.designation.DesignationSource.CROSSHAIR : message.source;
                    final net.vas.rdp.uav.designation.DesignationType type =
                            message.hintedType == null ? net.vas.rdp.uav.designation.DesignationType.POINT : message.hintedType;
                    net.minecraft.util.math.Vec3d origin = new net.minecraft.util.math.Vec3d(
                            message.rayOriginX, message.rayOriginY, message.rayOriginZ);
                    net.minecraft.util.math.Vec3d direction = new net.minecraft.util.math.Vec3d(
                            message.rayDirX, message.rayDirY, message.rayDirZ);
                    if (direction.lengthSquared() <= 0.0D) {
                        notifyDesignationReject(player, source,
                                net.vas.rdp.uav.designation.RejectReason.NO_HIT,
                                "Invalid ray direction");
                        return;
                    }
                    net.minecraft.util.math.Vec3d normalized = direction.normalize();
                    net.minecraft.util.math.Vec3d end = origin.add(normalized.scale(message.rayMaxDistance));
                    net.minecraft.util.math.RayTraceResult hit = player.world.rayTraceBlocks(origin, end, false, true, false);
                    net.vas.rdp.uav.designation.Designation.PointPayload pointPayload = null;
                    net.vas.rdp.uav.designation.Designation.EntityPayload entityPayload = null;
                    if (candidateEntity != null) {
                        entityPayload = new net.vas.rdp.uav.designation.Designation.EntityPayload(
                                candidateEntity.getUniqueID(),
                                net.minecraft.entity.EntityList.getKey(candidateEntity) == null ? null
                                        : net.minecraft.entity.EntityList.getKey(candidateEntity).toString(),
                                candidateEntity.posX, candidateEntity.posY, candidateEntity.posZ,
                                player.world.getTotalWorldTime());
                    } else {
                        if (hit == null) {
                            notifyDesignationReject(player, source,
                                    net.vas.rdp.uav.designation.RejectReason.NO_HIT,
                                    "No valid target was resolved from the client ray");
                            return;
                        }
                        net.minecraft.util.math.Vec3d resolved = hit.hitVec == null ? end : hit.hitVec;
                        pointPayload = new net.vas.rdp.uav.designation.Designation.PointPayload(
                                resolved.x, resolved.y, resolved.z, false,
                                (int) Math.floor(resolved.y));
                    }

                    net.vas.rdp.uav.designation.Designation designation = net.vas.rdp.uav.designation.Designation.builder()
                            .operatorId(player.getUniqueID())
                            .dimension(player.dimension)
                            .type(type)
                            .source(source)
                            .createdAtTick(player.world.getTotalWorldTime())
                            .expiresAtTick(player.world.getTotalWorldTime() + net.vas.rdp.uav.designation.DesignationConfig.designationTtlTicks)
                            .confidence(1.0F)
                            .provenance(candidateEntity != null
                                    ? (net.minecraft.entity.EntityList.getKey(candidateEntity) == null ? "entity" : net.minecraft.entity.EntityList.getKey(candidateEntity).toString())
                                    : (hit == null ? "ray" : "ray-hit"))
                            .point(pointPayload)
                            .entity(entityPayload)
                            .priority(net.vas.rdp.uav.target.TargetPriority.NORMAL)
                            .notes("Queued from server-authoritative operator designation")
                            .build();

                    if (isDuplicateDesignation(manager, player, designation)) {
                        notifyDesignationReject(player, source,
                                net.vas.rdp.uav.designation.RejectReason.DUPLICATE,
                                "Another designation already matches this target");
                        return;
                    }

                    net.vas.rdp.uav.designation.DesignationWorldData.get(player.world).add(designation);
                    manager.add(designation);
                    notifyDesignationConfirm(player, designation);
                    syncDesignationQueue(player);
                } catch (RuntimeException ex) {
                    notifyDesignationReject(player,
                            net.vas.rdp.uav.designation.DesignationSource.CROSSHAIR,
                            net.vas.rdp.uav.designation.RejectReason.INTERNAL_ERROR,
                            ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    RdpUavDebug.warn("Designation", "Request rejected: " + ex.getMessage());
                }
            });
            return null;
        }
    }

    public static final class ServerDesignationRemoveRequestHandler implements IMessageHandler<PacketDesignationRemoveRequest, IMessage> {
        @Override
        public IMessage onMessage(final PacketDesignationRemoveRequest message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                if (message == null || player == null) return;
                net.vas.rdp.uav.designation.DesignationManager manager =
                        net.vas.rdp.uav.designation.DesignationManager.forWorld(player.world);
                net.vas.rdp.uav.designation.Designation designation = manager.find(message.designationId());
                if (designation == null || !player.getUniqueID().equals(designation.getOperatorId())) {
                    return;
                }
                net.vas.rdp.uav.designation.DesignationWorldData.get(player.world).remove(designation.getDesignationId());
                manager.remove(designation.getDesignationId());
                syncDesignationQueue(player);
            });
            return null;
        }
    }

    public static final class ServerDesignationPromoteToMissionHandler implements IMessageHandler<PacketDesignationPromoteToMission, IMessage> {
        @Override
        public IMessage onMessage(final PacketDesignationPromoteToMission message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
                if (message == null || player == null) return;
                net.vas.rdp.uav.designation.DesignationManager manager =
                        net.vas.rdp.uav.designation.DesignationManager.forWorld(player.world);
                for (java.util.UUID designationId : message.designationIds()) {
                    net.vas.rdp.uav.designation.Designation designation = manager.find(designationId);
                    if (designation == null || !player.getUniqueID().equals(designation.getOperatorId())) {
                        continue;
                    }
                    try {
                        net.vas.rdp.uav.mission.MissionType missionType =
                                net.vas.rdp.uav.mission.MissionType.STRIKE;
                        net.vas.rdp.uav.mission.RDPMission mission =
                                manager.promoteToMission(player, designationId, missionType);
                        if (mission != null) {
                            notifyDesignationConfirm(player, designation);
                        } else {
                            notifyDesignationReject(player,
                                    designation.getSource(),
                                    net.vas.rdp.uav.designation.RejectReason.INTERNAL_ERROR,
                                    "Designation could not be promoted to a mission");
                        }
                    } catch (RuntimeException ex) {
                        notifyDesignationReject(player,
                                designation.getSource(),
                                net.vas.rdp.uav.designation.RejectReason.INTERNAL_ERROR,
                                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                    }
                }
                syncDesignationQueue(player);
            });
            return null;
        }
    }

    public static final class ClientDesignationConfirmHandler implements IMessageHandler<PacketDesignationConfirm, IMessage> {
        @Override
        public IMessage onMessage(final PacketDesignationConfirm message, MessageContext context) {
            if (message == null) return null;
            RDPUavMod.removeClientDesignation(message.designationId());
            return null;
        }
    }

    public static final class ClientDesignationRejectHandler implements IMessageHandler<PacketDesignationReject, IMessage> {
        @Override
        public IMessage onMessage(final PacketDesignationReject message, MessageContext context) {
            if (message == null) return null;
            return null;
        }
    }

    public static final class ClientDesignationQueueSyncHandler implements IMessageHandler<PacketDesignationQueueSync, IMessage> {
        @Override
        public IMessage onMessage(final PacketDesignationQueueSync message, MessageContext context) {
            if (message == null) return null;
            RDPUavMod.replaceClientDesignationQueue(message.designations());
            return null;
        }
    }

    /** Authenticated, sequenced operator attitude correction. */
    public static final class OperatorTrim implements IMessage {
        private long aircraftMost, aircraftLeast, missionMost, missionLeast;
        private long sequence;
        private long clientTick;
        private float yawDelta, pitchDelta, rollDelta;

        public OperatorTrim() { }
        private OperatorTrim(java.util.UUID aircraftId, java.util.UUID missionId,
                             long sequence, long clientTick, float yawDelta, float pitchDelta,
                             float rollDelta) {
            this.aircraftMost = aircraftId.getMostSignificantBits();
            this.aircraftLeast = aircraftId.getLeastSignificantBits();
            this.missionMost = missionId.getMostSignificantBits();
            this.missionLeast = missionId.getLeastSignificantBits();
            this.sequence = sequence;
            this.clientTick = clientTick;
            this.yawDelta = yawDelta;
            this.pitchDelta = pitchDelta;
            this.rollDelta = rollDelta;
        }
        public void fromBytes(ByteBuf buffer) {
            aircraftMost = buffer.readLong(); aircraftLeast = buffer.readLong();
            missionMost = buffer.readLong(); missionLeast = buffer.readLong();
            sequence = buffer.readLong(); clientTick = buffer.readLong();
            yawDelta = buffer.readFloat(); pitchDelta = buffer.readFloat();
            rollDelta = buffer.isReadable() ? buffer.readFloat() : 0.0F;
        }
        public void toBytes(ByteBuf buffer) {
            buffer.writeLong(aircraftMost); buffer.writeLong(aircraftLeast);
            buffer.writeLong(missionMost); buffer.writeLong(missionLeast);
            buffer.writeLong(sequence); buffer.writeLong(clientTick);
            buffer.writeFloat(yawDelta); buffer.writeFloat(pitchDelta);
            buffer.writeFloat(rollDelta);
        }
        private java.util.UUID aircraftId() { return new java.util.UUID(aircraftMost, aircraftLeast); }
        private java.util.UUID missionId() { return new java.util.UUID(missionMost, missionLeast); }
        float getRollDelta() { return rollDelta; }
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
                        message.yawDelta, message.pitchDelta, message.rollDelta,
                        player.world.getTotalWorldTime(), profile);
                if (accepted) {
                    RdpUavDebug.event("OperatorTrim", "ATTITUDE_CORRECTION_ACCEPTED",
                            "mission", mission.getMissionId(), "aircraft", aircraft.getUniqueID(),
                            "operator", player.getUniqueID(), "sequence", message.sequence,
                            "yawDelta", message.yawDelta, "pitchDelta", message.pitchDelta,
                            "rollDelta", message.rollDelta,
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
        private boolean hasCruiseAltitudeY;
        private int cruiseAltitudeY;
        public SquareStrike() { }
        public SquareStrike(String parameters) {
            this(parameters, "");
        }
        public SquareStrike(String parameters, String weapon) {
            this(parameters, weapon, null);
        }
        public SquareStrike(String parameters, String weapon, Integer cruiseAltitudeY) {
            this.parameters = parameters == null ? "" : parameters;
            this.weapon = weapon == null ? "" : weapon;
            if (cruiseAltitudeY != null) {
                this.hasCruiseAltitudeY = true;
                this.cruiseAltitudeY = cruiseAltitudeY;
            }
        }

        public void fromBytes(ByteBuf buffer) {
            parameters = readString(buffer);
            if (buffer.isReadable()) weapon = readString(buffer);
            if (buffer.isReadable()) {
                hasCruiseAltitudeY = buffer.readBoolean();
                if (hasCruiseAltitudeY) cruiseAltitudeY = buffer.readInt();
            }
        }
        public void toBytes(ByteBuf buffer) {
            writeString(buffer, parameters); writeString(buffer, weapon);
            buffer.writeBoolean(hasCruiseAltitudeY);
            if (hasCruiseAltitudeY) buffer.writeInt(cruiseAltitudeY);
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
        public boolean isClearRequest() { return clear; }
        public boolean hasTargetId() {
            return targetId != null && !targetId.trim().isEmpty();
        }
        public boolean isInvalidLockRequest() {
            return !clear && !hasTargetId();
        }

        public static final class EntityTargetAssignment implements IMessage {
            private String missionId = "";
            private String targetId = "";

            public EntityTargetAssignment() { }

            public EntityTargetAssignment(String missionId, java.util.UUID targetId) {
                this.missionId = missionId == null ? "" : missionId;
                this.targetId = targetId == null ? "" : targetId.toString();
            }

            public void fromBytes(ByteBuf buffer) {
                missionId = readString(buffer);
                targetId = readString(buffer);
            }

            public void toBytes(ByteBuf buffer) {
                writeString(buffer, missionId);
                writeString(buffer, targetId);
            }

            public String getMissionId() {
                return missionId;
            }

            public java.util.UUID getTargetId() {
                return targetId == null || targetId.trim().isEmpty()
                        ? null : java.util.UUID.fromString(targetId);
            }
        }

        public static final class ServerEntityTargetAssignmentHandler
                implements IMessageHandler<EntityTargetAssignment, IMessage> {
            public IMessage onMessage(final EntityTargetAssignment message, MessageContext context) {
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
                        java.util.UUID targetId = message.getTargetId();
                        if (targetId == null) {
                            throw new IllegalArgumentException("Target UUID is required");
                        }
                        net.vas.rdp.uav.mission.RDPMission mission =
                                net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                        .getRepository().find(missionId);
                        if (mission == null || mission.isTerminal()
                                || !player.getUniqueID().equals(mission.getOwnerId())
                                || !aircraft.getUniqueID().equals(mission.getAircraftId())) {
                            throw new IllegalArgumentException("Mission is not assignable");
                        }
                        net.vas.rdp.uav.target.TargetResolver.Resolution resolution =
                                TARGET_RESOLVER.resolveLoaded(player.world, targetId);
                        net.minecraft.entity.Entity target = resolution.getEntity();
                        if (target == null) {
                            throw new IllegalArgumentException(resolution.getCode().name());
                        }
                        if (target == aircraft || target == player || !target.canBeCollidedWith()
                                || !net.vas.rdp.uav.target.TargetPolicy.isSelectableExactContact(
                                player.world, target, player)) {
                            throw new IllegalArgumentException("INVALID_TARGET");
                        }
                        net.vas.rdp.uav.profile.UavVehicleProfile profile =
                                aircraft.getAcInfo() == null ? null
                                : net.vas.rdp.uav.profile.UavVehicleProfiles.forAircraft(
                                aircraft.getAcInfo().name);
                        if (profile == null || !profile.supportsTargetLock()) {
                            throw new IllegalArgumentException("PROFILE_UNSUPPORTED");
                        }
                        if (target.getDistance(aircraft) > profile.getLockRange()) {
                            throw new IllegalArgumentException("TARGET_OUT_OF_RANGE");
                        }
                        if (!net.vas.rdp.uav.target.TargetPolicy.hasLineOfSight(
                                player.world, aircraft, target)) {
                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    "UAV target assignment rejected: LINE_OF_SIGHT_BLOCKED."));
                            return;
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
                                "UAV entity target assigned: " + target.getUniqueID()));
                    } catch (IllegalArgumentException failure) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "UAV entity target rejected: "
                                        + (failure.getMessage() == null
                                        ? "INVALID_TARGET" : failure.getMessage()) + "."));
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
        private int page;
        private int pageCount = 1;
        public MissionData() { }
        MissionData(String[] rows, String[] ids, String[] weaponRows,
                    String[] weaponNames, int page, int pageCount) {
            this.rows = rows; this.ids = ids; this.weaponRows = weaponRows;
            this.weaponNames = weaponNames; this.page = page; this.pageCount = pageCount;
        }
        static MissionData from(EntityPlayerMP player, int requestedPage) {
            java.util.List<net.vas.rdp.uav.mission.RDPMission> missions =
                    net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                            .getRepository().byOwner(player.getUniqueID());
            int pageCount = missions.isEmpty() ? 1
                    : (missions.size() - 1) / MISSION_PAGE_SIZE + 1;
            int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
            int start = page * MISSION_PAGE_SIZE;
            int end = Math.min(missions.size(), start + MISSION_PAGE_SIZE);
            String[] rows = new String[end - start];
            String[] ids = new String[end - start];
            for (int i = start; i < end; i++) {
                net.vas.rdp.uav.mission.RDPMission mission = missions.get(i);
                ids[i - start] = mission.getMissionId().toString();
                boolean waitState = mission.getState()
                        == net.vas.rdp.uav.mission.MissionState.WAITING_FOR_TARGET
                        || mission.getState()
                        == net.vas.rdp.uav.mission.MissionState.LOST;
                boolean holdUnavailable = (mission.getState()
                        == net.vas.rdp.uav.mission.MissionState.HOLDING
                        && "HOLD_UNAVAILABLE_LAST_SAFE_ROUTE".equals(
                        mission.getLastTransitionReason())
                        || (waitState && !net.vas.rdp.uav.profile.UavVehicleProfiles
                        .forAircraft(mission.getAircraftType()).supportsStationHold()));
                boolean strikeAuthorizationRequired = mission.isAttack()
                        && !mission.isDestructiveAuthorized()
                        && mission.getState()
                        == net.vas.rdp.uav.mission.MissionState.TARGET_LOCKED;
                String authorizationStatus = strikeAuthorizationRequired
                        ? "  AUTH REQUIRED"
                        : mission.isAttack() && mission.isDestructiveAuthorized()
                        ? "  AUTHORIZED" : "";
                rows[i - start] = mission.getMissionId().toString().substring(0, 8) + "  "
                        + mission.getState().name() + authorizationStatus
                        + (holdUnavailable ? "  HOLD N/A" : "")
                        + "  " + mission.getAircraftType()
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
                    int ammunition = weapon.getAmmo() + weapon.getAmmoReserve();
                    String reason = capability == null ? "UNREGISTERED_CAPABILITY"
                            : capability.availabilityReason(ammunition);
                    String status = "OK".equals(reason) ? "AVAILABLE" : "BLOCKED";
                    String behavior = capability == null ? "UNREGISTERED"
                            : capability.getBehavior().name();
                    String objectives = capability == null ? "[]" : capability.getObjectives().toString();
                    weaponRows.add(status + ":" + reason + " " + weapon.getName() + "/"
                            + weapon.getInfo().type + " behavior=" + behavior
                            + " objectives=" + objectives + " ammo=" + ammunition);
                    if ("OK".equals(reason)) {
                        // Only an executable, loaded capability belongs in the
                        // operator's weapon selector; every other weapon remains
                        // visible with its authoritative blocked reason.
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
                    weaponNames.toArray(new String[0]), page, pageCount);
        }
        public String[] getRows() { return rows; }
        public String[] getIds() { return ids; }
        public String[] getWeaponRows() { return weaponRows; }
        public String[] getWeaponNames() { return weaponNames; }
        public int getPage() { return page; }
        public int getPageCount() { return pageCount; }
        public void fromBytes(ByteBuf buffer) {
            rows = new String[0];
            ids = new String[0];
            weaponRows = new String[0];
            weaponNames = new String[0];
            page = 0;
            pageCount = 1;
            if (buffer.readableBytes() < 4) {
                throw new IllegalArgumentException("Invalid mission row count");
            }
            int count = buffer.readInt();
            if (count < 0 || count > MISSION_PAGE_SIZE) {
                throw new IllegalArgumentException("Invalid mission row count");
            }
            rows = new String[count];
            ids = new String[count];
            for (int i = 0; i < count; i++) {
                ids[i] = readString(buffer);
                rows[i] = readString(buffer);
            }
            if (!buffer.isReadable()) {
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
            if (buffer.isReadable()) {
                if (buffer.readableBytes() != 8) {
                    throw new IllegalArgumentException("Invalid mission page metadata");
                }
                page = buffer.readInt();
                pageCount = buffer.readInt();
                if (pageCount < 1 || page > pageCount - 1 || page < 0) {
                    throw new IllegalArgumentException("Invalid mission page metadata");
                }
            }
        }
        public void toBytes(ByteBuf buffer) {
            if (rows == null || ids == null || weaponRows == null || weaponNames == null) {
                throw new IllegalArgumentException("Invalid mission snapshot");
            }
            if (rows.length > MISSION_PAGE_SIZE || ids.length != rows.length
                    || weaponRows.length > 64 || weaponNames.length > 64
                    || weaponNames.length > weaponRows.length || page < 0 || pageCount < 1
                    || page >= pageCount) {
                throw new IllegalArgumentException("Invalid mission snapshot");
            }
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
            buffer.writeInt(page);
            buffer.writeInt(pageCount);
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
                    net.vas.rdp.uav.mission.RDPMission selected =
                            net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                    .getRepository().activeForAircraft(ridden.getUniqueID(),
                                            player.getUniqueID());
                    if (selected == null) {
                        return new TargetTelemetry(
                                player.dimension);
                    }

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
            Objects.requireNonNull(player.getServer()).addScheduledTask(
                    () -> sendMissionData(player, message.getPage()));
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
                    net.minecraft.entity.Entity lockedTarget = null;
                    if (mission.getTargetId() != null) {
                        lockedTarget = TARGET_RESOLVER.findLoaded(
                                player.world, mission.getTargetId());
                        if (lockedTarget == null) {
                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                    "Attack authorization rejected: locked target is no longer loaded."));
                            return;
                        }
                        x = lockedTarget.posX;
                        y = lockedTarget.posY;
                        z = lockedTarget.posZ;
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
                    if (lockedTarget != null
                            && !net.vas.rdp.uav.target.TargetPolicy.isVisuallyObservable(
                            player.world, aircraft, lockedTarget, profile.getLockRange())) {
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Attack authorization rejected: locked target is not freshly visible."));
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
                    long authorizationTick = player.world.getTotalWorldTime();
                    mission.authorizeDestructiveAction(authorizationTick);
                    mission.transitionTo(net.vas.rdp.uav.mission.MissionState.ATTACK_TARGET,
                            authorizationTick);
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
                    net.minecraft.entity.Entity lockedTarget = null;
                    if (mission.getTargetId() != null) {
                        lockedTarget = TARGET_RESOLVER.findLoaded(
                                player.world, mission.getTargetId());
                        if (lockedTarget == null) {
                            rejectKamikaze(player, "Nose-dive rejected: locked target is no longer loaded.");
                            return;
                        }
                        x = lockedTarget.posX; y = lockedTarget.posY; z = lockedTarget.posZ;
                        net.vas.rdp.uav.profile.UavVehicleProfile profile =
                                net.vas.rdp.uav.profile.UavVehicleProfiles.forAircraft(
                                        aircraft.getAcInfo() == null
                                                ? null : aircraft.getAcInfo().name);
                        if (profile == null || !profile.supportsTargetLock()
                                || !net.vas.rdp.uav.target.TargetPolicy.isVisuallyObservable(
                                player.world, aircraft, lockedTarget, profile.getLockRange())) {
                            rejectKamikaze(player,
                                    "Nose-dive rejected: locked target is not freshly visible.");
                            return;
                        }
                    }
                    if (FLIGHT_ADAPTER.armKamikaze(aircraft, x, y, z,
                            mission.getObservationDistance(), mission.getTargetId())) {
                        rejectKamikaze(player,
                                "Nose-dive rejected: this UAV profile has no supported destructive terminal path.");
                        return;
                    }
                    // Reusable and long-range airframes may only enter a
                    // destructive route through this operator-authenticated request.
                    long authorizationTick = player.world.getTotalWorldTime();
                    mission.authorizeDestructiveAction(authorizationTick);
                    KamikazePilotOverride.lock(aircraft.getUniqueID(), mission.getTargetId(),
                            mission.getMissionId());
                    mission.transitionTo(net.vas.rdp.uav.mission.MissionState.KAMIKADZE_TARGET,
                            authorizationTick);
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
                if (message.target == null || !message.target.matches(
                        "(?i)(mod=[A-Za-z0-9_.-]+|[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+)")) {
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
                if ((message.version != 3 && message.version != 4 && message.version != 5)
                        || (message.swarmEnabled && message.version < 4)
                        || message.unsupportedConfiguration
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
                if (message.hasCruiseAltitudeY
                        && (message.cruiseAltitudeY < 1
                        || message.cruiseAltitudeY > player.world.getHeight() - 2)) {
                    reject(player, "Mission definition rejected: cruise altitude is outside this world's safe bounds.");
                    return;
                }
                if (message.swarmEnabled) {
                    try {
                        if (message.swarmId == null || message.swarmId.trim().isEmpty()) {
                            throw new IllegalArgumentException("Select an owned swarm first");
                        }
                        MissionDefinition definition = message.toMissionDefinition();
                        MissionValidationResult validation = MissionValidationResult.validate(definition);
                        if (!validation.isValid()) {
                            throw new IllegalArgumentException(validation.getErrors().get(0));
                        }
                        int launched = RDPUavMod.swarms().launchMission(player.world,
                                player.getUniqueID(), java.util.UUID.fromString(message.swarmId), definition);
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                "Swarm mission assigned to " + launched + " UAV(s)."));
                        sendMissionData(player);
                        sendSwarmSnapshot(player, message.swarmId);
                    } catch (RuntimeException invalid) {
                        reject(player, "Swarm mission rejected: "
                                + (invalid.getMessage() == null ? "server validation failed" : invalid.getMessage()));
                        sendSwarmSnapshot(player, message.swarmId);
                    }
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
                        if (message.hasCruiseAltitudeY) {
                            mission.setCruiseAltitudeY(message.cruiseAltitudeY);
                            net.vas.rdp.uav.RdpUavDebug.event("MissionRuntime",
                                    "CRUISE_ALTITUDE_ACCEPTED",
                                    "mission", mission.getMissionId(),
                                    "aircraft", mission.getAircraftId(),
                                    "dimension", mission.getDimension(),
                                    "worldY", message.cruiseAltitudeY,
                                    "source", "MISSION_DEFINITION_PACKET_V5");
                        }
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
            if (message.hasCruiseAltitudeY) {
                mission.setCruiseAltitudeY(message.cruiseAltitudeY);
                net.vas.rdp.uav.RdpUavDebug.event("MissionRuntime",
                        "CRUISE_ALTITUDE_ACCEPTED", "mission", mission.getMissionId(),
                        "aircraft", mission.getAircraftId(), "dimension", mission.getDimension(),
                        "worldY", message.cruiseAltitudeY,
                        "source", "MISSION_DEFINITION_PACKET_V5");
            }
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
                if (message.hasCruiseAltitudeY
                        && (message.cruiseAltitudeY < 1
                        || message.cruiseAltitudeY > player.world.getHeight() - 2)) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            "Square strike rejected: cruise altitude is outside this world's safe bounds."));
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
                java.util.List<net.vas.rdp.uav.mission.RDPMission> missions =
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                .getRepository().byOwner(player.getUniqueID());
                for (int i = missions.size() - 1; i >= 0; i--) {
                    net.vas.rdp.uav.mission.RDPMission mission = missions.get(i);
                    if (!existingMissionIds.contains(mission.getMissionId())
                            && mission.getAircraftId().equals(aircraft.getUniqueID())
                            && !mission.isTerminal()) {
                        if (message.weapon != null && !message.weapon.trim().isEmpty()) {
                            mission.setGuidedWeapon(message.weapon);
                        }
                        if (message.hasCruiseAltitudeY) {
                            mission.setCruiseAltitudeY(message.cruiseAltitudeY);
                            net.vas.rdp.uav.RdpUavDebug.event("MissionRuntime",
                                    "CRUISE_ALTITUDE_ACCEPTED",
                                    "mission", mission.getMissionId(),
                                    "aircraft", mission.getAircraftId(),
                                    "dimension", mission.getDimension(),
                                    "worldY", message.cruiseAltitudeY,
                                    "source", "SQUARE_STRIKE_PACKET");
                        }
                        net.vas.rdp.uav.persistence.RDPMissionWorldData.get(player.world)
                                .markDirty();
                        break;
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
                if (message.isClearRequest()) {
                    clearTargetLock(player, aircraft);
                    return;
                }
                if (message.isInvalidLockRequest()) {
                    rejectTargetLock(player, aircraft, message.targetId, "INVALID_TARGET");
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
                    java.util.UUID targetId = java.util.UUID.fromString(message.targetId);
                    net.vas.rdp.uav.target.TargetResolver.Resolution resolution =
                            TARGET_RESOLVER.resolveLoaded(player.world, targetId);
                    net.minecraft.entity.Entity target = resolution.getEntity();
                    if (target == null) {
                        rejectTargetLock(player, aircraft, message.targetId,
                                resolution.getCode().name(),
                                target == null ? null : net.minecraft.entity.EntityList.getKey(target),
                                target == null ? null : (double) target.getDistance(aircraft),
                                false, "NOT_CHECKED");
                        return;
                    }
                    if (target == aircraft || target == player || !target.canBeCollidedWith()
                            || aircraft.getAcInfo() == null) {
                        rejectTargetLock(player, aircraft, message.targetId, "INVALID_TARGET",
                                net.minecraft.entity.EntityList.getKey(target),
                                (double) target.getDistance(aircraft), true, "NOT_CHECKED");
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
                        rejectTargetLock(player, aircraft, message.targetId, "TARGET_OUT_OF_RANGE",
                                net.minecraft.entity.EntityList.getKey(target),
                                (double) target.getDistance(aircraft), true, "ACCEPTED");
                        return;
                    }
                    if (!net.vas.rdp.uav.target.TargetPolicy.hasLineOfSight(
                            player.world, aircraft, target)) {
                        rejectTargetLock(player, aircraft, message.targetId,
                                "LINE_OF_SIGHT_BLOCKED",
                                net.minecraft.entity.EntityList.getKey(target),
                                (double) target.getDistance(aircraft), true, "BLOCKED");
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
                    rejectTargetLock(player, aircraft, message.targetId, "INVALID_TARGET",
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
            if ("TARGET_OUT_OF_RANGE".equals(reason)
                    && distance != null && Double.isFinite(lockRange)) {
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
                    message.getWeaponRows(), message.getWeaponNames(),
                    message.getPage(), message.getPageCount()));
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
