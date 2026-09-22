# Mission telemetry

RDP-UAV diagnostics are server-side and disabled by default. Enable them with
the existing `rdpuav-debug.cfg` setting or the `/rdpuav debug on` command.

## State transitions

Every accepted `RDPMission.transitionTo(...)` emits one structured debug event:

```text
[DEBUG][MissionLifecycle] event=MISSION_STATE_TRANSITION
mission=<uuid> aircraft=<uuid> from=<state> to=<state>
phase=<phase> controlOwner=<owner> tick=<world-tick> target=<uuid|null>
```

This event is transition-based rather than tick-based. Repeated assignments to
the current state update the mission timestamp but do not emit a duplicate
transition event.

## Terminal guidance

Terminal guidance already emits throttled `TERMINAL_METRIC_START`,
`TERMINAL_CONVERGENCE_STATE`, and `UPDATE` events. `UPDATE` includes vehicle
and target positions, motion, distance, waypoint, native node, replacement
count, trend, classification, closing rate, minimum distance, entry distance,
and elapsed terminal ticks.

These events describe the native route command and observed telemetry. They do
not imply that the aircraft physically converged or that a payload executed.
Physical trajectory classifications require a live runtime capture.
