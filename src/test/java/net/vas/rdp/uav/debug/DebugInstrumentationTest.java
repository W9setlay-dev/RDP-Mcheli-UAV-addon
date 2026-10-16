package net.vas.rdp.uav.debug;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class DebugInstrumentationTest {
    private final DebugHub hub = DebugHub.getInstance();
    private final Map<DebugChannel, Boolean> originalEnabled =
            new EnumMap<>(DebugChannel.class);
    private final Map<DebugChannel, DebugLevel> originalLevels =
            new EnumMap<>(DebugChannel.class);
    private final AtomicInteger acceptedEvents = new AtomicInteger();
    private final CountingSink sink = new CountingSink("instrumentation-" + UUID.randomUUID());
    private boolean originalMasterEnabled;

    @Before
    public void configureHub() {
        originalMasterEnabled = hub.isMasterEnabled();
        for (DebugChannel channel : DebugChannel.values()) {
            originalEnabled.put(channel, channel.isEnabled());
            originalLevels.put(channel, hub.getChannelLevel(channel));
            hub.setChannelEnabled(channel, false);
            hub.setChannelLevel(channel, DebugLevel.DEBUG);
        }
        hub.setMasterEnabled(true);
        hub.registerSink(sink);
    }

    @After
    public void restoreHub() {
        hub.unregisterSink(sink.id());
        for (DebugChannel channel : DebugChannel.values()) {
            hub.setChannelEnabled(channel, originalEnabled.get(channel));
            hub.setChannelLevel(channel, originalLevels.get(channel));
        }
        hub.setMasterEnabled(originalMasterEnabled);
    }

    @Test
    public void cachedChannelGuardPreventsFormattingUntilEnabled_forEveryChannel() {
        for (DebugChannel channel : DebugChannel.values()) {
            acceptedEvents.set(0);
            AtomicInteger formattingAttempts = new AtomicInteger();
            if (channel.isEnabled()) {
                formattingAttempts.incrementAndGet();
                channel.debug(DebugContext.none(), String.format(Locale.ROOT,
                        "guard_probe channel=%s", channel.getId()));
            }
            assertEquals(channel.getId(), 0, formattingAttempts.get());
            assertEquals(channel.getId(), 0, acceptedEvents.get());

            hub.setChannelEnabled(channel, true);
            if (channel.isEnabled()) {
                formattingAttempts.incrementAndGet();
                channel.debug(DebugContext.none(), String.format(Locale.ROOT,
                        "guard_probe channel=%s", channel.getId()));
            }
            assertEquals(channel.getId(), 1, formattingAttempts.get());
            assertEquals(channel.getId(), 1, acceptedEvents.get());
        }
    }

    @Test
    public void safeValue_removesCharactersReservedByTheConsoleFormat() {
        assertEquals("unsafe__value_", DebugText.safeValue("unsafe[\nvalue]"));
    }

    private final class CountingSink implements DebugSink {
        private final String id;

        private CountingSink(String id) {
            this.id = id;
        }

        @Override
        public void accept(DebugEvent event) {
            acceptedEvents.incrementAndGet();
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
