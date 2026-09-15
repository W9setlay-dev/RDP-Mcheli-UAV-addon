package net.vas.rdp.uav.target;

import java.util.Locale;

public final class TargetSpec {
    private final TargetSelectionMode mode;
    private final String registryId;
    private final String namespace;
    private final TargetPriority priority;
    private final double x;
    private final double y;
    private final double z;

    private TargetSpec(TargetSelectionMode mode, String registryId, String namespace,
                       TargetPriority priority, double x, double y, double z) {
        if (priority == null) {
            throw new IllegalArgumentException("Target priority is required");
        }
        this.mode = mode;
        this.registryId = registryId;
        this.namespace = namespace;
        this.priority = priority;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static TargetSpec manual(String registryId, TargetPriority priority) {
        String normalized = normalize(registryId);
        if (normalized == null || !normalized.contains(":")) {
            throw new IllegalArgumentException("Manual target must be a namespace:path registry ID");
        }
        return new TargetSpec(TargetSelectionMode.MANUAL, normalized, null, priority, 0, 0, 0);
    }

    public static TargetSpec automatic(String namespace, TargetPriority priority) {
        String normalized = normalize(namespace);
        if (normalized == null || normalized.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Automatic target namespace must not contain ':'");
        }
        return new TargetSpec(TargetSelectionMode.AUTO, null, normalized, priority, 0, 0, 0);
    }

    public static TargetSpec hostile(TargetPriority priority) {
        return new TargetSpec(TargetSelectionMode.AUTO, null, "hostile", priority, 0, 0, 0);
    }

    public static TargetSpec point(double x, double y, double z, TargetPriority priority) {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)
                || Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
            throw new IllegalArgumentException("Point target coordinates must be finite");
        }
        return new TargetSpec(TargetSelectionMode.POINT, null, null, priority, x, y, z);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public TargetSelectionMode getMode() {
        return mode;
    }

    public String getRegistryId() {
        return registryId;
    }

    public String getNamespace() {
        return namespace;
    }

    public TargetPriority getPriority() {
        return priority;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
}
