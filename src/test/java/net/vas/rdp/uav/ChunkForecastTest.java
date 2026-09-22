package net.vas.rdp.uav;

import net.vas.rdp.uav.world.ChunkForecast;
import net.vas.rdp.uav.world.ChunkObservation;
import net.vas.rdp.uav.world.ChunkState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChunkForecastTest {
    @Test
    public void loadedChunkContinuesWithoutLoadRequest() {
        ChunkObservation observation = new ChunkObservation(0, 2, 3, 10,
                ChunkState.LOADED);
        assertEquals(ChunkForecast.Decision.CONTINUE,
                ChunkForecast.decide(observation, false));
    }

    @Test
    public void unloadedChunkWaitsOnlyWhenCapabilityAllowsLoading() {
        ChunkObservation observation = new ChunkObservation(0, 2, 3, 10,
                ChunkState.UNLOADED);
        assertEquals(ChunkForecast.Decision.WAIT,
                ChunkForecast.decide(observation, true));
        assertEquals(ChunkForecast.Decision.REPLAN,
                ChunkForecast.decide(observation, false));
    }
}
