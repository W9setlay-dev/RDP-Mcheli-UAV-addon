package net.vas.rdp.uav.weapon;

import com.norwood.mcheli.weapon.MCH_WeaponInfo;
import com.norwood.mcheli.weapon.MCH_WeaponSet;

/**
 * Reads the real McHeli runtime capability for chunk streaming. The addon does
 * not create an independent chunk loader; it consumes the authoritative
 * MCH_WeaponInfo.enableChunkLoader flag, which is populated from McHeli's YAML
 * field named CanLoadChunks.
 */
public final class GuidedChunkCapability {
    private GuidedChunkCapability() { }

    public static boolean allowUnloadedTarget(boolean supportsUnloadedTargetExecution,
                                              boolean mcheliCanLoadChunk) {
        return supportsUnloadedTargetExecution || mcheliCanLoadChunk;
    }

    public static boolean fromWeaponSet(MCH_WeaponSet weapon) {
        if (weapon == null || weapon.getInfo() == null) return false;
        return fromWeaponInfo(weapon.getInfo());
    }

    public static boolean fromWeaponInfo(MCH_WeaponInfo info) {
        if (info == null) return false;
        return info.enableChunkLoader;
    }
}
