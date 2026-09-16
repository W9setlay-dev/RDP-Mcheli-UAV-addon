package net.vas.rdp.uav.weapon;

public final class GuidedWeaponDecision {
    private final boolean accepted;
    private final String reason;
    private final String weapon;
    private GuidedWeaponDecision(boolean accepted, String reason, String weapon) {
        this.accepted = accepted; this.reason = reason; this.weapon = weapon;
    }
    public static GuidedWeaponDecision accept(String weapon) {
        return new GuidedWeaponDecision(true, "OK", weapon);
    }
    public static GuidedWeaponDecision reject(String reason) {
        return new GuidedWeaponDecision(false, reason, null);
    }
    public boolean isAccepted() { return accepted; }
    public String getReason() { return reason; }
    public String getWeapon() { return weapon; }
}
