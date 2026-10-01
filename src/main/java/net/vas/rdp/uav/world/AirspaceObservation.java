package net.vas.rdp.uav.world;

/** Loaded-state and collision sample around one point on a proposed 3D flight corridor. */
public final class AirspaceObservation {
    private final long tick;
    private final ChunkState state;
    private final boolean collision;
    private final boolean fluid;

    public AirspaceObservation(long tick, ChunkState state,
                               boolean collision, boolean fluid) {
        if (tick < 0L || state == null) throw new IllegalArgumentException("Invalid airspace observation");
        this.tick = tick;
        this.state = state;
        this.collision = collision;
        this.fluid = fluid;
    }
    public long getTick() { return tick; }
    public ChunkState getState() { return state; }
    public boolean hasCollision() { return collision; }
    public boolean hasFluid() { return fluid; }
}
