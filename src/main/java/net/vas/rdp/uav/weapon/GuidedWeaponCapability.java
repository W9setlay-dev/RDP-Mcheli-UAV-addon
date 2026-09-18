package net.vas.rdp.uav.weapon;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Configuration-derived capability for one McHeli weapon definition/type. */
public final class GuidedWeaponCapability {
    private final String definition;
    private final String type;
    private final GuidedNativeBehavior behavior;
    private final Set<GuidedObjectiveType> objectives;
    private final GuidedTerminalProfile terminalProfile;
    private final boolean supportsUnloadedTargetExecution;

    public GuidedWeaponCapability(String definition, String type, GuidedNativeBehavior behavior,
                                  Set<GuidedObjectiveType> objectives,
                                  GuidedTerminalProfile terminalProfile) {
        this(definition, type, behavior, objectives, terminalProfile, false);
    }

    public GuidedWeaponCapability(String definition, String type, GuidedNativeBehavior behavior,
                                  Set<GuidedObjectiveType> objectives,
                                  GuidedTerminalProfile terminalProfile,
                                  boolean supportsUnloadedTargetExecution) {
        if (definition == null || definition.trim().isEmpty() || type == null
                || type.trim().isEmpty() || behavior == null || objectives == null
                || terminalProfile == null) {
            throw new IllegalArgumentException("Weapon capability fields are required");
        }
        this.definition = definition.trim().toLowerCase();
        this.type = type.trim().toLowerCase();
        this.behavior = behavior;
        EnumSet<GuidedObjectiveType> copy = objectives.isEmpty()
                ? EnumSet.noneOf(GuidedObjectiveType.class) : EnumSet.copyOf(objectives);
        this.objectives = Collections.unmodifiableSet(copy);
        this.terminalProfile = terminalProfile;
        this.supportsUnloadedTargetExecution = supportsUnloadedTargetExecution;
    }

    public String getDefinition() { return definition; }
    public String getType() { return type; }
    public GuidedNativeBehavior getBehavior() { return behavior; }
    public Set<GuidedObjectiveType> getObjectives() { return objectives; }
    public GuidedTerminalProfile getTerminalProfile() { return terminalProfile; }
    public boolean supportsUnloadedTargetExecution() {
        return supportsUnloadedTargetExecution;
    }
    public boolean supports(GuidedObjectiveType objective) {
        return objective != null && objectives.contains(objective)
                && behavior != GuidedNativeBehavior.UNSUPPORTED;
    }
}
