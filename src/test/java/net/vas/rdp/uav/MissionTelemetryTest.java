package net.vas.rdp.uav;

import net.vas.rdp.uav.mission.MissionTelemetry;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class MissionTelemetryTest {
    @Test
    public void projectionCopiesAuthoritativeMissionState() {
        RDPMission mission = new RDPMission(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "switchblade", MissionType.RECONNAISSANCE,
                TargetSpec.point(10, 20, 30, TargetPriority.NORMAL),
                false, false, false, 256, 64, 1);
        MissionTelemetry telemetry = MissionTelemetry.from(mission);
        assertEquals(mission.getMissionId(), telemetry.getMissionId());
        assertEquals(mission.getState(), telemetry.getState());
        assertEquals(mission.getPhase(), telemetry.getPhase());
        assertEquals(mission.getControlOwner(), telemetry.getControlOwner());
        assertEquals(mission.getTargetId(), telemetry.getTargetId());
    }
}
