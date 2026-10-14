package net.vas.rdp.uav.client;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.MCH_Config;
import com.norwood.mcheli.MCH_Key;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.vas.rdp.uav.network.RdpUavNetwork;

import java.util.UUID;

/** Samples camera and lateral-key corrections into server-validated packets. */
@SideOnly(Side.CLIENT)
public final class OperatorTrimClientHandler {
    private static final float ROLL_CORRECTION_PER_TICK = 1.0F;
    private static long sequence = Math.max(1L, System.currentTimeMillis() << 10);
    private UUID sampledAircraft;
    private UUID sampledMission;
    private float previousRelativeYaw;
    private float previousRelativePitch;
    private boolean hasSample;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }
        if (mc.currentScreen != null) {
            reset();
            return;
        }
        MCH_EntityAircraft aircraft = net.vas.rdp.uav.mission.UavControlAccess
                .findControlledAircraft(mc.player);
        RdpUavTargetHud.Telemetry telemetry = RdpUavTargetHud.instance().snapshot();
        if (aircraft == null || telemetry == null || !telemetry.active
                || telemetry.dimension != mc.world.provider.getDimension()
                || telemetry.missionId == null || telemetry.missionId.trim().isEmpty()) {
            reset();
            return;
        }
        UUID missionId;
        try {
            missionId = UUID.fromString(telemetry.missionId);
        } catch (IllegalArgumentException invalid) {
            reset();
            return;
        }
        Entity camera = mc.getRenderViewEntity();
        if (camera == null) camera = mc.player;
        float relativeYaw = wrap(camera.rotationYaw - aircraft.getYaw());
        float relativePitch = camera.rotationPitch - aircraft.getPitch();
        if (!Float.isFinite(relativeYaw) || !Float.isFinite(relativePitch)) {
            reset();
            return;
        }
        if (!hasSample || !aircraft.getUniqueID().equals(sampledAircraft)
                || !missionId.equals(sampledMission)) {
            sampledAircraft = aircraft.getUniqueID();
            sampledMission = missionId;
            previousRelativeYaw = relativeYaw;
            previousRelativePitch = relativePitch;
            hasSample = true;
            return;
        }
        float yawDelta = wrap(relativeYaw - previousRelativeYaw);
        // Minecraft pitch grows as the view moves down; invert it so look-up
        // produces a positive climb correction.
        float pitchDelta = previousRelativePitch - relativePitch;
        previousRelativeYaw = relativeYaw;
        previousRelativePitch = relativePitch;
        yawDelta = clamp(yawDelta, -7.5F, 7.5F);
        pitchDelta = clamp(pitchDelta, -5.0F, 5.0F);
        float rollDelta = lateralRollCorrection();
        if (Math.abs(yawDelta) < 0.015F && Math.abs(pitchDelta) < 0.015F
                && rollDelta == 0.0F) return;
        long next = ++sequence;
        if (next <= 0L) {
            sequence = Math.max(1L, System.currentTimeMillis() << 10);
            next = ++sequence;
        }
        RdpUavNetwork.sendOperatorTrim(aircraft.getUniqueID(), missionId,
                next, mc.world.getTotalWorldTime(), yawDelta, pitchDelta, rollDelta);
    }

    private static float lateralRollCorrection() {
        if (MCH_Config.KeyLeft == null || MCH_Config.KeyRight == null) return 0.0F;
        boolean left = MCH_Key.isKeyDown(MCH_Config.KeyLeft.prmInt);
        boolean right = MCH_Key.isKeyDown(MCH_Config.KeyRight.prmInt);
        return lateralRollCorrection(left, right);
    }

    static float lateralRollCorrection(boolean left, boolean right) {
        if (left == right) return 0.0F;
        return left ? -ROLL_CORRECTION_PER_TICK : ROLL_CORRECTION_PER_TICK;
    }

    private void reset() {
        hasSample = false;
        sampledAircraft = null;
        sampledMission = null;
    }

    private static float wrap(float angle) {
        while (angle >= 180.0F) angle -= 360.0F;
        while (angle < -180.0F) angle += 360.0F;
        return angle;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
