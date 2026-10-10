package net.vas.rdp.uav.client.vision;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.ARBPixelBufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLSync;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Downscales and reads the currently bound McHeli feed framebuffer through fenced PBOs. */
final class AsyncPixelCapture {
    private static final int SLOT_COUNT = 3;
    private static final int SAMPLE_COUNT = 120;
    private final PixelBufferPool pixelBuffers;
    private final VisionProcessingWorker worker;
    private final PboSlot[] slots = new PboSlot[SLOT_COUNT];
    private final long[] captureSamples = new long[SAMPLE_COUNT];
    private int sampleCount;
    private int sampleCursor;
    private int framebuffer;
    private int renderbuffer;
    private int width;
    private int height;
    private long sequence;
    private long nextCaptureNanos;
    private long lastCaptureOverheadNanos;
    private long droppedFrames;
    private long captureWindowStartNanos;
    private long capturesInWindow;
    private volatile int measuredCaptureRateHz;
    private String currentWorldKey = "";
    private long cachedP95Nanos;
    private long lastP95CalculationNanos;

    AsyncPixelCapture(PixelBufferPool pixelBuffers, VisionProcessingWorker worker) {
        this.pixelBuffers = pixelBuffers;
        this.worker = worker;
        for (int i = 0; i < slots.length; i++) slots[i] = new PboSlot();
    }

    void capture(Minecraft minecraft) {
        if (!supported()) {
            worker.unavailable("OpenGL 3.2 sync, framebuffer blit, or PBO support is unavailable");
            return;
        }
        if (minecraft == null || minecraft.world == null
                || minecraft.displayWidth <= 0 || minecraft.displayHeight <= 0) {
            worker.unavailable("Client render source is unavailable");
            return;
        }
        String worldKey = System.identityHashCode(minecraft.world) + ":"
                + minecraft.world.provider.getDimension();
        if (!worldKey.equals(currentWorldKey)) {
            currentWorldKey = worldKey;
            nextCaptureNanos = 0L;
            discardPendingGpuFrames();
        }
        worker.setSource("mcheli-uav-feed", cameraId(minecraft));
        int targetWidth = VisionClientConfig.inputWidth;
        int targetHeight = VisionClientConfig.inputHeight;
        if (targetWidth != width || targetHeight != height || framebuffer == 0) {
            closeGlResources();
            try {
                initialize(targetWidth, targetHeight);
            } catch (RuntimeException failure) {
                worker.unavailable("Perception framebuffer initialization failed: "
                        + failure.getClass().getSimpleName());
                return;
            }
        }

        long now = System.nanoTime();
        pollOneReadyFrame(now);
        long interval = 1_000_000_000L / VisionClientConfig.captureRateHz;
        if (now < nextCaptureNanos) return;
        nextCaptureNanos = now + interval;
        PboSlot slot = freeSlot();
        if (slot == null) {
            droppedFrames++;
            return;
        }
        issueReadback(minecraft, slot, now);
    }

    long getLastCaptureOverheadNanos() { return lastCaptureOverheadNanos; }
    long getCaptureP95Nanos() {
        if (sampleCount == 0) return 0L;
        long now = System.nanoTime();
        if (now - lastP95CalculationNanos < 1_000_000_000L) return cachedP95Nanos;
        long[] sorted = Arrays.copyOf(captureSamples, sampleCount);
        Arrays.sort(sorted);
        int index = Math.max(0, (int) Math.ceil(sampleCount * 0.95D) - 1);
        cachedP95Nanos = sorted[index];
        lastP95CalculationNanos = now;
        return cachedP95Nanos;
    }
    long getDroppedFrames() { return droppedFrames; }
    int getMeasuredCaptureRateHz() { return measuredCaptureRateHz; }

    void clearWorld() {
        currentWorldKey = "";
        worker.disable("Waiting for McHeli UAV camera feed");
        nextCaptureNanos = 0L;
        captureWindowStartNanos = 0L;
        capturesInWindow = 0L;
        measuredCaptureRateHz = 0;
        sampleCount = 0;
        sampleCursor = 0;
        cachedP95Nanos = 0L;
        lastP95CalculationNanos = 0L;
    }

    boolean hasGlResources() {
        if (framebuffer != 0 || renderbuffer != 0) return true;
        for (PboSlot slot : slots) if (slot.buffer != 0) return true;
        return false;
    }

    void closeGlResources() {
        if (framebuffer == 0 && renderbuffer == 0
                && slots[0].buffer == 0 && slots[1].buffer == 0 && slots[2].buffer == 0) {
            return;
        }
        int previousPackBuffer = GL11.glGetInteger(
                ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_BINDING_ARB);
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        GL15.glBindBuffer(ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB, 0);
        for (PboSlot slot : slots) {
            if (slot.sync != null) GL32.glDeleteSync(slot.sync);
            slot.sync = null;
            slot.sequence = -1L;
            if (slot.buffer != 0) GL15.glDeleteBuffers(slot.buffer);
            slot.buffer = 0;
        }
        GL15.glBindBuffer(ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB,
                ownsBuffer(previousPackBuffer) ? 0 : previousPackBuffer);
        if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
        if (renderbuffer != 0) GL30.glDeleteRenderbuffers(renderbuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
        framebuffer = 0;
        renderbuffer = 0;
        width = 0;
        height = 0;
    }

    private void initialize(int frameWidth, int frameHeight) {
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        int previousPackBuffer = GL11.glGetInteger(
                ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_BINDING_ARB);
        try {
            framebuffer = GL30.glGenFramebuffers();
            renderbuffer = GL30.glGenRenderbuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_RGBA8,
                    frameWidth, frameHeight);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, renderbuffer);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                    != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Perception downscale framebuffer is incomplete");
            }
            width = frameWidth;
            height = frameHeight;
            long byteCount = (long) frameWidth * frameHeight * 4L;
            for (PboSlot slot : slots) {
                slot.buffer = GL15.glGenBuffers();
                GL15.glBindBuffer(ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB, slot.buffer);
                GL15.glBufferData(ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB,
                        byteCount, GL15.GL_STREAM_READ);
            }
            GL15.glBindBuffer(ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB, previousPackBuffer);
        } catch (RuntimeException failure) {
            closeGlResources();
            throw failure;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
            GL15.glBindBuffer(ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB, previousPackBuffer);
        }
    }

    private void pollOneReadyFrame(long now) {
        for (PboSlot slot : slots) {
            if (slot.sync == null) continue;
            int result = GL32.glClientWaitSync(slot.sync, 0, 0L);
            if (result == GL32.GL_WAIT_FAILED) {
                releaseSlot(slot);
                worker.unavailable("GPU pixel-readback fence reported GL_WAIT_FAILED");
                return;
            }
            if (result != GL32.GL_ALREADY_SIGNALED
                    && result != GL32.GL_CONDITION_SATISFIED) continue;

            byte[] pixels = pixelBuffers.acquire(width * height * 4);
            if (pixels == null) {
                droppedFrames++;
                releaseSlot(slot);
                return;
            }
            int previousPackBuffer = GL11.glGetInteger(
                    ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_BINDING_ARB);
            GL15.glBindBuffer(ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB, slot.buffer);
            ByteBuffer mapped = GL15.glMapBuffer(ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB,
                    GL15.GL_READ_ONLY, (ByteBuffer) null);
            if (mapped == null || mapped.remaining() < pixels.length) {
                if (mapped != null) GL15.glUnmapBuffer(ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB);
                GL15.glBindBuffer(ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB, previousPackBuffer);
                pixelBuffers.release(pixels);
                releaseSlot(slot);
                worker.unavailable("GPU pixel-readback buffer could not be mapped");
                return;
            }
            mapped.get(pixels, 0, pixels.length);
            boolean unmapped = GL15.glUnmapBuffer(ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB);
            GL15.glBindBuffer(ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB, previousPackBuffer);
            long capturedAt = slot.capturedAtNanos;
            long frameSequence = slot.sequence;
            String camera = slot.cameraId;
            releaseSlot(slot);
            if (!unmapped) {
                pixelBuffers.release(pixels);
                worker.unavailable("GPU pixel-readback buffer contents became invalid");
                return;
            }
            VisualFrame frame = VisualFrame.takeOwnership(frameSequence, capturedAt,
                    "mcheli-uav-feed", camera, width, height, pixels);
            worker.submit(frame);
            return;
        }
    }

    private void issueReadback(Minecraft minecraft, PboSlot slot, long now) {
        long startedAt = System.nanoTime();
        int sourceFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        if (sourceFramebuffer == 0) {
            worker.unavailable("McHeli feed render event has no bound source framebuffer");
            return;
        }

        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousPackBuffer = GL11.glGetInteger(
                ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_BINDING_ARB);
        int[] packParameters = {
                GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT),
                GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH),
                GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS),
                GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS),
                GL11.glGetInteger(GL11.GL_PACK_SWAP_BYTES),
                GL11.glGetInteger(GL11.GL_PACK_LSB_FIRST)
        };
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL30.glBlitFramebuffer(0, 0, minecraft.displayWidth, minecraft.displayHeight,
                    0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL15.glBindBuffer(ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB, slot.buffer);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, 0);
            GL11.glPixelStorei(GL11.GL_PACK_LSB_FIRST, 0);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, 0L);
            slot.sync = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            slot.sequence = sequence++;
            slot.capturedAtNanos = now;
            slot.cameraId = cameraId(minecraft);
        } catch (RuntimeException failure) {
            releaseSlot(slot);
            worker.unavailable("GPU frame capture failed: " + failure.getClass().getSimpleName());
        } finally {
            GL15.glBindBuffer(ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB, previousPackBuffer);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, packParameters[0]);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, packParameters[1]);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, packParameters[2]);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, packParameters[3]);
            GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, packParameters[4]);
            GL11.glPixelStorei(GL11.GL_PACK_LSB_FIRST, packParameters[5]);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            lastCaptureOverheadNanos = System.nanoTime() - startedAt;
            captureSamples[sampleCursor] = lastCaptureOverheadNanos;
            sampleCursor = (sampleCursor + 1) % SAMPLE_COUNT;
            sampleCount = Math.min(sampleCount + 1, SAMPLE_COUNT);
            updateCaptureRate(now);
        }
    }

    private PboSlot freeSlot() {
        for (PboSlot slot : slots) if (slot.sync == null) return slot;
        return null;
    }

    private void discardPendingGpuFrames() {
        for (PboSlot slot : slots) releaseSlot(slot);
    }

    private void releaseSlot(PboSlot slot) {
        if (slot.sync != null) GL32.glDeleteSync(slot.sync);
        slot.sync = null;
        slot.sequence = -1L;
        slot.cameraId = "";
    }

    private boolean ownsBuffer(int bufferId) {
        if (bufferId == 0) return false;
        for (PboSlot slot : slots) if (slot.buffer == bufferId) return true;
        return false;
    }

    private static boolean supported() {
        return GLContext.getCapabilities().OpenGL30
                && GLContext.getCapabilities().OpenGL32;
    }

    private static String cameraId(Minecraft minecraft) {
        if (minecraft == null) return "unavailable";
        Entity viewEntity = minecraft.getRenderViewEntity();
        return viewEntity == null ? "no-render-view"
                : viewEntity.getUniqueID().toString();
    }

    private void updateCaptureRate(long now) {
        if (captureWindowStartNanos == 0L) captureWindowStartNanos = now;
        capturesInWindow++;
        long elapsed = now - captureWindowStartNanos;
        if (elapsed >= 1_000_000_000L) {
            measuredCaptureRateHz = (int) Math.round(
                    capturesInWindow * 1_000_000_000.0D / elapsed);
            capturesInWindow = 0L;
            captureWindowStartNanos = now;
        }
    }

    private static final class PboSlot {
        int buffer;
        GLSync sync;
        long sequence = -1L;
        long capturedAtNanos;
        String cameraId = "";
    }
}
