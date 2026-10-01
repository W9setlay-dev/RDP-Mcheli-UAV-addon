package net.vas.rdp.uav.world;

/** One non-loading terrain query for a world column. Coordinates are Minecraft blocks. */
public final class TerrainObservation {
    private final int dimension;
    private final int blockX;
    private final int blockZ;
    private final int surfaceY;
    private final long tick;
    private final ChunkState loadedState;
    private final boolean solidSurface;
    private final boolean fluidSurface;
    private final boolean collisionSurface;

    public TerrainObservation(int dimension, int blockX, int blockZ, int surfaceY,
                              long tick, ChunkState loadedState, boolean solidSurface,
                              boolean fluidSurface, boolean collisionSurface) {
        if (tick < 0L || loadedState == null) throw new IllegalArgumentException("Invalid terrain observation");
        this.dimension = dimension;
        this.blockX = blockX;
        this.blockZ = blockZ;
        this.surfaceY = surfaceY;
        this.tick = tick;
        this.loadedState = loadedState;
        this.solidSurface = solidSurface;
        this.fluidSurface = fluidSurface;
        this.collisionSurface = collisionSurface;
    }

    public int getDimension() { return dimension; }
    public int getBlockX() { return blockX; }
    public int getBlockZ() { return blockZ; }
    public int getSurfaceY() { return surfaceY; }
    public long getTick() { return tick; }
    public ChunkState getLoadedState() { return loadedState; }
    public boolean hasSolidSurface() { return solidSurface; }
    public boolean hasFluidSurface() { return fluidSurface; }
    public boolean hasCollisionSurface() { return collisionSurface; }
    public boolean isKnown() { return loadedState == ChunkState.LOADED; }
}
