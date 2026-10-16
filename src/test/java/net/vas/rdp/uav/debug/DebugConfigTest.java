package net.vas.rdp.uav.debug;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DebugConfigTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void initializeForgeConfigurationHome() throws Exception {
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, temporaryFolder.getRoot());
    }

    @Test
    public void load_missingChannelSettings_addsDefaultsForEveryChannel() throws Exception {
        File configDir = temporaryFolder.newFolder();

        DebugConfig config = DebugConfig.load(configDir);

        Configuration saved = new Configuration(new File(configDir, "rdp-uav-debug.cfg"));
        saved.load();
        for (DebugChannel channel : DebugChannel.values()) {
            String prefix = "debug.channel." + channel.getId() + ".";
            assertTrue(saved.getCategory(Configuration.CATEGORY_GENERAL)
                    .containsKey(prefix + "enabled"));
            assertTrue(saved.getCategory(Configuration.CATEGORY_GENERAL)
                    .containsKey(prefix + "level"));
            assertEquals(channel.isDefaultEnabled(),
                    config.getChannelSettings(channel).isEnabled());
            assertEquals(channel.getDefaultLevel(), config.getChannelSettings(channel).getLevel());
        }
    }

    @Test
    public void load_orphanChannelSetting_keepsKeyAndReportsWarning() throws Exception {
        File configDir = temporaryFolder.newFolder();
        File configFile = new File(configDir, "rdp-uav-debug.cfg");
        Configuration initial = new Configuration(configFile);
        initial.load();
        initial.getBoolean("debug.channel.retired.enabled", Configuration.CATEGORY_GENERAL,
                true, "Retained legacy channel setting.");
        initial.save();
        List<String> warnings = new ArrayList<>();

        DebugConfig.load(configDir, warnings::add);

        Configuration saved = new Configuration(configFile);
        saved.load();
        assertTrue(saved.getCategory(Configuration.CATEGORY_GENERAL)
                .containsKey("debug.channel.retired.enabled"));
        assertTrue(warnings.contains(
                "[WARN][CONFIG] event=ORPHAN_KEY key=debug.channel.retired.enabled"));
    }

    @Test
    public void load_missingFrameworkSettings_addsSpecifiedDefaults() throws Exception {
        File configDir = temporaryFolder.newFolder();

        DebugConfig config = DebugConfig.load(configDir);

        assertEquals(DebugLevel.DEBUG, config.getLevel());
        assertFalse(config.isMasterEnabled());
        assertFalse(config.isFileEnabled());
        assertEquals(16, config.getFileMaxSizeMb());
        assertEquals(4, config.getFileMaxBackups());
        assertEquals(120, config.getNetworkMaxPerSecond());
        assertEquals(512, config.getRetentionCapacityPerChannel());
        assertEquals(64, config.getRetentionEmergencyCapacity());
        assertEquals(32, config.getRateDefaultMaxPerTick());
        assertEquals(256, config.getRateDefaultMaxPerSecond());
        assertEquals(64, config.getRateDefaultBurstThreshold());
        assertFalse(config.isIncludePositions());
        assertFalse(config.isIncludePlayers());
        assertEquals(DebugConfig.Preset.OFF, config.getPreset());

        Configuration saved = new Configuration(new File(configDir, "rdp-uav-debug.cfg"));
        saved.load();
        for (String key : new String[] {
                "debug.enabled", "debug.level", "debug.file.enabled", "debug.file.maxSizeMb",
                "debug.file.maxBackups", "debug.network.maxPerSecond",
                "debug.retention.capacityPerChannel", "debug.retention.emergencyCapacity",
                "debug.rate.default.maxPerTick", "debug.rate.default.maxPerSecond",
                "debug.rate.default.burstThreshold", "debug.privacy.includePositions",
                "debug.privacy.includePlayers", "debug.preset"
        }) {
            assertTrue(saved.getCategory(Configuration.CATEGORY_GENERAL).containsKey(key));
        }
    }

    @Test
    public void load_presets_appliesExpectedChannelFlagsAndLevels() throws Exception {
        assertPreset("OFF", false, new DebugChannel[0], DebugLevel.DEBUG);
        assertPreset("LIGHT", true,
                new DebugChannel[] {DebugChannel.MISSION, DebugChannel.FLIGHT, DebugChannel.GUIDANCE},
                DebugLevel.INFO);
        assertPreset("MISSION", true, new DebugChannel[] {
                DebugChannel.MISSION, DebugChannel.FLIGHT, DebugChannel.GUIDANCE,
                DebugChannel.WAYPOINT, DebugChannel.STRIKE, DebugChannel.FILTER,
                DebugChannel.STANDOFF, DebugChannel.WEAPON
        }, DebugLevel.DEBUG);
        assertPreset("NETWORK", true,
                new DebugChannel[] {DebugChannel.NETWORK, DebugChannel.DATALINK},
                DebugLevel.TRACE);
        assertPreset("VISION", true, new DebugChannel[] {DebugChannel.VISION}, DebugLevel.TRACE);
        assertPreset("FULL", true, DebugChannel.values(), DebugLevel.DEBUG);
        assertPreset("TRACE", true, DebugChannel.values(), DebugLevel.TRACE);
    }

    private void assertPreset(String preset, boolean masterEnabled, DebugChannel[] enabledChannels,
                              DebugLevel enabledLevel) throws Exception {
        File configDir = temporaryFolder.newFolder();
        File configFile = new File(configDir, "rdp-uav-debug.cfg");
        Configuration configuration = new Configuration(configFile);
        configuration.load();
        configuration.getString("debug.preset", Configuration.CATEGORY_GENERAL, preset,
                "Test preset.",
                new String[] {"OFF", "LIGHT", "MISSION", "NETWORK", "VISION", "FULL", "TRACE"});
        configuration.save();

        DebugConfig loaded = DebugConfig.load(configDir);
        List<DebugChannel> expectedEnabled = Arrays.asList(enabledChannels);

        assertEquals(DebugConfig.Preset.valueOf(preset), loaded.getPreset());
        assertEquals(masterEnabled, loaded.isMasterEnabled());
        for (DebugChannel channel : DebugChannel.values()) {
            boolean expected = expectedEnabled.contains(channel);
            assertEquals(preset + " channel " + channel.getId(), expected,
                    loaded.getChannelSettings(channel).isEnabled());
            assertEquals(preset + " cached flag " + channel.getId(), expected,
                    channel.isEnabled());
            assertEquals(preset + " level " + channel.getId(),
                    expected ? enabledLevel : channel.getDefaultLevel(),
                    loaded.getChannelSettings(channel).getLevel());
        }
        if (preset.equals("OFF")) {
            assertFalse(loaded.isMasterEnabled());
        } else {
            assertTrue(loaded.isMasterEnabled());
        }
    }
}
