package net.vas.rdp.uav.flight;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

/** Server-side geometry policy for native WingMan square-strike routes. */
public final class SquareStrikeConfig {
    public static double altitudeOffset = 0.0D;
    public static double headingDegrees = 0.0D;
    public static double passSpacingFactor = 0.25D;
    public static double minimumAltitude = 8.0D;

    private SquareStrikeConfig() { }

    public static void load(File configDir) {
        Configuration config = new Configuration(new File(configDir, "rdp-uav-square-strike.cfg"));
        config.load();
        altitudeOffset = config.getFloat("altitudeOffset", "squareStrike", 0.0F,
                -512.0F, 512.0F, "Offset from the requested strike center Y.");
        headingDegrees = config.getFloat("headingDegrees", "squareStrike", 0.0F,
                -360.0F, 360.0F, "Square pass heading.");
        passSpacingFactor = config.getFloat("passSpacingFactor", "squareStrike", 0.25F,
                0.05F, 1.0F, "Pass spacing as a fraction of square size.");
        minimumAltitude = config.getFloat("minimumAltitude", "squareStrike", 8.0F,
                0.0F, 4096.0F, "Minimum generated strike altitude.");
        if (config.hasChanged()) config.save();
    }
}
