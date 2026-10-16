package net.vas.rdp.uav.debug;

public interface DebugSink {
    void accept(DebugEvent event);

    void flush();

    String id();
}
