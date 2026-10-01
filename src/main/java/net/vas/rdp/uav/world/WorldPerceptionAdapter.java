package net.vas.rdp.uav.world;

import net.minecraft.world.World;

public interface WorldPerceptionAdapter {
    ChunkObservation observeChunk(World world, int chunkX, int chunkZ, long tick);

    /** Default perception is explicitly unknown; implementations must not load a chunk. */
    default TerrainObservation observeTerrain(World world, int blockX, int blockZ, long tick) {
        if (world == null || tick < 0L) throw new IllegalArgumentException("World and tick are required");
        return new TerrainObservation(world.provider.getDimension(), blockX, blockZ,
                0, tick, ChunkState.UNKNOWN, false, false, false);
    }

    /** A default unknown means the planner must hold, never assume clear air. */
    default AirspaceObservation observeAirspace(World world, int blockX, int blockY,
                                                int blockZ, int horizontalRadius, long tick) {
        if (world == null || tick < 0L) throw new IllegalArgumentException("World and tick are required");
        return new AirspaceObservation(tick, ChunkState.UNKNOWN, false, false);
    }
}
