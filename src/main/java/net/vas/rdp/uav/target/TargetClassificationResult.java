package net.vas.rdp.uav.target;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TargetClassificationResult {
    private final Classification classification;
    private final double confidence;
    private final boolean conflictingEvidence;
    private final List<TargetEvidence> evidence;

    TargetClassificationResult(Classification classification, double confidence,
                               boolean conflictingEvidence,
                               List<TargetEvidence> evidence) {
        if (classification == null || !Double.isFinite(confidence)
                || confidence < 0.0D || confidence > 1.0D
                || evidence == null || evidence.size() > 32) {
            throw new IllegalArgumentException("Invalid classification result");
        }
        for (TargetEvidence item : evidence) {
            if (item == null) throw new IllegalArgumentException("Classification evidence is required");
        }
        this.classification = classification;
        this.confidence = confidence;
        this.conflictingEvidence = conflictingEvidence;
        this.evidence = Collections.unmodifiableList(new ArrayList<>(evidence));
    }

    public Classification getClassification() { return classification; }
    public double getConfidence() { return confidence; }
    public boolean hasConflictingEvidence() { return conflictingEvidence; }
    public List<TargetEvidence> getEvidence() { return evidence; }

    public String explain() {
        if (evidence.isEmpty()) return classification.name() + ": no evidence";
        StringBuilder result = new StringBuilder(classification.name());
        result.append(" (").append(String.format(java.util.Locale.ROOT, "%.2f", confidence))
                .append(')');
        if (conflictingEvidence) result.append(" [conflicting evidence]");
        for (TargetEvidence item : evidence) {
            result.append("; ").append(item.getSource().name())
                    .append(':').append(item.getReason());
        }
        return result.toString();
    }
}
