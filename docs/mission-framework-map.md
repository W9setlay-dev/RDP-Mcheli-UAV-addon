# RDP-UAV mission framework map

This document maps the current server-side mission path to its implementation
surfaces. It distinguishes source/test evidence from live MCheli behavior.

| Phase | Implementation | Current evidence | Remaining boundary |
|---|---|---|---|
| Task creation | `RdpUavNetwork`, `GuiMissionControl`, `MissionManager` | Mission identity, owner, aircraft, target specification, and queue assignment are represented in source and covered by tests | Exact connected-player creation flow still needs a target-instance mission trace |
| Preflight validation | `MissionLifecycle.dispatch`, `WingManFlightAdapter`, `GuidedWeaponAdapter`, `TargetAuthority` | Server checks aircraft presence, dimension, fuel, ammunition, target availability, ownership, and configured capability | Native aircraft-specific acceptance is not proven without live mission execution |
| Mission persistence | `RDPMissionWorldData`, `MissionNbtCodec` | Schema 4 migration preserves accepted-launch semantics, validates lifecycle ownership, and rejects malformed persisted states | In-memory projectile-to-mission association is not restored after server restart |
| Dispatch and route | `MissionLifecycle`, `MissionSafety`, `WingManFlightAdapter`, `AirframeController`, `NativeAircraftControlAdapter` | WingMan supplies route setpoints; a leased RDP fixed-wing/helicopter controller writes bounded MCheliCE attitude/throttle setpoints; matching WingMan movement helpers are guarded | Physical route acceptance, airframe response, and per-profile calibration require live telemetry |
| Entity designation | `TargetAuthority`, `TargetResolver`, `ServerAiTargetLockHandler` | Server validates UUID, dimension, loaded state, range, profile, ownership, and mission assignment | No nearby-target substitution is permitted, but exact runtime lock retention remains unobserved |
| Point/area designation | `RDPMission`, `SquareStrikePlan`, `WingManFlightAdapter.armSquareStrike` | Deterministic bounded pass nodes are generated; coordinate geometry is regression-tested | Native square-strike execution and weapon effect remain runtime-unverified |
| One-way terminal path | `KamikazePilotOverride`, `WingManFlightAdapter`, `MissionLifecycle` | Terminal state ownership, timeout, target immutability, and route-preserving corrections are represented | Physical descent, impact, and detonation are not proven by source tests |
| Reusable path | `UavVehicleProfile`, `AiDecisionEngine`, `MissionLifecycle` | Profile and recovery states exist, with explicit capability checks | Standoff release, threat-aware scoring, and return behavior are not fully proven in the native runtime |
| FPV/helicopter path | `HelicopterFlightController`, `NativeAircraftControlAdapter`, `FpvFlightStateMachine` | Feedback control calculates yaw/pitch/roll/throttle and applies the setpoints through the MCheliCE aircraft API under a lease | Hover stability, collective response, moving-target capture, and airframe calibration remain live tests |
| Weapon release | `GuidedWeaponAdapter`, `GuidedWeaponRegistry` | Capability registry fails closed for unsupported TV guidance; launch is distinct from completion | Native projectile spawn/release acceptance requires live observation |
| Projectile tracking | `MissileGuidanceController`, `ProjectileGuidanceMission` | Server observes loaded projectiles and correlates them with mission objectives | Association is currently in-memory and does not survive restart |
| Outcome assessment | `MissionState`, `MissionLifecycle`, `MissileGuidanceController` | `LAUNCH_ACCEPTED`, `PROJECTILE_TRACKED`, `IMPACT_OBSERVED`, `EFFECT_CONFIRMED`, and `OUTCOME_UNKNOWN` are distinct; missing/disappeared projectiles now converge to unknown | Authoritative native impact and effect callbacks are not wired end-to-end |
| Recovery and cleanup | `MissionLifecycle`, `MissionManager`, `RDPMission` | Assignment release and terminal state handling are present | Full reusable recovery proof and duplicate-event runtime validation remain pending |
| Diagnostics | `RdpUavDebug`, `docs/mission-telemetry.md` | Bounded mission, target, route, terminal, and projectile events are documented | Telemetry is only physically meaningful when captured during a real mission |

## Runtime evidence boundary

The ForgeGradle development server currently reaches the following milestones in
a clean run:

1. RDP-UAV coremod loads.
2. Forge loads the expected mod set.
3. `DedicatedServer: Done` is emitted.
4. RDP Core marks the server context `RUNNING`.

Those milestones prove server initialization only. They do not prove aircraft
movement, target lock retention, route convergence, projectile impact, or game
effect confirmation.

The development log also contains a nonfatal
`RDPWorldSavedData(String)` reflection failure from the external
`rdpcore-1.0.0.jar`. This repository does not modify that dependency. The
failure must remain separately tracked until a compatible RDP Core artifact is
available.
