package net.vas.rdp.uav.client.vision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded image-space association; it deliberately makes no world-position estimate. */
public final class PerceptionTracker {
    private static final int CONFIRMING_HITS = 3;
    private static final int MAX_STALE_FRAMES = 2;
    private static final int MAX_TRACK_AGE_FRAMES = 8;
    private static final double MAX_CENTROID_DISTANCE_SQUARED = 0.0025D;

    private final Map<Long, MutableTrack> tracks = new LinkedHashMap<>();
    private long nextTrackId = 1L;
    private long lastSequence = -1L;

    public List<TrackView> update(long frameSequence, List<VisualDetection> detections) {
        if (frameSequence < 0L || detections == null) {
            throw new IllegalArgumentException("Frame sequence and detections are required");
        }
        for (VisualDetection detection : detections) {
            if (detection == null) throw new IllegalArgumentException("Detection is required");
        }
        if (frameSequence <= lastSequence) return snapshot();

        long frameGap = lastSequence < 0L ? 1L : frameSequence - lastSequence;
        lastSequence = frameSequence;
        List<Association> candidates = associations(detections);
        boolean[] detectionMatched = new boolean[detections.size()];
        for (Association candidate : candidates) {
            MutableTrack track = candidate.track;
            if (track.matched || detectionMatched[candidate.detectionIndex]) continue;
            track.detection = detections.get(candidate.detectionIndex);
            track.matched = true;
            track.hits++;
            track.missedFrames = 0;
            detectionMatched[candidate.detectionIndex] = true;
        }

        for (MutableTrack track : tracks.values()) {
            if (!track.matched) {
                track.missedFrames = saturatingAdd(track.missedFrames, frameGap);
            }
            track.matched = false;
        }
        for (int i = 0; i < detections.size(); i++) {
            if (!detectionMatched[i]) {
                tracks.put(nextTrackId, new MutableTrack(nextTrackId++,
                        frameSequence, detections.get(i)));
            }
        }

        tracks.values().removeIf(
                mutableTrack -> mutableTrack.missedFrames > MAX_TRACK_AGE_FRAMES);
        return snapshot();
    }

    public List<TrackView> snapshot() {
        List<TrackView> result = new ArrayList<>(tracks.size());
        for (MutableTrack track : tracks.values()) {
            result.add(new TrackView(track.id, track.firstSequence, lastSequence,
                    track.hits, track.missedFrames, track.lifecycle(), track.detection));
        }
        return Collections.unmodifiableList(result);
    }

    public void reset() {
        tracks.clear();
        nextTrackId = 1L;
        lastSequence = -1L;
    }

    private List<Association> associations(List<VisualDetection> detections) {
        List<Association> result = new ArrayList<>();
        for (MutableTrack track : tracks.values()) {
            track.matched = false;
            for (int i = 0; i < detections.size(); i++) {
                VisualDetection detection = detections.get(i);
                if (!track.detection.getClassId().equals(detection.getClassId())) continue;
                double overlap = intersectionOverUnion(track.detection, detection);
                double distance = centroidDistanceSquared(track.detection, detection);
                if (overlap >= 0.05D || distance <= MAX_CENTROID_DISTANCE_SQUARED) {
                    result.add(new Association(track, i, overlap, distance));
                }
            }
        }
        Collections.sort(result, Association.ORDER);
        return result;
    }

    private static double intersectionOverUnion(VisualDetection first, VisualDetection second) {
        double left = Math.max(first.getX(), second.getX());
        double top = Math.max(first.getY(), second.getY());
        double right = Math.min(first.getX() + first.getWidth(),
                second.getX() + second.getWidth());
        double bottom = Math.min(first.getY() + first.getHeight(),
                second.getY() + second.getHeight());
        double intersection = Math.max(0.0D, right - left) * Math.max(0.0D, bottom - top);
        double union = first.getWidth() * first.getHeight()
                + second.getWidth() * second.getHeight() - intersection;
        return union <= 0.0D ? 0.0D : intersection / union;
    }

    private static double centroidDistanceSquared(VisualDetection first, VisualDetection second) {
        double dx = centerX(first) - centerX(second);
        double dy = centerY(first) - centerY(second);
        return dx * dx + dy * dy;
    }

    private static double centerX(VisualDetection detection) {
        return detection.getX() + detection.getWidth() * 0.5D;
    }

    private static double centerY(VisualDetection detection) {
        return detection.getY() + detection.getHeight() * 0.5D;
    }

    private static long saturatingAdd(long first, long second) {
        if (second > Long.MAX_VALUE - first) return Long.MAX_VALUE;
        return first + second;
    }

    private static final class MutableTrack {
        final long id;
        final long firstSequence;
        VisualDetection detection;
        int hits = 1;
        long missedFrames;
        boolean matched;

        MutableTrack(long id, long firstSequence, VisualDetection detection) {
            this.id = id;
            this.firstSequence = firstSequence;
            this.detection = detection;
        }

        TrackLifecycle lifecycle() {
            if (missedFrames > MAX_STALE_FRAMES) return TrackLifecycle.LOST;
            if (missedFrames > 0L) return TrackLifecycle.STALE;
            return hits >= CONFIRMING_HITS ? TrackLifecycle.CONFIRMED : TrackLifecycle.TENTATIVE;
        }
    }

    private static final class Association {
        static final Comparator<Association> ORDER = (first, second) -> {
            int overlap = Double.compare(second.overlap, first.overlap);
            return overlap != 0 ? overlap : Double.compare(first.distanceSquared,
                    second.distanceSquared);
        };

        final MutableTrack track;
        final int detectionIndex;
        final double overlap;
        final double distanceSquared;

        Association(MutableTrack track, int detectionIndex, double overlap,
                    double distanceSquared) {
            this.track = track;
            this.detectionIndex = detectionIndex;
            this.overlap = overlap;
            this.distanceSquared = distanceSquared;
        }
    }

    public enum TrackLifecycle {
        TENTATIVE, CONFIRMED, STALE, LOST
    }

    public static final class TrackView {
        private final long id;
        private final long firstSequence;
        private final long lastProcessedSequence;
        private final int hitCount;
        private final long missedFrames;
        private final TrackLifecycle lifecycle;
        private final VisualDetection detection;

        TrackView(long id, long firstSequence, long lastProcessedSequence,
                  int hitCount, long missedFrames, TrackLifecycle lifecycle,
                  VisualDetection detection) {
            this.id = id;
            this.firstSequence = firstSequence;
            this.lastProcessedSequence = lastProcessedSequence;
            this.hitCount = hitCount;
            this.missedFrames = missedFrames;
            this.lifecycle = lifecycle;
            this.detection = detection;
        }

        public long getId() { return id; }
        public long getFirstSequence() { return firstSequence; }
        public long getLastProcessedSequence() { return lastProcessedSequence; }
        public int getHitCount() { return hitCount; }
        public long getMissedFrames() { return missedFrames; }
        public TrackLifecycle getLifecycle() { return lifecycle; }
        public VisualDetection getDetection() { return detection; }
    }
}
