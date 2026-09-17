package net.vas.rdp.uav.client;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.vas.rdp.uav.network.RdpUavNetwork;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public final class RdpUavClientKeys {
    private static long lastAssignmentTap;
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

    private RdpUavClientKeys() { }

    public static String label(KeyBinding binding) {
        if (binding == null || binding.getKeyCode() == Keyboard.KEY_NONE) return "Unbound";
        return Keyboard.getKeyName(binding.getKeyCode());
    }
    public static String missionControlKey() { return label(OPEN_MISSION_CONTROL); }
    public static String pointKey() { return label(POINT_TASK); }
    public static String reconKey() { return label(RECON_TASK); }
    public static String attackKey() { return label(AUTO_ATTACK_TASK); }
    public static String refreshKey() { return label(REFRESH_MISSIONS); }
    public static String authorizeKey() { return label(AUTHORIZE_SELECTED); }
    public static String abortKey() { return label(ABORT_SELECTED); }
    public static String kamikazeKey() { return label(KAMIKAZE_SELECTED); }
    public static String selfDestructKey() { return label(FIRE_SELF_DESTRUCT); }
    public static String entityLockKey() { return label(AI_TARGET_LOCK); }
    public static String areaLockKey() { return label(SIGHT_STABILIZATION); }
    public static String clearLockKey() { return label(CLEAR_TARGET_LOCK); }

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
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new RdpUavClientKeys());
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) return;
        if (AI_TARGET_LOCK.isPressed()) lockAiTarget(minecraft);
        if (SIGHT_STABILIZATION.isPressed()) stabilizeSight(minecraft);
        if (CLEAR_TARGET_LOCK.isPressed()) RdpUavNetwork.clearAiTargetLock();
        MCH_EntityAircraft aircraft = net.vas.rdp.uav.mission.UavControlAccess
                .findControlledAircraft(minecraft.player);
        if (aircraft == null) return;
        if (OPEN_MISSION_CONTROL.isPressed()) {
            minecraft.displayGuiScreen(new GuiMissionControl());
            RdpUavNetwork.requestMissionData();
        }
        if (POINT_TASK.isPressed()) RdpUavNetwork.createPointMission();
        if (RECON_TASK.isPressed()) GuiMissionControl.createReconFromField();
        if (AUTO_ATTACK_TASK.isPressed()) GuiMissionControl.createAutoAttackFromField();
        if (REFRESH_MISSIONS.isPressed()) GuiMissionControl.requestRefresh();
        if (AUTHORIZE_SELECTED.isPressed()) GuiMissionControl.authorizeSelected();
        if (ABORT_SELECTED.isPressed()) {
            long now = System.currentTimeMillis();
            if (now - lastAssignmentTap <= net.vas.rdp.uav.flight.KamikazeConfig.doubleTapWindowMillis) {
                GuiMissionControl.abortSelected();
                lastAssignmentTap = 0L;
            } else {
                lastAssignmentTap = now;
            }
        }
        if (KAMIKAZE_SELECTED.isPressed()) GuiMissionControl.armKamikazeSelected();
        if (FIRE_SELF_DESTRUCT.isPressed()) RdpUavNetwork.fireSelfDestruct();
        if (NEXT_CONTACT.isPressed()) RdpUavNetwork.cycleContact(true);
        if (PREVIOUS_CONTACT.isPressed()) RdpUavNetwork.cycleContact(false);
    }

    private void lockAiTarget(Minecraft minecraft) {
        if (minecraft.objectMouseOver == null || minecraft.objectMouseOver.entityHit == null) {
            RdpUavNetwork.lockAiTarget();
            return;
        }
        net.minecraft.entity.Entity target = minecraft.objectMouseOver.entityHit;
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
