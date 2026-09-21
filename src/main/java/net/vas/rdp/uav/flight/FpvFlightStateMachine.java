package net.vas.rdp.uav.flight;

/**
 * Explicit transition boundary for FPV movement policy.
 */
public final class FpvFlightStateMachine {
    private FpvFlightState state = FpvFlightState.MOVING_TO_TARGET;

    public FpvFlightState getState() {
        return state;
    }

    public void moveToTarget() {
        transition(FpvFlightState.MOVING_TO_TARGET);
    }

    public void hover() {
        transition(FpvFlightState.HOVER);
    }

    public void beginTerminalApproach() {
        transition(FpvFlightState.TERMINAL_APPROACH);
    }

    private void transition(FpvFlightState next) {
        if (next == null) {
            throw new IllegalArgumentException("FPV flight state is required");
        }
        if (state == FpvFlightState.TERMINAL_APPROACH && next != state) {
            throw new IllegalStateException("Terminal FPV approach cannot change flight state");
        }
        state = next;
    }
}
