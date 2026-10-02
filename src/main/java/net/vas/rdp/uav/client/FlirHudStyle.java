package net.vas.rdp.uav.client;

/** Presentation-only bracket styling; it does not represent sensor measurements. */
final class FlirHudStyle {
    private FlirHudStyle() { }

    static int bracketColor(boolean flirEnabled, boolean locked) {
        if (flirEnabled) return locked ? 0xFFFFFFFF : 0xBFFFFFFF;
        return locked ? 0xF2FF1F1F : 0xCCFFB81F;
    }

    static float lineWidth(boolean flirEnabled, boolean locked) {
        return flirEnabled && !locked ? 1.5F : 2.0F;
    }
}
