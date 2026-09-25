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
| single-press exact lock / separate clear action | `.\gradlew.bat test --no-daemon` | PASS | Current default lock is key `3` and clear is key `5`; client sends the exact crosshair UUID; live packet behavior remains unverified |
| production artifact build before leased-controller rework | `.\gradlew.bat build --no-daemon` | HISTORICAL | Reobfuscated addon JAR built successfully; earlier artifact SHA-256 `D3B0D52D5BE4FFFE63C8D8E5615DAA2149DA73B667FFB795656E671F0B0A1024`; superseded by the current build below |
| live instance log inspection | `minecraft/logs/debug.log` through 2026-09-25 | ACTIONED | Crosshair locks were rejected as `TARGET_FILTERED` for `mcheli:mch.e.plane`; another request targeted the controlled aircraft itself and was rejected as `PROFILE_UNSUPPORTED` |
| exact operator vehicle lock | server UUID validation | PASS (source) | Explicit crosshair locks now allow loaded McHeli aircraft when `allowHostileVehicles=true`, while automatic discovery keeps the hostile classifier |
| instant kamikaze payload | AI/manual detonation path | PASS (source) | Detonation now uses the configured McHeli explosion at the authoritative target point for both plane and helicopter paths; physical block/entity damage remains `LIVE_VALIDATION_REQUIRED` |
| performance-aware native guidance | deterministic unit tests and McHeliCE setter integration | PASS (source) | Native commands now use profile cruise/stall speed, climb/descent, bank, turn-radius, and observed motion-speed limits; McHeliCE remains the physics executor |
| RDP instance deployment | copy rebuilt `rdp-uav-addon-0.1.0.jar` to an RDP instance | NOT ATTEMPTED | No JAR was copied during the current rework; deployment is intentionally outside source validation |
| final sequential validation | `.\gradlew.bat test --no-daemon` then `.\gradlew.bat build --no-daemon` | PASS | Tests and reobfuscated build passed sequentially; no deployment claim is made |
| guided weapon capability and native seeker contract | `.\gradlew.bat test --no-daemon` | PASS | Verified objective compatibility, UUID continuity, native API decision handling, and explicit unsupported outcomes |
| guided weapon live launch/seeker/impact | controlled RDP gameplay session | BLOCKED | Source and native API wiring are complete, but this environment cannot reliably drive a controlled in-game launch and observe seeker acquisition, trajectory, impact, or damage |
| native PARKED route completion | source contract and regression review | PASS | PARKED no longer implies mission success: locked missions advance only inside the validated execution envelope, and post-launch terminal guidance cannot re-route or launch twice |
| packet string framing | `.\gradlew.bat test --no-daemon` | PASS | Shared network string framing rejects null and strings over 1024 UTF-8 bytes before allocation/use |
| Forge dedicated-server startup smoke | `.\gradlew.bat runServer --no-daemon` | PARTIAL | Reached `Done` with McHeliCE, WingMan, RDP Core, and addon coremod loaded; reproduced `RDPWorldSavedData.<init>(String)` failure during world load/save |
| native aircraft method presence | `javap -classpath libs/mcheli-ce-1.5.1.jar -public com.norwood.mcheli.aircraft.MCH_EntityAircraft` | PASS | Verified public rotation and throttle methods; method presence is not physical-flight evidence |
| official McHeliCE control-path comparison | `Warfactory-Official/McHeliCE@cbaff7f30e549ff75e485388c24c74a4316c2228` plus local 1.5.1 JAR | PASS | Official WingMan server handlers use bounded `setRotYaw`, `setRotPitch`, `setRotRoll`, `setThrottle`, and `updateControl`; local signatures match |
| native server control shaping | `.\gradlew.bat test --tests net.vas.rdp.uav.NativeControlMathTest --no-daemon` | PASS | Yaw/pitch/roll rate limits, yaw wrap, helicopter collective behavior, and finite bounded commands are deterministic |
| native server control ownership | source contract and `.\gradlew.bat test --no-daemon` | PASS | Mission lease is required; operator presence never fails an active mission. AI remains the authoritative native-control writer while the operator is observation-only |
| native physical flight response | controlled Forge/McHeliCE runtime | LIVE_VALIDATION_REQUIRED | The source-compatible server path is implemented, but visible turning, climb, bank, stall, collision, and recovery still require runtime observation |
| observed aircraft state estimator | `.\gradlew.bat test --tests net.vas.rdp.uav.AircraftStateTest --no-daemon` | PASS | Captures finite position history, velocity, horizontal/vertical speed, mission owner, lease validity, route node, and target distance without issuing controls |
| terminal approach geometry | `.\gradlew.bat test --tests net.vas.rdp.uav.TerminalApproachPlannerTest --no-daemon` | PASS | Selects bounded 20/30/40/50/60 degree terminal angles, finite dive runs, and bounded climb behavior when the UAV starts below the target |
| square-strike route geometry | `.\gradlew.bat test --no-daemon` | PASS | Boustrophedon passes use configured heading/spacing and append a finite terminal approach leg before the one-way payload gate |
| RDP leased airframe-controller path | source tests and bytecode-transformer tests | PASS (SOURCE) | Fixed-wing and helicopter controllers consume active route setpoints; the lease applies rate-limited MCheliCE attitude/throttle setters and injected guards suppress competing WingMan movement writes |
| current reobfuscated addon build | `.\gradlew.bat build --no-daemon` | PASS | Current artifact SHA-256 `31A743C937DCD916F671127E5D6F562730A59309C46A0E4AF2D862932D97E9D5`; size 558,953 bytes |
| current full unit suite | `.\gradlew.bat test --no-daemon` | PASS | 236 tests, zero failures, zero errors, zero skipped |
| active RDP instance addon JAR | installed instance file inspection | NOT DEPLOYED | Installed artifact remains the older 541,876-byte JAR (SHA-256 `C4002E5C15689C291A3F478F846F1F874C6FEF500073ECE63FA86428D5C6532E`); source build was not copied into the instance |
| active RDP instance McHeliCE JAR | SHA-256 comparison with local dependency | MATCH | Both files are 82,128,715 bytes with SHA-256 `740B4DF7C6AE8FF66C2E54ADD422AF4F4495EEEF45DDF5815AF7FDF7FF49015B` |

## Developer live handoff

### Developer Live Test: observed state and WingMan route

Build:

- Addon JAR: `D:\Forge Modding\rdp-uav-addon\build\libs\rdp-uav-addon-0.1.0.jar`
- SHA-256: `31A743C937DCD916F671127E5D6F562730A59309C46A0E4AF2D862932D97E9D5`
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

1. Install the artifact above in a disposable copy of the RDP instance and restart. The checked-in source and build artifact have not been deployed to the active instance.
2. Create a point reconnaissance mission for the configured reusable UAV.
3. Confirm the server accepts the mission and observe the WingMan route.
4. Lock the exact target entity through the authoritative lock action.
5. Repeat with the one-way profile only after the reusable route is stable.
6. Abort before terminal commitment, then separately test a committed terminal
   mission without attempting to force an abort.

### Developer Live Test: exact entity lock input

The default lock action is key `3` and the GUI **Entity lock [3]** button. The
client sends only the UUID captured from the crosshair; the server resolves
that UUID in the current world and never substitutes a different registry
entity. Key `3` may also be assigned to hotbar slot 2 in an existing
`options.txt`; the addon now checks the raw LWJGL event as well as the Forge
binding, but a custom rebind is still recommended if the hotbar action causes
visible interference.

1. Start an active mission with a selected aircraft and keep the target loaded.
2. With the GUI closed, aim directly at the target and press `3` once.
3. Confirm `logs\debug.log` contains `Entity lock request`, followed by either
    `Entity lock accepted` or an explicit `Entity lock rejected` reason.
4. Open Mission Control while still aiming at the target, then click
    **Entity lock [3]**. The GUI captures the crosshair UUID on open and reuses
    it if the screen hides the world ray trace.
5. Confirm HUD/telemetry shows the target UUID and the accepted lock state.
6. Repeat against a McHeli aircraft only when `allowHostileVehicles=true`.
    A loaded aircraft is valid for explicit crosshair selection; automatic
    hostile scanning remains separately filtered.
7. If there is no active mission, `NO_ACTIVE_MISSION` is expected: locking is
    intentionally not a mission-creation action.

Acceptance of the packet, exact UUID continuity, and player-facing status are
source/instrumentation validated. Actual client key delivery, McHeli aircraft
acceptance, guided launch, and payload effects remain
`LIVE_VALIDATION_REQUIRED`.

### Developer Live Test: `T` point task

`T` is a point-strike command, not entity lock. With an authorized controlled
aircraft, the server ray traces the operator's current camera look vector out
to 256 blocks and creates a point mission at the hit block. An unobstructed ray
or missing controlled aircraft does not create an entity target. Use key `3`
or the GUI Entity lock button to request a specific entity UUID.

1. Aim at a loaded block within the aircraft profile's allowed range and press
   `T` once.
2. Confirm the server reports the created point mission and its exact point.
3. Aim into open sky and press `T`; the server should report no camera point.
4. Verify a nearby entity is not selected by either point-task attempt.

Key delivery and camera alignment still require the developer's live client.

Expected telemetry:

- `MISSION_DISPATCH_ATTEMPT`
- `ROUTE_DISPATCH`
- `CONTROL_LEASE_REJECTED` only when an intentional competing claim exists
- mission ID, aircraft UUID, state, control owner, route node, and server tick
- target UUID remains unchanged across reacquisition
- aircraft state samples show finite position and velocity values

Pass:

- WingMan advances route nodes; the RDP controller owns leased attitude/throttle setpoints, and McHeliCE integrates the resulting physics.
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
3. Mount or observe the aircraft during an active mission. The mission must
   remain active; operator flight input must not replace the mission lease or
   native AI command stream.
4. Press `N` before terminal commitment and confirm the aircraft can be handled
   manually afterward. Visible control restoration and camera/free-look
   alignment remain `LIVE_VALIDATION_REQUIRED`.

### Entity-lock evidence

The entity-lock action is the `3` key or the GUI **Entity lock** button. It
sends the exact crosshair entity UUID to the server; the server validates
operator ownership, aircraft profile, loaded target identity, policy filters,
and lock range. Every request now emits an unconditional `RDP-UAV/Network`
log line for request, acceptance, or rejection, while detailed structured
events remain controlled by `rdp-uav-debug.cfg`.

After an accepted lock, `targetId` is persisted for reconnaissance/search
missions as well as attack missions; it is not restricted to the later attack
states. Confirm the mission HUD changes to `TARGET_LOCKED` and the log includes
`Entity lock accepted`. If the entity unloads, the mission must report the
same UUID as lost/waiting; it must never substitute another entity with the
same registry type.

### Profile execution audit

| Profile | Native route | Guidance/terminal behavior | Payload/recovery |
| --- | --- | --- | --- |
| `ONE_WAY` | WingMan route plus bounded native yaw/pitch/roll/throttle | Moving-target lead, finite multi-angle terminal leg, no terminal loiter | Self-destruct only after speed, nose/radius, geometry, and weapon checks |
| `HELICOPTER_ONE_WAY_FPV` | Leased helicopter attitude/collective feedback controller; FPV state selects mission policy without a separate unconsumed command object | Bounded hover/standoff and climb/descent target normalization | Terminal payload remains finite; visual hover and camera behavior require live validation |
| `REUSABLE_UAV` | Native route with profile altitude envelope | Target lead and standoff observation; no forced terminal dive | Guided/standoff attack then return/recovery path |
| `LONG_RANGE_REUSABLE_UAV` | Same reusable path with extended lock/detection envelope | Bounded prediction and no unbounded target-coordinate jumps | Guided attack/recovery; long-range chunk loading and physical range remain live checks |

The source path keeps target identity and target position separate from aircraft
motion: target coordinates feed a calculated waypoint/terminal plan; the leased RDP controller applies bounded attitude/throttle setpoints, and McHeliCE integrates aircraft physics. A nose-facing target with lateral
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
