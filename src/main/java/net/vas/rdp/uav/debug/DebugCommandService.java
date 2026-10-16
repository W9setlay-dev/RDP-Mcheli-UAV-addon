package net.vas.rdp.uav.debug;

import net.vas.rdp.uav.RdpUavDebug;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DebugCommandService {
    private static final Object CAPTURE_LOCK = new Object();
    private static CaptureSink activeCapture;

    public List<String> execute(String[] arguments, boolean authorized, File logsDirectory) {
        List<String> output = new ArrayList<>();
        if (!authorized) {
            output.add("Debug logging requires operator permission.");
            return output;
        }
        if (arguments == null || arguments.length == 0) {
            output.add(usage());
            return output;
        }

        String action = arguments[0].toLowerCase(Locale.ROOT);
        if (DebugChannel.COMMAND.isEnabled()) {
            DebugChannel.COMMAND.info(DebugContext.none(),
                    "DEBUG_COMMAND action=" + DebugText.safeValue(action)
                            + " argumentCount=" + arguments.length
                            + " authorized=" + authorized);
        }
        switch (action) {
            case "on":
                applyPreset(DebugConfig.Preset.FULL, output);
                return output;
            case "off":
                applyPreset(DebugConfig.Preset.OFF, output);
                return output;
            case "status":
                status(output);
                return output;
            case "list":
                listChannels(output);
                return output;
            case "preset":
                preset(arguments, output);
                return output;
            case "channel":
                channel(arguments, output);
                return output;
            case "capture":
                capture(arguments, logsDirectory, output);
                return output;
            case "dump":
                dump(arguments, logsDirectory, output);
                return output;
            case "emergency":
                emergency(logsDirectory, output);
                return output;
            case "reload":
                try {
                    RdpUavDebug.reload();
                    output.add("Debug config reloaded.");
                } catch (RuntimeException failure) {
                    output.add("Debug config reload failed: " + message(failure));
                }
                return output;
            default:
                output.add(usage());
                return output;
        }
    }

    private void status(List<String> output) {
        DebugHub hub = DebugHub.getInstance();
        DebugConfig config = RdpUavDebug.getFrameworkConfig();
        output.add("RDP-UAV Debug master=" + hub.isMasterEnabled()
                + " preset=" + (config == null ? "UNKNOWN" : config.getPreset())
                + " sinks=" + String.join(",", hub.activeSinkIds()));
        for (DebugChannel channel : DebugChannel.values()) {
            output.add(channel.getId() + " enabled=" + channel.isEnabled()
                    + " level=" + hub.getChannelLevel(channel) + " dropped=0");
        }
    }

    private void listChannels(List<String> output) {
        for (DebugChannel channel : DebugChannel.values()) {
            output.add(channel.getId() + " enabled=" + channel.isEnabled()
                    + " level=" + DebugHub.getInstance().getChannelLevel(channel)
                    + " - " + channel.getDescription());
        }
    }

    private void preset(String[] arguments, List<String> output) {
        if (arguments.length < 2) {
            output.add("Usage: /rdpuav debug preset <OFF|LIGHT|MISSION|NETWORK|VISION|FULL|TRACE>");
            return;
        }
        try {
            applyPreset(DebugConfig.Preset.valueOf(arguments[1].toUpperCase(Locale.ROOT)), output);
        } catch (IllegalArgumentException exception) {
            output.add("Unknown debug preset: " + arguments[1]);
        }
    }

    private void applyPreset(DebugConfig.Preset preset, List<String> output) {
        try {
            RdpUavDebug.applyPreset(preset);
            output.add("Debug preset applied: " + preset.name());
        } catch (RuntimeException failure) {
            output.add("Debug preset failed: " + message(failure));
        }
    }

    private void channel(String[] arguments, List<String> output) {
        if (arguments.length < 3) {
            output.add("Usage: /rdpuav debug channel <id> on|off|level <LEVEL>");
            return;
        }
        DebugChannel channel = findChannel(arguments[1]);
        if (channel == null) {
            output.add("Unknown debug channel: " + arguments[1]);
            return;
        }
        DebugHub hub = DebugHub.getInstance();
        String operation = arguments[2].toLowerCase(Locale.ROOT);
        if ("on".equals(operation) || "off".equals(operation)) {
            hub.setChannelEnabled(channel, "on".equals(operation));
            output.add(channel.getId() + " enabled=" + channel.isEnabled());
            return;
        }
        if ("level".equals(operation) && arguments.length >= 4) {
            try {
                DebugLevel level = DebugLevel.valueOf(arguments[3].toUpperCase(Locale.ROOT));
                hub.setChannelLevel(channel, level);
                output.add(channel.getId() + " level=" + level.name());
            } catch (IllegalArgumentException exception) {
                output.add("Unknown debug level: " + arguments[3]);
            }
            return;
        }
        output.add("Usage: /rdpuav debug channel <id> on|off|level <LEVEL>");
    }

    private void capture(String[] arguments, File logsDirectory, List<String> output) {
        if (arguments.length < 2) {
            output.add("Usage: /rdpuav debug capture start <filename>|stop");
            return;
        }
        if ("stop".equalsIgnoreCase(arguments[1])) {
            stopCapture(output);
            return;
        }
        if (!"start".equalsIgnoreCase(arguments[1]) || arguments.length < 3) {
            output.add("Usage: /rdpuav debug capture start <filename>|stop");
            return;
        }
        String filename = arguments[2].toLowerCase(Locale.ROOT).endsWith(".log")
                ? arguments[2] : arguments[2] + ".log";
        synchronized (CAPTURE_LOCK) {
            if (activeCapture != null) {
                output.add("A debug capture is already active.");
                return;
            }
            try {
                CaptureSink sink = new CaptureSink(logsDirectory, filename);
                DebugHub.getInstance().registerSink(sink);
                activeCapture = sink;
                output.add("Debug capture started: " + sink.getFile().getName());
            } catch (RuntimeException failure) {
                output.add("Debug capture could not start: " + message(failure));
            }
        }
    }

    private void stopCapture(List<String> output) {
        synchronized (CAPTURE_LOCK) {
            if (activeCapture == null) {
                output.add("No debug capture is active.");
                return;
            }
            CaptureSink sink = activeCapture;
            try {
                sink.flush();
                sink.close();
                DebugHub.getInstance().unregisterSink(CaptureSink.ID);
                activeCapture = null;
                output.add("Debug capture stopped: " + sink.getFile().getName());
            } catch (RuntimeException failure) {
                output.add("Debug capture could not stop: " + message(failure));
            }
        }
    }

    private void dump(String[] arguments, File logsDirectory, List<String> output) {
        try {
            List<DebugEvent> events;
            if (arguments.length >= 2) {
                DebugChannel channel = findChannel(arguments[1]);
                if (channel == null) {
                    output.add("Unknown debug channel: " + arguments[1]);
                    return;
                }
                events = DebugHub.getInstance().snapshot(channel, null, Integer.MAX_VALUE);
            } else {
                events = DebugHub.getInstance().snapshotAll(null, Integer.MAX_VALUE);
            }
            File target = DebugCaptureFiles.createTimestamped(
                    logsDirectory, "rdp-uav-debug-capture-");
            DebugCaptureFiles.write(target, events);
            output.add("Debug dump written: " + target.getName() + " events=" + events.size());
        } catch (RuntimeException failure) {
            output.add("Debug dump failed: " + message(failure));
        }
    }

    private void emergency(File logsDirectory, List<String> output) {
        try {
            DebugConfig config = RdpUavDebug.getFrameworkConfig();
            int capacity = config == null ? 64 : config.getRetentionEmergencyCapacity();
            List<DebugEvent> events = DebugHub.getInstance().emergencySnapshot(null, capacity);
            File target = DebugCaptureFiles.createTimestamped(
                    logsDirectory, "rdp-uav-debug-capture-");
            DebugCaptureFiles.write(target, events);
            output.add("Emergency capture written: " + target.getName()
                    + " events=" + events.size());
        } catch (RuntimeException failure) {
            output.add("Emergency capture failed: " + message(failure));
        }
    }

    private static DebugChannel findChannel(String id) {
        for (DebugChannel channel : DebugChannel.values()) {
            if (channel.getId().equalsIgnoreCase(id)) return channel;
        }
        return null;
    }

    private static String message(RuntimeException failure) {
        String detail = failure.getMessage();
        return detail == null || detail.trim().isEmpty()
                ? failure.getClass().getSimpleName() : detail;
    }

    private static String usage() {
        return "Usage: /rdpuav debug status|list|preset <name>|channel <id> on|off|level <LEVEL>"
                + "|capture start <filename>|stop|dump [channel]|emergency|reload";
    }
}
