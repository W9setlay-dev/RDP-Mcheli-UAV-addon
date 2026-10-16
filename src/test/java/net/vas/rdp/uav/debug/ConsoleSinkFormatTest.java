package net.vas.rdp.uav.debug;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConsoleSinkFormatTest {
    @Test
    public void format_eachLevelUsesCanonicalLevelAndChannelNames() {
        for (DebugLevel level : DebugLevel.values()) {
            DebugEvent event = event(level, "event=TEST value=1.250", DebugContext.none());

            assertEquals("[" + level.name() + "][MISSION] event=TEST value=1.250",
                    ConsoleSink.format(event));
        }
    }

    @Test
    public void format_contextContainsEveryNonNullFieldInStableOrder() {
        DebugContext context = new DebugContext(
                "mission-1", "aircraft-2", "target-3", "operator-4", -7, 123456789L);
        DebugEvent event = event(DebugLevel.INFO, "event=CONTEXT", context);

        assertEquals("[INFO][MISSION] event=CONTEXT [ctx: mission=mission-1 aircraft=aircraft-2 "
                + "target=target-3 op=operator-4 dim=-7 tick=123456789]",
                ConsoleSink.format(event));
    }

    @Test
    public void format_contextOmitsNullIdentifiers() {
        DebugContext context = new DebugContext(null, "aircraft-2", null, null, 0, 0L);
        DebugEvent event = event(DebugLevel.DEBUG, "event=PARTIAL", context);

        assertEquals("[DEBUG][MISSION] event=PARTIAL [ctx: aircraft=aircraft-2 dim=0 tick=0]",
                ConsoleSink.format(event));
    }

    @Test
    public void numericFormatters_followLocaleIndependentPrecisionRules() {
        assertEquals("115.947", ConsoleSink.formatDouble(115.9472));
        assertEquals("-22.493", ConsoleSink.formatFloat(-22.4926f));
        assertEquals("0.125", ConsoleSink.formatDurationMillis(0.125));
        assertEquals("123456789", ConsoleSink.formatLong(123456789L));
        assertEquals("-7", ConsoleSink.formatInt(-7));
        assertEquals("true", ConsoleSink.formatBoolean(true));
    }

    @Test
    public void format_quotesContextValuesContainingWhitespace() {
        DebugContext context = new DebugContext(
                "mission alpha", null, null, null, 0, 1L);

        assertTrue(ConsoleSink.format(event(DebugLevel.INFO, "event=SPACE", context))
                .contains("mission=\"mission alpha\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void format_messageWithReservedCharacters_isRejected() {
        ConsoleSink.format(event(DebugLevel.ERROR, "event=BAD\nvalue=1", DebugContext.none()));
    }

    private static DebugEvent event(DebugLevel level, String message, DebugContext context) {
        return new DebugEvent(DebugChannel.MISSION, level, message, context, 1000L, null);
    }
}
