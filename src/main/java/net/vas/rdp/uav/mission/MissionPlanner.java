package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.ContactLifecycle;
import net.vas.rdp.uav.target.ContactSnapshot;

/**
 * Pure planning seam for target availability and objective readiness.
 *
 * <p>This phase does not connect the planner to production lifecycle
 * transitions. The existing MissionLifecycle remains authoritative until
 * planner decisions have deterministic coverage.</p>
 */
public final class MissionPlanner {
    public MissionPlanDecision decide(ContactSnapshot target, boolean objectiveReady) {
        if (target == null) return MissionPlanDecision.WAIT_FOR_TARGET;
        ContactLifecycle lifecycle = target.getLifecycle();
        if (!target.isVisible() || lifecycle == ContactLifecycle.LOST
                || lifecycle == ContactLifecycle.EXPIRED
                || target.getConfidence() <= 0.0D) {
            return MissionPlanDecision.REACQUIRE_TARGET;
        }
        if (!objectiveReady) return MissionPlanDecision.TRACK_TARGET;
        return MissionPlanDecision.EXECUTE_OBJECTIVE;
    }
}
