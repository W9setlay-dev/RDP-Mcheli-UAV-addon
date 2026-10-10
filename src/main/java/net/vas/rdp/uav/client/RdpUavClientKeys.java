package net.vas.rdp.uav.client;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.vas.rdp.uav.client.vision.VisionClientConfig;
import net.vas.rdp.uav.client.vision.VisionPresentationMode;
import net.vas.rdp.uav.network.RdpUavNetwork;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public final class RdpUavClientKeys {
    private static final KeyBinding OPEN_MISSION_CONTROL = new KeyBinding(
            "key.rdpuav.mission_control", Keyboard.KEY_Q, "key.categories.rdpuav");
    private static final KeyBinding POINT_TASK = new KeyBinding(
            "key.rdpuav.point_task", Keyboard.KEY_T, "key.categories.rdpuav");
    private static final KeyBinding RECON_TASK = new KeyBinding(
            "key.rdpuav.recon_task", Keyboard.KEY_O, "key.categories.rdpuav");
    private static final KeyBinding AUTO_ATTACK_TASK = new KeyBinding(
            "key.rdpuav.auto_attack", Keyboard.KEY_P, "key.categories.rdpuav");
    private static final KeyBinding REFRESH_MISSIONS = new KeyBinding(
            "key.rdpuav.refresh", Keyboard.KEY_K, "key.categories.rdpuav");
    private static final KeyBinding AUTHORIZE_SELECTED = new KeyBinding(
            "key.rdpuav.authorize", Keyboard.KEY_E, "key.categories.rdpuav");
    private static final KeyBinding ABORT_SELECTED = new KeyBinding(
            "key.rdpuav.abort", Keyboard.KEY_N, "key.categories.rdpuav");
    private static final KeyBinding KAMIKAZE_SELECTED = new KeyBinding(
            "key.rdpuav.kamikaze", Keyboard.KEY_1, "key.categories.rdpuav");
    private static final KeyBinding FIRE_SELF_DESTRUCT = new KeyBinding(
            "key.rdpuav.fire_self_destruct", Keyboard.KEY_2, "key.categories.rdpuav");
    private static final KeyBinding AI_TARGET_LOCK = new KeyBinding(
            "key.rdpuav.ai_target_lock", Keyboard.KEY_3, "key.categories.rdpuav");
    private static final KeyBinding SIGHT_STABILIZATION = new KeyBinding(
            "key.rdpuav.sight_stabilization", Keyboard.KEY_4, "key.categories.rdpuav");
    private static final KeyBinding CLEAR_TARGET_LOCK = new KeyBinding(
            "key.rdpuav.clear_target_lock", Keyboard.KEY_5, "key.categories.rdpuav");
    private static final KeyBinding NEXT_CONTACT = new KeyBinding(
            "key.rdpuav.next_contact", Keyboard.KEY_6, "key.categories.rdpuav");
    private static final KeyBinding PREVIOUS_CONTACT = new KeyBinding(
            "key.rdpuav.previous_contact", Keyboard.KEY_7, "key.categories.rdpuav");
    private static final KeyBinding FLIR_TOGGLE = new KeyBinding(
            "key.rdpuav.flir_toggle", Keyboard.KEY_F8, "key.categories.rdpuav");
    private static final KeyBinding CYCLE_VISION_PRESENTATION = new KeyBinding(
            "key.rdpuav.vision_presentation", Keyboard.KEY_F7, "key.categories.rdpuav");

    private RdpUavClientKeys() { }

    public static String label(KeyBinding binding) {
        if (binding == null || binding.getKeyCode() == Keyboard.KEY_NONE) return "Unbound";
        return Keyboard.getKeyName(binding.getKeyCode());
    }
    @SuppressWarnings("unused")
    public static String missionControlKey() { return label(OPEN_MISSION_CONTROL); }
    public static String pointKey() { return label(POINT_TASK); }
    public static String reconKey() { return label(RECON_TASK); }
    public static String attackKey() { return label(AUTO_ATTACK_TASK); }
    @SuppressWarnings("unused")
    public static String refreshKey() { return label(REFRESH_MISSIONS); }
    public static String authorizeKey() { return label(AUTHORIZE_SELECTED); }
    public static String abortKey() { return label(ABORT_SELECTED); }
    public static String kamikazeKey() { return label(KAMIKAZE_SELECTED); }
    @SuppressWarnings("unused")
    public static String selfDestructKey() { return label(FIRE_SELF_DESTRUCT); }
    public static String entityLockKey() { return label(AI_TARGET_LOCK); }
    public static String areaLockKey() { return label(SIGHT_STABILIZATION); }
    public static String clearLockKey() { return label(CLEAR_TARGET_LOCK); }
    public static String flirKey() { return label(FLIR_TOGGLE); }

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN_MISSION_CONTROL);
        ClientRegistry.registerKeyBinding(POINT_TASK);
        ClientRegistry.registerKeyBinding(RECON_TASK);
        ClientRegistry.registerKeyBinding(AUTO_ATTACK_TASK);
        ClientRegistry.registerKeyBinding(REFRESH_MISSIONS);
        ClientRegistry.registerKeyBinding(AUTHORIZE_SELECTED);
        ClientRegistry.registerKeyBinding(ABORT_SELECTED);
        ClientRegistry.registerKeyBinding(KAMIKAZE_SELECTED);
        ClientRegistry.registerKeyBinding(FIRE_SELF_DESTRUCT);
        ClientRegistry.registerKeyBinding(AI_TARGET_LOCK);
        ClientRegistry.registerKeyBinding(SIGHT_STABILIZATION);
        ClientRegistry.registerKeyBinding(CLEAR_TARGET_LOCK);
        ClientRegistry.registerKeyBinding(NEXT_CONTACT);
        ClientRegistry.registerKeyBinding(PREVIOUS_CONTACT);
        ClientRegistry.registerKeyBinding(FLIR_TOGGLE);
        ClientRegistry.registerKeyBinding(CYCLE_VISION_PRESENTATION);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new RdpUavClientKeys());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new OperatorTrimClientHandler());
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft minecraft = net.minecraftforge.fml.client.FMLClientHandler.instance().getClient();
        if (CYCLE_VISION_PRESENTATION.isPressed()
                && (minecraft.currentScreen == null
                || com.cleanroommc.modularui.screen.ModularScreen.getCurrent() != null)) {
            VisionPresentationMode mode = VisionClientConfig.presentationMode.next();
            VisionClientConfig.presentationMode = mode;
            if (minecraft.player != null) {
                minecraft.player.sendMessage(new TextComponentString(
                        "Pixel vision presentation: " + mode + "."));
            }
        }
        boolean flirPressed = FLIR_TOGGLE.isPressed();
        if (minecraft.currentScreen == null && flirPressed) {
            boolean enabled = RdpUavHudConfig.toggleFlir();
            if (minecraft.player != null) {
                minecraft.player.sendMessage(new TextComponentString(
                        "FLIR target display " + (enabled ? "enabled" : "disabled") + "."));
            }
        }
        if (minecraft.player == null) return;
        boolean lockPressed = Keyboard.getEventKeyState()
                && Keyboard.getEventKey() == AI_TARGET_LOCK.getKeyCode();
        if (lockPressed) lockAiTarget(minecraft);
        if (SIGHT_STABILIZATION.isPressed()) stabilizeSight(minecraft);
        if (CLEAR_TARGET_LOCK.isPressed()) RdpUavNetwork.clearAiTargetLock();
        if (ABORT_SELECTED.isPressed()) {
            GuiMissionControl.abortSelected();
        }
        if (POINT_TASK.isPressed()) submitPointTask(minecraft);
        MCH_EntityAircraft aircraft = net.vas.rdp.uav.mission.UavControlAccess
                .findControlledAircraft(minecraft.player);
        if (aircraft == null) return;
        if (OPEN_MISSION_CONTROL.isPressed()) {
            minecraft.displayGuiScreen(new GuiMissionControl());
            RdpUavNetwork.requestMissionData();
        }
        if (RECON_TASK.isPressed()) GuiMissionControl.createReconFromField();
        if (AUTO_ATTACK_TASK.isPressed()) GuiMissionControl.createAutoAttackFromField();
        if (REFRESH_MISSIONS.isPressed()) GuiMissionControl.requestRefresh();
        if (AUTHORIZE_SELECTED.isPressed()) GuiMissionControl.authorizeSelected();
        if (KAMIKAZE_SELECTED.isPressed()) GuiMissionControl.armKamikazeSelected();
        if (FIRE_SELF_DESTRUCT.isPressed()) RdpUavNetwork.fireSelfDestruct();
        if (NEXT_CONTACT.isPressed()) RdpUavNetwork.cycleContact(true);
        if (PREVIOUS_CONTACT.isPressed()) RdpUavNetwork.cycleContact(false);
    }

    /** Shared by the point-task key and the mission-control button. */
    public static void submitPointTask(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.world == null) return;
        MCH_EntityAircraft aircraft = net.vas.rdp.uav.mission.UavControlAccess
                .findControlledAircraft(minecraft.player);
        if (aircraft == null) {
            minecraft.player.sendMessage(new TextComponentString(
                    "Point task unavailable: no controlled McHeli aircraft."));
            return;
        }

        ClientAimRayTracer.AimTarget aim = ClientAimRayTracer.trace(
                minecraft, minecraft.getRenderPartialTicks(), minecraft.player, aircraft);
        if (aim.isBlock()) {
            RdpUavNetwork.submitPointAimBlock(aim.getBlockPos(), aim.getHitVec());
        } else if (aim.isEntity()) {
            Entity target = aim.getEntity();
            RdpUavNetwork.submitPointAimEntity(target.getUniqueID());
        } else {
            minecraft.player.sendMessage(new TextComponentString(
                    "Point task not sent: no block or entity on the camera ray within 500 blocks."));
        }
    }

    private void lockAiTarget(Minecraft minecraft) {
        MCH_EntityAircraft aircraft = net.vas.rdp.uav.mission.UavControlAccess
                .findControlledAircraft(minecraft.player);
        ClientAimRayTracer.AimTarget aim = ClientAimRayTracer.trace(
                minecraft, minecraft.getRenderPartialTicks(), minecraft.player, aircraft);
        if (!aim.isEntity()) {
            minecraft.player.sendMessage(new TextComponentString(aim.isBlock()
                    ? "Target lock not sent: the active camera ray hits a block."
                    : "Target lock not sent: no entity on the active camera ray."));
            return;
        }
        Entity target = aim.getEntity();
        GuiMissionControl.rememberCrosshairTarget(target);
        RdpUavNetwork.lockAiTarget(target.getUniqueID());
    }

    private void stabilizeSight(Minecraft minecraft) {
        if (minecraft.objectMouseOver != null
                && minecraft.objectMouseOver.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
            net.minecraft.util.math.BlockPos pos = minecraft.objectMouseOver.getBlockPos();
            RdpUavNetwork.stabilizeArea(pos.getX() + 0.5D,
                    pos.getY() + 1.0D, pos.getZ() + 0.5D);
        }
    }
}
