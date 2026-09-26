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
        Minecraft mc = net.minecraftforge.fml.client.FMLClientHandler.instance().getClient();
        if (mc.player == null || mc.world == null || System.currentTimeMillis() - receivedAt > 3000L) return;
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
        if (RdpUavHudConfig.markerVisible && telemetry.hasPosition
                && (telemetry.targetUuid == null || telemetry.targetUuid.isEmpty())
                && MissionUiState.isMarkerVisible(telemetry.missionState, telemetry.markerState)) drawMarker(mc,
                event.getResolution().getScaledWidth(), event.getResolution().getScaledHeight());
    }

    /** Draws a target lock around the synchronized entity's actual 3D bounds. */
    @SubscribeEvent
    public void renderWorldTarget(RenderWorldLastEvent event) {
        if (!RdpUavHudConfig.markerVisible || telemetry == null || !telemetry.active
                || telemetry.targetUuid == null || telemetry.targetUuid.isEmpty()
                || !MissionUiState.isMarkerVisible(telemetry.missionState, telemetry.markerState)
                || System.currentTimeMillis() - receivedAt > 3000L) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null
                || telemetry.dimension != mc.world.provider.getDimension()) return;
        Entity target = resolveTarget(mc, telemetry.targetUuid);
        if (target == null || target.isDead) return;

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

        boolean locked = "TARGET_LOCKED".equals(telemetry.targetState);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GL11.glLineWidth(2.0F);
        if (locked) GlStateManager.color(1.0F, 0.12F, 0.12F, 0.95F);
        else GlStateManager.color(1.0F, 0.72F, 0.12F, 0.8F);
        drawTargetBrackets(box);
        GlStateManager.popMatrix();
        GL11.glPopAttrib();
    }

    private Entity resolveTarget(Minecraft mc, String targetUuid) {
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
        for (Entity entity : mc.world.loadedEntityList) {
            if (id.equals(entity.getUniqueID())) {
                cachedTarget = entity;
                break;
            }
        }
        return cachedTarget;
    }

    private static boolean same(String first, String second) {
        return first == null ? second == null : first.equals(second);
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
        int color = telemetry.targetState.equals("TARGET_LOCKED") ? 0xFF66FF66 : 0xFFFFCC66;
        GlStateManager.pushMatrix();
        GlStateManager.scale(RdpUavHudConfig.scale, RdpUavHudConfig.scale, 1.0F);
        x = (int) (x / RdpUavHudConfig.scale);
        y = (int) (y / RdpUavHudConfig.scale);
        String target = telemetry.registryId == null ? "NO TARGET" : telemetry.registryId;
        font.drawStringWithShadow("RDP UAV SYSTEM", x, y, 0xFFFFFFFF);
        font.drawStringWithShadow("MISSION: " + telemetry.missionId, x, y + 10, 0xFFFFFFFF);
        font.drawStringWithShadow("UAV: " + telemetry.aircraftType, x, y + 20, 0xFFFFFFFF);
        font.drawStringWithShadow("STATE: " + telemetry.missionState, x, y + 30, color);
        font.drawStringWithShadow("TARGET: " + target, x, y + 42, 0xFFFFFFFF);
        font.drawStringWithShadow("STATUS: " + telemetry.targetState, x, y + 52, color);
        font.drawStringWithShadow("ENTITY: " + telemetry.entityName, x, y + 62, 0xFFFFFFFF);
        font.drawStringWithShadow("UUID: " + telemetry.targetUuid, x, y + 72, 0xFFFFFFFF);
        font.drawStringWithShadow("PRIORITY: " + telemetry.priority, x, y + 82, 0xFFFFFFFF);
        font.drawStringWithShadow("WINGMAN: " + telemetry.wingmanState
                + " NODE " + telemetry.wingmanNode, x, y + 92, 0xFFFFFFFF);
        font.drawStringWithShadow("DISTANCE: " + String.format(java.util.Locale.ROOT, "%.1fm", telemetry.distance)
                + (telemetry.outOfRange ? "  OUT OF RANGE" : ""), x, y + 102,
                telemetry.outOfRange ? 0xFFFF5555 : 0xFFFFFFFF);
        int detailY = y + 112;
        font.drawStringWithShadow("CONF: " + String.format(java.util.Locale.ROOT, "%.2f", telemetry.confidence)
                + "  VEL: " + String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f",
                telemetry.velocityX, telemetry.velocityY, telemetry.velocityZ), x, detailY, 0xFFFFFFFF);
        font.drawStringWithShadow("BEARING: " + String.format(java.util.Locale.ROOT, "%.1f", telemetry.bearing)
                + "  ELEV: " + String.format(java.util.Locale.ROOT, "%.1f", telemetry.elevation)
                + "  MARKER: " + telemetry.markerState, x, detailY + 10, 0xFFFFFFFF);
        detailY += 20;
        if (!telemetry.failureReason.isEmpty()) {
            font.drawStringWithShadow("FAILURE: " + telemetry.failureReason, x, detailY, 0xFFFF5555);
            detailY += 10;
        }
        font.drawStringWithShadow("DIMENSION: " + telemetry.dimension, x, detailY, 0xFFFFFFFF);
        font.drawStringWithShadow("SCAN: " + telemetry.scanState
                + "  DEEP: " + telemetry.deepScanState, x, detailY + 10, 0xFFFFFFFF);
        GlStateManager.popMatrix();
    }

    private void drawMarker(Minecraft mc, int width, int height) {
        if (telemetry.dimension != mc.world.provider.getDimension()) return;
        net.minecraft.entity.Entity camera = mc.getRenderViewEntity();
        if (camera == null) camera = mc.player;
        if (camera == null) return;
        Vec3d cameraPosition = camera.getPositionEyes(mc.getRenderPartialTicks());
        Vec3d targetPosition = new Vec3d(telemetry.x, telemetry.y, telemetry.z);
        RayTraceResult obstruction = mc.world.rayTraceBlocks(cameraPosition, targetPosition,
                false, true, false);
        if (obstruction != null && obstruction.typeOfHit == RayTraceResult.Type.BLOCK) return;
        double[] screen = project(mc, telemetry.x, telemetry.y, telemetry.z, width, height);
        if (screen == null) return;
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.color(0.3F, 1.0F, 0.3F, telemetry.targetState.equals("TARGET_LOCKED") ? 1.0F : 0.65F);
        GL11.glLineWidth(2.0F);
        double size = 10.0D;
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2d(screen[0] - size, screen[1] - size);
        GL11.glVertex2d(screen[0] + size, screen[1] - size);
        GL11.glVertex2d(screen[0] + size, screen[1] + size);
        GL11.glVertex2d(screen[0] - size, screen[1] + size);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();

        if (telemetry.registryId != null && !telemetry.registryId.isEmpty()) {
            FontRenderer font = mc.fontRenderer;
            int labelX = (int) Math.round(screen[0] + 12.0D);
            int labelY = (int) Math.round(screen[1] - 12.0D);
            font.drawStringWithShadow(telemetry.registryId, labelX, labelY, 0xFFFF5555);
            font.drawStringWithShadow("LOCK " + telemetry.targetState, labelX, labelY + 10, 0xFFFFFFFF);
        }
    }

    private double[] project(Minecraft mc, double x, double y, double z, int width, int height) {
        net.minecraft.entity.Entity origin = mc.getRenderViewEntity();
        if (origin == null) origin = mc.player;
        if (origin == null) return null;
        double px = origin.prevPosX + (origin.posX - origin.prevPosX) * mc.getRenderPartialTicks();
        double py = origin.prevPosY + (origin.posY - origin.prevPosY) * mc.getRenderPartialTicks()
                + origin.getEyeHeight();
        double pz = origin.prevPosZ + (origin.posZ - origin.prevPosZ) * mc.getRenderPartialTicks();
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        double yaw = Math.toRadians(origin.prevRotationYaw
                + (origin.rotationYaw - origin.prevRotationYaw) * mc.getRenderPartialTicks());
        double pitch = Math.toRadians(origin.prevRotationPitch
                + (origin.rotationPitch - origin.prevRotationPitch) * mc.getRenderPartialTicks());
        double forwardX = -Math.sin(yaw) * Math.cos(pitch);
        double forwardY = -Math.sin(pitch);
        double forwardZ = Math.cos(yaw) * Math.cos(pitch);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double upX = Math.sin(yaw) * Math.sin(pitch);
        double upY = Math.cos(pitch);
        double upZ = -Math.cos(yaw) * Math.sin(pitch);
        double depth = dx * forwardX + dy * forwardY + dz * forwardZ;
        if (depth <= 0.05D) return null;
        double fov = Math.toRadians(mc.gameSettings.fovSetting);
        double focalY = height / (2.0D * Math.tan(fov / 2.0D));
        double focalX = focalY * ((double) width / (double) height);
        return new double[] {width / 2.0D + (dx * rightX + dz * rightZ) * focalX / depth,
                height / 2.0D - (dx * upX + dy * upY + dz * upZ) * focalY / depth};
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
