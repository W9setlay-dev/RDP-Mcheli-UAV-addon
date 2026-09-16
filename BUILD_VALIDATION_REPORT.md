# Build validation report

**Validation date:** 2026-09-07  
**Project:** RDP UAV Missions  
**Requested target:** Minecraft 1.12.2 / Forge `1.12.2-14.23.5.2864`

## Required status

```text
TARGET FORGE:
1.12.2-14.23.5.2864

RESOLVED FORGE:
Exact Forge Maven metadata and artifacts are reachable. The exact
userdev3 artifact is available, but ForgeGradle 2.3 requests the legacy
userdev.jar classifier, which is not published for this version. The exact
Forge userdev dependency therefore remains unresolved by this build.

MINECRAFT CLIENT TEST: BLOCKED
MINECRAFT SERVER TEST: BLOCKED
FORGEGRADLE TEST: BLOCKED
MCHeli CE 1.5.1 TEST: PASS
WINGMAN TEST: PASS
RDP UAV MISSION TEST: PASS
```

## Evidence

* `rdp-uav-addon/build.gradle` requests exactly
  `1.12.2-14.23.5.2864`; no version substitution was made.
* The configured repository is `https://maven.minecraftforge.net`.
* The exact Forge `userdev3.jar`, `universal.jar`, and `sources.jar` return
  HTTP 200 from the configured Maven repository.
* The exact Forge `userdev.jar` requested by ForgeGradle 2.3 returns HTTP 404.
* `gradle dependencies --configuration forgeGradleUserDevPackage` resolves
  the exact coordinate in dependency reporting, but `gradle test` is blocked
  during `extractUserdev` because `forge-1.12.2-14.23.5.2864-userdev.jar` is
  unavailable.
* ForgeGradle task configuration succeeds with the exact requested version.
* The current checkout has no Gradle wrapper and no `gradle` executable on
  PATH, so a fresh `gradle test` invocation was unavailable in this session;
  the previously recorded exact ForgeGradle failure remains the applicable
  blocker.
* McHeli CE `1.5.1` and RDP Core `1.0.0` were inspected from the installed
  jars. The WingMan classes are present under
  `com.norwood.mcheli.wingman`.
* All addon Java sources compile with `javac -proc:none -source 8 -target 8`
  against the installed McHeli CE/RDP Core jars, cached Minecraft 1.12.2
  mappings, and the installed ModularUI API.
* Dependency-free mission tests pass, including legal mission transitions,
  duplicate aircraft assignment rejection, ownership-aware aircraft assignment,
  queue retention/removal, manual target behavior, automatic priority
  selection, target locking, and failure reasons.

## Exact blockers

1. ForgeGradle 2.3.4 hardcodes the legacy `userdev` classifier for the
   configured Forge version.
2. Maven publishes `userdev3` for exact Forge `14.23.5.2864`, not the
   `userdev` filename ForgeGradle requests.
3. Because ForgeGradle cannot extract the exact userdev artifact, the actual
   Forge client, server, and ForgeGradle test tasks cannot be run.
4. McHeli/WingMan and RDP UAV results above are source/API and unit-level
   results only; they are not claims of exact Forge runtime integration.

No Forge version was downgraded, and no runtime result was fabricated.

## Post-implementation audit

### IMPLEMENTED FEATURES

* ForgeGradle addon project metadata targeting Minecraft 1.12.2 and exact
  Forge `14.23.5.2864`.
* Mission-domain enums and validated `RDPMission` configuration/state data.
* Runtime-toggleable debug logging through
  `config/rdp-uav-debug.cfg` and `/rdpuav debug on|off|status|reload`,
  with tagged lifecycle, WingMan, command, and network diagnostics.
* Server-authoritative kamikaze impact detonation with configurable speed
  threshold, nose collision sweep, native McHeli self-destruct weapon firing,
  and explicit detonation/fire-rejection mission states.
* Exact-entity operator AI target locking from the Minecraft crosshair, server
  UUID/lock/weapon validation, WingMan kamikaze override, configurable close
  firing distance, and explicit lock-clear cancellation.
* In-memory aircraft-to-mission exclusivity for the current process.
* In-memory mission queue and repository, with explicit peek/remove acceptance
  semantics so unaccepted work is retained.
* Manual registry-ID matching and automatic namespace matching over supplied
  candidates.
* Automatic candidate ordering by candidate priority, then distance.
* UUID-based target-lock data with last-known coordinates and dimension.
* `RDPScanReport` value object with priority and an NBT-backed
  `WorldSavedData` report repository.
* NBT codec for mission and report fields and a `WorldSavedData` container for
  mission records.
* Source-level live-world target resolver using loaded entities, registry IDs,
  dimensions, radius, dead-entity filtering, and UUIDs.
* Preliminary `MissionRunner` acceptance/timeout abstraction and aircraft
  registry domain model.
* A survival-level read-only command surface:
  `/rdpuav mission list`, `/rdpuav mission status <id>`, and
  `/rdpuav mission gui`.
* A small mission-control GUI with server-synchronized owned-mission
  summaries, mission selection, a server-validated Abort selected action,
  Refresh/Close controls, and an AI operator help page.
* A validated SimpleNetworkWrapper request/response path for opening the GUI
  refreshing mission summaries, and requesting a safe mission abort.
* A single source-level `WingManFlightAdapter` boundary using verified
  McHeli/WingMan classes.
* Dependency-free core test coverage for assignment, transitions, target
  selection, locking, and failure reason retention.

### PARTIAL FEATURES

* **Operator GUI:** mission monitoring and help are implemented, but mission
  creation, aircraft selection, detailed mission inspection, and report
  browsing are intentionally not part of this small feature request.

* **Mission state machine:** states, legal transitions, queue acceptance, and a
  preliminary timeout runner exist, but complete state handlers, recovery,
  flight observation, and active-world execution are absent.
* **Mission persistence:** mission and report records can be encoded to NBT,
  and queued missions have a reconstruction hook, but assignment ownership,
  aircraft registry, and full restart reconciliation are incomplete.
* **Target acquisition:** both candidate selection and a source-level
  live-world resolver exist, but visibility, target movement/loss handling,
  and mission-runner integration are absent.
* **WingMan integration:** the adapter creates a recon-only `MissionOrder` and
  `MissionNode` list and registers a `WingmanEntry`, but it does not resolve
  bases/runways/parking, create RTB/landing plans, observe WingMan state, or
  translate state/failure results back to `RDPMission`.
* **Permissions:** the command declares permission level 0, but there is no
  mission creation command or ownership validation because creation is not
  exposed through the command/API surface.
* **Performance scheduling:** the tick handler no longer discards queued work,
  but it does not yet schedule target tracking or scans.

### STUBS / PLACEHOLDERS

* `RDPUavMod.ServerTickHandler` remains a placeholder: it preserves queued
  work but does not dispatch or execute it.
* `MissionQueue` is process memory only; persisted queue reconstruction is not
  wired into startup.
* `RDPScanReport` still has no scanner, RDP Core publication, or report query
  command.
* `MissionManager.release` only releases an in-memory assignment; it does not
  complete/abort/fail a mission or persist the change.
* `WingManFlightAdapter.savePlan` only writes the global WingMan plan registry;
  it is not linked to a mission or persisted RDP record.
* No GUI, container, network packet, HUD, automatic RDP mission generator,
  attack controller, deep scanner, or integration test implementation exists.

### RUNTIME-VERIFIED FEATURES

None of the addon features have been executed inside the exact Forge
`14.23.5.2864` client or server. The installed runtime jars were inspected,
and source-level compilation was performed against installed McHeli CE/RDP
Core jars plus cached Minecraft mappings. Those checks do not establish
runtime behavior.

The new GUI/network source also compiles against cached Minecraft 1.12.2
Forge APIs and Java 8 language level. Its packet delivery and rendering remain
unverified until a matching client/server runtime is launched.

The survival abort path is source-validated only: the server checks mission
ownership and rejects terminal or malformed requests, but no live multiplayer
packet test has been run.

### UNVERIFIED FEATURES

* Actual `WingmanEntry` execution by `AutonomousFlightHandler`.
* Actual aircraft dispatch, navigation, attack, orbit, landing, parking, or
  kamikaze behavior through the adapter.
* McHeli UAV ownership/pairing and loaded-aircraft validation.
* Forge event registration and command behavior in a live server.
* `WorldSavedData` save/load behavior in a live world.
* Target visibility, target movement/loss recovery, dimension changes, and
  scan results.
* Any interaction with RDP Core world state, anomalies, mutation state, or
  reports.

### BLOCKED FEATURES

* Exact ForgeGradle compilation/test execution is blocked because ForgeGradle
  2.3 requests `forge-1.12.2-14.23.5.2864-userdev.jar`, while the repository
  publishes `userdev3` for that exact Forge version.
* Exact Forge client and server startup tests are consequently blocked.
* End-to-end survival mission creation and autonomous flight are blocked by
  both the Forge userdev issue and the missing gameplay implementation.

### KNOWN RISKS

* **Execution gap:** the server tick handler preserves queued missions but
  performs no dispatch, transition, or execution.
* **Restart inconsistency:** assignments and queues are not persisted or
  reconstructed; the same aircraft can be reassigned after restart while an
  old persisted mission remains active.
* **Persistence fragility:** `MissionNbtCodec.read` uses unchecked enum and UUID
  parsing. A corrupted or older save can fail world-data loading rather than
  producing a controlled mission failure.
* **Target-selection semantics:** priority is stored on each candidate rather
  than derived from mission target rules, and there is no configured entity
  type/filter or search-radius enforcement.
* **Ownership/security:** no code verifies that the selected aircraft belongs
  to or was dispatched by the requesting player. The adapter can be called
  with any `MCH_EntityAircraft` supplied by another caller.
* **Survival GUI actions:** Abort selected is server-authoritative and
  ownership-checked, but its live packet behavior remains unverified until a
  client/server runtime test.
* **Attack safety:** autonomous attack is intentionally excluded from the
  current recon-only adapter path; any future strike feature must be isolated
  and separately permissioned.
* **Client/server separation:** the command is server-side, which is correct,
  but there is no client GUI/network layer. No packet validation or
  synchronization exists.
* **TPS behavior:** current ticking is cheap, but the intended scans/tracking
  are not implemented; there is no evidence yet for bounded entity/block
  queries or multi-UAV performance.
* **Data races across worlds:** `RDPUavMod` owns one global manager while
  `WorldSavedData` is per-world; assignments and queue entries are not keyed
  by dimension/world.
* **API coupling:** direct construction and mutation of `WingmanEntry` relies on
  public binary fields. It is verified against the installed jar but remains
  sensitive to McHeli/WingMan implementation changes.

### RECOMMENDED NEXT STEP

Before adding UI polish or more mission states, implement and test the
server-side execution boundary: an ownership-checked aircraft registry,
persisted assignment/recovery reconstruction, a real mission runner with
explicit state handlers and timeouts, and a target resolver that queries the
live world by registry ID plus UUID. Then connect report persistence and RDP
Core publication. Only after that should GUI/network packets and full Forge
runtime testing be added.

## Current aggressive audit (2026-09-14)

### Executive summary

The reported `FAILED` result was not a HUD-only problem. The common mission
path could fail before execution because a stale inert `WingmanEntry` was
treated as an active controller, and manual entity lookup still used the old
256-block acquisition radius even though the documented mission envelope is
5000 blocks. The adapter now removes only inert `NONE`/`PARKED` entries and
preserves active native controllers. Manual missions use the 5000-block
envelope and report `OUT_OF_RANGE` or `INVALID_TARGET` instead of waiting for
a generic target failure.

The installed McHeli CE 1.5.1 jar is the authoritative dependency available
locally. Its server `AutonomousFlightHandler` consumes native
`WingmanEntry` mission nodes, so dispatch is a real native hand-off rather
than a teleport or simulation. Normal attack-node completion is not proof of
a projectile, hit, damage, or ammunition use: the native attack node scans
loaded hostile entities and the addon does not observe a damage event.
Kamikaze weapon use is the only path directly confirmed to call
`MCH_WeaponSet.use`.

### Feature audit

| Feature | Status | Evidence | Problem | Action |
| --- | --- | --- | --- | --- |
| Mission creation/queue | REAL | `RdpUavCommand`, `MissionManager` | Queue is global while persistence is per world | Retain; live restart test remains required |
| Server lifecycle | PARTIAL | `MissionLifecycle` | Loaded-entity and WingMan timing are runtime-sensitive | Retain; correlated failure logging is present |
| Manual target lookup | FIXED | `TargetResolver`, lifecycle dispatch | Old 256-block gate rejected valid distant loaded targets | Use 5000-block mission envelope |
| Target lock | PARTIAL | UUID persisted in `RDPMission` | `TargetLock` is not the operative authority | Treat mission UUID/state as authoritative |
| WingMan dispatch | FIXED | `WingManFlightAdapter`, native jar | Inert registry entries caused permanent rejection | Reclaim only inert entries |
| Normal attack | UNVERIFIED | native attack node and WingMan handler | Node completion is not weapon/result confirmation | Do not claim live damage success |
| Guided weapon dispatch | SOURCE-VERIFIED / LIVE-UNVERIFIED | `GuidedWeaponAdapter`, `GuidedNativeContract`, McHeliCE 1.5.1 APIs | Live seeker acquisition, trajectory, impact, and damage still require an in-game run | Entity seekers use native `setTarget` + `MCH_WeaponSet.use`; coordinate/TV weapons use native WingMan routing; incompatible combinations fail explicitly |
| Kamikaze | PARTIAL/REAL command | `MCH_WeaponSet.use`, impact detector | Live explosion/damage is unverified | Keep server-only native fire path |
| Square strike | PARTIAL | `SquareStrikePlan`, `MissionLimits` | Nodes are generated; effects are unverified | Keep 5000-block validation |
| AI tasking | PARTIAL | `AiTaskingService` | Native effect remains unverified | Retain assignment checks |
| HUD/network | REAL telemetry | `RdpUavNetwork`, `RdpUavTargetHud` | Telemetry is not proof of world effect | Retain diagnostic failure reason |

### Mission execution trace

```text
command/GUI packet
  -> server authorization and MissionManager.create
  -> persisted PLANNED mission and queue
  -> MissionLifecycle dispatch
  -> loaded UAV/target/range/ammunition checks
  -> native WingmanEntry + MissionNode plan
  -> McHeli server AutonomousFlightHandler
  -> lifecycle observes native state
  -> persisted mission state and telemetry HUD
```

The previous early failure points were stale `WingmanRegistry` entries and
manual target lookup outside the 256-block search radius. They are now
distinguished from unavailable aircraft, invalid targets, and out-of-range
targets.

### Target and server/client audit

Target identity is UUID-based after acquisition. Manual registry lookup only
selects a loaded entity with the exact registry ID; automatic lookup selects a
loaded entity in the requested namespace. The server owns acquisition, range,
locking, execution, and state transitions. The client receives telemetry and
renders it; a green marker is not proof that an attack is attackable or
completed. Dead, unloaded, and cross-dimension entities are rejected or
recovered through lifecycle rules.

### McHeli CE / WingMan audit

Verified local API path:

```text
WingManFlightAdapter.dispatch
  -> WingmanRegistry.put(WingmanEntry)
  -> native MissionNode.flyTo/loiter/attack
  -> AutonomousFlightHandler server tick
  -> aircraft steering and node advancement
```

`orbitRadius` is currently not consumed by the native plan; only loiter
duration is represented. `armAttack` appends a native attack node, but the
native handler does not prove that the requested target was hit. Explicit
self-destruct calls the verified native weapon API and checks cooldown/ammo.
The public upstream repository/Wiki was unavailable during this audit, so no
claim is made beyond the local jar and source/API evidence.

### AI and logging audit

AI recommendation is separated from mission creation and lifecycle execution.
Operator-owned assignments are checked before AI submission. Debug output
includes mission, aircraft, target, state, dimension, tick age, WingMan
state/node, and failure reason. Lifecycle tick logging is throttled to avoid
per-tick spam; state changes remain logged at info level and periodic progress
at debug level.

### Tests and build

`gradlew.bat test --no-daemon` passes after the fixes, and `git diff --check`
passes. The project can compile and run its source/unit suite. The exact
Forge/McHeli client/server mission path, aircraft steering, weapon release,
impact, landing, parking, and HUD packet behavior still require a running
matching instance. No live mission is claimed as passed without that evidence.
