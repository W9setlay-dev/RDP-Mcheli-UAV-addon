package net.vas.rdp.uav;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.vas.rdp.uav.mission.MissionManager;
import net.vas.rdp.uav.network.RdpUavNetwork;

@Mod(modid = RDPUavMod.MOD_ID, name = "RDP UAV Missions", version = "0.1.0",
        dependencies = "required-after:mcheli@[1.5.1,);required-after:rdpcore")
public final class RDPUavMod {
    public static final String MOD_ID = "rdpuav";
    private static final MissionManager MISSIONS = new MissionManager();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        RdpUavNetwork.init();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ServerTickHandler());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new net.vas.rdp.uav.command.RdpUavCommand());
    }

    public static MissionManager missions() {
        return MISSIONS;
    }

    private static final class ServerTickHandler {
        @SubscribeEvent
        public void onWorldTick(TickEvent.WorldTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
                return;
            }
            // Mission execution is intentionally scheduled separately from every-tick scanning.
            // A queued mission is retained until a registered MissionRunner
            // accepts it; no tick path may discard work silently.
        }
    }
}
