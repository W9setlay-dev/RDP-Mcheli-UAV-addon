package net.vas.rdp.uav.world;

public final class ChunkObservation {
    private final int dimension;
    private final int chunkX;
    private final int chunkZ;
    private final long tick;
    private final ChunkState state;

    public ChunkObservation(int dimension, int chunkX, int chunkZ, long tick,
                            ChunkState state) {
        if (state == null || tick < 0L) {
            throw new IllegalArgumentException("Chunk observation is invalid");
        }
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.tick = tick;
        this.state = state;
    }

    public int getDimension() { return dimension; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public long getTick() { return tick; }
    public ChunkState getState() { return state; }
}
