# McHeliCE & RDP UAV Integration Map

This file is repository context for Copilot work on the Minecraft 1.12.2 addon.
It describes the checked-in architecture and separates it from design proposals.
For detailed ownership, invariants, and runtime evidence, read the linked
architecture and API-verification documents before changing behavior.

## Verified runtime boundary

- The project targets Java 8 and Minecraft 1.12.2. McHeliCE 1.5.1 and WingMan
  runtime jars are supplied by the local environment, not checked in.
- `RdpUavCorePlugin` registers `RdpUavTransformer`, an ASM
  `IClassTransformer`. The current integration is **not** implemented with
  SpongePowered `@Mixin`.
- Mission intent, target identity, lifecycle, and control leases belong to the
  addon. WingMan supplies route sequencing and active route coordinates;
  `WingManFlightAdapter` is the boundary to native route state.
- Under an active supported mission-control lease,
  `NativeAircraftControlAdapter` rate-limits and applies attitude/throttle
  setpoints using verified McHeliCE methods: `setRotYaw(float)`,
  `setRotPitch(float)`, `setRotRoll(float)`, `setThrottle(double)`, and
  `updateControl()`. These are not the proposed `setControlPitch/Yaw/Roll`
  methods. The addon does not teleport the aircraft or directly write its
  position or motion. McHeliCE continues its normal physics and collision
  update.
- The transformer guards selected WingMan/autonomous movement writers and
  suppresses operator pilot-axis input when addon mission authority applies.
  It is a targeted compatibility hook, not a blanket replacement for native
  station, camera, weapon, or mission behavior.
- Source-level and unit-test evidence do not prove aircraft trajectories,
  terminal convergence, impact, damage, or successful payload effects. Use
  the runtime boundary and checklist in
  [API verification notes](../docs/api-verification-notes.md) and the
  [runtime test matrix](../docs/runtime-test-matrix.md) before making such
  claims.

## Component and ownership map

```text
operator UI / commands
        |
        v
RdpUavNetwork -> MissionManager -> RDPMission (identity, state, persistence)
                                      |
                                      v
                               MissionLifecycle
                         /            |            \
                        v             v             v
           TargetAuthority   WingManFlightAdapter  TerrainRoutePlanner
                                       |             |
                                       v             v
                             WingMan route state   WorldPerceptionAdapter
                                       |
                                       v
                             AirframeController
                                       |
                                       v
                         NativeAircraftControlAdapter
                                       |
                                       v
                          McHeliCE aircraft physics
```

| Concern | Current owner / boundary | Guardrail |
| --- | --- | --- |
| Mission state and locked target | `RDPMission`, `MissionManager`, `MissionLifecycle` | State changes use the mission transition API; a committed entity target remains identified by UUID. |
| Route sequencing | Native WingMan behind `WingManFlightAdapter` | Treat the active WingMan target as a route setpoint, not as permission to mutate entity motion. |
| Vehicle-specific control law | `AirframeController` selects fixed-wing or helicopter controller from profile and runtime type | Unsupported profiles fail explicitly; tune against observed runtime behavior. |
| Final aircraft setpoints | `NativeAircraftControlAdapter` under `MissionMovementAuthority` | Apply bounded native setters; do not set entity position or motion. |
| Terrain and airspace observations | `WorldPerceptionAdapter`; Minecraft implementation is `MinecraftWorldPerceptionAdapter` | Queries do not request chunk loads. Unknown/unloaded observations are not clear-air evidence. |
| Payload and guided weapons | `GuidedWeaponAdapter` and native McHeli/WingMan adapter contracts | Do not directly steer spawned projectiles or infer an impact from a command. |
| Coremod integration | `RdpUavCorePlugin` + `RdpUavTransformer` | Verify transformed class/method names, descriptors, and preservation tests against the installed jar. |

See the [current architecture](../docs/current-architecture.md) for the wider
mission, target, and persistence map; [API verification notes](../docs/api-verification-notes.md)
for evidence and unsupported native operations; and
[terrain, FSM, authority, and trim notes](../docs/terrain-fsm-authority-and-trim.md)
for the terrain/control implementation and its limits.

## Terrain and corridor: current behavior vs. design constraints

### Current source behavior

- `MinecraftWorldPerceptionAdapter` checks chunk/block availability before
  reading terrain or airspace and does not load missing chunks. Terrain
  observations distinguish loaded, unloaded, and unknown; airspace samples
  also report collision and fluid occupancy.
- `TerrainRoutePlanner` samples transit terrain at bounded intervals (up to 49
  samples, 8 blocks apart) over a lookahead derived from speed and capped at
  384 blocks. The current speed horizon is 40 ticks; it is not a configurable
  reaction-time parameter.
- Transit altitude is terrain-relative: the planner derives cruise altitude
  from the highest sampled surface and the profile's AGL limits, then checks
  climb feasibility and a sampled 3D airspace corridor.
- Unknown/unloaded terrain or airspace, collision/fluid occupancy, and
  infeasible climb produce explicit hold plans. This is bounded local sampling,
  not a complete voxel ray tracer or global detour planner.
- Terminal routing has a separate clearance policy and a limited final-contact
  exception; do not apply transit altitude rules blindly to terminal commitment.

### Requirements for future terrain work

- Compute the required surface from the highest relevant solid-block top and
  fluid top; never let water or lava be treated as empty air.
- During transit, anchor route altitude to known terrain plus profile-specific
  minimum/cruise AGL, subject to world-height and climb-envelope limits. Do not
  set transit altitude from target Y.
- Define predictive lookahead as horizontal speed multiplied by an explicit
  reaction horizon when introducing that policy. Sample the 3D corridor; an
  unloaded/unknown sample must remain blocked and request a documented safe
  hold/climb/replan state.
- Keep mission intent, target commitment, profile capability, flight authority,
  terminal guidance, and recovery state separate. Do not invent lifecycle enum
  values such as `APPROACH_GATE` or `TERMINAL_DIVE` without wiring transitions,
  persistence/migration, and tests.

## Guidance mathematics: proposal, not a claim of implementation

For a future proportional-navigation design, define relative position
`r = p_target - p_vehicle`, relative velocity `v_rel = v_target - v_vehicle`,
range `R = |r|`, and line-of-sight unit vector `r_hat = r / R`. The LOS angular
rate vector is `omega_LOS = (r x v_rel) / R^2`. Closing speed is
`V_c = -dot(r_hat, v_rel)`; only positive closing speed represents closure.
The proposed normal acceleration command is
`a_c = N * V_c * (omega_LOS x r_hat)`, with a documented navigation constant
in the requested interval `N in [3, 5]`. Handle zero range, non-finite state,
receding targets, dimensionality, acceleration limits, and target validity
explicitly. Validate the sign convention and coordinate frame with tests before
connecting this model to a controller.

The supplied cross-track expression
`e_track = dot(p_vehicle - p_start, n_path)` is a signed scalar error for a
specified unit normal `n_path`, not a 3D position-error vector. Define the
horizontal/3D path frame and sign convention before using it.

Do not convert an acceleration vector directly into the fictional
`setControlPitch`/`setControlYaw` API. Aircraft commands must go through the
existing profile controller and leased native control adapter; guided weapons
must remain behind `GuidedWeaponAdapter`. Establish measured or explicitly
provisional g-load, turn-rate, and pitch-rate envelopes for each profile rather
than asserting unsupported airframe limits.

## Five-phase designer workflow

Use these phases for substantial cross-subsystem work. Gather source/API
evidence in Phases 1-3; do not generate production code based solely on the
suggested names in a prompt. Keep live-runtime validation distinct from tests.

### Phase 1: Architecture

> Design an adaptive terrain-clearance and water-avoidance extension for
> `rdp-uav-addon`. Do not write code yet. Trace current terrain perception,
> `TerrainRoutePlanner`, `MissionLifecycle`, `AirframeController`, mission
> state/persistence, and movement authority. Propose data structures, safe
> state transitions, unknown/unloaded chunk behavior, recovery, and tests.
> Clearly separate existing APIs from proposed components and identify the
> smallest implementation slice.

### Phase 2: Math model

> Specify surface height including solid and fluid surfaces, terrain-relative
> route altitude, lookahead, climb feasibility, and any proposed 3D LOS or
> proportional-navigation equations with coordinate frames and sign
> conventions. State finite/zero-range and unknown-terrain behavior. Do not
> claim flight-envelope limits without source evidence or measured telemetry;
> distinguish provisional tuning parameters from verified constants.

### Phase 3: Bytecode integration strategy

> Inspect `RdpUavCorePlugin`, `RdpUavTransformer`, transformer tests, and the
> installed McHeliCE/WingMan 1.5.1 bytecode. Identify exact current target
> classes, method names/descriptors, tick ordering, and ownership guards needed
> for this behavior. The project currently uses ASM `IClassTransformer`.
> Evaluate SpongePowered `@Mixin` only as a separate migration proposal:
> verify that classes such as `MCH_WeaponGuidanceSystem` or
> `MCH_EntityTVMissile` exist in this runtime, that the desired method and
> mapping are real, and that MixinBooter configuration is wired before
> recommending hooks. Do not present proposed mixins as implemented.

### Phase 4: Production implementation

> Implement the smallest complete change that follows the repository's
> verified APIs, Java 8 / Forge 1.12.2 constraints, mission authority, and
> native movement boundary. Include validation, explicit blocked/failure
> outcomes, persistence/migration if state changes, and focused regression
> tests. Do not add stubs or speculative McHeli methods. Update directly
> related docs; do not claim physical flight behavior from compilation.

### Phase 5: Telemetry and test harness

> Design bounded diagnostics and a focused test matrix for the new behavior.
> Identify what can be proven with deterministic Java tests and what requires
> a live Forge/McHeli instance. For client HUD work, verify the existing
> rendering/event path, projection, thread side, and 1.12.2 rendering APIs
> before proposing VBO/BufferBuilder code; do not describe a renderer as
> Vulkan-compatible without evidence.

## Reusable capability prompts

### Proportional-navigation terminal guidance

> Design and test a standalone pure-math guidance component for a future
> guided-ordnance or one-way terminal profile. Inputs are finite vehicle and
> target positions/velocities in the same dimension and coordinate frame.
> Derive the true 3D PN law, LOS angular-rate vector, closing-speed handling,
> and acceleration limits; define zero-range and receding-target behavior.
> Enable terminal guidance only inside a configured gate with validated target
> identity and a clear opaque-block LOS. Before implementation, determine
> whether the result belongs in `GuidedWeaponAdapter` or the leased aircraft
> controller. Do not steer projectiles directly or call nonexistent aircraft
> input methods.

### Square-strike grid coverage

> Extend the existing square-strike route model only after tracing its persisted
> area dimensions, `MissionLifecycle`, and `WingManFlightAdapter`. Design a
> bounded serpentine 2D coverage pattern over the configured area; for every
> route point, use loaded terrain observations and profile AGL before dispatch.
> Treat unknown chunks and blocked 3D segments as explicit hold/replan outcomes;
> add altitude steps only when world height, climb envelope, and corridor
> clearance are validated. Preserve target/mission authority, persistence
> compatibility, and route bounds, with deterministic tests for coverage and
> blocked segments.

## Change checklist

Before changing this integration:

1. Read the three architecture/API/terrain references linked above and inspect
   current callers, tests, persistence, and bytecode signatures.
2. Preserve UUID target authority, server-side validation, movement leases,
   profile-specific behavior, and fail-closed unknown-terrain handling.
3. Use existing adapters. Do not add direct position/motion writes, unsupported
   native APIs, unbounded diagnostic logging, or speculative Mixin wiring.
4. Run focused tests, then the relevant project build/test and
   `git diff --check`. Report runtime as `STARTED`, `BLOCKED`, or `NOT ATTEMPTED`;
   tests alone do not prove in-game flight or payload outcomes.
