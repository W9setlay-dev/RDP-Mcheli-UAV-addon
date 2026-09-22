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

## Mission ownership

- `MissionManager` owns assignment state, aircraft auth, and queue behavior.
- `RDPMission` owns the mission’s target, state, last update tick, and failure reason.
- `MissionLifecycle` owns the dispatch/update loop and conversion of target findings into state transitions.
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
- `ContactSnapshot` is the authoritative tracked-contact projection: it carries
  stable entity/registry identity, current position, velocity, lifecycle,
  confidence, and current entity bounds. `TargetLock` additionally supports
  lock-owner and mission association metadata.

## Flight and execution architecture

- `WingManFlightAdapter` is the verified handoff boundary to WingMan.
- `UavVehicleProfile` selects mission capability independently of the native
  McHeli airframe type. One-way FPV vehicles use the explicit
  `HELICOPTER_ONE_WAY_FPV` profile; reusable UAVs retain altitude-hold and
  observation behavior.
- `FpvFlightStateMachine` keeps FPV movement policy explicit:
  `MOVING_TO_TARGET`, `HOVER`, and `TERMINAL_APPROACH`. `HOVER` is a bounded
  native loiter at the current position, not an unconditional altitude
  controller. Target identity remains attached while the aircraft waits for
  reacquisition.
- `FlightCommand` is the single FPV movement output. HOVER emits zero
  forward/lateral/vertical command values and owns the native hold route;
  target pursuit is rejected while HOVER owns the tick. Terminal approach is
  monotonic and cannot be overwritten by a later pursuit update. An attack
  intent still starts in `AI_APPROACH`; `TERMINAL_APPROACH` is selected only
  when the mission explicitly arms the one-way terminal/kamikaze path.
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
| `WingManFlightAdapter` | native WingMan handoff | Real controller boundary |
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
aircraft to equal the immutable center coordinate. The existing WingMan route
boundary remains responsible for movement; live native route and collision
behavior still requires Forge/McHeliCE validation.

Blocked approach decisions now install one finite perpendicular detour followed
by the target point through `WingManFlightAdapter.refreshBoundedDetour`.
This replaces the previous repeated orbit route for obstacle/side-approach
decisions and returns control to the normal target route after the detour.

## Known architectural constraints

- The mission system is server-authoritative and is not a fully autonomous “AI brain” by itself.
- The real-world mission logic remains source-level and needs runtime validation in a live Forge instance.
- The architecture is modular enough to support additional hostile-world mission types, but the relevant behavior is not yet implemented as an end-to-end mission doctrine.

## 20 — Movement Output

```text
FlightCommand
├── forward
├── lateral
├── vertical
├── yaw
├── pitch
├── speed
├── mode
└── terminal/avoidance state
```

Supported FPV modes are:

```text
PLAYER_CONTROLLED
AI_APPROACH
HOVER
REPOSITION
TERMINAL_APPROACH
TERMINAL_STATE
```

The control loop is:

```text
TARGET LOCK
     ↓
TARGET STATE RESOLVER
     ↓
FPV FLIGHT DECISION
  /       |        \
APPROACH HOVER  TERMINAL
   ↓       ↓         ↓
forward + stabilize direct
vertical  position  approach
correction
     \       |       /
      └──────┴──────┘
             ↓
   ONE FINAL FLIGHT COMMAND
             ↓
          McHeliCE
```

The adapter delegates that final command to McHeliCE/WingMan's native
`flyTo` and `loiter` route primitives. No maximum-engine hold is treated as
normal FPV behavior, and HOVER and target-pursuit never write movement in the
same controller update.

## 21 — Terminal Approach and Nose-Dive Review

The existing nose-dive path is **RETAINED BUT MOVED UNDER FLIGHT AUTHORITY**.
`MissionLifecycle` selects the kamikaze terminal mission state, while
`WingManFlightAdapter.armKamikaze()` appends the bounded native approach
waypoints through the same WingMan mission route used by other movement.
`KamikazeImpactDetector` only observes nose geometry and speed; it does not
write position, motion, altitude, or camera orientation. Payload firing remains
an execution action after the terminal geometry and impact policy are
satisfied.

This is not a second dive controller: no class outside
`WingManFlightAdapter` writes aircraft motion, and reusable profiles cannot
enter this path unless destructive authorization and the existing mission
checks allow it. The current source cannot prove live McHeli climb/descent
behavior, so terminal speed, native pitch response, and collision timing still
require in-game validation.
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
and WingMan remain the movement and execution authorities.

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
