package net.vas.rdp.uav.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;
import net.vas.rdp.uav.network.RdpUavNetwork;
import net.vas.rdp.uav.target.TargetClassifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GuiMissionControl extends GuiScreen {
    private static volatile GuiMissionControl active;
    private String[] missionRows = new String[0];
    private String[] missionIds = new String[0];
    private int selected = -1;
    private boolean help;
    private int filterMode;
    private GuiTextField targetField;
    private GuiTextField filterField;
    private String status = "";
    private String pendingDangerAction;

    public GuiMissionControl() { active = this; }

    public static void receiveMissionData(String[] rows, String[] ids) {
        if (active == null) return;
        String selectedId = selectedMissionId();
        active.missionRows = rows == null ? new String[0] : rows;
        active.missionIds = ids == null ? new String[0] : ids;
        active.selected = -1;
        for (int i = 0; i < active.missionIds.length; i++) {
            if (selectedId != null && selectedId.equals(active.missionIds[i])) {
                active.selected = i;
                break;
            }
        }
        active.rebuildButtons();
    }

    public static String selectedMissionId() {
        if (active == null || active.selected < 0 || active.selected >= active.missionIds.length) return null;
        return active.missionIds[active.selected];
    }

    public static void requestRefresh() { RdpUavNetwork.requestMissionData(); }

    public static void authorizeSelected() {
        String id = selectedMissionId();
        if (id != null) RdpUavNetwork.authorizeAttack(id);
    }

    public static void armKamikazeSelected() {
        String id = selectedMissionId();
        if (id != null) RdpUavNetwork.armKamikaze(id);
    }

    public static void abortSelected() {
        String id = selectedMissionId();
        if (id != null) RdpUavNetwork.abortMission(id);
    }

    public static void createReconFromField() {
        if (active != null) active.createEntityMission(false);
    }

    public static void createAutoAttackFromField() {
        if (active != null) active.createEntityMission(true);
    }

    public static void createSquareFromField() {
        if (active != null) active.createSquareMission();
    }

    public void initGui() {
        int left = width / 2 - 220;
        if (targetField == null) {
            targetField = new GuiTextField(10, fontRenderer, left, 42, 210, 18);
            targetField.setMaxStringLength(96);
        }
        if (filterField == null) {
            filterField = new GuiTextField(11, fontRenderer, width / 2 + 15, 42, 205, 18);
            filterField.setMaxStringLength(32);
        }
        rebuildButtons();
        requestRefresh();
    }

    private void rebuildButtons() {
        buttonList.clear();
        int left = width / 2 - 220;
        int right = width / 2 + 15;
        buttonList.add(new GuiButton(0, left, height - 28, 62, 20, "Refresh"));
        buttonList.add(new GuiButton(1, left + 67, height - 28, 92, 20, help ? "Dashboard" : "Help"));
        buttonList.add(new GuiButton(2, right + 158, height - 28, 62, 20, "Close"));
        buttonList.add(new GuiButton(3, left, height - 54, 100, 20, "Authorize [A]"));
        buttonList.add(new GuiButton(4, left + 105, height - 54, 100, 20, "Abort [X]"));
        buttonList.add(new GuiButton(8, right + 105, height - 54, 115, 20, "Nose-dive [N]"));
        buttonList.add(new GuiButton(5, left, height - 80, 68, 20, "Recon [R]"));
        buttonList.add(new GuiButton(6, left + 73, height - 80, 68, 20, "Attack [K]"));
        buttonList.add(new GuiButton(7, left + 146, height - 80, 59, 20, "Point [P]"));
        buttonList.add(new GuiButton(9, right, height - 80, 100, 20, "Square strike"));
        buttonList.add(new GuiButton(10, right + 105, height - 80, 115, 20, "Lock crosshair [L]"));
        buttonList.add(new GuiButton(13, right, height - 54, 100, 20, "Clear lock [J]"));
        buttonList.add(new GuiButton(12, right, 66, 220, 20, filterLabel()));
        for (int i = 0, shown = 0; i < missionRows.length && shown < 11; i++) {
            if (!matchesFilter(missionRows[i])) continue;
            buttonList.add(new GuiButton(100 + i, left, 78 + shown * 17, 205, 15,
                    (i == selected ? "> " : "  ") + missionRows[i]));
            shown++;
        }
    }

    private String filterLabel() {
        return filterMode == 0 ? "Filter: ALL" : filterMode == 1 ? "Filter: ACTIVE" : "Filter: ALERTS";
    }

    private boolean matchesFilter(String row) {
        if (row == null) return false;
        String normalized = row.toLowerCase(java.util.Locale.ROOT);
        String query = filterField == null ? "" : filterField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        if (!query.isEmpty() && normalized.indexOf(query) < 0) return false;
        if (filterMode == 1) {
            return normalized.indexOf("complete") < 0 && normalized.indexOf("aborted") < 0
                    && normalized.indexOf("failed") < 0 && normalized.indexOf("detonated") < 0;
        }
        if (filterMode == 2) {
            return normalized.indexOf("failed") >= 0 || normalized.indexOf("lost") >= 0
                    || normalized.indexOf("out") >= 0;
        }
        return true;
    }

    private void requestData() { RdpUavNetwork.requestMissionData(); }

    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) requestData();
        else if (button.id == 1) { help = !help; rebuildButtons(); }
        else if (button.id == 2) mc.displayGuiScreen(null);
        else if (button.id == 3) confirmDangerAction("authorize", new Runnable() {
            @Override public void run() { authorizeSelected(); }
        }, "Authorizing the selected mission will arm server-side controls.");
        else if (button.id == 4) confirmDangerAction("abort", new Runnable() {
            @Override public void run() { abortSelected(); }
        }, "Aborting this mission will end the active task.");
        else if (button.id == 5) createEntityMission(false);
        else if (button.id == 6) confirmDangerAction("attack", new Runnable() {
            @Override public void run() { createEntityMission(true); }
        }, "Launch an autonomous hostile-target attack?");
        else if (button.id == 7) {
            RdpUavNetwork.createPointMission();
            status = "Point task submitted from your camera aim.";
        } else if (button.id == 8) confirmDangerAction("nosedive", new Runnable() {
            @Override public void run() { armKamikazeSelected(); status = "Nose-dive request submitted; only supported UAVs will arm."; }
        }, "Arm the kamikaze dive for the selected aircraft?");
        else if (button.id == 9) createSquareMission();
        else if (button.id == 10) lockCrosshairTarget();
        else if (button.id == 13) {
            RdpUavNetwork.clearAiTargetLock();
            status = "Target lock clear requested; recovery will be attempted.";
        }
        else if (button.id == 12) {
            filterMode = (filterMode + 1) % 3;
            rebuildButtons();
        } else if (button.id >= 100) {
            selected = button.id - 100;
            rebuildButtons();
        }
    }

    private void confirmDangerAction(String action, Runnable execute, String confirmText) {
        if (pendingDangerAction != null && pendingDangerAction.equals(action)) {
            pendingDangerAction = null;
            execute.run();
            return;
        }
        pendingDangerAction = action;
        status = confirmText + " Press again to confirm.";
    }

    private void lockCrosshairTarget() {
        RayTraceResult hit = mc.objectMouseOver;
        if (hit != null && hit.entityHit != null) {
            RdpUavNetwork.lockAiTarget(hit.entityHit.getUniqueID());
            status = "Target lock requested for " + hit.entityHit.getName() + ".";
            return;
        }
        status = "No entity under crosshair. Use Clear lock [J] to request recovery.";
    }

    private void createEntityMission(boolean attack) {
        String target = targetField == null ? "" : targetField.getText().trim();
        if (target.indexOf(':') <= 0 || target.indexOf(':') == target.length() - 1) {
            status = "Enter a target as namespace:id.";
            return;
        }
        RdpUavNetwork.createMission(target, attack);
        status = attack ? "Autonomous attack submitted; WingMan arms after target lock."
                : "Recon mission submitted.";
    }

    private void createSquareMission() {
        String parameters = targetField == null ? "" : targetField.getText().trim();
        if (!parameters.matches("-?[0-9.]+\\s+-?[0-9.]+\\s+-?[0-9.]+(\\s+[0-9.]+)?")) {
            status = "Square format: centerX centerY centerZ [size].";
            return;
        }
        RdpUavNetwork.createSquareStrike(parameters);
        status = "Autonomous square strike submitted to WingMan.";
    }

    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (targetField != null && targetField.textboxKeyTyped(typedChar, keyCode)) return;
        if (filterField != null && filterField.textboxKeyTyped(typedChar, keyCode)) {
            rebuildButtons();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (targetField != null) targetField.mouseClicked(mouseX, mouseY, mouseButton);
        if (filterField != null) filterField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "RDP UAV TACTICAL OPERATOR", width / 2, 12, 0xFFFFFF);
        if (help) {
            drawHelp();
        } else {
            int left = width / 2 - 220;
            int right = width / 2 + 15;
            drawString(fontRenderer, "MISSION TARGET / AREA", left, 30, 0xB0D8FF);
            if (targetField != null) targetField.drawTextBox();
            drawString(fontRenderer, "MISSION FILTER", right, 30, 0xB0D8FF);
            if (filterField != null) filterField.drawTextBox();
            if (!status.isEmpty()) drawString(fontRenderer, status, left, 61, 0xFFCC66);
            drawMissions(left, right);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawMissions(int left, int right) {
        drawRect(left - 4, 74, left + 209, height - 89, 0x66000000);
        drawRect(right - 4, 74, right + 224, height - 89, 0x66000000);
        drawString(fontRenderer, "SERVER MISSIONS", left, 66, 0xB0D8FF);
        if (missionRows.length == 0) {
            drawString(fontRenderer, "No missions assigned.", left, 85, 0xAAAAAA);
        } else {
            int y = 82;
            for (String row : missionRows) {
                if (!matchesFilter(row)) continue;
                drawString(fontRenderer, row, left, y, 0xFFFFFF);
                y += 17;
                if (y > height - 100) break;
            }
        }
        drawString(fontRenderer, "SELECTED MISSION", right, 66, 0xB0D8FF);
        if (selected >= 0 && selected < missionRows.length) {
            drawString(fontRenderer, missionRows[selected], right, 86, 0xFFFFFF);
            drawString(fontRenderer, "ID: " + missionIds[selected], right, 101, 0xAAAAAA);
        } else {
            drawString(fontRenderer, "Select a mission to inspect.", right, 86, 0xAAAAAA);
        }
        drawThreatSuggestions(right, 122);
        drawTelemetry(right, 185);
    }

    private void drawThreatSuggestions(int x, int y) {
        drawString(fontRenderer, "THREAT SUGGESTIONS", x, y, 0xB0D8FF);
        List<String> suggestions = collectThreatSuggestions();
        if (suggestions.isEmpty()) {
            drawString(fontRenderer, "No hostile targets in scan range.", x, y + 18, 0xAAAAAA);
            return;
        }
        for (int i = 0; i < suggestions.size() && i < 5; i++) {
            drawString(fontRenderer, suggestions.get(i), x, y + 18 + i * 12, 0xFFCC66);
        }
    }

    private List<String> collectThreatSuggestions() {
        List<String> result = new ArrayList<String>();
        if (mc == null || mc.world == null || mc.player == null) return result;
        for (Entity entity : mc.world.loadedEntityList) {
            if (entity == null || entity.isDead || entity == mc.player) continue;
            ResourceLocation id = EntityList.getKey(entity);
            if (id == null) continue;
            String classification = TargetClassifier.classify(id.toString());
            if ("UNKNOWN".equals(classification)) continue;
            double distance = entity.getDistanceSq(mc.player.posX, mc.player.posY, mc.player.posZ);
            if (distance > 250000.0D) continue;
            result.add(String.format(java.util.Locale.ROOT, "%s  [%s]  %.1fm",
                    id.toString(), classification, Math.sqrt(distance)));
        }
        Collections.sort(result);
        return result;
    }

    private void drawTelemetry(int x, int y) {
        RdpUavTargetHud.Telemetry data = RdpUavTargetHud.instance().snapshot();
        drawString(fontRenderer, "LIVE TARGET TELEMETRY", x, y, 0xB0D8FF);
        if (data == null || !data.active) {
            drawString(fontRenderer, "No active telemetry link.", x, y + 18, 0xAAAAAA);
            return;
        }
        int color = "TARGET_LOCKED".equals(data.targetState) ? 0x66FF66 : 0xFFCC66;
        drawString(fontRenderer, "STATE: " + data.missionState, x, y + 18, color);
        drawString(fontRenderer, "LOCK: " + data.targetState, x, y + 31, color);
        drawString(fontRenderer, "TARGET: " + (data.registryId == null ? "NONE" : data.registryId),
                x, y + 44, 0xFFFFFF);
        drawString(fontRenderer, "CLASS: " + TargetClassifier.classify(data.registryId == null ? "" : data.registryId),
                x, y + 57, 0xFFFFFF);
        drawString(fontRenderer, "PRIORITY: " + data.priority + "  RANGE: "
                + String.format(java.util.Locale.ROOT, "%.1fm", data.distance), x, y + 70, 0xFFFFFF);
        drawString(fontRenderer, "SCAN: " + data.scanState + " / " + data.deepScanState,
                x, y + 83, 0xFFFFFF);
        drawString(fontRenderer, "WINGMAN: " + data.wingmanState, x, y + 96, 0xFFFFFF);
        if (!data.failureReason.isEmpty()) drawString(fontRenderer, "ALERT: " + data.failureReason,
                x, y + 109, 0xFF5555);
    }

    private void drawHelp() {
        int x = width / 2 - 210;
        drawString(fontRenderer, "AI OPERATOR CONTROLS", x, 42, 0xB0D8FF);
        drawString(fontRenderer, "Select a server-owned mission to inspect its state and aircraft.", x, 62, 0xFFFFFF);
        drawString(fontRenderer, "Use the filter field or cycle ALL / ACTIVE / ALERTS.", x, 76, 0xFFFFFF);
        drawString(fontRenderer, "Lock crosshair sends the existing server-validated AI target-lock request.", x, 90, 0xFFFFFF);
        drawString(fontRenderer, "Dangerous actions require a second press to confirm before execution.", x, 104, 0xFFCC66);
        drawString(fontRenderer, "Threat suggestions are derived from loaded hostile entities and target classification.", x, 118, 0xFFFFFF);
        drawString(fontRenderer, "Square strike uses: centerX centerY centerZ [size].", x, 132, 0xFFFFFF);
        drawString(fontRenderer, "Command fallback: /rdpuav mission list", x, 150, 0xAAAAAA);
    }

    public void onGuiClosed() {
        if (active == this) active = null;
        super.onGuiClosed();
    }
}
