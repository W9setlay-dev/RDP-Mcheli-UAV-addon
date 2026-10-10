package net.vas.rdp.uav.client.vision;

/** Maps top-left-origin normalized image coordinates into a GUI feed viewport. */
public final class DetectionViewportMapper {
    private DetectionViewportMapper() { }

    public static ScreenRect map(VisualDetection detection, int x, int y,
                                 int width, int height) {
        if (detection == null || width <= 0 || height <= 0) return null;

        int left = x + floorPixel(detection.getX() * width);
        int right = x + ceilPixel((detection.getX() + detection.getWidth()) * width);
        int top = y + floorPixel(detection.getY() * height);
        int bottom = y + ceilPixel((detection.getY() + detection.getHeight()) * height);

        left = Math.max(x, Math.min(x + width, left));
        right = Math.max(x, Math.min(x + width, right));
        top = Math.max(y, Math.min(y + height, top));
        bottom = Math.max(y, Math.min(y + height, bottom));
        if (right <= left || bottom <= top) return null;
        return new ScreenRect(left, top, right - left, bottom - top);
    }

    private static int floorPixel(double value) {
        double nearest = Math.rint(value);
        return (int) (Math.abs(value - nearest) < 1.0E-9D
                ? nearest : Math.floor(value));
    }

    private static int ceilPixel(double value) {
        double nearest = Math.rint(value);
        return (int) (Math.abs(value - nearest) < 1.0E-9D
                ? nearest : Math.ceil(value));
    }

    public static final class ScreenRect {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        ScreenRect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }
}
