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
        metrics.record(43, 1);
        assertEquals(TARGET, metrics.getTargetId());
        assertEquals(TerminalGuidanceMetrics.Classification.TIMEOUT,
                metrics.getClassification());
    }

    @Test
    public void preservesEntryDistanceAfterHistoryEviction() {
        TerminalGuidanceMetrics metrics = new TerminalGuidanceMetrics(TARGET, 100);
        metrics.record(100, 250);
        for (int i = 1; i <= TerminalGuidanceMetrics.HISTORY_LIMIT + 3; i++) {
            metrics.record(100 + i, 250 - i);
        }
        assertEquals(250.0D, metrics.getDistanceAtEntry(), 0.0D);
    }

    @Test
    public void closingRateUsesObservedTickSpacing() {
        TerminalGuidanceMetrics metrics = new TerminalGuidanceMetrics(TARGET, 0);
        metrics.record(10, 100);
        metrics.record(12, 90);
        assertEquals(5.0D, metrics.getClosingRate(), 0.0D);
        metrics.record(12, 80);
        assertEquals(0.0D, metrics.getClosingRate(), 0.0D);
    }

    @Test
    public void metricSeriesRemainTargetIsolated() {
        UUID otherTarget = UUID.randomUUID();
        TerminalGuidanceMetrics first = new TerminalGuidanceMetrics(TARGET, 0);
        TerminalGuidanceMetrics second = new TerminalGuidanceMetrics(otherTarget, 0);
        first.record(0, 100);
        second.record(0, 10);
        second.record(2, 5);
        assertEquals(TARGET, first.getTargetId());
        assertEquals(100.0D, first.getDistanceAtEntry(), 0.0D);
        assertEquals(otherTarget, second.getTargetId());
        assertEquals(10.0D, second.getDistanceAtEntry(), 0.0D);
        assertEquals(2.5D, second.getClosingRate(), 0.0D);
    }

    private TerminalGuidanceMetrics metrics(double... distances) {
        TerminalGuidanceMetrics metrics = new TerminalGuidanceMetrics(TARGET, 0);
        for (int i = 0; i < distances.length; i++) metrics.record(i, distances[i]);
        return metrics;
    }
}
