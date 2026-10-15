package net.vas.rdp.uav.formation;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/** Server configuration for formation assignment and separation screening. */
public final class FormationConfig {
    public static FormationPreset defaultPreset = FormationPreset.WEDGE;
    public static int maximumWingmen = 8;
    public static double sideSpacing = 12.0D;
    public static double altitudeSpacing = 4.0D;
    public static double rearSpacing = 16.0D;
    public static double minimumHorizontalSeparation = 24.0D;
    public static double minimumVerticalSeparation = 8.0D;
    public static int conflictTimeWindowTicks = 60;
    public static int maximumResolutionAttempts = 3;
    public static double minimumTurnRadiusOverride;

    private FormationConfig() { }

    /** Loads the bounded server settings and persists Forge's normalized configuration. */
    public static void load(File directory) {
        if (directory == null) throw new IllegalArgumentException("Configuration directory is required");
        Configuration configuration = new Configuration(
                new File(directory, "rdp-uav-formation.cfg"));
        configuration.load();
        String presetName = configuration.getString("defaultPreset", "formation",
                defaultPreset.name(), "Default formation preset.",
                new String[] { "ECHELON_RIGHT", "ECHELON_LEFT", "LINE_ABREAST",
                        "TRAIL", "WEDGE", "COLUMN" });
        try {
            defaultPreset = FormationPreset.valueOf(presetName);
        } catch (IllegalArgumentException invalidPreset) {
            throw new IllegalStateException("Invalid formation defaultPreset: " + presetName,
                    invalidPreset);
        }
        maximumWingmen = configuration.getInt("maxWingmen", "formation",
                maximumWingmen, 1, 64, "Maximum wingmen per formation.");
        sideSpacing = configuration.getFloat("sideSpacing", "formation",
                (float) sideSpacing, 1.0F, 512.0F, "Formation side spacing in blocks.");
        altitudeSpacing = configuration.getFloat("altitudeSpacing", "formation",
                (float) altitudeSpacing, 1.0F, 512.0F, "Formation altitude spacing in blocks.");
        rearSpacing = configuration.getFloat("rearSpacing", "formation",
                (float) rearSpacing, 1.0F, 512.0F, "Formation rear spacing in blocks.");
        minimumHorizontalSeparation = configuration.getFloat(
                "minHorizontalSeparation", "deconfliction",
                (float) minimumHorizontalSeparation, 1.0F, 512.0F,
                "Minimum horizontal separation in blocks.");
        minimumVerticalSeparation = configuration.getFloat(
                "minVerticalSeparation", "deconfliction",
                (float) minimumVerticalSeparation, 1.0F, 512.0F,
                "Minimum vertical separation in blocks.");
        conflictTimeWindowTicks = configuration.getInt("conflictTimeWindowTicks",
                "deconfliction", conflictTimeWindowTicks, 1, 1200,
                "Conflict detection time window in ticks.");
        maximumResolutionAttempts = configuration.getInt("maxResolutionAttempts",
                "deconfliction", maximumResolutionAttempts, 1, 10,
                "Maximum resolution proposals per aircraft pair and mission.");
        minimumTurnRadiusOverride = configuration.getFloat("minimumTurnRadiusOverride",
                "path", (float) minimumTurnRadiusOverride, 0.0F, 512.0F,
                "Turn-radius override in blocks; zero uses the aircraft profile.");
        if (configuration.hasChanged()) configuration.save();
    }
}
