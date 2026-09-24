package net.vas.rdp.uav.flight;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.UavVehicleProfiles;

import java.lang.reflect.Method;

/**
 * Native McHeli control bridge using only the verified aircraft APIs exposed by
 * the official McHeliCE source tree.
 */
public final class NativeAircraftControlAdapter {
    private NativeAircraftControlAdapter() { }

    public static boolean canControlAircraft(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return false;
        if (!isKnownProfile(aircraft)) return false;
        return hasMethod(aircraft, "setRotYaw", float.class)
                || hasMethod(aircraft, "setRotPitch", float.class)
                || hasMethod(aircraft, "setRotRoll", float.class)
                || hasMethod(aircraft, "setThrottle", double.class)
                || hasMethod(aircraft, "setCurrentThrottle", double.class);
    }

    public static boolean isKnownProfile(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return false;
        String definition = aircraft.getAcInfo() == null ? "" : aircraft.getAcInfo().name;
        UavVehicleProfile profile = UavVehicleProfiles.forAircraft(definition);
        return profile != null && profile.getExecutionProfile() != UavExecutionProfile.UNKNOWN;
    }

    private static boolean hasMethod(MCH_EntityAircraft aircraft, String name, Class<?> type) {
        try {
            aircraft.getClass().getMethod(name, type);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

}
