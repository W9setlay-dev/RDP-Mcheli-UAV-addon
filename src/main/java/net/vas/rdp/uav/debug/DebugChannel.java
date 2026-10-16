package net.vas.rdp.uav.debug;

public enum DebugChannel {
    MISSION("mission", "Mission lifecycle, state transitions, targets"),
    FLIGHT("flight", "Flight control, controller commands, applied setpoints"),
    GUIDANCE("guidance", "Guidance authority, source selection, handoff"),
    TELEMETRY("telemetry", "Telemetry frame send/receive, cache updates"),
    VISION("vision", "Perception pipeline, frame capture, inference timing"),
    DESIGNATION("designation", "Designation creation, validation, rejection"),
    WAYPOINT("waypoint", "Waypoint registry, resolution, goto commands"),
    STRIKE("strike", "Strike command validation and execution"),
    FORMATION("formation", "Formation slots, role assignment, slot changes"),
    DECONFLICTION("deconfliction", "Conflict detection, resolution, budget exhaustion"),
    FILTER("filter", "Entity classification, standard identity, evidence"),
    STANDOFF("standoff", "Standoff profile resolution and distance checks"),
    WEAPON("weapon", "Weapon classification, arm, release gate"),
    NETWORK("network", "Packet send/receive, packet loss, latency"),
    COMMAND("command", "/rdpuav command entry, validation, outcome"),
    TAWS("taws", "Terrain awareness, AGL, corridor"),
    DATALINK("datalink", "Link status, degradation, recovery"),
    JOURNEYMAP("journeymap", "Waypoint bridge, overlay, import"),
    CONFIG("config", "Config load, migration, missing keys"),
    PROFILE("profile", "Vehicle profile resolution and fallback");

    private final String id;
    private final DebugLevel defaultLevel;
    private final boolean defaultEnabled;
    private final String description;
    private boolean enabled;

    DebugChannel(String id, String description) {
        this.id = id;
        this.defaultLevel = DebugLevel.DEBUG;
        this.defaultEnabled = false;
        this.description = description;
        this.enabled = defaultEnabled;
    }

    public String getId() {
        return id;
    }

    public DebugLevel getDefaultLevel() {
        return defaultLevel;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isLevelEnabled(DebugLevel level) {
        if (level == null || !DebugHub.getInstance().isMasterEnabled()) return false;
        if (level == DebugLevel.ERROR) return true;
        return enabled && level.ordinal() >= DebugHub.getInstance().getChannelLevel(this).ordinal();
    }

    public void trace(DebugContext context, String message) {
        emit(DebugLevel.TRACE, context, message, null);
    }

    public void debug(DebugContext context, String message) {
        emit(DebugLevel.DEBUG, context, message, null);
    }

    public void info(DebugContext context, String message) {
        emit(DebugLevel.INFO, context, message, null);
    }

    public void warn(DebugContext context, String message) {
        emit(DebugLevel.WARN, context, message, null);
    }

    public void error(DebugContext context, String message) {
        emit(DebugLevel.ERROR, context, message, null);
    }

    public void error(DebugContext context, String message, Throwable cause) {
        emit(DebugLevel.ERROR, context, message, cause);
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private void emit(DebugLevel level, DebugContext context, String message, Throwable cause) {
        if (!isLevelEnabled(level)) return;
        DebugHub.getInstance().emit(new DebugEvent(
                this, level, message, context, System.currentTimeMillis(), cause));
    }
}
