package net.vas.rdp.uav.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.vas.rdp.uav.network.RdpUavNetwork;
import java.io.IOException;

public final class GuiMissionControl extends GuiScreen {
    private static volatile GuiMissionControl active;
    private String[] missionRows = new String[0];
    private String[] missionIds = new String[0];
    private int selected = -1;
    private boolean help;

    public GuiMissionControl() { active = this; }

    public static void receiveMissionData(String[] rows, String[] ids) {
        if (active != null) {
            active.missionRows = rows == null ? new String[0] : rows;
            active.missionIds = ids == null ? new String[0] : ids;
            active.selected = -1;
            active.rebuildButtons();
        }
    }

    public void initGui() {
        rebuildButtons();
        requestData();
    }

    private void rebuildButtons() {
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 102, height - 32, 65, 20, "Refresh"));
        buttonList.add(new GuiButton(1, width / 2 - 32, height - 32, 65, 20, help ? "Missions" : "Operator help"));
        buttonList.add(new GuiButton(2, width / 2 + 38, height - 32, 65, 20, "Close"));
        buttonList.add(new GuiButton(3, width / 2 - 32, height - 56, 65, 20, "Abort selected"));
        for (int i = 0; i < missionRows.length && i < 12; i++) {
            buttonList.add(new GuiButton(100 + i, width / 2 - 120, 58 + i * 16, 240, 14,
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
            RdpUavNetwork.abortMission(missionIds[selected]);
        } else if (button.id >= 100) {
            selected = button.id - 100;
            initGui();
        }
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "RDP UAV Mission Control", width / 2, 18, 0xFFFFFF);
        if (help) drawHelp();
        else drawMissions();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawMissions() {
        drawString(fontRenderer, "Your AI pilot missions:", width / 2 - 120, 42, 0xB0D8FF);
        if (missionRows.length == 0) {
            drawString(fontRenderer, "No missions are currently assigned.", width / 2 - 120, 62, 0xAAAAAA);
            return;
        }
        int y = 62;
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
        drawString(fontRenderer, "5. This screen does not authorize autonomous attacks.", x, 118, 0xFFCC66);
        drawString(fontRenderer, "Command fallback: /rdpuav mission list", x, 142, 0xAAAAAA);
    }

    public void onGuiClosed() {
        if (active == this) active = null;
        super.onGuiClosed();
    }
}
