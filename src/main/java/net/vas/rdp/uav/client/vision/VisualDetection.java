package net.vas.rdp.uav.client.vision;

/** Immutable normalized image-space result; score is a feature-match score, not a probability. */
public final class VisualDetection {
    public static final String BEACON_BEAM = "minecraft:beacon_beam";

    private final String classId;
    private final double score;
    private final double x;
    private final double y;
    private final double width;
    private final double height;

    public VisualDetection(String classId, double score, double x, double y,
                           double width, double height) {
        if (classId == null || classId.trim().isEmpty()
                || !finite(score) || score < 0.0D || score > 1.0D
                || !finite(x) || !finite(y) || !finite(width) || !finite(height)
                || x < 0.0D || y < 0.0D || width <= 0.0D || height <= 0.0D
                || x + width > 1.000001D || y + height > 1.000001D) {
            throw new IllegalArgumentException("Invalid visual detection");
        }
        this.classId = classId;
        this.score = score;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public String getClassId() { return classId; }
    public double getScore() { return score; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
