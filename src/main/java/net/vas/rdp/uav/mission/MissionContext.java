package net.vas.rdp.uav.mission;

import net.minecraft.world.World;

/**
 * Immutable input boundary for one server-side mission orchestration pass.
 *
 * <p>The context is intentionally small while the existing lifecycle remains
 * the behavior owner. It provides a stable seam for future world perception
 * and telemetry without changing current mission semantics.</p>
 */
public final class MissionContext {
    private final World world;
    private final long tick;

    public MissionContext(World world) {
        if (world == null || world.isRemote) {
            throw new IllegalArgumentException("A server world is required");
        }
        this.world = world;
        this.tick = world.getTotalWorldTime();
    }

    public World getWorld() {
        return world;
    }

    public long getTick() {
        return tick;
    }
}
