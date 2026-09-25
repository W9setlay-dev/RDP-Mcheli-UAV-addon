package net.vas.rdp.uav.world;

import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderServer;

/**
 * Server-side chunk availability perception backed by the Forge world.
 *
 * <p>This adapter deliberately reports only loaded-state knowledge. It does
 * not load chunks or infer terrain, threats, or return-route safety.</p>
 */
public final class MinecraftWorldPerceptionAdapter implements WorldPerceptionAdapter {
    @Override
    public ChunkObservation observeChunk(World world, int chunkX, int chunkZ, long tick) {
        if (world == null || tick < 0L) {
            throw new IllegalArgumentException("World and non-negative tick are required");
        }
        ChunkState state = ((ChunkProviderServer) world.getChunkProvider())
                .chunkExists(chunkX, chunkZ)
                ? ChunkState.LOADED : ChunkState.UNLOADED;
        return new ChunkObservation(world.provider.getDimension(), chunkX, chunkZ, tick, state);
    }
}
