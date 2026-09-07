package net.vas.rdp.uav.target;

import java.util.Locale;

public final class TargetSpec {
    private final TargetSelectionMode mode;
    private final String registryId;
    private final String namespace;
    private final TargetPriority priority;

    private TargetSpec(TargetSelectionMode mode, String registryId, String namespace,
                       TargetPriority priority) {
        if (priority == null) {
            throw new IllegalArgumentException("Target priority is required");
        }
        this.mode = mode;
        this.registryId = registryId;
        this.namespace = namespace;
        this.priority = priority;
    }

    public static TargetSpec manual(String registryId, TargetPriority priority) {
        String normalized = normalize(registryId);
        if (normalized == null || !normalized.contains(":")) {
            throw new IllegalArgumentException("Manual target must be a namespace:path registry ID");
        }
        return new TargetSpec(TargetSelectionMode.MANUAL, normalized, null, priority);
    }

    public static TargetSpec automatic(String namespace, TargetPriority priority) {
        String normalized = normalize(namespace);
        if (normalized == null || normalized.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Automatic target namespace must not contain ':'");
        }
        return new TargetSpec(TargetSelectionMode.AUTO, null, normalized, priority);
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
}
