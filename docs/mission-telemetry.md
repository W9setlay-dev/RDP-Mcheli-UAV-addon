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
- `TerminalGuidance/WAYPOINT_UPDATE_ACCEPTED`: terminal replacement of only the
  active immutable `FLY_TO` node. The route suffix, mission index, and node
  timer are preserved.
- `TerminalGuidance/WAYPOINT_UPDATE_REJECTED`: terminal update was not applied,
  including when the native active node is not a `FLY_TO` node.
- `TargetLock/REQUEST`, `TargetLock/ACCEPTED`, and `TargetLock/REJECTED`:
  server-side lock authorization, validated target identity, mission identity,
  range, dimension, and the exact rejection reason.
- `MISSION_SNAPSHOT`: every five world ticks while active, including mission
  state/phase/owner, aircraft position/yaw/pitch/motion, target position and
  distance, target UUID, route size, mission index/node timer, native target
  coordinates, current node, and native state.

These fields distinguish a command being issued or accepted from observed
aircraft state. They do not imply that the native controller moved the vehicle
or that a weapon affected the target.

The server-side projectile bridge also emits a bounded outcome record when a
projectile that was observed by the addon disappears without an authoritative
impact/effect event. The owning mission transitions to `OUTCOME_UNKNOWN` and
includes either `PROJECTILE_DISAPPEARED_WITHOUT_IMPACT` or
`PROJECTILE_NOT_OBSERVED_WITHIN_TIMEOUT` as the reason. This is intentionally
not reported as a hit or successful payload effect.

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

Entity target lock is a designation operation, not a mission creator. A lock
request is rejected with `NO_ACTIVE_MISSION` when the aircraft has no selected
mission; the server no longer silently creates a reconnaissance mission.

## Payload outcome states

`LAUNCH_ACCEPTED` means only that the native release boundary accepted a
launch. It is deliberately non-terminal. A later observation may move the
mission through `PROJECTILE_TRACKED`, `IMPACT_OBSERVED`, and
`EFFECT_CONFIRMED`; loss of the projectile or insufficient evidence is
represented by `OUTCOME_UNKNOWN`. `COMPLETE` is reserved for the required
mission outcome and recovery, not for the launch call itself.

## Runtime validation boundary

The ForgeGradle development server is considered startup-valid only after all
of the following are observed in the same run: coremod loading, all expected
mods loaded, `DedicatedServer: Done`, and RDP Core marking the server context
`RUNNING`. These milestones prove server initialization, not physical UAV
flight.

The development runtime currently logs a nonfatal reflection stack trace from
the external `rdpcore-1.0.0.jar` when its world-save integration requests a
legacy `RDPWorldSavedData(String)` constructor. That dependency is outside this
repository and is not modified by the addon. It must remain a separately
reported runtime limitation until the matching RDP Core artifact is supplied.
