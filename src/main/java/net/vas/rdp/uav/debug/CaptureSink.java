package net.vas.rdp.uav.debug;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class CaptureSink implements DebugSink {
    public static final String ID = "capture";

    private final File file;
    private BufferedWriter writer;

    public CaptureSink(File logsDirectory, String filename) {
        if (logsDirectory == null) throw new IllegalArgumentException("logsDirectory must not be null");
        if (filename == null || !filename.matches("[A-Za-z0-9._-]+")
                || filename.contains("..")) {
            throw new IllegalArgumentException("Capture filename must be a simple file name.");
        }
        File normalizedLogs = logsDirectory.getAbsoluteFile();
        this.file = new File(normalizedLogs, filename).getAbsoluteFile();
        if (!normalizedLogs.equals(file.getParentFile())) {
            throw new IllegalArgumentException("Capture file must be inside the logs directory.");
        }
        try {
            if (!normalizedLogs.exists() && !normalizedLogs.mkdirs()) {
                throw new IllegalStateException("Could not create logs directory " + normalizedLogs);
            }
            if (file.exists()) {
                throw new IllegalArgumentException("Capture file already exists: " + file.getName());
            }
            writer = new BufferedWriter(new FileWriter(file, false));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not open capture file " + file, exception);
        }
    }

    @Override
    public synchronized void accept(DebugEvent event) {
        if (writer == null) throw new IllegalStateException("Capture sink is closed.");
        try {
            writer.write(DebugCaptureFiles.formatForFile(event));
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write capture file " + file, exception);
        }
    }

    @Override
    public synchronized void flush() {
        if (writer == null) return;
        try {
            writer.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not flush capture file " + file, exception);
        }
    }

    public synchronized void close() {
        if (writer == null) return;
        try {
            writer.close();
            writer = null;
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not close capture file " + file, exception);
        }
    }

    public File getFile() {
        return file;
    }

    @Override
    public String id() {
        return ID;
    }
}
