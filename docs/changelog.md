# Changelog

## 2026-09-15

- Added the required hostile-world mission audit and architecture documents under `docs/`.
- Documented the source-of-truth classification matrix for mission, flight, target, and persistence requirements.
- Recorded the repository baseline, runtime constraints, target flow, and hostile-world design priorities.
- Produced a practical rework proposal grounded in the current mission architecture and the RDP hostile-world use case.
- Reworked `GuiMissionControl` into a two-pane tactical operator dashboard with mission filtering,
  selected-mission inspection, live target telemetry, and crosshair target-lock control.
- Exposed a read-only HUD telemetry snapshot for client UI reuse without duplicating network state.

## Architectural direction

The project is now documented around a realistic RDP mission doctrine:

- persistent reconnaissance and threat monitoring
- target filtering for hostile mobs and modded entities
- route coverage and patrol loops
- mission recovery and target reacquisition
- multi-UAV coordination without redundant coverage
- operator visibility and clear mission reasons

This keeps the work aligned with the current source architecture and the real-world survival demands of a hostile RDP Minecraft world.
