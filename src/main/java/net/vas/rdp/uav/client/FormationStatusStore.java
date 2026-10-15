package net.vas.rdp.uav.client;

import net.vas.rdp.uav.network.PacketDeconflictionAlert;
import net.vas.rdp.uav.network.PacketFormationSync;
import net.vas.rdp.uav.network.PacketOrbitStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Client-only synchronized presentation snapshots received from the server. */
public final class FormationStatusStore {
    private static List<PacketFormationSync.FormationSlotView> slots =
            Collections.emptyList();
    private static PacketDeconflictionAlert lastAlert;
    private static PacketOrbitStatus lastOrbitStatus;

    private FormationStatusStore() { }

    public static synchronized void replaceFormation(
            List<PacketFormationSync.FormationSlotView> value) {
        slots = Collections.unmodifiableList(new ArrayList<>(value));
    }

    public static synchronized List<PacketFormationSync.FormationSlotView> getFormation() {
        return slots;
    }

    public static synchronized void setLastAlert(PacketDeconflictionAlert value) {
        lastAlert = value;
    }

    public static synchronized PacketDeconflictionAlert getLastAlert() {
        return lastAlert;
    }

    public static synchronized void setLastOrbitStatus(PacketOrbitStatus value) {
        lastOrbitStatus = value;
    }

    public static synchronized PacketOrbitStatus getLastOrbitStatus() {
        return lastOrbitStatus;
    }
}
