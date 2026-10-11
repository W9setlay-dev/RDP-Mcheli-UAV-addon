package net.vas.rdp.uav.dev;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class LegacyDevClientLauncher {
    private LegacyDevClientLauncher() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("sun.java.command", "GradleStart");
        Class<?> legacyDev = Class.forName("net.minecraftforge.legacydev.MainClient");
        Method main = legacyDev.getMethod("main", String[].class);
        try {
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("LegacyDev client launch failed", cause);
        }
    }
}
