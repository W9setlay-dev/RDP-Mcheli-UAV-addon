package net.vas.rdp.uav.debug;

import java.util.Objects;

public final class DebugEvent {
    private final DebugChannel channel;
    private final DebugLevel level;
    private final String message;
    private final DebugContext context;
    private final long timestampMillis;
    private final Throwable cause;

    public DebugEvent(DebugChannel channel, DebugLevel level, String message, DebugContext context,
                      long timestampMillis, Throwable cause) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.level = Objects.requireNonNull(level, "level");
        this.message = Objects.requireNonNull(message, "message");
        this.context = context == null ? DebugContext.none() : context;
        this.timestampMillis = timestampMillis;
        this.cause = cause;
    }

    public DebugChannel getChannel() {
        return channel;
    }

    public DebugLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public DebugContext getContext() {
        return context;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public Throwable getCause() {
        return cause;
    }
}
