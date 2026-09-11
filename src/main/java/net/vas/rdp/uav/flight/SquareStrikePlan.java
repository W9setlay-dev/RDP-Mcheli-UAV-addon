package net.vas.rdp.uav.flight;

import com.norwood.mcheli.wingman.mission.MissionNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic boustrophedon pattern for a coordinate-square strike.
 *
 * <p>This class only creates native WingMan mission nodes. It never moves an
 * aircraft or treats node creation as evidence that a pass was completed.</p>
 */
public final class SquareStrikePlan {
    private final SquareArea area;
    private final double centerX;
    private final double altitude;
    private final double centerZ;
    private final double width;
    private final double length;
    private final double headingDegrees;
    private final double passSpacing;
    private final double attackRadius;
    private final int strikePasses;

    public SquareStrikePlan(double centerX, double altitude, double centerZ,
                            double width, double length, double headingDegrees,
                            double passSpacing, double attackRadius, int strikePasses) {
        if (!finite(centerX) || !finite(altitude) || !finite(centerZ)
                || !finite(width) || !finite(length) || !finite(headingDegrees)
                || !finite(passSpacing) || !finite(attackRadius)
                || width <= 0 || length <= 0 || passSpacing <= 0
                || attackRadius <= 0 || strikePasses <= 0) {
            throw new IllegalArgumentException("Invalid square strike parameters");
        }
        if (passSpacing > length) {
            throw new IllegalArgumentException("Pass spacing must not exceed square length");
        }
        this.centerX = centerX;
        this.altitude = altitude;
        this.centerZ = centerZ;
        this.width = width;
        this.length = length;
        this.headingDegrees = headingDegrees;
        this.passSpacing = passSpacing;
        this.attackRadius = attackRadius;
        this.strikePasses = strikePasses;
        this.area = new SquareArea(centerX, centerZ, width, length);
    }

    public List<MissionNode> createNodes() {
        List<MissionNode> nodes = new ArrayList<MissionNode>();
        int rows = (int) Math.ceil(length / passSpacing);
        double heading = Math.toRadians(headingDegrees);
        double alongX = Math.cos(heading);
        double alongZ = Math.sin(heading);
        double acrossX = -alongZ;
        double acrossZ = alongX;
        double startAcross = -length / 2.0D;
        double endAlong = width / 2.0D;
        double startAlong = -endAlong;

        for (int row = 0; row < rows; row++) {
            double across = Math.min(length / 2.0D, startAcross + row * passSpacing);
            boolean reverse = (row & 1) == 1;
            double firstAlong = reverse ? endAlong : startAlong;
            double secondAlong = reverse ? startAlong : endAlong;
            nodes.add(MissionNode.flyTo(pointX(firstAlong, across, alongX, acrossX),
                    altitude, pointZ(firstAlong, across, alongZ, acrossZ)));
            nodes.add(MissionNode.flyTo(pointX(secondAlong, across, alongX, acrossX),
                    altitude, pointZ(secondAlong, across, alongZ, acrossZ)));
            nodes.add(MissionNode.attack(attackRadius));
        }
        return Collections.unmodifiableList(nodes);
    }

    public int getStrikePasses() { return strikePasses; }

    public boolean contains(double x, double z) {
        return area.contains(x, z);
    }

    public double getMinX() { return area.minX(); }
    public double getMaxX() { return area.maxX(); }
    public double getMinZ() { return area.minZ(); }
    public double getMaxZ() { return area.maxZ(); }

    private double pointX(double along, double across, double alongX, double acrossX) {
        return centerX + along * alongX + across * acrossX;
    }

    private double pointZ(double along, double across, double alongZ, double acrossZ) {
        return centerZ + along * alongZ + across * acrossZ;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
