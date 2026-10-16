package net.vas.rdp.uav.debug;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;
import java.util.Objects;

public final class ConsoleSink implements DebugSink {
    private static final String ID = "console";

    private final Logger logger;

    public ConsoleSink() {
        this(LogManager.getLogger("RDP-UAV/Debug"));
    }

    ConsoleSink(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void accept(DebugEvent event) {
        Objects.requireNonNull(event, "event");
        String message = format(event);
        switch (event.getLevel()) {
            case TRACE:
                log(org.apache.logging.log4j.Level.TRACE, message, event.getCause());
                break;
            case DEBUG:
                log(org.apache.logging.log4j.Level.DEBUG, message, event.getCause());
                break;
            case INFO:
                log(org.apache.logging.log4j.Level.INFO, message, event.getCause());
                break;
            case WARN:
                log(org.apache.logging.log4j.Level.WARN, message, event.getCause());
                break;
            case ERROR:
                log(org.apache.logging.log4j.Level.ERROR, message, event.getCause());
                break;
            default:
                throw new IllegalStateException("Unhandled debug level: " + event.getLevel());
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public String id() {
        return ID;
    }

    static String format(DebugEvent event) {
        String message = event.getMessage();
        if (message.indexOf('\n') >= 0 || message.indexOf('\r') >= 0
                || message.indexOf('[') >= 0 || message.indexOf(']') >= 0
                || !message.equals(message.trim())) {
            throw new IllegalArgumentException("Debug message violates the log format");
        }

        StringBuilder formatted = new StringBuilder()
                .append('[').append(event.getLevel().name()).append(']')
                .append('[').append(event.getChannel().name()).append("] ")
                .append(message);
        appendContext(formatted, event.getContext());
        return formatted.toString();
    }

    private static void appendContext(StringBuilder formatted, DebugContext context) {
        if (context == DebugContext.none()) return;
        StringBuilder fields = new StringBuilder();
        appendField(fields, "mission", context.getMissionId());
        appendField(fields, "aircraft", context.getAircraftId());
        appendField(fields, "target", context.getTargetId());
        appendField(fields, "op", context.getOperatorId());
        appendField(fields, "dim", String.valueOf(context.getDimension()));
        appendField(fields, "tick", String.valueOf(context.getTick()));
        if (fields.length() > 0) {
            formatted.append(" [ctx: ").append(fields).append(']');
        }
    }

    private static void appendField(StringBuilder fields, String name, String value) {
        if (value == null) return;
        if (fields.length() > 0) fields.append(' ');
        fields.append(name).append('=').append(quoteIfNeeded(value));
    }

    private static String quoteIfNeeded(String value) {
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
                || value.indexOf('[') >= 0 || value.indexOf(']') >= 0) {
            throw new IllegalArgumentException("Debug context violates the log format");
        }
        if (value.indexOf(' ') < 0 && value.indexOf('\t') < 0) return value;
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private void log(org.apache.logging.log4j.Level level, String message, Throwable cause) {
        if (cause == null) {
            logger.log(level, message);
        } else {
            logger.log(level, message, cause);
        }
    }

    public static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    public static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    public static String formatDurationMillis(double millis) {
        return String.format(Locale.ROOT, "%.3f", millis);
    }

    public static String formatLong(long value) {
        return String.valueOf(value);
    }

    public static String formatInt(int value) {
        return String.valueOf(value);
    }

    public static String formatBoolean(boolean value) {
        return String.valueOf(value);
    }
}
