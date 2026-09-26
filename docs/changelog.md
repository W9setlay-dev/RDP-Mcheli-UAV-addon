# Changelog

## 2026-09-26 — Bounded route recovery

- Count no-progress recovery transitions instead of clearing the budget when a target exists or a replacement route is accepted.
- Compare accumulated best distance-to-target/waypoint so slow movement can establish progress even when each tick's delta is below 0.5 blocks.
- Preserve recovery reasons in FSM transitions and fail with `_RECOVERY_EXHAUSTED` after the bounded retry budget is consumed without observed movement.
- Persist route progress and the last meaningful-progress tick in mission NBT schema 6; old saves receive a conservative creation-tick baseline.
- `compileJava`, `compileTestJava`, and `reobfJar` passed; no tests were executed and the artifact was not installed into an RDP instance.

## 2026-09-26 — Stable target-loss hold routing

- Prevented each target-loss grace tick from resetting the fixed-wing hold route's waypoint index and timer.
- Allowed a completed hold leg to renew, while preserving return and landing routes.
- On reacquisition, resumes from a parked hold entry and removes the hold-only loiter suffix before tracking/attack routing.
- `compileJava`, `compileTestJava`, and `reobfJar` passed; tests were not executed. The rebuilt artifact was not installed into an RDP instance.

## 2026-09-26 — Target-loss FSM rework

- Persisted the last authoritative target-observation tick in mission schema 5.
- Added a 40-tick contact grace that preserves target identity and mission state while replacing stale attack steering with a non-attacking hold route.
- Separated navigation stall from contact loss: a stalled route replans against the retained target; contact freshness alone advances the mission to `LOST` and `WAITING_FOR_TARGET`.
- Preserved the committed terminal target identity and bounded terminal timeout during temporary target unavailability.
- Updated the existing progress-policy contract; `compileJava`, `compileTestJava`, and `reobfJar` passed. No tests were executed and no JAR was deployed to the RDP instance.

## 2026-09-26 — Runtime-evidence fixes

- Added the current RDP `latest.log` findings, exact JAR identities, and an FSM/detonation architecture update to `UAV_AI_FRAMEWORK_REWORK_REPORT.md`.
- Reset the mission no-progress deadline only after the server accepts a replacement route, breaking the observed one-tick `SEARCHING <-> REPLAN` dispatch loop while preserving actual movement-based stall detection.
- Recorded transition reasons in the server lifecycle event stream.
- Changed entity-lock rendering to use the synchronized entity's interpolated 3D bounding box; retained projected markers for coordinate/point objectives.
- Added distance-versus-profile-range feedback for rejected entity-lock attempts.
- Replaced the broad kamikaze proximity AABB with a forward nose ray/sweep, rate-limited contact logs, and added an `EventPriority.HIGHEST` Forge fuse subscriber invoked by a transformer hook at the exact MCheliCE 1.5.1 pre-destruction method.
- Confirmed successful synchronous `MCH_Explosion` creation in mission state before the aircraft-loss scan. The event records `hitEntity` when supplied but does not infer that the intended target was hit or destroyed.
- Restored null-safe movement lease matching and added regressions for lease identity and route-progress baselines.
- Verification: `.\gradlew.bat test build --no-daemon` passed all 239 tests and produced the 568,498-byte reobfuscated artifact with SHA-256 `0333FE0BABB83B6CA197E61056AEA51A406DF3CDFC430981A0DE13E1EE276687`. No source JAR was installed into the active RDP instance; runtime flight, rendering, packet delivery, and fuse timing remain developer live-test items.

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
