package net.vas.rdp.uav.debug;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DebugHub {
    private static final DebugHub INSTANCE = new DebugHub();

    private final CopyOnWriteArrayList<DebugSink> sinks = new CopyOnWriteArrayList<>();
    private final Map<DebugChannel, DebugLevel> channelLevels =
            new EnumMap<>(DebugChannel.class);
    private final Map<DebugChannel, DebugRetentionBuffer> retentionBuffers =
            new EnumMap<>(DebugChannel.class);
    private volatile boolean masterEnabled;
    private volatile DebugRetentionBuffer emergencyBuffer;

    private DebugHub() {
        for (DebugChannel channel : DebugChannel.values()) {
            channelLevels.put(channel, channel.getDefaultLevel());
            retentionBuffers.put(channel, new DebugRetentionBuffer());
        }
        emergencyBuffer = new DebugRetentionBuffer();
        sinks.add(new ConsoleSink());
    }

    public static DebugHub getInstance() {
        return INSTANCE;
    }

    public boolean isMasterEnabled() {
        return masterEnabled;
    }

    public void setMasterEnabled(boolean enabled) {
        masterEnabled = enabled;
    }

    public boolean isChannelEnabled(DebugChannel channel) {
        return channel != null && channel.isEnabled();
    }

    public void setChannelEnabled(DebugChannel channel, boolean enabled) {
        if (channel != null) channel.setEnabled(enabled);
    }

    public DebugLevel getChannelLevel(DebugChannel channel) {
        if (channel == null) return DebugLevel.DEBUG;
        synchronized (channelLevels) {
            return channelLevels.get(channel);
        }
    }

    public void setChannelLevel(DebugChannel channel, DebugLevel level) {
        if (channel == null) throw new IllegalArgumentException("channel must not be null");
        if (level == null) throw new IllegalArgumentException("level must not be null");
        synchronized (channelLevels) {
            channelLevels.put(channel, level);
        }
    }

    public void emit(DebugEvent event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");
        if (!masterEnabled) return;
        if (event.getLevel() != DebugLevel.ERROR
                && (!event.getChannel().isEnabled()
                || event.getLevel().ordinal() < getChannelLevel(event.getChannel()).ordinal())) {
            return;
        }

        synchronized (retentionBuffers) {
            retentionBuffers.get(event.getChannel()).record(event);
        }
        emergencyBuffer.record(event);
        for (DebugSink sink : sinks) {
            if (masterEnabled) sink.accept(event);
        }
    }

    public void registerSink(DebugSink sink) {
        if (sink == null) throw new IllegalArgumentException("sink must not be null");
        for (DebugSink registered : sinks) {
            if (registered == sink || registered.id().equals(sink.id())) return;
        }
        sinks.add(sink);
    }

    public boolean unregisterSink(String id) {
        if (id == null || "console".equals(id)) return false;
        for (DebugSink sink : sinks) {
            if (id.equals(sink.id())) return sinks.remove(sink);
        }
        return false;
    }

    public List<String> activeSinkIds() {
        List<String> ids = new ArrayList<>();
        for (DebugSink sink : sinks) ids.add(sink.id());
        return Collections.unmodifiableList(ids);
    }

    public List<DebugEvent> snapshotAll(DebugContext filter, int max) {
        if (max <= 0) return Collections.emptyList();
        List<DebugEvent> all = new ArrayList<>();
        synchronized (retentionBuffers) {
            for (DebugChannel channel : DebugChannel.values()) {
                all.addAll(retentionBuffers.get(channel).snapshot(filter, Integer.MAX_VALUE));
            }
        }
        all.sort((left, right) -> Long.compare(left.getTimestampMillis(),
                right.getTimestampMillis()));
        int start = Math.max(0, all.size() - max);
        return Collections.unmodifiableList(new ArrayList<>(all.subList(start, all.size())));
    }

    public void dumpCapture(DebugContext filter, File target) {
        DebugCaptureFiles.write(target, snapshotAll(filter, Integer.MAX_VALUE));
    }

    public List<DebugEvent> snapshot(DebugChannel channel, DebugContext filter, int max) {
        if (channel == null) throw new IllegalArgumentException("channel must not be null");
        synchronized (retentionBuffers) {
            return retentionBuffers.get(channel).snapshot(filter, max);
        }
    }

    public List<DebugEvent> emergencySnapshot(DebugContext filter, int max) {
        return emergencyBuffer.snapshot(filter, max);
    }

    public void configure(DebugConfig config) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        masterEnabled = config.isMasterEnabled();
        for (DebugChannel channel : DebugChannel.values()) {
            DebugConfig.ChannelSettings settings = config.getChannelSettings(channel);
            channel.setEnabled(settings.isEnabled());
            setChannelLevel(channel, settings.getLevel());
        }
        synchronized (retentionBuffers) {
            for (DebugChannel channel : DebugChannel.values()) {
                retentionBuffers.put(channel,
                        new DebugRetentionBuffer(config.getRetentionCapacityPerChannel()));
            }
        }
        emergencyBuffer = new DebugRetentionBuffer(config.getRetentionEmergencyCapacity());
    }

    public String summarize() {
        StringBuilder summary = new StringBuilder("DebugHub{masterEnabled=")
                .append(masterEnabled).append(", channels=");
        boolean first = true;
        for (DebugChannel channel : DebugChannel.values()) {
            if (!first) summary.append(", ");
            first = false;
            summary.append(channel.getId()).append('=')
                    .append(channel.isEnabled()).append('/')
                    .append(getChannelLevel(channel));
        }
        return summary.append('}').toString();
    }
}
