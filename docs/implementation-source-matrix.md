# Implementation source matrix

This matrix records the source-of-truth classification for currently known mission-system requirements.

| Requirement | Source category | Actual API/class | Reason | Verification evidence |
| --- | --- | --- | --- | --- |
| vehicle entity access | MCHELI_NATIVE | `com.norwood.mcheli.aircraft.MCH_EntityAircraft` | UAV entity lifecycle and identity are owned by McHeliCE, not the addon | `MissionLifecycle.findAircraft(...)`, `MissionManager.ensureAuthorized(...)`, `WingManFlightAdapter` |
| vehicle lifecycle | MCHELI_NATIVE | `MCH_EntityAircraft` and native aircraft metadata | The addon validates but does not own the aircraft lifecycle | `MissionManager.ensureAuthorized(...)`, `UavEnergyHandler`, `WingManFlightAdapter` |
| movement representation | WINGMAN_ADAPTER | `com.norwood.mcheli.wingman.wingman.WingmanEntry`, `MissionNode` | Movement and objective flow are represented via WingMan’s native mission-node model | `WingManFlightAdapter` and `MissionLifecycle.dispatch(...)` |
| heading and rotation | WINGMAN_ADAPTER | WingMan mission nodes and aircraft control state | Rotation and heading are handled by the merged WingMan/aircraft runtime rather than custom logic | `WingManFlightAdapter`, `KamikazeImpactDetector` |
| mission state machine | EXISTING_PROJECT_LOGIC | `RDPMission`, `MissionState` | State transitions are owned by the addon’s domain logic and enforced via `transitionTo(...)` | `RDPMission.transitionTo(...)`, `MissionState` |
| target discovery | CUSTOM_ADDON_LOGIC | `TargetResolver`, `TargetClassifier`, `EntityList` | The addon resolves world entities and classifies them using registry IDs and world data, not a native mission engine | `TargetResolver.find(...)`, `TargetClassifier` |
| target filtering | CUSTOM_ADDON_LOGIC | `TargetSelector`, `TargetSpec`, `TargetClassifier` | Filtering is done by namespace and registry-id rules in addon code | `TargetSelector.matchesNamespace(...)`, `TargetSpec` |
| target locking | EXISTING_PROJECT_LOGIC | `RDPMission.lockTarget(...)`, `MissionLifecycle.reacquire(...)` | The addon owns lock state as persisted mission metadata | `RDPMission.lockTarget(...)`, `MissionLifecycle` |
| target attack definition | WINGMAN_ADAPTER | WingMan `MissionNode.Type.ATTACK` and `WingManFlightAdapter` | Attack definitions are passed to WingMan rather than simulated by the addon | `WingManFlightAdapter.dispatch(...)` / `SquareStrikePlan` |
| navigation objectives | WINGMAN_ADAPTER | `WingmanEntry`, mission nodes, target coordinates | The addon supplies objective coordinates while WingMan steers the craft | `MissionLifecycle.dispatch(...)`, `WingManFlightAdapter` |
| role behavior | EXISTING_PROJECT_LOGIC | `MissionType`, `TargetPriority`, AI intent classes | Roles and mission kinds are modeled by addon logic and the decision layer | `AiTaskRequest`, `AiDecisionEngine`, `MissionType` |
| capability detection | CUSTOM_ADDON_LOGIC + MCHELI_NATIVE | `WingManFlightAdapter.hasAttackAmmunition(...)`, `MCH_EntityAircraft` | The addon inspects aircraft and weapon data, but the actual support is in the native McHeli definition | `WingManFlightAdapter`, `MCH_EntityAircraft` |
| operator commands | CUSTOM_ADDON_LOGIC | `RdpUavCommand`, `RdpUavClientKeys` | Command logic is entirely server-side and custom to the addon | `RdpUavCommand`, `RdpUavNetwork` |
| HUD synchronization | CUSTOM_ADDON_LOGIC | `RdpUavTargetHud`, `RdpUavNetwork.syncTargetTelemetry(...)` | HUD is addon-rendered and synchronized through custom packets | `RdpUavTargetHud`, `RdpUavNetwork` |
| persistence | EXISTING_PROJECT_LOGIC | `RDPMissionWorldData`, `MissionNbtCodec`, `ReportNbtCodec` | The addon owns the world-scoped persistence contract for missions and reports | `RDPMissionWorldData`, `MissionNbtCodec` |
| diagnostics | CUSTOM_ADDON_LOGIC | `RdpUavDebug` | Verbose logging is custom addon instrumentation | `RdpUavDebug`, logger usage in lifecycle |
| swarm coordination | EXISTING_PROJECT_LOGIC | `MissionManager`, `AircraftAssignmentRegistry` | Multi-UAV coordination is still modeled as assignment and queue logic, not a full swarm planner | `AircraftAssignmentRegistry`, `MissionManager` |
| reusable external logic | MCHELI_NATIVE / WINGMAN_ADAPTER | McHeli classes and WingMan entries | The addon should not duplicate real flight or weapon logic | Project docs and adapter boundary |
| contact telemetry | MCHELI_NATIVE + CUSTOM_ADDON_LOGIC | `ContactTracker`, `ContactSnapshot`, native entity identity | UUID continuity, bounded velocity and lifecycle are addon state; flight remains native | `MissionLifecycle.reacquire(...)`, `RdpUavTargetHud` |
| weapon guidance | WINGMAN_ADAPTER | `WingmanEntry.currentAutoTarget`, `autoTargetX/Y/Z`, `MissionNode.attack` | McHeliCE has no verified projectile steering API; native attack routing is authoritative | `WingManFlightAdapter.updateTargetGuidance(...)` |

## Unresolved classification items

- Direct hostile-structure model detection is not yet classified as a first-class mission type; it is still a point/entity target abstraction.
- Full dynamic mission doctrine for hostile bandits, military vehicles, and anomaly tracking remains conceptual and should be implemented under `EXISTING_PROJECT_LOGIC` once validated.

## Audit gate statement

Every currently known major requirement has been assigned a provisional source classification. The remaining work is architectural refinement rather than a blind rewrite.
