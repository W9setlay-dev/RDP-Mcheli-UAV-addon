package net.vas.rdp.uav.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.vas.rdp.uav.RDPUavMod;
import net.vas.rdp.uav.mission.AircraftRecord;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.mission.UavControlAccess;
import net.vas.rdp.uav.mission.MissionLimits;
import net.vas.rdp.uav.ai.AiDecision;
import net.vas.rdp.uav.ai.AiResource;
import net.vas.rdp.uav.ai.AiTaskRequest;
import net.vas.rdp.uav.ai.AutonomyLevel;
import net.vas.rdp.uav.persistence.RDPMissionWorldData;
import net.vas.rdp.uav.network.RdpUavNetwork;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetResolver;
import net.vas.rdp.uav.target.TargetSpec;
import net.vas.rdp.uav.flight.WingManFlightAdapter;
import net.vas.rdp.uav.profile.UavVehicleProfiles;
import net.vas.rdp.uav.RdpUavDebug;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class RdpUavCommand extends CommandBase {
    @Override
    @Nonnull
    public String getName() {
        return "rdpuav";
    }

    @Override
    @Nonnull
    public String getUsage(ICommandSender sender) {
        return "/rdpuav authorize | debug on|off|status|reload | swarm create <name> | swarm add <id> | swarm remove <id> <aircraft> | swarm pause|resume|disband <id> | swarm reform <id> <formation> | mission return | mission ...";
    }

    @Override
    @Nonnull
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                           String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "authorize", "debug", "mission", "swarm");
        }
        if (args.length == 2 && "swarm".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "create", "add", "remove",
                    "pause", "resume", "reform", "disband", "status");
        }
        if (args.length == 2 && "debug".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "on", "off", "status", "reload");
        }
        if (args.length == 2 && "mission".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "create", "ai", "attack-point",
                    "square", "return", "gui", "list", "status");
        }
        return java.util.Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[0])) {
            if (!sender.canUseCommand(2, getName())) {
                sender.sendMessage(new TextComponentString("Debug logging requires operator permission."));
                return;
            }
            handleDebug(sender, args[1]);
            return;
        }
        if (!(sender instanceof EntityPlayer)) {
            sender.sendMessage(new TextComponentString("RDP UAV commands require a player sender."));
            return;
        }
        if (args.length == 1 && "authorize".equalsIgnoreCase(args[0])) {
            authorize((EntityPlayer) sender);
            return;
        }
        if ("swarm".equalsIgnoreCase(args[0])) {
            handleSwarm((EntityPlayer) sender, args);
            return;
        }
        if (args.length < 2 || !"mission".equalsIgnoreCase(args[0])) {
            sender.sendMessage(new TextComponentString(getUsage(sender)));
            return;
        }

        EntityPlayer player = (EntityPlayer) sender;
        if ("attack-point".equalsIgnoreCase(args[1])) {
            createPointAttack(player);
            return;
        }
        if ("square".equalsIgnoreCase(args[1])) {
            createSquareStrike(player, args);
            return;
        }
        if ("return".equalsIgnoreCase(args[1])) {
            returnToBase(player);
            return;
        }
        if ("create".equalsIgnoreCase(args[1])) {
            createMission(player, args);
            return;
        }
        if ("ai".equalsIgnoreCase(args[1])) {
            createAiMission(player, args);
            return;
        }
        if ("gui".equalsIgnoreCase(args[1])) {
            if (!(player instanceof EntityPlayerMP)) {
                sender.sendMessage(new TextComponentString("The mission GUI requires a server player."));
                return;
            }
            MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(player);
            if (!UavControlAccess.isOperator(aircraft, player)) {
                sender.sendMessage(new TextComponentString(
                        "Open the mission GUI while seated as the UAV pilot."));
                return;
            }
            RdpUavNetwork.openMissionControl((EntityPlayerMP) player);
            return;
        }
        List<RDPMission> missions = RDPMissionWorldData.get(player.world).getRepository()
                .byOwner(player.getUniqueID());
        if ("list".equalsIgnoreCase(args[1])) {
            sender.sendMessage(new TextComponentString("RDP UAV missions: " + missions.size()));
            for (RDPMission mission : missions) {
                sender.sendMessage(new TextComponentString(mission.getMissionId() + " "
                        + mission.getState().name() + " " + mission.getAircraftType()));
            }
            return;
        }
        if ("status".equalsIgnoreCase(args[1]) && args.length >= 3) {
            for (RDPMission mission : missions) {
                if (mission.getMissionId().toString().equals(args[2])) {
                    sender.sendMessage(new TextComponentString(mission.getMissionId() + " "
                            + mission.getState().name()));
                    return;
                }
            }
            sender.sendMessage(new TextComponentString("MISSION_NOT_FOUND"));
            return;
        }
        sender.sendMessage(new TextComponentString(getUsage(sender)));
    }

    private void returnToBase(EntityPlayer player) {
        MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(player);
        if (!UavControlAccess.isOperator(aircraft, player)) {
            player.sendMessage(new TextComponentString("You must control the aircraft to request return-to-base."));
            return;
        }
        RDPMission mission = null;
        for (RDPMission candidate : RDPMissionWorldData.get(player.world).getRepository()
                .byOwner(player.getUniqueID())) {
            if (candidate.getAircraftId().equals(aircraft.getUniqueID()) && !candidate.isTerminal()) {
                mission = candidate;
                break;
            }
        }
        if (mission == null) {
            player.sendMessage(new TextComponentString("No active mission is assigned to this aircraft."));
            return;
        }
        if (mission.getState() == net.vas.rdp.uav.mission.MissionState.PLANNED
                || mission.getState() == net.vas.rdp.uav.mission.MissionState.DISPATCHED) {
            player.sendMessage(new TextComponentString("Mission has not entered flight; return-to-base is unavailable."));
            return;
        }
        if (!new WingManFlightAdapter().requestReturn(aircraft, "RDP_PLAYER_RTB")) {
            player.sendMessage(new TextComponentString("Native McHeli return-to-base was rejected."));
            return;
        }
        mission.transitionTo(net.vas.rdp.uav.mission.MissionState.RETURNING,
                player.world.getTotalWorldTime());
        RDPMissionWorldData.get(player.world).markDirty();
        player.sendMessage(new TextComponentString("Return-to-base requested for mission "
                + mission.getMissionId() + "."));
    }

    private void handleSwarm(EntityPlayer player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(new TextComponentString(
                    "Usage: /rdpuav swarm create <name> | add|remove|pause|resume|reform|disband|status <id>"));
            return;
        }
        String action = args[1];
        try {
            if ("create".equalsIgnoreCase(action)) {
                net.vas.rdp.uav.swarm.SwarmRecord record =
                        RDPUavMod.swarms().create(player.world, player.getUniqueID(), args[2]);
                player.sendMessage(new TextComponentString("Swarm created: " + record.getSwarmId()));
                return;
            }
            UUID swarmId = UUID.fromString(args[2]);
            if ("add".equalsIgnoreCase(action)) {
                MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(player);
                if (!UavControlAccess.isOperator(aircraft, player) || aircraft.getAcInfo() == null
                        || !RDPUavMod.missions().ensureAuthorized(aircraft, player)) {
                    throw new IllegalArgumentException("You must control an authorized UAV.");
                }
                net.vas.rdp.uav.profile.UavVehicleProfile profile =
                        net.vas.rdp.uav.profile.UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name);
                if (profile.getExecutionProfile() == net.vas.rdp.uav.profile.UavExecutionProfile.UNKNOWN) {
                    throw new IllegalArgumentException("UAV definition has no configured profile.");
                }
                boolean added = RDPUavMod.swarms().addMember(player.world, player.getUniqueID(), swarmId,
                        new net.vas.rdp.uav.swarm.SwarmMember(aircraft.getUniqueID(),
                                profile.getExecutionProfile().name(), profile.supportsLongRangeLock(),
                                aircraft.getWeapons() != null));
                if (!added) throw new IllegalArgumentException("Member rejected or swarm full.");
                player.sendMessage(new TextComponentString("Swarm member added: " + aircraft.getUniqueID()));
                return;
            }
            if ("remove".equalsIgnoreCase(action)) {
                if (args.length < 4 || !RDPUavMod.swarms().removeMember(player.world, player.getUniqueID(),
                        swarmId, UUID.fromString(args[3]))) throw new IllegalArgumentException("Member not found.");
                player.sendMessage(new TextComponentString("Swarm member removed."));
                return;
            }
            if ("pause".equalsIgnoreCase(action) || "resume".equalsIgnoreCase(action)
                    || "disband".equalsIgnoreCase(action)) {
                net.vas.rdp.uav.swarm.SwarmState state = "pause".equalsIgnoreCase(action)
                        ? net.vas.rdp.uav.swarm.SwarmState.AWAITING_OPERATOR
                        : "resume".equalsIgnoreCase(action)
                        ? net.vas.rdp.uav.swarm.SwarmState.READY
                        : net.vas.rdp.uav.swarm.SwarmState.DISBANDED;
                if (!RDPUavMod.swarms().setState(player.world, player.getUniqueID(), swarmId, state)) {
                    throw new IllegalArgumentException("Swarm not found or unauthorized.");
                }
                player.sendMessage(new TextComponentString("Swarm state: " + state.name()));
                return;
            }
            if ("reform".equalsIgnoreCase(action)) {
                if (args.length < 4) throw new IllegalArgumentException("Formation is required.");
                net.vas.rdp.uav.swarm.SwarmFormation formation =
                        net.vas.rdp.uav.swarm.SwarmFormation.valueOf(args[3].toUpperCase());
                if (!RDPUavMod.swarms().reform(player.world, player.getUniqueID(), swarmId, formation)) {
                    throw new IllegalArgumentException("Swarm not found or unauthorized.");
                }
                player.sendMessage(new TextComponentString("Swarm reformed: " + formation.name()));
                return;
            }
            net.vas.rdp.uav.swarm.SwarmRecord record = RDPUavMod.swarms().find(player.world, swarmId);
            if (!RDPUavMod.swarms().owns(record, player.getUniqueID())) {
                throw new IllegalArgumentException("Swarm not found or unauthorized.");
            }
            player.sendMessage(new TextComponentString("Swarm " + record.getName() + " state="
                    + record.getState() + " members=" + record.getMembers().size()
                    + " leader=" + record.referenceMember()));
        } catch (RuntimeException failure) {
            player.sendMessage(new TextComponentString("Swarm command rejected: "
                    + (failure.getMessage() == null ? "invalid request" : failure.getMessage())));
        }
    }

    private void handleDebug(ICommandSender sender, String action) {
        if ("on".equalsIgnoreCase(action)) {
            RdpUavDebug.setEnabled(true);
        } else if ("off".equalsIgnoreCase(action)) {
            RdpUavDebug.setEnabled(false);
        } else if ("reload".equalsIgnoreCase(action)) {
            RdpUavDebug.reload();
        } else if (!"status".equalsIgnoreCase(action)) {
            sender.sendMessage(new TextComponentString(
                    "Usage: /rdpuav debug on|off|status|reload"));
            return;
        }
        sender.sendMessage(new TextComponentString("RDP UAV " + RdpUavDebug.status()));
    }

    private void authorize(EntityPlayer player) {
        MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(player);
        if (!UavControlAccess.isOperator(aircraft, player)) {
            player.sendMessage(new TextComponentString(
                    "Aircraft cannot be found or controlled. Pilot the McHeli aircraft or connect to its UAV station, then try again."));
            return;
        }
        UUID aircraftId = aircraft.getUniqueID();
        if (aircraft.getAcInfo() == null) {
            player.sendMessage(new TextComponentString("UAV metadata is unavailable; authorization rejected."));
            return;
        }
        RDPUavMod.missions().registerAircraft(new AircraftRecord(aircraftId, player.getUniqueID(),
                aircraft.dimension, aircraft.getAcInfo().name, true, true));
        player.sendMessage(new TextComponentString("UAV authorized: " + aircraft.getAcInfo().name
                + " (" + aircraftId + ")"));
    }

    private void createMission(EntityPlayer player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(new TextComponentString(
                    "Usage: /rdpuav mission create <namespace:id|mod=namespace> [attack] [low|normal|high|critical]"));
            return;
        }

        MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(player);
        if (!UavControlAccess.isOperator(aircraft, player)) {
            player.sendMessage(new TextComponentString(
                    "Aircraft cannot be found or controlled. Pilot the McHeli aircraft or connect to its UAV station."));
            return;
        }
        UUID aircraftId = aircraft.getUniqueID();
        if (!RDPUavMod.missions().ensureAuthorized(aircraft, player)
                || !RDPUavMod.missions().canAssignAircraft(aircraftId, player.getUniqueID())) {
            player.sendMessage(new TextComponentString(
                    "UAV is not authorized for this player or is already assigned."));
            return;
        }
        boolean attack = args.length >= 4 && "attack".equalsIgnoreCase(args[3]);
        TargetPriority priority = parsePriority(args.length >= 5 ? args[4] : null,
                attack ? TargetPriority.HIGH : TargetPriority.NORMAL);
        try {
            if (aircraft.getAcInfo() == null) {
                player.sendMessage(new TextComponentString("UAV metadata is unavailable; mission rejected."));
                return;
            }
            RDPMission mission = new RDPMission(UUID.randomUUID(), player.getUniqueID(), aircraftId,
                    aircraft.getAcInfo().name, attack ? MissionType.STRIKE : MissionType.RECONNAISSANCE,
                    parseTarget(args[2], priority), attack, false, true,
                    MissionLimits.MAX_SQUARE_STRIKE_DISTANCE, 64.0D,
                    player.world.getTotalWorldTime());
            mission.setDimension(player.dimension);
            RDPUavMod.missions().create(player.world, mission);
            RDPMissionWorldData.get(player.world).markDirty();
            RdpUavDebug.debug("Command", "mission created id={} owner={} aircraft={} target={} attack={}",
                    mission.getMissionId(), player.getUniqueID(), aircraft.getUniqueID(), args[2], attack);
            player.sendMessage(new TextComponentString("Mission created: "
                    + mission.getMissionId() + " (" + mission.getMissionType().name() + ")"));
        } catch (IllegalArgumentException failure) {
            player.sendMessage(new TextComponentString("Invalid target: " + failure.getMessage()));
        } catch (IllegalStateException failure) {
            player.sendMessage(new TextComponentString("Mission rejected: " + failure.getMessage()));
        }
    }

    private void createAiMission(EntityPlayer player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(new TextComponentString(
                    "Usage: /rdpuav mission ai <namespace:id|mod=namespace> [attack]"));
            return;
        }
        MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(player);
        if (!UavControlAccess.isOperator(aircraft, player)
                || !RDPUavMod.missions().ensureAuthorized(aircraft, player)
                || !RDPUavMod.missions().canAssignAircraft(aircraft.getUniqueID(), player.getUniqueID())) {
            player.sendMessage(new TextComponentString(
                    "AI requires an authorized, available McHeli UAV under your control."));
            return;
        }
        boolean attack = args.length >= 4 && "attack".equalsIgnoreCase(args[3]);
        try {
            TargetSpec target = parseTarget(args[2],
                    attack ? TargetPriority.HIGH : TargetPriority.NORMAL);
            AiTaskRequest request = new AiTaskRequest(UUID.randomUUID(), player.getUniqueID(),
                    attack ? MissionType.STRIKE : MissionType.RECONNAISSANCE, target,
                    target.getPriority(), attack, AutonomyLevel.AUTONOMOUS);
            AircraftRecord record = RDPUavMod.missions().findAircraft(aircraft.getUniqueID());
            WingManFlightAdapter adapter = new WingManFlightAdapter();
            AiDecision decision = RDPUavMod.ai().assess(request,
                    java.util.Collections.singletonList(new AiResource(record, 0.0D,
                            adapter.hasAttackAmmunition(aircraft)
                                    || adapter.hasSelfDestructWeaponWithAmmo(aircraft))));
            if (decision.getKind() != AiDecision.Kind.RECOMMEND) {
                player.sendMessage(new TextComponentString("AI mission rejected: "
                        + decision.getReason()));
                return;
            }

            RDPMission mission = RDPUavMod.ai().executeAutonomous(player.world, request,
                    decision, player.world.getTotalWorldTime());
            RDPMissionWorldData.get(player.world).markDirty();
            player.sendMessage(new TextComponentString("AI mission accepted: "
                    + mission.getMissionId() + " (" + decision.getIntent().name() + ")"));
        } catch (RuntimeException failure) {
            player.sendMessage(new TextComponentString("AI mission rejected: "
                    + (failure.getMessage() == null ? "invalid request" : failure.getMessage())));
        }
    }

    private TargetSpec parseTarget(String value, TargetPriority priority) {
        if (value != null && value.regionMatches(true, 0, "mod=", 0, 4)) {
            return TargetSpec.modNamespace(value.substring(4), priority);
        }
        return TargetSpec.manual(value, priority);
    }

    private TargetPriority parsePriority(String value, TargetPriority fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            return TargetPriority.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("priority must be low, normal, high, or critical");
        }
    }

    private void createPointAttack(EntityPlayer player) {
        MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(player);
        if (!UavControlAccess.isOperator(aircraft, player)
                || !RDPUavMod.missions().ensureAuthorized(aircraft, player)
                || !RDPUavMod.missions().canAssignAircraft(aircraft.getUniqueID(), player.getUniqueID())) {
            player.sendMessage(new TextComponentString(
                    "You must pilot an authorized, available McHeli aircraft."));
            return;
        }

        Vec3d start = player.getPositionEyes(1.0F);
        Vec3d end = start.add(player.getLookVec().scale(256.0D));
        RayTraceResult hit = player.world.rayTraceBlocks(start, end, false, true, false);
        if (hit == null || hit.hitVec == null) {
            player.sendMessage(new TextComponentString("No camera point was selected."));
            return;
        }
        if (aircraft.getAcInfo() == null) {
            player.sendMessage(new TextComponentString("UAV metadata is unavailable; mission rejected."));
            return;
        }
        long tick = player.world.getTotalWorldTime();
        RDPMission mission = new RDPMission(UUID.randomUUID(), player.getUniqueID(),
                aircraft.getUniqueID(), aircraft.getAcInfo().name, MissionType.STRIKE,
                TargetSpec.point(hit.hitVec.x, hit.hitVec.y, hit.hitVec.z, TargetPriority.HIGH),
                true, false, false, 256.0D, 32.0D, tick);
        mission.setDimension(player.dimension);
        try {
            RDPUavMod.missions().create(player.world, mission);
            RDPMissionWorldData.get(player.world).markDirty();
            RdpUavDebug.debug("Command", "point mission created id={} owner={} aircraft={} target=({}, {}, {})",
                    mission.getMissionId(), player.getUniqueID(), aircraft.getUniqueID(),
                    hit.hitVec.x, hit.hitVec.y, hit.hitVec.z);
            player.sendMessage(new TextComponentString("Point attack mission created: "
                    + mission.getMissionId()));
        } catch (RuntimeException failure) {
            RDPUavMod.missions().release(mission);
            if (mission.getState() != net.vas.rdp.uav.mission.MissionState.FAILED) {
                mission.fail(failure.getMessage() == null ? "POINT_ATTACK_FAILED" : failure.getMessage(), tick);
            }
            RDPMissionWorldData.get(player.world).markDirty();
            player.sendMessage(new TextComponentString("Point attack rejected: " + failure.getMessage()));
        }
    }

    private void createSquareStrike(EntityPlayer player, String[] args) {
        if (args.length < 5) {
            player.sendMessage(new TextComponentString(
                    "Usage: /rdpuav mission square <x> <y> <z> [size]"));
            return;
        }
        MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(player);
        if (!UavControlAccess.isOperator(aircraft, player)
                || !RDPUavMod.missions().ensureAuthorized(aircraft, player)
                || !RDPUavMod.missions().canAssignAircraft(aircraft.getUniqueID(), player.getUniqueID())) {
            player.sendMessage(new TextComponentString(
                    "You must pilot an authorized, available McHeli UAV."));
            return;
        }
        try {
            assert aircraft.getAcInfo() != null;
            net.vas.rdp.uav.profile.UavVehicleProfile vehicleProfile =
                    UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name);
            if (aircraft.isUAV() && vehicleProfile.getExecutionProfile()
                    == net.vas.rdp.uav.profile.UavExecutionProfile.UNKNOWN) {
                throw new IllegalArgumentException("UAV definition has no configured execution profile");
            }
            double x = Double.parseDouble(args[2]);
            double y = Double.parseDouble(args[3]);
            double z = Double.parseDouble(args[4]);
            double size = args.length >= 6 ? Double.parseDouble(args[5]) : 64.0D;
            if (aircraft.getAcInfo() == null) {
                throw new IllegalArgumentException("UAV metadata is unavailable");
            }
            double dx = aircraft.posX - x;
            double dy = aircraft.posY - y;
            double dz = aircraft.posZ - z;
            if (dx * dx + dy * dy + dz * dz
                    > MissionLimits.MAX_SQUARE_STRIKE_DISTANCE
                    * MissionLimits.MAX_SQUARE_STRIKE_DISTANCE) {
                throw new IllegalArgumentException("target must be within 5000 blocks of the UAV");
            }
            if (size < MissionLimits.MIN_SQUARE_SIZE || size > MissionLimits.MAX_SQUARE_SIZE) {
                throw new IllegalArgumentException("size must be 16..512");
            }
            RDPMission mission = new RDPMission(UUID.randomUUID(), player.getUniqueID(),
                    aircraft.getUniqueID(), aircraft.getAcInfo().name, MissionType.SQUARE_STRIKE,
                    TargetSpec.point(x, y, z, TargetPriority.HIGH), true, false, false,
                    Math.max(256.0D, size * 2.0D),
                    Math.min(64.0D, Math.max(16.0D, size / 4.0D)),
                    player.world.getTotalWorldTime());
            mission.setDimension(player.dimension);
            RDPUavMod.missions().create(player.world, mission);
            RDPMissionWorldData.get(player.world).markDirty();
            player.sendMessage(new TextComponentString("Square strike mission created: "
                    + mission.getMissionId()));
        } catch (RuntimeException failure) {
            player.sendMessage(new TextComponentString("Square strike rejected: "
                    + (failure.getMessage() == null ? "invalid parameters" : failure.getMessage())));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
