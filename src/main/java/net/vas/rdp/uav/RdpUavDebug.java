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
    private static volatile boolean missionDiagnostics;
    private static volatile File configFile;

    private RdpUavDebug() { }

    /**
     * Reads the debug configuration from the mod config directory and initializes
     * the runtime toggles used by mission and flight diagnostics.
     *
     * @param configDir the Forge config root for the mod; the generated file is
     *                  stored as {@code rdp-uav-debug.cfg}
     */
    public static void load(File configDir) {
        File file = new File(configDir, "rdp-uav-debug.cfg");
        Configuration config = new Configuration(file);
        config.load();
        enabled = config.getBoolean("enabled", "debug", false,
                "Enable verbose RDP UAV diagnostics in latest.log.");
        missionDiagnostics = config.getBoolean("missionDiagnostics", "debug", false,
                "Enable bounded mission lifecycle, route, target, and vehicle telemetry.");
        if (config.hasChanged()) config.save();
        configFile = file;
        LOGGER.info("Debug logging initialized: enabled={} config={}", enabled, file.getPath());
    }

    /**
     * @return {@code true} when the general debug logger is active
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * @return {@code true} when the bounded mission telemetry stream is enabled
     */
    public static boolean isMissionDiagnosticsEnabled() {
        return missionDiagnostics;
    }

    /**
     * Enables or disables the main diagnostic logger and persists the setting when
     * a configuration file has already been created.
     *
     * @param value the new runtime state for the debug logger
     */
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

    /**
     * Enables or disables the bounded mission telemetry stream and persists the
     * toggle to the config file when available.
     *
     * @param value {@code true} to enable filtered mission lifecycle events
     */
    public static void setMissionDiagnostics(boolean value) {
        synchronized (LOCK) {
            missionDiagnostics = value;
            if (configFile != null) {
                Configuration config = new Configuration(configFile);
                config.load();
                config.get("debug", "missionDiagnostics", value,
                        "Enable bounded mission lifecycle, route, target, and vehicle telemetry.")
                        .set(value);
                config.save();
            }
        }
        LOGGER.info("Mission diagnostics {}", value ? "enabled" : "disabled");
    }

    /**
     * Reloads the current debug config without restarting the mod.
     */
    public static void reload() {
        File file = configFile;
        if (file == null) {
            LOGGER.warn("Debug logging cannot reload before mod pre-initialization");
            return;
        }
        load(file.getParentFile());
    }

    /**
     * Writes a plain-text debug entry for a subsystem when general diagnostics are
     * enabled.
     *
     * @param component the subsystem or feature label
     * @param message the message body to log
     */
    public static void debug(String component, String message) {
        if (enabled) LOGGER.info("[DEBUG][{}] {}", component, message);
    }

    /**
     * Writes a formatted debug entry when the general diagnostics flag is active.
     *
     * @param component the subsystem or feature label
     * @param format the message template; supports {@code {}} placeholders
     * @param arguments values applied to the template
     */
    public static void debug(String component, String format, Object... arguments) {
        if (enabled) LOGGER.info("[DEBUG][{}] {}", component, formatMessage(format, arguments));
    }

    /**
     * Emits a stable key/value event that can be filtered and correlated from
     * latest.log without requiring callers to hand-build format strings.
     *
     * @param component the subsystem or feature label
     * @param event the canonical event name to log
     * @param fields alternating key/value pairs to attach to the event
     */
    public static void event(String component, String event, Object... fields) {
        if (!enabled && !missionDiagnostics) return;
        StringJoiner values = new StringJoiner(" ");
        if (fields != null) {
            for (int i = 0; i + 1 < fields.length; i += 2) {
                values.add(fields[i] + "=" + fields[i + 1]);
            }
        }
        LOGGER.info("[DEBUG][{}] event={} {}", component, event, values.toString());
    }

    /**
     * Logs a warning for a subsystem without requiring the debug flag to be on.
     *
     * @param component the subsystem or feature label
     * @param message the warning message to log
     */
    public static void warn(String component, String message) {
        LOGGER.warn("[{}] {}", component, message);
    }

    /**
     * @return the runtime status string used by diagnostics and config screens
     */
    public static String status() {
        return "debug=" + enabled + " missionDiagnostics=" + missionDiagnostics + " config="
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
