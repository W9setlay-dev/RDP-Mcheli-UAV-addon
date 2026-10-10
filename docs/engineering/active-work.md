# RDP-UAV-Addon Active Work

## Master objective

Develop the existing RDP-UAV-Addon repository into a complete, integrated,
maintainable autonomous simulation framework operating inside Minecraft 1.12.2,
Forge 14.23.5.2864, and McHeliCE 1.5.1. This objective is permanent; phase
completion is not project completion.

The framework must connect operator/API input, validation, mission lifecycle,
decision and behavior logic, vehicle control, McHeliCE, authoritative state,
client synchronization, operator UI, telemetry, and tests wherever required by
the verified project specification.

## Baseline

- Repository branch: `main`, 33 commits ahead of `origin/main` at baseline.
- Existing documentation includes `docs/ai/03-implementation-roadmap.md`,
  `docs/current-architecture.md`, `docs/implementation-source-matrix.md`,
  `docs/runtime-test-matrix.md`, and the phase reports. No prior
  `docs/engineering/active-work.md` checkpoint was present.
- Working tree initially contained a user-owned one-line correction in
  `src/main/java/net/vas/rdp/uav/target/TargetLock.java`
  (`2package` to `package`). It was preserved and not staged.
- `.\gradlew.bat test`: **PASS** (Gradle 4.10.3, JUnit test task).
- Runtime flight, packet synchronization in a live client/server, and GUI
  behavior were not exercised. Do not infer runtime success from source or
  automated tests.

## Overall phase plan and dependencies

This plan follows the established roadmap in
`docs/ai/03-implementation-roadmap.md`; it extends the existing work rather
than restarting or replacing it.

| Phase | Scope | Depends on | Status at checkpoint |
| --- | --- | --- | --- |
| 0. Baseline and evidence map | Load specifications, map current implementations/tests, preserve an operational checkpoint | None | **Complete for initial session**; runtime evidence still absent |
| 1. Mission lifecycle hardening | Legal/idempotent transitions, terminal cleanup, assignment and queue reconciliation, failure reasons | Phase 0 | **In progress**; source trace and queued-restoration contract coverage added; broader assignment-restoration validation remains |
| 2. Target identity and tracking | Authoritative UUID lock, validity/reacquisition, stale/unloaded entity handling, shared HUD/mission identity | Phase 1 | Existing source and tests are documented; live continuity unverified |
| 3. Profiles and decision/execution contracts | Gate mission intent against vehicle capabilities; connect bounded decision proposals to executable mission behavior | Phase 1 | Profiles and tasking are documented; full extensible mission doctrine is not established |
| 4. McHeliCE integration and flight authority | One control owner, native adapter, bounded control, manual/supervised/recovery handoffs | Phases 1 and 3 | Source/tests present; latest lease-expiry/authority change is in `d2d5439`; physical behavior unverified |
| 5. Payload and mission outcomes | Validate native payload boundary, mission authorization, and observed outcomes | Phases 2, 3, and 4 | Existing adapters/tests are documented; live launch, impact, and effect unverified |
| 6. Swarm coordination | Membership, roles, allocation, reassignment, recovery, and mission ownership consistency | Phases 1, 2, and 3 | Existing source/tests are documented; live and save/reload behavior unverified |
| 7. Persistence, networking, and operator UI | Persist authoritative state; validate commands; synchronize projections; connect UI to backend | Phases 1-6 as applicable | Existing source/tests are documented; live client/server behavior unverified |
| 8. Telemetry, RDPCore, reliability, and performance | Failure observability, verified optional/core integration, bounded work, lifecycle cleanup | Phases 1-7 | RDPCore behavior and runtime/performance evidence require further verification |
| 9. Runtime acceptance and release gates | Exercise the integrated execution path in the target runtime; preserve evidence and unresolved gaps | Phases 1-8 | **Not verified**; requires runtime evidence from the configured Minecraft/McHeli environment |
| MV-01. Client pixel-perception foundation | Bounded capture of McHeli UAV-feed pixels, narrow detector, image-space tracking, diagnostics, and runtime evidence | Verified feed render path | **Implemented and unit-tested**; GL source binding, accuracy, and measured in-game behavior remain unverified |

Dependencies are directional: phase 1 is a prerequisite for authoritative
execution; phases 2-4 establish target, capability, and control contracts;
phases 5-7 consume those contracts; phases 8-9 verify and harden the integrated
system. A phase may be revisited when evidence reveals a defect.

## Capability status

These are baseline classifications, not claims that every documented feature
has been independently re-audited in this session.

| Capability | Evidence-based status |
| --- | --- |
| Mission management | Source, tests, and lifecycle/persistence reports exist; this session's full test task passes. Runtime state transitions remain unverified. |
| Decision-making and autonomous behavior | Mission planning/tasking and flight execution components are documented. The source matrix identifies broader dynamic mission doctrine as not yet implemented end-to-end. |
| McHeliCE integration and vehicle control | APIs are documented as source/bytecode-verified in project notes; current physical flight quality and tick ordering remain runtime-unverified. |
| Target identity and lock | Server-owned UUID identity and supporting source/tests are documented; live invalidation/reacquisition is unverified. |
| Networking and authority | Server-authoritative paths and contract tests are documented; live replay, synchronization, and multi-client behavior are unverified. |
| Operator UI | GUI and HUD paths are documented; in-game interaction, scaling, and synchronized behavior are unverified. |
| Client pixel perception | Client-only bounded feed capture, tinted-beam feature scoring, image-space tracking, and optional diagnostics are implemented and covered by automated tests; runtime framebuffer binding, accuracy, and performance are unverified. |
| MV presentation | Normal/Assist/Diagnostics presentation, client F7 mode cycling, and snapshot-derived image-space markers in the verified ModularUI feed area are implemented and unit-tested; live alignment, GL behavior, and performance remain unverified. |
| Telemetry and observability | Diagnostic source and reports exist; runtime diagnostic completeness is not established here. |
| RDPCore integration | **UNKNOWN** at this baseline: no integration behavior was independently confirmed in this session. |
| Performance and reliability | Source and regression tests exist; runtime performance and complete world/entity lifecycle coverage are not established here. |

## Current phase and slice

- Current master roadmap phase: **Phase 1 — Mission lifecycle hardening**;
  no Phase 1 completion is implied by the separate machine-vision slice.
- Current focused work: **MV-01 — Client pixel-perception foundation**.
- Completed slice: added a focused regression contract in
  `src/test/java/net/vas/rdp/uav/persistence/RDPMissionWorldDataTest.java`.
  It verifies that `RDPMissionWorldData.restoreQueued` restores persisted
  queued missions exactly once and excludes an active non-queued mission.
- Source trace: `MissionManager` reconciles stale assignments and restores
  nonterminal assignment reservations; `MissionLifecycle` only dispatches
  `PLANNED` queued missions and repairs stale queue flags; `MissionNbtCodec`
  rejects terminal records marked queued. These source paths are not a claim
  of live world-reload correctness.
- The focused test and full `.\gradlew.bat test` task passed after this slice.
- The recent control-lease change in commit `d2d5439` is existing repository
  work, not a change made by this checkpoint. The pre-existing
  `TargetLock.java` edit remains user-owned.
- No production mission behavior was changed in the original lifecycle slice.
- MV-01 adds client-only feed-frame capture, a pixel detector, bounded tracking,
  and optional diagnostics. This is not server-authoritative perception and is
  not connected to mission targeting or execution.
- MV-01 verification: `.\gradlew.bat build` **PASS**, including compilation,
  the full JUnit suite, jar assembly, and reobfuscation. The detector/tracker
  and worker tests use synthetic frames; no in-game frame, accuracy rate, or
  runtime frame-time measurement was collected.
- MV-PRESENTATION-01 adds a client-only `NORMAL` / `ASSIST` / `DIAGNOSTICS`
  selector cycled by the rebindable F7 key. Assist maps actual normalized
  snapshot tracks into the McHeli UAV feed widget's live ModularUI area, marks
  stale tracks distinctly, hides lost locations, and suppresses snapshots older
  than two seconds. The coordinate mapping is verified against local McHeliCE
  bytecode and ModularUI 3.0.5 source; the callback draws after feed widgets and
  therefore does not alter detector input. Automated tests cover mapping,
  resizing, modes, and freshness policy. In-game alignment, GL behavior, and
  render-time impact remain unverified.

### MV-PRESENTATION-01 manual acceptance

1. Build with `.\gradlew.bat build`; record `git rev-parse HEAD` and the
   SHA-256 of `build\libs\rdp-uav-addon-0.1.0.jar`.
2. Launch Minecraft 1.12.2 with the configured Forge 14.23.5.2859, McHeliCE
   1.5.1, and ModularUI runtime. Set `perception.enabled=true` and
   `perception.overlayEnabled=true` in `config\rdp-uav-vision.cfg`; start with
   a visible magenta-tinted beacon in the UAV feed.
3. Open the UAV station feed. Cycle F7 through Normal, Assist, and Diagnostics.
   Normal must show no vision graphics; Assist boxes/labels must align with
   feed detections after resizing and GUI-scale changes; Diagnostics must show
   current state/age/capture/dropped-frame metrics. Confirm no detected marker
   appears outside the feed area, no lost track retains an old marker, and no
   result older than two seconds remains drawn.
4. Close the feed and exercise world unload/reopen. Capture `latest.log`, the
   config, screenshots of each mode and resized layout, build identifier/hash,
   and frame-time/FPS observations with presentation Normal versus Assist.
   Record any GL error/crash, absent/misaligned overlay, stale marker, or
   sustained frame-time change. Runtime status remains **NOT VERIFIED** until
   the operator evidence is reviewed.

## Known defects, questions, and blockers

- **Runtime blocker / validation gap:** flight authority and physical aircraft
  response have not been validated in a live Forge/McHeliCE instance. Do not
  treat unit tests or the latest lease fix as proof the aircraft moves.
- **Scope gap:** the source matrix explicitly says full dynamic mission
  doctrine for hostile vehicles/anomalies is not yet an end-to-end mission
  capability. Confirm exact requirements against the original repository
  specification before implementing doctrine.
- **Unknown:** actual RDPCore API/lifecycle integration and its required
  behavior have not been verified during this baseline.
- **Compatibility note:** `build.gradle` currently configures Forge
  `1.12.2-14.23.5.2859`; the stated target in the project contract is
  `14.23.5.2864`. Do not change this version without checking wrapper/dependency
  compatibility and confirming that the repository's intended target is 2864.
- **Git state:** preserve all existing user work; do not stage unrelated files,
  create a commit, or push without an explicit task-scoped authorization.

## Next exact implementation action

Next MV-01 action: run the documented in-game procedure and capture the
requested logs, screenshot, build hash, and before/after frame-time evidence.
The code/build/test gate is complete; framebuffer binding, detector accuracy,
and runtime performance remain unverified until those artifacts exist. Then
resume the Phase 1 mission-restoration regression below; machine-vision work
does not replace or complete it.

Acceptance for that slice:

1. MV-01 client capture remains bounded, optional, pixel-only, and isolated
   from server authority; its code compiles and automated tests pass.
2. GL render-state restoration, source/world resets, and capture/worker failure
   reporting are reviewed; runtime-only claims remain unverified.
3. The machine-vision design page contains the architecture, limits, current
   validation status, and an exact manual evidence procedure.
4. Then the assignment-restoration conflict is exercised through the actual
   manager and world-data path, preserving the winner's reservation while the
   conflict mission fails explicitly; do not add a test seam just to avoid
   verifying the actual 1.12.2 `World`/`MapStorage` API.
5. Review `git diff --check` and update this checkpoint with verified evidence
   only. Preserve the pre-existing `TargetLock.java` change.
