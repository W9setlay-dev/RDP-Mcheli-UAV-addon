package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetSelectionMode;
import net.vas.rdp.uav.target.TargetSpec;

import java.util.UUID;

/** A server-validated designation queued after the mission's current target. */
public final class MissionObjective {
    private final UUID designationId;
    private final TargetSpec targetSpec;
    private final UUID targetEntityId;

    public MissionObjective(UUID designationId, TargetSpec targetSpec, UUID targetEntityId) {
        if (designationId == null || targetSpec == null
                || (targetSpec.getMode() == TargetSelectionMode.MANUAL
                && targetEntityId == null)
                || (targetSpec.getMode() == TargetSelectionMode.POINT
                && targetEntityId != null)
                || targetSpec.getMode() == TargetSelectionMode.AUTO) {
            throw new IllegalArgumentException("Invalid composed mission objective");
        }
        this.designationId = designationId;
        this.targetSpec = targetSpec;
        this.targetEntityId = targetEntityId;
    }

    public UUID getDesignationId() { return designationId; }
    public TargetSpec getTargetSpec() { return targetSpec; }
    public UUID getTargetEntityId() { return targetEntityId; }
}
