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
    private final FlightIntent intent;
    private final double confidence;

    public AiDecision(Kind kind, UUID taskId, UUID aircraftId, String reason, int score) {
        this(kind, taskId, aircraftId, reason, score, FlightIntent.HOLD, 0.0D);
    }

    public AiDecision(Kind kind, UUID taskId, UUID aircraftId, String reason, int score,
                      FlightIntent intent, double confidence) {
        if (kind == null || taskId == null || reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("AI decision fields are required");
        }
        if (intent == null || confidence < 0.0D || confidence > 1.0D) {
            throw new IllegalArgumentException("AI decision confidence is invalid");
        }
        this.kind = kind;
        this.taskId = taskId;
        this.aircraftId = aircraftId;
        this.reason = reason;
        this.score = score;
        this.intent = intent;
        this.confidence = confidence;
    }

    public Kind getKind() { return kind; }
    public UUID getTaskId() { return taskId; }
    public UUID getAircraftId() { return aircraftId; }
    public String getReason() { return reason; }
    public int getScore() { return score; }
    public FlightIntent getIntent() { return intent; }
    public double getConfidence() { return confidence; }
}
