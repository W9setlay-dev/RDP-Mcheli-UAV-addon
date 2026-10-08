# AI flight control ownership and validation

## Implemented ownership boundary

The active mission flight path has three separate responsibilities:

1. `MissionLifecycle` decides mission phase, target, safety policy, and route
   updates.
2. MCheliCE WingMan advances the `MissionNode` list and publishes the effective
   route target in `WingmanEntry.autoTargetX/Y/Z`.
3. `AirframeController` and `NativeAircraftControlAdapter` own the final
   attitude/throttle setpoints while the server-side RDP control lease is
   active. MCheliCE still integrates the aircraft and resolves collision.

The server tick hook applies controls at `WorldTick START` with Forge event
priority `HIGHEST`. This priority does not establish that the callback runs
after every WingMan tick handler. A targeted transformer guard is injected
into movement helper methods found in the bundled MCheliCE 1.5.1
`WingmanTickHandler` and `AutonomousFlightHandler`; while the mission lease is
active, those guarded writes are suppressed. The separate packet hook blocks
the operator's flight-axis flags while mission ownership is active; camera,
weapon, and station actions keep their native packet path. Exact transformed
ordering and physical response still require runtime validation.

During final `LANDING` and `PARKING`, the guard releases the lease and allows
the full native MCheliCE recovery branch to run. Those branches include direct
motion writes outside the generic helper guard; running both paths there would
violate single-writer ownership.

The controller never writes `posX/Y/Z` or `motionX/Y/Z`. Its outputs are
rate-limited calls to `setRotYaw`, `setRotPitch`, `setRotRoll`, `setThrottle`,
and `updateControl`. This is a closed-loop, attitude-command autopilot using
native MCheliCE aircraft physics. The verified public control path does not
provide a general server-side equivalent of the player's camera/joystick input
axes, so this implementation does not claim to reproduce a human pilot's raw
input or prove player-like flight quality.

## State and control laws

`AircraftStateEstimator` retains position history and records physical motion,
derived velocity, horizontal/vertical speed, yaw/pitch/roll, throttle, ground
track, route progress and distance, target distance, mission owner, control
lease, source, units, confidence, and validity reason. The active setpoint is
the current `WingmanEntry.autoTargetX/Y/Z`, not the coordinates of an arbitrary
`LOITER` or `ATTACK` node. When a moving target updates the active `FLY_TO`, the
adapter updates that route node too, so WingMan does not restore stale
coordinates at the next tick.

The fixed-wing controller:

- computes desired course from world-space route delta;
- compares course with both body heading and observed ground track;
- uses bounded roll/yaw setpoints to reduce the route error;
- derives a vertical-speed request from altitude error and damps observed
  vertical speed before calculating pitch;
- in terminal guidance, derives the nose-down setpoint from the active route's
  vertical/horizontal slope, with a bounded 60-degree envelope and the same
  per-tick attitude rate limit;
- adjusts throttle from configured cruise/stall envelopes and observed speed.

The helicopter controller:

- controls yaw toward the route course;
- projects route error and measured velocity onto body-forward and lateral
  axes, then requests bounded pitch and roll;
- controls collective throttle from altitude error and measured vertical rate;
- keeps a nonzero lateral roll envelope instead of clamping helicopter roll to
  the fixed-wing profile's zero bank value.

Per-tick yaw, pitch, roll, and throttle deltas are rate-limited. The configured
speed, turn, climb, descent, and bank values remain provisional source defaults,
not measured specifications. Each supported airframe definition must be
calibrated in the RDP client before its profile can be described as realistic.
Unknown profiles are rejected by the addon controller.

FPV policy remains in `FpvFlightStateMachine`: pursuit, hover, and terminal
approach are mission states. They all use the active airframe controller; no
unconsumed `FlightCommand` object is presented as a working flight output.
Hover retains its current route target. Terminal state is monotonic and uses
the existing bounded target-intercept route update.

## What this change establishes

- One RDP flight-command path is enforced for active missions with a supported
  profile and a valid lease.
- WingMan remains useful for waypoint/attack/recovery route sequencing without
  also writing the final attitude/throttle values during that lease.
- Fixed-wing and helicopter control laws read observed movement, rather than
  pointing the aircraft nose at the target and assuming that equals the path.
- Dynamic `FLY_TO` target updates, hover hold, operator flight-axis suppression,
  bounded setter changes, and desired-versus-observed debug telemetry are
  connected to production code.
- Source tests and bytecode-transformer tests establish the controller math,
  setter wiring, control lease, and targeted MCheliCE method guards.

These are source/integration facts. They do not prove live flight, profile
stability, marker position, impact, or successful weapon effects.

An LLM adapter should remain outside the per-tick flight and weapon-control
path. It may draft a mission proposal or summarize telemetry, but deterministic
server validation must resolve the target identity, airframe capability,
movement setpoints, abort window, weapon authorization, and outcome state.
An LLM response must never directly write aircraft controls or authorize a
weapon release.

## Remaining work before calling this a complete release

- Calibrate each enabled aircraft profile in-game. Current common profiles
  (`switchblade`, `shahed136`, FPV variants, and `mq9`) use heuristics and may
  have different MCheli definitions or control response.
- Validate takeoff, landing, parking, and return-to-base. The controller is
  most directly exercised on an airborne route. Final `LANDING` and `PARKING`
  are explicitly handed back to MCheliCE's native recovery handlers because
  their bytecode includes direct movement writes outside the generic route
  helpers; mission recovery callbacks and handoff timing need live validation.
- Add a full 3D swept-volume route planner. The existing terminal corridor gate
  detects some loaded/blocked cases but is not general obstacle avoidance;
  unloaded chunks must remain unknown and cannot be treated as clear.
- Validate moving-target pursuit, target loss/reacquisition, target-above and
  target-below terminal passes, finite one-way impact, and reusable standoff
  release in a loaded world.
- Verify UI target-lock input and world-space marker behavior in player, UAV
  station, and remote-view cameras. Existing source projection is not evidence
  of correct visible alignment.
- For each supported missile, verify native guidance/seeker acquisition,
  projectile tracking, impact callback, and effect confirmation. TV coordinate
  guidance now has a source-verified per-update server handoff; live camera
  input isolation, steering, impact, and effect confirmation remain runtime
  checks rather than established behavior.
- Compare `desiredTrack`, `courseError`, position, motion, body yaw, pitch, roll,
  throttle, route node, and owner from server logs with the visible aircraft
  trajectory. Server logs alone cannot establish visual trajectory or reliable
  operator usability.

## Developer live validation sequence

Use the newly built addon JAR from `build/libs` in a disposable RDP instance;
do not overwrite the source project's dependency JAR. Keep the aircraft,
route, and target chunks loaded and enable the addon flight diagnostics.

1. Run a straight, level `FLY_TO` in a clear area without a weapon. Confirm the
   lease owner is `RDP_AI`, WingMan helper writes are suppressed, and the body
   heading and measured ground track converge on the desired track.
2. Repeat with left/right course changes and a gentle climb/descent for one
   fixed-wing profile. Record the actual turn radius, speed, bank, and climb.
3. Repeat with one helicopter/FPV profile: hover hold, lateral translation,
   altitude hold, target reacquisition, and operator flight input while AI owns
   the mission.
4. Run a moving-target track, first with the target at similar altitude, then
   above and below. Capture route target, target velocity, course error, body
   attitude, and observed trajectory.
5. Test terminal approach, abort boundary, collision/payload outcome, then
   reusable standoff and weapon release as separate tests.
6. Test obstacle and unloaded-chunk handling only after straight-route
   controls are stable. Verify every test terminates in an explicit state or
   bounded hold; do not interpret missing events as success.
7. Return the exact addon JAR hash, current `latest.log` and `debug.log`, and a
   short note of what was visually observed. These live observations are needed
   for the final per-profile tuning pass.
