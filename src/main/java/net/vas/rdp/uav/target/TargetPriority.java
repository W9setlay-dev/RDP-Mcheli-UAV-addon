package net.vas.rdp.uav.target;

public enum TargetPriority {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    CRITICAL(3);

    private final int weight;

    TargetPriority(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}
