package net.vas.rdp.uav.weapon;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Read-only server safety gates for release decisions. */
public final class GuidedSafetyPolicy {
    private GuidedSafetyPolicy() { }

    public static GuidedSafetyDecision evaluate(World world, Entity aircraft,
                                                GuidedStrikeObjective objective,
                                                boolean requireLineOfSight) {
        return evaluate(world, aircraft, objective, requireLineOfSight,
                new GuidedTerminalProfile(0.0D, true));
    }

    public static GuidedSafetyDecision evaluate(World world, Entity aircraft,
                                                GuidedStrikeObjective objective,
                                                boolean requireLineOfSight,
                                                GuidedTerminalProfile profile) {
        return evaluate(world, aircraft, objective, requireLineOfSight, profile, false);
    }

    public static GuidedSafetyDecision evaluate(World world, Entity aircraft,
                                                GuidedStrikeObjective objective,
                                                boolean requireLineOfSight,
                                                GuidedTerminalProfile profile,
                                                boolean allowUnloadedTarget) {
        if (world == null || aircraft == null || objective == null
                || world.provider.getDimension() != objective.getDimension()) {
            return GuidedSafetyDecision.REJECT_DIMENSION;
        }
        BlockPos target = new BlockPos(objective.getX(), objective.getY(), objective.getZ());
        if (!allowUnloadedTarget && !world.isBlockLoaded(target)) {
            return GuidedSafetyDecision.HOLD_CHUNK_UNLOADED;
        }
        GuidedTerrainAnalyzer.Result terrain = GuidedTerrainAnalyzer.analyze(
                world, aircraft, objective, profile);
        GuidedSafetyDecision terrainDecision = terrainDecision(terrain, allowUnloadedTarget);
        if (terrainDecision != GuidedSafetyDecision.ALLOW) return terrainDecision;
        if (requireLineOfSight) {

            Vec3d start = new Vec3d(aircraft.posX, aircraft.posY + aircraft.getEyeHeight(), aircraft.posZ);
            Vec3d end = new Vec3d(objective.getX(), objective.getY(), objective.getZ());
            RayTraceResult hit = world.rayTraceBlocks(start, end, false, true, false);
            if (hit != null && hit.typeOfHit != RayTraceResult.Type.MISS) {
                // A block-target ray normally terminates on the selected
                // target block itself; that is not an obstruction.
                if (hit.hitVec == null || hit.hitVec.squareDistanceTo(end) > 2.25D) {
                    return GuidedSafetyDecision.HOLD_LINE_OF_SIGHT;
                }
            }
        }
        return GuidedSafetyDecision.ALLOW;
    }

    public static GuidedSafetyDecision terrainDecision(GuidedTerrainAnalyzer.Result terrain,
                                                       boolean allowUnloadedTarget) {
        if (terrain == GuidedTerrainAnalyzer.Result.UNLOADED) {
            return allowUnloadedTarget
                    ? GuidedSafetyDecision.ALLOW : GuidedSafetyDecision.HOLD_CHUNK_UNLOADED;
        }
        return terrain == GuidedTerrainAnalyzer.Result.CLEAR
                ? GuidedSafetyDecision.ALLOW : GuidedSafetyDecision.HOLD_TERRAIN;
    }
}
