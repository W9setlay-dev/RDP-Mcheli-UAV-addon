package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class MissionQueueContractTest {
    @Test
    public void containsReportsActualMembershipAndEnqueueIsIdempotent() {
        MissionQueue queue = new MissionQueue();
        RDPMission mission = mission();

        assertFalse(queue.contains(mission));
        queue.enqueue(mission);
        queue.enqueue(mission);

        assertTrue(queue.contains(mission));
        assertEquals(1, queue.size());
        assertSame(mission, queue.poll());
        assertFalse(queue.contains(mission));
    }

    private static RDPMission mission() {
        return new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "switchblade", MissionType.RECONNAISSANCE,
                TargetSpec.point(0, 80, 0, TargetPriority.NORMAL),
                false, false, false, 64, 32, 0);
    }
}
