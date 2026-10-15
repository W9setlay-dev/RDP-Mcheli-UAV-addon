package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MissionObjectiveTest {
    @Test(expected = IllegalArgumentException.class)
    public void entityObjectiveRequiresAnExactEntityId() {
        new MissionObjective(UUID.randomUUID(),
                TargetSpec.manual("minecraft:zombie", TargetPriority.HIGH), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void pointObjectiveCannotHaveAnEntityId() {
        new MissionObjective(UUID.randomUUID(),
                TargetSpec.point(1.0D, 2.0D, 3.0D, TargetPriority.HIGH),
                UUID.randomUUID());
    }

    @Test(expected = IllegalArgumentException.class)
    public void missionRejectsMoreThanTheBoundedFollowOnObjectiveCount() {
        RDPMission mission = new RDPMission(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "TEST_UAV", MissionType.STRIKE,
                TargetSpec.point(0.0D, 32.0D, 0.0D, TargetPriority.HIGH),
                true, false, false, 64.0D, 32.0D, 1L);
        List<MissionObjective> objectives = new ArrayList<>();
        for (int i = 0; i < RDPMission.MAX_COMPOSED_OBJECTIVES; i++) {
            objectives.add(new MissionObjective(UUID.randomUUID(),
                    TargetSpec.point(i, 32.0D, 0.0D, TargetPriority.HIGH), null));
        }

        mission.setFollowOnObjectives(objectives);
    }
}
