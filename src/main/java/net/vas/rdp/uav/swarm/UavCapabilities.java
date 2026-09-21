package net.vas.rdp.uav.swarm;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable capability declaration used when validating swarm roles. */
public final class UavCapabilities {
    private final boolean longRange;
    private final boolean payload;
    private final boolean tracking;
    private final boolean search;
    private final boolean reusable;
    private final Set<String> capabilities;

    private UavCapabilities(Builder builder) {
        longRange = builder.longRange;
        payload = builder.payload;
        tracking = builder.tracking;
        search = builder.search;
        reusable = builder.reusable;
        capabilities = Collections.unmodifiableSet(new LinkedHashSet<>(builder.capabilities));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean supportsLongRange() { return longRange; }
    public boolean hasPayload() { return payload; }
    public boolean supportsTracking() { return tracking; }
    public boolean supportsSearch() { return search; }
    public boolean isReusable() { return reusable; }
    public Set<String> getCapabilities() { return capabilities; }

    public boolean supports(SwarmRole role) {
        if (role == null) return false;
        switch (role) {
            case COMMANDER:
                return search || tracking;
            case SEARCHER:
                return search;
            case TRACKER:
                return tracking;
            case EXECUTOR:
                return payload;
            case SUPPORT:
                return reusable;
            case RESERVE:
                return true;
            default:
                return false;
        }
    }

    public static final class Builder {
        private boolean longRange;
        private boolean payload;
        private boolean tracking;
        private boolean search;
        private boolean reusable;
        private final Set<String> capabilities = new LinkedHashSet<>();

        public Builder longRange(boolean value) { longRange = value; return this; }
        public Builder payload(boolean value) { payload = value; return this; }
        public Builder tracking(boolean value) { tracking = value; return this; }
        public Builder search(boolean value) { search = value; return this; }
        public Builder reusable(boolean value) { reusable = value; return this; }
        public Builder capability(String value) {
            if (value != null && !value.trim().isEmpty()) {
                capabilities.add(value.trim().toUpperCase());
            }
            return this;
        }
        public UavCapabilities build() { return new UavCapabilities(this); }
    }
}
