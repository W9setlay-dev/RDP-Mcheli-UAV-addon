package net.vas.rdp.uav.mission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MissionValidationResult {
    private final List<String> errors;
    private MissionValidationResult(List<String> errors) {
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }
    public boolean isValid() { return errors.isEmpty(); }
    public List<String> getErrors() { return errors; }
    public static MissionValidationResult validate(MissionDefinition definition) {
        if (definition == null) return new MissionValidationResult(
                Collections.singletonList("Mission definition is required"));
        List<String> errors = new ArrayList<>();
        if (definition.getMissionType() == MissionType.PATROL) {
            if (definition.isAttack()) errors.add("Patrol routes cannot authorize attack execution");
            try {
                PatrolRouteCodec.validate(definition.getWaypoints());
            } catch (IllegalArgumentException invalid) {
                errors.add(invalid.getMessage());
            }
        } else if (definition.getTarget().isEmpty()) {
            errors.add("Target is required");
        } else if (definition.getMissionType() == MissionType.SQUARE_STRIKE) {
            String[] values = definition.getTarget().trim().split("\\s+");
            if (values.length < 3 || values.length > 4) {
                errors.add("Square target must be centerX centerY centerZ [size]");
            } else {
                for (int i = 0; i < values.length; i++) {
                    try {
                        double value = Double.parseDouble(values[i]);
                        if (!Double.isFinite(value)) {
                            errors.add("Square coordinates and size must be finite");
                            break;
                        }
                    } catch (NumberFormatException invalid) {
                        errors.add("Square coordinates and size must be numeric");
                        break;
                    }
                }
            }
        } else if (!definition.getTarget().matches(
                "(?i)(mod=[A-Za-z0-9_.-]+|[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+)")) {
            errors.add("Target must be namespace:id or mod=namespace");
        }
        if (definition.getMissionType() == null) errors.add("Mission type is required");
        if (definition.isSwarmEnabled() && definition.getSwarmMaximumMembers() < 1) {
            errors.add("Swarm size must be positive");
        }
        if (definition.getGuidanceMode().equals("UNSPECIFIED")) errors.add("Guidance mode is required");
        if (definition.getRecoveryMode().equals("UNSPECIFIED")) errors.add("Recovery mode is required");
        return new MissionValidationResult(errors);
    }
}
