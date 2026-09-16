package net.vas.rdp.uav.weapon;

import com.norwood.mcheli.weapon.MCH_WeaponBase;
import com.norwood.mcheli.weapon.MCH_WeaponGuidanceSystem;
import com.norwood.mcheli.weapon.MCH_WeaponSet;

/**
 * Small, explicit boundary for the McHeli 1.5.1 public native APIs.
 */
public final class GuidedNativeContract {
    private GuidedNativeContract() { }

    public static boolean supportsEntitySeeker(MCH_WeaponSet weapon) {
        if (weapon == null) return false;
        MCH_WeaponBase current = weapon.getCurrentWeapon();
        return current != null && current.getGuidanceSystem() != null;
    }

    public static MCH_WeaponGuidanceSystem guidanceSystem(MCH_WeaponSet weapon) {
        if (!supportsEntitySeeker(weapon)) return null;
        return weapon.getCurrentWeapon().getGuidanceSystem();
    }

    public static String entityDecision(boolean nativeApiAvailable, boolean targetAvailable) {
        if (!targetAvailable) return "GUIDED_TARGET_LOST";
        return nativeApiAvailable ? "OK" : "UNSUPPORTED_SEEKER_TARGET_ASSIGNMENT";
    }
}
