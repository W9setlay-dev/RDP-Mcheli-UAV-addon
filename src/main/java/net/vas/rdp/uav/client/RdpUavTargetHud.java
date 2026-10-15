package net.vas.rdp.uav.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@SideOnly(Side.CLIENT)
public final class RdpUavTargetHud {
    private static final long CONTACT_REFRESH_INTERVAL_MS = 50L;
    private static final RdpUavTargetHud INSTANCE = new RdpUavTargetHud();
    private Telemetry telemetry;
    private long receivedAt;
    private UUID cachedTargetId;
    private Entity cachedTarget;
    private net.minecraft.world.World cachedWorld;
    private long nextTargetLookupAt;
    private final FloatBuffer modelViewBuffer = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
    private List<TargetOverlayGeometry.ScreenBox> contactBoxes = Collections.emptyList();
    private net.minecraft.world.World projectionWorld;
    private Entity projectionCamera;
    private long contactBoxesReceivedAt;
    private int projectionScaledWidth;
    private int projectionScaledHeight;

    private RdpUavTargetHud() { }

    public static RdpUavTargetHud instance() {
        return INSTANCE;
    }

    public void receive(Telemetry value) {
        Telemetry previous = telemetry;
        telemetry = value;
        receivedAt = System.currentTimeMillis();
        if (previous == null || value == null
                || !same(previous.targetUuid, value.targetUuid)) {
            cachedTargetId = null;
            cachedTarget = null;
            cachedWorld = null;
            nextTargetLookupAt = 0L;
        }
        if (previous == null || value == null
                || previous.active != value.active
                || previous.dimension != value.dimension
                || !same(previous.aircraftType, value.aircraftType)) {
            clearContactBoxes();
        }
    }

    public Telemetry snapshot() {
        return telemetry;
    }

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = net.minecraftforge.fml.client.FMLClientHandler.instance() == null
                ? null : net.minecraftforge.fml.client.FMLClientHandler.instance().getClient();
        if (mc == null || mc.player == null || mc.world == null) {
            clearContactBoxes();
            return;
        }
        drawProjectedContacts(mc);
        if (!RdpUavHudConfig.enabled || telemetry == null || !telemetry.active
                || System.currentTimeMillis() - receivedAt > 3000L) return;
        // Eligibility is decided on the server from McHeli's actual pilot/station
        // relationship. Repeating that lookup on the client breaks station-owned
        // UAV HUDs because the client does not always expose the controlled craft.
        if (RdpUavHudConfig.telemetryVisible) drawTelemetry(mc, event.getResolution().getScaledWidth(),
                event.getResolution().getScaledHeight());
        if (RdpUavHudConfig.anomalyAlertEnabled) {
            RdpUavAnomalyAlert.Alert alert = RdpUavAnomalyAlert.evaluate(mc.world, mc.player);
            if (alert != null) {
                RdpUavAnomalyAlert.drawAlert(mc, event.getResolution().getScaledWidth(),
                        event.getResolution().getScaledHeight(), alert);
            }
        }
    }

    /** Draws synchronized entity bounds or a point marker in world space. */
    @SubscribeEvent
    public void renderWorldTarget(RenderWorldLastEvent event) {
        updateProjectedContacts(event);
        if (!RdpUavHudConfig.markerVisible || telemetry == null || !telemetry.active
                || !MissionUiState.isMarkerVisible(telemetry.missionState, telemetry.markerState)
                || System.currentTimeMillis() - receivedAt > 3000L) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || telemetry.dimension != mc.world.provider.getDimension()) return;
        if (telemetry.targetUuid == null || telemetry.targetUuid.isEmpty()) {
            if (telemetry.hasPosition) drawPointMarker(mc, event.getPartialTicks());
            return;
        }
        Entity target = resolveTarget(mc, telemetry.targetUuid);
        if (target == null || target.isDead || target.world == null) return;

        float partialTicks = event.getPartialTicks();
        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partialTicks;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partialTicks;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partialTicks;
        AxisAlignedBB box = target.getEntityBoundingBox().offset(
                x - target.posX, y - target.posY, z - target.posZ);
        Entity camera = mc.getRenderViewEntity();
        if (camera == null) camera = mc.player;
        if (camera == null) return;
        Vec3d cameraPosition = camera.getPositionEyes(partialTicks);
        box = box.offset(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

        boolean locked = isTargetLocked(telemetry.targetState);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.depthMask(false);
            GL11.glLineWidth(FlirHudStyle.lineWidth(RdpUavHudConfig.flirEnabled, locked));
            int color = FlirHudStyle.bracketColor(RdpUavHudConfig.flirEnabled, locked);
            GlStateManager.color(
                    ((color >> 16) & 0xFF) / 255.0F,
                    ((color >> 8) & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F,
                    ((color >>> 24) & 0xFF) / 255.0F);
            drawTargetBrackets(box);
        } finally {
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
        }
    }

    public UUID selectProjectedContact(Minecraft mc, int mouseX, int mouseY) {
        if (canShowContactOverlay(mc) || mc.currentScreen != null
                || projectionWorld != mc.world
                || projectionCamera != renderCamera(mc)
                || !hasSupportedTelemetryProfile()
                || !matchesScaledResolution(mc)
                || System.currentTimeMillis() - contactBoxesReceivedAt > 500L) {
            return null;
        }
        TargetOverlayGeometry.ScreenBox selected =
                TargetOverlayGeometry.selectAt(contactBoxes, mouseX, mouseY);
        return selected == null ? null : selected.getTargetId();
    }

    private void updateProjectedContacts(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (canShowContactOverlay(mc) || mc.currentScreen != null) {
            clearContactBoxes();
            return;
        }
        net.vas.rdp.uav.profile.UavVehicleProfile profile =
                net.vas.rdp.uav.profile.UavVehicleProfiles.forAircraft(telemetry.aircraftType);
        if (profile == null || !profile.supportsTargetLock()) {
            clearContactBoxes();
            return;
        }

        Entity camera = mc.getRenderViewEntity();
        if (camera == null) camera = mc.player;
        if (camera == null || camera.world != mc.world) {
            clearContactBoxes();
            return;
        }
        long now = System.currentTimeMillis();
        if (projectionWorld == mc.world && projectionCamera == camera
                && matchesScaledResolution(mc)
                && now - contactBoxesReceivedAt < CONTACT_REFRESH_INTERVAL_MS) return;
        clearContactBoxes();
        float partialTicks = event.getPartialTicks();
        Vec3d cameraPosition = camera.getPositionEyes(partialTicks);
        if (!isFinite(cameraPosition)) return;

        float[] modelView = readMatrix(GL11.GL_MODELVIEW_MATRIX, modelViewBuffer);
        float[] projection = readMatrix(GL11.GL_PROJECTION_MATRIX, projectionBuffer);
        viewportBuffer.clear();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportBuffer);
        viewportBuffer.flip();
        if (viewportBuffer.remaining() < 4) return;
        int viewportX = viewportBuffer.get();
        int viewportY = viewportBuffer.get();
        int viewportWidth = viewportBuffer.get();
        int viewportHeight = viewportBuffer.get();
        net.minecraft.client.gui.ScaledResolution resolution =
                new net.minecraft.client.gui.ScaledResolution(mc);

        List<TargetOverlayGeometry.ScreenBox> projected = new ArrayList<>();
        AxisAlignedBB searchBounds = camera.getEntityBoundingBox().grow(profile.getLockRange());
        for (Entity target : mc.world.getEntitiesWithinAABBExcludingEntity(camera, searchBounds)) {
            if (target == null || target == camera || target == mc.player || target.isDead
                    || target.world != mc.world || !target.canBeCollidedWith()
                    || target.getDistance(camera) > profile.getLockRange()
                    || !net.vas.rdp.uav.target.TargetPolicy.isSelectableExactContact(
                    mc.world, target, mc.player)) {
                continue;
            }
            ResourceLocation registryId = EntityList.getKey(target);
            if (registryId == null) continue;

            AxisAlignedBB targetBounds = interpolatedBounds(target, partialTicks);
            Vec3d targetCenter = new Vec3d(
                    (targetBounds.minX + targetBounds.maxX) * 0.5D,
                    (targetBounds.minY + targetBounds.maxY) * 0.5D,
                    (targetBounds.minZ + targetBounds.maxZ) * 0.5D);
            RayTraceResult obstruction = mc.world.rayTraceBlocks(
                    cameraPosition, targetCenter, false, true, false);
            if (obstruction != null && obstruction.typeOfHit == RayTraceResult.Type.BLOCK) continue;

            String label = net.vas.rdp.uav.target.TargetClassifier.classify(registryId.toString())
                    + "  " + target.getName();
            TargetOverlayGeometry.ScreenBox screenBox =
                    TargetOverlayGeometry.projectBounds(target.getUniqueID(), label,
                            targetBounds.minX - cameraPosition.x,
                            targetBounds.minY - cameraPosition.y,
                            targetBounds.minZ - cameraPosition.z,
                            targetBounds.maxX - cameraPosition.x,
                            targetBounds.maxY - cameraPosition.y,
                            targetBounds.maxZ - cameraPosition.z,
                            modelView, projection, viewportX, viewportY,
                            viewportWidth, viewportHeight,
                            resolution.getScaledWidth(), resolution.getScaledHeight());
            if (screenBox != null) projected.add(screenBox);
        }
        contactBoxes = Collections.unmodifiableList(projected);
        projectionWorld = mc.world;
        projectionCamera = renderCamera(mc);
        contactBoxesReceivedAt = now;
        projectionScaledWidth = resolution.getScaledWidth();
        projectionScaledHeight = resolution.getScaledHeight();
    }

    private void drawProjectedContacts(Minecraft mc) {
        if (canShowContactOverlay(mc) || mc.currentScreen != null
                || projectionWorld != mc.world
                || projectionCamera != renderCamera(mc)
                || !matchesScaledResolution(mc)
                || System.currentTimeMillis() - contactBoxesReceivedAt > 500L) {
            clearContactBoxes();
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GlStateManager.disableDepth();
            for (TargetOverlayGeometry.ScreenBox box : contactBoxes) {
                int color = targetOverlayColor(box.getLabel());
                int left = box.getLeft();
                int top = box.getTop();
                int right = box.getRight();
                int bottom = box.getBottom();
                Gui.drawRect(left, top, right, top + 1, color);
                Gui.drawRect(left, bottom - 1, right, bottom, color);
                Gui.drawRect(left, top, left + 1, bottom, color);
                Gui.drawRect(right - 1, top, right, bottom, color);
                mc.fontRenderer.drawStringWithShadow(box.getLabel(),
                        left, Math.max(0, top - 10), color);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private boolean canShowContactOverlay(Minecraft mc) {
        return mc == null || mc.player == null || mc.world == null
                || !RdpUavHudConfig.enabled || !RdpUavHudConfig.contactOverlayVisible
                || telemetry == null || !telemetry.active
                || telemetry.dimension != mc.world.provider.getDimension()
                || System.currentTimeMillis() - receivedAt > 3000L;
    }

    private boolean hasSupportedTelemetryProfile() {
        if (telemetry == null || telemetry.aircraftType == null) return false;
        net.vas.rdp.uav.profile.UavVehicleProfile profile =
                net.vas.rdp.uav.profile.UavVehicleProfiles.forAircraft(telemetry.aircraftType);
        return profile != null && profile.supportsTargetLock();
    }

    private boolean matchesScaledResolution(Minecraft mc) {
        if (mc == null) return false;
        net.minecraft.client.gui.ScaledResolution resolution =
                new net.minecraft.client.gui.ScaledResolution(mc);
        return projectionScaledWidth == resolution.getScaledWidth()
                && projectionScaledHeight == resolution.getScaledHeight();
    }

    private static Entity renderCamera(Minecraft mc) {
        Entity camera = mc == null ? null : mc.getRenderViewEntity();
        return camera == null && mc != null ? mc.player : camera;
    }

    private void clearContactBoxes() {
        contactBoxes = Collections.emptyList();
        projectionWorld = null;
        projectionCamera = null;
        contactBoxesReceivedAt = 0L;
        projectionScaledWidth = 0;
        projectionScaledHeight = 0;
    }

    private static AxisAlignedBB interpolatedBounds(Entity entity, float partialTicks) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        return entity.getEntityBoundingBox().offset(
                x - entity.posX, y - entity.posY, z - entity.posZ);
    }

    private float[] readMatrix(int matrix, FloatBuffer buffer) {
        buffer.clear();
        GL11.glGetFloatv(matrix, buffer);
        buffer.flip();
        float[] values = new float[16];
        for (int i = 0; i < values.length && buffer.hasRemaining(); i++) {
            values[i] = buffer.get();
        }
        return values;
    }

    private static boolean isFinite(Vec3d vector) {
        return vector != null && Double.isFinite(vector.x)
                && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static int targetOverlayColor(String label) {
        if (label.startsWith("MILITARY")) return 0xFFFF6666;
        if (label.startsWith("VEHICLE")) return 0xFF66D9FF;
        if (label.startsWith("SCOUT")) return 0xFFFFFF66;
        if (label.startsWith("HOSTILE")) return 0xFFFFAA55;
        return 0xFFE0E0E0;
    }

    private void drawPointMarker(Minecraft mc, float partialTicks) {
        Entity camera = mc.getRenderViewEntity();
        if (camera == null) camera = mc.player;
        if (camera == null) return;
        Vec3d cameraPosition = camera.getPositionEyes(partialTicks);
        Vec3d marker = new Vec3d(telemetry.x, telemetry.y, telemetry.z);
        RayTraceResult obstruction = mc.world.rayTraceBlocks(
                cameraPosition, marker, false, true, false);
        if (obstruction != null && obstruction.typeOfHit == RayTraceResult.Type.BLOCK) return;

        double x = marker.x - cameraPosition.x;
        double y = marker.y - cameraPosition.y;
        double z = marker.z - cameraPosition.z;
        double halfSize = 0.5D;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.depthMask(false);
            GL11.glLineWidth(FlirHudStyle.lineWidth(RdpUavHudConfig.flirEnabled, false));
            int color = FlirHudStyle.bracketColor(RdpUavHudConfig.flirEnabled, false);
            GlStateManager.color(
                    ((color >> 16) & 0xFF) / 255.0F,
                    ((color >> 8) & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F,
                    ((color >>> 24) & 0xFF) / 255.0F);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(x - halfSize, y, z);
            GL11.glVertex3d(x + halfSize, y, z);
            GL11.glVertex3d(x, y - halfSize, z);
            GL11.glVertex3d(x, y + halfSize, z);
            GL11.glVertex3d(x, y, z - halfSize);
            GL11.glVertex3d(x, y, z + halfSize);
            GL11.glEnd();
        } finally {
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
        }
    }

    private Entity resolveTarget(Minecraft mc, String targetUuid) {
        if (mc == null || mc.world == null || targetUuid == null || targetUuid.trim().isEmpty()) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(targetUuid);
        } catch (IllegalArgumentException invalidUuid) {
            return null;
        }
        if (cachedTargetId != null && cachedTargetId.equals(id)
                && cachedWorld == mc.world && cachedTarget != null
                && !cachedTarget.isDead && cachedTarget.world == mc.world) return cachedTarget;

        long now = System.currentTimeMillis();
        if (cachedTargetId != null && cachedTargetId.equals(id)
                && cachedWorld == mc.world && now < nextTargetLookupAt) return null;
        cachedTargetId = id;
        cachedWorld = mc.world;
        cachedTarget = null;
        nextTargetLookupAt = now + 250L;
        if (mc.world.loadedEntityList == null) return null;
        for (Entity entity : mc.world.loadedEntityList) {
            if (entity != null && id.equals(entity.getUniqueID())
                    && !entity.isDead && entity.world == mc.world) {
                cachedTarget = entity;
                break;
            }
        }
        return cachedTarget;
    }

    private static boolean same(String first, String second) {
        return Objects.equals(first, second);
    }

    private static boolean isTargetLocked(String targetState) {
        return "TARGET_LOCKED".equals(targetState);
    }

    private static void drawTargetBrackets(AxisAlignedBB box) {
        double sx = Math.max(0.15D, Math.min(0.55D, (box.maxX - box.minX) * 0.28D));
        double sy = Math.max(0.15D, Math.min(0.55D, (box.maxY - box.minY) * 0.28D));
        double sz = Math.max(0.15D, Math.min(0.55D, (box.maxZ - box.minZ) * 0.28D));
        GL11.glBegin(GL11.GL_LINES);
        for (int ix = 0; ix <= 1; ix++) {
            double x = ix == 0 ? box.minX : box.maxX;
            double dx = ix == 0 ? sx : -sx;
            for (int iy = 0; iy <= 1; iy++) {
                double y = iy == 0 ? box.minY : box.maxY;
                double dy = iy == 0 ? sy : -sy;
                for (int iz = 0; iz <= 1; iz++) {
                    double z = iz == 0 ? box.minZ : box.maxZ;
                    double dz = iz == 0 ? sz : -sz;
                    GL11.glVertex3d(x, y, z); GL11.glVertex3d(x + dx, y, z);
                    GL11.glVertex3d(x, y, z); GL11.glVertex3d(x, y + dy, z);
                    GL11.glVertex3d(x, y, z); GL11.glVertex3d(x, y, z + dz);
                }
            }
        }
        double cx = (box.minX + box.maxX) * 0.5D;
        double cy = (box.minY + box.maxY) * 0.5D;
        double cz = (box.minZ + box.maxZ) * 0.5D;
        GL11.glVertex3d(cx - sx * 0.18D, cy, cz); GL11.glVertex3d(cx + sx * 0.18D, cy, cz);
        GL11.glVertex3d(cx, cy - sy * 0.18D, cz); GL11.glVertex3d(cx, cy + sy * 0.18D, cz);
        GL11.glEnd();
    }

    private void drawTelemetry(Minecraft mc, int width, int height) {
        FontRenderer font = mc.fontRenderer;
        int x = 8 + RdpUavHudConfig.xOffset;
        int y = 8 + RdpUavHudConfig.yOffset;
        String missionState = telemetry.missionState == null ? "UNKNOWN" : telemetry.missionState;
        String targetState = telemetry.targetState == null ? "NONE" : telemetry.targetState;
        String classification = telemetry.classification == null
                ? "UNKNOWN" : telemetry.classification;
        String targetName = telemetry.registryId == null || telemetry.registryId.trim().isEmpty()
                ? "NO TARGET" : telemetry.registryId;
        String entityName = telemetry.entityName == null ? "" : telemetry.entityName;
        String targetUuid = telemetry.targetUuid == null ? "" : telemetry.targetUuid;
        String wingmanState = telemetry.wingmanState == null ? "NONE" : telemetry.wingmanState;
        String wingmanNode = telemetry.wingmanNode == null ? "none" : telemetry.wingmanNode;
        String markerState = telemetry.markerState == null ? "HIDDEN" : telemetry.markerState;
        String failureReason = telemetry.failureReason == null ? "" : telemetry.failureReason;
        int color = isTargetLocked(targetState) ? 0xFF66FF66 : 0xFFFFCC66;
        GlStateManager.pushMatrix();
        GlStateManager.scale(RdpUavHudConfig.scale, RdpUavHudConfig.scale, 1.0F);
        x = (int) (x / RdpUavHudConfig.scale);
        y = (int) (y / RdpUavHudConfig.scale);
        font.drawStringWithShadow("RDP UAV SYSTEM", x, y, 0xFFFFFFFF);
        font.drawStringWithShadow("MISSION: " + (telemetry.missionId == null ? "UNKNOWN" : telemetry.missionId), x, y + 10, 0xFFFFFFFF);
        font.drawStringWithShadow("UAV: " + (telemetry.aircraftType == null ? "UNKNOWN" : telemetry.aircraftType), x, y + 20, 0xFFFFFFFF);
        font.drawStringWithShadow("STATE: " + missionState, x, y + 30, color);
        font.drawStringWithShadow("TARGET: " + targetName, x, y + 42, 0xFFFFFFFF);
        font.drawStringWithShadow("STATUS: " + targetState, x, y + 52, color);
        font.drawStringWithShadow("CLASS: " + classification + " "
                + String.format(java.util.Locale.ROOT, "%.2f", telemetry.classificationConfidence)
                + (telemetry.classificationConflict ? " CONFLICT" : ""), x, y + 62,
                telemetry.classificationConflict ? 0xFFFFCC66 : 0xFFFFFFFF);
        font.drawStringWithShadow("ENTITY: " + entityName, x, y + 72, 0xFFFFFFFF);
        font.drawStringWithShadow("UUID: " + targetUuid, x, y + 82, 0xFFFFFFFF);
        font.drawStringWithShadow("PRIORITY: " + (telemetry.priority == null ? "NONE" : telemetry.priority), x, y + 92, 0xFFFFFFFF);
        font.drawStringWithShadow("WINGMAN: " + wingmanState + " NODE " + wingmanNode, x, y + 102, 0xFFFFFFFF);
        font.drawStringWithShadow("DISTANCE: " + String.format(java.util.Locale.ROOT, "%.1fm", telemetry.distance)
                + (telemetry.outOfRange ? "  OUT OF RANGE" : ""), x, y + 112,
                telemetry.outOfRange ? 0xFFFF5555 : 0xFFFFFFFF);
        int detailY = y + 122;
        font.drawStringWithShadow("CONF: " + String.format(java.util.Locale.ROOT, "%.2f", telemetry.confidence)
                + "  VEL: " + String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f",
                telemetry.velocityX, telemetry.velocityY, telemetry.velocityZ), x, detailY, 0xFFFFFFFF);
        font.drawStringWithShadow("BEARING: " + String.format(java.util.Locale.ROOT, "%.1f", telemetry.bearing)
                + "  ELEV: " + String.format(java.util.Locale.ROOT, "%.1f", telemetry.elevation)
                + "  MARKER: " + markerState, x, detailY + 10, 0xFFFFFFFF);
        detailY += 20;
        if (!failureReason.isEmpty()) {
            font.drawStringWithShadow("FAILURE: " + failureReason, x, detailY, 0xFFFF5555);
            detailY += 10;
        }
        font.drawStringWithShadow("DIMENSION: " + telemetry.dimension, x, detailY, 0xFFFFFFFF);
        font.drawStringWithShadow("SCAN: " + (telemetry.scanState == null ? "NONE" : telemetry.scanState)
                + "  DEEP: " + (telemetry.deepScanState == null ? "NONE" : telemetry.deepScanState), x, detailY + 10, 0xFFFFFFFF);
        GlStateManager.popMatrix();
    }

    public static final class Telemetry {
        public final boolean active;
        public final boolean hasPosition;
        public final String missionId;
        public final String aircraftType;
        public final String missionState;
        public final String targetState;
        public final String registryId;
        public final String entityName;
        public final String targetUuid;
        public final String priority;
        public final String scanState;
        public final String deepScanState;
        public final String failureReason;
        public final String wingmanState;
        public final String wingmanNode;
        public final int dimension;
        public final boolean outOfRange;
        public double velocityX, velocityY, velocityZ, confidence, bearing, elevation;
        public boolean visible;
        public String lifecycle, markerState;
        public String classification = "UNKNOWN";
        public double classificationConfidence;
        public boolean classificationConflict;
        public final double x, y, z, distance;

        public Telemetry(boolean active, boolean hasPosition, String missionId, String aircraftType,
                         String missionState, String targetState, String registryId, String targetUuid,
                         String priority, String scanState, String deepScanState, int dimension,
                         double x, double y, double z, double distance) {
            this(active, hasPosition, missionId, aircraftType, missionState, targetState, registryId,
                    "", targetUuid, priority, scanState, deepScanState, dimension, false,
                    x, y, z, distance);
        }

        public Telemetry(boolean active, boolean hasPosition, String missionId, String aircraftType,
                         String missionState, String targetState, String registryId, String entityName,
                         String targetUuid, String priority, String scanState, String deepScanState,
                         int dimension, boolean outOfRange, double x, double y, double z, double distance) {
            this(active, hasPosition, missionId, aircraftType, missionState, targetState, registryId,
                    entityName, targetUuid, priority, scanState, deepScanState, "", "NONE", "none",
                    dimension, outOfRange, x, y, z, distance);
        }

        public Telemetry(boolean active, boolean hasPosition, String missionId, String aircraftType,
                         String missionState, String targetState, String registryId, String entityName,
                         String targetUuid, String priority, String scanState, String deepScanState,
                         String failureReason, String wingmanState, String wingmanNode,
                         int dimension, boolean outOfRange, double x, double y, double z, double distance) {
            this.active = active;
            this.hasPosition = hasPosition;
            this.missionId = missionId;
            this.aircraftType = aircraftType;
            this.missionState = missionState;
            this.targetState = targetState;
            this.registryId = registryId;
            this.entityName = entityName;
            this.targetUuid = targetUuid;
            this.priority = priority;
            this.scanState = scanState;
            this.deepScanState = deepScanState;
            this.failureReason = failureReason == null ? "" : failureReason;
            this.wingmanState = wingmanState == null ? "NONE" : wingmanState;
            this.wingmanNode = wingmanNode == null ? "none" : wingmanNode;
            this.dimension = dimension;
            this.outOfRange = outOfRange;
            this.x = x; this.y = y; this.z = z; this.distance = distance;
            this.velocityX = this.velocityY = this.velocityZ = 0.0D;
            this.confidence = hasPosition ? 1.0D : 0.0D;
            this.bearing = this.elevation = 0.0D;
            this.visible = hasPosition;
            this.lifecycle = "NONE";
            this.markerState = hasPosition ? "CONTACT" : "HIDDEN";
        }

        public void setRichTelemetry(double vx, double vy, double vz, double confidence,
                                     double bearing, double elevation, boolean visible,
                                     String lifecycle, String markerState) {
            this.velocityX = vx; this.velocityY = vy; this.velocityZ = vz;
            this.confidence = confidence; this.bearing = bearing; this.elevation = elevation;
            this.visible = visible; this.lifecycle = lifecycle == null ? "NONE" : lifecycle;
            this.markerState = markerState == null ? "HIDDEN" : markerState;
        }

        public void setClassificationProjection(String classification, double confidence,
                                                boolean conflict) {
            if (classification == null || classification.trim().isEmpty()
                    || !Double.isFinite(confidence) || confidence < 0.0D
                    || confidence > 1.0D) {
                throw new IllegalArgumentException("Invalid target classification projection");
            }
            this.classification = classification;
            this.classificationConfidence = confidence;
            this.classificationConflict = conflict;
        }
    }
}
