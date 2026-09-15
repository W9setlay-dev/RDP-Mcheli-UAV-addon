# RDP UAV Missions: Developer Guide

This guide explains how the RDP UAV addon is organized, how its mission
contracts work, how the operator GUI communicates with the server, and how to
develop and validate changes safely.

The addon is a separate Forge project inside this modpack repository. It is
not a replacement for McHeli CE, WingMan, or RDP Core. It provides mission
domain logic and a controlled integration boundary around those mods.

## 1. Supported environment

The source project currently records this exact validation target:

| Component | Version |
|---|---|
| Minecraft | 1.12.2 |
| Forge target in `build.gradle` | 14.23.5.2864 |
| McHeli CE | 1.5.1 |
| WingMan | Included in McHeli CE 1.5.1 |
| RDP Core | 1.0.0 |
| Java source/target | 1.8 |

The deployment instructions in `README.md` also describe a Forge
14.23.5.2859 build because that is the Forge artifact available in the local
cache and the requested deployment environment. Do not silently mix these
targets:

* Use 14.23.5.2864 when validating the checked-in `build.gradle`.
* Use 14.23.5.2859 only when deliberately preparing a deployment build for
  that runtime, after changing the Forge version intentionally and recording
  the change.
* Never describe a 14.23.5.2859 build as validation of 14.23.5.2864.

McHeli CE and RDP Core jars are external dependencies. They are intentionally
not committed to source control.

## 2. Project layout

```text
rdp-uav-addon/
  build.gradle
  README.md
  BUILD_VALIDATION_REPORT.md
  DEVELOPER_GUIDE.md
  config/
    rdpuav-missions.cfg.example
  src/main/java/net/vas/rdp/uav/
    RDPUavMod.java
    command/
      RdpUavCommand.java
    client/
      GuiMissionControl.java
    network/
      RdpUavNetwork.java
    mission/
      RDPMission.java
      MissionState.java
      MissionType.java
      MissionManager.java
      MissionQueue.java
      MissionRepository.java
      MissionRunner.java
      AircraftRegistry.java
      AircraftRecord.java
    target/
      TargetSpec.java
      TargetSelector.java
      TargetAcquisition.java
      TargetResolver.java
      TargetLock.java
    scan/
      RDPScanReport.java
      RDPScanReportRepository.java
      ReportNbtCodec.java
    persistence/
      MissionNbtCodec.java
      RDPMissionWorldData.java
    flight/
      WingManFlightAdapter.java
    energy/
      UavEnergy.java
      UavEnergyProvider.java
      UavEnergyConfig.java
      UavEnergyHandler.java
  src/test/java/
    net/vas/rdp/uav/CoreMissionTest.java
```

### Responsibility of each layer

**`RDPUavMod`** is the Forge entrypoint. It registers the network channel,
server command, and server-side event hooks. It should not contain mission
rules or GUI rendering.

**`mission`** contains server-owned mission state, assignment rules, queue
behavior, aircraft ownership records, and the preliminary runner abstraction.
This package should remain independent of client rendering.

**`target`** contains target configuration and selection. Candidate selection
is deterministic and testable without Minecraft; `TargetResolver` is the
Minecraft-facing live-world lookup.

**`scan`** contains report value objects and persistence. Scanner execution
and RDP Core publication are not complete yet.

**`persistence`** stores mission records in `WorldSavedData`. Saved data is
per world and must be treated as authoritative server state.

**`flight`** is the only intended WingMan integration boundary. New McHeli or
WingMan calls should be isolated here rather than spread through mission
classes.

**`network`** contains packet definitions and server/client handlers.
Packets are transport only; authorization and state validation belong on the
server handler.

**`client`** contains GUI code. It must never decide whether a player owns a
mission or whether a mission can be aborted.

**`energy`** owns the Forge Energy capability attached to real McHeli UAV
entities. The capability is server-authoritative, persists with the entity's
NBT data, and is consumed from the server world tick. It does not replace
McHeli flight physics, fuel, weapons, or WingMan steering.

### Per-aircraft energy configuration

The addon enables Forge Energy for any McHeli UAV listed in an enabled
`aircraft.<internal-name>` category. The internal name is the aircraft
definition name, not its display name; therefore `switchblade.yml` maps to the
`aircraft.switchblade` category. Capability attachment is intentionally not
gated on `isUAV()` or loaded aircraft metadata because Forge fires the
capability event while McHeli is still constructing the entity.

After the first launch, edit:

```text
<minecraft>/config/rdp-uav-energy.cfg
```

The global defaults and plane UAV overrides can be configured as follows:

```properties
[energy]
    capacity=250000
    idleDrainPerTick=2
    flightDrainPerTick=8
    minimumOperatingEnergy=1

[aircraft.switchblade]
    enabled=true
    capacity=150000
    idleDrainPerTick=2
    flightDrainPerTick=10
    minimumOperatingEnergy=1

[aircraft.shahed136]
    enabled=true
    capacity=150000
    idleDrainPerTick=2
    flightDrainPerTick=10
    minimumOperatingEnergy=1
```

`aircraft.switchblade` and `aircraft.shahed136` override the global values. Any enabled UAV profile,
including non-RF/non-fluid-fuel aircraft, receives the capability. Restart the
game after changing the file. The current addon does not parse custom energy
fields from McHeli YAML files, so adding an `energy:` block to a plane
definition has no effect.

Forge Energy/RF consumers can interact with an enabled UAV through the
standard `CapabilityEnergy.ENERGY` capability. Energy-powered UAVs are eligible
for missions and all existing addon features when their stored energy meets
`minimumOperatingEnergy`; they do not need McHeli fluid fuel. RF aircraft also
continue to mirror their Forge Energy storage to McHeli fuel. HBM Nuclear Tech
can power the Switchblade only when
the installed HBM/RF compatibility layer exposes its energy network through
Forge Energy/RF. The addon does not compile against HBM's private API or
pretend that an HBM-only custom energy port is automatically compatible.
When stored energy reaches the configured minimum, the addon sets McHeli fuel
availability to zero and lets McHeli's normal flight simulation handle the
resulting loss of operation.

## 3. Mission lifecycle

The mission state machine is defined in `MissionState`. The normal
reconnaissance path is:

```text
PLANNED
  -> DISPATCHED
  -> TAXI
  -> TAKEOFF
  -> CRUISE
  -> SEARCHING
  -> TARGET_ACQUIRED
  -> TARGET_LOCKED
  -> APPROACHING
  -> ON_TARGET
  -> ORBITING or SCANNING
  -> DEEP_SCANNING
  -> REPORTING
  -> RETURNING
  -> LANDING
  -> PARKING
  -> COMPLETE
```

Not every mission must use every optional state. `FAILED` and `ABORTED` are
terminal states and can be entered from any non-terminal state. Terminal
states cannot transition again.

`RDPMission.transitionTo` is the single domain entry point for state changes.
Do not assign the `state` field directly. The transition method enforces legal
edges, monotonic ticks, and queue status updates.

Attack-intent missions begin in reconnaissance mode and are automatically
armed by the server after `TARGET_LOCKED`. `Authorize attack` remains a manual
operator override. Do not invoke attack, collision, or kamikaze behavior
before the server has validated the lock and aircraft state.

## 4. Mission creation and ownership

An `RDPMission` records:

* mission, owner, and aircraft UUIDs;
* aircraft type and mission type;
* manual or automatic target specification;
* target priority and scan settings;
* search and observation radii;
* current state, target UUID, failure reason, and report ID;
* dimension, queue status, and recovery policy.

`MissionManager` reserves an aircraft before storing and queueing a mission.
`AircraftRegistry` and `AircraftRecord` provide domain-level ownership and
availability checks.

The GUI can now request reconnaissance, autonomous attack, and point missions.
Those requests are only intents; the server command path performs the actual
creation and validates all of the following:

1. sender is a real player;
2. sender owns or is authorized to use the aircraft;
3. aircraft exists in the requested dimension and is operational;
4. aircraft is not already assigned;
5. target specification and radii are valid;
6. requested behavior is allowed for the mission type.

Never trust owner, aircraft, dimension, target, or permission values supplied
by a client packet.

## 5. Persistence and restart behavior

`RDPMissionWorldData` stores mission records through `WorldSavedData`.
`MissionNbtCodec` encodes mission configuration and state fields. Reports have
their own `RDPScanReportRepository`.

Persistence is server-side and world-specific. The current implementation is
not a complete restart/recovery system. In particular:

* in-memory assignments are not fully rebuilt on world load;
* the queue reconstruction hook exists but is not wired into startup;
* aircraft records are not fully persisted and reconciled with live entities;
* malformed UUID or enum data can still fail decoding;
* report publication to RDP Core is not implemented.

When extending persistence:

* add fields with backward-compatible defaults;
* avoid changing existing NBT keys without a migration;
* validate UUIDs and enum names before using them;
* mark data dirty after every server-side mutation;
* rebuild in-memory indexes after loading saved records;
* keep dimension/world identity explicit.

## 6. Operator GUI

Open the GUI on a server with:

```text
/rdpuav mission gui
```

The GUI currently provides:

* a list of missions owned by the current player;
* mission selection;
* a target registry-ID field for entity missions;
* `Recon`, which creates a reconnaissance mission;
* `Auto attack`, which creates reconnaissance-first attack intent;
* `Point task`, which targets the block/structure under the operator camera;
* `Authorize attack`, available only after a target is locked;
* `Refresh`;
* `Abort selected`;
* `Close`;
* an `Operator help` page explaining the AI pilot workflow.

The GUI is a setup and safe control surface, not the AI pilot itself. The AI
pilot is represented by server-side mission and WingMan integration code. The
operator chooses the task, observes mission state, explicitly authorizes an
attack, or requests a safe abort.

When the player is piloting a UAV, the client keys provide quick task
controls. `U` opens Mission Control, `R` creates a registry-ID recon task from
the GUI field, `K` creates an autonomous registry-ID attack task from the GUI
field, `P` creates a camera-point task, `F` refreshes missions, `A` authorizes
the selected attack, and `X` aborts the selected mission. These keys are
configurable in Minecraft's Controls menu under `RDP UAV`; every action
remains server-validated. The server-side command remains available as
`/rdpuav mission gui`.

### Operator workflow

1. Pilot an authorized McHeli UAV and open `/rdpuav mission gui`.
2. For an entity mission, enter a fully qualified registry ID such as
   `techguns:zombie`, `srparasites:fumor`, `lycanitesmobs:abaia`, or
   `scp:...`. Use `Recon` for observation only, or `Auto attack` to create an
   attack-intent mission that arms after reconnaissance locks the target.
3. For a block or structure, aim the UAV camera at the point and press
   `Point task`.
4. Select the mission and use `Refresh` while WingMan searches and locks the
   target.
5. Auto attack arms after `TARGET_LOCKED` through the WingMan adapter.
   `Authorize attack` can still be
   pressed as an operator override, and the server re-checks ownership,
   UAV status, mission state, target data, and aircraft assignment.
6. Use `Abort selected` to stop a non-terminal mission.

The GUI does not silently select a target when the registry ID is malformed;
the setup status area reports that the value must use `namespace:id` form.
The command equivalents remain available for operators who prefer chat:
`/rdpuav mission create <namespace:id>`, optionally with `attack`, and
`/rdpuav mission attack-point`.

### Debug logging

Debug logging is disabled by default and is controlled by the server operator.
The setting is stored in `<minecraft>/config/rdp-uav-debug.cfg` and can be
changed without restarting:

```text
/rdpuav debug on
/rdpuav debug off
/rdpuav debug status
/rdpuav debug reload
```

The commands require operator permission level 2. Output is written to
`logs/latest.log` with the prefix `[RDP-UAV/Debug]`; subsystem tags include
`Lifecycle`, `WingMan`, `Network`, and `Command`. Enable it immediately before
reproducing a problem, then disable it after collecting the log because tick
and telemetry diagnostics are intentionally verbose.

### Kamikaze impact trigger

`KamikazeImpactDetector` runs on the server lifecycle for armed
`KAMIKADZE_TARGET` missions. It computes the aircraft nose from McHeli's
current and previous position/orientation, sweeps that point between ticks,
and checks both world collision boxes and collidable entities. The speed gate uses the aircraft motion vector and
`KamikazeConfig.minimumImpactSpeed`; the same gate applies to proximity
detonation, so acquiring a point target cannot fire the payload immediately.
Point-target and entity-target attack missions with a loaded
self-destruct weapon are promoted to `KAMIKADZE_TARGET`; WingMan is not given
an attack node that could fire the destructive payload on target acquisition.
The lifecycle detonates when the target point/entity/vehicle is within
`KamikazeConfig.detonationRadius`.

When an impact or proximity trigger is reached,
`WingManFlightAdapter.fireSelfDestructWeapon` selects the parsed
`destruct=true` weapon and reloads reserve ammunition when needed. Helicopters
use McHeliCE's native path; planes and other aircraft use the parsed explosion
configuration directly before aircraft destruction. A successful shot
transitions the mission to `KAMIKADZE_DETONATED` and releases the WingMan
entry. A transient fire rejection leaves the armed mission active for a retry.

Operators can manually fire a SelfDestruct payload with the
`RDP UAV fire SelfDestruct` binding (default `H`). The server validates
operator ownership, the destructive weapon flag, and ammunition, then calls
the same adapter without requiring a mission target or autonomous approach.

### Operator target lock

`RdpUavClientKeys` uses Minecraft's `objectMouseOver.entityHit`, so the input
refers to the exact entity under the operator's crosshair rather than a
registry search result. `L` locks that entity in one press; `J` (or the
dashboard button) clears the lock. `AiTargetLock` carries only the UUID and a
clear flag.
`ServerAiTargetLockHandler` performs the authoritative checks:

* the sender controls the McHeli UAV;
* the target is loaded, collidable, and not the aircraft itself;
* no other mission already owns the aircraft.

After validation, the server creates a non-destructive reconnaissance mission
with an exact manual target specification. `MissionLifecycle` refreshes a
bounded native WingMan approach/orbit route as the target moves and only
advances after WingMan reports route progress. The separate `J` key or
dashboard clear action clears the runtime lock and requests normal recovery.
Destructive attack and kamikaze missions remain separate operator-authorized
actions. Locks are runtime-only and must be re-established after restart.

### GUI packet flow

```text
Player command
  -> server sends OpenMissionControl
  -> client opens GuiMissionControl
  -> client sends RequestMissionData
  -> server filters missions by player UUID
  -> server sends MissionData
  -> client renders summaries
```

For abort:

```text
Client selects mission
  -> client sends AbortMission(mission UUID)
  -> server parses UUID
  -> server finds mission in the player's world
  -> server checks owner UUID and non-terminal state
  -> server transitions to ABORTED
  -> server releases assignment and marks saved data dirty
  -> server sends refreshed MissionData
```

For mission setup:

```text
Client sends CreateMission(target, attack, point)
  -> server schedules work on the server thread
  -> server converts the request to the existing command validation path
  -> server checks piloting, UAV authorization, target syntax, availability,
     target resolution, and WingMan dispatch
  -> server persists the mission and returns it on the next MissionData refresh
```

For attack authorization:

```text
Client selects a locked mission
  -> client sends AuthorizeAttack(mission UUID)
  -> server checks owner, attack intent, TARGET_LOCKED state, UAV status,
     live aircraft, and target coordinates
  -> server calls WingManFlightAdapter.armAttack(...)
  -> server transitions to ATTACK_TARGET and marks saved data dirty
```

The client never performs the abort transition locally. A malformed,
unauthorized, missing, or terminal mission request must have no effect.

When adding GUI actions, follow this rule:

> Client buttons express intent; server handlers authorize and execute it.

Do not place mission mutation, ownership checks, or permission checks in
`GuiMissionControl`.

## 7. Network development rules

`RdpUavNetwork` uses Forge 1.12.2 `SimpleNetworkWrapper`. Each packet must
have a unique discriminator and an explicit side.

For server-bound packets:

* obtain the sender from `MessageContext`;
* schedule work on the server thread;
* validate packet sizes before allocating arrays or strings;
* validate UUIDs and enum values;
* resolve all game objects from server state;
* return no sensitive data for objects the sender does not own.

For client-bound packets:

* schedule GUI changes on the client thread;
* treat packet data as display data only;
* handle missing or stale GUI instances safely.

Do not send complete mission objects or arbitrary NBT to the client. Send the
minimum display fields required by the current screen.

## 8. McHeli and WingMan integration

The installed McHeli CE 1.5.1 jar verified the following public integration
types:

* `MCH_EntityAircraft`
* `UAVTracker`
* `MCH_UavControl`
* `MissionOrder`
* `MissionNode`
* `MissionPlan`
* `AutonomousFlightHandler`
* `WingmanEntry`
* `WingmanRegistry`
* `MarkerRegistry`
* `TaxiRouteRegistry`

`WingManFlightAdapter` is the intended seam. It currently creates a
reconnaissance mission plan and registers it through the verified WingMan
types. Attack arming changes the existing WingMan entry to `ATK_AUTO`, sets
the live target coordinates/UUID, adds a strike mission type, and appends an
attack node. The server still requires explicit operator authorization before
this path is used.

WingMan remains authoritative for aircraft movement and rotation. Do not
teleport the aircraft or force entity `rotationYaw`/`rotationPitch` from the
addon. The installed McHeli CE API does not expose safe yaw/pitch setters on
`MissionNode`; direct rotation hacks would fight WingMan's flight controller.
If realistic turning needs improvement, extend the adapter only through
verified WingMan/McHeli control APIs and test turn-rate, approach angle,
overshoot recovery, and target loss in the real instance.

The adapter exposes recovery/terminal observations used by the server
mission lifecycle, but it does not provide authoritative takeoff arrival,
landing, or parking telemetry.

Aircraft pack weapon definitions are not interpreted as reconnaissance
behavior. Attack mode is armed automatically after a validated target lock for
autonomous attack missions, or manually by the operator override. This addon
does not claim weapon firing, damage, or self-destruct behavior without a live
instance test.

Never infer API signatures from online examples when the installed jar is
available. Verify the exact binary with `javap` or a source-compatible compile.

## 9. Development setup

From the repository root:

1. Install a Java 8 JDK and select it with `JAVA_HOME`.
2. Obtain the exact McHeli CE 1.5.1 and RDP Core 1.0.0 jars.
3. Copy them to:

   ```text
   rdp-uav-addon/libs/mcheli-ce-1.5.1.jar
   rdp-uav-addon/libs/rdpcore-1.0.0.jar
   ```

   Alternatively set `RDP_MCHELI_JAR` and `RDP_CORE_JAR` to absolute paths.
4. Confirm the Forge version in `rdp-uav-addon/build.gradle` matches the
   runtime you intend to build for.
5. Run Gradle from `rdp-uav-addon`.
6. Copy only the built jar from `build/libs` into the instance `mods`
   directory.

Do not copy source directories into `mods`. Client and dedicated server must
use the same addon jar and matching McHeli/RDP Core versions.

## 10. Validation commands

### Dependency-free core test

Compile the domain classes and test with Java 8:

```text
javac -source 8 -target 8 -d <output> <core source files> CoreMissionTest.java
java -cp <output> net.vas.rdp.uav.CoreMissionTest
```

This test covers mission transitions, assignment exclusivity, aircraft
ownership behavior, queue retention, target selection, target locking, and
failure reasons.

### Source/API compile

Compile all addon sources with:

* Minecraft 1.12.2 Forge mapped API;
* McHeli CE 1.5.1;
* RDP Core 1.0.0;
* required Forge/Netty/Guava libraries;
* Java source and target 8.

This proves source-level compatibility only. It does not prove that a
Minecraft client or server can start.

### Runtime validation

The keyboard path is deliberately split between intent and execution:

* `P`, `R`, and `K` submit mission requests through server packets.
* `A` is the explicit attack authorization gate; `K` alone cannot arm a weapon.
* `N` is an explicit destructive operator action; the server still requires
  supported aircraft, target, payload, and route conditions.
* `H` is a direct payload-fire request, but the server still validates operator
  control, aircraft support, destructive weapon metadata, and ammunition.
* `L` and `J` lock and clear exact targets without substituting nearby entities.
* `X` aborts only the requesting player's own non-terminal mission.
* `U` and `F` are read-only dashboard/data operations.

For each key, verify both the client action and the corresponding server
rejection path with an unauthorized player or invalid/stale mission before
testing the successful path.

A real integration validation must use a matching client and server:

1. start the dedicated server;
2. connect with the matching client;
3. run `/rdpuav mission gui`;
4. verify only the player's missions are shown;
5. verify Refresh updates the list;
6. verify Operator help opens and closes;
7. verify selecting and aborting an owned active mission works;
8. verify a different player cannot abort it;
9. verify malformed or terminal abort requests do nothing;
10. verify the mission record survives a save/restart cycle.
11. press `L` over an exact entity or McHeli vehicle and verify the server
    reports a non-destructive reconnaissance lock;
12. press `J` or dashboard **Clear lock** and verify recovery is requested;
13. create a mission from the GUI using Recon, Auto attack, and Point task;
12. verify malformed target IDs are rejected without creating a mission;
13. verify an Auto attack mission arms WingMan only after `TARGET_LOCKED`;
14. verify manual `Authorize attack` is accepted only at `TARGET_LOCKED`;
15. verify the UAV turns and approaches through WingMan control rather than
    teleporting or snapping its rotation;
16. press `N` or use the `Nose-dive` button while a locked attack mission is
    selected; verify the server accepts it only for supported Switchblade or
    Shahed UAVs and rejects ordinary aircraft;
17. for the Switchblade and Shahed packs, verify only the declared
    `SelfDestruct: true` weapon configuration enables terminal kamikaze
    behavior after autonomous attack arming or manual authorization.
18. spawn a Switchblade and verify it exposes Forge Energy while a Shahed-136
    does not;
19. verify the Switchblade energy value decreases on the server while idle and
    at the configured flight rate while moving;
20. save and reload the world, then verify the Switchblade energy value is
    preserved and that an empty battery disables further McHeli operation.

Do not report runtime verification when only `javac`, unit tests, or jar
inspection have been performed.

## 11. Known limitations

### McHeli UAV-station explosion guard

The source tree contains an experimental transformer history for
`MCH_EntityUavStation.attackEntityFrom`, but it is not registered in the
packaged addon. It previously caused a startup `VerifyError` under the target
runtime and must not be re-enabled without frame-safe bytecode and runtime
validation.

The following are intentionally incomplete and should not be mistaken for
finished gameplay:

* the tick lifecycle uses WingMan's public entry state and cannot verify
  physical takeoff, arrival, landing, weapon effects, or self-destruct;
* scan reports persist observation metadata, but no scanner contents or
  RDP Core report publication is implemented;
* aircraft discovery and persistent pairing are incomplete;
* GUI report browsing and detailed mission panels do not exist;
* the GUI setup flow is source/build validated but has not been live-tested
  through a matching client/server session;
* attack steering and any weapon, damage, or self-destruct behavior remain
  live-instance validation items and are not claimed by this addon;
* exact ForgeGradle 14.23.5.2864 execution is blocked by the missing legacy
  `userdev.jar` classifier in ForgeGradle 2.3;
* live GUI and packet behavior remains runtime-unverified unless a matching
  client/server test has been executed.
* Forge Energy is source/build validated only; a live battery drain, recharge,
  NBT reload, and capability-consumer test has not been executed in Minecraft.

The authoritative status is `BUILD_VALIDATION_REPORT.md`.

## 12. Truthful feature status

The following table is the authoritative implementation boundary. A feature
is not considered working merely because its enum, command, packet, or
adapter method exists.

| Feature | Current status |
|---|---|
| Forge 1.12.2 addon loading and McHeli dependency range | Source/build validated; exact runtime startup depends on the reobfuscated JAR and matching instance |
| UAV authorization and ownership checks | Implemented in command and server packet paths |
| Mission GUI setup, refresh, abort, and help | Implemented at source level; live client/server behavior not verified |
| Entity registry-ID and point target creation | Implemented for loaded entities and ray-traced points |
| WingMan reconnaissance dispatch | Server tick lifecycle now dispatches queued missions through the adapter; physical flight remains runtime-unverified |
| Target search/lock progression | Server tick lifecycle performs loaded-entity acquisition and UUID-based reacquisition; unloaded-chunk search is not implemented |
| Autonomous attack missions | Server tick arms WingMan attack mode after a validated target lock; live firing/damage is unverified |
| Manual attack authorization | Server-side checks and WingMan attack-node arming remain available as an override |
| Aircraft yaw/pitch/roll movement | Performed by McHeli WingMan handlers; not independently implemented or live-tested by this addon |
| Switchblade/Shahed pre-dive fallback | Adapter adds a computed 35-degree waypoint; it is not proof of collision, weapon release, damage, or self-destruct success |
| Native McHeli attack-angle/nose-dive support | Not exposed by the inspected WingMan 1.5.1 API |
| Target-loss recovery and mission resumption | Target loss now fails recon missions or requests WingMan RTB for active attacks; full mission resumption is not implemented |
| Scanning and report persistence | Observation metadata is persisted to WorldSavedData; scanner contents and RDP Core publication are not implemented |
| Return-to-base, landing, parking, and failure translation | Lifecycle requests WingMan RTB and maps public entry states; physical landing and all failure modes remain runtime-unverified |
| World-save restart/reconciliation | Queued missions are restored on first world tick; active WingMan assignments are not fully reconstructed |
| Switchblade Forge Energy capability | Implemented in the addon with configurable capacity/drain/minimum values; live capability, drain, recharge, and reload behavior remain unverified |
| Forge Energy on Shahed-136 and other UAVs | Deliberately disabled by aircraft identity |
| Production survival readiness | Not established until a matching live instance test passes |

Do not change a row to “implemented” based only on compilation, `javap`
output, a unit test, or a JAR listing. Runtime claims require a matching
Minecraft 1.12.2 client/server session and observable evidence in logs and
world behavior.

## 13. Safe contribution workflow

Before changing code:

1. identify whether the change is client-only, server-only, domain-only, or
   an integration change;
2. verify the exact API in the installed dependency jar;
3. preserve server authority for all mutations;
4. add or update dependency-free tests where possible;
5. avoid changing Forge versions as a workaround;
6. update the validation report if the implementation or test status changes.

Before declaring a feature complete, answer these questions:

* Is it implemented, partial, or a stub?
* Is it source/API-tested or runtime-tested?
* Does it survive a world save and restart?
* Does it enforce ownership and survival permissions?
* Does it preserve client/server separation?
* Does it avoid unbounded entity, block, pathfinding, or packet work?
* Does it avoid autonomous attack behavior unless explicitly authorized and
  separately implemented?
## Target-selection safety contract

`WAITING_FOR_TARGET` is the explicit no-candidate state. Lifecycle code clears
the authoritative target UUID and native WingMan guidance target while retaining
last-target history for diagnostics. `LOST` is reserved for a previously
acquired target that became unavailable; it is not mission failure. Both player
and AI operators retain an operational UAV and continue scheduled scanning.
Payload, V4, and guidance decisions require a current resolved target.

## Vehicle profile contract

`UavVehicleProfiles` resolves the exact `MCH_EntityAircraft.getAcInfo().name`
through the generated `rdp-uav-vehicles.cfg`. `UavExecutionProfile.ONE_WAY`
and `REUSABLE_UAV` are policy choices independent of McHeliCE's technical
aircraft type. The reusable policy supplies a bounded execution altitude and
uses target X/Z for route guidance; the one-way policy preserves the configured
McHeliCE payload path. The addon never fabricates a payload or infers terminal
behavior solely because a weapon happens to expose SelfDestruct metadata.

Long-range reusable profiles add independent detection, lock, and mission
execution ranges. Detection uses the shared target resolver; lock validation
uses the authoritative UUID and 3D distance; reusable execution uses the
configured horizontal mission range while preserving execution altitude.
