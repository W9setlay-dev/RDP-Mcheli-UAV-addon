package net.vas.rdp.uav.path;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/** Server configuration for orbit sampling and terminal path bounds. */
public final class PathConfig {
    public static double orbitRadiusMultiplier = 2.5D;
    public static int orbitSampleIntervalTicks = 5;
    public static double[] terminalDiveAngles = { 20.0D, 30.0D, 40.0D, 50.0D, 60.0D };
    public static double terminalMinimumRun = 48.0D;
    public static double terminalMaximumRun = 192.0D;

    private PathConfig() { }

    /** Loads the bounded server path settings. */
    public static void load(File directory) {
        if (directory == null) throw new IllegalArgumentException("Configuration directory is required");
        Configuration configuration = new Configuration(new File(directory, "rdp-uav-path.cfg"));
        configuration.load();
        orbitRadiusMultiplier = configuration.getFloat("orbitRadiusMultiplier", "path",
                (float) orbitRadiusMultiplier, 1.0F, 10.0F,
                "Orbit radius as a multiple of the aircraft profile turn radius.");
        orbitSampleIntervalTicks = configuration.getInt("orbitSampleIntervalTicks", "path",
                orbitSampleIntervalTicks, 1, 40, "Orbit sample interval in ticks.");
        terminalDiveAngles = parseTerminalDiveAngles(configuration.getString(
                "terminalDiveAngles", "terminal", "20,30,40,50,60",
                "Allowed terminal approach angles in degrees."));
        terminalMinimumRun = configuration.getFloat("terminalMinimumRun", "terminal",
                (float) terminalMinimumRun, 1.0F, 512.0F,
                "Minimum straight terminal run in blocks.");
        terminalMaximumRun = configuration.getFloat("terminalMaximumRun", "terminal",
                (float) terminalMaximumRun, 1.0F, 1024.0F,
                "Maximum straight terminal run in blocks.");
        validateTerminalRunBounds(terminalMinimumRun, terminalMaximumRun);
        if (configuration.hasChanged()) configuration.save();
    }

    static double[] parseTerminalDiveAngles(String value) {
        if (value == null) throw new IllegalStateException("terminalDiveAngles is required");
        String[] angleValues = value.split(",");
        if (angleValues.length == 0 || angleValues.length > 16) {
            throw new IllegalStateException("terminalDiveAngles must contain 1 to 16 values");
        }
        double[] parsedAngles = new double[angleValues.length];
        for (int index = 0; index < angleValues.length; index++) {
            try {
                parsedAngles[index] = Double.parseDouble(angleValues[index].trim());
            } catch (NumberFormatException invalidAngle) {
                throw new IllegalStateException("Invalid terminal dive angle: "
                        + angleValues[index], invalidAngle);
            }
            if (!Double.isFinite(parsedAngles[index])
                    || parsedAngles[index] < -25.0D || parsedAngles[index] > 60.0D) {
                throw new IllegalStateException("Terminal dive angle is outside [-25, 60]");
            }
        }
        return parsedAngles;
    }

    static void validateTerminalRunBounds(double minimum, double maximum) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)
                || minimum < 1.0D || minimum > 512.0D
                || maximum < 1.0D || maximum > 1024.0D) {
            throw new IllegalStateException("Terminal run bounds are outside configured limits");
        }
        if (maximum < minimum) {
            throw new IllegalStateException("terminalMaximumRun must not be below terminalMinimumRun");
        }
    }
}
