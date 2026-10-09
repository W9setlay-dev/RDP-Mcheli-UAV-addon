package net.vas.rdp.uav.client.vision;

import com.norwood.mcheli.uav.WidgetUavCameraFeed;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GLContext;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Client-only capture, cleanup, and optional diagnostics for McHeli's UAV feed. */
@SideOnly(Side.CLIENT)
public final class VisionClientEvents {
    private static final long FEED_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(2L);
    private final PixelBufferPool pixelBuffers = new PixelBufferPool();
    private final VisionProcessingWorker worker =
            new VisionProcessingWorker(pixelBuffers, VisionClientConfig.minimumFeatureScore);
    private final AsyncPixelCapture capture = new AsyncPixelCapture(pixelBuffers, worker);
    private long lastFeedEventNanos;
    private boolean feedObserved;
    private boolean resetGpuResources;

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
        try {
            capture.capture(minecraft);
        } catch (RuntimeException failure) {
            worker.unavailable("Client capture failed: " + failure.getClass().getSimpleName());
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            feedObserved = false;
            capture.clearWorld();
            resetGpuResources = true;
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (!VisionClientConfig.enabled || !VisionClientConfig.overlayEnabled
                || event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        PerceptionSnapshot state = worker.snapshot();
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
    }

    private double lastAppliedMinimumFeatureScore = VisionClientConfig.minimumFeatureScore;

    private static void draw(Minecraft minecraft, String text, int y, int color) {
        minecraft.fontRenderer.drawStringWithShadow(text, 4.0F, (float) y, color);
    }

    private static boolean hasGlContext() {
        try {
            return GLContext.getCapabilities().OpenGL30;
        } catch (IllegalStateException unavailable) {
            return false;
        }
    }
}
