package net.vas.rdp.uav.flight;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * Fired synchronously at McHeli's native aircraft-destruction boundary.
 * A subscriber may mark the fuse as handled only after the native payload
 * effect succeeded. The event itself is deliberately non-cancelable so another
 * mod cannot accidentally suppress McHeli's ordinary destruction path.
 */
public final class KamikazePreDestructionEvent extends Event {
    private final MCH_EntityAircraft aircraft;
    private final DamageSource damageSource;
    private final Entity causingEntity;
    private final String destructionReason;
    private boolean fuseHandled;

    public KamikazePreDestructionEvent(MCH_EntityAircraft aircraft,
                                       DamageSource damageSource,
                                       Entity causingEntity,
                                       String destructionReason) {
        this.aircraft = aircraft;
        this.damageSource = damageSource;
        this.causingEntity = causingEntity;
        this.destructionReason = destructionReason == null ? "unknown" : destructionReason;
    }

    public MCH_EntityAircraft getAircraft() { return aircraft; }
    public DamageSource getDamageSource() { return damageSource; }
    public Entity getCausingEntity() { return causingEntity; }
    public String getDestructionReason() { return destructionReason; }
    public boolean isFuseHandled() { return fuseHandled; }
    void markFuseHandled() { fuseHandled = true; }
}
