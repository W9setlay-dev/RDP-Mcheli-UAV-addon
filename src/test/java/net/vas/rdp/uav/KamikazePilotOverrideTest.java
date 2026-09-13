package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.KamikazePilotOverride;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KamikazePilotOverrideTest {
    @Test
    public void lockCanBeReplacedAndCleared() {
        UUID aircraft = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        KamikazePilotOverride.lock(aircraft, first);
        assertEquals(first, KamikazePilotOverride.targetFor(aircraft));
        KamikazePilotOverride.lock(aircraft, second);
        assertEquals(second, KamikazePilotOverride.targetFor(aircraft));
        assertEquals(second, KamikazePilotOverride.clear(aircraft));
        assertNull(KamikazePilotOverride.targetFor(aircraft));
    }
}
