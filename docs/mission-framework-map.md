# RDP-UAV mission framework map

This document maps the current server-side mission path to its implementation
surfaces. It distinguishes source/test evidence from live MCheli behavior.

| Phase | Implementation | Current evidence | Remaining boundary |
|---|---|---|---|
| Task creation | `RdpUavNetwork`, `GuiMissionControl`, `MissionManager` | Mission identity, owner, aircraft, target specification, and queue assignment are represented in source and covered by tests | Exact connected-player creation flow still needs a target-instance mission trace |
| Preflight validation | `MissionLifecycle.dispatch`, `WingManFlightAdapter`, `GuidedWeaponAdapter`, `TargetAuthority` | Server checks aircraft presence, dimension, fuel, ammunition, target availability, ownership, and configured capability | Native aircraft-specific acceptance is not proven without live mission execution |
| Mission persistence | `RDPMissionWorldData`, `MissionNbtCodec` | Current worktree codec declares schema 7 and persists mission lifecycle metadata; the codec is locally modified, so this is not a clean-revision migration claim | In-memory projectile-to-mission association is not restored after server restart; live save/reload is unverified |
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

Historical project records describe a ForgeGradle server reaching `Done` and a
separate RDP Core world-data constructor failure. Those records are not a
current runtime acceptance result. The saved 2026-09-27 development client log
loads Forge 14.23.5.2768 and addon hooks, then crashes during client
initialization in McHeliCE `LegacyModelLoader` with a missing
`IModel.asVanillaModel()` method, before entering a world. The root
`logs/latest.log` is empty, and the compressed 2026-10-02 log contains only
test-worker hook messages. No saved current server-ready mission run exists.

These records prove neither mission persistence nor aircraft movement, target
lock retention, route convergence, projectile impact, or game-effect
confirmation. The historical RDP Core constructor report remains unverified
against a successful current world-load run; local JAR signatures alone are
not runtime evidence.
