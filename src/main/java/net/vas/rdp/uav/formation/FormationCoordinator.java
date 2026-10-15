package net.vas.rdp.uav.formation;

import net.vas.rdp.uav.swarm.FormationSlot;
import net.vas.rdp.uav.swarm.SwarmFormation;
import net.vas.rdp.uav.swarm.SwarmMember;
import net.vas.rdp.uav.swarm.SwarmRecord;
import net.vas.rdp.uav.swarm.SwarmRole;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side adapter between role tasking and the persisted swarm aggregate. */
public final class FormationCoordinator {
    private final FormationSlotAssignment assignment = new FormationSlotAssignment();

    /** Reconciles deterministic geometry into the swarm's existing persisted slots. */
    public Map<UUID, FormationGeometry> reconcile(SwarmRecord swarm, FormationPreset preset) {
        if (swarm == null || preset == null) {
            throw new IllegalArgumentException("Swarm and formation preset are required");
        }
        Map<UUID, WingmanRole> roles = new LinkedHashMap<>();
        UUID leader = swarm.referenceMember();
        for (SwarmMember member : swarm.getMembers()) {
            WingmanRole role = WingmanRole.fromSwarmRole(member.getRole());
            roles.put(member.getAircraftId(),
                    member.getAircraftId().equals(leader) ? WingmanRole.LEADER : role);
        }
        double sideSpacing = swarm.getConfiguration().getHorizontalSpacing();
        double altitudeSpacing = swarm.getConfiguration().getVerticalSpacing();
        double rearSpacing = FormationConfig.rearSpacing;
        Map<UUID, FormationGeometry> geometries = assignment.assign(
                roles, preset, sideSpacing, altitudeSpacing, rearSpacing);
        for (SwarmMember member : swarm.getMembers()) {
            FormationGeometry geometry = geometries.get(member.getAircraftId());
            if (geometry != null) {
                member.setSlot(new FormationSlot(geometry.getSlotIndex(),
                        geometry.getSideOffset(), geometry.getAltitudeOffset(),
                        geometry.getRearOffset(), geometry.getSlotIndex()));
            }
        }
        return geometries;
    }

    /** Resolves the legacy swarm preset into the closest supported deterministic layout. */
    public static FormationPreset fromSwarmFormation(SwarmFormation formation) {
        if (formation == null) throw new IllegalArgumentException("Swarm formation is required");
        switch (formation) {
            case LINE: return FormationPreset.LINE_ABREAST;
            case COLUMN: return FormationPreset.COLUMN;
            case WEDGE: return FormationPreset.WEDGE;
            case GRID:
                return FormationConfig.defaultPreset == FormationPreset.ECHELON_LEFT
                        ? FormationPreset.ECHELON_LEFT : FormationPreset.ECHELON_RIGHT;
            case RING:
            case ORBIT: return FormationPreset.TRAIL;
            case CUSTOM:
            default:
                throw new IllegalArgumentException("Custom swarm layouts are not supported");
        }
    }
}
