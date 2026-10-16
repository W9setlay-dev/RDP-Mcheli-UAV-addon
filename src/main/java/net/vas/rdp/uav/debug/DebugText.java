package net.vas.rdp.uav.debug;

public final class DebugText {
    private DebugText() { }

    public static String safeValue(Object value) {
        String text = String.valueOf(value);
        StringBuilder safe = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            safe.append(character == '\r' || character == '\n'
                    || character == '[' || character == ']' ? '_' : character);
        }
        return safe.toString();
    }
}
