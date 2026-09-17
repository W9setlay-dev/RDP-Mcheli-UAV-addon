package net.vas.rdp.uav.target;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.Objects;

/** Server-side contact safety policy.  It never moves or mutates an entity. */
public final class TargetPolicy {
    public static boolean rejectDeeplyEmbedded = true;
    public static boolean allowHostileVehicles = true;
    public static double maximumEmbeddedDepth = 1.5D;

    private TargetPolicy() { }

    public static void load(File configDir) {
        Configuration config = new Configuration(new File(configDir, "rdp-uav-targets.cfg"));
        config.load();
        rejectDeeplyEmbedded = config.getBoolean("rejectDeeplyEmbedded", "safety",
                rejectDeeplyEmbedded, "Reject contacts whose feet are deeply inside solid ground.");
        allowHostileVehicles = config.getBoolean("allowHostileVehicles", "filter",
                allowHostileVehicles, "Allow hostile military/bandit vehicles as selectable contacts.");
        maximumEmbeddedDepth = config.getFloat("maximumEmbeddedDepth", "safety",
                (float) maximumEmbeddedDepth, 0.1F, 16F,
                "Maximum tolerated distance below the supporting block top.");
        if (config.hasChanged()) config.save();
    }

    public static boolean isSafeContact(World world, Entity entity) {
        if (world == null || entity == null || entity.isDead) return false;
        if (!rejectDeeplyEmbedded) return true;
        int y = (int) Math.floor(entity.posY);
        BlockPos support = new BlockPos(entity.posX, y, entity.posZ);
        if (!world.getBlockState(support).getMaterial().blocksMovement()) return true;
        double supportTop = support.getY() + 1.0D;
        return entity.posY >= supportTop - maximumEmbeddedDepth;
    }

    /** Policy used by operator crosshair selection; missions use this same gate. */
    public static boolean isSelectableContact(World world, Entity entity, EntityPlayer operator) {
        if (!isSafeContact(world, entity)) return false;
        if (entity instanceof EntityPlayer
                && operator != null && operator.isOnSameTeam(entity)) return false;
        String id = EntityList.getKey(entity) == null ? "" : Objects.requireNonNull(EntityList.getKey(entity)).toString();
        return TargetClassifier.isHostileCandidate(id)
                || (allowHostileVehicles && TargetClassifier.isHostileVehicle(id));
    }
}
