package net.vas.rdp.uav.world;

import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;

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

    @Override
    public TerrainObservation observeTerrain(World world, int blockX, int blockZ, long tick) {
        if (world == null || tick < 0L) {
            throw new IllegalArgumentException("World and non-negative tick are required");
        }
        BlockPos column = new BlockPos(blockX, 0, blockZ);
        if (!world.isBlockLoaded(column)) {
            return new TerrainObservation(world.provider.getDimension(), blockX, blockZ,
                    0, tick, ChunkState.UNLOADED, false, false, false);
        }
        // World#getHeight reads the already loaded chunk's height map. The
        // preceding isBlockLoaded guard prevents a terrain query from loading it.
        int topY = Math.min(world.getHeight() - 1, world.getHeight(column).getY() - 1);
        boolean solid = false;
        boolean fluid = false;
        boolean collision = false;
        int surfaceY = Math.max(0, topY);
        // Check a short band under the height-map result so a fluid surface and
        // non-full collision shapes are reported accurately without a tall scan.
        for (int y = topY; y >= Math.max(0, topY - 4); y--) {
            BlockPos pos = new BlockPos(blockX, y, blockZ);
            IBlockState state = world.getBlockState(pos);
            Material material = state.getMaterial();
            boolean liquid = material.isLiquid();
            boolean hasCollision = state.getCollisionBoundingBox(world, pos) != null;
            if (liquid || hasCollision) {
                surfaceY = y;
                solid = hasCollision && !liquid;
                fluid = liquid;
                collision = hasCollision;
                break;
            }
        }
        return new TerrainObservation(world.provider.getDimension(), blockX, blockZ,
                surfaceY, tick, ChunkState.LOADED, solid, fluid, collision);
    }

    @Override
    public AirspaceObservation observeAirspace(World world, int blockX, int blockY,
                                               int blockZ, int horizontalRadius, long tick) {
        if (world == null || tick < 0L || horizontalRadius < 0 || horizontalRadius > 4) {
            throw new IllegalArgumentException("Invalid airspace query");
        }
        boolean collision = false;
        boolean fluid = false;
        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = new BlockPos(blockX + dx, blockY + dy, blockZ + dz);
                    if (pos.getY() < 0 || pos.getY() >= world.getHeight()) continue;
                    if (!world.isBlockLoaded(pos)) {
                        return new AirspaceObservation(tick, ChunkState.UNLOADED,
                                collision, fluid);
                    }
                    IBlockState state = world.getBlockState(pos);
                    Material material = state.getMaterial();
                    if (material != null && material.isLiquid()) fluid = true;
                    if (state.getCollisionBoundingBox(world, pos) != null) collision = true;
                }
            }
        }
        return new AirspaceObservation(tick, ChunkState.LOADED, collision, fluid);
    }
}
