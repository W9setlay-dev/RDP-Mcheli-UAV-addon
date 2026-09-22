# Mission telemetry

RDP-UAV diagnostics are server-side and disabled by default. Enable the
general logger with the existing `rdpuav-debug.cfg` setting or the
`/rdpuav debug on` command. Enable the bounded mission extension with
`/rdpuav debug mission on`, or set `B:missionDiagnostics=true` in the same
configuration file.

## Mission runtime snapshots

Mission diagnostics use the existing `RdpUavDebug` logger and add
`MissionRuntime` events without changing mission behavior:

- `MISSION_DISPATCH_ATTEMPT`: sampled dispatch availability and mission state.
- `TARGET_LOOKUP`: sampled target presence and formatted target identity/position.
- `ROUTE_DISPATCH`: native route command acceptance, route size, mission index,
  node timer, and current node.
- `ROUTE_REFRESH`: waypoint replacement acceptance and native route state.
- `MISSION_SNAPSHOT`: every five world ticks while active, including mission
  state/phase/owner, aircraft position/yaw/pitch/motion, target position and
  distance, target UUID, route size, mission index/node timer, native target
  coordinates, current node, and native state.

These fields distinguish a command being issued or accepted from observed
aircraft state. They do not imply that the native controller moved the vehicle
or that a weapon affected the target.

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
