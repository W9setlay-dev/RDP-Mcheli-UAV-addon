# Dependency diagram

```text
RDP-UAV-Addon
    |
    +-- Forge lifecycle (`RDPUavMod`)
    |       |
    |       +-- command layer (`RdpUavCommand`)
    |       +-- network layer (`RdpUavNetwork`)
    |       +-- world tick (`MissionLifecycle`)
    |
    +-- mission domain (`RDPMission`, `MissionState`, `MissionType`)
    |       |
    |       +-- mission queue (`MissionQueue`)
    |       +-- assignment registry (`AircraftAssignmentRegistry`)
    |       +-- aircraft registry (`AircraftRegistry`)
    |
    +-- target domain (`TargetSpec`, `TargetSelector`, `TargetResolver`, `TargetClassifier`)
    |       |
    |       +-- hostile namespace logic
    |       +-- world entity resolution
    |
    +-- flight boundary (`WingManFlightAdapter`)
    |       |
    |       +-- vehicle capability checks
    |       +-- attack and self-destruct support
    |       +-- native WingMan mission node use
    |
    +-- AI and sensor layer (`AiTaskingService`, `AiDecisionEngine`, `SensorFusion`)
    |       |
    |       +-- operator intent
    |       +-- recommendation / approval path
    |
    +-- persistence (`RDPMissionWorldData`, `MissionNbtCodec`, `RDPScanReportRepository`)
    |
    +-- diagnostics (`RdpUavDebug`)
    |
    +-- client HUD / GUI (`RdpUavTargetHud`, `GuiMissionControl`)

Minecraft 1.12.2 / Forge
    |
    +-- MCHeliCE 1.5.1 entities and aircraft metadata
    |
    +-- WingMan adapter / native mission nodes
    |
    +-- World and entity state
    |
    +-- player and server authority
```

## Dependency caveat

The addon depends on McHeliCE and WingMan for actual flight and weapon behavior, but it owns the mission state, target logic, persistence, and operator-facing mission flow. This separation is essential for a realistic hostile-world mission system.
