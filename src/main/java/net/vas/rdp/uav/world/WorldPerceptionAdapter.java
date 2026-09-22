package net.vas.rdp.uav.world;

import net.minecraft.world.World;

public interface WorldPerceptionAdapter {
    ChunkObservation observeChunk(World world, int chunkX, int chunkZ, long tick);
}
