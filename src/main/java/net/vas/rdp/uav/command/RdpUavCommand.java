package net.vas.rdp.uav.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.vas.rdp.uav.RDPUavMod;
import net.vas.rdp.uav.mission.AircraftRecord;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.mission.UavControlAccess;
import net.vas.rdp.uav.persistence.RDPMissionWorldData;
import net.vas.rdp.uav.network.RdpUavNetwork;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetResolver;
import net.vas.rdp.uav.target.TargetSpec;
import net.vas.rdp.uav.flight.WingManFlightAdapter;

import java.util.List;
import java.util.UUID;

public final class RdpUavCommand extends CommandBase {
    @Override
    public String getName() {
        return "rdpuav";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rdpuav authorize | mission create <namespace:id> [attack] | mission attack-point | mission gui|list|status <id>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayer)) {
            sender.sendMessage(new TextComponentString("RDP UAV commands require a player sender."));
            return;
        }
        if (args.length == 1 && "authorize".equalsIgnoreCase(args[0])) {
            authorize((EntityPlayer) sender);
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
        if ("create".equalsIgnoreCase(args[1])) {
            createMission(player, args);
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

    private void authorize(EntityPlayer player) {
        MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(player);
        if (!UavControlAccess.isOperator(aircraft, player)) {
            player.sendMessage(new TextComponentString(
                    "Authorization requires piloting a McHeli UAV. Mount the UAV and try again."));
            return;
        }
        UUID aircraftId = aircraft.getUniqueID();
        RDPUavMod.missions().registerAircraft(new AircraftRecord(aircraftId, player.getUniqueID(),
                aircraft.dimension, aircraft.getAcInfo().name, true, true));
        player.sendMessage(new TextComponentString("UAV authorized: " + aircraft.getAcInfo().name
                + " (" + aircraftId + ")"));
    }

    private void createMission(EntityPlayer player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(new TextComponentString(
                    "Usage: /rdpuav mission create <namespace:id> [attack]"));
            return;
        }

        MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(player);
        if (!UavControlAccess.isOperator(aircraft, player)) {
            player.sendMessage(new TextComponentString(
                    "You must be piloting an authorized McHeli UAV."));
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
        try {
            RDPMission mission = new RDPMission(UUID.randomUUID(), player.getUniqueID(), aircraftId,
                    aircraft.getAcInfo().name, attack ? MissionType.STRIKE : MissionType.RECONNAISSANCE,
                    TargetSpec.manual(args[2], TargetPriority.HIGH), attack, false, true,
                    256.0D, 64.0D, player.world.getTotalWorldTime());
            mission.setDimension(player.dimension);
            RDPUavMod.missions().create(player.world, mission);
            RDPMissionWorldData.get(player.world).markDirty();
            player.sendMessage(new TextComponentString("Mission created: "
                    + mission.getMissionId() + " (" + mission.getMissionType().name() + ")"));
        } catch (IllegalArgumentException failure) {
            player.sendMessage(new TextComponentString("Invalid target: " + failure.getMessage()));
        } catch (IllegalStateException failure) {
            player.sendMessage(new TextComponentString("Mission rejected: " + failure.getMessage()));
        }
    }

    private void createPointAttack(EntityPlayer player) {
        MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(player);
        if (!UavControlAccess.isOperator(aircraft, player)
                || !RDPUavMod.missions().ensureAuthorized(aircraft, player)
                || !RDPUavMod.missions().canAssignAircraft(aircraft.getUniqueID(), player.getUniqueID())) {
            player.sendMessage(new TextComponentString(
                    "You must pilot an authorized, available McHeli UAV."));
            return;
        }
        Vec3d start = player.getPositionEyes(1.0F);
        Vec3d end = start.add(player.getLookVec().scale(256.0D));
        RayTraceResult hit = player.world.rayTraceBlocks(start, end, false, true, false);
        if (hit == null || hit.hitVec == null) {
            player.sendMessage(new TextComponentString("No camera point was selected."));
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

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
