package net.vas.rdp.uav.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.vas.rdp.uav.network.RdpUavNetwork;
import java.io.IOException;

public final class GuiMissionControl extends GuiScreen {
    private static volatile GuiMissionControl active;
    private String[] missionRows = new String[0];
    private String[] missionIds = new String[0];
    private int selected = -1;
    private boolean help;
    private GuiTextField targetField;
    private String status = "";

    public GuiMissionControl() { active = this; }

    public static void receiveMissionData(String[] rows, String[] ids) {
        if (active != null) {
            String selectedId = active.selectedMissionId();
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
    }

    public static String selectedMissionId() {
        if (active == null || active.selected < 0 || active.selected >= active.missionIds.length) {
            return null;
        }
        return active.missionIds[active.selected];
    }

    public static void requestRefresh() {
        RdpUavNetwork.requestMissionData();
    }

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

    public void initGui() {
        if (targetField == null) {
            targetField = new GuiTextField(10, fontRenderer, width / 2 - 120, 36, 240, 18);
            targetField.setMaxStringLength(96);
        }
        rebuildButtons();
        requestData();
    }

    private void rebuildButtons() {
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 102, height - 32, 65, 20, "Refresh"));
        buttonList.add(new GuiButton(1, width / 2 - 32, height - 32, 65, 20, help ? "Missions" : "Operator help"));
        buttonList.add(new GuiButton(2, width / 2 + 38, height - 32, 65, 20, "Close"));
        buttonList.add(new GuiButton(3, width / 2 - 105, height - 56, 100, 20, "Authorize [A]"));
        buttonList.add(new GuiButton(4, width / 2 + 5, height - 56, 100, 20, "Abort [X]"));
        buttonList.add(new GuiButton(8, width / 2 - 50, height - 108, 100, 20, "Nose-dive [N]"));
        buttonList.add(new GuiButton(5, width / 2 - 120, height - 82, 75, 20, "Recon [R]"));
        buttonList.add(new GuiButton(6, width / 2 - 40, height - 82, 75, 20, "Attack [K]"));
        buttonList.add(new GuiButton(7, width / 2 + 40, height - 82, 75, 20, "Point [P]"));
        for (int i = 0; i < missionRows.length && i < 12; i++) {
            buttonList.add(new GuiButton(100 + i, width / 2 - 120, 76 + i * 16, 240, 14,
                    (i == selected ? "> " : "  ") + missionRows[i]));
        }
    }

    private void requestData() {
        RdpUavNetwork.requestMissionData();
    }

    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) requestData();
        else if (button.id == 1) { help = !help; initGui(); }
        else if (button.id == 2) mc.displayGuiScreen(null);
        else if (button.id == 3 && selected >= 0 && selected < missionIds.length) {
            RdpUavNetwork.authorizeAttack(missionIds[selected]);
        } else if (button.id == 4 && selected >= 0 && selected < missionIds.length) {
            RdpUavNetwork.abortMission(missionIds[selected]);
        } else if (button.id == 5) {
            createEntityMission(false);
        } else if (button.id == 6) {
            createEntityMission(true);
        } else if (button.id == 7) {
            RdpUavNetwork.createPointMission();
            status = "Point task submitted from your camera aim.";
        } else if (button.id == 8) {
            armKamikazeSelected();
            status = "Nose-dive request submitted; only supported UAVs will arm.";
        } else if (button.id >= 100) {
            selected = button.id - 100;
            initGui();
        }
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

    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (targetField != null && targetField.textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (targetField != null) targetField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "RDP UAV Mission Control", width / 2, 18, 0xFFFFFF);
        if (help) {
            drawHelp();
        } else {
            drawString(fontRenderer, "Target registry ID (for example techguns:zombie)", width / 2 - 120,
                    26, 0xB0D8FF);
            if (targetField != null) targetField.drawTextBox();
            if (!status.isEmpty()) drawString(fontRenderer, status, width / 2 - 120, 56, 0xFFCC66);
            drawMissions();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawMissions() {
        drawString(fontRenderer, "Your AI pilot missions:", width / 2 - 120, 62, 0xB0D8FF);
        if (missionRows.length == 0) {
            drawString(fontRenderer, "No missions are currently assigned.", width / 2 - 120, 82, 0xAAAAAA);
            return;
        }
        int y = 82;
        for (String row : missionRows) {
            drawString(fontRenderer, row, width / 2 - 120, y, 0xFFFFFF);
            y += 14;
            if (y > height - 48) break;
        }
    }

    private void drawHelp() {
        int x = width / 2 - 145;
        drawString(fontRenderer, "AI operator help", x, 42, 0xB0D8FF);
        drawString(fontRenderer, "1. Select an assigned UAV and review its mission state.", x, 62, 0xFFFFFF);
        drawString(fontRenderer, "2. Use Refresh after server-side mission changes.", x, 76, 0xFFFFFF);
        drawString(fontRenderer, "3. PLANNED missions are queued; active states are server-owned.", x, 90, 0xFFFFFF);
        drawString(fontRenderer, "4. The AI pilot handles flight execution; operators monitor status.", x, 104, 0xFFFFFF);
        drawString(fontRenderer, "5. Auto attack arms after target lock; the button is a manual override.", x, 118, 0xFFCC66);
        drawString(fontRenderer, "6. Recon and attack plans use the target field; Point task uses your camera.", x, 132, 0xFFFFFF);
        drawString(fontRenderer, "Command fallback: /rdpuav mission list", x, 150, 0xAAAAAA);
    }

    public void onGuiClosed() {
        if (active == this) active = null;
        super.onGuiClosed();
    }
}
