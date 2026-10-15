package net.vas.rdp.uav.client;

import java.util.List;
import java.util.UUID;

/** Pure projection and hit-testing helpers for the client contact overlay. */
public final class TargetOverlayGeometry {
    private TargetOverlayGeometry() { }

    public static ScreenBox projectBounds(UUID targetId, String label,
                                          double minX, double minY, double minZ,
                                          double maxX, double maxY, double maxZ,
                                          float[] modelView, float[] projection,
                                          int viewportX, int viewportY,
                                          int viewportWidth, int viewportHeight,
                                          int scaledWidth, int scaledHeight) {
        if (targetId == null || modelView == null || modelView.length < 16
                || projection == null || projection.length < 16
                || viewportWidth <= 0 || viewportHeight <= 0
                || scaledWidth <= 0 || scaledHeight <= 0
                || !finite(minX, minY, minZ, maxX, maxY, maxZ)) {
            return null;
        }

        double left = Double.POSITIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        double nearestDepth = Double.POSITIVE_INFINITY;
        int projectedCorners = 0;
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    double worldX = x == 0 ? minX : maxX;
                    double worldY = y == 0 ? minY : maxY;
                    double worldZ = z == 0 ? minZ : maxZ;
                    ScreenPoint point = projectPoint(worldX, worldY, worldZ,
                            modelView, projection, viewportX, viewportY,
                            viewportWidth, viewportHeight, scaledWidth, scaledHeight);
                    if (point == null) continue;
                    left = Math.min(left, point.x);
                    top = Math.min(top, point.y);
                    right = Math.max(right, point.x);
                    bottom = Math.max(bottom, point.y);
                    nearestDepth = Math.min(nearestDepth, point.depth);
                    projectedCorners++;
                }
            }
        }
        if (projectedCorners == 0) return null;

        left = clamp(left, scaledWidth);
        right = clamp(right, scaledWidth);
        top = clamp(top, scaledHeight);
        bottom = clamp(bottom, scaledHeight);
        if (right <= left || bottom <= top) return null;
        return new ScreenBox(targetId, label, (int) Math.floor(left),
                (int) Math.floor(top), (int) Math.ceil(right),
                (int) Math.ceil(bottom), (float) nearestDepth);
    }

    public static ScreenBox selectAt(List<ScreenBox> boxes, int x, int y) {
        if (boxes == null) return null;
        ScreenBox best = null;
        for (ScreenBox candidate : boxes) {
            if (candidate == null || !candidate.contains(x, y)) continue;
            if (best == null || candidate.area() < best.area()
                    || candidate.area() == best.area()
                    && (candidate.depth < best.depth
                    || Float.compare(candidate.depth, best.depth) == 0
                    && candidate.targetId.toString().compareTo(best.targetId.toString()) < 0)) {
                best = candidate;
            }
        }
        return best;
    }

    private static ScreenPoint projectPoint(double x, double y, double z,
                                            float[] modelView, float[] projection,
                                            int viewportX, int viewportY,
                                            int viewportWidth, int viewportHeight,
                                            int scaledWidth, int scaledHeight) {
        double modelX = modelView[0] * x + modelView[4] * y
                + modelView[8] * z + modelView[12];
        double modelY = modelView[1] * x + modelView[5] * y
                + modelView[9] * z + modelView[13];
        double modelZ = modelView[2] * x + modelView[6] * y
                + modelView[10] * z + modelView[14];
        double modelW = modelView[3] * x + modelView[7] * y
                + modelView[11] * z + modelView[15];
        double clipX = projection[0] * modelX + projection[4] * modelY
                + projection[8] * modelZ + projection[12] * modelW;
        double clipY = projection[1] * modelX + projection[5] * modelY
                + projection[9] * modelZ + projection[13] * modelW;
        double clipZ = projection[2] * modelX + projection[6] * modelY
                + projection[10] * modelZ + projection[14] * modelW;
        double clipW = projection[3] * modelX + projection[7] * modelY
                + projection[11] * modelZ + projection[15] * modelW;
        if (!finite(clipX, clipY, clipZ, clipW) || clipW <= 1.0E-7D) return null;

        double normalizedX = clipX / clipW;
        double normalizedY = clipY / clipW;
        double normalizedZ = clipZ / clipW;
        if (!finite(normalizedX, normalizedY, normalizedZ)
                || normalizedZ < -1.0D || normalizedZ > 1.0D) return null;
        double windowX = viewportX + (normalizedX + 1.0D) * viewportWidth * 0.5D;
        double windowY = viewportY + (normalizedY + 1.0D) * viewportHeight * 0.5D;
        double screenX = (windowX - viewportX) * scaledWidth / viewportWidth;
        double screenY = scaledHeight
                - (windowY - viewportY) * scaledHeight / viewportHeight;
        if (!finite(screenX, screenY)) return null;
        return new ScreenPoint(screenX, screenY, normalizedZ * 0.5D + 0.5D);
    }

    private static boolean finite(double first, double second) {
        return Double.isFinite(first) && Double.isFinite(second);
    }

    private static boolean finite(double first, double second, double third) {
        return finite(first, second) && Double.isFinite(third);
    }

    private static boolean finite(double first, double second,
                                  double third, double fourth) {
        return finite(first, second, third) && Double.isFinite(fourth);
    }

    private static boolean finite(double first, double second, double third,
                                  double fourth, double fifth, double sixth) {
        return finite(first, second, third, fourth)
                && Double.isFinite(fifth) && Double.isFinite(sixth);
    }

    private static double clamp(double value, double maximum) {
        return Math.max(0.0, Math.min(maximum, value));
    }

    private static final class ScreenPoint {
        private final double x;
        private final double y;
        private final double depth;

        private ScreenPoint(double x, double y, double depth) {
            this.x = x;
            this.y = y;
            this.depth = depth;
        }
    }

    public static final class ScreenBox {
        private final UUID targetId;
        private final String label;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final float depth;

        public ScreenBox(UUID targetId, String label, int left, int top,
                         int right, int bottom, float depth) {
            if (targetId == null || right < left || bottom < top
                    || !Float.isFinite(depth)) {
                throw new IllegalArgumentException("Invalid projected target box");
            }
            this.targetId = targetId;
            this.label = label == null ? "" : label;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.depth = depth;
        }

        public UUID getTargetId() { return targetId; }
        public String getLabel() { return label; }
        public int getLeft() { return left; }
        public int getTop() { return top; }
        public int getRight() { return right; }
        public int getBottom() { return bottom; }

        public boolean contains(int x, int y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }

        private long area() {
            return (long) (right - left) * (long) (bottom - top);
        }
    }
}
