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
| Telemetry and observability | Diagnostic source and reports exist; runtime diagnostic completeness is not established here. |
| RDPCore integration | **UNKNOWN** at this baseline: no integration behavior was independently confirmed in this session. |
| Performance and reliability | Source and regression tests exist; runtime performance and complete world/entity lifecycle coverage are not established here. |

## Current phase and slice

- Current phase: **Phase 1 — Mission lifecycle hardening**.
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
- No production mission behavior was changed in this slice.

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

Continue Phase 1 with a focused regression for
`MissionManager.restoreAssignments`: restore two nonterminal missions claiming
the same aircraft and prove the conflict mission becomes `FAILED` with
`AIRCRAFT_RESTORE_CONFLICT` while the first mission retains the aircraft
reservation. Verify the real 1.12.2 `World`/`MapStorage` test construction
before writing the fixture; do not add a production test seam solely to avoid
that API. If the existing behavior fails, patch only the first broken
production boundary. Avoid duplicating reconciliation behavior already
described in `docs/phase-d-authority-persistence-rework.md`.

Acceptance for that slice:

1. The restoration conflict and first broken layer are exercised through the
   actual manager and world-data path.
2. The first mission's reservation remains intact and the conflicting mission
   has an explicit failure reason without releasing the winner's assignment.
3. A focused regression test covers any defect found and the existing test
   suite remains green.
4. The diff and `git diff --check` are reviewed; the checkpoint and defect
   status are updated with only verified claims.
5. Runtime-only claims remain explicitly unverified until runtime evidence is
   supplied or collected.
