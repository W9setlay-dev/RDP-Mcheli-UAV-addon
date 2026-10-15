# Swarm formation and path planning

## Ownership and data flow

The server-owned `SwarmManager` remains the authority for swarm membership,
formation slots, derived role constraints, and predicted-conflict monitoring.
Formation geometry is reconciled into the existing swarm slot model; clients
receive presentation snapshots and do not choose authoritative assignments.

WingMan continues to supply the active route target. During non-terminal route
phases, `WingManFlightAdapter` converts that target into a cached, sampled path
using `PathPlanner`, then passes the sample through the existing fixed-wing or
helicopter controller and movement-lease boundary. The cache is scoped to the
aircraft, mission, route node, and autonomous state; it replans when the route
target materially changes. Terminal-stage guidance deliberately retains its
existing direct path.

The pure path API also provides orbit, figure-eight, and racetrack primitives,
plus a straight terminal-approach utility. Those primitives are planning
capabilities; no claim is made that every primitive is currently selected by
WingMan route state.

## Synchronization and configuration

Server-to-client message types are registered for formation snapshots,
deconfliction alerts, and orbit status:

- `PacketFormationSync`
- `PacketDeconflictionAlert`
- `PacketOrbitStatus`

Configuration examples are provided in `config/rdp-uav-formation.cfg.example`
and `config/rdp-uav-path.cfg.example`. Runtime packet behavior and the
aircraft-specific path profiles still require validation in a compatible
Minecraft 1.12.2 / McHeliCE client and world.

## Current limits

- Conflict prediction and alerting are wired into server swarm reconciliation.
  The deconfliction service can generate and validate bounded proposals, but
  the manager does not yet apply those proposals to live mission routes or
  formation slots. Alerts therefore report detection, not an applied
  resolution.
- Role constraints are derived and held as transient server snapshots. Their
  `holdFormation` flag is consumed by route following. Engagement,
  detection-range, and formation-priority fields are not yet enforced by a
  mission-tasking consumer.
- Route samples are terrain-checked by the existing terrain route planner
  before control is applied. Terminal guidance remains outside cruise-path
  smoothing.
- Unit and contract tests do not replace in-game multiplayer, packet, or
  aircraft-profile validation.

## Validation

The Java 8 test suite covers formation assignment, conflict detection and
proposal validation, Dubins/orbit/route planning, sampled controller guidance,
and swarm contracts. Live Minecraft/McHeliCE runtime validation remains
outstanding unless a compatible client and world are available.
