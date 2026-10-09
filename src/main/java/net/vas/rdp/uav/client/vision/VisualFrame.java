package net.vas.rdp.uav.client.vision;

import java.util.Arrays;

/** Owned RGBA image with a top-left image origin and immutable capture metadata. */
public final class VisualFrame {
    public static final int MAX_DIMENSION = 2048;
    private static final int CHANNELS = 4;

    private final long sequence;
    private final long capturedAtNanos;
    private final String sourceId;
    private final String cameraId;
    private final int width;
    private final int height;
    private final byte[] rgba;

    private VisualFrame(long sequence, long capturedAtNanos, String sourceId,
                        String cameraId, int width, int height, byte[] rgba,
                        boolean copyPixels) {
        if (sequence < 0L || capturedAtNanos < 0L
                || sourceId == null || sourceId.trim().isEmpty()
                || cameraId == null || cameraId.trim().isEmpty()
                || width <= 0 || height <= 0
                || width > MAX_DIMENSION || height > MAX_DIMENSION
                || rgba == null || rgba.length != (long) width * height * CHANNELS) {
            throw new IllegalArgumentException("Invalid visual frame");
        }
        this.sequence = sequence;
        this.capturedAtNanos = capturedAtNanos;
        this.sourceId = sourceId;
        this.cameraId = cameraId;
        this.width = width;
        this.height = height;
        this.rgba = copyPixels ? Arrays.copyOf(rgba, rgba.length) : rgba;
    }

    public static VisualFrame copyOf(long sequence, long capturedAtNanos,
                                     String sourceId, String cameraId,
                                     int width, int height, byte[] rgba) {
        return new VisualFrame(sequence, capturedAtNanos, sourceId, cameraId,
                width, height, rgba, true);
    }

    static VisualFrame takeOwnership(long sequence, long capturedAtNanos,
                                     String sourceId, String cameraId,
                                     int width, int height, byte[] rgba) {
        return new VisualFrame(sequence, capturedAtNanos, sourceId, cameraId,
                width, height, rgba, false);
    }

    public long getSequence() { return sequence; }
    public long getCapturedAtNanos() { return capturedAtNanos; }
    public String getSourceId() { return sourceId; }
    public String getCameraId() { return cameraId; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public int red(int x, int y) { return channel(x, y, 0); }
    public int green(int x, int y) { return channel(x, y, 1); }
    public int blue(int x, int y) { return channel(x, y, 2); }
    public int alpha(int x, int y) { return channel(x, y, 3); }

    byte[] ownedPixels() { return rgba; }

    private int channel(int x, int y, int channel) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IndexOutOfBoundsException("Pixel outside visual frame");
        }
        int glY = height - 1 - y;
        return rgba[(glY * width + x) * CHANNELS + channel] & 0xFF;
    }
}
