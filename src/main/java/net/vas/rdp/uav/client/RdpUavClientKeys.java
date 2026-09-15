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
    private static final KeyBinding OPEN_MISSION_CONTROL = new KeyBinding(
            "key.rdpuav.mission_control", Keyboard.KEY_U, "key.categories.rdpuav");
    private static final KeyBinding POINT_TASK = new KeyBinding(
            "key.rdpuav.point_task", Keyboard.KEY_P, "key.categories.rdpuav");
    private static final KeyBinding RECON_TASK = new KeyBinding(
            "key.rdpuav.recon_task", Keyboard.KEY_R, "key.categories.rdpuav");
    private static final KeyBinding AUTO_ATTACK_TASK = new KeyBinding(
            "key.rdpuav.auto_attack", Keyboard.KEY_K, "key.categories.rdpuav");
    private static final KeyBinding REFRESH_MISSIONS = new KeyBinding(
            "key.rdpuav.refresh", Keyboard.KEY_F, "key.categories.rdpuav");
    private static final KeyBinding AUTHORIZE_SELECTED = new KeyBinding(
            "key.rdpuav.authorize", Keyboard.KEY_A, "key.categories.rdpuav");
    private static final KeyBinding ABORT_SELECTED = new KeyBinding(
            "key.rdpuav.abort", Keyboard.KEY_X, "key.categories.rdpuav");
    private static final KeyBinding KAMIKAZE_SELECTED = new KeyBinding(
            "key.rdpuav.kamikaze", Keyboard.KEY_N, "key.categories.rdpuav");
    private static final KeyBinding FIRE_SELF_DESTRUCT = new KeyBinding(
            "key.rdpuav.fire_self_destruct", Keyboard.KEY_H, "key.categories.rdpuav");
    private static final KeyBinding AI_TARGET_LOCK = new KeyBinding(
            "key.rdpuav.ai_target_lock", Keyboard.KEY_L, "key.categories.rdpuav");
    private static final KeyBinding CLEAR_TARGET_LOCK = new KeyBinding(
            "key.rdpuav.clear_target_lock", Keyboard.KEY_J, "key.categories.rdpuav");

    private RdpUavClientKeys() { }

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
        ClientRegistry.registerKeyBinding(CLEAR_TARGET_LOCK);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new RdpUavClientKeys());
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) return;
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
        if (ABORT_SELECTED.isPressed()) GuiMissionControl.abortSelected();
        if (KAMIKAZE_SELECTED.isPressed()) GuiMissionControl.armKamikazeSelected();
        if (FIRE_SELF_DESTRUCT.isPressed()) RdpUavNetwork.fireSelfDestruct();
        if (AI_TARGET_LOCK.isPressed()) lockAiTarget(minecraft);
        if (CLEAR_TARGET_LOCK.isPressed()) RdpUavNetwork.clearAiTargetLock();
    }

    private void lockAiTarget(Minecraft minecraft) {
        if (minecraft.objectMouseOver != null && minecraft.objectMouseOver.entityHit != null) {
            RdpUavNetwork.lockAiTarget(minecraft.objectMouseOver.entityHit.getUniqueID());
        }
    }
}
