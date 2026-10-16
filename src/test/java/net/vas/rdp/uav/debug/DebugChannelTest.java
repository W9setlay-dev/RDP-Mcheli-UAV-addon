package net.vas.rdp.uav.debug;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DebugChannelTest {
    @Test
    public void enabledFlagIsPlainBooleanReadDirectly() throws Exception {
        Field enabled = DebugChannel.class.getDeclaredField("enabled");
        java.lang.reflect.Method isEnabled = DebugChannel.class.getMethod("isEnabled");
        assertEquals(boolean.class, enabled.getType());
        assertFalse(Modifier.isVolatile(enabled.getModifiers()));
        assertEquals(boolean.class, isEnabled.getReturnType());
        assertEquals(0, isEnabled.getParameterTypes().length);
        assertFalse(Modifier.isSynchronized(isEnabled.getModifiers()));

        enabled.setAccessible(true);
        boolean original = enabled.getBoolean(DebugChannel.FLIGHT);
        try {
            enabled.setBoolean(DebugChannel.FLIGHT, true);
            assertTrue(DebugChannel.FLIGHT.isEnabled());
            enabled.setBoolean(DebugChannel.FLIGHT, false);
            assertFalse(DebugChannel.FLIGHT.isEnabled());
        } finally {
            enabled.setBoolean(DebugChannel.FLIGHT, original);
        }
    }

    @Test
    public void masterEnabled_setterUpdatesHubState() {
        DebugHub hub = DebugHub.getInstance();
        boolean original = hub.isMasterEnabled();
        try {
            hub.setMasterEnabled(true);
            assertTrue(hub.isMasterEnabled());
        } finally {
            hub.setMasterEnabled(original);
        }
    }
}
