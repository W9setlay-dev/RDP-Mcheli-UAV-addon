# UAV AI Framework Rework Report

## Scope and evidence policy

This is the single cumulative report for the server-authoritative UAV mission
framework rework requested for Minecraft Forge 1.12.2, MCheliCE 1.5.1, and the
RDP modpack. Runtime behavior is not considered proven without developer
supplied in-game telemetry. Runtime-dependent items remain
`PENDING_DEVELOPER_IN_GAME_TEST`.

## Phase 0 — initial read-only audit

### Repository and build baseline

- Repository: `D:\Forge Modding\rdp-uav-addon`
- Branch: `main`, `ahead 15` of `origin/main`
- Initial working tree: clean; no unrelated modified or untracked files were
  reported by `git status --short --branch`.
- Latest commit at audit start: `ff42fa5 Save uncommitted changes`.
- Build: Gradle/ForgeGradle 2.3, Java source/target 8, Minecraft 1.12.2.
- `build.gradle` currently targets Forge `1.12.2-14.23.5.2768`; project
  documentation also refers to `14.23.5.2864`. This version drift remains an
  integration risk.
- Local dependencies include `libs/mcheli-ce-1.5.1.jar`,
  `libs/rdpcore-1.0.0.jar`, and `libs/modularui-3.0.2.jar`.
- Existing tests and generated reports are present. Prior documentation
  records source-level test success, but does not establish live flight,
  packet, payload, or impact behavior.

### Existing production architecture

The production path is:

`RDPUavMod.ServerTickHandler` → `MissionOrchestrator` /
`MissionLifecycle` → `WingManFlightAdapter` → MCheliCE `WingmanEntry`.

At `WorldTick START`, `MissionLifecycle.applyNativeControls` calls
`WingManFlightAdapter.applyNativeControl`, which can call
`NativeAircraftControlAdapter.apply`. At `WorldTick END`, mission dispatch,
route/target updates, lifecycle transitions, missile guidance, and telemetry
run. The adapter currently writes bounded yaw/pitch/roll, throttle, and
`updateControl()` directly; it does not write position or motion.

The repository also contains a separate native WingMan route writer. This means
the audit must prove whether direct native attitude/throttle writes are part of
the intended single authority or compete with `AutonomousFlightHandler` /
`WingmanTickHandler`. Existing documentation is inconsistent on this point.

### Verified local MCheliCE 1.5.1 facts

Inspection of `libs/mcheli-ce-1.5.1.jar` with `jar tf` and `javap` verified:

- `MCH_EntityAircraft` exposes `setRotYaw(float)`,
  `setRotPitch(float)`, `setRotRoll(float)`, `setThrottle(double)`,
  `setCurrentThrottle(double)`, `getCurrentThrottle()`, `updateControl()`,
  `isUAV()`, `getUavStation()`, `getControlled()`, and `getOperator()`.
- `PacketPlayerControlBase` has protected processing methods including
  `handlePilotControls(...)`, `handleUseWeapon(...)`, and
  `handleStationUnmount(...)`.
- The local jar contains `MCH_EntityUavStation`, all player-control packet
  subclasses, and the WingMan autonomous handlers.
- Signature availability does not prove physical semantics. The meaning of
  `setThrottle` versus `setCurrentThrottle`, event order, and packet-to-physics
  behavior still require bytecode/source tracing and runtime evidence.

### Authority gap requiring resolution

`MissionMovementAuthority` currently controls only addon calls through an
in-memory per-aircraft lease. `operatorFlightInputAllowed` is a policy query,
not a MCheli packet interception point. `UavControlAccess` identifies a
station operator, but no production code currently rejects or neutralizes
McHeliCE `PacketPlayerControlBase` flight-axis input while a mission lease is
active. The existing `RdpUavTransformer` targets
`MCH_EntityUavStation`, but only patches null handling in
`attackEntityFrom`; it does not arbitrate flight packets.

Until this is closed, the addon must not claim that AI ownership suppresses
operator movement input. The implementation phase must either add a verified
transformer/integration hook at the real packet application boundary, or fail
closed and document the profile as unsupported. No fake player, packet spoof,
position teleport, or projectile/aircraft motion replacement is acceptable.

### Developer decision

The developer authorized all required integration approaches, including a
narrow coremod/ASM transformer when public MCheliCE APIs are insufficient.

### Existing coverage and likely gaps

The repository already contains deterministic tests for mission lifecycle,
target tracking, terminal guidance metrics, native control math, vehicle
profiles, square-strike production wiring, and movement-authority leases.
Those tests do not yet prove:

- MCheli station packet arbitration and stale input clearing.
- Actual `setThrottle`/`setCurrentThrottle` physics semantics.
- Single movement writer ordering against native WingMan handlers.
- Bounded swept-volume obstacle planning and unknown chunk handling.
- Complete fixed-wing and helicopter control laws against observed state.
- Truthful live payload/effect confirmation and projectile lifecycle.

## Questions requiring a developer decision

1. **Dependency integration boundary:** if bytecode tracing shows that
   suppressing station/player flight inputs requires a MCheliCE source or
   transformer change outside the addon’s public hooks, may this project add a
   narrowly scoped coremod transformer for the exact packet application method?
   Recommended default: allow only a verified, fail-closed transformer in this
   addon; do not modify or publish a separate MCheliCE fork.
2. **Forge target:** should the rework standardize on
   `14.23.5.2768` (the active build file) or `14.23.5.2864` (the documented
   runtime)? Recommended default: preserve the active build target until the
   developer confirms the installed runtime.

Independent source inspection, bytecode analysis, tests, and documentation can
continue while these decisions are pending. Any dependent implementation will
be marked unsupported rather than silently assumed.

## Proposed implementation phases

1. Verify MCheli packet/handler bytecode and establish one movement authority.
2. Implement control ownership, packet arbitration, stale-input clearing, and
   lease lifecycle.
3. Complete bounded aircraft observation and telemetry.
4. Connect fixed-wing route-following control and per-aircraft capabilities.
5. Connect helicopter/FPV control and verified throttle/hover behavior.
6. Add bounded swept-volume route/obstacle planning and safe replanning.
7. Complete moving-target tracking, terminal commitment, square strike, and
   one-way payload outcome handling.
8. Complete reusable UAV standoff, operator-confirmed weapons, and projectile
   outcome tracking.
9. Audit target controls, filters, network authorization, and HUD state.
10. Run regression/build checks and publish the developer in-game validation
    matrix.

## Evidence classification

Current findings are based on source inspection, repository documentation, and
local jar signature inspection. No claim in this report is live in-game
verified. Runtime claims must be labeled `PENDING_DEVELOPER_IN_GAME_TEST` until
the developer supplies build identity, logs, and observed telemetry.

## Next actions

- Trace `PacketPlayerControlBase.process` and its subclasses in the exact local
  jar, including field writes and aircraft lookup.
- Trace WingMan tick handlers and Forge event registration/order.
- Determine the semantics of throttle setters and whether a safe, singular
  control boundary is possible without dependency modification.
- Then implement the first authority phase and update this report with exact
  symbols, tests, commands, and results.

## Phase 1 — packet movement arbitration

### Objective

Reject server-side flight-axis input at the real MCheliCE packet application
boundary while an active mission owns the aircraft, without suppressing
camera, weapon, unmount, or other operator channels.

### Changes

- `MissionMovementAuthority.suppressOperatorFlightInput(...)` now resolves the
  server mission assignment and, for `MISSION_AI`, `TERMINAL_GUIDANCE`, or
  `RECOVERY`, clears `throttleUp`, `throttleDown`, `moveLeft`, and
  `moveRight`. It fails open only when there is no active mission or the
  world/entity/operator is unavailable.
- `RdpUavTransformer` now instruments
  `PacketPlayerControlBase.handlePilotControls(...)`. The injected guard
  returns before MCheliCE writes flight-axis flags, while the packet's other
  handlers remain untouched.
- `RdpUavTransformerTest` now verifies that the packet class contains the
  authority call after transformation. The existing UAV-station frame test is
  preserved.

### Evidence and remaining limits

The target method and its field writes were verified from the exact local
`mcheli-ce-1.5.1.jar` bytecode. The transformer change is source-level until
the focused Gradle test passes. Live proof that held keys, direct pilots,
station operators, and handback behave correctly remains
`PENDING_DEVELOPER_IN_GAME_TEST`.

## Phase 2 — bounded aircraft observation

### Objective

Expose the observed physical motion channel and distinguish it from
position-delta velocity so guidance does not treat a placeholder as a control
measurement.

### Changes

- `AircraftState` now stores actual MCheliCE `motionX/Y/Z`, sample interval,
  and derived ground-track yaw. Zero movement is represented as a valid
  stationary state; ground-track yaw is unavailable (`NaN`) only when no
  horizontal movement was observed.
- `AircraftState.observeWithPhysicalMotion(...)` computes position-delta
  velocity using the real bounded sample interval. The existing
  `observe(...)` factory remains compatible for deterministic tests and
  explicitly represents physical motion as unavailable.
- `AircraftStateEstimator` now supplies the native motion values and elapsed
  sample interval for each aircraft. It retains one cached state per aircraft.
- A regression test covers physical motion, ground track, interval, and
  position-delta velocity as separate values.

### Audit and limits

The estimator is called from the WingMan adapter's observed-state path and
does not write movement. Route tangent, target intercept, obstacle knowledge,
and live target confidence still require their respective production data
providers; they must not be filled with synthetic zeros. Runtime values remain
`PENDING_DEVELOPER_IN_GAME_TEST`.

### Automated verification

- Command: `.\\gradlew.bat test --no-daemon`
- Result after Phase 1: `BUILD SUCCESSFUL` (all existing and transformer tests).
- Result after Phase 2: `BUILD SUCCESSFUL` (all existing and new aircraft-state
  tests).
- Command: `.\\gradlew.bat jar --no-daemon`
- Result: `BUILD SUCCESSFUL`; production artifact was rebuilt under
  `build/libs/`.
- `git diff --check`: passed.
- Forge runtime/client-server launch, native packet behavior, aircraft
  trajectory, and payload effects remain unverified in this environment.
