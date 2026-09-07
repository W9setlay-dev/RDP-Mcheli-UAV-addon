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

The current code also contains attack-related enum values because the original
mission model allowed them. The current execution phase is reconnaissance
only. Do not invoke autonomous attack, collision, or kamikaze behavior from
the GUI, runner, or recon adapter.

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

The GUI currently does not create missions. This is intentional: it avoids
exposing an incomplete aircraft pairing and flight-dispatch path to survival
players. Any future creation endpoint must validate all of the following on
the server:

1. sender is a real player;
2. sender owns or is authorized to use the aircraft;
3. aircraft exists in the requested dimension and is operational;
4. aircraft is not already assigned;
5. target specification and radii are valid;
6. requested behavior is allowed for the mission type.

Never trust owner, aircraft, dimension, or permission values supplied by a
client packet.

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
* `Refresh`;
* `Abort selected`;
* `Close`;
* an `Operator help` page explaining the AI pilot workflow.

The GUI is a monitor and safe control surface, not the AI pilot itself. The
AI pilot is represented by server-side mission and flight integration code.
The operator observes mission state and may request a safe abort.

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
recon-only mission plan and registers it through the verified WingMan types.
It does not yet provide complete takeoff observation, target arrival,
landing, parking, RTB, or failure translation.

The installed Switchblade and Shahed-136 definitions are UAVs with
autopilot disabled and self-destructing `fpvbomb` weapons. Do not treat those
weapon definitions as normal reconnaissance behavior. The recon path must not
activate attack or kamikaze behavior.

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

Do not report runtime verification when only `javac`, unit tests, or jar
inspection have been performed.

## 11. Known limitations

The following are intentionally incomplete and should not be mistaken for
finished gameplay:

* the server tick handler does not yet execute a complete mission lifecycle;
* target resolution is not fully connected to target-loss recovery;
* scanners and bounded scan scheduling are not implemented;
* RDP Core report publication is not implemented;
* aircraft discovery and persistent pairing are incomplete;
* mission creation is not exposed through the GUI;
* GUI report browsing and detailed mission panels do not exist;
* exact ForgeGradle 14.23.5.2864 execution is blocked by the missing legacy
  `userdev.jar` classifier in ForgeGradle 2.3;
* live GUI and packet behavior remains runtime-unverified unless a matching
  client/server test has been executed.

The authoritative status is `BUILD_VALIDATION_REPORT.md`.

## 12. Safe contribution workflow

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

