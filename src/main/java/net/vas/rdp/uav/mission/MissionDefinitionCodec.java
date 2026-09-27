package net.vas.rdp.uav.mission;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.vas.rdp.uav.target.TargetPriority;

/** Versioned configuration-only persistence for GUI mission definitions. */
public final class MissionDefinitionCodec {
    private static final int VERSION = 1;
    private MissionDefinitionCodec() { }

    public static NBTTagCompound write(MissionDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("Definition is required");
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("version", VERSION);
        tag.setString("type", definition.getMissionType().name());
        tag.setString("target", definition.getTarget());
        tag.setString("aircraftType", definition.getAircraftType());
        tag.setString("guidedWeapon", definition.getGuidedWeapon());
        tag.setString("priority", definition.getPriority().name());
        tag.setBoolean("attack", definition.isAttack());
        tag.setBoolean("swarmEnabled", definition.isSwarmEnabled());
        tag.setInteger("swarmMaximumMembers", definition.getSwarmMaximumMembers());
        tag.setString("searchMode", definition.getSearchMode());
        tag.setString("trackingMode", definition.getTrackingMode());
        tag.setString("guidanceMode", definition.getGuidanceMode());
        tag.setString("executionMode", definition.getExecutionMode());
        tag.setString("recoveryMode", definition.getRecoveryMode());
        NBTTagList selected = new NBTTagList();
        for (java.util.UUID id : definition.getSelectedUavs()) {
            NBTTagCompound value = new NBTTagCompound();
            value.setString("id", id.toString());
            selected.appendTag(value);
        }
        tag.setTag("selectedUavs", selected);
        tag.setString("policyDomain", definition.getTargetPolicy().getDomain());
        tag.setInteger("policyVersion", definition.getTargetPolicy().getVersion());
        writeList(tag, "policyInclude", definition.getTargetPolicy().getInclude());
        writeList(tag, "policyRequire", definition.getTargetPolicy().getRequire());
        writeList(tag, "policyExclude", definition.getTargetPolicy().getExclude());
        tag.setBoolean("policyRejectEmbedded", definition.getTargetPolicy().isRejectEmbedded());
        tag.setBoolean("policyAllowVehicles", definition.getTargetPolicy().isAllowVehicleTargets());
        tag.setDouble("policyMinimumConfidence", definition.getTargetPolicy().getMinimumConfidence());
        return tag;
    }

    public static MissionDefinition read(NBTTagCompound tag) {
        if (tag == null) throw new IllegalArgumentException("Definition tag is required");
        MissionDefinition definition = new MissionDefinition();
        try {
            definition.setMissionType(MissionType.valueOf(tag.getString("type")))
                    .setTarget(tag.getString("target"))
                    .setAircraftType(tag.getString("aircraftType"))
                    .setGuidedWeapon(tag.getString("guidedWeapon"))
                    .setPriority(TargetPriority.valueOf(tag.getString("priority")))
                    .setAttack(tag.getBoolean("attack"))
                    .setSwarmEnabled(tag.getBoolean("swarmEnabled"));
            if (tag.hasKey("swarmMaximumMembers")) {
                definition.setSwarmMaximumMembers(tag.getInteger("swarmMaximumMembers"));
            }
            if (tag.hasKey("searchMode")) definition.setSearchMode(tag.getString("searchMode"));
            if (tag.hasKey("trackingMode")) definition.setTrackingMode(tag.getString("trackingMode"));
            if (tag.hasKey("guidanceMode")) definition.setGuidanceMode(tag.getString("guidanceMode"));
            if (tag.hasKey("executionMode")) definition.setExecutionMode(tag.getString("executionMode"));
            if (tag.hasKey("recoveryMode")) definition.setRecoveryMode(tag.getString("recoveryMode"));
            NBTTagList selected = tag.getTagList("selectedUavs", 10);
            for (int i = 0; i < selected.tagCount(); i++) {
                String selectedId = selected.getCompoundTagAt(i).getString("id");
                if (selectedId.trim().isEmpty()) {
                    throw new IllegalArgumentException("Selected UAV identity is missing");
                }
                definition.selectUav(java.util.UUID.fromString(selectedId));
            }
            if (tag.hasKey("policyDomain") && !"ENTITY".equals(tag.getString("policyDomain"))) {
                throw new IllegalArgumentException("Unsupported target policy domain");
            }
            net.vas.rdp.uav.target.TargetPolicy.Builder policy =
                    net.vas.rdp.uav.target.TargetPolicy.builder();
            addList(policy, tag, "policyInclude", 0);
            addList(policy, tag, "policyRequire", 1);
            addList(policy, tag, "policyExclude", 2);
            if (tag.hasKey("policyRejectEmbedded")) {
                policy.rejectDeeplyEmbedded(tag.getBoolean("policyRejectEmbedded"));
            }
            if (tag.hasKey("policyAllowVehicles")) {
                policy.allowHostileVehicles(tag.getBoolean("policyAllowVehicles"));
            }
            if (tag.hasKey("policyMinimumConfidence")) {
                policy.withMinimumConfidence(tag.getDouble("policyMinimumConfidence"));
            }
            definition.setTargetPolicy(policy.snapshot());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid mission definition", invalid);
        }
        return definition;
    }

    private static void writeList(NBTTagCompound tag, String key, java.util.List<String> values) {
        NBTTagList list = new NBTTagList();
        for (String value : values) {
            NBTTagCompound item = new NBTTagCompound();
            item.setString("value", value);
            list.appendTag(item);
        }
        tag.setTag(key, list);
    }

    private static void addList(net.vas.rdp.uav.target.TargetPolicy.Builder policy,
                                NBTTagCompound tag, String key, int kind) {
        NBTTagList list = tag.getTagList(key, 10);
        String[] values = new String[list.tagCount()];
        for (int i = 0; i < list.tagCount(); i++) {
            values[i] = list.getCompoundTagAt(i).getString("value");
        }
        if (kind == 0) policy.include(values);
        else if (kind == 1) policy.require(values);
        else policy.exclude(values);
    }
}
