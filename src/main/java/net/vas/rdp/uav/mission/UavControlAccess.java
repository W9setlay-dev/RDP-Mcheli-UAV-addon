package net.vas.rdp.uav.mission;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.uav.MCH_EntityUavStation;
import com.norwood.mcheli.uav.IUavStation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * McHeli treats a UAV station operator differently from an aircraft pilot:
 * the aircraft is controlled by the station and is not ridden by the player.
 */
public final class UavControlAccess {
    private UavControlAccess() { }

    public static boolean isOperator(MCH_EntityAircraft aircraft, EntityPlayer player) {
        if (aircraft == null || player == null) return false;
        // A piloted McHeli aircraft is already operator-authorized through
        // its seat. UAV stations are only relevant for remote aircraft.
        if (!aircraft.isUAV()) return aircraft.isPilot(player);
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

    public static MCH_EntityAircraft findControlledAircraft(EntityPlayer player) {
        if (player == null) return null;
        MCH_EntityAircraft aircraft = MCH_EntityAircraft.getAircraft_RiddenOrControl(player);
        if (isOperator(aircraft, player)) return aircraft;
        Entity mount = player.getRidingEntity();
        if (mount instanceof MCH_EntityUavStation) {
            MCH_EntityAircraft controlled = ((MCH_EntityUavStation) mount).getControlled();
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
    public static boolean hasDirectAircraftRider(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return false;
        Entity rider = aircraft.getRiddenByEntity();
        return rider != null && !(rider instanceof MCH_EntityUavStation);
    }

    /**
     * Returns true when the UAV is actively under a human-operated station or
     * direct-rider control. This is distinct from a passive AI mission lease and
     * is the correct operator-presence signal for manual authority handoff.
     */
    public static boolean hasOperatorInStation(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return false;
        Entity rider = aircraft.getRiddenByEntity();
        if (rider instanceof MCH_EntityUavStation) {
            MCH_EntityUavStation station = (MCH_EntityUavStation) rider;
            return station.getControlled() == aircraft && station.getOperator() != null;
        }
        if (rider != null) {
            return true;
        }
        IUavStation station = aircraft.getUavStation();
        return station != null
                && aircraft == station.getControlled()
                && station.getOperator() != null;
    }

    private static boolean sameEntity(Entity first, Entity second) {
        return first == second || (first != null && second != null
                && first.getUniqueID().equals(second.getUniqueID()));
    }
}
