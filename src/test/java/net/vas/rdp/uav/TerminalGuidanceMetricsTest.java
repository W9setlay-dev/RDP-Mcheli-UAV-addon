package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.TerminalGuidanceMetrics;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class TerminalGuidanceMetricsTest {
    private static final UUID TARGET = UUID.randomUUID();

    @Test
    public void classifiesConvergenceAndSlowConvergence() {
        TerminalGuidanceMetrics metrics = metrics(100, 80, 60, 40, 25, 15, 10, 8, 7);
        assertEquals(TerminalGuidanceMetrics.Classification.CONVERGING,
                metrics.getClassification());

        metrics = metrics(100, 95, 90);
        assertEquals(TerminalGuidanceMetrics.Classification.SLOW_CONVERGENCE,
                metrics.getClassification());
    }

    @Test
    public void detectsStallAndNeedsHistory() {
        TerminalGuidanceMetrics metrics = metrics(10, 10.02);
        assertEquals(TerminalGuidanceMetrics.Classification.INSUFFICIENT_DATA,
                metrics.getClassification());
        metrics.record(3, 10.01);
        assertEquals(TerminalGuidanceMetrics.Classification.STALLED,
                metrics.getClassification());
    }

    @Test
    public void distinguishesNoiseFromOvershoot() {
        TerminalGuidanceMetrics metrics = metrics(10, 10.02, 10.01, 10.03);
        assertEquals(TerminalGuidanceMetrics.Classification.STALLED,
                metrics.getClassification());
        metrics = metrics(100, 70, 45, 20, 8, 12, 22, 35);
        assertEquals(TerminalGuidanceMetrics.Classification.OVERSHOT,
                metrics.getClassification());
    }

    @Test
    public void detectsOrbitAndDivergence() {
        TerminalGuidanceMetrics metrics = metrics(100, 70, 40, 20, 35, 55, 30, 15, 28, 50, 35, 18);
        assertEquals(TerminalGuidanceMetrics.Classification.ORBITING,
                metrics.getClassification());
        metrics = metrics(20, 25, 30, 36);
        assertEquals(TerminalGuidanceMetrics.Classification.DIVERGING,
                metrics.getClassification());
    }

    @Test
    public void retainsTargetAndTimeout() {
        TerminalGuidanceMetrics metrics = new TerminalGuidanceMetrics(TARGET, 42);
        metrics.record(42, 20);
        metrics.timeout();
        assertEquals(TARGET, metrics.getTargetId());
        assertEquals(TerminalGuidanceMetrics.Classification.TIMEOUT,
                metrics.getClassification());
    }

    private TerminalGuidanceMetrics metrics(double... distances) {
        TerminalGuidanceMetrics metrics = new TerminalGuidanceMetrics(TARGET, 0);
        for (int i = 0; i < distances.length; i++) metrics.record(i, distances[i]);
        return metrics;
    }
}
