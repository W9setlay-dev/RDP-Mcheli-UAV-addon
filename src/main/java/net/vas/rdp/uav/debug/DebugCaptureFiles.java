package net.vas.rdp.uav.debug;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class DebugCaptureFiles {
    private DebugCaptureFiles() {
    }

    static File createTimestamped(File logsDirectory, String prefix) {
        if (logsDirectory == null) throw new IllegalArgumentException("logsDirectory must not be null");
        if (prefix == null || !prefix.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid capture filename prefix.");
        }
        if (!logsDirectory.exists() && !logsDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create logs directory " + logsDirectory);
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT)
                .format(new Date());
        File candidate = new File(logsDirectory, prefix + timestamp + ".log");
        int suffix = 1;
        try {
            while (!candidate.createNewFile()) {
                candidate = new File(logsDirectory,
                        prefix + timestamp + "-" + suffix++ + ".log");
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create debug capture " + candidate, exception);
        }
        return candidate;
    }

    static void write(File target, List<DebugEvent> events) {
        if (target == null) throw new IllegalArgumentException("target must not be null");
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create capture directory " + parent);
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(target))) {
            for (DebugEvent event : events) {
                writer.write(formatForFile(event));
                writer.newLine();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write debug capture " + target, exception);
        }
    }

    static String formatForFile(DebugEvent event) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
                .format(new Date(event.getTimestampMillis()));
        String thread = Thread.currentThread().getName() + "/" + event.getLevel().name();
        return "[" + timestamp + "] [" + thread + "] [RDP-UAV/Debug] "
                + ConsoleSink.format(event);
    }
}
