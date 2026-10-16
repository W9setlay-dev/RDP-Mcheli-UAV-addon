package net.vas.rdp.uav.debug;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public final class DebugConfig {
    private static final Logger LOGGER = LogManager.getLogger("RDP-UAV/Debug");
    private static final String CATEGORY = Configuration.CATEGORY_GENERAL;
    private static final String CHANNEL_PREFIX = "debug.channel.";

    private final boolean masterEnabled;
    private final DebugLevel level;
    private final boolean fileEnabled;
    private final int fileMaxSizeMb;
    private final int fileMaxBackups;
    private final int networkMaxPerSecond;
    private final int retentionCapacityPerChannel;
    private final int retentionEmergencyCapacity;
    private final int rateDefaultMaxPerTick;
    private final int rateDefaultMaxPerSecond;
    private final int rateDefaultBurstThreshold;
    private final boolean includePositions;
    private final boolean includePlayers;
    private final Preset preset;
    private final Map<DebugChannel, ChannelSettings> channelSettings;

    private DebugConfig(boolean masterEnabled, DebugLevel level, boolean fileEnabled,
                        int fileMaxSizeMb, int fileMaxBackups,
                        int networkMaxPerSecond, int retentionCapacityPerChannel,
                        int retentionEmergencyCapacity, int rateDefaultMaxPerTick,
                        int rateDefaultMaxPerSecond, int rateDefaultBurstThreshold,
                        boolean includePositions, boolean includePlayers, Preset preset,
                        EnumMap<DebugChannel, ChannelSettings> channelSettings) {
        this.masterEnabled = masterEnabled;
        this.level = level;
        this.fileEnabled = fileEnabled;
        this.fileMaxSizeMb = fileMaxSizeMb;
        this.fileMaxBackups = fileMaxBackups;
        this.networkMaxPerSecond = networkMaxPerSecond;
        this.retentionCapacityPerChannel = retentionCapacityPerChannel;
        this.retentionEmergencyCapacity = retentionEmergencyCapacity;
        this.rateDefaultMaxPerTick = rateDefaultMaxPerTick;
        this.rateDefaultMaxPerSecond = rateDefaultMaxPerSecond;
        this.rateDefaultBurstThreshold = rateDefaultBurstThreshold;
        this.includePositions = includePositions;
        this.includePlayers = includePlayers;
        this.preset = preset;
        this.channelSettings = Collections.unmodifiableMap(new EnumMap<>(channelSettings));
    }

    public static DebugConfig load(File configDir) {
        return load(configDir, LOGGER::warn);
    }

    static DebugConfig load(File configDir, Consumer<String> warningSink) {
        if (configDir == null) throw new IllegalArgumentException("configDir must not be null");
        if (warningSink == null) throw new IllegalArgumentException("warningSink must not be null");

        Configuration config = new Configuration(new File(configDir, "rdp-uav-debug.cfg"));
        config.load();
        config.getBoolean("debug.enabled", CATEGORY, false,
                "Enable the debug framework.");
        DebugLevel level = readLevel(config, "debug.level", DebugLevel.DEBUG, warningSink);
        boolean fileEnabled = config.getBoolean("debug.file.enabled", CATEGORY, false,
                "Enable the rolling debug file sink.");
        int fileMaxSizeMb = config.getInt("debug.file.maxSizeMb", CATEGORY, 16, 1, 1024,
                "Maximum rolling debug file size in MiB.");
        int fileMaxBackups = config.getInt("debug.file.maxBackups", CATEGORY, 4, 1, 100,
                "Maximum number of rolling debug file backups.");
        int networkMaxPerSecond = config.getInt("debug.network.maxPerSecond", CATEGORY, 120,
                1, 100000, "Maximum debug events sent over the network per second.");
        int retentionCapacity = config.getInt("debug.retention.capacityPerChannel", CATEGORY,
                512, 1, 100000, "Retained debug events per channel.");
        int emergencyCapacity = config.getInt("debug.retention.emergencyCapacity", CATEGORY,
                64, 1, 100000, "Retained events in the emergency capture buffer.");
        int maxPerTick = config.getInt("debug.rate.default.maxPerTick", CATEGORY, 32,
                1, 100000, "Default maximum debug events per tick and channel.");
        int maxPerSecond = config.getInt("debug.rate.default.maxPerSecond", CATEGORY, 256,
                1, 1000000, "Default maximum debug events per second and channel.");
        int burstThreshold = config.getInt("debug.rate.default.burstThreshold", CATEGORY, 64,
                1, 1000000, "Default burst threshold for debug event rate limiting.");
        boolean includePositions = config.getBoolean("debug.privacy.includePositions", CATEGORY,
                false, "Include entity positions in debug output.");
        boolean includePlayers = config.getBoolean("debug.privacy.includePlayers", CATEGORY,
                false, "Include player identifiers in debug output.");
        Preset preset = readPreset(config, warningSink);

        EnumMap<DebugChannel, ChannelSettings> settings = new EnumMap<>(DebugChannel.class);
        for (DebugChannel channel : DebugChannel.values()) {
            String channelPrefix = CHANNEL_PREFIX + channel.getId() + ".";
            boolean enabled = config.getBoolean(channelPrefix + "enabled", CATEGORY,
                    channel.isDefaultEnabled(), "Enable the " + channel.getId() + " debug channel.");
            DebugLevel channelLevel = readLevel(config, channelPrefix + "level",
                    channel.getDefaultLevel(), warningSink);
            settings.put(channel, new ChannelSettings(enabled, channelLevel));
        }

        warnForOrphanChannels(config, warningSink);
        boolean masterEnabled = applyPreset(preset, settings);
        if (config.hasChanged()) config.save();

        DebugConfig loaded = new DebugConfig(masterEnabled, level, fileEnabled, fileMaxSizeMb,
                fileMaxBackups, networkMaxPerSecond, retentionCapacity, emergencyCapacity,
                maxPerTick, maxPerSecond, burstThreshold, includePositions, includePlayers,
                preset, settings);
        DebugHub.getInstance().configure(loaded);
        return loaded;
    }

    public static DebugConfig applyPreset(File configDir, Preset preset) {
        if (configDir == null) throw new IllegalArgumentException("configDir must not be null");
        if (preset == null) throw new IllegalArgumentException("preset must not be null");
        File file = new File(configDir, "rdp-uav-debug.cfg");
        Configuration config = new Configuration(file);
        config.load();
        config.get(CATEGORY, "debug.preset", Preset.OFF.name(),
                "Debug preset applied when the configuration is loaded.")
                .set(preset.name());
        EnumMap<DebugChannel, ChannelSettings> settings = defaultSettings();
        applyPreset(preset, settings);
        config.get(CATEGORY, "debug.enabled", false,
                "Enable the debug framework.").set(preset != Preset.OFF);
        for (DebugChannel channel : DebugChannel.values()) {
            ChannelSettings channelSettings = settings.get(channel);
            String prefix = CHANNEL_PREFIX + channel.getId() + ".";
            config.get(CATEGORY, prefix + "enabled", channel.isDefaultEnabled(),
                    "Enable the " + channel.getId() + " debug channel.")
                    .set(channelSettings.isEnabled());
            config.get(CATEGORY, prefix + "level", channel.getDefaultLevel().name(),
                    "Minimum level for " + prefix + "level.")
                    .set(channelSettings.getLevel().name());
        }
        config.save();
        return load(configDir);
    }

    private static EnumMap<DebugChannel, ChannelSettings> defaultSettings() {
        EnumMap<DebugChannel, ChannelSettings> settings = new EnumMap<>(DebugChannel.class);
        for (DebugChannel channel : DebugChannel.values()) {
            settings.put(channel, new ChannelSettings(
                    channel.isDefaultEnabled(), channel.getDefaultLevel()));
        }
        return settings;
    }

    private static DebugLevel readLevel(Configuration config, String key, DebugLevel fallback,
                                         Consumer<String> warningSink) {
        String configured = config.getString(key, CATEGORY, fallback.name(),
                "Minimum level for " + key + ".");
        try {
            return DebugLevel.valueOf(configured);
        } catch (IllegalArgumentException exception) {
            warningSink.accept("[WARN][CONFIG] event=INVALID_LEVEL key=" + key
                    + " value=" + configured + " fallback=" + fallback.name());
            config.get(CATEGORY, key, fallback.name(), "Minimum level for " + key + ".")
                    .set(fallback.name());
            return fallback;
        }
    }

    private static Preset readPreset(Configuration config, Consumer<String> warningSink) {
        String configured = config.getString("debug.preset", CATEGORY, Preset.OFF.name(),
                "Debug preset applied when the configuration is loaded.");
        try {
            return Preset.valueOf(configured);
        } catch (IllegalArgumentException exception) {
            warningSink.accept("[WARN][CONFIG] event=INVALID_PRESET key=debug.preset value="
                    + configured + " fallback=OFF");
            config.get(CATEGORY, "debug.preset", Preset.OFF.name(),
                    "Debug preset applied when the configuration is loaded.")
                    .set(Preset.OFF.name());
            return Preset.OFF;
        }
    }

    private static void warnForOrphanChannels(Configuration config, Consumer<String> warningSink) {
        ConfigCategory category = config.getCategory(CATEGORY);
        for (String key : category.getValues().keySet()) {
            if (!key.startsWith(CHANNEL_PREFIX)) continue;
            String channelAndField = key.substring(CHANNEL_PREFIX.length());
            int separator = channelAndField.indexOf('.');
            String channelId = separator < 0
                    ? channelAndField : channelAndField.substring(0, separator);
            if (findChannel(channelId) == null) {
                warningSink.accept("[WARN][CONFIG] event=ORPHAN_KEY key=" + key);
            }
        }
    }

    private static DebugChannel findChannel(String id) {
        for (DebugChannel channel : DebugChannel.values()) {
            if (channel.getId().equals(id)) return channel;
        }
        return null;
    }

    private static boolean applyPreset(Preset preset,
                                       EnumMap<DebugChannel, ChannelSettings> settings) {
        for (DebugChannel channel : DebugChannel.values()) {
            settings.put(channel, new ChannelSettings(false, channel.getDefaultLevel()));
        }

        switch (preset) {
            case OFF:
                return false;
            case LIGHT:
                enable(settings, DebugLevel.INFO, DebugChannel.MISSION, DebugChannel.FLIGHT,
                        DebugChannel.GUIDANCE);
                return true;
            case MISSION:
                enable(settings, DebugLevel.DEBUG, DebugChannel.MISSION, DebugChannel.FLIGHT,
                        DebugChannel.GUIDANCE, DebugChannel.WAYPOINT, DebugChannel.STRIKE,
                        DebugChannel.FILTER, DebugChannel.STANDOFF, DebugChannel.WEAPON);
                return true;
            case NETWORK:
                enable(settings, DebugLevel.TRACE, DebugChannel.NETWORK, DebugChannel.DATALINK);
                return true;
            case VISION:
                enable(settings, DebugLevel.TRACE, DebugChannel.VISION);
                return true;
            case FULL:
                enable(settings, DebugLevel.DEBUG, DebugChannel.values());
                return true;
            case TRACE:
                enable(settings, DebugLevel.TRACE, DebugChannel.values());
                return true;
            default:
                throw new IllegalStateException("Unhandled debug preset: " + preset);
        }
    }

    private static void enable(EnumMap<DebugChannel, ChannelSettings> settings, DebugLevel level,
                               DebugChannel... channels) {
        for (DebugChannel channel : channels) settings.put(channel, new ChannelSettings(true, level));
    }

    public boolean isMasterEnabled() {
        return masterEnabled;
    }

    public DebugLevel getLevel() {
        return level;
    }

    public boolean isFileEnabled() {
        return fileEnabled;
    }

    public int getFileMaxSizeMb() {
        return fileMaxSizeMb;
    }

    public int getFileMaxBackups() {
        return fileMaxBackups;
    }

    public int getNetworkMaxPerSecond() {
        return networkMaxPerSecond;
    }

    public int getRetentionCapacityPerChannel() {
        return retentionCapacityPerChannel;
    }

    public int getRetentionEmergencyCapacity() {
        return retentionEmergencyCapacity;
    }

    public int getRateDefaultMaxPerTick() {
        return rateDefaultMaxPerTick;
    }

    public int getRateDefaultMaxPerSecond() {
        return rateDefaultMaxPerSecond;
    }

    public int getRateDefaultBurstThreshold() {
        return rateDefaultBurstThreshold;
    }

    public boolean isIncludePositions() {
        return includePositions;
    }

    public boolean isIncludePlayers() {
        return includePlayers;
    }

    public Preset getPreset() {
        return preset;
    }

    public ChannelSettings getChannelSettings(DebugChannel channel) {
        if (channel == null) throw new IllegalArgumentException("channel must not be null");
        return channelSettings.get(channel);
    }

    public enum Preset {
        OFF,
        LIGHT,
        MISSION,
        NETWORK,
        VISION,
        FULL,
        TRACE
    }

    public static final class ChannelSettings {
        private final boolean enabled;
        private final DebugLevel level;

        private ChannelSettings(boolean enabled, DebugLevel level) {
            this.enabled = enabled;
            this.level = level;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public DebugLevel getLevel() {
            return level;
        }
    }
}
