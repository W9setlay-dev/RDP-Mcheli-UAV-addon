# Current architecture

## High-level layers

```text
Client / Operator UI
    |
    v
RdpUavNetwork + GUI/HUD
    |
    v
MissionManager
    |
    +-- AircraftAssignmentRegistry
    +-- AircraftRegistry
    +-- MissionQueue
    +-- RDPMissionWorldData
    |
    v
MissionLifecycle
    |
    +-- WingManFlightAdapter
    +-- TargetResolver
    +-- KamikazeImpactDetector
    +-- KamikazePilotOverride
    |
    v
McHeliCE / WingMan / Minecraft world
```

## Mission ownership

- `MissionManager` owns assignment state, aircraft auth, and queue behavior.
- `RDPMission` owns the mission’s target, state, last update tick, and failure reason.
- `MissionLifecycle` owns the dispatch/update loop and conversion of target findings into state transitions.

## Target architecture

- `TargetSpec` defines the mission target definition.
- `TargetSelector` decides the best valid candidate from candidate lists.
- `TargetResolver` searches the live world for entities near a location.
- `TargetClassifier` decides whether an entity registry ID is actually hostile or modded hostile.

## Flight and execution architecture

- `WingManFlightAdapter` is the verified handoff boundary to WingMan.
- `MissionLifecycle.dispatch(...)` validates mission conditions before a dispatch is attempted.
- `KamikazeImpactDetector` checks nose impact and proximity-detonation conditions.
- `KamikazePilotOverride` supports explicit operator-triggered self-destruct logic.

## Persistence architecture

- `RDPMissionWorldData` persists mission records in world-saved data.
- `MissionNbtCodec` encodes mission state to NBT.
- `RDPScanReportRepository` persists scan reports and ranks threat values in a similar world-scoped pattern.

## Network and operator surface

- `RdpUavNetwork` handles mission GUI and HUD synchronization.
- `GuiMissionControl` provides a mission-monitoring UI.
- `RdpUavTargetHud` renders operator telemetry.
- `RdpUavCommand` exposes operator commands such as `/rdpuav mission ...` and `/rdpuav debug ...`.

## Current responsibilities

| Component | Responsibility | Notes |
| --- | --- | --- |
| `RDPUavMod` | Forge lifecycle registration | Registers commands, network, tick handlers |
| `MissionManager` | assignment, validation, queue | Source-level mission authority |
| `MissionLifecycle` | dispatch and mission processing | High-value mission execution boundary |
| `WingManFlightAdapter` | native WingMan handoff | Real controller boundary |
| `TargetSelector` | candidate ranking | deterministic priority-first logic |
| `TargetResolver` | entity lookup | radius-based search in loaded world |
| `TargetClassifier` | hostile namespace recognition | heuristic by registry ID |
| `RdpUavDebug` | diagnostics | verbose server-side logging |

## Known architectural constraints

- The mission system is server-authoritative and is not a fully autonomous “AI brain” by itself.
- The real-world mission logic remains source-level and needs runtime validation in a live Forge instance.
- The architecture is modular enough to support additional hostile-world mission types, but the relevant behavior is not yet implemented as an end-to-end mission doctrine.
