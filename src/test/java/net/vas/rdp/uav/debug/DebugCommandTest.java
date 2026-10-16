package net.vas.rdp.uav.debug;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import net.vas.rdp.uav.RdpUavDebug;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DebugCommandTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private DebugCommandService service;
    private File configDirectory;
    private File logsDirectory;

    @Before
    public void initialize() throws Exception {
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, temporaryFolder.getRoot());
        configDirectory = temporaryFolder.newFolder("config");
        logsDirectory = temporaryFolder.newFolder("logs");
        service = new DebugCommandService();
        RdpUavDebug.load(configDirectory);
    }

    @After
    public void stopCaptureIfActive() {
        service.execute(new String[] {"capture", "stop"}, true, logsDirectory);
        DebugHub.getInstance().setMasterEnabled(false);
    }

    @Test
    public void execute_unauthorizedCaller_doesNotChangeHub() {
        boolean original = DebugHub.getInstance().isMasterEnabled();

        List<String> output = service.execute(new String[] {"on"}, false, logsDirectory);

        assertFalse(output.isEmpty());
        assertEquals(original, DebugHub.getInstance().isMasterEnabled());
    }

    @Test
    public void execute_statusAndList_returnsMasterAndChannelRegistry() {
        List<String> preset = service.execute(
                new String[] {"preset", "MISSION"}, true, logsDirectory);
        assertTrue(preset.get(0), preset.get(0).contains("applied"));

        List<String> status = service.execute(new String[] {"status"}, true, logsDirectory);
        List<String> channels = service.execute(new String[] {"list"}, true, logsDirectory);

        assertTrue(status.get(0), status.get(0).contains("master=true"));
        assertTrue(status.get(0).contains("preset=MISSION"));
        assertTrue(channels.stream().anyMatch(line -> line.startsWith("mission ")
                && line.contains("Mission lifecycle")));
        assertEquals(DebugChannel.values().length, channels.size());
    }

    @Test
    public void execute_presetAndLegacyAliases_updateHubAndPersist() throws Exception {
        List<String> preset = service.execute(
                new String[] {"preset", "NETWORK"}, true, logsDirectory);
        assertTrue(preset.get(0), preset.get(0).contains("applied"));

        assertTrue(DebugHub.getInstance().isMasterEnabled());
        assertTrue(DebugChannel.NETWORK.isEnabled());
        assertEquals(DebugLevel.TRACE, DebugHub.getInstance().getChannelLevel(DebugChannel.NETWORK));
        Configuration saved = new Configuration(
                new File(configDirectory, "rdp-uav-debug.cfg"));
        saved.load();
        assertEquals("NETWORK", saved.getString("debug.preset",
                Configuration.CATEGORY_GENERAL, "OFF", "preset"));

        service.execute(new String[] {"off"}, true, logsDirectory);
        assertFalse(DebugHub.getInstance().isMasterEnabled());
        service.execute(new String[] {"on"}, true, logsDirectory);
        assertTrue(DebugHub.getInstance().isMasterEnabled());
        assertEquals(DebugConfig.Preset.FULL,
                RdpUavDebug.getFrameworkConfig().getPreset());
    }

    @Test
    public void execute_channelActions_toggleAndSetThreshold() {
        List<String> preset = service.execute(
                new String[] {"preset", "FULL"}, true, logsDirectory);
        assertTrue(preset.get(0), preset.get(0).contains("applied"));

        service.execute(new String[] {"channel", "vision", "off"}, true, logsDirectory);
        assertFalse(DebugChannel.VISION.isEnabled());

        service.execute(new String[] {"channel", "vision", "on"}, true, logsDirectory);
        service.execute(new String[] {"channel", "vision", "level", "WARN"}, true, logsDirectory);

        assertTrue(DebugChannel.VISION.isEnabled());
        assertEquals(DebugLevel.WARN,
                DebugHub.getInstance().getChannelLevel(DebugChannel.VISION));
    }

    @Test
    public void execute_dumpChannelAndEmergency_writesRetainedEvents() throws Exception {
        List<String> preset = service.execute(
                new String[] {"preset", "FULL"}, true, logsDirectory);
        assertTrue(preset.get(0), preset.get(0).contains("applied"));
        DebugHub.getInstance().emit(event(DebugChannel.MISSION, "event=MISSION"));
        DebugHub.getInstance().emit(event(DebugChannel.FLIGHT, "event=FLIGHT"));

        List<String> channelOutput = service.execute(
                new String[] {"dump", "mission"}, true, logsDirectory);
        List<String> emergencyOutput = service.execute(
                new String[] {"emergency"}, true, logsDirectory);

        File channelFile = new File(logsDirectory, filenameFrom(channelOutput.get(0)));
        File emergencyFile = new File(logsDirectory, filenameFrom(emergencyOutput.get(0)));
        assertTrue(new String(Files.readAllBytes(channelFile.toPath()), StandardCharsets.UTF_8)
                .contains("event=MISSION"));
        String emergencyText = new String(
                Files.readAllBytes(emergencyFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(emergencyText.contains("event=MISSION"));
        assertTrue(emergencyText.contains("event=FLIGHT"));
    }

    @Test
    public void execute_captureStartAndStop_writesEventsToRequestedFile() throws Exception {
        List<String> preset = service.execute(
                new String[] {"preset", "FULL"}, true, logsDirectory);
        assertTrue(preset.get(0), preset.get(0).contains("applied"));

        List<String> started = service.execute(
                new String[] {"capture", "start", "operator-session"}, true, logsDirectory);
        DebugHub.getInstance().emit(event(DebugChannel.MISSION, "event=CAPTURED"));
        List<String> stopped = service.execute(
                new String[] {"capture", "stop"}, true, logsDirectory);

        File captureFile = new File(logsDirectory, "operator-session.log");
        assertTrue(started.get(0).contains("operator-session.log"));
        assertTrue(stopped.get(0).contains("operator-session.log"));
        assertTrue(new String(Files.readAllBytes(captureFile.toPath()), StandardCharsets.UTF_8)
                .contains("event=CAPTURED"));
        assertFalse(DebugHub.getInstance().activeSinkIds().contains(CaptureSink.ID));
    }

    @Test
    public void execute_captureRejectsPathTraversal() {
        List<String> output = service.execute(
                new String[] {"capture", "start", "..\\outside"}, true, logsDirectory);

        assertTrue(output.get(0).startsWith("Debug capture could not start:"));
        assertFalse(new File(temporaryFolder.getRoot(), "outside.log").exists());
    }

    @Test
    public void execute_reload_reloadsPresetFromConfig() {
        setPresetInConfig("VISION");
        RdpUavDebug.load(configDirectory);
        setPresetInConfig("NETWORK");

        List<String> output = service.execute(new String[] {"reload"}, true, logsDirectory);

        assertTrue(output.get(0).contains("reloaded"));
        assertEquals(DebugConfig.Preset.NETWORK,
                RdpUavDebug.getFrameworkConfig().getPreset());
        assertTrue(DebugChannel.NETWORK.isEnabled());
        assertFalse(DebugChannel.VISION.isEnabled());
    }

    @Test
    public void execute_unknownChannelAndPreset_returnsValidationMessages() {
        List<String> channel = service.execute(
                new String[] {"channel", "unknown", "on"}, true, logsDirectory);
        List<String> preset = service.execute(
                new String[] {"preset", "unknown"}, true, logsDirectory);

        assertTrue(channel.get(0).contains("Unknown debug channel"));
        assertTrue(preset.get(0).contains("Unknown debug preset"));
    }

    private void setPresetInConfig(String preset) {
        Configuration config = new Configuration(new File(configDirectory, "rdp-uav-debug.cfg"));
        config.load();
        config.get(Configuration.CATEGORY_GENERAL, "debug.preset", "OFF", "preset")
                .set(preset);
        config.save();
    }

    private static DebugEvent event(DebugChannel channel, String message) {
        return new DebugEvent(channel, DebugLevel.DEBUG, message, DebugContext.none(),
                System.currentTimeMillis(), null);
    }

    private static String filenameFrom(String message) {
        int separator = message.lastIndexOf(": ");
        return message.substring(separator + 2).split(" ")[0];
    }
}
