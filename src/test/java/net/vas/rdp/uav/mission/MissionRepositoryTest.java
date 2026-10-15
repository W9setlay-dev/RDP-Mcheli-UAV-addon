package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class MissionRepositoryTest {
    @Test
    public void activeForAircraftSkipsEarlierTerminalMission() {
        UUID ownerId = UUID.randomUUID();
        UUID aircraftId = UUID.randomUUID();
        RDPMission terminal = mission(ownerId, aircraftId, 1L, MissionState.ABORTED);
        RDPMission active = mission(ownerId, aircraftId, 3L, MissionState.PLANNED);
        MissionRepository repository = new MissionRepository();
        repository.add(terminal);
        repository.add(active);

        assertSame(active, repository.activeForAircraft(aircraftId, ownerId));
    }

    @Test
    public void ownerScopedLookupDoesNotExposeAnotherOwnersMission() {
        UUID aircraftId = UUID.randomUUID();
        RDPMission terminal = mission(UUID.randomUUID(), aircraftId, 1L,
                MissionState.ABORTED);
        RDPMission active = mission(UUID.randomUUID(), aircraftId, 3L,
                MissionState.PLANNED);
        MissionRepository repository = new MissionRepository();
        repository.add(terminal);
        repository.add(active);

        assertNull(repository.activeForAircraft(aircraftId, UUID.randomUUID()));
        assertSame(active, repository.activeForAircraft(aircraftId));
    }

    private static RDPMission mission(UUID ownerId, UUID aircraftId, long createdTick,
                                     MissionState state) {
        return RDPMission.restore(UUID.randomUUID(), ownerId, aircraftId, "switchblade",
                MissionType.RECONNAISSANCE,
                TargetSpec.point(10.0D, 20.0D, 30.0D, TargetPriority.NORMAL),
                false, false, false, 256.0D, 64.0D, createdTick, state, null,
                createdTick, null, null);
    }
}
