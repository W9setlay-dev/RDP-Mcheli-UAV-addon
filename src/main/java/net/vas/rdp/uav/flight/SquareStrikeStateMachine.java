package net.vas.rdp.uav.flight;

public final class SquareStrikeStateMachine {
    private SquareStrikeState state = SquareStrikeState.CREATED;

    public void transitionTo(SquareStrikeState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException("Invalid square strike transition "
                    + state + " -> " + next);
        }
        state = next;
    }

    public void override() { transitionTo(SquareStrikeState.OPERATOR_OVERRIDE); }
    public void abort() { transitionTo(SquareStrikeState.ABORTED); }
    public void emergency() { transitionTo(SquareStrikeState.EMERGENCY); }
    public SquareStrikeState getState() { return state; }
}
