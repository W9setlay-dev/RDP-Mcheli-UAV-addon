package net.vas.rdp.uav.client.vision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Finds tall saturated magenta features in pixels. It is a narrow visual
 * feature detector for tinted beacon-beam test scenes, not semantic
 * recognition of arbitrary Minecraft objects.
 */
public final class BeaconBeamDetector {
    private static final int MAX_DETECTIONS = 16;
    private static final int MAX_COLUMN_GAP = 1;
    private int[] topByColumn = new int[0];
    private int[] bottomByColumn = new int[0];
    private int[] hitsByColumn = new int[0];
    private volatile double minimumScore;

    public BeaconBeamDetector(double minimumScore) {
        setMinimumScore(minimumScore);
    }

    public void setMinimumScore(double minimumScore) {
        if (!finite(minimumScore) || minimumScore < 0.0D || minimumScore > 1.0D) {
            throw new IllegalArgumentException("Minimum detector score must be in [0,1]");
        }
        this.minimumScore = minimumScore;
    }

    public List<VisualDetection> detect(VisualFrame frame) {
        if (frame == null) throw new IllegalArgumentException("Visual frame is required");
        int width = frame.getWidth();
        int height = frame.getHeight();
        ensureScratch(width);
        for (int x = 0; x < width; x++) {
            topByColumn[x] = -1;
            bottomByColumn[x] = -1;
            hitsByColumn[x] = 0;
            int runStart = -1;
            int runEnd = -1;
            int hits = 0;
            int gap = 0;
            for (int y = 0; y < height; y++) {
                if (isMagentaBeamPixel(frame, x, y)) {
                    if (runStart < 0) runStart = y;
                    runEnd = y;
                    hits++;
                    gap = 0;
                } else if (runStart >= 0 && ++gap > 2) {
                    break;
                }
            }
            if (runStart >= 0 && runEnd - runStart + 1 >= minimumBeamHeight(height)) {
                topByColumn[x] = runStart;
                bottomByColumn[x] = runEnd;
                hitsByColumn[x] = hits;
            }
        }

        List<VisualDetection> detections = new ArrayList<>();
        int x = 0;
        while (x < width && detections.size() < MAX_DETECTIONS) {
            while (x < width && topByColumn[x] < 0) x++;
            if (x >= width) break;
            int left = x;
            int right = x;
            int top = topByColumn[x];
            int bottom = bottomByColumn[x];
            int pixelHits = hitsByColumn[x];
            int gap = 0;
            x++;
            while (x < width && gap <= MAX_COLUMN_GAP) {
                if (topByColumn[x] < 0) {
                    gap++;
                    x++;
                    continue;
                }
                gap = 0;
                right = x;
                top = Math.min(top, topByColumn[x]);
                bottom = Math.max(bottom, bottomByColumn[x]);
                pixelHits += hitsByColumn[x];
                x++;
            }

            int boxWidth = right - left + 1;
            int boxHeight = bottom - top + 1;
            if (boxHeight < boxWidth * 2) continue;
            double density = pixelHits / (double) (boxWidth * boxHeight);
            double extent = Math.min(1.0D, boxHeight / (height * 0.25D));
            double score = clamp(0.65D * density + 0.35D * extent);
            if (score >= minimumScore) {
                detections.add(new VisualDetection(VisualDetection.BEACON_BEAM,
                        score, left / (double) width, top / (double) height,
                        boxWidth / (double) width, boxHeight / (double) height));
            }
        }
        return Collections.unmodifiableList(detections);
    }

    private void ensureScratch(int width) {
        if (topByColumn.length == width) return;
        topByColumn = new int[width];
        bottomByColumn = new int[width];
        hitsByColumn = new int[width];
    }

    private static boolean isMagentaBeamPixel(VisualFrame frame, int x, int y) {
        int red = frame.red(x, y);
        int green = frame.green(x, y);
        int blue = frame.blue(x, y);
        return frame.alpha(x, y) >= 128
                && red >= 128 && blue >= 128
                && green <= 112
                && red * 10 >= green * 16
                && blue * 10 >= green * 16;
    }

    private static int minimumBeamHeight(int frameHeight) {
        return Math.max(8, frameHeight / 12);
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
