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

## Phase 3 — fixed-wing route-following contract

### Objective

Convert the already-defined FPV route intent into a bounded native guidance
bias without inventing a second movement writer. The fixed-wing/FPV guidance
must remain deterministic, profile-aware, and consistent with the mission's
server-authoritative control boundary.

### Changes

- `NativeAircraftControlAdapter.applyFpvGuidance(...)` now folds the FPV
  command's forward, lateral, and vertical intent into the native target
  vector in a bounded, profile-safe way before the server controller computes
  the final yaw/pitch/roll/throttle values.
- Forward bias is clamped to a small positive/negative envelope, lateral bias
  is constrained to a narrow side slip correction, and vertical bias is limited
  to the existing native terminal-approach safety window.
- Regression coverage was added to verify the path-bias remains bounded and
  deterministic when a mission is already using the FPV approach mode.

### Audit and limits

This is a deterministic controller contract, not a claim of live aircraft
behavior. It is intentionally bounded and separated from position or motion
writing until the developer verifies the real in-game response of the airframe.
`PENDING_DEVELOPER_IN_GAME_TEST` remains in effect.

### Automated verification

- Command: `.\\gradlew.bat test --no-daemon`
- Result after Phase 1: `BUILD SUCCESSFUL` (all existing and transformer tests).
- Result after Phase 2: `BUILD SUCCESSFUL` (all existing and new aircraft-state
  tests).
- Result after Phase 3: `BUILD SUCCESSFUL` (all fixed-wing and FPV contract
  tests).
- Command: `.\\gradlew.bat jar --no-daemon`
- Result: `BUILD SUCCESSFUL`; production artifact was rebuilt under
  `build/libs/`.
- `git diff --check`: passed.
- Forge runtime/client-server launch, native packet behavior, aircraft
  trajectory, and payload effects remain unverified in this environment.

## Phase 4 — helicopter / FPV controller contract

### Objective

Convert the remaining explicit FPV/heli mission intent into a single bounded
controller decision that stays outside the raw entity-writer path. This is the
next architectural gate before obstacle planning and terminal route recovery.

### Changes

- Added `FpvFlightController`, a deterministic, bounded controller that emits
  a single `FlightCommand` from aircraft position and target position while
  enforcing hover, approach, and terminal modes.
- Wired the active FPV route updates in `WingManFlightAdapter` to use the
  controller instead of hard-coded approach commands, keeping the route logic
  and command generation consistent.
- Added regression coverage for hover, bounded approach, and terminal
  approach output.

### Audit and limits

The controller is pure, deterministic, and fails closed on invalid inputs. It
still does not claim in-game flight proof, and runtime tuning remains a
developer-confirmed task once the mod is exercised in live Forge/McHeliCE.
`PENDING_DEVELOPER_IN_GAME_TEST` remains in effect.

## Phase 5 — obstacle planning and safe replanning

### Objective

Add a bounded detour seam for blocked corridors before the mission enters a
terminal commitment or aggressive intercept. This stays at the pure-planning
layer: no native aircraft motion or position writes are introduced.

### Changes

- Added `RouteDetourPlanner`, a deterministic offset planner that computes a
  bounded side-step detour for blocked approach corridors.
- The planner remains fail-closed on invalid coordinates and returns a direct
  route when the corridor is clear.
- Regression tests cover both the direct-route and blocked-route cases.

### Audit and limits

This is a pure route-planning contract and does not claim actual obstacle
avoidance behavior in-game. Live server validation remains
`PENDING_DEVELOPER_IN_GAME_TEST`.

## Phase 6 — terminal release gate

### Objective

Establish a deterministic release decision for the final terminal commitment so
mission logic can distinguish a valid direct strike from a hold/detour state.
This is the final planning gate before weapon authorization and payload release,
without writing native aircraft motion or bypassing the native flight boundary.

### Changes

- Added `TerminalCommitPolicy`, a pure decision gate that combines the
  corridor-safety assessment with the bounded detour planner.
- The policy returns one of `RELEASE`, `DETOUR_REQUIRED`, `HOLD`, or `ABORT`
  to make the mission lifecycle explicit before any terminal weapon release is
  allowed.
- Regression tests cover both the clear-approach case and the blocked-corridor
  detour requirement.

### Audit and limits

The release gate is deliberately fail-closed and only evaluates server-side
static safety conditions. It does not claim any in-game projectile or aircraft
behavior beyond the verified native control boundary, and live runtime validation
remains `PENDING_DEVELOPER_IN_GAME_TEST`.

## Phase 7 вЂ” movement-authority cleanup and stale-lease handling

### Objective

Make the single-writer movement lease explicit and deterministic for the
mission tick window, including stale-lease cleanup and fail-closed renewal of the
same active mission. This prevents silent control re-entrancy while keeping the
runtime path server-side and bounded.

### Changes

- `MissionMovementAuthority.claimControl(...)` now acknowledges both a fresh
  lease and a renewal for the same mission without letting a stale competing
  writer silently steal authority.
- Added `releaseExpiredControls(long tick)` to clear aged leases explicitly and
  keep the single-writer set bounded.
- `suppressOperatorFlightInput(...)` now fails closed when the mission manager is
  unavailable so the packet-boundary check never throws or mutates a stale
  aircraft state while the mod is reloading or recovering.
- Added regression coverage for stale lease expiry and cleanup in
  `MissionMovementAuthorityTest`.

### Audit and limits

This is a server-authoritative safety improvement, not live in-game proof of
flight or packet behavior. The native MCheliCE runtime, real packet ordering, and
live aircraft trajectory remain `PENDING_DEVELOPER_IN_GAME_TEST` until the
developer exercises the actual mod in Forge and provides logs/video.

### Automated verification

- Command: `.\gradlew.bat test --no-daemon --tests net.vas.rdp.uav.mission.MissionMovementAuthorityTest --tests net.vas.rdp.uav.core.RdpUavTransformerTest`
- Result: `BUILD SUCCESSFUL`.
- Command: `.\gradlew.bat test --no-daemon --tests net.vas.rdp.uav.AircraftStateTest`
- Result: `BUILD SUCCESSFUL`.
- Command: `.\gradlew.bat test --no-daemon`
- Result earlier in the workspace: `BUILD SUCCESSFUL`.
- `git diff --check`: passed.

## Phase 8 вЂ” telemetry provenance and motion-quality metadata

### Objective

Make every aircraft observation explicitly traceable to the source it came
from, the unit system it uses, and the confidence level assigned to that sample.
This keeps guidance and diagnostics honest when a mission is using native motion,
position-delta velocity, or partial-only telemetry.

### Changes

- `AircraftState` now carries explicit `source`, `units`, `confidence`, and
  `validityReason` metadata alongside the measured position, velocity, and
  motion fields.
- `AircraftStateEstimator` tags observations as `PHYSICAL_MOTION` when native
  motion values are present and `POSITION_DELTA` otherwise, while preserving the
  server-authoritative lease and route metadata.
- Added regression checks covering both the physical-motion path and explicit
  partial-sample metadata reporting.

### Audit and limits

This is deterministic telemetry bookkeeping, not in-game flight proof. It makes
observation quality visible to server-side diagnostics and guidance code without
claiming live aircraft physics validation. Runtime behavior remains
`PENDING_DEVELOPER_IN_GAME_TEST` until the developer provides live server logs and
video from the actual Forge/McHeliCE instance.

## Phase 9 вЂ” route progress and stalled-target detection

### Objective

Convert the last-progress metadata already stored in `AircraftState` into a
server-side deterministic route-progress monitor that marks real route gain and
bounded stall windows without inventing a second motion authority.

### Changes

- Added `RouteProgressTracker`, which compares the previous and current route
  progress, target distance, and waypoint distance to decide whether the aircraft
  has made meaningful progress or crossed a stall timeout.
- `AircraftStateEstimator` now applies the tracker when updating the sample's
  `lastMeaningfulProgressTick` and `routeProgress`; it keeps the last known route
  progress stable when the aircraft is idle or oscillating, while advancing it
  only on real gain.
- Added `RouteProgressTrackerTest` to cover both a valid route advance and a
  bounded no-progress timeout.

### Audit and limits

This continues the offline-safe deterministic rework: route progress is now
explicitly measured and bounded, but no claim is made that live WingMan flight or
terminal approach convergence has been proven from source-only validation. The
live runtime remains `PENDING_DEVELOPER_IN_GAME_TEST` until the developer runs the
actual Forge/McHeliCE mission matrix and captures logs/video.

## Phase 10 вЂ” mission-level progress contract

### Objective

Make the mission state itself carry explicit route-progress bookkeeping so the
server can distinguish a moving target from a stalled engagement without
silently replacing the lock or treating static state as progress.

### Changes

- `RDPMission` now tracks `routeProgress` and `lastMeaningfulProgressTick` as
  authority-level metadata, rather than leaving progress only in transient
  telemetry snapshots.
- Added `recordProgress(...)` and `isProgressStalled(...)` to the mission model,
  using the same deterministic progress window as the flight-layer tracker.
- Added a regression in `AutonomousMissionTest` confirming that visible progress
  suppresses the stall flag and that static route geometry eventually triggers a
  stalled condition.

### Audit and limits

This is still source-validated safety logic, not live Minecraft flight proof.
The mission model now exposes a bounded, testable progress contract; the actual
Forge live engine and target file remain `PENDING_DEVELOPER_IN_GAME_TEST`.

## Phase 11 вЂ” no-progress mission policy

### Objective

Translate the mission's deterministic progress window into an explicit server
policy that chooses `HOLD`, `REPLAN`, or `REACQUIRE` when a target is stalled or
lost without needing a live flight proof.

### Changes

- Added `MissionProgressPolicy`, a pure deterministic policy that evaluates the
  mission state, target presence, and progress age against a bounded stall window.
- `RDPMission.recordProgress(...)` now consults the policy and transitions a
  stalled tracked mission to `LOST` or `REPLAN` instead of silently continuing a
  static route.
- The mission-level regression now verifies the `TARGET_LOCKED -> LOST` path and
  keeps the policy fail-closed when the progress window expires.

### Audit and limits

This remains an offline-safe deterministic contract: it enforces a bounded
progress decision on server state, but runtime flight and target behavior in the
actual Forge/McHeliCE session remain `PENDING_DEVELOPER_IN_GAME_TEST`.
