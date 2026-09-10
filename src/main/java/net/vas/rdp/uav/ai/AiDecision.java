package net.vas.rdp.uav.ai;

import java.util.UUID;

public final class AiDecision {
    public enum Kind {
        RECOMMEND,
        REQUEST_APPROVAL,
        ACCEPT,
        REJECT
    }

    private final Kind kind;
    private final UUID taskId;
    private final UUID aircraftId;
    private final String reason;
    private final int score;

    public AiDecision(Kind kind, UUID taskId, UUID aircraftId, String reason, int score) {
        if (kind == null || taskId == null || reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("AI decision fields are required");
        }
        this.kind = kind;
        this.taskId = taskId;
        this.aircraftId = aircraftId;
        this.reason = reason;
        this.score = score;
    }

    public Kind getKind() { return kind; }
    public UUID getTaskId() { return taskId; }
    public UUID getAircraftId() { return aircraftId; }
    public String getReason() { return reason; }
    public int getScore() { return score; }
}
