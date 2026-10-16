package net.vas.rdp.uav.debug;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DebugTimerTest {
    @Test
    public void preservesSubMillisecondDurationsAsFractionalDouble() throws Exception {
        double durationMillis = DebugTimer.nanosToMillis(250_000L);

        assertTrue(durationMillis > 0.0);
        assertEquals(0.25, durationMillis, 0.0);
        Method stopMillis = DebugTimer.class.getMethod("stopMillis");
        assertEquals(double.class, stopMillis.getReturnType());
        assertTrue(DebugTimer.start().stopMillis() > 0.0);
    }
}
