# Repository truth baseline

**Reviewed:** 2026-10-07
**Branch:** `main`
**Repository:** `D:\Forge Modding\rdp-uav-addon`

This baseline reflects the current working checkout, including its uncommitted
changes. It is a source/build audit, not a claim that the addon has passed an
in-game mission scenario. Preserve unrelated working-tree changes when
continuing this work.

## Build and dependency tuple

| Component | Current evidence |
|---|---|
| Minecraft | 1.12.2 |
| Forge configured in `build.gradle` | 14.23.5.2859 |
| ForgeGradle | 3.0.197 |
| Gradle wrapper | 4.10.3 |
| Java used for this verification | Temurin OpenJDK 8u502; source/target 1.8 |
| Mappings | stable `39-1.12` |
| McHeli CE / bundled WingMan | local `libs/mcheli-ce-1.5.1.jar`; public signatures inspected |
| RDP Core | local `libs/rdpcore-1.0.0.jar` |
| ModularUI | Gradle compile/runtime dependency 3.0.5 |
| Elegant Networking | local `libs/elegant-networking-1.12-3.17.jar` |
| MixinBooter | Gradle runtime dependency 11.17 |
| JUnit | 4.13.2 |

`libs/modularui-3.0.2.jar` exists locally, but the checked-in Gradle
configuration resolves `com.cleanroommc:modularui:3.0.5`; do not treat the
unused local file as the build dependency.

This is the configured and successfully built source tuple, not a declaration
that a deployment runtime tuple is supported. The checked-in ForgeGradle run
block configures `runClient` only; it does not configure `runServer`. The
`run/mods` folder is empty, and the existing run directory contains world
data. No deployment jars were copied and no world was opened during this
phase.

## 1. Architecture map

```text
Client input / GUI / camera ray
        │ intent packets; never authoritative mission state
        ▼
RdpUavNetwork ─────────────────────────────────────┐
        │ server-thread validation                 │ server snapshots/events
        ▼                                          │
RDPUavMod / command and tick handlers              │
        ▼                                          │
MissionManager ─ AircraftAssignmentRegistry        │
        ├──────── MissionQueue                     │
        ▼                                          │
RDPMission / MissionState / target UUID            │
        │                                          │
        ├── MissionLifecycle ─ TargetResolver / TargetPolicy
        │        ├── WingManFlightAdapter ─ WingMan route state
        │        ├── AirframeController / movement lease
        │        │        └── NativeAircraftControlAdapter
        │        │              └── McHeliCE physics/collision
        │        └── GuidedWeaponAdapter / missile observation
        │
        ├── MissionNbtCodec / RDPMissionWorldData
        ├── scan report repository / WorldSavedData
        └── swarm manager / swarm persistence

Server telemetry ──────────────────────────────────┘
        ▼
Client state ─ GuiMissionControl / RdpUavTargetHud
```

| Area | Primary source owner | Boundary |
|---|---|---|
| Client GUI and key input | `GuiMissionControl`, `RdpUavClientKeys` | Creates intent; renders synchronized data |
| Network | `RdpUavNetwork` | Packets schedule server work; each server handler validates independently |
| Mission/assignment | `MissionManager`, `MissionQueue`, `RDPMission` | Server-owned assignment and explicit lifecycle transitions |
| Mission execution | `MissionLifecycle` | Server tick decisions and cleanup |
| Aircraft capability | `UavVehicleProfiles`, `UavVehicleProfile`, `VehicleCapability` | Explicit capability/profile resolution |
| Targeting | `TargetResolver`, `TargetPolicy`, `TargetSelector`, `TargetAuthority` | Loaded-world resolution and server target policy |
| WingMan | `WingManFlightAdapter` | Route intent/entry integration, not the final RDP movement writer |
| Movement | `MissionMovementAuthority`, `AirframeController`, `NativeAircraftControlAdapter` | Lease-guarded setpoints through McHeli setters; no position teleport |
| Payload | `GuidedWeaponRegistry`, `GuidedWeaponAdapter`, `GuidedNativeContract` | Capability/authorization and native handoff boundary |
| AI proposals | `AiTaskingService`, `AiDecisionEngine` | Proposes work; does not replace mission lifecycle authority |
| HUD | `RdpUavTargetHud` | Client-only presentation of server telemetry |
| Persistence | `MissionNbtCodec`, `RDPMissionWorldData`, `RDPScanReportRepository`, swarm saved data | World-scoped NBT data; runtime observations are not automatically fresh after reload |

## 2. Feature matrix

`EXISTS` means source implementation is present; it does not imply runtime
verification. `PARTIAL` means a defined boundary exists but end-to-end or
runtime behavior is incomplete. `UNKNOWN` means the inspected evidence cannot
establish the behavior.

| Feature | Status | Evidence and boundary |
|---|---|---|
| Mission definitions, validation, state machine | EXISTS | `MissionDefinition`, `RDPMission`, `MissionState`; tests cover deterministic contracts |
| Server aircraft assignment and operator authorization | EXISTS | `MissionManager` restores persisted mission reservations while unloaded and refreshes loaded/type/dimension state only after operator verification; covered by registry tests |
| Mission queue and tick lifecycle | EXISTS | `MissionQueue`, `MissionLifecycle`; restart reconciliation for active native assignments is incomplete |
| Mission NBT persistence | PARTIAL | World-saved mission records and codec exist; live save/restart/reconciliation is unverified |
| Exact entity target identity | EXISTS | Mission target UUID and exact loaded-UUID resolution; registry suggestions now preserve selected UUID |
| Server target policy/range/sightline | PARTIAL | Source checks exist; sightline is aircraft/target bounds-center ray, not remote camera visibility |
| McHeli camera view and thermal modes | EXISTS (API) | Public signatures checked in local McHeli CE 1.5.1 jar; raw pixels/server sensor results not established |
| Pixel-level machine vision / automated visual classification | NOT IMPLEMENTED | No verified public image-buffer or authoritative detection contract |
| Client target brackets and point marker | EXISTS (SOURCE) | World-space entity AABB brackets and point cross; GL/projection behavior unverified |
| WingMan route dispatch | EXISTS (SOURCE) | Adapter writes route intent; physical movement/convergence unverified |
| RDP leased aircraft attitude/throttle control | EXISTS (SOURCE) | Bounded setters and movement ownership tests; aircraft response not runtime-verified |
| Guided weapon capability and availability panel | EXISTS (SOURCE) | Server capability/reason rows; native launch/effect outcomes unverified |
| Scan/report persistence | PARTIAL | Observation/report metadata repository exists; scanner contents and RDP Core publication are not established |
| Swarm assignment and coordination | PARTIAL | Server-side managers and persistence exist; live multi-aircraft coordination and reload unverified |
| Current Forge development-client startup | MAIN MENU REACHED; INTERACTIVE USABILITY BLOCKED | Latest isolated Forge 2859 launch reaches `GuiMainMenu`; ModularUI 3.0.5 keybinding mixin is absent in the default development launch and mouse-input processing crashes before world entry |
| Matching dedicated-server startup | UNKNOWN / NOT ATTEMPTED | No `runServer` task/configuration exists in this build file; no current matching server-ready run is recorded |

## 3. Verification matrix

| Boundary | Status | Evidence |
|---|---|---|
| Source compilation | PASS | Fresh Gradle build on configured Forge 2859 |
| Focused tests | PASS | Aircraft restore/registry ownership, lock contract/UUID packet round-trip, weapon availability, movement lease, lifecycle tests |
| Full unit suite | PASS | Fresh `build --no-daemon --rerun-tasks --console=plain`: 317 tests, zero failures/errors |
| Reobfuscated artifact | PASS | `build` and `reobfJar` completed successfully for 2859 |
| Whitespace/diff check | PASS | `git diff --check` |
| McHeli public API signatures | API-VERIFIED | Inspected installed McHeli CE 1.5.1 jar with `javap`; not behavioral evidence |
| Forge 2859 client startup | MAIN MENU REACHED; GUI USABILITY BLOCKED | The prior `NetworkRegistry.newChannel` NPE was not reproduced. Latest default launch crashes at `GuiMainMenu` mouse-input processing in ModularUI `KeyBindAccess`; diagnostic mixin registration/remapping attempts did not establish a complete usable client |
| Dedicated server startup on current tuple | NOT ATTEMPTED | Historical probes do not establish current server readiness |
| GUI/network target-lock end-to-end | NOT ATTEMPTED | No current session crossed world entry and client/server interaction |
| Marker rendering/camera projection | NOT ATTEMPTED | No live world or inspected screenshot for current code |
| Aircraft movement/recovery | NOT ATTEMPTED | Unit/source checks cannot prove McHeli physics response or pilot packet timing |
| Weapon launch/impact/effect | NOT ATTEMPTED | No live projectile/effect confirmation |
| Save/reload and swarm reconciliation | NOT ATTEMPTED | Codec/source tests do not prove runtime world restart behavior |

See `BUILD_VALIDATION_REPORT.md` and `runtime-test-matrix.md` for commands,
historical probes, and detailed provenance.

## Phase 2 — Server ownership and aircraft registry

**Source/unit gate: PASS. Runtime lifecycle: NOT ATTEMPTED.**

The audit found that `restoreAssignments` created an unloaded `AircraftRecord`
after restart, then routed the persisted mission through the ordinary
availability-gated assignment method. That rejected the record solely because
the entity was not currently loaded and failed the persisted mission as
`AIRCRAFT_RESTORE_UNAVAILABLE`. Restoration now has a separate reservation
path that verifies stored owner, operational state, and absence of another
mission while allowing the entity itself to remain unloaded. Ordinary new
assignments still require a loaded, operational record. When a verified
operator later presents the aircraft, its loaded/type/dimension snapshot is
refreshed without replacing owner or mission reservation.

Regression coverage verifies unloaded restore reservation, rejection of
duplicate/missing/wrong-owner restore attempts, continued unavailability until
the operator refreshes the record, active mission reservation preservation,
and availability after the owning mission releases. World unload, dimension
transfer, disconnect, and live save/restart behavior remain source-reviewed
or unverified at runtime; the Phase 3 persistence/recovery and Phase 15 runtime
gates remain open.

## Phase 3 — Persistence and restart recovery

**Codec/world-data source tests: PASS. Full restart gate: NOT YET VERIFIED.**

`RDPMission` remains the durable mission domain record; the live per-world
entity index and `TargetLock` are rebuilt by `MissionLifecycle`, rather than
serialized as fresh runtime evidence. The codec now also stores a distinct
last-known target UUID/dimension/position/tick snapshot. Loading that snapshot
does not recreate `TargetLock` or make the observation current. Older records
without these optional fields still decode. Schema version 9 also persists
the last target unavailability reason, not a live lock.

The world-data loader previously allowed one malformed mission record to throw
out of `readFromNBT`, aborting the whole data load. It now rejects malformed
records individually, emits one bounded warning with count and first reason,
marks the saved data dirty so rejected entries are removed on the next save,
and preserves valid mission records. Tests cover malformed UUID and enum values
alongside a valid mission, target-position persistence without a live lock,
and existing mission/queue round trips.

World save/restart, multi-world assignment conflict recovery, and missing
entity recovery remain unverified in a running Forge world. The full Phase 3
gate and Phase 15 runtime gate remain open.

## Phase 4 — Mission execution engine

**Source architecture/tests: PASS. Full gate: BLOCKED pending live runtime.**

The existing execution path is wired through the server world-tick handler,
`MissionLifecycle` queue restoration and dispatch, capability/fuel/target
validation, WingMan route acceptance, observed aircraft/target updates,
bounded timeout and recovery handling, terminal reporting, and
`MissionManager` cleanup. This phase required no new subsystem: introducing
another dispatcher/context abstraction would duplicate the present ownership
boundaries. State-transition events already carry mission, aircraft,
previous/new state, reason, phase, control owner, tick, and target; this
continuation adds the persisted mission dimension to complete the requested
correlation fields.

Existing state-machine, queue, timeout, recovery, lease-release, lifecycle,
telemetry-projection, and packet-contract tests pass in the full suite. This is
not evidence of physical route progress, server observation timing, or
in-world completion. The plan's Phase 4 gate explicitly requires a live run;
it remains open because the recorded Forge 2859 client attempt fails before
world entry and no matching ready dedicated server is available.

## Phase 5 — Target resolution and entity lock

**Server-authoritative source gate: PASS. Live lock/sync gate: NOT ATTEMPTED.**

UUID resolution now returns explicit `INVALID_TARGET`, `TARGET_NOT_LOADED`,
`TARGET_DEAD`, `TARGET_WRONG_DIMENSION`, or `LOADED` status. Operator lock and
mission suggestion handlers continue to validate exact UUID, loaded-world
membership, operator/mission ownership, profile support, target policy, range,
line of sight, and terminal-target immutability before mission mutation.
Range and unsupported-profile failures are no longer conflated in the
mission-suggestion path.

The mission retains the committed UUID through loss. Its runtime `TargetLock`
projection transitions to LOST on invalidation and EXPIRED after the
observation grace period; both are debounced into bounded structured events.
Same-UUID observation restores VALID, while registry-ID matching cannot
substitute another entity. The last reason survives NBT reload, but neither
the runtime lock nor contact observation is restored as fresh evidence.

Packet intent/UUID round trips, lock status idempotence, loss/expiry/recovery,
and NBT stale-state contracts are covered by focused tests. World ray tracing,
live handler execution, client synchronization, and lock expiry/reacquisition
remain runtime-unverified; Phase 5's end-to-end gate remains open.

## Phase 6 вЂ” McHeli / WingMan execution integration

**Source/API audit: PASS. Runtime execution gate: RUNTIME-UNVERIFIED.**

The existing execution boundary was traced from `MissionLifecycle` through
`WingManFlightAdapter`, `WingmanRegistry`/`WingmanEntry`, native
`MissionNode` records, McHeli's `AutonomousFlightHandler`, and
`MCH_EntityAircraft`. The addon installs route intent in the WingMan registry;
its leased flight computer is the sole RDP attitude/throttle writer through
`NativeAircraftControlAdapter`. It does not directly set aircraft position or
motion. The configured Forge 2859 build compiles against the installed McHeli
CE 1.5.1 dependency; recorded `javap` checks establish public method presence,
not runtime compatibility or aircraft response.

Execution evidence is now explicit at three levels: `ROUTE_DISPATCH` means a
route was created and installed in `WingmanRegistry`; `ROUTE_NODE_ADVANCED`
records an observed `missionIndex` advance and current native state; periodic
`MISSION_SNAPSHOT` samples aircraft motion, route/node, mission state, and
native `AutonomousState`. This is not proof of physical convergence or
payload/world effect. Dispatch exceptions retain the existing recovery path
and now include the original throwable in the error log. Timeout, failure,
completion, and the terminal-guidance aircraft-loss path have explicit
mission outcomes. Outside terminal effect confirmation, a missing aircraft
remains `AIRCRAFT_UNAVAILABLE`/`WAITING_FOR_WORLD`: loaded-world absence alone
cannot distinguish chunk unload from destruction.

The guided path separately validates mission/objective, target identity,
capability, ammunition and safety before calling the native launch boundary.
`LAUNCH_ACCEPTED` is not projectile tracking, impact observation, or effect
confirmation; later states keep those outcomes distinct. Existing focused
capability, decision, mission lifecycle, and route-progress tests pass in the
fresh build. No matching in-world dispatch, node progression, flight response,
guided launch, or payload effect was observed, so Phase 6 remains
runtime-unverified and its live gate is open.

The previous `ClientSoundRegistry.commitChanges` failure was traced to the
Gradle copy task preserving the `META-INF` prefix twice, so the file passed to
ForgeGradle did not exist at its configured path. Correcting the destination
allows ForgeGradle to apply McHeli's rule making
`SoundHandler.loadSoundResource` public; the access-transformer log and
transformed Forge 2859 class confirm that change.

An intermediate isolated launch then failed in FML channel registration at
`NetworkRegistry.newChannel:207`. That NPE did not reproduce in the later
launches and is historical, with no established cause. The latest
class-traced launch reached `GuiMainMenu` and crashed during screen-event
processing with `NotImplementedException: KeyBindingMap getHASH()` from
ModularUI 3.0.5's accessor stub. Its manifest declares
`ModularUICore`, whose `IEarlyMixinLoader` registers `mixin.modularui.json`,
but the development launch's `fml.coreMods.load` property omits that coremod.
Adding it diagnostically registered the mixin config, but the production
SRG-named references did not match the MCP-named classes in ForgeGradle's
`-recomp.jar`; the key accessor still failed. Disabling refmap remapping made
that accessor apply and left the client at the menu during passive
observation, but other ModularUI GUI mixins still failed. No actual input,
world entry, or mission scenario was verified. The exact crash reports and
logs are preserved in the session-state evidence directory.

## 4. Limitation matrix

| Limitation | Evidence / root cause | Severity | Next verification or resolution |
|---|---|---|---|
| Forge 2859 development client is not interactively verified | Default launch reaches `GuiMainMenu` but crashes on screen input because ModularUI's keybinding accessor mixin is not registered. Diagnostic registration reveals a second MCP/SRG mapping incompatibility; refmap-disabled launch applies the key accessor but reports other failed ModularUI GUI mixins. The earlier `NetworkRegistry.newChannel` NPE was not reproduced. | Blocking runtime evidence | Use a production-mapped runtime or a verified deobfuscated ModularUI dependency/launch setup; then verify menu input before creating a disposable world |
| Initial runtime isolation override was ineffective | First attempt changed the `run/` working data: logs, generated McHeli language files, and mod config files have 2026-10-07 timestamps; no world files have timestamps from that attempt | User-data preservation note | Preserve these files as found; all subsequent client attempts use the separate session-state run directory |
| No current dedicated-server readiness | No current matching server log showing readiness/world ticks | High | Start a disposable server directory only after its EULA/config/mod set is deliberately prepared |
| Remote camera pose is not server authority | McHeli camera API is client-dependent; server LOS uses aircraft and target bounds centers | High for camera/selection parity | Keep documents explicit; test false accepts/rejects live without claiming pixel parity |
| HUD GL projection is untested | Unit tests do not execute Forge render event or OpenGL state | Medium | Live client screenshot/state inspection after startup blocker is resolved |
| Physical flight/control timing untested | Source order/transformer guard does not prove all tick ordering or aircraft response | High | Matching live mission, telemetry, competing-input, terminal release and recovery scenario |
| Runtime observations are not persisted as fresh evidence | Target UUID is persisted, but live contact/observation freshness must be reacquired | High | World save/reload scenario; verify same UUID only and explicit unavailable behavior |
| Scan/RDP Core integration is incomplete | Metadata persistence exists; scanner payload and Core publication contract are not established | Medium | Verify actual RDP Core API before any integration change |
| Historical version statements remain in old evidence | Older reports concern other Forge tuple/probe dates | Medium | Preserve only when clearly marked historical; use `build.gradle` as current build authority |

## Source scan and build notes

The Phase 0 marker scan found no `TODO`, `FIXME`, `stub`, `placeholder`, or
`UnsupportedOperationException` references in production Java sources. The two
`UnsupportedOperationException` matches are test assertions for unsupported
behavior, not empty production stubs. The repository contains 201 production
Java files, 57 test Java files, and 57 test classes. The last fresh full test
run generated 317 test results with zero failures or errors.

Gradle emits the legacy stable-mapping caution and deprecation notices during
configuration/build. The checked-in build completed successfully despite
those warnings; they are recorded rather than silently treated as runtime
evidence.

## Working tree and scope note

The checkout is dirty on branch `main`. In particular, the agent/policy edits
and `src.zip` deletion are user-owned pre-existing work. The current phase
does not stage, commit, revert, or reset any repository content. The existing
`run/` directory contains world data and was not relaunched during this audit.
