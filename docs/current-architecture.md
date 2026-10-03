# Current architecture

## High-level layers

```text
Client / Operator UI
    |
    v
RdpUavNetwork + GUI/HUD
    |
    v
MissionManager
    |
    +-- AircraftAssignmentRegistry
    +-- AircraftRegistry
    +-- MissionQueue
    +-- RDPMissionWorldData
    |
    v
MissionLifecycle
    |
    +-- WingManFlightAdapter
    +-- TargetResolver
    +-- KamikazeImpactDetector
    +-- KamikazePilotOverride
    |
    v
McHeliCE / WingMan / Minecraft world
```

## Responsibility boundaries

```text
operator intent
  -> MissionDefinition / server validation
  -> MissionManager assignment
  -> RDPMission state + target UUID
  -> MissionLifecycle execution
       |-> TargetAuthority observation (server-owned target lock remains in mission)
       |-> WingManFlightAdapter route nodes
       |     -> WingmanEntry effective route setpoint
       |     -> AirframeController
       |     -> MissionMovementAuthority lease
       |     -> NativeAircraftControlAdapter attitude/throttle writes
       |     -> McHeliCE physics and collision
       |-> GuidedWeaponAdapter validated native release
             -> McHeliCE/WingMan projectile and effect behavior

client camera selection -> validated server request only
server telemetry -> client HUD projection only
```

Vehicle capabilities are defined by the configured `UavVehicleProfile` /
`VehicleCapability`; `MissionType` represents intent, and `SwarmRole` represents
a member's current assignment. None of those replaces mission state or owns
movement.

## Mission ownership

- `MissionDefinition` represents operator intent and configuration validation;
  it is not a live mission and cannot authorize execution on its own.
- `MissionManager` owns aircraft assignment, authorization checks, and queue
  behavior. It creates and reserves runtime missions on the server.
- `RDPMission` owns persisted mission identity, lifecycle state, target UUID /
  `TargetLock`, and mission outcome metadata. `MissionState` constrains legal
  transitions.
- `MissionLifecycle` owns dispatch and per-tick execution decisions, including
  conversion of server observations into mission transitions. It is the
  production executor; planning/tasking proposals do not replace it.
- `RDPMissionWorldData` and `MissionNbtCodec` own mission world persistence.
  The current worktree codec reports schema 7; that source file has local
  modifications, so this is not a clean-revision or runtime migration claim.
- `RDPMission.getPhase()` and `getControlOwner()` expose compatibility projections for
  flight ownership without replacing the persisted `MissionState` graph. A
  `KAMIKADZE_TARGET` mission is owned by terminal guidance; ordinary target
  locking remains mission-AI ownership and does not install a pilot override.

## Target architecture

- `TargetSpec` defines the mission target definition.
- `TargetSelector` decides the best valid candidate from candidate lists.
- `ContactSnapshot` exposes the center of the tracked hitbox as the world-space
  aim point. Terminal geometry uses this point when a live tracked contact is
  available, rather than an arbitrary camera-relative or stale marker position.
- Terminal guidance is closed-loop at the WingMan boundary: while
  `ControlOwner.TERMINAL_GUIDANCE` is active, the adapter periodically replaces
  the current native `flyTo` waypoint with a bounded, smoothed correction from
  the aircraft and locked entity motion. Terminal corrections contain no
  loiter/orbit node; payload execution remains owned by the mission lifecycle.
- When debug logging is enabled, terminal updates emit bounded telemetry at
  `terminalDiagnosticsIntervalTicks`, including physical aircraft motion,
  target hitbox-center aim point, distance trend, bearing, active waypoint,
  route size/node type, and replacement count. This telemetry is evidence for
  live McHeli validation; it is not a simulation of convergence.
- The deterministic terminal classifier uses a bounded 12-sample history:
  fewer than three samples is `INSUFFICIENT_DATA`; sustained net decrease is
  `CONVERGING` or `SLOW_CONVERGENCE`; noise-sized movement is `STALLED`;
  three consecutive post-minimum increases beyond tolerance is `OVERSHOT`;
  four sustained increases is `DIVERGING`; three or more bounded radial
  reversals is `ORBITING`; timeout is an explicit `TIMEOUT` event. These
  classifications are diagnostic only and never alter flight control.
- `TargetResolver` searches the live world for entities near a location.
- `TargetClassifier` decides whether an entity registry ID is actually hostile or modded hostile.
- `RDPMission.targetId` / `TargetLock` is the authoritative mission lock
  identity. `ContactSnapshot` is the server tracker’s current observation
  projection: it carries entity/registry identity, position, velocity,
  lifecycle, confidence, and entity bounds, but does not replace mission lock
  identity. Client camera selection is a request input; the HUD renders
  synchronized server telemetry and does not create or authorize a lock.

## Flight and execution architecture

- `WingManFlightAdapter` submits and updates WingMan route nodes. WingMan's
  `WingmanEntry.autoTargetX/Y/Z` is the effective current route setpoint; the
  addon uses that value for route following and keeps an active `FLY_TO` node
  synchronized with filtered moving-target guidance.
- `AircraftStateEstimator` records position, MCheli motion, derived horizontal
  and vertical speed, heading, ground track, route distance, mission owner,
  lease state, and sample age. Units are Minecraft blocks per tick and degrees.
- `AirframeController` selects a fixed-wing or helicopter control law from the
  configured `UavVehicleProfile`. The controller compares route course and
  altitude with measured ground track, speed, vertical rate, and orientation,
  and emits bounded yaw/pitch/roll/throttle setpoints.
- `NativeAircraftControlAdapter` is the single addon movement writer. On
  server `WorldTick START` at Forge priority `HIGHEST`, it rate-limits the
  setpoints and calls the verified MCheliCE aircraft setters and
  `updateControl()`. This priority does not establish that the callback runs
  after every WingMan tick callback. The transformer guards matching
  WingMan movement helpers while the mission lease is active. The adapter
  never writes entity position or motion; MCheliCE integrates velocity,
  position, and collisions.
- `MissionMovementAuthority` grants a short per-aircraft lease. The coremod
  guard acquires it before WingMan movement helper writes; while it is active,
  those helper writes are suppressed. The packet hook rejects operator flight
  axes while leaving camera, weapon, and station actions on their native path.
  Unsupported profiles and terminal mission states do not take the helper
  guard, so they are not silently frozen by this control path.
- The fixed-wing and helicopter speed, turn, bank, climb, descent, and throttle
  envelopes are provisional source defaults. They are not measured aircraft
  specifications and must be calibrated from live telemetry per definition.
- `UavVehicleProfile` and `VehicleCapability` describe configured airframe
  capabilities and execution envelopes; `MissionType` describes requested
  mission intent; `SwarmRole` describes a member's current operational
  assignment. Assignment changes do not redefine aircraft capability.
- Fixed-wing and rotary-wing controllers are separate. Fixed-wing control
  accounts for ground track, bank, and stall-speed envelope; rotary control
  uses horizontal velocity/position and collective response. The physical
  `MCH_EntityHeli` type can select the rotary controller independently of the
  configured execution profile. Neither source defaults nor unit tests prove
  live flight quality.
- `FpvFlightStateMachine` retains `MOVING_TO_TARGET`, `HOVER`, and
  `TERMINAL_APPROACH` mission policy. FPV hover holds the current WingMan route
  target with the same helicopter feedback controller; pursuit cannot replace
  hover until reacquisition exits that state. Terminal approach is monotonic.
  There is no detached `FlightCommand` value that appears to control movement
  without a production consumer.
- `GuidedWeaponAdapter` is the separate verified McHeli guided-weapon boundary;
  it accepts only registry-authorized ENTITY/SQUARE objectives and delegates
  target coordinates/UUIDs to native WingMan fields.  A configured guided
  weapon priority is required to opt into this path; otherwise conventional
  UAV, one-way, and square-strike dispatch remains unchanged.
- Guided release diagnostics distinguish dimension rejection, unloaded-chunk
  and line-of-sight holds, unsupported objective/guidance, and ammunition
  rejection. Entity-seeker target assignment is explicitly unsupported at
  this boundary; no projectile steering or seeker state is inferred from
  WingMan coordinates.
- `MissionLifecycle.dispatch(...)` validates mission conditions before a dispatch is attempted.
- `KamikazeImpactDetector` checks nose impact and proximity-detonation conditions.
- `KamikazePilotOverride` supports explicit operator-triggered self-destruct logic.
- `KAMIKADZE_TARGET` is a terminal commitment boundary: entity target
  replacement is rejected by `RDPMission`, and the lifecycle fails the mission
  with `TERMINAL_TIMEOUT` if native execution does not complete within the
  configured terminal window.

## Persistence architecture

- `RDPMissionWorldData` persists mission records in world-saved data.
- `MissionNbtCodec` encodes mission state to NBT.
- `RDPScanReportRepository` persists scan reports and ranks threat values in a similar world-scoped pattern.

## Network and operator surface

- `RdpUavNetwork` handles mission GUI and HUD synchronization.
- `GuiMissionControl` provides a mission-monitoring UI.
- `RdpUavTargetHud` renders operator telemetry.
- `RdpUavCommand` exposes operator commands such as `/rdpuav mission ...` and `/rdpuav debug ...`.

## Current responsibilities

| Component | Responsibility | Notes |
| --- | --- | --- |
| `RDPUavMod` | Forge lifecycle registration | Registers commands, network, tick handlers |
| `MissionManager` | assignment, validation, queue | Source-level mission authority |
| `MissionLifecycle` | dispatch and mission processing | High-value mission execution boundary |
| `WingManFlightAdapter` | WingMan route boundary | Supplies route setpoints to the RDP flight computer |
| `AirframeController` | fixed-wing/helicopter feedback law | Produces bounded desired attitude/throttle |
| `NativeAircraftControlAdapter` | final movement write | Rate-limited MCheliCE setters under the mission lease |
| `TargetSelector` | candidate ranking | deterministic priority-first logic |
| `TargetResolver` | entity lookup | radius-based search in loaded world |
| `TargetClassifier` | hostile namespace recognition | heuristic by registry ID |
| `RdpUavDebug` | diagnostics | verbose server-side logging |

Threat suggestions in `GuiMissionControl` retain the discovered entity UUID
and registry ID instead of being display-only text. Clicking a suggestion sends
the UUID through `RdpUavNetwork.lockAiTarget`; the existing server-side
operator, range, target-policy, and identity validation path remains
authoritative. No client-side target assignment or second target registry is
introduced.

Square-strike missions now persist their configured width/depth in
`RDPMission` and `MissionNbtCodec`. Terminal geometry uses the existing
`SquareArea` containment policy for square missions rather than requiring the
aircraft to equal the immutable center coordinate. WingMan advances route nodes
and publishes active setpoints; the leased RDP airframe controller supplies
bounded attitude/throttle commands while McHeliCE integrates motion and
collision. Live route and collision behavior still requires Forge/McHeliCE
validation.

Blocked approach decisions now install one finite perpendicular detour followed
by the target point through `WingManFlightAdapter.refreshBoundedDetour`.
This replaces the previous repeated orbit route for obstacle/side-approach
decisions and returns control to the normal target route after the detour.

## Known architectural constraints

- The mission system is server-authoritative and is not a fully autonomous “AI brain” by itself.
- The real-world mission logic remains source-level and needs runtime validation in a live Forge instance.
- The architecture is modular enough to support additional hostile-world mission types, but the relevant behavior is not yet implemented as an end-to-end mission doctrine.

## 20 — Movement output

`NativeFlightControlCommand` carries bounded desired yaw, pitch, roll, throttle,
desired ground track, course error, and vertical-speed target. `AirframeController`
derives the command from observed state and the effective WingMan route target.
`NativeAircraftControlAdapter` limits per-tick changes and applies the command
under the mission lease through MCheliCE aircraft setters.

```text
MISSION / TARGET / ROUTE
          ↓
WingMan autoTargetX/Y/Z
          ↓
AircraftStateEstimator (position, velocity, attitude, track)
          ↓
fixed-wing or helicopter feedback controller
          ↓
exclusive movement lease + rate limit
          ↓
MCH_EntityAircraft setters → normal MCheliCE physics and collision update
```

The state machine remains responsible for FPV policy (`MOVING_TO_TARGET`,
`HOVER`, `TERMINAL_APPROACH`); it does not emit a second movement command.
The controller uses direct attitude/throttle setpoints because that is the
verified autonomous API available in the target MCheliCE build. This is a
closed-loop autopilot using native physics, not an emulation of raw player
camera events; live equivalence to a piloted aircraft is not proven.

WingMan's `flyTo` and `loiter` nodes define the route and mission progression.
The RDP profile controller supplies final movement setpoints. HOVER and
target-pursuit never select different movement targets in the same controller
update, and helicopter collective throttle is feedback-controlled rather than
held permanently at maximum. Runtime hover stability has not been demonstrated.

## 21 — Terminal Approach and Nose-Dive Review

The existing nose-dive path is **RETAINED BUT MOVED UNDER FLIGHT AUTHORITY**.
`MissionLifecycle` selects the kamikaze terminal mission state, while
`WingManFlightAdapter.armKamikaze()` appends the bounded approach waypoints to
the WingMan route. The RDP flight computer follows those waypoints through its
fixed-wing or helicopter control law.
`KamikazeImpactDetector` only observes nose geometry and speed; it does not
write position, motion, altitude, or camera orientation. Payload firing remains
an execution action after the terminal geometry and impact policy are
satisfied.

The route planner and flight controller are separate: WingMan advances the
route, while the RDP controller writes bounded attitude/throttle setpoints;
neither writes entity motion. Reusable profiles cannot enter this path unless
destructive authorization and mission checks allow it. The current source
cannot prove live McHeli climb/descent behavior, so terminal speed, pitch
response, impact, and collision timing still require in-game validation.
### Target-policy decisions

Target eligibility remains separate from target locking and flight execution. The
server-side `TargetPolicy.Builder` normalizes include, require, and exclude
values before evaluation and returns a machine-readable `ResultCode` alongside
the human-readable reason. This preserves the existing targeting defaults while
allowing resolver, GUI, telemetry, and network validation paths to report the
same rejection category without parsing display text. Unsupported policy
domains must be rejected explicitly rather than treated as entity filters.

### Swarm coordination boundary

Swarm state is a coordination layer above individual missions. `SwarmRecord`
remains the persisted membership aggregate, while `SwarmAgent`,
`UavCapabilities`, `SwarmConfiguration`, and `SwarmAssignmentManager` provide
explicit capability and role contracts. Role assignment is server-side and
rejects unsupported executor, tracker, or support roles before changing member
state. Assignments do not move aircraft or implement weapon guidance; mission
policy and the leased per-aircraft flight computer own those decisions, while
WingMan provides route sequencing.

Formation slots and search sectors are computed by `FormationController` and
`SearchCoordinator`; they are plans, not movement commands. `SwarmRecoveryManager`
handles unavailable members and commander failover without aborting healthy
missions. `SwarmTargetRegistry` and `SwarmStatus` expose shared identity and
lifecycle state as immutable coordination projections, while `TargetLock`,
`MissionLifecycle`, and the existing weapon adapters remain authoritative for
target tracking, flight, and execution.

### Mission GUI configuration boundary

`MissionDefinition` and `MissionValidationResult` are GUI-independent
configuration contracts. `GuiMissionControl` uses them for local validation and
section navigation, but still submits create, lock, swarm, guidance, and
execution requests through `RdpUavNetwork`. The client does not authorize
targets, steer aircraft, or mutate runtime mission state. Validation failures
are shown in the dedicated validation section; launch remains subject to the
existing server-side mission and safety checks. The GUI launch action uses the
versioned `CreateMission.LaunchMissionDefinition` packet; the server validates
version, target syntax, mission type, swarm bounds, operator ownership, and
guided-weapon availability before mapping the request into the existing mission
command/`MissionManager` path. Legacy `CreateMission` requests remain
supported for older controls and keybinds. `SQUARE_STRIKE` definitions are
routed to the dedicated square-strike packet instead of being downgraded to a
generic entity mission; swarm, custom target-policy, selected-UAV, and
non-default workflow settings are rejected until their runtime authorities are
available on the launch path.
