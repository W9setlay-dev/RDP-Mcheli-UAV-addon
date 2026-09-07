package net.vas.rdp.uav.flight;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.wingman.mission.MissionNode;
import com.norwood.mcheli.wingman.mission.MissionOrder;
import com.norwood.mcheli.wingman.mission.MissionPlan;
import com.norwood.mcheli.wingman.mission.MissionType;
import com.norwood.mcheli.wingman.wingman.WingmanEntry;
import com.norwood.mcheli.wingman.wingman.WingmanRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The only RDP-facing boundary for WingMan control.
 */
public final class WingManFlightAdapter {
    public boolean dispatch(MCH_EntityAircraft aircraft, double x, double y, double z,
                            double orbitRadius, int orbitTicks) {
        if (aircraft == null || !aircraft.isUAV()) {
            return false;
        }
        UUID aircraftId = aircraft.getUniqueID();
        if (WingmanRegistry.get(aircraftId) != null) {
            return false;
        }

        MissionOrder order = new MissionOrder();
        order.missionTypes.add(MissionType.RECON);
        order.targetX = x;
        order.targetZ = z;
        order.orbitRadius = orbitRadius;
        order.cruiseAlt = y;
        order.orbitAttack = false;

        List<MissionNode> nodes = new ArrayList<MissionNode>();
        nodes.add(MissionNode.flyTo(x, y, z));
        if (orbitTicks > 0) {
            nodes.add(MissionNode.loiter(orbitTicks));
        }

        WingmanEntry entry = new WingmanEntry(aircraft, WingmanEntry.ATK_NONE);
        entry.order = order;
        entry.mission = nodes;
        entry.missionIndex = 0;
        WingmanRegistry.put(aircraftId, entry);
        return true;
    }

    public void release(MCH_EntityAircraft aircraft) {
        if (aircraft != null) {
            WingmanRegistry.remove(aircraft.getUniqueID());
        }
    }

    public boolean isDispatched(MCH_EntityAircraft aircraft) {
        return aircraft != null && WingmanRegistry.get(aircraft.getUniqueID()) != null;
    }

    public UUID getAircraftId(MCH_EntityAircraft aircraft) {
        return aircraft == null ? null : aircraft.getUniqueID();
    }

    public void savePlan(String name, List<MissionNode> nodes) {
        if (name == null || name.trim().isEmpty() || nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("WingMan plan name and nodes are required");
        }
        MissionPlan.put(name, new ArrayList<MissionNode>(nodes));
    }
}
