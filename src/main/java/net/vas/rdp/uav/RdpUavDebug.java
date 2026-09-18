package net.vas.rdp.uav;

import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Locale;
import java.util.IllegalFormatException;
import java.util.StringJoiner;

/**
 * Central, runtime-toggleable diagnostics for the server-side addon.
 *
 * <p>Debug logging is deliberately disabled by default. Every debug line has
 * the same prefix and subsystem tag so it can be filtered from latest.log.</p>
 */
public final class RdpUavDebug {
    private static final Logger LOGGER = LogManager.getLogger("RDP-UAV/Debug");
    private static final Object LOCK = new Object();
    private static volatile boolean enabled;
    private static volatile File configFile;

    private RdpUavDebug() { }

    public static void load(File configDir) {
        File file = new File(configDir, "rdp-uav-debug.cfg");
        Configuration config = new Configuration(file);
        config.load();
        enabled = config.getBoolean("enabled", "debug", false,
                "Enable verbose RDP UAV diagnostics in latest.log.");
        if (config.hasChanged()) config.save();
        configFile = file;
        LOGGER.info("Debug logging initialized: enabled={} config={}", enabled, file.getPath());
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        synchronized (LOCK) {
            enabled = value;
            if (configFile != null) {
                Configuration config = new Configuration(configFile);
                config.load();
                config.get("debug", "enabled", value,
                        "Enable verbose RDP UAV diagnostics in latest.log.").set(value);
                config.save();
            }
        }
        LOGGER.info("Debug logging {}", value ? "enabled" : "disabled");
    }

    public static void reload() {
        File file = configFile;
        if (file == null) {
            LOGGER.warn("Debug logging cannot reload before mod pre-initialization");
            return;
        }
        load(file.getParentFile());
    }

    public static void debug(String component, String message) {
        if (enabled) LOGGER.info("[DEBUG][{}] {}", component, message);
    }

    public static void debug(String component, String format, Object... arguments) {
        if (enabled) LOGGER.info("[DEBUG][{}] {}", component, formatMessage(format, arguments));
    }

    /**
     * Emits a stable key/value event that can be filtered and correlated from
     * latest.log without requiring callers to hand-build format strings.
     */
    public static void event(String component, String event, Object... fields) {
        if (!enabled) return;
        StringJoiner values = new StringJoiner(" ");
        if (fields != null) {
            for (int i = 0; i + 1 < fields.length; i += 2) {
                values.add(String.valueOf(fields[i]) + "=" + String.valueOf(fields[i + 1]));
            }
        }
        LOGGER.info("[DEBUG][{}] event={} {}", component, event, values.toString());
    }

    public static void warn(String component, String message) {
        LOGGER.warn("[{}] {}", component, message);
    }

    public static String status() {
        return "debug=" + enabled + " config="
                + (configFile == null ? "<not loaded>" : configFile.getPath());
    }

    private static String formatMessage(String format, Object[] arguments) {
        if (format == null) return "";
        if (arguments == null || arguments.length == 0) return format;
        String value = format;
        for (Object argument : arguments) {
            int marker = value.indexOf("{}");
            if (marker < 0) break;
            String replacement = String.valueOf(argument).replace("$", "\\$");
            value = value.substring(0, marker) + replacement + value.substring(marker + 2);
        }
        if (value.equals(format) && format.indexOf('%') >= 0) {
            try { return String.format(Locale.ROOT, format, arguments); }
            catch (IllegalFormatException ignored) { }
        }
        return value;
    }
}
