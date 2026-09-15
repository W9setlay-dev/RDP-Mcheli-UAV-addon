package net.vas.rdp.uav.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.vas.rdpcore.api.RDPAPI;
import net.vas.rdpcore.region.Hotspot;
import net.vas.rdpcore.world.RDPWorldState;
import net.vas.rdpcore.world.Scar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
public final class RdpUavAnomalyAlert {
    private static volatile Alert currentAlert;
    private static volatile long lastShownAtMs;

    private RdpUavAnomalyAlert() { }

    public static Alert evaluate(World world, Entity observer) {
        if (world == null || RdpUavHudConfig.anomalyAlertEnabled == false) return null;
        double globalRdp = RDPAPI.getGlobalRDPLevel(world);
        double regionalRdp = 0.0D;
        if (observer != null) {
            int cx = (int) Math.floor(observer.posX / 16.0D);
            int cz = (int) Math.floor(observer.posZ / 16.0D);
            regionalRdp = RDPAPI.getRegionalRDPLevel(world, cx, cz);
        }
        String biomeName = "";
        if (observer != null) {
            biomeName = world.getBiome(observer.getPosition()).getRegistryName() == null
                    ? ""
                    : world.getBiome(observer.getPosition()).getRegistryName().toString();
        }
        RDPWorldState state = RDPAPI.getWorldState(world);
        Map<String, Hotspot> hotspots = state == null ? null : state.getHotspots();
        Map<String, Scar> scars = state == null ? null : state.getScars();
        Alert alert = evaluateAgainstRdpData(globalRdp, regionalRdp, biomeName,
                hotspots, scars, RDPAPI.getCurrentStage(world) == null ? "UNKNOWN" : RDPAPI.getCurrentStage(world).name());
        if (alert == null) {
            currentAlert = null;
            return null;
        }
        long now = System.currentTimeMillis();
        if (currentAlert != null && currentAlert.kind.equals(alert.kind)
                && now - lastShownAtMs < RdpUavHudConfig.anomalyAlertCooldownMs) {
            return null;
        }
        if (currentAlert != null && now - lastShownAtMs > RdpUavHudConfig.anomalyAlertDisplayMs) {
            currentAlert = null;
        }
        currentAlert = alert;
        lastShownAtMs = now;
        return alert;
    }

    public static Alert evaluateAgainstRdpData(double globalRdp, double regionalRdp,
                                              String biomeName, Map<String, Hotspot> hotspots,
                                              Map<String, Scar> scars, String stageName) {
        if (Double.isNaN(globalRdp) || Double.isInfinite(globalRdp)) globalRdp = 0.0D;
        if (Double.isNaN(regionalRdp) || Double.isInfinite(regionalRdp)) regionalRdp = 0.0D;
        boolean anyHotspot = hotspots != null && !hotspots.isEmpty();
        boolean anyScar = scars != null && !scars.isEmpty();
        boolean distortedBiome = isDistortedBiome(biomeName);
        double threshold = Math.max(globalRdp, regionalRdp);
        if (threshold < RdpUavHudConfig.anomalyAlertThreshold && !anyHotspot && !anyScar && !distortedBiome) {
            return null;
        }

        String kind = "RDP_SURGE";
        double severity = 0.55D;
        String detail = "RDP pressure rising";

        if (anyHotspot) {
            for (Hotspot hotspot : hotspots.values()) {
                if (hotspot == null || hotspot.type == null) continue;
                String type = hotspot.type.toLowerCase(java.util.Locale.ROOT);
                if (type.contains("gravity") || type.contains("grav")) {
                    kind = "GRAVITY_FIELD";
                    severity = 1.0D;
                    detail = "Gravity field detected near the active UAV corridor.";
                    break;
                }
                if (type.contains("dimensional") || type.contains("inter") || type.contains("rift")) {
                    kind = "INTERDIMENSIONAL_FIELD";
                    severity = 0.95D;
                    detail = "Dimensional instability is active in the local zone.";
                    break;
                }
                if (type.contains("scar") || type.contains("spatial") || type.contains("distort")) {
                    kind = "RDP_HOTSPOT";
                    severity = 0.85D;
                    detail = "Regional distortion is destabilizing the surrounding space.";
                    break;
                }
            }
        }

        if (kind.equals("RDP_SURGE") && anyScar) {
            kind = "RDP_SCAR";
            severity = 0.75D;
            detail = "A scar or repeated fault line has been recorded in the world state.";
        }

        if (kind.equals("RDP_SURGE") && distortedBiome) {
            kind = "DISTORTED_BIOME";
            severity = 0.7D;
            detail = "The biome signature matches a distorted or hostile RDP region.";
        }

        if (kind.equals("RDP_SURGE") && threshold >= RdpUavHudConfig.anomalyAlertThreshold) {
            detail = "The local RDP profile is elevated and may affect movement or target confidence.";
        }

        String label = kind.equals("RDP_SURGE") ? "RDP SURGE" : kind.replace('_', ' ');
        return new Alert(kind, label, detail + " Global=" + formatValue(globalRdp)
                + " Regional=" + formatValue(regionalRdp) + " Stage=" + (stageName == null ? "UNKNOWN" : stageName), severity);
    }

    public static void drawAlert(Minecraft mc, int width, int height, Alert alert) {
        if (mc == null || mc.fontRenderer == null || alert == null) return;
        FontRenderer font = mc.fontRenderer;
        int x = width / 2 - 160;
        int y = 14;
        GlStateManager.pushMatrix();
        GlStateManager.scale(RdpUavHudConfig.scale, RdpUavHudConfig.scale, 1.0F);
        x = (int) (x / RdpUavHudConfig.scale);
        y = (int) (y / RdpUavHudConfig.scale);
        font.drawStringWithShadow("RDP ALERT", x, y, 0xFFFF5555);
        font.drawStringWithShadow(alert.summary, x, y + 10, 0xFFFFFF);
        font.drawStringWithShadow(alert.detail, x, y + 20, 0xFFC9C9C9);
        GlStateManager.popMatrix();
    }

    public static Alert current() {
        return currentAlert;
    }

    private static boolean isDistortedBiome(String biomeName) {
        if (biomeName == null || biomeName.trim().isEmpty()) return false;
        String normalized = biomeName.toLowerCase(java.util.Locale.ROOT);
        String[] hints = RdpUavHudConfig.anomalyBiomeHints;
        if (hints == null) return false;
        for (String hint : hints) {
            if (hint == null) continue;
            if (normalized.contains(hint.toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }

    private static String formatValue(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    public static final class Alert {
        public final String kind;
        public final String summary;
        public final String detail;
        public final double severity;

        public Alert(String kind, String summary, String detail, double severity) {
            this.kind = kind == null ? "RDP_SURGE" : kind;
            this.summary = summary == null ? "RDP SURGE" : summary;
            this.detail = detail == null ? "" : detail;
            this.severity = severity;
        }
    }
}
