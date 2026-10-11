package net.vas.rdp.uav.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.Objects;
import java.util.UUID;

@SideOnly(Side.CLIENT)
public final class RdpUavTargetHud {
    private static final RdpUavTargetHud INSTANCE = new RdpUavTargetHud();
    private Telemetry telemetry;
    private long receivedAt;
    private UUID cachedTargetId;
    private Entity cachedTarget;
    private net.minecraft.world.World cachedWorld;
    private long nextTargetLookupAt;

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
    }

    public Telemetry snapshot() {
        return telemetry;
    }

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL
                || !RdpUavHudConfig.enabled || telemetry == null || !telemetry.active) return;
        Minecraft mc = net.minecraftforge.fml.client.FMLClientHandler.instance() == null
                ? null : net.minecraftforge.fml.client.FMLClientHandler.instance().getClient();
        if (mc == null || mc.player == null || mc.world == null
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
        font.drawStringWithShadow("ENTITY: " + entityName, x, y + 62, 0xFFFFFFFF);
        font.drawStringWithShadow("UUID: " + targetUuid, x, y + 72, 0xFFFFFFFF);
        font.drawStringWithShadow("PRIORITY: " + (telemetry.priority == null ? "NONE" : telemetry.priority), x, y + 82, 0xFFFFFFFF);
        font.drawStringWithShadow("WINGMAN: " + wingmanState + " NODE " + wingmanNode, x, y + 92, 0xFFFFFFFF);
        font.drawStringWithShadow("DISTANCE: " + String.format(java.util.Locale.ROOT, "%.1fm", telemetry.distance)
                + (telemetry.outOfRange ? "  OUT OF RANGE" : ""), x, y + 102,
                telemetry.outOfRange ? 0xFFFF5555 : 0xFFFFFFFF);
        int detailY = y + 112;
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
    }
}
