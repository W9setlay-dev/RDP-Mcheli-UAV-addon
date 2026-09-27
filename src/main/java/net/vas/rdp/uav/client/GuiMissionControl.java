package net.vas.rdp.uav.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;
import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.weapon.MCH_WeaponSet;
import net.vas.rdp.uav.mission.UavControlAccess;
import net.vas.rdp.uav.weapon.GuidedWeaponRegistry;
import net.vas.rdp.uav.RdpUavDebug;
import net.vas.rdp.uav.network.RdpUavNetwork;
import net.vas.rdp.uav.target.TargetClassifier;
import net.vas.rdp.uav.target.TargetPolicy;
import net.vas.rdp.uav.mission.MissionDefinition;
import net.vas.rdp.uav.mission.MissionValidationResult;
import net.vas.rdp.uav.mission.MissionType;
import net.minecraft.nbt.NBTTagCompound;
import net.vas.rdp.uav.mission.MissionDefinitionCodec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class GuiMissionControl extends GuiScreen {
    private static volatile GuiMissionControl active;
    private static java.util.UUID capturedCrosshairTargetId;
    private String[] missionRows = new String[0];
    private String[] missionIds = new String[0];
    private int selected = -1;
    private boolean help;
    private int filterMode;
    private GuiTextField targetField;
    private GuiTextField filterField;
    private GuiTextField policyField;
    private GuiTextField swarmIdField;
    private GuiTextField swarmSizeField;
    private String status = "";
    private String pendingDangerAction;
    private boolean uavMode;
    private boolean guidedMode;
    private String guidanceSummary = "No controlled aircraft";
    private String[] guidedWeaponRows = new String[0];
    private String[] guidedWeaponNames = new String[0];
    private int selectedGuidedWeapon;
    private String lastMode = "";
    private boolean swarmParticipation = true;
    private boolean swarmAllowDuplicates;
    private int targetPriority;
    private List<TargetSuggestion> threatSuggestions = Collections.emptyList();
    private MissionGuiTab activeTab = MissionGuiTab.OVERVIEW;
    private int tabPage;
    private final MissionDefinition definition = new MissionDefinition();
    private NBTTagCompound savedDefinition;

    public GuiMissionControl() {
        active = this;
        capturedCrosshairTargetId = null;
        rememberCurrentCrosshairTarget();
    }

    private void rememberCurrentCrosshairTarget() {
        if (mc != null && mc.objectMouseOver != null
                && mc.objectMouseOver.entityHit != null) {
            rememberCrosshairTarget(mc.objectMouseOver.entityHit);
        }
    }

    static void rememberCrosshairTarget(Entity target) {
        if (target != null) capturedCrosshairTargetId = target.getUniqueID();
    }

    static Entity capturedCrosshairTarget() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || capturedCrosshairTargetId == null) {
            return null;
        }
        for (Entity entity : mc.world.loadedEntityList) {
            if (entity != null && !entity.isDead
                    && capturedCrosshairTargetId.equals(entity.getUniqueID())) {
                return entity;
            }
        }
        return null;
    }

    public static void receiveMissionData(String[] rows, String[] ids) {
        receiveMissionData(rows, ids, new String[0], new String[0]);
    }

    private boolean selectedMissionIsActionable() {
        return selected >= 0 && selected < missionRows.length
                && MissionUiState.isActionable(missionState(missionRows[selected]));
    }

    private static final class TargetSuggestion {
        private final java.util.UUID entityId;
        private final String registryId;
        private final String label;

        private TargetSuggestion(java.util.UUID entityId, String registryId, String label) {
            this.entityId = entityId;
            this.registryId = registryId;
            this.label = label;
        }
    }

    public static void receiveMissionData(String[] rows, String[] ids,
                                          String[] weaponRows, String[] weaponNames) {
        if (active == null) return;
        String selectedId = selectedMissionId();
        active.missionRows = rows == null ? new String[0] : rows;
        active.missionIds = ids == null ? new String[0] : ids;
        active.selected = -1;
        active.guidedWeaponRows = weaponRows == null ? new String[0] : weaponRows;
        if (weaponNames == null) {
            active.guidedWeaponNames = new String[0];
        } else {
            List<String> names = new ArrayList<>();
            for (String name : weaponNames) {
                if (name != null && !name.trim().isEmpty()) names.add(name);
            }
            active.guidedWeaponNames = names.toArray(new String[0]);
        }
        if (active.selectedGuidedWeapon >= active.guidedWeaponNames.length) {
            active.selectedGuidedWeapon = 0;
        }
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
        RdpUavNetwork.abortMission(id == null ? "" : id);
    }

    public static void clearSelected() {
        String id = selectedMissionId();
        if (id != null) RdpUavNetwork.clearSelectedMission(id);
    }

    public static void clearAll() { RdpUavNetwork.clearAllMissions(); }
    public static void returnSelected() {
        String id = selectedMissionId();
        if (id != null) RdpUavNetwork.returnToBase(id);
    }

    public static void createReconFromField() {
        if (active != null) active.createEntityMission(false);
    }

    public static void createAutoAttackFromField() {
        if (active != null) active.createEntityMission(true);
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
        if (policyField == null) {
            policyField = new GuiTextField(14, fontRenderer, width / 2 - 220, 84, 425, 18);
            policyField.setMaxStringLength(220);
        }
        if (swarmIdField == null) {
            swarmIdField = new GuiTextField(12, fontRenderer, width / 2 + 15, 63, 205, 18);
            swarmIdField.setMaxStringLength(36);
        }
        if (swarmSizeField == null) {
            swarmSizeField = new GuiTextField(13, fontRenderer, width / 2 - 220, 63, 60, 18);
            swarmSizeField.setMaxStringLength(2);
            swarmSizeField.setText("1");
        }
        rebuildButtons();
        requestRefresh();
    }

    private void rebuildButtons() {
        refreshMode();
        buttonList.clear();
        int left = width / 2 - 220;
        int right = width / 2 + 15;
        int tabX = left + 24;
        buttonList.add(new GuiButton(290, left, 2, 20, 18, "<"));
        int firstTab = tabPage * 5;
        for (int offset = 0; offset < 5 && firstTab + offset < MissionGuiTab.values().length; offset++) {
            MissionGuiTab tab = MissionGuiTab.values()[firstTab + offset];
            buttonList.add(new GuiButton(300 + tab.ordinal(), tabX, 2, 70, 18,
                    (tab == activeTab ? "> " : "") + tab.name()));
            tabX += 72;
        }
        buttonList.add(new GuiButton(291, tabX, 2, 20, 18, ">"));
        // Keep every action inside one of the two 205/220px content columns.
        buttonList.add(new GuiButton(0, left, height - 28, 62, 20, "Refresh"));
        buttonList.add(new GuiButton(1, left + 67, height - 28, 62, 20, help ? "Dashboard" : "Help"));
        buttonList.add(new GuiButton(2, right, height - 28, 62, 20, "Close"));
        buttonList.add(new GuiButton(16, right + 67, height - 28, 115, 20, "Return to base"));
        if (activeTab == MissionGuiTab.VALIDATION) {
            buttonList.add(new GuiButton(26, left, height - 54, 100, 20, "Validate"));
            buttonList.add(new GuiButton(27, left + 105, height - 54, 100, 20, "Save"));
            buttonList.add(new GuiButton(28, right, height - 54, 100, 20, "Load"));
            buttonList.add(new GuiButton(29, right + 105, height - 54, 115, 20, "Launch mission"));
        }
        if (uavMode) {
            buttonList.add(new GuiButton(3, left, height - 54, 100, 20,
                    "Authorize [" + RdpUavClientKeys.authorizeKey() + "]"));
            buttonList.add(new GuiButton(4, left + 105, height - 54, 100, 20,
                    "Abort [" + RdpUavClientKeys.abortKey() + "]"));
            buttonList.add(new GuiButton(13, right, height - 54, 100, 20,
                    "Clear lock [" + RdpUavClientKeys.clearLockKey() + "]"));
            buttonList.add(new GuiButton(8, right + 105, height - 54, 115, 20,
                    "Nose-dive [" + RdpUavClientKeys.kamikazeKey() + "]"));
            buttonList.add(new GuiButton(14, left, height - 80, 100, 20, "Archive selected"));
            buttonList.add(new GuiButton(15, left + 105, height - 80, 100, 20, "Archive all"));
            buttonList.add(new GuiButton(9, right, height - 80, 100, 20, "Square strike"));
            buttonList.add(new GuiButton(10, right + 105, height - 80, 115, 20,
                    "Entity lock [" + RdpUavClientKeys.entityLockKey() + "]"));
            buttonList.add(new GuiButton(5, left, height - 106, 64, 20,
                    "Recon [" + RdpUavClientKeys.reconKey() + "]"));
            buttonList.add(new GuiButton(6, left + 70, height - 106, 64, 20,
                    "Attack [" + RdpUavClientKeys.attackKey() + "]"));
            buttonList.add(new GuiButton(7, left + 140, height - 106, 65, 20,
                    "Point [" + RdpUavClientKeys.pointKey() + "]"));
            buttonList.add(new GuiButton(20, right, height - 132, 220, 20,
                    "Target priority: " + targetPriorityLabel()));
            buttonList.add(new GuiButton(17, right, height - 106, 220, 20,
                    "Apply swarm mission settings"));
            buttonList.add(new GuiButton(21, right, height - 158, 105, 20,
                    "Assign crosshair"));
            buttonList.add(new GuiButton(22, right + 110, height - 158, 110, 20,
                    "Release target"));
            buttonList.add(new GuiButton(18, left, height - 132, 100, 20,
                    "Swarm " + (swarmParticipation ? "ON" : "OFF")));
            buttonList.add(new GuiButton(19, left + 105, height - 132, 100, 20,
                    "Duplicates " + (swarmAllowDuplicates ? "ON" : "OFF")));
        } else if (guidedMode) {
            buttonList.add(new GuiButton(3, left, height - 54, 100, 20, "Authorize mission"));
            buttonList.add(new GuiButton(4, left + 105, height - 54, 100, 20, "Abort mission"));
            buttonList.add(new GuiButton(10, right, height - 54, 110, 20,
                    "Entity lock [" + RdpUavClientKeys.entityLockKey() + "]"));
            buttonList.add(new GuiButton(9, right + 115, height - 54, 105, 20,
                    "Area [" + RdpUavClientKeys.areaLockKey() + "]"));
            buttonList.add(new GuiButton(13, left, height - 80, 100, 20, "Release AI control"));
            buttonList.add(new GuiButton(14, left + 105, height - 80, 100, 20, "Weapon details"));
            buttonList.add(new GuiButton(23, right, height - 106, 220, 20,
                    guidedWeaponButtonLabel()));
            buttonList.add(new GuiButton(24, right, height - 132, 220, 20,
                    "Adopt launched missile"));
            buttonList.add(new GuiButton(25, right, height - 158, 220, 20,
                    "Reassign to crosshair"));
        }
        buttonList.add(new GuiButton(12, right, 64, 220, 20, filterLabel()));
        buttonList.add(new GuiButton(30, right, 84, 220, 20, "Apply target policy"));
        threatSuggestions = collectThreatSuggestions();
        for (int i = 0; i < threatSuggestions.size() && i < 5; i++) {
            TargetSuggestion suggestion = threatSuggestions.get(i);
            buttonList.add(new GuiButton(200 + i, right, 158 + i * 12, 220, 11,
                    suggestion.label));
        }
        for (int i = 0, shown = 0; i < missionRows.length && shown < 11; i++) {
            if (matchesFilter(missionRows[i])) continue;
            buttonList.add(new GuiButton(100 + i, left, 108 + shown * 17, 205, 15,
                    (i == selected ? "> " : "  ") + missionRows[i]));
            shown++;
        }
        boolean actionable = selectedMissionIsActionable();
        boolean hasSelection = selected >= 0 && selected < missionRows.length;
        boolean hasControlledAircraft = mc != null && mc.player != null
                && UavControlAccess.findControlledAircraft(mc.player) != null;
        for (GuiButton button : buttonList) {
            if (button.id == 3 || button.id == 4 || button.id == 8
                    || button.id == 16 || button.id == 24
                    || button.id == 25) {
                button.enabled = actionable;
            } else if (button.id == 13) {
                // Lock release is an aircraft action, not an action on the
                // currently selected mission record.
                button.enabled = hasControlledAircraft;
            } else if (button.id == 14) {
                // Terminal records can still be archived.
                button.enabled = hasSelection;
            } else if (button.id == 9) {
                // A new area mission is independent of the selected record.
                button.enabled = uavMode;
            }
        }
    }

    private void refreshMode() {
            if (mc == null || mc.player == null) {
                uavMode = false;
                guidedMode = false;
                guidanceSummary = "No controlled aircraft";
                return;
            }
            MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(mc.player);
            uavMode = aircraft != null && aircraft.isUAV();
            guidedMode = false;
            guidanceSummary = aircraft == null ? "No controlled aircraft" : "No supported guided weapon with ammunition";
            if (aircraft == null || aircraft.getWeapons() == null) return;
            for (MCH_WeaponSet weapon : aircraft.getWeapons()) {
                if (weapon == null || weapon.getInfo() == null) continue;
                net.vas.rdp.uav.weapon.GuidedWeaponCapability capability =
                        GuidedWeaponRegistry.find(weapon.getName(), weapon.getInfo().type);
                if (capability == null
                        || capability.getBehavior()
                        == net.vas.rdp.uav.weapon.GuidedNativeBehavior.UNSUPPORTED) continue;
                if (weapon.getAmmo() > 0 || weapon.getAmmoReserve() > 0) {
                    guidedMode = true;
                    guidanceSummary = weapon.getName() + " / " + weapon.getInfo().type
                            + " ammo " + (weapon.getAmmo() + weapon.getAmmoReserve());
                    break;
                }
                guidanceSummary = weapon.getName() + " / " + weapon.getInfo().type + " NO AMMUNITION";
            }
            String mode = uavMode && guidedMode ? "UAV_GUIDED"
                    : uavMode ? "UAV" : guidedMode ? "GUIDED" : "NONE";
            if (!mode.equals(lastMode)) {
                lastMode = mode;
                RdpUavDebug.debug("GUI", "mode={} aircraft={} guidance={}", mode,
                        aircraft.getUniqueID(), guidanceSummary);
            }
        }

        private String filterLabel() {
        return filterMode == 0 ? "Filter: ALL" : filterMode == 1 ? "Filter: ACTIVE" : "Filter: ALERTS";
    }

    private boolean matchesFilter(String row) {
        if (row == null) return true;
        String normalized = row.toLowerCase(java.util.Locale.ROOT);
        String query = filterField == null ? "" : filterField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        if (!query.isEmpty() && !normalized.contains(query)) return true;
        String state = missionState(row);
        if (filterMode == 1) {
            return !MissionUiState.isActionable(state) || MissionUiState.isAlert(state);
        }
        if (filterMode == 2) {
            return !MissionUiState.isAlert(state)
                    && !MissionUiState.isTerminal(state)
                    && !"OUTCOME_UNKNOWN".equals(state);
        }
        return false;
    }

    private String missionState(String row) {
        if (row == null) return "";
        String[] fields = row.trim().split("\\s+");
        return fields.length < 2 ? "" : fields[1];
    }

    private void requestData() { RdpUavNetwork.requestMissionData(); }

    protected void actionPerformed(GuiButton button) {
        if (button.id == 290) {
            tabPage = Math.max(0, tabPage - 1);
            rebuildButtons();
        } else if (button.id == 291) {
            tabPage = Math.min((MissionGuiTab.values().length - 1) / 5, tabPage + 1);
            rebuildButtons();
        } else if (button.id >= 300 && button.id < 300 + MissionGuiTab.values().length) {
            activeTab = MissionGuiTab.values()[button.id - 300];
            rebuildButtons();
        } else if (button.id == 0) requestData();
        else if (button.id == 26) {
            definition.setTarget(targetField == null ? "" : targetField.getText());
            MissionValidationResult validation = MissionValidationResult.validate(definition);
            status = validation.isValid() ? "Mission definition is valid."
                    : "Validation failed: " + validation.getErrors().get(0);
            activeTab = MissionGuiTab.VALIDATION;
            rebuildButtons();
        } else if (button.id == 27) {
            definition.setTarget(targetField == null ? "" : targetField.getText());
            if (MissionValidationResult.validate(definition).isValid()) {
                savedDefinition = MissionDefinitionCodec.write(definition);
                status = "Configuration saved locally; launch remains server-authoritative.";
            } else {
                status = "Cannot save invalid mission definition.";
            }
            rebuildButtons();
        } else if (button.id == 28) {
            if (savedDefinition == null) {
                status = "No saved mission definition is available.";
            } else {
                MissionDefinition loaded = MissionDefinitionCodec.read(savedDefinition);
                definition.setTarget(loaded.getTarget())
                        .setMissionType(loaded.getMissionType())
                        .setPriority(loaded.getPriority())
                        .setAttack(loaded.isAttack())
                        .setSwarmEnabled(loaded.isSwarmEnabled())
                        .setSwarmMaximumMembers(loaded.getSwarmMaximumMembers());
                if (targetField != null) targetField.setText(loaded.getTarget());
                if (policyField != null) policyField.setText(policyText(loaded.getTargetPolicy()));
                status = "Saved mission definition loaded.";
            }
            rebuildButtons();
        } else if (button.id == 29) {
            definition.setTarget(targetField == null ? "" : targetField.getText())
                    .setAttack(definition.getMissionType() == MissionType.STRIKE);
            MissionValidationResult validation = MissionValidationResult.validate(definition);
            if (!validation.isValid()) {
                status = "Cannot launch: " + validation.getErrors().get(0);
            } else {
                if (RdpUavNetwork.launchMissionDefinition(definition)) {
                    status = "Versioned mission definition submitted for server validation.";
                } else {
                    status = "Cannot launch: selected workflow requires an unsupported runtime path.";
                }
            }
            rebuildButtons();
        }
        else if (button.id == 30) {
            applyTargetPolicy();
        }
        else if (button.id == 1) { help = !help; rebuildButtons(); }
        else if (button.id == 2) mc.displayGuiScreen(null);
        else if (button.id == 3) confirmDangerAction("authorize", GuiMissionControl::authorizeSelected, "Authorizing the selected mission will arm server-side controls.");
        else if (button.id == 4) confirmDangerAction("abort", GuiMissionControl::abortSelected, "Aborting this mission will end the active task.");
        else if (button.id == 14) confirmDangerAction("clear-selected", GuiMissionControl::clearSelected, "Archive the selected mission record? It remains in server history.");
        else if (button.id == 15) confirmDangerAction("clear-all", GuiMissionControl::clearAll, "Archive all of your mission records? They remain in server history.");
        else if (button.id == 16) confirmDangerAction("return-to-base", GuiMissionControl::returnSelected, "Request native McHeli return and landing for the selected mission?");
        else if (button.id == 5) createEntityMission(false);
        else if (button.id == 6) confirmDangerAction("attack", () -> createEntityMission(true), "Launch an autonomous hostile-target attack?");
        else if (button.id == 7) {
            RdpUavNetwork.createPointMission();
            status = "Point task submitted from your camera aim.";
        } else if (button.id == 8) confirmDangerAction("nosedive", () -> { armKamikazeSelected(); status = "Nose-dive request submitted; only supported UAVs will arm."; }, "Arm the kamikaze dive for the selected aircraft?");
        else if (button.id == 9) createSquareMission();
        else if (button.id == 10) lockCrosshairTarget();
        else if (button.id == 13) {
            RdpUavNetwork.clearAiTargetLock();
            status = "Target lock clear requested; recovery will be attempted.";
        }
        else if (button.id == 17) applySwarmSettings();
        else if (button.id == 18) { swarmParticipation = !swarmParticipation; rebuildButtons(); }
        else if (button.id == 19) { swarmAllowDuplicates = !swarmAllowDuplicates; rebuildButtons(); }
        else if (button.id == 20) { targetPriority = (targetPriority + 1) % 4; rebuildButtons(); }
        else if (button.id == 21) assignSwarmCrosshair();
        else if (button.id == 22) releaseSwarmTarget();
        else if (button.id == 23) {
            if (guidedWeaponNames.length > 0) {
                selectedGuidedWeapon = (selectedGuidedWeapon + 1) % guidedWeaponNames.length;
                status = "Selected guided weapon: " + guidedWeaponNames[selectedGuidedWeapon];
                rebuildButtons();
            }
        } else if (button.id == 24) {
            RdpUavNetwork.adoptLaunchedProjectile();
            status = "AI adoption requested for the selected controlled-aircraft missile.";
        } else if (button.id == 25) {
            String missionId = selectedMissionId();
            RayTraceResult hit = mc.objectMouseOver;
            if (missionId == null || hit == null || hit.entityHit == null) {
                status = "Select a guided mission and aim at an entity.";
            } else {
                RdpUavNetwork.reassignGuidedMission(missionId, hit.entityHit.getUniqueID());
                status = "Guided reassignment requested for " + hit.entityHit.getName() + ".";
            }
        }
        else if (button.id == 12) {
            filterMode = (filterMode + 1) % 3;
            rebuildButtons();
        } else if (button.id >= 200 && button.id < 205) {
            int suggestionIndex = button.id - 200;
            if (suggestionIndex < threatSuggestions.size()) {
                TargetSuggestion suggestion = threatSuggestions.get(suggestionIndex);
                String missionId = selectedMissionId();
                if (missionId != null) {
                    RdpUavNetwork.assignRegistryTarget(missionId, suggestion.registryId);
                } else {
                    RdpUavNetwork.lockAiTarget(suggestion.entityId);
                }
                status = "Target assignment requested for " + suggestion.label + ".";
            }
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
        if (mc == null || mc.world == null) {
            status = "Target lock unavailable while the world is not loaded.";
            return;
        }
        RayTraceResult hit = mc.objectMouseOver;
        Entity target = hit == null ? null : hit.entityHit;
        if (target == null || target.world == null || target.isDead) target = capturedCrosshairTarget();
        if (target != null && target.world != null && !target.isDead) {
            rememberCrosshairTarget(target);
            RdpUavNetwork.lockAiTarget(target.getUniqueID());
            status = "Target lock requested for " + target.getName() + ".";
            return;
        }
        capturedCrosshairTargetId = null;
        status = "No crosshair contact; aim directly at a valid target.";
    }

    private void createEntityMission(boolean attack) {
        String target = targetField == null ? "" : targetField.getText().trim();
        if (!target.matches("(?i)(mod=[A-Za-z0-9_.-]+|[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+)")) {
            status = "Enter namespace:id or mod=namespace.";
            return;
        }

        definition.setTarget(target).setAttack(attack)
                .setMissionType(attack ? MissionType.STRIKE : MissionType.RECONNAISSANCE)
                .setPriority(net.vas.rdp.uav.target.TargetPriority.values()[targetPriority]);
        MissionValidationResult validation = MissionValidationResult.validate(definition);
        if (!validation.isValid()) {
            status = validation.getErrors().get(0);
            activeTab = MissionGuiTab.VALIDATION;
            rebuildButtons();
            return;
        }
        definition.setGuidedWeapon(selectedGuidedWeaponName());
        if (RdpUavNetwork.launchMissionDefinition(definition)) {
            status = attack ? "Versioned autonomous attack submitted for server validation."
                    : "Versioned reconnaissance mission submitted for server validation.";
        } else {
            status = "Mission definition rejected before submission.";
        }
    }

    private void applyTargetPolicy() {
        String value = policyField == null ? "" : policyField.getText().trim();
        TargetPolicy.Builder policy = TargetPolicy.builder();
        boolean rejectEmbedded = true;
        boolean allowVehicles = true;
        double confidence = 0.0D;
        try {
            if (!value.isEmpty()) {
                for (String clause : value.split(";")) {
                    String[] pair = clause.split("=", 2);
                    if (pair.length != 2) throw new IllegalArgumentException("Use key=value clauses");
                    String key = pair[0].trim().toLowerCase(java.util.Locale.ROOT);
                    String[] values = splitPolicyValues(pair[1]);
                    switch (key) {
                        case "include":
                            policy.include(values);
                            break;
                        case "require":
                            policy.require(values);
                            break;
                        case "exclude":
                            policy.exclude(values);
                            break;
                        case "rejectembedded":
                            rejectEmbedded = Boolean.parseBoolean(pair[1].trim());
                            break;
                        case "allowvehicles":
                            allowVehicles = Boolean.parseBoolean(pair[1].trim());
                            break;
                        case "confidence":
                            confidence = Double.parseDouble(pair[1].trim());
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown policy key: " + key);
                    }
                }
            }
            definition.setTargetPolicy(policy.rejectDeeplyEmbedded(rejectEmbedded)
                    .allowHostileVehicles(allowVehicles)
                    .withMinimumConfidence(confidence).snapshot());
            status = "Target policy applied and will be server-validated.";
        } catch (IllegalArgumentException invalid) {
            status = "Target policy rejected: " + invalid.getMessage();
        }
        rebuildButtons();
    }

    private static String[] splitPolicyValues(String value) {
        if (value.trim().isEmpty()) return new String[0];
        String[] values = value.split(",");
        if (values.length > 32) throw new IllegalArgumentException("Maximum 32 filters");
        for (String item : values) {
            if (!item.trim().matches("[A-Za-z0-9_:.*/-]{1,64}")) {
                throw new IllegalArgumentException("Invalid filter token");
            }
        }
        return values;
    }

    private static String policyText(TargetPolicy.Snapshot policy) {
        return "include=" + joinPolicy(policy.getInclude())
                + ";require=" + joinPolicy(policy.getRequire())
                + ";exclude=" + joinPolicy(policy.getExclude())
                + ";rejectembedded=" + policy.isRejectEmbedded()
                + ";allowvehicles=" + policy.isAllowVehicleTargets()
                + ";confidence=" + policy.getMinimumConfidence();
    }

    private static String joinPolicy(java.util.List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(',');
            result.append(value);
        }
        return result.toString();
    }

    private String targetPriorityLabel() {
        switch (targetPriority) {
            case 0: return "LOW";
            case 1: return "NORMAL";
            case 2: return "HIGH";
            default: return "CRITICAL";
        }
    }

    private void applySwarmSettings() {
        String id = swarmIdField == null ? "" : swarmIdField.getText().trim();
        int maximum;
        try {
            maximum = Integer.parseInt(swarmSizeField == null ? "1" : swarmSizeField.getText().trim());
        } catch (NumberFormatException invalid) {
            status = "Swarm size must be a number.";
            return;
        }
        if (maximum < 1 || maximum > 64) {
            status = "Swarm size must be between 1 and 64.";
            return;
        }
        RdpUavNetwork.updateSwarmConfig(id, maximum, swarmParticipation, swarmAllowDuplicates);
        status = "Swarm mission settings submitted for server validation.";
    }

    private void assignSwarmCrosshair() {
        String swarmId = swarmIdField == null ? "" : swarmIdField.getText().trim();
        RayTraceResult hit = mc.objectMouseOver;
        if (swarmId.isEmpty() || hit == null || hit.entityHit == null) {
            status = "Enter a swarm ID and aim at an entity.";
            return;
        }
        RdpUavNetwork.assignSwarmTarget(swarmId, hit.entityHit.getUniqueID(), swarmAllowDuplicates);
        status = "Swarm target assignment submitted for server validation.";
    }

    private void releaseSwarmTarget() {
        String swarmId = swarmIdField == null ? "" : swarmIdField.getText().trim();
        if (swarmId.isEmpty()) {
            status = "Enter a swarm ID first.";
            return;
        }
        RdpUavNetwork.releaseSwarmTarget(swarmId);
        status = "Swarm target release submitted for server validation.";
    }

    private void createSquareMission() {
        String parameters = targetField == null ? "" : targetField.getText().trim();
        if (!parameters.matches("-?[0-9.]+\\s+-?[0-9.]+\\s+-?[0-9.]+(\\s+[0-9.]+)?")) {
            status = "Square format: X Y Z [size].";
            return;
        }
        definition.setTarget(parameters).setAttack(true).setMissionType(MissionType.SQUARE_STRIKE);
        RdpUavNetwork.createSquareStrike(parameters, selectedGuidedWeaponName());
        status = "Autonomous square strike submitted to WingMan.";
    }

    private String selectedGuidedWeaponName() {
        return selectedGuidedWeapon >= 0 && selectedGuidedWeapon < guidedWeaponNames.length
                ? guidedWeaponNames[selectedGuidedWeapon] : "";
    }

    private String guidedWeaponButtonLabel() {
        return guidedWeaponNames.length == 0 ? "Weapon: none supported"
                : "Weapon: " + guidedWeaponNames[selectedGuidedWeapon];
    }

    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (targetField != null && targetField.textboxKeyTyped(typedChar, keyCode)) return;
        if (filterField != null && filterField.textboxKeyTyped(typedChar, keyCode)) {
            rebuildButtons();
            return;
        }
        if (policyField != null && policyField.textboxKeyTyped(typedChar, keyCode)) return;
        if (swarmIdField != null && swarmIdField.textboxKeyTyped(typedChar, keyCode)) return;
        if (swarmSizeField != null && swarmSizeField.textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (targetField != null) targetField.mouseClicked(mouseX, mouseY, mouseButton);
        if (filterField != null) filterField.mouseClicked(mouseX, mouseY, mouseButton);
        if (policyField != null) policyField.mouseClicked(mouseX, mouseY, mouseButton);
        if (swarmIdField != null) swarmIdField.mouseClicked(mouseX, mouseY, mouseButton);
        if (swarmSizeField != null) swarmSizeField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        refreshMode();
        String title = uavMode && guidedMode ? "GUIDED WEAPON CONTROL"
                : uavMode ? "RDP UAV MISSION CONTROL"
                : guidedMode ? "GUIDED WEAPON CONTROL" : "RDP MISSION CONTROL";
        drawCenteredString(fontRenderer, title + " / "
                + activeTab.name(),
                width / 2,
                12,
                0xFFFFFF);

        if (help) {
            drawHelp();
        } else {
            int left = width / 2 - 220;
            int right = width / 2 + 15;
            drawString(fontRenderer, "SECTION: " + activeTab.name(), left, 30, 0xB0D8FF);
            drawString(fontRenderer, "ENTITY ID FILTER (namespace:path)", left, 42, 0xB0D8FF);
            if (activeTab == MissionGuiTab.VALIDATION) {
                MissionValidationResult validation = MissionValidationResult.validate(definition);
                drawString(fontRenderer, validation.isValid() ? "VALID" : "INVALID",
                        right, 30, validation.isValid() ? 0x88FFAA : 0xFF6666);
                int y = 42;
                for (String error : validation.getErrors()) {
                    drawString(fontRenderer, error, right, y, 0xFF6666);
                    y += 12;
                }

            }
            if (targetField != null) targetField.drawTextBox();
            drawString(fontRenderer, "MISSION FILTER", right, 30, 0xB0D8FF);
            if (filterField != null) filterField.drawTextBox();
            drawString(fontRenderer, "TARGET POLICY (include=...,require=...,exclude=...)", left, 73, 0xB0D8FF);
            if (policyField != null) policyField.drawTextBox();
            drawString(fontRenderer, "SWARM ID", right, 51, 0xB0D8FF);
            if (swarmIdField != null) swarmIdField.drawTextBox();
            drawString(fontRenderer, "SWARM MAX", left, 51, 0xB0D8FF);
            if (swarmSizeField != null) swarmSizeField.drawTextBox();
            if (!status.isEmpty()) drawString(fontRenderer, status, left, 64, 0xFFCC66);
            drawString(fontRenderer, uavMode ? "UAV MISSIONS" : "GUIDED WEAPON MODE",
                    left, 80, 0x88FFAA);
            if (guidedMode) drawString(fontRenderer, "PAYLOAD: " + guidanceSummary,
                    right, 80, 0x88FFAA);
            if (guidedWeaponRows.length > 0) {
                drawString(fontRenderer, "GUIDED WEAPONS / PROJECTILES", right, 96, 0xB0D8FF);
                for (int i = 0; i < guidedWeaponRows.length && i < 6; i++) {
                    drawString(fontRenderer, guidedWeaponRows[i], right, 109 + i * 12, 0xFFFFFF);
                }
            }
            drawMissions(left, right);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawMissions(int left, int right) {
        int panelTop = 108;
        drawRect(left - 4, panelTop, left + 209, height - 113, 0x66000000);
        drawRect(right - 4, panelTop, right + 224, height - 113, 0x66000000);
        drawString(fontRenderer, "SERVER MISSIONS", left, panelTop + 2, 0xB0D8FF);
        if (missionRows.length == 0) {
            drawString(fontRenderer, "No missions assigned.", left, panelTop + 20, 0xAAAAAA);
        } else {
            int y = panelTop + 20;
            for (String row : missionRows) {
                if (matchesFilter(row)) continue;
                drawString(fontRenderer, fontRenderer.trimStringToWidth(row, 205), left, y, 0xFFFFFF);
                y += 17;
                if (y > height - 124) break;
            }
        }
        drawString(fontRenderer, "SELECTED MISSION", right, panelTop + 2, 0xB0D8FF);
        if (selected >= 0 && selected < missionRows.length) {
            drawString(fontRenderer, fontRenderer.trimStringToWidth(missionRows[selected], 220),
                    right, panelTop + 20, 0xFFFFFF);
            drawString(fontRenderer, fontRenderer.trimStringToWidth(
                    "ID: " + missionIds[selected], 220), right, panelTop + 35, 0xAAAAAA);
        } else {
            drawString(fontRenderer, "Select a mission to inspect.", right, panelTop + 20, 0xAAAAAA);
        }
        drawThreatSuggestions(right, panelTop + 56);
        drawTelemetry(right, panelTop + 140);
    }

    private void drawThreatSuggestions(int x, int y) {
        drawString(fontRenderer, "THREAT SUGGESTIONS", x, y, 0xB0D8FF);
        if (threatSuggestions.isEmpty()) {
            drawString(fontRenderer, "No hostile targets in scan range.", x, y + 18, 0xAAAAAA);
        }
    }

    private List<TargetSuggestion> collectThreatSuggestions() {
        List<TargetSuggestion> result = new ArrayList<>();
        if (mc == null || mc.world == null || mc.player == null) return result;
        for (Entity entity : mc.world.loadedEntityList) {
            if (entity == null || entity.isDead || entity == mc.player) continue;
            ResourceLocation id = EntityList.getKey(entity);
            if (id == null) continue;
            String classification = TargetClassifier.classify(id.toString());
            if ("UNKNOWN".equals(classification)) continue;
            double distance = entity.getDistanceSq(mc.player.posX, mc.player.posY, mc.player.posZ);
            if (distance > 250000.0D) continue;
            result.add(new TargetSuggestion(entity.getUniqueID(),
                    id.toString(),
                    String.format(java.util.Locale.ROOT, "%s  [%s]  %.1fm",
                            id, classification, Math.sqrt(distance))));
        }
        result.sort(Comparator.comparing(left -> left.label));
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
