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

## Current rework status

- The production mission path now records progress in `MissionLifecycle.updateActive(...)`
  via `RDPMission.recordProgress(...)`, so route stalls and temporal timeout handling
  are driven by the real server tick cycle rather than idle bookkeeping.
- Missing target/route distances are treated as unknown (`NaN`) and normalized to
  "no measurable progress" instead of zero; this prevents false "zero distance"
  regressions from stalling or reacquiring the mission incorrectly.
- The fixed-wing/helicopter profile controller selection is now used from the active
  WingMan adapter path before the native control adapter applies server-side attitude
  and throttle commands.
- Runtime in-game flight proof remains `PENDING_DEVELOPER_IN_GAME_TEST`; source/test
  validation is a deterministic contract check, not a physical flight claim.

## Current runtime evidence and Phase H changes — 2026-09-26

### Evidence identity and limits

- Inspected `D:\ElyPrismLauncher\instances\RDP - (Reality Distortion Phenomen-v1)\minecraft\logs\latest.log`, 48,745,899 bytes, last written 2026-09-25 18:51:54. It is the newest supplied runtime evidence, but predates this source rework.
- The installed `mcheli-ce-1.5.1.jar` and `libs/mcheli-ce-1.5.1.jar` match at SHA-256 `740B4DF7C6AE8FF66C2E54ADD422AF4F4495EEEF45DDF5815AF7FDF7FF49015B`.
- At audit start, the installed addon and existing `build/libs/rdp-uav-addon-0.1.0.jar` matched at SHA-256 `976558789D27863436280C5A24B4A4AB9BC8B010BDFEA29DEF2E68FEA58B0D43`. This is the build that produced the log; it does not contain Phase H source changes.
- The log contains no `[RDP-UAV/... ERROR]` or RDP exception stack. It does contain unrelated startup errors from NuclearCraftAdditions metadata and VintageFix cache recovery. Neither is evidence for the UAV lock/FSM/fuse failures. TickDynamic tile-entity warnings also have no demonstrated connection to the UAV events.

### Findings confirmed by the supplied log and exact source

1. **Entity-lock input reaches the server, then is rejected by the configured range.** The log contains eight `TargetLock REQUEST` entries, three entity requests rejected as `OUT_OF_RANGE`, and no accepted entity lock. For `shahed136`, the active profile has `lockRange=256`; the rejected loaded and filter-accepted targets were 299.325 and 309.188 blocks away. The client sends the exact crosshair UUID and the server performs authorization, loaded-entity, filter, profile, and distance checks. This trace does not support a missing key packet as the cause of these attempts. The observed server decision is consistent with the current profile limit.
2. **The lock marker cannot show a 3D entity box.** `RdpUavTargetHud` previously drew a fixed-size 2D screen square around a projected telemetry point. It did not use the target entity's `AxisAlignedBB`, so it could not represent the target's actual dimensions or camera-space depth. The image references suggest corner brackets and a center cue; their numerical labels and implied sensor capabilities are not treated as project facts.
3. **The mission FSM repeatedly dispatches a route without starting a new progress interval.** The log records 19,560 `SEARCHING -> REPLAN` and 18,489 `REPLAN -> SEARCHING` transitions. One mission repeatedly transitions on consecutive ticks while WingMan accepts the same `FLY_TO` route. `recordProgress` can mark a no-progress route for replanning; after the route is accepted, the prior expired stall timestamp remained active. On the next tick the same route was immediately replanned. The same no-progress policy also maps navigation stalls in locked states to `LOST`, mixing aircraft route progress with target visibility.
4. **`TAKEOFF` is a label in this trace, not a demonstrated takeoff phase.** The point mission log goes `DISPATCHED -> TAKEOFF -> CRUISE -> SEARCHING` in the same server tick. That proves state transitions occurred; it does not prove a climbout controller, safe-altitude gate, or measured minimum-speed gate ran.
5. **The point-task key path did execute in this session.** At 18:48:35 the server logged creation of a point mission with explicit XYZ coordinates. This proves one T/point-task request in this session; it does not prove camera alignment or general key reliability.
6. **One mission's synchronous explosion was followed by a false aircraft-loss outcome.** At 18:37:34, aircraft `9997bc33-9bda-456c-a35d-dc0772064f0a` logged `PAYLOAD_DETONATED`; the mission then recorded `PAYLOAD_LAUNCH_ACCEPTED`, and one tick later transitioned to `AIRCRAFT_LOST` with `EFFECT_NOT_CONFIRMED`. Source inspection showed `detonateSelfDestructWeapon` called `MCH_Explosion.newExplosion`, destructed the aircraft, and returned success, while the lifecycle only recorded launch acceptance and never called `confirmKamikazeEffect` in production. This is a confirmed lifecycle race after a synchronous native effect, not proof that the target was hit or destroyed.
7. **A separate terminal run never logged a payload effect before aircraft loss.** Mission `cdeb744a-1656-4298-9c55-1b39a5b5d22f` was a point attack. It entered `KAMIKADZE_TARGET`, generated 125 repeated broad “nose block impact” candidates over its terminal interval, then became `AIRCRAFT_LOST` with `EFFECT_NOT_CONFIRMED`. The old detector expanded a swept box by `impactSweepPadding + triggerDistance` in every axis. Those log messages were broad proximity candidates, not proof of a nose collision. At an earlier sample the aircraft-to-point distance was still 9.75 blocks and its course error was about 99 degrees; the sample does not establish a valid terminal impact geometry.

### Changes made in this phase

- A successful route dispatch now starts a fresh no-progress window and resets per-route progress. The reset is applied to initial dispatch, replanning, and one-way route refresh; dispatch acceptance still does not count as physical movement. Mission transition diagnostics now include the transition reason.
- Entity target visualization now draws red lock brackets around the synchronized target entity's interpolated world-space bounding box, with an amber state while the server has not reported `TARGET_LOCKED`. Entity targets no longer fall back to the old screen-space square if the client cannot resolve the target entity. Point objectives retain their projected point marker. Server telemetry remains the authority; the client marker cannot assign a target.
- `OUT_OF_RANGE` feedback now includes measured distance and profile lock limit so the operator can distinguish a rejected request from a non-working key.
- The contact detector now traces a narrow forward nose segment to blocks/entities instead of treating a large expanded 3D box as contact. Repeated contact logs are rate-limited.
- At the native effect boundary, the addon now requires a non-null `MCH_Explosion.ExplosionResult`, logs whether MCH reports an entity hit, and explicitly does not infer target destruction. Once the native effect call succeeds, the mission records `KAMIKADZE_DETONATED` synchronously before the aircraft can disappear from the next world-tick scan.
- Added a synchronous `KamikazePreDestructionEvent` posted from a transformer hook at the verified McHeliCE 1.5.1 `MCH_EntityAircraft.handleDestruction(DamageSource, Entity, String, boolean)` method. The `EventPriority.HIGHEST` subscriber detonates only a committed, non-reusable self-destruct mission with sufficient speed and an actual nose-segment contact; the transformer skips native destruction only after the subscriber records a successful payload effect. The event is non-cancelable so unrelated subscribers cannot accidentally suppress McHeli destruction. If the event path fails or the weapon is unavailable, the hook fails open to McHeli's ordinary destruction behavior.
- Restored null-safe `AircraftControlLease.matches`; a null UUID must not throw while evaluating movement ownership.

### FSM and detonation architecture

Keep four related but independent records rather than making every fact another rapidly changing mission enum value:

1. **Mission phase:** `PLANNED -> DISPATCHED -> TAKEOFF/CLIMBOUT -> TRANSIT/SEARCH -> TARGET_ACQUIRED/LOCKED -> APPROACH -> TERMINAL_COMMIT -> EFFECT_PENDING -> EFFECT_CONFIRMED | OUTCOME_UNKNOWN -> RECOVERY/COMPLETE/FAILED`.
2. **Contact track:** candidate UUID, loaded/dimension status, last-observed tick, hitbox, sampled position and velocity, confidence, and lifecycle `SEARCHING -> CANDIDATE -> LOCKED -> TRACKING -> LOST_GRACE -> REACQUIRING`. A route stall must not itself mean target loss. Target loss is based on contact freshness and server resolution after a bounded grace interval.
3. **Route controller:** active route revision, route node, last measurable aircraft progress, last accepted replan tick, and a bounded retry deadline. An accepted replacement route resets its stall baseline once; the state stays in a stable replan/route phase until subsequent aircraft telemetry demonstrates progress. Do not toggle `SEARCHING` and `REPLAN` every tick.
4. **Payload outcome:** `UNARMED -> ARMED -> CONTACT_CANDIDATE -> DETONATION_COMMITTED -> NATIVE_EFFECT_CREATED -> TARGET_EFFECT_OBSERVED | TARGET_EFFECT_UNVERIFIED`. A synchronous `MCH_Explosion` result confirms that the native effect routine ran; `ExplosionResult.hitEntity` is useful evidence but does not identify the intended target, so the code must not claim target destruction from it. The pre-destruction callback is idempotent through mission state and the payload-launched latch.

The client lock display is a read-only projection of server-confirmed identity. It resolves the UUID in the client's current world and draws the entity's interpolated AABB as corner brackets; if the target entity is absent locally, show status/telemetry without inventing a box at a stale point. `T` point strike remains a coordinate objective and does not alias entity lock.

### Validation and remaining work

- `.\gradlew.bat test --no-daemon`: PASS, 239 tests, 0 failures, 0 errors, 0 skipped. Includes a regression for fresh route progress windows and a transformer test against the exact McHeliCE 1.5.1 JAR signature.
- `.\gradlew.bat build --no-daemon`: PASS. Reobfuscated artifact: `build/libs/rdp-uav-addon-0.1.0.jar`, 568,498 bytes, SHA-256 `0333FE0BABB83B6CA197E61056AEA51A406DF3CDFC430981A0DE13E1EE276687`.
- Source compilation and ASM transformation are validated. No source changes were copied to the active RDP instance and no current-build gameplay was performed.
- `PENDING_DEVELOPER_IN_GAME_TEST`: actual `EventPriority.HIGHEST` fuse invocation before native aircraft destruction; exact explosion/aircraft-removal ordering; marker alignment in player/UAV/remote camera modes; entity lock inside/outside 256 blocks; route progress recovery; and T key camera-point alignment.
- Still outside this phase: a real bounded `TAKEOFF`/climbout controller, independent target-loss hysteresis, full fixed-wing/helicopter flight-profile calibration, terrain/water-safe 3D navigation, missile seeker UI and projectile outcome confirmation, reusable stand-off attack/recovery, and comprehensive filter/swarm GUI validation. No source/test result here establishes realistic live flight or full-framework release readiness.

### Developer live-test handoff for this artifact

1. In a disposable copy of the RDP instance, back up the installed addon JAR and install the artifact above; keep the exact McHeliCE JAR whose hash is recorded in this report. Restart the client/server so the coremod transformer runs from startup.
2. At startup, confirm the new log line says the `MCH_EntityAircraft.handleDestruction` fuse hook was installed. If it reports a signature mismatch, stop this live scenario and save the log; ordinary aircraft destruction should remain enabled.
3. Test entity lock with a loaded target first within the Shahed profile's 256-block limit, then at approximately 300 blocks. Expect an accepted lock in-range and an explicit `OUT_OF_RANGE` message with distance and profile limit out-of-range. Check that the accepted entity receives the red 3D AABB brackets in player view and in the UAV/remote camera modes you use.
4. Submit a point task with `T` while aiming at a loaded block. Record the created server XYZ and confirm the point marker remains distinct from the entity AABB marker.
5. Fly a harmless straight route for at least 200 ticks in a loaded area. Save mission transition events and confirm an accepted replan opens one new progress window instead of repeating `SEARCHING -> REPLAN -> SEARCHING` each tick.
6. For the one-way payload case, use a disposable test area and a supported loaded self-destruct weapon. Save the complete interval from terminal commit through the pre-destruction fuse event, `PAYLOAD_DETONATED`, `PAYLOAD_EFFECT_CONFIRMED`, and final mission state. Record whether the intended target was actually hit separately from whether the explosion was created.
7. Keep the new `latest.log` and, if available, a short trajectory capture. Those artifacts are needed to close the runtime items marked `PENDING_DEVELOPER_IN_GAME_TEST`.

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

## Phase 12 - single movement authority and partial-observation progress (historical)

This phase is a historical snapshot and is superseded by Phase 15. Its
observation-only flight decision was later replaced after the exact MCheliCE
1.5.1 route/control bytecode was traced.

### Changes

- Autonomous WingMan ticks no longer call `NativeAircraftControlAdapter`; the
  WingMan route/native tick is the sole movement writer and RDP records the
  resulting aircraft state for telemetry.
- Route and mission progress now ignore a distance delta unless both samples
  are finite. A single missing observation therefore cannot become a false
  zero-distance improvement.
- Added regressions for partially missing target and waypoint distances.

### Audit and limits

This removes the source-level competing attitude/throttle writer, but does not
prove the McHeliCE tick order or physical convergence. Live validation remains
`PENDING_DEVELOPER_IN_GAME_TEST`.

## Phase 13 - intentional observation states and progress timeout scope

### Changes

- Progress policy now accepts an explicit `progressExpected` signal.
- Orbit, scanning, deep scanning, and holding states no longer convert
  intentional stationary behavior into `LOST` or `REPLAN`.
- The normal approach/route states retain the existing bounded stall policy;
  `WAITING_FOR_TARGET` remains timeout-eligible rather than becoming an
  accidental indefinite wait.
- Added policy regressions for both exempt and timeout-eligible states.

### Audit and limits

This is source- and unit-test-validated lifecycle behavior. It does not prove
that native WingMan enters or exits each observation state at the expected
runtime tick.

## Phase 14 - loaded-corridor gate for terminal approach

### Changes

- Terminal approach now samples the aircraft-to-target corridor at bounded
  intervals and requires each sample block position to be loaded before the
  ray trace and terminal safety decision run.
- An unavailable corridor returns `HOLD_AND_REEVALUATE`; it is not treated as
  clear terrain.
- `MinecraftWorldPerceptionAdapter` now supplies the chunk-loaded observation
  used by this gate without forcing chunk loads.
- The terminal corridor also uses the existing `TargetClassifier` to reject
  release when a hostile entity or hostile aircraft occupies the bounded
  corridor envelope, excluding the committed target itself.

### Audit and limits

This is a fail-closed chunk-availability gate plus a bounded hostile-contact
probe, not a full obstacle or return-route planner. `returnViable` still
requires a production return-route implementation; live validation remains
`PENDING_DEVELOPER_IN_GAME_TEST`.

## Phase 15 - leased RDP airframe controllers

### Changes

- Added an active fixed-wing/helicopter feedback path from the current WingMan
  `autoTargetX/Y/Z` setpoint through `AircraftStateEstimator`, an airframe
  controller, and `NativeAircraftControlAdapter` to bounded MCheliCE
  yaw/pitch/roll/throttle setters and `updateControl()`.
- Set RDP's server WorldTick START listener to lowest priority so the command
  is applied after WingMan callbacks and before aircraft physics.
- **Current-source correction:** This bullet records the Phase 15 implementation
  at that historical point, not the current listener priority. Current
  `RDPUavMod.ServerTickHandler` is registered at `EventPriority.HIGHEST`;
  priority alone does not prove ordering after every WingMan callback. See
  `current-architecture.md` and `ai-flight-control-ownership.md` for the
  current boundary and remaining runtime evidence requirement.
- Confined the RDP control lease to route-flight phases; `LANDING` and
  `PARKING` hand the final recovery leg back to native MCheliCE handlers because
  those paths contain direct movement writes outside the guarded FLY_TO helpers.
- Added transformer guards for the movement helpers found in the local
  MCheliCE 1.5.1 WingMan tick and autonomous-flight handler classes. A guard
  acquires the movement lease before the corresponding native write; operator
  flight-axis packet suppression remains active during mission ownership.
- Updated active moving-target `FLY_TO` nodes alongside effective route
  coordinates so WingMan does not restore a stale target setpoint next tick.
- Corrected route-distance telemetry to use effective `autoTarget` coordinates
  for non-coordinate nodes, and retained separate helicopter lateral roll
  authority instead of clamping FPV roll to zero.
- Terminal fixed-wing pitch now follows the planned route slope up to its
  bounded nose-down envelope; the adapter still rate-limits attitude changes.
- Removed the dead FPV `FlightCommand`/`FpvFlightController` path. FPV state
  selects mission policy, while the production helicopter controller emits
  and applies the real native setpoints.
- Reworked current architecture and developer guidance to distinguish route
  sequencing, RDP control ownership, and MCheliCE physics.

### Audit and limits

The local source/unit/ASM-transformer test suite passed (236 tests, zero failures) and the reobfuscated build completed successfully on 2026-09-25. The artifact SHA-256 is `31A743C937DCD916F671127E5D6F562730A59309C46A0E4AF2D862932D97E9D5`.

That evidence can establish compilation, math,
setter calls, event wiring, and bytecode guard insertion. It cannot establish
physical trajectory, profile calibration, takeoff/landing response, obstacle
avoidance, camera marker alignment, impact, or weapon effects. Current profile
values are heuristics. The available MCheliCE API uses attitude/throttle
setters for autonomous control; it does not expose a general player camera-axis
control object to this addon. The implementation is therefore a physics-based
closed-loop autopilot, not a proven copy of direct human input.

Remaining release work is enumerated in
`docs/ai-flight-control-ownership.md` and the developer handoff in
`docs/runtime-test-matrix.md`.

## Phase I — target freshness hysteresis and route-stall separation — 2026-09-26

### Source findings

- `ContactTracker` already had a 40-tick lost-contact grace, but
  `MissionLifecycle` immediately cleared/released target guidance when the
  entity was unavailable. The mission state therefore did not honor the
  tracker's grace interval.
- A route stall while in `TARGET_LOCKED`, `APPROACHING`, or `ATTACK_TARGET`
  was classified as `REACQUIRE`, and `RDPMission.recordProgress` translated
  that navigation timeout to mission state `LOST`. Route progress is not
  evidence that the entity disappeared.
- Target loss handling tried to defer several locked/attack states directly
  to `WAITING_FOR_TARGET`, a transition the FSM did not permit. A persisted
  target UUID could consequently be retained in data while the transition
  failed at runtime.

### Changes

- Persisted the last server observation tick for the locked target in mission
  schema 5. Older saved missions without this timestamp receive `-1`, so the
  addon does not invent a fresh contact after loading old world data.
- During the 40-tick contact grace, the mission keeps the same target UUID and
  does not change its mission state. FPV aircraft enter their existing hover
  behavior; other aircraft receive a short non-attacking forward hold leg while
  the mission keeps its control lease. The prior attack route is replaced so
  it cannot continue steering toward a stale point or launch against a stale
  target.
- After grace expires, the mission transitions once to `LOST`, then to
  `WAITING_FOR_TARGET` on the following update. The target UUID is retained;
  reacquisition can only resolve that same entity. FSM transitions now permit
  this outcome from route and target-dependent phases.
- Route-progress timeout now requests `REPLAN` for active navigation states
  and retains target identity. Intentional on-target/reporting phases no longer
  use route-motion timeout as their progress expectation. Actual target loss
  comes from contact freshness, independently of navigation stall.
- During an already committed kamikaze approach, the same UUID remains
  authoritative. The existing terminal timeout remains the bound. The last
  finite approach is preserved during grace; after grace the adapter may hold
  a forward leg, except an FPV controller already in terminal approach keeps
  its committed route because its hover state machine rejects a late mode
  switch.

### Validation and limits

- `.\gradlew.bat compileJava --no-daemon`: PASS after the FSM, persistence,
  and flight-route changes. `compileTestJava` also passed, confirming the
  updated existing policy contract compiles; no unit tests were executed.
- `reobfJar` completed successfully. Artifact:
  `build/libs/rdp-uav-addon-0.1.0.jar`, 569,676 bytes, SHA-256
  `C0BC13390669D8B5D8F0F517CACA00E826FE7FEEFD4C3F596670BFA990FE422A`.
  This JAR was not copied into the active RDP instance.
- No current-build JAR was installed into the active RDP instance. The 40-tick
  policy, loaded/unloaded chunk behavior, same-UUID reacquisition, route
  suspension, and FPV terminal behavior require live validation.
- The hold leg is a bounded route setpoint, not terrain clearance or a tested
  loiter procedure. It does not prove the aircraft can safely remain airborne
  in every MCheli definition.

## Phase J — stable target-loss hold routing — 2026-09-26

### Source finding

`MissionLifecycle` requests `holdForTargetReacquisition` on every server tick
while an entity is unavailable. The adapter rebuilt the same two-node
`FLY_TO -> LOITER` route on every request and reset its route index and timer.
That prevented WingMan from completing the bounded hold leg. A completed hold
could also leave an old `LOITER` suffix behind when the same target returned,
allowing the resumed mission to orbit before its next action.

### Change

- The adapter now records which aircraft currently owns a target-loss hold
  route and preserves its active `FLY_TO` or `LOITER` node across repeated
  lifecycle updates.
- Once WingMan completes or parks that leg, a later hold request may install a
  fresh bounded forward leg. Return-to-base and landing routes remain untouched.
- Reacquiring the target removes the hold marker and replaces the hold route
  with a fresh single `FLY_TO` target leg, discarding the hold-only `LOITER`
  suffix. A parked hold entry is returned to `ENROUTE` only during this
  explicit route resumption.
- The marker is cleared when another route, attack, terminal route, mission
  release, or new dispatch takes ownership.

### Validation and limits

- `.\gradlew.bat compileJava compileTestJava reobfJar --no-daemon`: PASS.
  Test sources compiled; no unit tests were executed.
- `git diff --check`: PASS (Git reports existing LF/CRLF normalization
  warnings).
- Reobfuscated artifact:
  `build/libs/rdp-uav-addon-0.1.0.jar`, 570,231 bytes, SHA-256
  `74C7A7BA3CA4CC9AFFCF883711B69D65DE576D0DE94206BFD4FD25BB24144EDB`.
- The artifact was not installed in the active RDP instance. Route-index
  stability, WingMan parking timing, same-UUID reacquisition, terminal resume,
  and observed flight path still require the developer's in-game run. The
  hold route remains a short route intent, not terrain-safe loiter.

## Phase K — bounded route recovery and measured progress — 2026-09-26

### Source finding

The lifecycle could repeatedly replan a mission without consuming its recovery
budget: the existence of a target cleared `recoveryAttempts` every active tick,
and accepting a replacement route cleared it again. In addition, route-stall
tracking compared the current waypoint distance with `NaN`, so it could not
recognize physical movement toward a waypoint. A one-tick distance delta also
missed slow but cumulative progress below the 0.5-block threshold.

### Change

- Recovery attempts now increase when a no-progress policy enters `REPLAN` and
  when an existing recovery path is deferred. The actual recovery reason is
  preserved as the FSM transition reason.
- Target presence and route acceptance no longer reset the budget. It resets
  only when consecutive server observations establish at least 0.5 blocks of
  accumulated distance reduction toward the active target or route waypoint.
- Distance baselines reset on accepted route dispatch. The first observation
  establishes a baseline; dispatch itself is not movement. Improvements smaller
  than the threshold accumulate against the best measured distance so slow
  motion can still count. When WingMan advances to a different route node, the
  waypoint-distance baseline is re-established for that node rather than
  comparing the new waypoint against the previous node's distance.
- Once more than five recovery attempts are consumed without measured movement,
  the mission enters `FAILED` with a `_RECOVERY_EXHAUSTED` reason. The lifecycle
  observes the terminal state and releases the aircraft mission lease.
- Recovery counter/state changes and meaningful progress-timestamp/route-index
  changes mark mission world data dirty so they survive a save before the next
  unrelated mission event.
- Mission NBT schema 6 persists route progress and the last meaningful-progress
  tick. Older saves without those fields load with a conservative baseline at
  mission creation; per-route distance samples are intentionally re-established
  from live observations after load.

### Validation and limits

- `git diff --check`: PASS (only repository LF/CRLF normalization warnings).
- `compileJava compileTestJava reobfJar --no-daemon`: PASS. Test sources compile;
  no unit tests were executed.
- Reobfuscated artifact:
  `build/libs/rdp-uav-addon-0.1.0.jar`, 570,977 bytes, SHA-256
  `895F4C37017BDEACC5EB83B792AA37CD37C1AAFB42671E6F8B97B184B018D416`.
- The artifact was not installed in the active RDP instance. The 0.5-block
  progress threshold, slow/curved profiles, target motion, actual WingMan
  redispatch, schema-6 migration, save/reload recovery accounting, and exhaustion
  cleanup need the developer's live run. Compilation is not evidence of aircraft
  motion.

## Phase L — native guided-projectile outcomes — 2026-09-26

### Source evidence from McHeliCE 1.5.1

- The local `libs/mcheli-ce-1.5.1.jar` implements server projectile collision in
  `MCH_EntityBaseBullet.onImpact(RayTraceResult,float)`. The terminal block/entity
  branch calls `func_70106_y()` after native impact handling. Several rejected,
  intercepted, or water-pass-through branches return before that removal. The
  observer therefore requires a server world, a block/entity ray result, and
  `isDead`; it does not treat a proximity update, timeout, or mere disappearance
  as impact.
- `MCH_Explosion.newExplosion(...)` and `newExplosionInWater(...)` return a
  nullable `ExplosionResult`; its `hitEntity` flag reports whether the explosion
  hit any entity. It does not identify the assigned objective. The inspected
  native return path provides no Forge event result suitable for this outcome
  handoff, so the addon uses narrow hooks at these McHeliCE return points.

### Changes in this phase

- `RdpUavTransformer` now installs a server impact observer in the verified
  `MCH_EntityBaseBullet.onImpact` method and observes native explosion results
  returned synchronously by McHeliCE.
- The callback associates only bullets with a currently authorized AI guidance
  assignment whose weapon matches the fired projectile. It records collision
  separately from native explosion processing and logs `explosionHitEntity`
  separately; it never claims that the assigned target was hit or destroyed.
- Impact evidence is retained by objective UUID when the projectile entity is
  pruned from the live-projectile map. Retention is bounded to 512 outcomes.
- Each observed projectile now retains its dimension and last observed block
  position. A missing projectile in an unloaded last-seen chunk is deferred,
  not classified as a miss; ticks for another dimension also no longer prune
  its in-memory tracking state. If that chunk loads and the projectile is still
  absent, the outcome becomes unknown.
- A mission with a confirmed collision but no native explosion result now moves
  to `OUTCOME_UNKNOWN` after 100 ticks instead of remaining in
  `IMPACT_OBSERVED` indefinitely.

### Validation and limits

- Exact MCheliCE API signatures and collision branch behavior were inspected
  from the local 1.5.1 JAR with `javap`.
- `gradlew compileJava --no-daemon`: PASS. `gradlew reobfJar --no-daemon`: PASS.
  Unit tests were not run.
- Current reobfuscated artifact:
  `build/libs/rdp-uav-addon-0.1.0.jar`, 578,342 bytes, SHA-256
  `39E56DA13529C2FE24E2052E6D30629699D42C72D7D003B26DD8CDD6D343EE68`.
- The artifact was not copied into the active RDP instance. Transformer
  installation messages, collision timing, target damage, explosion effects,
  and chunk-unload deferral still require a live developer run. A non-null
  native explosion result proves that McHeliCE processed its explosion
  operation; `hitEntity` proves only that some entity was hit by that
  explosion. Target-specific damage/destruction is not yet measured.

### Remaining outcome risks

- The chunk guard uses the projectile's last sampled block position. Its
  behavior across chunk save/reload must be confirmed in-game; projectile
  persistence itself is controlled by McHeliCE/Minecraft and is not guaranteed
  by this in-memory observer.
- Aircraft/world save and server restart persistence for projectile assignments
  is not implemented. The new retained observations are bounded in-memory
  runtime evidence only.
- Projectiles whose subclasses bypass the verified base `onImpact` path require
  their own signature audit; first live validation should use the guided
  AAM/AT/AS/TV families whose local 1.5.1 classes inherit this method.

### Carried helicopter-controller failure

The previously reported `Invalid helicopter controller inputs` exception was
not changed in this phase. In the current checkout that exception is thrown
only for a null `AircraftState`, non-finite target coordinates, or a negative
`currentTick`. The checked `helicopterSeparatesHorizontalAttitudeAndCollective`
source case supplies a state and finite `(30, 28, 20)` coordinates at tick 12,
so the pasted stack trace does not match the current source inputs. JUnit was
not run; diagnose this next against the exact failing checkout/build and runtime
arguments before changing the controller's fail-closed behavior.

## Phase 1 — responsibility alignment execution ledger — 2026-10-02

```text
CURRENT PHASE / APPROVAL:
  Phase 1 responsibility alignment and focused regression lock — approved and complete.
  Phase 2 adapter/runtime acceptance — not approved to execute; D-008 is unresolved.

COMPLETED:
  Rechecked current Git state; aligned existing responsibility/evidence docs;
  added one clean-file role/profile separation contract; ran focused and full
  tests/build; reviewed diff and whitespace.

IN PROGRESS:
  None. No native handoff or runtime behavior was changed.

PENDING:
  Phase 2 isolated client/server acceptance, per-airframe control validation,
  live packet/HUD/target/payload/persistence checks.

BLOCKED:
  The exact supported Minecraft/Forge/Java/McHeliCE/RDP Core tuple is unknown.
  Repository configuration/docs conflict on Forge 2768/2859/2864. User was
  unavailable to select the deployment tuple. Do not select a replacement
  based only on source, docs, or JAR signatures.

FILES CHANGED:
  docs/UAV_AI_FRAMEWORK_REWORK_REPORT.md
  docs/ai-flight-control-ownership.md
  docs/api-verification-notes.md
  docs/current-architecture.md
  docs/implementation-source-matrix.md
  docs/mission-framework-map.md
  docs/mission-telemetry.md
  docs/runtime-test-matrix.md
  src/test/java/net/vas/rdp/uav/SwarmAssignmentManagerTest.java
  Existing dirty source/test paths and docs/GITHUB_COPILOT_WORKFLOW.md remain
  user-owned and untouched.

VERIFICATION:
  Focused: PASS — .\gradlew.bat test --tests net.vas.rdp.uav.SwarmAssignmentManagerTest
    --tests net.vas.rdp.uav.AirframeControllerTest --tests
    net.vas.rdp.uav.VehicleCapabilityTest --tests
    net.vas.rdp.uav.NetworkClientServerContractTest --tests
    net.vas.rdp.uav.GuidedMissileMissionLifecycleTest --tests
    net.vas.rdp.uav.mission.MissionMovementAuthorityTest --tests
    net.vas.rdp.uav.core.RdpUavTransformerTest --no-daemon
    Result: BUILD SUCCESSFUL; focused source-contract tests passed. Does not prove
    Forge tick order, physical aircraft response, network delivery, rendering,
    native release, damage, or persistence in a live game.
  Full tests/build: PASS — .\gradlew.bat test build --no-daemon
    Result: BUILD SUCCESSFUL; 282 tests, 0 failures, 0 errors, 0 skipped,
    52 suites (Gradle XML reports). Build/package ran; no artifact was deployed.
    Existing Gradle deprecation and ForgeGradle mapping warnings remain.
  Diff/scope: PASS — git diff --check produced no whitespace errors; only an
    LF/CRLF normalization warning. git status showed the nine files above plus
    pre-existing user changes and the known untracked runbook; no unexpected
    tracked or untracked build output appeared.
  Forge/McHeli runtime: NOT RUN for Phase 1. Existing saved 2026-09-27 client
    run fails during McHeliCE model initialization on Forge 2768 before world
    entry; no successful current mission run is available.
  Long-running/regression: BLOCKED — no live runtime tuple/approval; no extended
    flight, save/reload, or multi-aircraft run was performed.

APPROVED DECISIONS:
  D-001 through D-007 are source/project-record boundaries approved for
  documentation/regression alignment only; no gameplay behavior change was
  authorized. D-008 (runtime tuple) remains unresolved. D-009 (protect local
  modifications) remains in force.

NEXT EXACT ACTION:
  Obtain the exact supported runtime tuple and explicit approval for an isolated
  disposable client/server run. Then execute Phase 2 runtime acceptance; stop
  before dependency changes, production deployment, or edits to any protected
  local path.
```

### Requirements coverage and remaining backlog

Status labels below distinguish source implementation from tests and live
operation. `IMPLEMENTED` means a source responsibility/path exists;
`TESTED` means a relevant deterministic check is present or was run;
`RUNTIME VERIFIED` requires observed in-game behavior. A source class or
successful build alone never establishes runtime verification.

| Requirement area | Current status and evidence | Proposed owner / dependency / phase |
|---|---|---|
| Mission lifecycle, task/decision handling | **IMPLEMENTED; TESTED (source contracts).** `MissionDefinition`, `AiDecisionEngine`/`AiTaskingService`, `MissionManager`, `RDPMission`, `MissionLifecycle`; lifecycle and transition tests. **RUNTIME VERIFIED: NO.** | Intent stays in request/definition; manager owns assignment; mission aggregate owns state; lifecycle executes. Phase 2 requires approved runtime; mission persistence work later must avoid dirty codec/state files absent approval. |
| Patrol, search, route planning, altitude, recovery | **PARTIAL; TESTED (deterministic route/controller tests).** Patrol/route planners, `TerrainRoutePlanner`, `WingManFlightAdapter`, recovery states and profiles exist. Global obstacle avoidance, physical altitude hold, return/landing, and prolonged recovery are not demonstrated. | Lifecycle chooses work; route planners create bounded paths; WingMan adapter owns native route contract; per-airframe controller writes controls. Phase 2 after startup; later recovery slice after movement acceptance. |
| Mission persistence | **IMPLEMENTED (source); PARTIAL (test); RUNTIME BLOCKED.** `RDPMissionWorldData`/`MissionNbtCodec`; current dirty worktree schema is 7. No current save/restart run. | Mission aggregate/codec owns mission record; native projectile correlation is not durable. Runtime save/reload depends on D-008 and clean world-load proof. |
| Vehicle capabilities/profiles | **IMPLEMENTED; TESTED (profile/capability unit checks).** `UavVehicleProfile`, `UavVehicleProfiles`, `VehicleCapability`. Envelopes remain provisional. | Profile describes capability, not current operational assignment. Per-definition tuning requires separate live cases. |
| Fixed-wing | **IMPLEMENTED; TESTED (controller math); RUNTIME NOT VERIFIED.** `FixedWingFlightController`, `AirframeController`, profile speed/turn/climb envelopes. | Phase 2 straight route, turn, climb/descent, stall/recovery measurement on an approved fixed-wing definition; do not infer from helicopter results. |
| Rotary-wing / FPV | **IMPLEMENTED; TESTED (controller and state contracts); RUNTIME NOT VERIFIED.** `HelicopterFlightController`, actual `MCH_EntityHeli` selection, `FpvFlightStateMachine`. | Separate Phase 2 hover, drift, altitude, standoff, terminal tests; controller profile must not be inferred from fixed-wing. |
| VTOL and long-endurance constraints | **UNKNOWN/PARTIAL.** No separately verified VTOL execution profile or live transition law was established. Long-range reusable profile exists with expanded configured lock/detection ranges; actual range/endurance is not proven. | Require an airframe/API-specific capability and user-approved doctrine before implementation; after basic flight and runtime target are established. |
| Sensors, observations, detection/classification, lock, continuity, fusion/world model | **PARTIAL; TESTED (contact/fusion source tests); RUNTIME NOT VERIFIED.** `MinecraftWorldPerceptionAdapter` provides bounded world observations; `TargetResolver`/`TargetClassifier`, `ContactTracker`, `SensorFusion`. No complete onboard sensor suite/world model is evidenced. | Server observation/authority layer owns contact inputs; mission UUID owns lock; sensor suite expansion is a later approved architecture slice. |
| Client override, camera, lock overlay/HUD, server/client ownership | **PARTIAL; TESTED (packet/UI source contracts); RUNTIME NOT VERIFIED.** Client selection request and synchronized HUD exist; server validates UUID; transformer targets operator axes. Camera alignment and packet path require live tests. | Server owns mission/lock; client supplies selection and displays telemetry only. Phase 2 client/server acceptance after D-008. |
| Navigation, guidance, maneuvers, movement authority | **IMPLEMENTED; TESTED (deterministic controllers and transformer contract); RUNTIME NOT VERIFIED.** Mission/lifecycle -> WingMan route/setpoint -> controller -> movement lease/native adapter -> McHeli physics. Tick ordering beyond registered priorities is not established. | Keep legacy calls in `WingManFlightAdapter`/transformer and writes in `NativeAircraftControlAdapter`; Phase 2 runtime trace required before any handoff change. |
| McHeli/WingMan adapters and guided missile/TV support | **PARTIAL.** Local McHeliCE 1.5.1 signatures were inspected; adapters and capability registry exist. TV is configured unsupported by default; method signatures do not prove behavior. | Adapter owns API translation; native engine owns projectile physics. Test one supported weapon live; TV remains unsupported unless verified and approved. |
| Swarm membership, roles, formation, allocation, cooperative search, communication, persistence, degraded mode | **PARTIAL; TESTED (source contracts); RUNTIME NOT VERIFIED.** `SwarmManager`, `SwarmRecord`, role/assignment managers, formation/search planners and world data exist; search is bounded. No proven physical separation, live communication, restart, or scale behavior. | Swarm owns bounded assignment/projections; member `RDPMission` retains mission/lock/execution. Phase after single aircraft and persistence baseline; dirty swarm files protected. |
| Payload control, SelfDestruct compatibility, failure handling | **PARTIAL; TESTED (outcome/state contracts); RUNTIME NOT VERIFIED.** Mission and guided adapters separate launch/impact/effect/unknown. Native hooks are transformer-targeted and local signatures exist. No live release/damage/effect evidence. | Mission authorizes and records outcomes; `GuidedWeaponAdapter` delegates native release; never mark success on failed handoff. Phase 2 only in disposable runtime after D-008 and scenario approval. |
| Mixin/coremod | **PARTIAL.** Addon uses `RdpUavTransformer`; current source names target method owners; local JAR contains those methods. Hook installation/compatibility on selected supported runtime is not established. MixinBooter dependency necessity remains unknown. | Keep ASM/coremod hooks isolated; verify exact injection points in selected JAR and runtime before any strategy/dependency change. |
| Telemetry, deterministic tests, build checks | **IMPLEMENTED; TESTED.** `RdpUavDebug`, telemetry and source tests; Phase 1 focused run and full suite/build passed as recorded above. Historical docs contain other dated results. | Continue layered checks; current run results apply to the current dirty worktree, not live Minecraft behavior. |
| Live runtime and long-running checks | **BLOCKED / NOT RUNTIME VERIFIED.** Saved Forge 2768 client fails before world entry; the 2026-10-02 isolated 2768 server probe stopped at the EULA gate before world startup; no successful current mission runtime or extended save/reload/multi-aircraft scenario. | First resolve D-008 and the isolated server EULA gate; then perform per-airframe, lock/network/HUD, payload, persistence, and endurance tests. |

## Phase 2 — isolated runtime gate ledger — 2026-10-02

```text
CURRENT PHASE / APPROVAL:
  Phase 2 adapter/runtime acceptance — user requested continuation.
  D-008 (supported runtime tuple) remains unresolved. The isolated probe below
  used the build.gradle-configured Forge 14.23.5.2768 only; it did not select
  that version as the supported deployment/runtime target.

COMPLETED:
  Read the new root logs/latest.log; it contains three test-worker hook
  messages, not a game launch. Created a project copy under session artifacts
  without copying or using the repository run/ directory. Ran the dedicated
  server startup task there. Forge 2768, McHeliCE core, MixinBooter, and the
  addon coremod initialized through launch-wrapper setup; Minecraft then
  stopped at its EULA prompt.

IN PROGRESS:
  None. No mission, world, client, or aircraft behavior was tested.

PENDING:
  Resolve D-008 against the intended runtime; establish the exact Java,
  Forge, McHeliCE, WingMan, and RDP Core tuple; obtain the required EULA
  authorization for a disposable server; then repeat startup and continue
  with a client/server smoke test before any gameplay acceptance.

BLOCKED:
  The isolated copy's run/eula.txt has eula=false. The task output explicitly
  says Minecraft requires EULA agreement and exits before world startup.
  No EULA agreement was supplied, so it was not changed. README, developer
  guide, and build.gradle still disagree on Forge 2768/2859/2864. Do not treat
  Gradle's BUILD SUCCESSFUL status as a Minecraft server-start pass.

RECOMMENDATION (not an approved version change):
  Treat Forge 14.23.5.2864 as the leading source-validation candidate because
  DEVELOPER_GUIDE.md identifies it as the build validation target and README.md
  calls it the exact installed runtime. Keep 2768 identified as the current
  build.gradle configuration, not as proof of supported deployment. Treat
  2859 as the separately documented deployment target. Reconcile configuration
  and verify the actual installed tuple before changing the project build.

FILES CHANGED:
  Repository: docs/runtime-test-matrix.md and
  docs/UAV_AI_FRAMEWORK_REWORK_REPORT.md (evidence/status only).
  The isolated source/dependency copy and generated logs/config are under the
  session artifacts directory, outside the repository. Repository run/,
  saves, worlds, and protected source/test edits were not used or changed.

VERIFICATION:
  Latest root log: PASS (read-only inspection) — logs/latest.log has only
  three test-worker hook messages; it is not runtime evidence.
  Isolated server launch: BLOCKED — command .\gradlew.bat runServer --no-daemon
  in the session-artifact copy returned Gradle BUILD SUCCESSFUL, but
  run\logs\latest.log ends with the Minecraft EULA refusal and the generated
  run\eula.txt says eula=false. No world directory or server-ready state was
  produced. This used Java 1.8.0_502 and configured Forge 14.23.5.2768 with
  local McHeliCE 1.5.1 / RDP Core 1.0.0; it is exploratory and not a supported
  runtime selection.
  Client/live mission checks: NOT RUN — no selected supported tuple; no GUI
  client was launched; EULA/server-world gate remains unresolved.
  Original user run data: PASS (preserved) — the repository run/ directory was
  not used. No runtime artifact was deployed.

APPROVED DECISIONS:
  D-001 through D-007 remain the Phase 1 documentation/regression boundaries.
  D-008 (runtime tuple) remains unresolved. D-009 (protect local modifications)
  remains in force. The request to continue Phase 2 authorized this isolated
  startup probe, not a new dependency/version, EULA agreement, deployment, or
  changes to protected files.

NEXT EXACT ACTION:
  Resolve the exact supported runtime tuple and authorize the disposable
  server's EULA gate. Then rerun server startup in the isolated copy and
  proceed to the client/server acceptance matrix only if the world reaches
  ready state; stop at any dependency, API, or persistence failure.
```

## Master architecture continuation checkpoint — 2026-10-02

```text
CURRENT PHASE / APPROVAL:
  Master prompt Phase 0 architecture discovery is complete. Continue the
  approved sequential architecture roadmap from Phase 1. D-010 through D-014
  are recorded in docs/DECISION_LOG.md. D-008 (the supported runtime tuple)
  remains unresolved; D-009 (preserve pre-existing local changes) remains in
  force.

COMPLETED:
  D-010 isolated toolchain investigation: ForgeGradle 2.3 cannot extract the
  exact Forge 2864 userdev3 artifact; ForgeGradle 3.0.197 can compile and
  package the copied source against Forge 2864. Full Gradle tests retain seven
  network-registry bootstrap failures. Root build configuration was not
  changed.
  D-011 retains the deterministic MissionLifecycle without a behavior-tree
  framework. D-012 evolves the existing compositional vehicle profile. D-013
  keeps observation and target tracks server-authoritative behind the
  profile-bounded perception adapter. D-014 assigns swarm configuration and
  monitoring to a dedicated mission-GUI page while the server remains the
  authority for validation, coordination, execution, and telemetry.
  Source inspection found that MissionGuiTab.SWARM currently has no dedicated
  content layout, swarm inputs are mixed into the general UAV view, and
  launchMissionDefinition refuses swarm definitions on the client. Existing
  server swarm records and periodic reconciliation are present.

IN PROGRESS:
  Implement the D-014 operator page against the existing server-side swarm
  aggregate; connect authorized membership/configuration to autonomous member
  missions and return synchronized owner-scoped telemetry.

PENDING:
  Sequentially audit Phase 1–6 contracts against existing profile, perception,
  flight, guidance, deterministic decision, patrol/search implementations;
  extend only verified gaps. Then finish swarm mission execution, fixed-wing
  diversity, payload compatibility, and resilience work. Preserve the approved
  server-authority boundaries.

BLOCKED:
  Full Forge 2864 test execution is not clean: seven network contract tests
  encounter Forge NetworkRegistry initialization NPEs under the isolated FG3
  test worker. Live Forge client/server and all gameplay acceptance remain
  unverified. Do not alter the main Forge/plugin selection until D-008 and the
  test bootstrap outcome are resolved.

FILES CHANGED IN THE D-010 CHECKPOINT:
  BUILD_VALIDATION_REPORT.md
  docs/DECISION_LOG.md
  docs/runtime-test-matrix.md
  The isolated Gradle probes are outside the repository. All earlier dirty
  source, test, and documentation changes remain preserved.

VERIFICATION:
  ForgeGradle 2.3 / Forge 2864: FAIL at extractUserdev because the requested
  legacy userdev artifact is not present.
  ForgeGradle 3.0.197 / Forge 2864: compileJava, compileTestJava, jar,
  reobfJar, and build -x test passed. The full test run passed 277/284 after
  adding the compile-only ModularUI jar to test runtime; 7 network contract
  tests failed during NetworkRegistry channel insertion.
  Main checkout baseline previously recorded: 284 tests and build passed on
  configured Forge 2768. No main test/build is claimed after this probe.
  Forge runtime: NOT RUN. No client/server or world was started, no EULA was
  accepted, and no artifact was deployed.

APPROVED DECISIONS:
  D-010: exact Forge 2864 build-path investigation in an isolated copy.
  D-011: deterministic MissionLifecycle and explicit policies; no behavior tree.
  D-012: extend UavVehicleProfile compositionally.
  D-013: server-side profile-bounded observation and authoritative tracks.
  D-014: dedicated swarm mission GUI page; server-authoritative coordination
  and self-autonomous swarm mission execution.

NEXT EXACT ACTION:
  Implement the actual SWARM tab layout and server-side launch/configuration
  path. Preserve failure feedback, owner authorization, profile/availability
  checks, bounded configuration, and per-member MissionLifecycle ownership.
```

### D-014 implementation checkpoint — 2026-10-03

```text
STATUS:
  The D-014 source implementation is complete and build-verified. Runtime
  behavior remains unverified.

IMPLEMENTED:
  A dedicated SWARM mission-control page now owns roster selection, member
  enrollment/removal, bounded configuration, leader selection, pause/resume,
  disband, shared-target selection, and mission launch. Server snapshots are
  owner-scoped; commands and mission intents are validated on the server.
  Mission fan-out creates independent MissionManager-owned missions and
  preserves the swarm association through persistence and linked retasking.
  Supported fan-out types are reconnaissance, observation, patrol, strike,
  and destroying. Patrol accepts the existing bounded waypoint syntax
  (x z [loiter seconds]; ...), and each selected aircraft's route is checked
  against its profile before launch. Strike missions retain the existing
  per-mission authorization requirement.
  Formation and altitude slot offsets now reach the server flight setpoints;
  attack terminal guidance does not receive a formation offset. Search
  patterns select target-loss search routes. Duplicate-target policy applies
  to both shared and independently acquired targets. Leadership failover and
  designated-member selection now affect coordination.

VERIFIED:
  Main checkout: Forge 1.12.2-14.23.5.2768, `gradlew.bat test --no-daemon
  --console=plain`: PASS, 293 tests, zero failures/errors/skips.
  Main checkout: `gradlew.bat build --no-daemon --console=plain`: PASS,
  including `jar`, `reobfJar`, and source jar tasks.
  Isolated Forge 2864 probe using ForgeGradle 3.0.197 and the updated main
  `src` tree: `compileJava compileTestJava jar reobfJar --rerun-tasks` PASS.
  This does not change the root ForgeGradle 2.3 / Forge 2768 selection and
  does not resolve the seven known Forge NetworkRegistry test-worker errors
  recorded by the earlier full Forge 2864 test run.
  `git diff --check`: PASS. Git reports line-ending conversion warnings on
  the existing Windows checkout; no whitespace errors were reported.

ARTIFACT:
  `build/libs/rdp-uav-addon-0.1.0.jar`, 728,044 bytes,
  SHA-256 8256FE89493CE43B28CB65C9B42031200804C5CA1A693413ECE002B88442F4AF.

RUNTIME VERIFIED:
  None. No client or mission server was launched. The earlier isolated server
  stopped at the Minecraft EULA gate (eula=false); no EULA was accepted, no
  world was created, and no artifact was deployed.

UNKNOWN / BLOCKED:
  In-game layout, packet behavior, control response to formation setpoints,
  terrain-safe patrol operation, mission fan-out, target sharing, persistence
  across restart, and strike authorization have not been exercised in a live
  Forge/McHeli instance. The final supported runtime tuple D-008 remains
  unresolved.

NEXT EXACT ACTION:
  Resolve D-015 before changing flight-control selection. Keep live swarm
  acceptance blocked until the runtime tuple and EULA gate are resolved.
```

### Phase 8 fixed-wing/VTOL audit — 2026-10-03

```text
STATUS:
  Read-only API/profile audit complete. D-015 is pending before implementation.

VERIFIED:
  The bundled McHeli CE 1.5.1 plane entity extends MCH_EntityAircraft and has
  native VTOL configuration/mode APIs. Its generic attitude/throttle methods
  are already the addon adapter boundary. Existing addon selection routes
  native helicopters and HELICOPTER_ONE_WAY_FPV profiles to the helicopter
  controller; all other supported execution profiles route to the fixed-wing
  controller. The selector does not inspect plane VTOL enable/current mode.

UNKNOWN:
  Which exact McHeli plane definitions the gameplay instance loads, whether
  their VTOL mode is enabled/default, how the selected UAVs behave through
  transition, and whether controller handoff can meet safe trajectories.

DECISION GATE:
  D-015 offers either a bounded fixed-wing first pass that rejects VTOL-enabled
  aircraft, or explicit VTOL-aware mode/controller handoff. No flight behavior
  was changed during this audit.

NEXT EXACT ACTION:
  User selects D-015 option A or B. Then implement only that approved scope,
  add profile/controller contract coverage, and update the live trajectory
  matrix. D-008 and live runtime validation remain unresolved independently.
```
