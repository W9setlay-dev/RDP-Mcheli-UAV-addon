package net.vas.rdp.uav.mission;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.vas.rdp.uav.target.ContactSnapshot;

import java.util.UUID;

/**
 * Thin orchestration boundary for the existing mission lifecycle.
 *
 * <p>No planning, targeting, guidance, or recovery behavior belongs here yet.
 * Delegation is deliberate so Phase 1 can establish the architectural seam
 * without introducing a second state machine.</p>
 */
public final class MissionOrchestrator {
    private final MissionLifecycle lifecycle;

    public MissionOrchestrator(MissionManager manager) {
        lifecycle = new MissionLifecycle(manager);
    }

    public void tick(MissionContext context) {
        if (context == null) throw new IllegalArgumentException("Mission context is required");
        lifecycle.tick(context.getWorld());
    }

    public ContactSnapshot contact(UUID targetId) {
        return lifecycle.contact(targetId);
    }

    public boolean cycleContact(World world, MCH_EntityAircraft aircraft, boolean next) {
        return lifecycle.cycleContact(world, aircraft, next);
    }

    public boolean cycleContact(World world, MCH_EntityAircraft aircraft,
                                EntityPlayer operator, boolean next) {
        return lifecycle.cycleContact(world, aircraft, operator, next);
    }

    public boolean clearContact(World world, MCH_EntityAircraft aircraft) {
        return lifecycle.clearContact(world, aircraft);
    }
}
