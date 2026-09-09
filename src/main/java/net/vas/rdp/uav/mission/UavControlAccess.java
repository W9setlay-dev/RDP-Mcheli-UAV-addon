package net.vas.rdp.uav.mission;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.uav.MCH_EntityUavStation;
import com.norwood.mcheli.uav.IUavStation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * McHeli treats a UAV station operator differently from an aircraft pilot:
 * the aircraft is controlled by the station and is not ridden by the player.
 */
public final class UavControlAccess {
    private UavControlAccess() { }

    public static boolean isOperator(MCH_EntityAircraft aircraft, EntityPlayer player) {
        if (aircraft == null || player == null || !aircraft.isUAV()) return false;
        if (aircraft.isPilot(player)) return true;
        Entity mount = player.getRidingEntity();
        if (mount instanceof MCH_EntityUavStation) {
            MCH_EntityUavStation station = (MCH_EntityUavStation) mount;
            return aircraft == station.getControlled()
                    && sameEntity(station.getOperator(), player);
        }
        IUavStation station = aircraft.getUavStation();
        return station != null && aircraft == station.getControlled()
                && sameEntity(station.getOperator(), player);
    }

    private static boolean sameEntity(Entity first, Entity second) {
        return first == second || (first != null && second != null
                && first.getUniqueID().equals(second.getUniqueID()));
    }
}
