package net.vas.rdp.uav.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.persistence.RDPMissionWorldData;
import net.vas.rdp.uav.network.RdpUavNetwork;

import java.util.List;

public final class RdpUavCommand extends CommandBase {
    @Override
    public String getName() {
        return "rdpuav";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rdpuav mission gui|list|status <id>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayer)) {
            sender.sendMessage(new TextComponentString("RDP UAV commands require a player sender."));
            return;
        }
        if (args.length < 2 || !"mission".equalsIgnoreCase(args[0])) {
            sender.sendMessage(new TextComponentString(getUsage(sender)));
            return;
        }
        EntityPlayer player = (EntityPlayer) sender;
        if ("gui".equalsIgnoreCase(args[1])) {
            if (!(player instanceof EntityPlayerMP)) {
                sender.sendMessage(new TextComponentString("The mission GUI requires a server player."));
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

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
