package net.vas.rdp.uav.formation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side deterministic formation-slot assignment for a swarm snapshot. */
public final class FormationSlotAssignment {
    private static final Comparator<UUID> UUID_ORDER =
            Comparator.comparing(UUID::toString);

    /** Assigns slots in stable UUID order; removed members leave no reserved slot. */
    public Map<UUID, FormationGeometry> assign(Map<UUID, WingmanRole> roles,
                                               FormationPreset preset,
                                               double sideSpacing,
                                               double altitudeSpacing,
                                               double rearSpacing) {
        if (roles == null || preset == null || !finite(sideSpacing, altitudeSpacing, rearSpacing)
                || sideSpacing <= 0.0D || altitudeSpacing <= 0.0D || rearSpacing <= 0.0D) {
            throw new IllegalArgumentException("Invalid formation assignment inputs");
        }
        List<UUID> aircraft = new ArrayList<>(roles.keySet());
        if (aircraft.contains(null)) throw new IllegalArgumentException("Aircraft UUID is required");
        aircraft.sort(UUID_ORDER);
        LinkedHashMap<UUID, FormationGeometry> assigned = new LinkedHashMap<>();
        for (int slot = 0; slot < aircraft.size(); slot++) {
            UUID aircraftId = aircraft.get(slot);
            WingmanRole role = roles.get(aircraftId);
            if (role == null) throw new IllegalArgumentException("Wingman role is required");
            double side = 0.0D;
            double altitude = 0.0D;
            double rear = 0.0D;
            if (slot > 0) {
                switch (preset) {
                    case ECHELON_RIGHT:
                        side = sideSpacing;
                        rear = slot * rearSpacing;
                        break;
                    case ECHELON_LEFT:
                        side = -sideSpacing;
                        rear = slot * rearSpacing;
                        break;
                    case LINE_ABREAST:
                        side = (slot % 2 == 1 ? 1.0D : -1.0D)
                                * ((slot + 1) / 2) * sideSpacing;
                        break;
                    case TRAIL:
                        rear = slot * rearSpacing;
                        break;
                    case WEDGE:
                        side = (slot % 2 == 1 ? 1.0D : -1.0D)
                                * ((slot + 1) / 2) * sideSpacing;
                        rear = ((slot + 1) / 2) * rearSpacing;
                        break;
                    case COLUMN:
                        rear = slot * rearSpacing;
                        altitude = slot * altitudeSpacing;
                        break;
                    default:
                        throw new IllegalStateException("Unsupported formation preset: " + preset);
                }
            }
            assigned.put(aircraftId, new FormationGeometry(slot, side, altitude, rear,
                    role, role == WingmanRole.STRIKER ? 30.0D : 25.0D, 15.0D));
        }
        return Collections.unmodifiableMap(assigned);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
