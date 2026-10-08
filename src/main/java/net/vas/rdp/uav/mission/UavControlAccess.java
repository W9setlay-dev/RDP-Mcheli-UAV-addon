package net.vas.rdp.uav.mission;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.uav.IUavStation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * McHeli treats a UAV station operator differently from an aircraft pilot:
 * the aircraft is controlled by the station and is not ridden by the player.
 */
public final class UavControlAccess {
    private static final String UAV_STATION_CLASS_NAME = "com.norwood.mcheli.uav.MCH_EntityUavStation";

    private UavControlAccess() { }

    public static boolean isOperator(MCH_EntityAircraft aircraft, EntityPlayer player) {
        if (aircraft == null || player == null) return false;
        // A piloted McHeli aircraft is already operator-authorized through
        // its seat. UAV stations are only relevant for remote aircraft.
        if (!aircraft.isUAV()) return aircraft.isPilot(player);
        if (aircraft.isPilot(player)) return true;
        Entity mount = player.getRidingEntity();
        if (isUavStation(mount)) {
            Entity stationOperator = stationOperator(mount);
            return stationOperator != null
                    && aircraft == controlledAircraft(mount)
                    && sameEntity(stationOperator, player);
        }
        IUavStation station = aircraft.getUavStation();
        return station != null && aircraft == station.getControlled()
                && sameEntity(station.getOperator(), player);
    }

    public static MCH_EntityAircraft findControlledAircraft(EntityPlayer player) {
        if (player == null) return null;
        MCH_EntityAircraft aircraft = MCH_EntityAircraft.getAircraft_RiddenOrControl(player);
        if (isOperator(aircraft, player)) return aircraft;
        Entity mount = player.getRidingEntity();
        if (isUavStation(mount)) {
            MCH_EntityAircraft controlled = controlledAircraft(mount);
            if (isOperator(controlled, player)) {
                return controlled;
            }
        }
        World world = player.world;
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof MCH_EntityAircraft
                    && isOperator((MCH_EntityAircraft) entity, player)) {
                return (MCH_EntityAircraft) entity;
            }
        }
        return null;
    }

    /**
     * Returns true only when a person or non-station entity is physically
     * riding the aircraft. McHeli may expose the remote UAV station as the
     * aircraft's rider; that is an observation/control link, not a competing
     * aircraft pilot.
     */
    public static boolean isDirectEntityRider(Object rider) {
        return rider != null && !isUavStation(rider);
    }

    public static boolean hasDirectAircraftRider(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return false;
        return isDirectEntityRider(aircraft.getRiddenByEntity());
    }

    /**
     * Returns true only for a direct physical rider on the aircraft. Passive
     * remote UAV-station presence is not a valid manual takeover signal: the
     * mission lease must remain authoritative while the station is only
     * observing or relaying control.
     */
    public static boolean hasOperatorInStation(MCH_EntityAircraft aircraft) {
        return hasDirectAircraftRider(aircraft);
    }

    private static boolean isUavStation(Object entity) {
        return entity != null && UAV_STATION_CLASS_NAME.equals(entity.getClass().getName());
    }

    private static MCH_EntityAircraft controlledAircraft(Entity mount) {
        if (!isUavStation(mount)) return null;
        try {
            Object controlled = mount.getClass().getMethod("getControlled").invoke(mount);
            return controlled instanceof MCH_EntityAircraft ? (MCH_EntityAircraft) controlled : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Entity stationOperator(Entity mount) {
        if (!isUavStation(mount)) return null;
        try {
            Object operator = mount.getClass().getMethod("getOperator").invoke(mount);
            return operator instanceof Entity ? (Entity) operator : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean sameEntity(Entity first, Entity second) {
        return first == second || (first != null && second != null
                && first.getUniqueID().equals(second.getUniqueID()));
    }
}
