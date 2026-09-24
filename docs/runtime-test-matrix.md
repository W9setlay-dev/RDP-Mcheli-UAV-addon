# Runtime test matrix

| Area | Command | Result | Notes |
| --- | --- | --- | --- |
| repository inspection | `git --no-pager status --short` | PASS | Confirmed working tree state |
| repository revision | `git --no-pager log -1 --oneline` | PASS | Resolved revision `1429d1f` |
| dependency inspection | `Get-ChildItem -Depth 2` | PASS | Confirmed project structure and runtime jar locations |
| code search | `grep -RInE 'TODO|FIXME|HACK|dummy|stub|placeholder|unsupported|not implemented|return null|return false|return true|FAILED|OUT_OF_RANGE|SelfDestruct|lock|unlock|target|mission|override|wingman|movement|rotation|velocity' .` | PASS | Source-level scan performed for stub and dead-code hotspots |
| baseline unit test | `.\gradlew.bat test --no-daemon --quiet` | PASS | Source-level tests passed |
| Forge runtime compilation | `./gradlew clean build` | BLOCKED | Exact Forge runtime setup depends on a missing userdev artifact |
| static analysis / packaging | `./gradlew check` | BLOCKED | Same Forge runtime blocker; substitute verification is the successful source test run |
| live Minecraft client/server test | unavailable in this environment | BLOCKED | Requires real Forge client/server runtime |
| JAR inspection | project JAR in `build/libs` | PASS | Build artifact exists |
| source validation | `javac` / project tests | PASS | Source-level validation only |
| exact entity/vehicle lock semantics | `.\gradlew.bat test --no-daemon` | PASS | Lock is non-destructive and server-revalidated; live McHeli acceptance remains unverified |
| native route refresh semantics | `.\gradlew.bat test --no-daemon` | PASS | Route rebuild code compiles and is covered by lifecycle source validation; live turning/orbit remains unverified |
| captured native WingMan route execution | CRM `latest.log` 2026-09-23 | OBSERVED | Server log records accepted `FLY_TO` routes and `FLY_TO reached`; no current process was running during this validation, and trajectory convergence remains unproven |
| captured kamikaze effect confirmation | CRM `latest.log` 2026-09-23 | NOT OBSERVED | Log contained a detonation-labeled path but zero `IMPACT_OBSERVED`/`EFFECT_CONFIRMED` events; it was treated as false success and the source path was corrected |
| single-press exact lock / separate clear action | `.\gradlew.bat test --no-daemon` | PASS | Client now sends exact crosshair UUID on `L`; `J` and dashboard clear request recovery; live packet behavior remains unverified |
| production artifact build | `.\gradlew.bat build --no-daemon` | PASS | Reobfuscated addon JAR built successfully; current SHA-256 `1BF3A772041606DB2C77FD28A948A6A084EE047D767AE20B162E812013A3034C` |
| RDP instance deployment | copy rebuilt `rdp-uav-addon-0.1.0.jar` to an RDP instance | NOT ATTEMPTED | No JAR was copied during the current rework; deployment is intentionally outside source validation |
| final sequential validation | `.\gradlew.bat test --no-daemon` then `.\gradlew.bat build --no-daemon` | PASS | Tests and reobfuscated build passed sequentially; no deployment claim is made |
| guided weapon capability and native seeker contract | `.\gradlew.bat test --no-daemon` | PASS | Verified objective compatibility, UUID continuity, native API decision handling, and explicit unsupported outcomes |
| guided weapon live launch/seeker/impact | controlled RDP gameplay session | BLOCKED | Source and native API wiring are complete, but this environment cannot reliably drive a controlled in-game launch and observe seeker acquisition, trajectory, impact, or damage |
| native PARKED route completion | source contract and regression review | PASS | PARKED no longer implies mission success: locked missions advance only inside the validated execution envelope, and post-launch terminal guidance cannot re-route or launch twice |
| packet string framing | `.\gradlew.bat test --no-daemon` | PASS | Shared network string framing rejects null and strings over 1024 UTF-8 bytes before allocation/use |
| Forge dedicated-server startup smoke | `.\gradlew.bat runServer --no-daemon` | PARTIAL | Reached `Done` with McHeliCE, WingMan, RDP Core, and addon coremod loaded; reproduced `RDPWorldSavedData.<init>(String)` failure during world load/save |
| native aircraft method presence | `javap -classpath libs/mcheli-ce-1.5.1.jar -public com.norwood.mcheli.aircraft.MCH_EntityAircraft` | PASS | Verified public rotation and throttle methods; method presence is not physical-flight evidence |
| official McHeliCE control-path comparison | `Warfactory-Official/McHeliCE@cbaff7f30e549ff75e485388c24c74a4316c2228` plus local 1.5.1 JAR | PASS | Official WingMan server handlers use bounded `setRotYaw`, `setRotPitch`, `setRotRoll`, `setThrottle`, and `updateControl`; local signatures match |
| native server control shaping | `.\gradlew.bat test --tests net.vas.rdp.uav.NativeControlMathTest --no-daemon` | PASS | Yaw/pitch/roll rate limits, yaw wrap, helicopter collective behavior, FPV throttle input, and finite bounded commands are deterministic |
| native server control ownership | source contract and `.\gradlew.bat test --no-daemon` | PASS | Mission lease is required; any directly ridden aircraft is rejected as `OPERATOR_CONTROL_CONFLICT`, including `isUAV()` aircraft; remote UAV-station operation requires the aircraft itself to remain unridden |
| native physical flight response | controlled Forge/McHeliCE runtime | LIVE_VALIDATION_REQUIRED | The source-compatible server path is implemented, but visible turning, climb, bank, stall, collision, and recovery still require runtime observation |
| observed aircraft state estimator | `.\gradlew.bat test --tests net.vas.rdp.uav.AircraftStateTest --no-daemon` | PASS | Captures finite position history, velocity, horizontal/vertical speed, mission owner, lease validity, route node, and target distance without issuing controls |
| terminal approach geometry | `.\gradlew.bat test --tests net.vas.rdp.uav.TerminalApproachPlannerTest --no-daemon` | PASS | Selects bounded 20/30/40/50/60 degree terminal angles, finite dive runs, and bounded climb behavior when the UAV starts below the target |
| square-strike route geometry | `.\gradlew.bat test --no-daemon` | PASS | Boustrophedon passes use configured heading/spacing and append a finite terminal approach leg before the one-way payload gate |

## Developer live handoff

### Developer Live Test: observed state and WingMan route

Build:

- Addon JAR: `D:\Forge Modding\rdp-uav-addon\build\libs\rdp-uav-addon-0.1.0.jar`
- SHA-256: `1BF3A772041606DB2C77FD28A948A6A084EE047D767AE20B162E812013A3034C`
- Expected McHeliCE: `1.5.1`, matching `libs\mcheli-ce-1.5.1.jar`
- Restart: required after replacing the addon JAR

Setup:

- Aircraft: configured `mq9` reusable UAV, then `switchblade` one-way UAV
- Missions: reconnaissance point mission, entity-lock reconnaissance, and one
  guided reusable attack only if the installed weapon is supported
- Target: stationary loaded entity in the same dimension, then a moving entity
- Conditions: keep aircraft, target, and route chunks loaded; do not use an
  operator-controlled seat for the aircraft

Action:

1. Replace only the installed addon JAR with the artifact above and restart.
2. Create a point reconnaissance mission for the configured reusable UAV.
3. Confirm the server accepts the mission and observe the WingMan route.
4. Lock the exact target entity through the authoritative lock action.
5. Repeat with the one-way profile only after the reusable route is stable.
6. Abort before terminal commitment, then separately test a committed terminal
   mission without attempting to force an abort.

Expected telemetry:

- `MISSION_DISPATCH_ATTEMPT`
- `ROUTE_DISPATCH`
- `CONTROL_LEASE_REJECTED` only when an intentional competing claim exists
- mission ID, aircraft UUID, state, control owner, route node, and server tick
- target UUID remains unchanged across reacquisition
- aircraft state samples show finite position and velocity values

Pass:

- WingMan remains the high-level route executor and McHeliCE remains the
  physics executor.
- Native control writes only bounded yaw, pitch, roll, and throttle through the
  verified McHeliCE API; no direct position or motion mutation is expected.
- Lease ownership is exclusive and released on pause, abort, failure, or loss.

### Developer Live Test: authoritative target policy

The Mission GUI now accepts a server-validated policy expression:
`include=minecraft:zombie;require=hostile;exclude=minecraft:player;confidence=0.5;allowvehicles=false`.
Use **Apply target policy** before launching. The policy is serialized in the
versioned launch packet, validated again on the server, persisted with the
mission, and applied during candidate discovery and reacquisition.

1. Apply an include policy for a loaded entity and launch reconnaissance.
2. Confirm the included entity can be selected while an excluded entity cannot.
3. Restart the server and verify the mission policy remains in its authoritative
   mission/HUD state.
4. Submit malformed or oversized tokens and confirm the GUI/server rejects them
   without creating a mission.

Policy serialization, validation, persistence, and deterministic filtering are
source/test covered. Visible GUI usability and in-world target acquisition are
`LIVE_VALIDATION_REQUIRED`.

### Remaining physical checks

Direct-seat/UAV-station ownership handoff, free-look camera alignment,
above/below terminal approach, reusable standoff/release/recovery, weapon
seeker behavior, collision, damage, and visible one-way finite flight remain
`LIVE_VALIDATION_REQUIRED`; compilation and deterministic tests do not claim
those outcomes.

### Abort-key validation

The abort key is `N` by default and now sends one abort request per key press;
it no longer requires a double-tap or an open mission GUI. If no mission row is
selected, the server resolves the active non-terminal mission owned by the
operator's currently controlled aircraft. The server still validates ownership,
terminal state, and terminal-commit restrictions before releasing WingMan,
missile guidance, and the mission control lease.

1. Start a point or reconnaissance mission and press `N` once while the GUI is
   closed.
2. Confirm the mission enters its aborted terminal state, WingMan is released,
   the mission lease is gone, native control stops, and direct/manual handling
   is available again. This handoff is `LIVE_VALIDATION_REQUIRED`.
3. Repeat with the GUI open and a selected mission row.
4. Press `N` during a committed one-way terminal dive; rejection is expected
   because terminal abort is intentionally forbidden after terminal commitment.

### Operator-only AI-control validation

1. Start missions with the aircraft unridden while the operator observes from a
   UAV station. Confirm native AI control continues and no direct pilot input is
   competing with it.
2. Repeat with an aircraft definition whose McHeli entity reports `isUAV()`.
3. Mount the aircraft directly during an active mission. The mission must fail
   with `OPERATOR_CONTROL_CONFLICT`, release WingMan and the lease, and stop
   issuing native commands.
4. Press `N` before terminal commitment and confirm the aircraft can be handled
   manually afterward. Visible control restoration and camera/free-look
   alignment remain `LIVE_VALIDATION_REQUIRED`.

### Profile execution audit

| Profile | Native route | Guidance/terminal behavior | Payload/recovery |
| --- | --- | --- | --- |
| `ONE_WAY` | WingMan route plus bounded native yaw/pitch/roll/throttle | Moving-target lead, finite multi-angle terminal leg, no terminal loiter | Self-destruct only after speed, nose/radius, geometry, and weapon checks |
| `HELICOPTER_ONE_WAY_FPV` | Same native path with helicopter attitude limits and FPV throttle command | Bounded hover/standoff and climb/descent target normalization | Terminal payload remains finite; visual hover and camera behavior require live validation |
| `REUSABLE_UAV` | Native route with profile altitude envelope | Target lead and standoff observation; no forced terminal dive | Guided/standoff attack then return/recovery path |
| `LONG_RANGE_REUSABLE_UAV` | Same reusable path with extended lock/detection envelope | Bounded prediction and no unbounded target-coordinate jumps | Guided attack/recovery; long-range chunk loading and physical range remain live checks |

The source path keeps target identity and target position separate from aircraft
motion: target coordinates feed a calculated waypoint/terminal plan, while
McHeliCE remains the sole physics executor. A nose-facing target with lateral
flight is therefore treated as a convergence failure/terminal diagnostic rather
than accepted as mission success. Visible confirmation that an aircraft does
not orbit or spiral while converging is `LIVE_VALIDATION_REQUIRED`.
- Target lock never silently changes to a nearby entity or point.
- Aircraft disappearance is reported as aircraft loss, not detonation.

Fail:

- `RDPWorldSavedData.<init>(String)` persists after dependency replacement.
- A mission reports success solely because the aircraft becomes `PARKED` or
  disappears.
- Two missions control one aircraft.
- Target UUID changes without an authoritative lock request.
- Any NaN/Infinity state sample or direct-teleport behavior appears.

Provide next:

- `latest.log` excerpt containing the mission ID and route events
- screenshot/video of the aircraft route and any terminal attempt
- exact aircraft definition, profile, weapon name, and coordinates
- whether the RDP Core constructor error was resolved

## Remaining risk

The runtime matrix is deliberately limited to what is available in this environment. The mission and guided-weapon systems are proven as source-level integrations against the local McHeliCE 1.5.1 API, but not as a live in-game autonomous flight or projectile-effects pipeline. The dedicated server now starts, but the RDP Core world-data constructor error blocks a trustworthy persisted-mission smoke test. The next runtime pass must resolve that dependency mismatch, then verify visible fixed-wing turning, moving-target tracking, orbit behavior, lock/clear feedback, native seeker acquisition, terminal approach, impact, and damage in the installed RDP instance.
