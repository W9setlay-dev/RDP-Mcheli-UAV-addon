package net.vas.rdp.uav.client.vision;

/** Client-only presentation choices; changing modes does not alter capture. */
public enum VisionPresentationMode {
    NORMAL,
    ASSIST,
    DIAGNOSTICS;

    public VisionPresentationMode next() {
        VisionPresentationMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
