package net.vas.rdp.uav.debug;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DebugHubTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final DebugHub hub = DebugHub.getInstance();

    @Before
    public void initializeForgeAndHub() throws Exception {
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, temporaryFolder.getRoot());
        hub.configure(config(2, 3));
        hub.setMasterEnabled(true);
    }

    @Test
    public void emit_masterDisabled_doesNotDispatchEvenErrors() {
        CountingSink sink = registerSink();
        hub.setMasterEnabled(false);

        hub.emit(event(DebugChannel.FLIGHT, DebugLevel.ERROR, "event=FAILED", 1L));

        assertEquals(0, sink.count.get());
    }

    @Test
    public void emit_disabledChannel_doesNotDispatchNonErrorEvents() {
        CountingSink sink = registerSink();
        hub.setChannelEnabled(DebugChannel.FLIGHT, false);

        hub.emit(event(DebugChannel.FLIGHT, DebugLevel.DEBUG, "event=DISABLED", 1L));

        assertEquals(0, sink.count.get());
    }

    @Test
    public void emit_errorLevel_dispatchesEvenWhenChannelIsDisabled() {
        CountingSink sink = registerSink();
        hub.setChannelEnabled(DebugChannel.FLIGHT, false);

        hub.emit(event(DebugChannel.FLIGHT, DebugLevel.ERROR, "event=ERROR", 1L));

        assertEquals(1, sink.count.get());
    }

    @Test
    public void emit_enabledChannel_dispatchesToRegisteredSink() {
        CountingSink sink = registerSink();

        hub.emit(event(DebugChannel.FLIGHT, DebugLevel.DEBUG, "event=ENABLED", 1L));

        assertEquals(1, sink.count.get());
    }

    @Test
    public void snapshot_perChannelRetention_respectsConfiguredCapacity() {
        hub.emit(event(DebugChannel.MISSION, DebugLevel.DEBUG, "event=ONE", 1L));
        hub.emit(event(DebugChannel.MISSION, DebugLevel.DEBUG, "event=TWO", 2L));
        hub.emit(event(DebugChannel.MISSION, DebugLevel.DEBUG, "event=THREE", 3L));
        hub.emit(event(DebugChannel.FLIGHT, DebugLevel.DEBUG, "event=OTHER", 4L));

        List<DebugEvent> retained = hub.snapshot(DebugChannel.MISSION, null, 10);

        assertEquals(2, retained.size());
        assertEquals("event=TWO", retained.get(0).getMessage());
        assertEquals("event=THREE", retained.get(1).getMessage());
        assertTrue(hub.snapshot(DebugChannel.FLIGHT, null, 10).get(0).getMessage()
                .equals("event=OTHER"));
    }

    @Test
    public void emergencySnapshot_eventsAcrossChannelsKeepsLastConfiguredCount() {
        hub.emit(event(DebugChannel.MISSION, DebugLevel.DEBUG, "event=ONE", 1L));
        hub.emit(event(DebugChannel.FLIGHT, DebugLevel.DEBUG, "event=TWO", 2L));
        hub.emit(event(DebugChannel.VISION, DebugLevel.DEBUG, "event=THREE", 3L));
        hub.emit(event(DebugChannel.NETWORK, DebugLevel.DEBUG, "event=FOUR", 4L));

        List<DebugEvent> retained = hub.emergencySnapshot(null, 10);

        assertEquals(3, retained.size());
        assertEquals("event=TWO", retained.get(0).getMessage());
        assertEquals("event=THREE", retained.get(1).getMessage());
        assertEquals("event=FOUR", retained.get(2).getMessage());
    }

    @Test
    public void snapshot_contextFilter_matchesAllSuppliedContextFields() {
        DebugContext context = new DebugContext("mission-a", null, null, null, 0, 12L);
        hub.emit(new DebugEvent(DebugChannel.MISSION, DebugLevel.DEBUG, "event=MATCH",
                context, 1L, null));
        hub.emit(new DebugEvent(DebugChannel.MISSION, DebugLevel.DEBUG, "event=MISS",
                context.withTick(13L), 2L, null));

        List<DebugEvent> retained = hub.snapshot(DebugChannel.MISSION, context, 10);

        assertEquals(1, retained.size());
        assertEquals("event=MATCH", retained.get(0).getMessage());
    }

    @Test
    public void registerSink_sameIdDoesNotRegisterDuplicateSink() {
        CountingSink original = registerSink();
        CountingSink duplicateId = new CountingSink(original.id());
        hub.registerSink(duplicateId);

        hub.emit(event(DebugChannel.MISSION, DebugLevel.DEBUG, "event=ONCE", 1L));

        assertEquals(1, original.count.get());
        assertEquals(0, duplicateId.count.get());
    }

    private CountingSink registerSink() {
        CountingSink sink = new CountingSink(UUID.randomUUID().toString());
        hub.registerSink(sink);
        return sink;
    }

    private DebugConfig config(int channelCapacity, int emergencyCapacity) {
        File configDir = temporaryFolder.getRoot();
        Configuration configuration = new Configuration(
                new File(configDir, "rdp-uav-debug.cfg"));
        configuration.load();
        configuration.getString("debug.preset", Configuration.CATEGORY_GENERAL, "FULL",
                "Test preset.", new String[] {
                        "OFF", "LIGHT", "MISSION", "NETWORK", "VISION", "FULL", "TRACE"
                });
        configuration.getInt("debug.retention.capacityPerChannel",
                Configuration.CATEGORY_GENERAL, channelCapacity, 1, 1000,
                "Test per-channel retention.");
        configuration.getInt("debug.retention.emergencyCapacity",
                Configuration.CATEGORY_GENERAL, emergencyCapacity, 1, 1000,
                "Test emergency retention.");
        configuration.save();
        return DebugConfig.load(configDir);
    }

    private static DebugEvent event(DebugChannel channel, DebugLevel level, String message,
                                    long timestamp) {
        return new DebugEvent(channel, level, message, DebugContext.none(), timestamp, null);
    }

    private static final class CountingSink implements DebugSink {
        private final String id;
        private final AtomicInteger count = new AtomicInteger();

        private CountingSink(String id) {
            this.id = id;
        }

        @Override
        public void accept(DebugEvent event) {
            count.incrementAndGet();
        }

        @Override
        public void flush() {
        }

        @Override
        public String id() {
            return id;
        }
    }
}
