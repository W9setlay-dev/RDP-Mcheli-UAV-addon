package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MissionAuthorizationPolicyTest {
    @Test
    public void attackMissionRequiresOperatorAuthorizationUntilExplicitlyGranted() {
        RDPMission mission = mission(true);

        assertTrue(MissionAuthorizationPolicy.requiresOperatorStrikeAuthorization(mission));

        mission.authorizeDestructiveAction(0L);

        assertFalse(MissionAuthorizationPolicy.requiresOperatorStrikeAuthorization(mission));
    }

    @Test
    public void reconnaissanceDoesNotRequireStrikeAuthorization() {
        assertFalse(MissionAuthorizationPolicy.requiresOperatorStrikeAuthorization(
                mission(false)));
    }

    @Test
    public void nullMissionDoesNotRequireAuthorization() {
        assertFalse(MissionAuthorizationPolicy.requiresOperatorStrikeAuthorization(null));
    }

    private static RDPMission mission(boolean attack) {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "switchblade", attack ? MissionType.STRIKE : MissionType.RECONNAISSANCE,
                TargetSpec.point(0, 80, 0, TargetPriority.NORMAL),
                attack, false, false, 64, 32, 0L);
    }
}
