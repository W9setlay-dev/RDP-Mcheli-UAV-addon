package net.vas.rdp.uav.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.vas.rdp.uav.mission.MissionDefinition;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.network.RdpUavNetwork;
import net.vas.rdp.uav.swarm.SearchPattern;
import net.vas.rdp.uav.swarm.SwarmConfiguration;
import net.vas.rdp.uav.swarm.SwarmFormation;
import net.vas.rdp.uav.swarm.SwarmLeadership;
import net.vas.rdp.uav.target.TargetPriority;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Operator intent and read-only telemetry surface for server-owned swarms. */
public final class GuiSwarmMissionControl extends GuiScreen {
    private static final MissionType[] SUPPORTED_MISSIONS = {
            MissionType.RECONNAISSANCE, MissionType.OBSERVATION,
            MissionType.STRIKE, MissionType.DESTROYING, MissionType.PATROL
    };
    private static final SwarmFormation[] SUPPORTED_FORMATIONS = {
            SwarmFormation.LINE, SwarmFormation.COLUMN, SwarmFormation.WEDGE,
            SwarmFormation.GRID, SwarmFormation.RING, SwarmFormation.ORBIT
    };
    private static final SearchPattern[] SUPPORTED_SEARCH = {
            SearchPattern.GRID, SearchPattern.LANE, SearchPattern.SPIRAL,
            SearchPattern.RING, SearchPattern.SECTOR
    };
    private static final SwarmLeadership[] SUPPORTED_LEADERSHIP = SwarmLeadership.values();

    private final GuiScreen parent;
    private String selectedSwarmId = "";
    private String selectedMemberId = "";
    private final Set<UUID> selectedUavs = new LinkedHashSet<>();
    private int swarmPage;
    private int memberPage;
    private int missionTypeIndex;
    private int priorityIndex = TargetPriority.NORMAL.ordinal();
    private int formationIndex;
    private int searchIndex;
    private int leadershipIndex = 1;
    private boolean allowDuplicates;
    private boolean commanderFailover = true;
    private boolean participation = true;
    private boolean confirmAttackLaunch;
    private boolean confirmDisband;
    private long lastSnapshotRevision = -1L;
    private GuiTextField nameField;
    private GuiTextField targetField;
    private GuiTextField patrolRouteField;
    private GuiTextField weaponField;
    private GuiTextField maximumField;
    private GuiTextField horizontalField;
    private GuiTextField verticalField;
    private String status = "Waiting for the server-owned swarm list.";

    public GuiSwarmMissionControl(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        int right = Math.max(245, width / 2 - 185);
        nameField = new GuiTextField(1, fontRenderer, 12, height - 112, 208, 18);
        nameField.setMaxStringLength(24);
        nameField.setText("UAV swarm");
        targetField = new GuiTextField(2, fontRenderer, right + 52, 47,
                Math.min(235, width - right - 250), 18);
        targetField.setMaxStringLength(96);
        patrolRouteField = new GuiTextField(7, fontRenderer, right + 52, 47,
                Math.min(235, width - right - 250), 18);
        patrolRouteField.setMaxStringLength(512);
        weaponField = new GuiTextField(3, fontRenderer, right + 52, 72,
                Math.min(175, width - right - 190), 18);
        weaponField.setMaxStringLength(64);
        maximumField = new GuiTextField(4, fontRenderer, right + 58, 98, 42, 18);
        maximumField.setMaxStringLength(2);
        horizontalField = new GuiTextField(5, fontRenderer, right + 159, 98, 52, 18);
        horizontalField.setMaxStringLength(5);
        verticalField = new GuiTextField(6, fontRenderer, right + 265, 98, 52, 18);
        verticalField.setMaxStringLength(5);
        rebuildButtons();
        RdpUavNetwork.requestSwarmSnapshot(selectedSwarmId);
    }

    @Override
    public void updateScreen() {
        if (nameField != null) nameField.updateCursorCounter();
        if (targetField != null) targetField.updateCursorCounter();
        if (patrolRouteField != null) patrolRouteField.updateCursorCounter();
        if (weaponField != null) weaponField.updateCursorCounter();
        if (maximumField != null) maximumField.updateCursorCounter();
        if (horizontalField != null) horizontalField.updateCursorCounter();
        if (verticalField != null) verticalField.updateCursorCounter();
        if (lastSnapshotRevision != SwarmGuiState.getRevision()) {
            lastSnapshotRevision = SwarmGuiState.getRevision();
            if (selectedSwarm() == null && !SwarmGuiState.getSwarms().isEmpty()) {
                selectedSwarmId = SwarmGuiState.getSwarms().get(0).id.toString();
                RdpUavNetwork.requestSwarmSnapshot(selectedSwarmId);
            }
            rebuildButtons();
        }
    }

    private void rebuildButtons() {
        if (buttonList == null) return;
        buttonList.clear();
        int right = Math.max(245, width / 2 - 185);
        buttonList.add(new GuiButton(0, 12, height - 29, 94, 20, "Refresh"));
        buttonList.add(new GuiButton(1, 111, height - 29, 109, 20, "Back to missions"));
        buttonList.add(new GuiButton(2, 12, height - 84, 100, 20,
                "Create from UAV"));
        buttonList.add(new GuiButton(3, 116, height - 84, 104, 20,
                "Add operated UAV"));
        buttonList.add(new GuiButton(4, 12, height - 57, 100, 20,
                "Remove member"));
        buttonList.add(new GuiButton(5, 116, height - 57, 104, 20,
                "Apply settings"));
        buttonList.add(new GuiButton(6, right + 360, height - 57, 100, 20,
                confirmDisband ? "Confirm disband" : "Disband swarm"));
        buttonList.add(new GuiButton(7, right + 256, height - 57, 100, 20,
                confirmAttackLaunch ? "Confirm launch" : "Launch mission"));
        SwarmGuiState.Swarm selected = selectedSwarm();
        buttonList.add(new GuiButton(8, right + 152, height - 57, 100, 20,
                selected != null && "AWAITING_OPERATOR".equals(selected.state)
                        ? "Resume swarm" : "Pause swarm"));
        buttonList.add(new GuiButton(25, right + 322, height - 84, 196, 20,
                "Set selected member as leader"));
        buttonList.add(new GuiButton(9, 190, 29, 24, 18, ">"));
        buttonList.add(new GuiButton(10, 12, 29, 24, 18, "<"));
        buttonList.add(new GuiButton(11, right + 322, 122, 94, 20,
                "Formation: " + currentFormation()));
        buttonList.add(new GuiButton(12, right + 420, 122, 98, 20,
                "Search: " + currentSearch()));
        buttonList.add(new GuiButton(13, right + 322, 146, 196, 20,
                "Leadership: " + currentLeadership()));
        buttonList.add(new GuiButton(14, right + 322, 170, 94, 20,
                "Duplicates " + (allowDuplicates ? "ON" : "OFF")));
        buttonList.add(new GuiButton(15, right + 420, 170, 98, 20,
                "Failover " + (commanderFailover ? "ON" : "OFF")));
        buttonList.add(new GuiButton(16, right + 322, 194, 196, 20,
                "Participation " + (participation ? "ON" : "OFF")));
        buttonList.add(new GuiButton(17, right + 52, 170, 126, 20,
                "Mission: " + SUPPORTED_MISSIONS[missionTypeIndex].name()));
        buttonList.add(new GuiButton(18, right + 184, 170, 126, 20,
                "Priority: " + TargetPriority.values()[priorityIndex].name()));
        buttonList.add(new GuiButton(19, right + 360, height - 29, 158, 20,
                confirmAttackLaunch ? "Cancel strike confirmation" : "Help"));
        buttonList.add(new GuiButton(23, right + 52, 146, 126, 20,
                "Assign crosshair target"));
        buttonList.add(new GuiButton(24, right + 184, 146, 126, 20,
                "Release shared target"));
        buttonList.add(new GuiButton(20, right + 322, 220, 92, 18, "Members <"));
        buttonList.add(new GuiButton(22, right + 420, 220, 98, 18, "Members >"));

        List<SwarmGuiState.Swarm> swarms = SwarmGuiState.getSwarms();
        for (int offset = 0; offset < 8; offset++) {
            int index = swarmPage * 8 + offset;
            if (index >= swarms.size()) break;
            SwarmGuiState.Swarm swarm = swarms.get(index);
            buttonList.add(new GuiButton(100 + offset, 12, 52 + offset * 19,
                    208, 17, (swarm.id.toString().equals(selectedSwarmId) ? "> " : "")
                    + trim(swarm.name + " [" + swarm.missionState + "]", 30)));
        }
        if (selected != null) {
            for (int offset = 0; offset < 16; offset++) {
                int index = memberPage * 16 + offset;
                if (index >= selected.members.size()) break;
                SwarmGuiState.Member member = selected.members.get(index);
                boolean chosen = selectedUavs.contains(member.aircraftId);
                int col = offset / 8;
                int row = offset % 8;
                int x = right + col * 260;
                String label = (chosen ? "[x] " : "[ ] ") + shortId(member.aircraftId)
                        + " " + member.role + " " + member.state
                        + (member.designatedLeader ? " LEADER" : "")
                        + (member.available ? "" : " offline");
                buttonList.add(new GuiButton(200 + offset, x, 240 + row * 17,
                        254, 16, trim(label, 38)));
            }
        }
        buttonList.get(2).enabled = SwarmGuiState.getSwarms().size() < 64;
        buttonList.get(3).enabled = selected != null;
        buttonList.get(4).enabled = selected != null && !selectedMemberId.isEmpty();
        buttonList.get(5).enabled = selected != null;
        buttonList.get(6).enabled = selected != null;
        buttonList.get(7).enabled = selected != null;
        buttonList.get(8).enabled = selected != null;
        for (GuiButton button : buttonList) {
            if (button.id == 25) button.enabled = selected != null
                    && !selectedMemberId.isEmpty();
        }
        for (GuiButton button : buttonList) {
            if (button.id == 23 || button.id == 24) button.enabled = selected != null;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        SwarmGuiState.Swarm selected = selectedSwarm();
        if (button.id == 0) {
            RdpUavNetwork.requestSwarmSnapshot(selectedSwarmId);
            status = "Refreshing server swarm state.";
        } else if (button.id == 1) {
            mc.displayGuiScreen(parent);
        } else if (button.id == 2) {
            RdpUavNetwork.sendSwarmOperatorCommand(0, "", "",
                    nameField == null ? "UAV swarm" : nameField.getText());
            status = "Create request sent; the server will verify the operated UAV.";
        } else if (button.id == 3 && selected != null) {
            RdpUavNetwork.sendSwarmOperatorCommand(1, selected.id.toString(), "", "");
            status = "Add request sent for the currently operated UAV.";
        } else if (button.id == 4 && selected != null && !selectedMemberId.isEmpty()) {
            RdpUavNetwork.sendSwarmOperatorCommand(2, selected.id.toString(), selectedMemberId, "");
            status = "Member removal requested.";
        } else if (button.id == 5) {
            applySettings(selected);
        } else if (button.id == 6 && selected != null) {
            if (!confirmDisband) {
                confirmDisband = true;
                status = "Click Confirm disband to abort this swarm's active missions.";
                rebuildButtons();
            } else {
                RdpUavNetwork.sendSwarmOperatorCommand(5, selected.id.toString(), "", "");
                confirmDisband = false;
                status = "Disband request sent.";
            }
        } else if (button.id == 7) {
            launch(selected);
        } else if (button.id == 8 && selected != null) {
            int operation = "AWAITING_OPERATOR".equals(selected.state) ? 4 : 3;
            RdpUavNetwork.sendSwarmOperatorCommand(operation, selected.id.toString(), "", "");
            status = operation == 3 ? "Pause request sent." : "Resume request sent.";
        } else if (button.id == 25 && selected != null && !selectedMemberId.isEmpty()) {
            RdpUavNetwork.sendSwarmOperatorCommand(6, selected.id.toString(),
                    selectedMemberId, "");
            status = "Designated leader request sent for server validation.";
        } else if (button.id == 23 && selected != null) {
            if (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null) {
                status = "Aim at a valid loaded entity before assigning it as the shared target.";
            } else {
                RdpUavNetwork.lockSharedSwarmTarget(selected.id.toString(),
                        mc.objectMouseOver.entityHit.getUniqueID());
                status = "Server validation requested for the crosshair target.";
            }
        } else if (button.id == 24 && selected != null) {
            RdpUavNetwork.clearSharedSwarmTarget(selected.id.toString());
            status = "Shared target release requested.";
        } else if (button.id == 9) {
            swarmPage++;
            clampPages();
            rebuildButtons();
        } else if (button.id == 10) {
            swarmPage = Math.max(0, swarmPage - 1);
            rebuildButtons();
        } else if (button.id == 11) {
            formationIndex = (formationIndex + 1) % SUPPORTED_FORMATIONS.length;
            rebuildButtons();
        } else if (button.id == 12) {
            searchIndex = (searchIndex + 1) % SUPPORTED_SEARCH.length;
            rebuildButtons();
        } else if (button.id == 13) {
            leadershipIndex = (leadershipIndex + 1) % SUPPORTED_LEADERSHIP.length;
            rebuildButtons();
        } else if (button.id == 14) {
            allowDuplicates = !allowDuplicates;
            rebuildButtons();
        } else if (button.id == 15) {
            commanderFailover = !commanderFailover;
            rebuildButtons();
        } else if (button.id == 16) {
            participation = !participation;
            rebuildButtons();
        } else if (button.id == 17) {
            missionTypeIndex = (missionTypeIndex + 1) % SUPPORTED_MISSIONS.length;
            confirmAttackLaunch = false;
            targetField.setFocused(false);
            patrolRouteField.setFocused(false);
            rebuildButtons();
        } else if (button.id == 18) {
            priorityIndex = (priorityIndex + 1) % TargetPriority.values().length;
            rebuildButtons();
        } else if (button.id == 19) {
            if (confirmAttackLaunch) {
                confirmAttackLaunch = false;
                status = "Strike confirmation canceled.";
            } else {
                status = "Reconnaissance and strike execution are server validated; each strike still requires mission authorization.";
            }
            rebuildButtons();
        } else if (button.id == 20) {
            memberPage = Math.max(0, memberPage - 1);
            rebuildButtons();
        } else if (button.id == 22) {
            memberPage++;
            clampPages();
            rebuildButtons();
        } else if (button.id >= 100 && button.id < 108) {
            int index = swarmPage * 8 + button.id - 100;
            if (index < SwarmGuiState.getSwarms().size()) selectSwarm(
                    SwarmGuiState.getSwarms().get(index));
        } else if (button.id >= 200 && button.id < 216 && selected != null) {
            int index = memberPage * 16 + button.id - 200;
            if (index < selected.members.size()) {
                SwarmGuiState.Member member = selected.members.get(index);
                selectedMemberId = member.aircraftId.toString();
                if (!selectedUavs.add(member.aircraftId)) selectedUavs.remove(member.aircraftId);
                rebuildButtons();
            }
        }
    }

    private void selectSwarm(SwarmGuiState.Swarm swarm) {
        selectedSwarmId = swarm.id.toString();
        selectedMemberId = "";
        selectedUavs.clear();
        memberPage = 0;
        confirmAttackLaunch = false;
        confirmDisband = false;
        formationIndex = indexOf(SUPPORTED_FORMATIONS, swarm.formation, 0);
        searchIndex = indexOf(SUPPORTED_SEARCH, swarm.searchPattern, 0);
        leadershipIndex = indexOf(SUPPORTED_LEADERSHIP, swarm.leadership, 1);
        allowDuplicates = swarm.allowDuplicates;
        commanderFailover = swarm.commanderFailover;
        participation = swarm.participation;
        if (missionTypeIndex == 0 && "STRIKE".equals(swarm.missionState)) {
            missionTypeIndex = 2;
        }
        if (maximumField != null) maximumField.setText(Integer.toString(swarm.maximumMembers));
        if (horizontalField != null) horizontalField.setText(number(swarm.horizontalSpacing));
        if (verticalField != null) verticalField.setText(number(swarm.verticalSpacing));
        status = "Selected " + swarm.name + "; loading its server roster.";
        RdpUavNetwork.requestSwarmSnapshot(selectedSwarmId);
        rebuildButtons();
    }

    private void applySettings(SwarmGuiState.Swarm swarm) {
        if (swarm == null) return;
        try {
            int maximum = Integer.parseInt(maximumField.getText().trim());
            double horizontal = Double.parseDouble(horizontalField.getText().trim());
            double vertical = Double.parseDouble(verticalField.getText().trim());
            SwarmConfiguration configuration = createConfiguration(maximum, horizontal, vertical);
            RdpUavNetwork.updateSwarmConfiguration(swarm.id.toString(), configuration, participation);
            status = "Configuration update submitted for server validation.";
        } catch (RuntimeException invalid) {
            status = "Enter a member limit from 1 to 64 and spacing from 1 to 512.";
        }
    }

    private void launch(SwarmGuiState.Swarm swarm) {
        if (swarm == null) return;
        MissionType type = SUPPORTED_MISSIONS[missionTypeIndex];
        boolean attack = type == MissionType.STRIKE || type == MissionType.DESTROYING;
        if (attack && !confirmAttackLaunch) {
            confirmAttackLaunch = true;
            status = "Confirm strike launch. Each generated strike mission still needs authorization.";
            rebuildButtons();
            return;
        }
        try {
            int maximum = Integer.parseInt(maximumField.getText().trim());
            double horizontal = Double.parseDouble(horizontalField.getText().trim());
            double vertical = Double.parseDouble(verticalField.getText().trim());
            SwarmConfiguration configuration = createConfiguration(maximum, horizontal, vertical);
            MissionDefinition definition = new MissionDefinition()
                    .setMissionType(type).setTarget(type == MissionType.PATROL
                            ? "" : targetField.getText())
                    .setGuidedWeapon(weaponField.getText())
                    .setPriority(TargetPriority.values()[priorityIndex])
                    .setAttack(attack).setSwarmEnabled(true).setSwarmId(swarm.id)
                    .setSwarmConfiguration(configuration);
            if (type == MissionType.PATROL) {
                definition.setGuidedWeapon("").setWaypoints(
                        net.vas.rdp.uav.mission.PatrolRouteCodec.parse(
                                patrolRouteField.getText()));
            }
            for (UUID aircraftId : selectedUavs) definition.selectUav(aircraftId);
            if (!RdpUavNetwork.launchMissionDefinition(definition)) {
                status = "Mission intent failed local validation; check its target and settings.";
            } else {
                status = "Swarm mission submitted; the server will validate each selected UAV.";
                confirmAttackLaunch = false;
            }
        } catch (RuntimeException invalid) {
            status = "Mission settings are invalid; check target, member limit, and spacing.";
        }
        rebuildButtons();
    }

    private void clampPages() {
        swarmPage = Math.min(swarmPage, Math.max(0, (SwarmGuiState.getSwarms().size() - 1) / 8));
        SwarmGuiState.Swarm selected = selectedSwarm();
        int count = selected == null ? 0 : selected.members.size();
        memberPage = Math.min(memberPage, Math.max(0, (count - 1) / 16));
    }

    private SwarmConfiguration createConfiguration(int maximum, double horizontal,
                                                   double vertical) {
        if (maximum < 1 || maximum > 64 || !Double.isFinite(horizontal)
                || horizontal < 1D || horizontal > 512D || !Double.isFinite(vertical)
                || vertical < 1D || vertical > 512D) {
            throw new IllegalArgumentException("Swarm settings are outside supported bounds");
        }
        return SwarmConfiguration.builder().maximumMembers(maximum)
                .horizontalSpacing(horizontal).verticalSpacing(vertical)
                .formation(SUPPORTED_FORMATIONS[formationIndex])
                .searchPattern(SUPPORTED_SEARCH[searchIndex])
                .leadership(SUPPORTED_LEADERSHIP[leadershipIndex])
                .allowDuplicateTargets(allowDuplicates)
                .allowCommanderFailover(commanderFailover).build();
    }

    private SwarmGuiState.Swarm selectedSwarm() {
        for (SwarmGuiState.Swarm swarm : SwarmGuiState.getSwarms()) {
            if (swarm.id.toString().equals(selectedSwarmId)) return swarm;
        }
        return null;
    }

    private String currentFormation() { return SUPPORTED_FORMATIONS[formationIndex].name(); }
    private String currentSearch() { return SUPPORTED_SEARCH[searchIndex].name(); }
    private String currentLeadership() { return SUPPORTED_LEADERSHIP[leadershipIndex].name(); }

    private static <T extends Enum<T>> int indexOf(T[] values, String name, int fallback) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].name().equals(name)) return i;
        }
        return fallback;
    }

    private static String shortId(UUID id) {
        String value = id.toString();
        return value.substring(0, 8);
    }

    private static String number(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private String trim(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 3) + "...";
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (nameField.textboxKeyTyped(typedChar, keyCode)
                || (SUPPORTED_MISSIONS[missionTypeIndex] == MissionType.PATROL
                ? patrolRouteField.textboxKeyTyped(typedChar, keyCode)
                : targetField.textboxKeyTyped(typedChar, keyCode)
                || weaponField.textboxKeyTyped(typedChar, keyCode))
                || maximumField.textboxKeyTyped(typedChar, keyCode)
                || horizontalField.textboxKeyTyped(typedChar, keyCode)
                || verticalField.textboxKeyTyped(typedChar, keyCode)) return;
        if (keyCode == 1) mc.displayGuiScreen(parent);
        else super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
        if (SUPPORTED_MISSIONS[missionTypeIndex] == MissionType.PATROL) {
            patrolRouteField.mouseClicked(mouseX, mouseY, mouseButton);
        } else {
            targetField.mouseClicked(mouseX, mouseY, mouseButton);
            weaponField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        maximumField.mouseClicked(mouseX, mouseY, mouseButton);
        horizontalField.mouseClicked(mouseX, mouseY, mouseButton);
        verticalField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "RDP UAV SWARM MISSION CONTROL", width / 2, 8, 0xFFFFFF);
        int right = Math.max(245, width / 2 - 185);
        drawString(fontRenderer, "OWNED SWARMS", 12, 43, 0xB0D8FF);
        drawString(fontRenderer, "NEW SWARM NAME", 12, height - 124, 0xB0D8FF);
        nameField.drawTextBox();
        if (SUPPORTED_MISSIONS[missionTypeIndex] == MissionType.PATROL) {
            drawString(fontRenderer, "PATROL ROUTE: x z [loiter seconds]; ...", right, 35, 0xB0D8FF);
            patrolRouteField.drawTextBox();
        } else {
            drawString(fontRenderer, "MISSION TARGET", right, 35, 0xB0D8FF);
            targetField.drawTextBox();
            drawString(fontRenderer, "GUIDED WEAPON (optional)", right, 60, 0xB0D8FF);
            weaponField.drawTextBox();
        }
        drawString(fontRenderer, "MAX", right, 102, 0xB0D8FF);
        maximumField.drawTextBox();
        drawString(fontRenderer, "H-SPACING", right + 108, 102, 0xB0D8FF);
        horizontalField.drawTextBox();
        drawString(fontRenderer, "V-SPACING", right + 214, 102, 0xB0D8FF);
        verticalField.drawTextBox();
        SwarmGuiState.Swarm selected = selectedSwarm();
        drawString(fontRenderer, selected == null ? "Select or create a swarm."
                : selected.name + " | " + selected.state + " | " + selected.missionState
                + " | " + selected.members.size() + " member(s)", right, 220, 0x88FFAA);
        drawString(fontRenderer, SUPPORTED_MISSIONS[missionTypeIndex] == MissionType.PATROL
                ? "Patrol routes accept 1..16 bounded waypoints; none selected means all eligible members."
                : "Target must be namespace:id or mod=namespace. None selected means all eligible members.",
                right, 235, 0xAAAAAA);
        if (selected != null && selected.members.isEmpty()) {
            drawString(fontRenderer, "Roster loading or this swarm has no members.",
                    right, 254, 0xAAAAAA);
        }
        if (selected != null && !"null".equals(selected.target)) {
            drawString(fontRenderer, "Shared target " + selected.targetState + ": "
                    + trim(selected.target, 40), right, height - 82, 0xAAAAAA);
        }
        if (!status.isEmpty()) drawString(fontRenderer, trim(status, 105), 12,
                height - 74, 0xFFCC66);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
