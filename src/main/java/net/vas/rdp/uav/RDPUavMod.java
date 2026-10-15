package net.vas.rdp.uav;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.vas.rdp.uav.mission.MissionManager;
import net.vas.rdp.uav.mission.MissionContext;
import net.vas.rdp.uav.mission.MissionOrchestrator;
import net.vas.rdp.uav.network.RdpUavNetwork;

/**
 * Forge entry point for the RDP UAV mission system.
 *
 * <p>The mod owns the authoritative mission registry, orbiting orchestration,
 * swarm reconciliation, and native control lease updates for each server tick.
 * Client GUIs are intentionally thin views of server-authoritative state.
 */
@Mod(modid = RDPUavMod.MOD_ID, name = "RDP UAV Missions", version = "0.1.0",
        dependencies = "required-after:mcheli@[1.5,);required-after:rdpcore")
public final class RDPUavMod {
    public static final String MOD_ID = "rdpuav";
    private static final MissionManager MISSIONS = new MissionManager();
    private static final MissionOrchestrator ORCHESTRATOR = new MissionOrchestrator(MISSIONS);
    private static final net.vas.rdp.uav.swarm.SwarmManager SWARMS =
            new net.vas.rdp.uav.swarm.SwarmManager(MISSIONS);
    private static final net.vas.rdp.uav.designation.DesignationManager DESIGNATIONS =
            new net.vas.rdp.uav.designation.DesignationManager();
    private static final java.util.Map<java.util.UUID, net.vas.rdp.uav.designation.DesignationView>
            CLIENT_DESIGNATIONS = new java.util.LinkedHashMap<>();
    private static final net.vas.rdp.uav.ai.AiTaskingService AI =
            new net.vas.rdp.uav.ai.AiTaskingService(MISSIONS);

    /**
     * Loads runtime config, vehicle profiles, and registry data before gameplay.
     *
     * @param event Forge pre-init event carrying the game configuration directory
     */
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
        net.vas.rdp.uav.designation.DesignationConfig.load(event.getModConfigurationDirectory());
    }

    /**
     * Registers network hooks and client/server listeners used by the mission system.
     *
     * @param event Forge init event for the current side
     */
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        RdpUavNetwork.init();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                new net.vas.rdp.uav.energy.UavEnergyHandler());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                new net.vas.rdp.uav.flight.KamikazeDetonationHandler());
        if (event.getSide() == Side.CLIENT) {
            java.io.File gameDir = net.minecraftforge.fml.client.FMLClientHandler
                    .instance().getClient().gameDir;
            net.vas.rdp.uav.client.vision.VisionClientConfig.load(
                    new java.io.File(gameDir, "config"));
            net.vas.rdp.uav.client.RdpUavClientKeys.register();
            net.vas.rdp.uav.client.RdpUavHudConfig.load(
                    net.minecraftforge.fml.client.FMLClientHandler.instance().getClient().gameDir);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    net.vas.rdp.uav.client.RdpUavTargetHud.instance());
            net.vas.rdp.uav.client.vision.VisionClientEvents visionEvents =
                    new net.vas.rdp.uav.client.vision.VisionClientEvents();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(visionEvents);
            net.minecraftforge.fml.common.FMLCommonHandler.instance().bus()
                    .register(visionEvents);
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ServerTickHandler());
    }

    /**
     * Registers the commands exposed to server operators and mission pilots.
     *
     * @param event the lifecycle event raised when the dedicated server starts
     */
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new net.vas.rdp.uav.command.RdpUavCommand());
    }

    /**
     * @return the shared server-side mission registry for the addon
     */
    public static MissionManager missions() {
        return MISSIONS;
    }

    /**
     * @return the designation manager used for queued operator targets
     */
    public static net.vas.rdp.uav.designation.DesignationManager designations() {
        return DESIGNATIONS;
    }

    /**
     * Client-side queue cache populated by the designation sync packet.
     */
    public static java.util.List<net.vas.rdp.uav.designation.DesignationView>
    clientDesignationQueue() {
        synchronized (CLIENT_DESIGNATIONS) {
            return new java.util.ArrayList<>(CLIENT_DESIGNATIONS.values());
        }
    }

    public static void replaceClientDesignationQueue(
            java.util.List<net.vas.rdp.uav.designation.DesignationView> designations) {
        synchronized (CLIENT_DESIGNATIONS) {
            CLIENT_DESIGNATIONS.clear();
            if (designations != null) {
                for (net.vas.rdp.uav.designation.DesignationView designation : designations) {
                    if (designation != null && designation.getDesignationId() != null) {
                        CLIENT_DESIGNATIONS.put(designation.getDesignationId(), designation);
                    }
                }
            }
        }
    }

    public static void removeClientDesignation(java.util.UUID designationId) {
        synchronized (CLIENT_DESIGNATIONS) {
            CLIENT_DESIGNATIONS.remove(designationId);
        }
    }

    /**
     * @return the AI tasking service used for autonomous mission planning
     */
    public static net.vas.rdp.uav.ai.AiTaskingService ai() {
        return AI;
    }

    /**
     * @return the swarm manager that enforces assignment and reconciliation
     */
    public static net.vas.rdp.uav.swarm.SwarmManager swarms() {
        return SWARMS;
    }

    /**
     * Resolves the last known contact snapshot for the supplied target UUID.
     *
     * @param targetId the authoritative entity identifier being queried
     * @return the contact snapshot if present, otherwise {@code null}
     */
    public static net.vas.rdp.uav.target.ContactSnapshot contact(java.util.UUID targetId) {
        return ORCHESTRATOR.contact(targetId);
    }

    /**
     * Cycles the active contact selection on a UAV by moving to the next or prior
     * valid target in the local observation set.
     *
     * @param world the world containing the aircraft
     * @param aircraft the aircraft whose contact list is being cycled
     * @param next {@code true} to move forward, {@code false} to move backward
     * @return {@code true} when the selection changed
     */
    public static boolean cycleContact(net.minecraft.world.World world,
                                       com.norwood.mcheli.aircraft.MCH_EntityAircraft aircraft,
                                       boolean next) {
        return ORCHESTRATOR.cycleContact(world, aircraft, next);
    }

    /**
     * Cycles the contact selection while a specific operator is bound to the
     * aircraft; this supports the player-driven mission UI flow.
     *
     * @param world the active game world
     * @param aircraft the controlled aircraft
     * @param operator the player making the request
     * @param next {@code true} to advance the selection, otherwise restore the previous one
     */
    public static void cycleContact(World world,
                                    MCH_EntityAircraft aircraft,
                                    EntityPlayer operator,
                                    boolean next) {
        ORCHESTRATOR.cycleContact(world, aircraft, operator, next);
    }

    /**
     * Clears the active contact on the supplied aircraft and lets the mission
     * planner recover to a safe route state.
     *
     * @param world the world containing the aircraft
     * @param aircraft the aircraft whose acquisition state should be cleared
     */
    public static void clearContact(World world,
                                    MCH_EntityAircraft aircraft) {
        ORCHESTRATOR.clearContact(world, aircraft);
    }

    private static final class ServerTickHandler {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onWorldTick(TickEvent.WorldTickEvent event) {
            if (event.world.isRemote) {
                return;
            }
            // Native attitude/throttle writes are intentionally staged at START so they
            // run before mission orchestration and route validation on the same server tick.
            if (event.phase == TickEvent.Phase.START) {
                ORCHESTRATOR.applyNativeControls(event.world);
                return;
            }
            if (event.phase != TickEvent.Phase.END) return;
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
                            RdpUavNetwork.syncDesignationQueue((EntityPlayerMP) player);
                        }
                    }
                }
        }
    }
}
