package net.vas.rdp.uav.flight;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import net.vas.rdp.uav.RdpUavDebug;
import net.minecraftforge.common.MinecraftForge;

/** Entry point injected into McHeliCE 1.5.1's verified aircraft destruction method. */
public final class KamikazePreDestructionHook {
    private KamikazePreDestructionHook() { }

    public static boolean beforeAircraftDestruction(MCH_EntityAircraft aircraft,
                                                     DamageSource source,
                                                     Entity causingEntity,
                                                     String reason,
                                                     boolean playerCaused) {
        if (aircraft == null || aircraft.world == null || aircraft.world.isRemote) return false;
        try {
            KamikazePreDestructionEvent event = new KamikazePreDestructionEvent(
                    aircraft, source, causingEntity, reason);
            MinecraftForge.EVENT_BUS.post(event);
            return event.isFuseHandled();
        } catch (RuntimeException failure) {
            RdpUavDebug.event("Kamikaze", "PRE_DESTRUCTION_HOOK_FAILED",
                    "aircraft", aircraft.getUniqueID(),
                    "reason", reason,
                    "error", failure.getClass().getSimpleName());
            return false;
        }
    }
}
