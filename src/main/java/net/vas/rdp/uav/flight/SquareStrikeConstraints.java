package net.vas.rdp.uav.flight;

public final class SquareStrikeConstraints {
    private final SquareArea area;
    private final double minAltitude;
    private final double maxAltitude;
    private final SearchPattern searchPattern;
    private final TargetPolicy targetPolicy;
    private final WeaponPolicy weaponPolicy;
    private final int priority;
    private final long timeLimitTicks;
    private final int maxEngagements;
    private final int maxTargetCount;
    private final boolean returnAfterObjective;
    private final double rtbThreshold;

    public SquareStrikeConstraints(SquareArea area, double minAltitude, double maxAltitude,
                                   SearchPattern searchPattern, TargetPolicy targetPolicy,
                                   WeaponPolicy weaponPolicy, int priority, long timeLimitTicks,
                                   int maxEngagements, int maxTargetCount,
                                   boolean returnAfterObjective, double rtbThreshold) {
        if (area == null || searchPattern == null || targetPolicy == null || weaponPolicy == null
                || finite(minAltitude) || finite(maxAltitude) || minAltitude > maxAltitude
                || priority < 0 || timeLimitTicks <= 0L || maxEngagements < 0
                || maxTargetCount < 0 || rtbThreshold < 0.0D || rtbThreshold > 1.0D) {
            throw new IllegalArgumentException("Invalid square strike constraints");
        }
        this.area = area;
        this.minAltitude = minAltitude;
        this.maxAltitude = maxAltitude;
        this.searchPattern = searchPattern;
        this.targetPolicy = targetPolicy;
        this.weaponPolicy = weaponPolicy;
        this.priority = priority;
        this.timeLimitTicks = timeLimitTicks;
        this.maxEngagements = maxEngagements;
        this.maxTargetCount = maxTargetCount;
        this.returnAfterObjective = returnAfterObjective;
        this.rtbThreshold = rtbThreshold;
    }

    public boolean allowsAltitude(double altitude) {
        return altitude >= minAltitude && altitude <= maxAltitude;
    }

    public boolean allowsEngagement(int engagements, int targets) {
        return (maxEngagements == 0 || engagements < maxEngagements)
                && (maxTargetCount == 0 || targets < maxTargetCount);
    }

    public boolean timedOut(long createdTick, long currentTick) {
        return currentTick < createdTick || currentTick - createdTick >= timeLimitTicks;
    }

    public SquareArea getArea() { return area; }
    public double getMinAltitude() { return minAltitude; }
    public double getMaxAltitude() { return maxAltitude; }
    public SearchPattern getSearchPattern() { return searchPattern; }
    public TargetPolicy getTargetPolicy() { return targetPolicy; }
    public WeaponPolicy getWeaponPolicy() { return weaponPolicy; }
    public int getPriority() { return priority; }
    public long getTimeLimitTicks() { return timeLimitTicks; }
    public int getMaxEngagements() { return maxEngagements; }
    public int getMaxTargetCount() { return maxTargetCount; }
    public boolean isReturnAfterObjective() { return returnAfterObjective; }
    public double getRtbThreshold() { return rtbThreshold; }

    private static boolean finite(double value) {
        return Double.isNaN(value) || Double.isInfinite(value);
    }
}
