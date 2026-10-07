# Runtime test matrix

## Evidence provenance

Rows below are cumulative, dated historical records unless explicitly
revalidated against the current checkout. The saved 2026-09-27 development
client log reaches addon hook initialization but crashes during client model
loading, before entering a world. The 2026-10-06 Forge 14.23.5.2859 attempt
failed in McHeli CE `ClientSoundRegistry.commitChanges` with an
`IllegalAccessError`; the McHeli access-transformer copy-path defect is now
fixed, and that error no longer reproduces. The subsequent isolated 2026-10-07
attempt reaches FML channel registration but fails at
`NetworkRegistry.newChannel` before world entry; the cause of that NPE remains
unresolved. The root `logs/latest.log` contains three test-worker hook messages
only.
On 2026-10-02 an isolated copy of the project was used for a Forge 2768
`runServer` startup probe; Forge and coremods initialized, then Minecraft
stopped at the EULA prompt (`eula=false`) before world/server startup. These
startup attempts do not validate server readiness or UAV behavior, and 2768 is
not a resolved supported-runtime selection.

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
| Forge dedicated-server startup smoke | `.\gradlew.bat runServer --no-daemon` | HISTORICAL PARTIAL | An earlier run reported `Done` with McHeliCE, WingMan, RDP Core, and addon coremod, then a `RDPWorldSavedData.<init>(String)` failure. This is not current-checkout server evidence; the saved 2026-09-27 client run crashes before world entry. |
| Isolated configured Forge 2768 server startup probe | `.\gradlew.bat runServer --no-daemon` from the isolated session-artifact copy | BLOCKED | Gradle reported `BUILD SUCCESSFUL`, but Minecraft stopped with “You need to agree to the EULA” and wrote `run\eula.txt` with `eula=false`; no world was loaded and no server readiness or gameplay was verified. The original repository `run\` directory was not used. This probe does not resolve whether 2768, 2859, or 2864 is the supported runtime. |
| native aircraft method presence | `javap -classpath libs/mcheli-ce-1.5.1.jar -public com.norwood.mcheli.aircraft.MCH_EntityAircraft` | PASS | Verified public rotation and throttle methods; method presence is not physical-flight evidence |
| Forge 14.23.5.2859 development-client startup (2026-10-06, historical) | `run/crash-reports/crash-2026-10-06_13.37.40-client.txt` | SUPERSEDED FAILURE | McHeli CE 1.5.1 `ClientSoundRegistry.commitChanges` threw `IllegalAccessError` because the McHeli access-transformer copy task duplicated the `META-INF` path; after fixing the task, that error no longer reproduces |
| Forge 14.23.5.2859 isolated development-client startup (2026-10-07) | `runtime-phase6-after-at/logs/latest.log` and `crash-reports/crash-2026-10-07_14.54.00-client.txt` in session-state | BLOCKED (FML CONSTRUCTION) | Sound access now passes; FML then throws `NullPointerException` in `NetworkRegistry.newChannel:207` during `FMLContainer.modConstruction`; no world entry or gameplay |
| official McHeliCE control-path comparison | `Warfactory-Official/McHeliCE@cbaff7f30e549ff75e485388c24c74a4316c2228` plus local 1.5.1 JAR | PASS | Official WingMan server handlers use bounded `setRotYaw`, `setRotPitch`, `setRotRoll`, `setThrottle`, and `updateControl`; local signatures match |
| native server control shaping | `.\gradlew.bat test --tests net.vas.rdp.uav.NativeControlMathTest --no-daemon` | PASS | Yaw/pitch/roll rate limits, yaw wrap, helicopter collective behavior, and finite bounded commands are deterministic |
| native server control ownership | source contract and `.\gradlew.bat test --no-daemon` | PASS | Mission lease is required; operator presence never fails an active mission. AI remains the authoritative native-control writer while the operator is observation-only |
| native physical flight response | controlled Forge/McHeliCE runtime | LIVE_VALIDATION_REQUIRED | The source-compatible server path is implemented, but visible turning, climb, bank, stall, collision, and recovery still require runtime observation |
| Phase 6 route/native execution | matching Forge 1.12.2 / McHeli CE 1.5.1 world with mission diagnostics | RUNTIME-UNVERIFIED | Source traces route creation/registry acceptance, observed node-index advances, native state, aircraft motion, failure and timeout; no current world entry or physical response was observed. Missing aircraft outside terminal effect confirmation remains unavailable because unload and destruction cannot be distinguished from loaded-list absence. |
| Existing `run/` data preservation | repository `run/` file timestamps | PARTIAL INCIDENT | The initial isolation override was ineffective and updated runtime logs, mod configs, and generated language assets; no world files changed. These files were left untouched afterward; retry was correctly isolated under session-state. |
| observed aircraft state estimator | `.\gradlew.bat test --tests net.vas.rdp.uav.AircraftStateTest --no-daemon` | PASS | Captures finite position history, velocity, horizontal/vertical speed, mission owner, lease validity, route node, and target distance without issuing controls |
| terminal approach geometry | `.\gradlew.bat test --tests net.vas.rdp.uav.TerminalApproachPlannerTest --no-daemon` | PASS | Selects bounded 20/30/40/50/60 degree terminal angles, finite dive runs, and bounded climb behavior when the UAV starts below the target |
| square-strike route geometry | `.\gradlew.bat test --no-daemon` | PASS | Boustrophedon passes use configured heading/spacing and append a finite terminal approach leg before the one-way payload gate |
| RDP leased airframe-controller path | source tests and bytecode-transformer tests | PASS (SOURCE) | Fixed-wing and helicopter controllers consume active route setpoints; the lease applies rate-limited MCheliCE attitude/throttle setters and injected guards suppress competing WingMan movement writes |
| current reobfuscated addon build | `.\gradlew.bat build --no-daemon` | PASS | Current artifact SHA-256 `31A743C937DCD916F671127E5D6F562730A59309C46A0E4AF2D862932D97E9D5`; size 558,953 bytes |
| current full unit suite | `.\gradlew.bat test --no-daemon` | PASS | 236 tests, zero failures, zero errors, zero skipped |
| active RDP instance addon JAR | installed instance file inspection | NOT DEPLOYED | Installed artifact remains the older 541,876-byte JAR (SHA-256 `C4002E5C15689C291A3F478F846F1F874C6FEF500073ECE63FA86428D5C6532E`); source build was not copied into the instance |
| active RDP instance McHeliCE JAR | SHA-256 comparison with local dependency | MATCH | Both files are 82,128,715 bytes with SHA-256 `740B4DF7C6AE8FF66C2E54ADD422AF4F4495EEEF45DDF5815AF7FDF7FF49015B` |
| RDP Reality Distortion latest log | `latest.log` 2026-09-25 18:51:54 | OBSERVED | 48,745,899 bytes; supplied log predates Phase H source changes |
| exact Shahed lock rejection | RDP `latest.log` TargetLock events | OBSERVED | Three entity requests rejected at 299.325/309.188 blocks against configured 256-block lock range; target was loaded and passed the filter; zero lock acceptances in this log |
| progress/replan state churn | RDP `latest.log` transition counts | OBSERVED | 19,560 `SEARCHING->REPLAN`, 18,489 `REPLAN->SEARCHING`; accepted routes were repeatedly dispatched while the expired progress window was retained |
| takeoff phase | RDP `latest.log` point mission at tick 16976 | NOT PROVEN | `DISPATCHED->TAKEOFF->CRUISE->SEARCHING` happened in one tick; no climbout behavior is established |
| point-task request | RDP `latest.log` 2026-09-25 18:48:35 | OBSERVED | Server created point mission `cdeb744a-1656-4298-9c55-1b39a5b5d22f` with server-side XYZ; camera alignment remains unverified |
| kamikaze effect/outcome race | RDP `latest.log` 2026-09-25 18:37:34 | OBSERVED | `PAYLOAD_DETONATED` was logged for the mission aircraft, followed by `AIRCRAFT_LOST / EFFECT_NOT_CONFIRMED` on the next tick; source now confirms the synchronous native effect before that scan |
| exact pre-destruction hook | `.\gradlew.bat test --no-daemon` | PASS (SOURCE) | Transformer test reads the exact local/runtime-matching MCheliCE 1.5.1 JAR and confirms the callback in `handleDestruction(DamageSource, Entity, String, boolean)`; live collision order remains pending |
| route progress window after accepted replan | `.\gradlew.bat test --no-daemon` | PASS (SOURCE) | Regression test confirms the next route does not re-enter `REPLAN` on the following tick; runtime route movement is unverified |
| client entity lock marker | `.\gradlew.bat test --no-daemon` | PASS (COMPILE) | Entity target marker now renders brackets from the client entity's interpolated AABB; player/UAV/remote camera alignment remains `LIVE_VALIDATION_REQUIRED` |
| full test suite after Phase H edits | `.\gradlew.bat test --no-daemon` | PASS | 239 tests, zero failures, zero errors, zero skipped |
| Phase H artifact in active RDP instance | installed instance file inspection | NOT DEPLOYED | No modified addon JAR was copied into the active RDP instance |
| Phase H reobfuscated artifact | `.\gradlew.bat build --no-daemon` | PASS | 568,498 bytes; SHA-256 `0333FE0BABB83B6CA197E61056AEA51A406DF3CDFC430981A0DE13E1EE276687` |
| Phase I source and test-source compilation | `.\gradlew.bat compileJava compileTestJava --no-daemon` | PASS | Target-loss grace, FSM transition, mission NBT schema 5, hold-route source, and updated existing policy contract compile; tests not executed |
| Phase I reobfuscated artifact | `.\gradlew.bat reobfJar --no-daemon` | PASS | 569,676 bytes; SHA-256 `C0BC13390669D8B5D8F0F517CACA00E826FE7FEEFD4C3F596670BFA990FE422A` |
| Phase I artifact in active RDP instance | installed instance file inspection | NOT DEPLOYED | Active installation still has the pre-Phase-I addon JAR |
| Target missing for fewer than 40 ticks | runtime contact-loss scenario | PENDING_DEVELOPER_IN_GAME_TEST | Confirm mission state does not flap, target UUID remains, and route stops using the stale attack point |
| Target missing beyond 40 ticks then returns | unload/reload same entity | PENDING_DEVELOPER_IN_GAME_TEST | Confirm one transition to LOST/WAITING_FOR_TARGET and reacquisition of the same UUID only |
| Navigation stall with target still loaded | harmless fixed-wing / FPV route | PENDING_DEVELOPER_IN_GAME_TEST | Confirm route goes to REPLAN while contact identity remains assigned; no false target-loss transition |
| Terminal target becomes unavailable | one-way and FPV committed terminal test | PENDING_DEVELOPER_IN_GAME_TEST | Confirm existing terminal timeout remains bounded and the committed FPV route is not silently switched to hover |
| Phase J source/test-source compilation and reobfuscated package | `.\gradlew.bat compileJava compileTestJava reobfJar --no-daemon` | PASS | Source and test sources compile; the test runner was not invoked |
| Phase J fixed-wing hold-route stability | source inspection and compilation | PASS (SOURCE) | Repeated contact-loss updates now preserve the active hold node/index; reacquisition drops the hold `LOITER` suffix |
| Phase J reobfuscated artifact | `build/libs/rdp-uav-addon-0.1.0.jar` | PASS | 570,231 bytes; SHA-256 `74C7A7BA3CA4CC9AFFCF883711B69D65DE576D0DE94206BFD4FD25BB24144EDB` |
| Phase J artifact in active RDP instance | installed instance file inspection | NOT DEPLOYED | Current artifact remains only in the addon build directory |
| Repeated target loss while fixed-wing hold is active | runtime missing-target scenario | PENDING_DEVELOPER_IN_GAME_TEST | Confirm the route index/timer advance instead of resetting every tick; verify a renewed leg after WingMan parks |
| Same target returns during/after hold | runtime same-UUID reacquisition | PENDING_DEVELOPER_IN_GAME_TEST | Confirm the active route changes back to the same entity and no hold-only orbit runs before approach/attack |
| Phase K source/test-source compilation and reobfuscated package | `.\gradlew.bat compileJava compileTestJava reobfJar --no-daemon` | PASS | Recovery accounting, accumulated distance baselines, and mission NBT schema 6 compile; test runner was not invoked |
| Phase K reobfuscated artifact | `build/libs/rdp-uav-addon-0.1.0.jar` | PASS | 570,977 bytes; SHA-256 `895F4C37017BDEACC5EB83B792AA37CD37C1AAFB42671E6F8B97B184B018D416` |
| Phase K artifact in active RDP instance | installed instance file inspection | NOT DEPLOYED | New artifact remains in the addon build directory |
| Slow approach accumulates progress | loaded waypoint with measured reductions under 0.5 blocks/tick | PENDING_DEVELOPER_IN_GAME_TEST | Confirm cumulative distance improvement refreshes the progress timer and does not falsely enter `REPLAN` |
| Genuine route stall and recovery budget | stationary aircraft with valid target/route | PENDING_DEVELOPER_IN_GAME_TEST | Confirm each timeout increments `recoveryAttempts`, accepted routes do not clear it, and only measured movement resets it |
| Recovery exhaustion and cleanup | force more than five no-progress recovery cycles | PENDING_DEVELOPER_IN_GAME_TEST | Confirm terminal reason contains `_RECOVERY_EXHAUSTED`, lease/route are released, and mission remains persisted as `FAILED` |
| Active mission save/load | save and reload while in SEARCHING or REPLAN | PENDING_DEVELOPER_IN_GAME_TEST | Confirm the codec retains progress/recovery fields without resetting or exhausting the mission; schema 9 stores last-known target position and loss reason as inert metadata, not a fresh lock. |
| Malformed saved mission records | `.\gradlew.bat test --tests net.vas.rdp.uav.persistence.RDPMissionWorldDataTest --no-daemon` | PASS (SOURCE) | Invalid UUID/enum records are rejected with a bounded warning while valid missions remain loadable; actual world-load recovery remains unverified. |

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

The runtime matrix combines historical reports with current pending scenarios;
its old `PASS` labels do not mean the current dirty checkout was rerun. Source
and local McHeliCE 1.5.1 API evidence do not prove a live autonomous flight or
projectile-effects pipeline. Earlier documentation reports a dedicated server
reaching `Done` and an RDP Core world-data constructor error, but no current
saved server-ready mission run is available. The saved 2026-09-27 client log
instead fails in McHeliCE model loading before world entry. The next runtime
pass must first use an explicitly selected compatible runtime tuple in an
isolated instance, then verify visible fixed-wing and rotary-wing behavior
separately, moving-target tracking, lock/clear feedback, native seeker
acquisition, terminal approach, impact, and effect confirmation. Do not replace
an installed production JAR as part of this verification.

### Phase J developer handoff

Build artifact:

- `D:\Forge Modding\rdp-uav-addon\build\libs\rdp-uav-addon-0.1.0.jar`
- SHA-256: `74C7A7BA3CA4CC9AFFCF883711B69D65DE576D0DE94206BFD4FD25BB24144EDB`
- Size: 570,231 bytes

Test in a disposable copy of the RDP instance. Keep the aircraft and route
chunks loaded, then make the locked entity unavailable and restore that same
entity UUID. First restore it before 40 server ticks; then repeat with a loss
longer than 40 ticks. Confirm that the fixed-wing hold route's node/index
continues to advance, that the mission retains the original UUID, and that
reacquisition removes the hold-only loiter before approach resumes. Separately
exercise terminal target loss and confirm its existing timeout still ends the
mission. Capture the exact JAR hash, `latest.log`, and visual observations;
server route logs do not prove the physical path.

### Phase K developer handoff

Build artifact:

- `D:\Forge Modding\rdp-uav-addon\build\libs\rdp-uav-addon-0.1.0.jar`
- SHA-256: `895F4C37017BDEACC5EB83B792AA37CD37C1AAFB42671E6F8B97B184B018D416`
- Size: 570,977 bytes

Install this exact JAR only in a disposable copy of the RDP instance. Keep the
UAV, route, and target chunks loaded. First make a slow but steadily closing
route (preferably a profile whose per-tick movement is below 0.5 blocks) and
verify the accumulated distance baseline prevents a false stall. Then hold the
aircraft stationary with its target still available: inspect mission telemetry
and logs for `recoveryAttempts`, `recoveryReason`, and the transition reason
after each 200-tick no-progress window. Confirm accepting a replacement route
does not reset the counter. Finally repeat without restoring progress until the
mission fails after the bounded budget, and verify the route/control lease is
released and the failed reason persists after save/reload. Record aircraft
profile, start/end coordinates, exact artifact hash, `latest.log`, and a short
video. Do not infer physical movement from the server's route acceptance log.

### Phase L — guided projectile outcome callbacks

Build artifact:

- `D:\Forge Modding\rdp-uav-addon\build\libs\rdp-uav-addon-0.1.0.jar`
- SHA-256: `39E56DA13529C2FE24E2052E6D30629699D42C72D7D003B26DD8CDD6D343EE68`
- Size: 578,342 bytes

Use a disposable RDP instance and a guided weapon already marked as supported
by the addon. Confirm startup logs report both `MCH_EntityBaseBullet.onImpact`
and `MCH_Explosion` observers installed. Then fire at a loaded block and at a
loaded entity. For each shot, record the mission/projectile UUIDs and inspect
`native_impact_observed`, `nativeExplosionProcessed`, `explosionHitEntity`, and
the mission transition sequence.

Expected:

- A server block/entity collision is recorded only after McHeliCE completes its
  terminal `onImpact` branch.
- A non-null native `ExplosionResult` advances the mission through
  `IMPACT_OBSERVED` to `EFFECT_CONFIRMED`. This confirms explosion processing,
  not that the assigned target was hit or destroyed.
- A collision without an explosion result reaches `OUTCOME_UNKNOWN` after 100
  ticks and does not remain pending forever.
- A water pass-through, rejected interception, timeout, or projectile
  disappearance without the native collision callback is not reported as an
  impact.

Compare `explosionHitEntity` with observed target health separately. That flag
only says the native blast hit some entity. Keep all relevant chunks loaded for
the first run. Then fly/fire a projectile across a chunk boundary and unload its
last observed chunk while it is in flight. The expected source behavior is
`projectile_observation_deferred` with reason
`LAST_PROJECTILE_CHUNK_UNLOADED`, with no immediate unknown/miss classification.
Reload the chunk: if the same projectile resumes, guidance should resume; if it
does not exist after the chunk is loaded, the outcome should become unknown.
Repeat while another dimension is ticking and confirm it does not prune this
projectile's state. Saved/restarted projectile assignments remain open. Save
the exact artifact hash, complete `latest.log`, weapon/profile, target type,
hit/miss result, and a short clip. This matrix has not yet been run in-game.

## Exact Forge 2864 toolchain probe — 2026-10-02

| Area | Command | Result | Notes |
| --- | --- | --- | --- |
| Forge 2864 with current ForgeGradle 2.3 | `.\gradlew.bat test build --no-daemon --stacktrace` in isolated copy | FAIL | `extractUserdev` requests the unpublished legacy `userdev` classifier; the exact version exposes `userdev3`. Main checkout unchanged. |
| Forge 2864 compile and package with ForgeGradle 3.0.197 | `.\gradlew.bat build -x test --no-daemon --rerun-tasks` in isolated copy | PASS | `compileJava`, `compileTestJava`, `jar`, and `reobfJar` completed with Forge 2864 and copied local dependencies. This does not select the production tuple. |
| Forge 2864 source tests with ForgeGradle 3.0.197 | `.\gradlew.bat test --no-daemon --rerun-tasks` in isolated copy | PARTIAL | 277/284 passed after supplying ModularUI to test runtime. Seven `NetworkClientServerContractTest` cases fail at Forge `NetworkRegistry.newChannel` initialization in the test worker. |
| Forge 2864 live client/server or mission runtime | not run | NOT RUN | No client or server was launched; no EULA, world, deployment, aircraft, mission, packet, or HUD behavior was validated. |

## D-014 source implementation verification — 2026-10-03

| Area | Command | Result | Notes |
| --- | --- | --- | --- |
| Main checkout tests (Forge 2768) | `gradlew.bat test --no-daemon --console=plain` | PASS | 293 tests, zero failures/errors/skips. |
| Main checkout build (Forge 2768) | `gradlew.bat build --no-daemon --console=plain` | PASS | Includes jar and reobfuscation. Artifact hash is recorded in `BUILD_VALIDATION_REPORT.md`. |
| Updated source compile/package (Forge 2864, isolated FG3) | `gradlew.bat compileJava compileTestJava jar reobfJar --rerun-tasks` | PASS | Updated main checkout `src` was copied into the preserved FG3.0.197 probe; compile, test compilation, jar, and reobfuscation all reran successfully. Tests were not rerun here. |
| Swarm GUI/client/server runtime acceptance | not run | NOT RUN | GUI layout, snapshot/command packets, formation flight, patrol, target sharing, restart persistence, and strike authorization require an authorized live Forge/McHeli instance. The disposable server remains at `eula=false`; no EULA was accepted. |

## Forge 2859 client usability probe — 2026-10-07

All launches below used newly named directories under session-state; none
loaded the repository's existing `run/world`.

| Boundary | Attempt | Result | Evidence |
| --- | --- | --- | --- |
| Forge/FML startup and menu construction | Default isolated ForgeGradle `runClient`, configured Forge 14.23.5.2859 / ModularUI 3.0.5 | PASS to `GuiMainMenu` | FML construction, mod lifecycle, resources, and sound initialized; `NetworkRegistry.newChannel` NPE did not reproduce |
| Default menu input event processing | Same launch, no world loaded | FAIL | Crash during `Updating screen events`: `NotImplementedException: KeyBindingMap getHASH()` from `KeyBindAccess`; crash report `runtime-usability-class-trace\crash-reports\crash-2026-10-07_15.20.44-client.txt` |
| Register ModularUI coremod explicitly | Diagnostic `fml.coreMods.load` also included `com.cleanroommc.modularui.core.ModularUICore` | FAIL | `mixin.modularui.json` registered, but its SRG target `field_74514_b` did not match MCP field `KeyBinding.HASH` in ForgeGradle's `-recomp.jar`; crash report `runtime-usability-modularui-core\crash-reports\crash-2026-10-07_15.26.27-client.txt` |
| Disable refmap in the development launch | Diagnostic only; no repository configuration change | PARTIAL | `KeyBindAccess` applied and the client remained at the menu during passive observation; other ModularUI GUI mixins still failed (`GuiContainer`/`GuiButton`). No click or keyboard action was tested |
| Supply ForgeGradle SRG-to-MCP mapping file | Diagnostic only; no repository configuration change | FAIL | ModularUI accessor still failed to resolve; the launch was stopped without a world or user interaction |
| Wrap ModularUI with ForgeGradle 3.0.197 `fg.deobf` | Diagnostic init script; no repository configuration change | FAIL | `runClient` still placed the original cached `modularui-3.0.5.jar` on its process classpath; the same GUI mixin resolution failures remained |
| World entry, client/server interaction, mission execution | Not attempted | NOT ATTEMPTED | No world, packet exchange, target lock, route, aircraft movement, payload, or HUD behavior was tested |

The default development run's `fml.coreMods.load` property omits
`ModularUICore`, although ModularUI 3.0.5's manifest declares it and the plugin
provides the early mixin configuration. Explicit registration exposes a
separate development-mapping mismatch: the released dependency contains SRG
references while the ForgeGradle `-recomp.jar` exposes MCP names. The
refmap-disabled launch is not a complete fix because other ModularUI mixins
still fail. Phase 6 therefore remains runtime-unverified; the previous
`NetworkRegistry.newChannel` NPE is historical and was not reproduced in
these later launches.
