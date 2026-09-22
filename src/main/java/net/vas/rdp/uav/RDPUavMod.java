package net.vas.rdp.uav;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.vas.rdp.uav.mission.MissionManager;
import net.vas.rdp.uav.mission.MissionContext;
import net.vas.rdp.uav.mission.MissionOrchestrator;
import net.vas.rdp.uav.network.RdpUavNetwork;

@Mod(modid = RDPUavMod.MOD_ID, name = "RDP UAV Missions", version = "0.1.0",
        dependencies = "required-after:mcheli@[1.5,);required-after:rdpcore")
public final class RDPUavMod {
    public static final String MOD_ID = "rdpuav";
    private static final MissionManager MISSIONS = new MissionManager();
    private static final MissionOrchestrator ORCHESTRATOR = new MissionOrchestrator(MISSIONS);
    private static final net.vas.rdp.uav.swarm.SwarmManager SWARMS =
            new net.vas.rdp.uav.swarm.SwarmManager(MISSIONS);
    private static final net.vas.rdp.uav.ai.AiTaskingService AI =
            new net.vas.rdp.uav.ai.AiTaskingService(MISSIONS);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        RdpUavDebug.load(event.getModConfigurationDirectory());
        net.vas.rdp.uav.energy.UavEnergyConfig.load(event.getModConfigurationDirectory());
        net.vas.rdp.uav.profile.UavVehicleProfiles.load(event.getModConfigurationDirectory());
        net.vas.rdp.uav.swarm.SwarmConfig.load(event.getModConfigurationDirectory());
        net.vas.rdp.uav.target.TargetPolicy.load(event.getModConfigurationDirectory());
        net.vas.rdp.uav.flight.KamikazeConfig.load(event.getModConfigurationDirectory());
        net.vas.rdp.uav.flight.SquareStrikeConfig.load(event.getModConfigurationDirectory());
        net.vas.rdp.uav.weapon.GuidedWeaponRegistry.load(event.getModConfigurationDirectory());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        RdpUavNetwork.init();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                new net.vas.rdp.uav.energy.UavEnergyHandler());
        if (event.getSide() == Side.CLIENT) {
            net.vas.rdp.uav.client.RdpUavClientKeys.register();
            net.vas.rdp.uav.client.RdpUavHudConfig.load(
                    net.minecraftforge.fml.client.FMLClientHandler.instance().getClient().gameDir);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    net.vas.rdp.uav.client.RdpUavTargetHud.instance());
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ServerTickHandler());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new net.vas.rdp.uav.command.RdpUavCommand());
    }

    public static MissionManager missions() {
        return MISSIONS;
    }

    public static net.vas.rdp.uav.ai.AiTaskingService ai() {
        return AI;
    }

    public static net.vas.rdp.uav.swarm.SwarmManager swarms() {
        return SWARMS;
    }

    public static net.vas.rdp.uav.target.ContactSnapshot contact(java.util.UUID targetId) {
        return ORCHESTRATOR.contact(targetId);
    }

    public static boolean cycleContact(net.minecraft.world.World world,
                                       com.norwood.mcheli.aircraft.MCH_EntityAircraft aircraft,
                                       boolean next) {
        return ORCHESTRATOR.cycleContact(world, aircraft, next);
    }

    public static boolean cycleContact(net.minecraft.world.World world,
                                       com.norwood.mcheli.aircraft.MCH_EntityAircraft aircraft,
                                       net.minecraft.entity.player.EntityPlayer operator,
                                       boolean next) {
        return ORCHESTRATOR.cycleContact(world, aircraft, operator, next);
    }

    public static boolean clearContact(net.minecraft.world.World world,
                                       com.norwood.mcheli.aircraft.MCH_EntityAircraft aircraft) {
        return ORCHESTRATOR.clearContact(world, aircraft);
    }

    private static final class ServerTickHandler {
        @SubscribeEvent
        public void onWorldTick(TickEvent.WorldTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
                return;
            }
            ORCHESTRATOR.tick(new MissionContext(event.world));
            net.vas.rdp.uav.weapon.MissileGuidanceController.tick(event.world);
            if (event.world.getTotalWorldTime() % net.vas.rdp.uav.swarm.SwarmConfig.updateIntervalTicks == 0L) {
                SWARMS.reconcile(event.world);
            }
                if (event.world.getTotalWorldTime() % 5L == 0L
                        && event.world.getMinecraftServer() != null) {
                    for (EntityPlayer player : event.world.playerEntities) {
                        if (player instanceof EntityPlayerMP) {
                            RdpUavNetwork.syncTargetTelemetry((EntityPlayerMP) player);
                            RdpUavNetwork.syncSwarmTelemetry((EntityPlayerMP) player);
                        }
                    }
                }
        }
    }
}
