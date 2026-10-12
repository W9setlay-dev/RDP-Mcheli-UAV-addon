package net.vas.rdp.uav.client.vision;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.norwood.mcheli.uav.WidgetUavCameraFeed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.vas.rdp.uav.RdpUavDebug;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/** Client-only capture, cleanup, and optional diagnostics for McHeli's UAV feed. */
@SideOnly(Side.CLIENT)
public final class VisionClientEvents {
    private static final long FEED_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(2L);
    private static volatile VisionClientEvents currentInstance;
    private final PixelBufferPool pixelBuffers = new PixelBufferPool();
    private final VisionProcessingWorker worker =
            new VisionProcessingWorker(pixelBuffers, VisionClientConfig.minimumFeatureScore);
    private final AsyncPixelCapture capture = new AsyncPixelCapture(pixelBuffers, worker);
    private final VisionObservationStatus observation = new VisionObservationStatus();
    private String loggedCaptureSession = "";
    private String loggedResultSession = "";
    private String loggedOverlaySession = "";
    private String loggedFailureReason = "";
    private long lastLoggedOverlaySequence = -1L;
    private long lastFeedEventNanos;
    private boolean feedObserved;
    private boolean resetGpuResources;
    private final Map<ModularGuiContext, Boolean> assistContexts = new WeakHashMap<>();
    private ModularGuiContext cachedFeedContext;
    private WidgetUavCameraFeed cachedFeed;

    public VisionClientEvents() {
        currentInstance = this;
    }

    public static VisionObservationStatus.Snapshot observationSnapshot() {
        VisionClientEvents current = currentInstance;
        return current == null ? VisionObservationStatus.waitingSnapshot()
                : current.observation.snapshot();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!VisionClientConfig.enabled || !WidgetUavCameraFeed.RENDERING_FEED) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (resetGpuResources) {
            capture.closeGlResources();
            resetGpuResources = false;
        }
        feedObserved = true;
        lastFeedEventNanos = System.nanoTime();
        String cameraId = minecraft.getRenderViewEntity() == null ? "unavailable"
                : minecraft.getRenderViewEntity().getUniqueID().toString();
        boolean newSession = !observation.snapshot().isFeedActive();
        observation.feedStarted(cameraId, capture.getLastIssuedSequence() + 1L);
        if (newSession) {
            loggedCaptureSession = "";
            loggedResultSession = "";
            loggedOverlaySession = "";
            loggedFailureReason = "";
            lastLoggedOverlaySequence = -1L;
            RdpUavDebug.event("Vision", "OBSERVATION_SESSION_STARTED",
                    "session", observation.snapshot().shortSessionId(),
                    "camera", cameraId, "source", "mcheli-uav-feed");
        }
        try {
            if (capture.capture(minecraft)) {
                long sequence = capture.getLastIssuedSequence();
                observation.frameCaptured(sequence, capture.getLastIssuedCaptureNanos());
                String session = observation.snapshot().shortSessionId();
                if (!session.equals(loggedCaptureSession) || sequence % 25L == 0L) {
                    RdpUavDebug.event("Vision", "FRAME_CAPTURE_ISSUED",
                            "session", session,
                            "sequence", sequence);
                    loggedCaptureSession = session;
                }
            } else {
                observation.observePipeline(worker.snapshot());
            }
        } catch (RuntimeException failure) {
            String reason = "Client capture failed: " + failure.getClass().getSimpleName();
            worker.unavailable(reason);
            observation.unavailable(reason);
            RdpUavDebug.event("Vision", "CAPTURE_FAILED",
                    "session", observation.snapshot().shortSessionId(),
                    "reason", failure.getClass().getSimpleName());
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        registerAssistCallback(Minecraft.getMinecraft());
        observePublishedResult();
        if (!VisionClientConfig.enabled) {
            boolean sessionWasActive = observation.snapshot().isFeedActive();
            String session = observation.snapshot().shortSessionId();
            observation.feedStopped("Perception disabled");
            if (sessionWasActive) {
                RdpUavDebug.event("Vision", "OBSERVATION_SESSION_STOPPED",
                        "session", session, "reason", "DISABLED");
            }
        }
        if (!hasGlContext()) return;

        if (!VisionClientConfig.enabled) {
            if (feedObserved || capture.hasGlResources()) {
                feedObserved = false;
                capture.clearWorld();
                capture.closeGlResources();
            }
            resetGpuResources = false;
            return;
        }
        if (VisionClientConfig.minimumFeatureScore
                != lastAppliedMinimumFeatureScore) {
            worker.setMinimumScore(VisionClientConfig.minimumFeatureScore);
            lastAppliedMinimumFeatureScore = VisionClientConfig.minimumFeatureScore;
        }
        if (resetGpuResources) {
            capture.closeGlResources();
            resetGpuResources = false;
        }
        if (feedObserved && System.nanoTime() - lastFeedEventNanos > FEED_TIMEOUT_NANOS) {
            feedObserved = false;
            capture.clearWorld();
            capture.closeGlResources();
            boolean sessionWasActive = observation.snapshot().isFeedActive();
            String session = observation.snapshot().shortSessionId();
            observation.feedStopped("McHeli UAV camera feed is no longer rendering");
            if (sessionWasActive) {
                RdpUavDebug.event("Vision", "OBSERVATION_SESSION_STOPPED",
                        "session", session, "reason", "FEED_TIMEOUT");
            }
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            feedObserved = false;
            capture.clearWorld();
            boolean sessionWasActive = observation.snapshot().isFeedActive();
            String session = observation.snapshot().shortSessionId();
            observation.feedStopped("Client world unloaded");
            if (sessionWasActive) {
                RdpUavDebug.event("Vision", "OBSERVATION_SESSION_STOPPED",
                        "session", session, "reason", "WORLD_UNLOAD");
            }
            resetGpuResources = true;
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (!VisionPresentationPolicy.isDiagnosticsEnabled(VisionClientConfig.enabled,
                VisionClientConfig.overlayEnabled, VisionClientConfig.presentationMode)
                || event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        PerceptionSnapshot state = worker.snapshot();
        observePublishedResult();
        Minecraft minecraft = Minecraft.getMinecraft();
        int color = state.getState() == PerceptionSnapshot.State.OPERATIONAL
                ? 0xFF80FF80 : state.getState() == PerceptionSnapshot.State.DEGRADED
                ? 0xFFFFC060 : 0xFFFF8080;
        int lineHeight = minecraft.fontRenderer.FONT_HEIGHT + 2;
        int y = 4;
        draw(minecraft, "Pixel vision: " + state.getState(), y, color);
        y += lineHeight;
        if (!state.getStatusReason().isEmpty()) {
            draw(minecraft, state.getStatusReason(), y, color);
            y += lineHeight;
        }
        List<PerceptionTracker.TrackView> tracks = state.getTracks();
        int confirmed = 0;
        for (PerceptionTracker.TrackView track : tracks) {
            if (track.getLifecycle() == PerceptionTracker.TrackLifecycle.CONFIRMED) {
                confirmed++;
            }
        }
        draw(minecraft, "Tracks: " + tracks.size() + " (confirmed " + confirmed + ")",
                y, 0xFFFFFFFF);
        y += lineHeight;
        int shown = Math.min(4, tracks.size());
        for (int i = 0; i < shown; i++) {
            PerceptionTracker.TrackView track = tracks.get(i);
            VisualDetection detection = track.getDetection();
            String summary = "#" + track.getId() + " " + detection.getClassId() + " "
                    + track.getLifecycle() + " score=" + detection.getScore() + " @ "
                    + (detection.getX() + detection.getWidth() * 0.5D) + ","
                    + (detection.getY() + detection.getHeight() * 0.5D);
            draw(minecraft, summary, y, 0xFFFFFFFF);
            y += lineHeight;
        }
        if (tracks.size() > shown) {
            draw(minecraft, "... " + (tracks.size() - shown) + " additional tracks",
                    y, 0xFFB0B0B0);
            y += lineHeight;
        }
        draw(minecraft, "Capture: " + capture.getMeasuredCaptureRateHz() + " Hz, P95 "
                        + TimeUnit.NANOSECONDS.toMillis(capture.getCaptureP95Nanos()) + " ms",
                y, 0xFFFFFFFF);
        y += lineHeight;
        String age = state.getCapturedAtNanos() == 0L ? "n/a"
                : TimeUnit.NANOSECONDS.toMillis(Math.max(0L,
                System.nanoTime() - state.getCapturedAtNanos())) + " ms";
        draw(minecraft, "Frame age: " + age + ", dropped "
                        + (state.getDroppedFrames() + capture.getDroppedFrames()),
                y, 0xFFFFFFFF);
        VisionObservationStatus.Snapshot status = observation.snapshot();
        y += lineHeight;
        draw(minecraft, "Observation " + status.shortSessionId() + ": "
                        + status.getStage() + " C/P/O="
                        + status.getCapturedSequence() + "/"
                        + status.getProcessedSequence() + "/"
                        + status.getPresentedSequence(),
                y, 0xFFB0B0B0);
        recordOverlay("DIAGNOSTICS", state.getSequence(), System.nanoTime());
    }

    private void registerAssistCallback(Minecraft minecraft) {
        ModularScreen screen = ModularScreen.getCurrent();
        if (minecraft == null || screen == null) {
            cachedFeedContext = null;
            cachedFeed = null;
            return;
        }
        ModularGuiContext context = screen.getContext();
        if (assistContexts.containsKey(context)) return;
        context.postRenderCallbacks.add(renderedContext -> renderAssist(minecraft, renderedContext));
        assistContexts.put(context, Boolean.TRUE);
    }

    private void renderAssist(Minecraft minecraft, ModularGuiContext context) {
        if (!VisionPresentationPolicy.isAssistEnabled(VisionClientConfig.enabled,
                VisionClientConfig.presentationMode)
                || minecraft == null || context == null
                || minecraft.world == null || minecraft.player == null) return;

        WidgetUavCameraFeed feed = findFeedWidget(context);
        if (feed == null) return;

        Area area = feed.getArea();
        if (area.width <= 0 || area.height <= 0) return;
        PerceptionSnapshot state = worker.snapshot();
        observePublishedResult();
        long now = System.nanoTime();
        boolean fresh = VisionPresentationPolicy.isSnapshotFresh(state, now);
        if (!fresh) {
            String reason = state.getStatusReason().isEmpty()
                    ? (state.getCapturedAtNanos() == 0L ? "NO FRESH RESULT" : "RESULT EXPIRED")
                    : state.getStatusReason();
            drawStatusPanel(minecraft, area.x, area.y, area.width,
                    "VISION " + state.getState() + " - " + reason);
            recordOverlay("ASSIST_STATUS", -1L, now);
            return;
        }

        List<PerceptionTracker.TrackView> tracks = state.getTracks();
        int lostCount = 0;
        for (PerceptionTracker.TrackView track : tracks) {
            PerceptionTracker.TrackLifecycle lifecycle = track.getLifecycle();
            if (lifecycle == PerceptionTracker.TrackLifecycle.LOST) {
                lostCount++;
                continue;
            }
            if (!VisionPresentationPolicy.shouldDrawBox(lifecycle, true)) continue;
            VisualDetection detection = track.getDetection();
            DetectionViewportMapper.ScreenRect rectangle = DetectionViewportMapper.map(
                    detection, area.x, area.y, area.width, area.height);
            if (rectangle == null) continue;
            int color = lifecycle == PerceptionTracker.TrackLifecycle.STALE
                    ? 0xFFFFC060
                    : lifecycle == PerceptionTracker.TrackLifecycle.TENTATIVE
                    ? 0xFF60D8FF : 0xFF80FF80;
            if (lifecycle == PerceptionTracker.TrackLifecycle.STALE) {
                drawDashedRectangle(rectangle, color);
            } else {
                drawRectangle(rectangle, color);
            }
            String label = "#" + track.getId() + " "
                    + (lifecycle == PerceptionTracker.TrackLifecycle.STALE
                    ? "STALE" : lifecycle.name())
                    + " " + detection.getClassId() + " match "
                    + String.format(java.util.Locale.ROOT, "%.2f", detection.getScore());
            drawFeedLabel(minecraft, area.x, area.y, area.width, area.height,
                    rectangle.getX(), rectangle.getY(), label, color);
        }
        if (lostCount > 0) {
            drawStatusPanel(minecraft, area.x, area.y, area.width,
                    "LOST TRACKS: " + lostCount + " (last positions hidden)");
        } else if (tracks.isEmpty()) {
            drawStatusPanel(minecraft, area.x, area.y, area.width,
                    "VISION OPERATIONAL - NO DETECTIONS");
        }
        recordOverlay("ASSIST", state.getSequence(), now);
    }

    private WidgetUavCameraFeed findFeedWidget(ModularGuiContext context) {
        if (context != cachedFeedContext || cachedFeed == null || !cachedFeed.isValid()) {
            cachedFeed = null;
            cachedFeedContext = context;
            for (ModularPanel panel : context.getScreen().getPanelManager().getReverseOpenPanels()) {
                for (IWidget widget : WidgetTree.iterableBFS(panel)) {
                    if (widget instanceof WidgetUavCameraFeed) {
                        cachedFeed = (WidgetUavCameraFeed) widget;
                        break;
                    }
                }
                if (cachedFeed != null) break;
            }
        }
        return cachedFeed;
    }

    private static void drawStatusPanel(Minecraft minecraft, int x, int y,
                                        int width, String text) {
        int textWidth = Math.max(0, Math.min(width - 4,
                minecraft.fontRenderer.getStringWidth(text)));
        if (textWidth == 0) return;
        Gui.drawRect(x, y, x + textWidth + 4,
                y + minecraft.fontRenderer.FONT_HEIGHT + 4, 0x90000000);
        minecraft.fontRenderer.drawStringWithShadow(
                minecraft.fontRenderer.trimStringToWidth(text, textWidth),
                x + 2, y + 2, 0xFFFFFFFF);
    }

    private static void drawFeedLabel(Minecraft minecraft, int x, int y, int width,
                                      int height, int boxX, int boxY, String text,
                                      int color) {
        if (height < minecraft.fontRenderer.FONT_HEIGHT + 2) return;
        int availableWidth = Math.max(0, width - 4);
        if (availableWidth == 0) return;
        String bounded = minecraft.fontRenderer.trimStringToWidth(text, availableWidth);
        int labelX = Math.max(x + 2, Math.min(boxX, x + width
                - minecraft.fontRenderer.getStringWidth(bounded) - 2));
        int labelY = boxY - minecraft.fontRenderer.FONT_HEIGHT - 2;
        if (labelY < y) labelY = Math.min(y + height - minecraft.fontRenderer.FONT_HEIGHT - 2,
                boxY + 2);
        minecraft.fontRenderer.drawStringWithShadow(bounded, labelX, labelY, color);
    }

    private static void drawRectangle(DetectionViewportMapper.ScreenRect rect, int color) {
        int right = rect.getX() + rect.getWidth();
        int bottom = rect.getY() + rect.getHeight();
        Gui.drawRect(rect.getX(), rect.getY(), right, rect.getY() + 1, color);
        Gui.drawRect(rect.getX(), bottom - 1, right, bottom, color);
        Gui.drawRect(rect.getX(), rect.getY(), rect.getX() + 1, bottom, color);
        Gui.drawRect(right - 1, rect.getY(), right, bottom, color);
    }

    private static void drawDashedRectangle(DetectionViewportMapper.ScreenRect rect,
                                            int color) {
        int right = rect.getX() + rect.getWidth();
        int bottom = rect.getY() + rect.getHeight();
        for (int x = rect.getX(); x < right; x += 6) {
            int end = Math.min(right, x + 3);
            Gui.drawRect(x, rect.getY(), end, rect.getY() + 1, color);
            Gui.drawRect(x, bottom - 1, end, bottom, color);
        }
        for (int y = rect.getY(); y < bottom; y += 6) {
            int end = Math.min(bottom, y + 3);
            Gui.drawRect(rect.getX(), y, rect.getX() + 1, end, color);
            Gui.drawRect(right - 1, y, right, end, color);
        }
    }

    private double lastAppliedMinimumFeatureScore = VisionClientConfig.minimumFeatureScore;

    private static void draw(Minecraft minecraft, String text, int y, int color) {
        minecraft.fontRenderer.drawStringWithShadow(text, 4.0F, (float) y, color);
    }

    private static boolean hasGlContext() {
        try {
            return GL.getCapabilities().OpenGL30;
        } catch (IllegalStateException unavailable) {
            return false;
        }
    }

    private void observePublishedResult() {
        PerceptionSnapshot state = worker.snapshot();
        long previousSequence = observation.snapshot().getProcessedSequence();
        observation.observePipeline(state);
        VisionObservationStatus.Snapshot status = observation.snapshot();
        if (status.getProcessedSequence() > previousSequence) {
            long sequence = status.getProcessedSequence();
            if (!status.shortSessionId().equals(loggedResultSession)
                    || sequence % 25L == 0L) {
                RdpUavDebug.event("Vision", "RESULT_PUBLISHED",
                        "session", status.shortSessionId(),
                        "sequence", sequence,
                        "tracks", state.getTracks().size(),
                        "processing_ms", TimeUnit.NANOSECONDS.toMillis(
                                state.getProcessingNanos()));
                loggedResultSession = status.shortSessionId();
            }
        }
        if ((state.getState() == PerceptionSnapshot.State.UNAVAILABLE
                || state.getState() == PerceptionSnapshot.State.DEGRADED)
                && !state.getStatusReason().equals(loggedFailureReason)) {
            RdpUavDebug.event("Vision", "PIPELINE_FAILURE",
                    "session", status.shortSessionId(),
                    "state", state.getState(),
                    "reason", state.getStatusReason());
            loggedFailureReason = state.getStatusReason();
        } else if (state.getState() == PerceptionSnapshot.State.OPERATIONAL) {
            loggedFailureReason = "";
        }
    }

    private void recordOverlay(String mode, long sequence, long renderedAtNanos) {
        observation.overlayRendered(mode, sequence, renderedAtNanos);
        VisionObservationStatus.Snapshot status = observation.snapshot();
        if (status.getStage() == VisionObservationStatus.Stage.PRESENTED
                && (!status.shortSessionId().equals(loggedOverlaySession)
                || lastLoggedOverlaySequence < 0L
                || status.getPresentedSequence() != lastLoggedOverlaySequence
                && status.getPresentedSequence() % 25L == 0L)) {
            RdpUavDebug.event("Vision", "OVERLAY_RENDERED",
                    "session", status.shortSessionId(),
                    "mode", mode, "sequence", status.getPresentedSequence());
            loggedOverlaySession = status.shortSessionId();
            lastLoggedOverlaySequence = status.getPresentedSequence();
        } else if (status.getStage() == VisionObservationStatus.Stage.STATUS_ONLY
                && !status.shortSessionId().equals(loggedOverlaySession)) {
            RdpUavDebug.event("Vision", "OVERLAY_STATUS_RENDERED",
                    "session", status.shortSessionId(), "mode", mode,
                    "pipeline", status.getPipelineState());
            loggedOverlaySession = status.shortSessionId();
        }
    }
}
