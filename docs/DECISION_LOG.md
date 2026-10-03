# Architecture decision log

This log records the user-approved architecture choices for the master
autonomous-UAV redesign prompt. The cumulative evidence and phase ledger remain
in [UAV_AI_FRAMEWORK_REWORK_REPORT.md](UAV_AI_FRAMEWORK_REWORK_REPORT.md);
runtime cases remain in [runtime-test-matrix.md](runtime-test-matrix.md).
Older decisions D-001 through D-009 are recorded in the dated phase ledgers in
the cumulative report and are not reinterpreted here.

## D-010 — Forge 2864 build-path investigation

- **Date:** 2026-10-02
- **Decision:** Option B approved: investigate the exact Forge
  `1.12.2-14.23.5.2864` toolchain in an isolated copy.
- **Reason:** The repository's ForgeGradle 2.3 path requests the legacy
  `userdev` classifier, while the exact Forge artifacts available here include
  `userdev3`. The main checkout also contains pre-existing local changes that
  must be preserved.
- **Alternatives considered:** Keep validating only against configured Forge
  2768; switch the main build immediately; test candidate compatibility outside
  the repository first.
- **User approval:** Approved by the user's continuation instruction and the
  explicit D-010 choice.
- **Affected systems:** isolated Gradle/Forge compatibility probe and build
  validation records. No main build-plugin or Forge-version selection is
  implied by the isolated probe.
- **Outcome:** ForgeGradle 2.3 fails at `extractUserdev` for the missing legacy
  classifier. ForgeGradle 3.0.197 compiles and packages the copied sources
  against Forge 2864 with `build -x test`; the full test run still has seven
  Forge `NetworkRegistry` initialization failures. See the 2026-10-02 entry in
  `BUILD_VALIDATION_REPORT.md`.

## D-011 — deterministic mission decision structure

- **Date:** 2026-10-02
- **Decision:** Retain `MissionLifecycle` as the deterministic execution state
  machine and evolve explicit decision/policy inputs around it. Do not add a
  behavior-tree framework at this stage.
- **Reason:** Existing mission state, recovery, and execution paths already use
  a deterministic lifecycle. Replacing or layering another executor would add
  competing state ownership before a concrete behavior requirement is
  established.
- **Alternatives considered:** Keep the current lifecycle and make policies
  explicit; add a hybrid behavior tree; replace lifecycle execution with a
  behavior tree.
- **User approval:** The user delegated the recommendation choice; option A was
  selected.
- **Affected systems:** mission lifecycle, task/decision policy, recovery, and
  diagnostics.

## D-012 — UAV profile model

- **Date:** 2026-10-02
- **Decision:** Evolve the existing `UavVehicleProfile` compositionally with
  capabilities and constraints. Do not introduce a parallel profile class
  hierarchy.
- **Reason:** Profiles and derived `VehicleCapability` values already exist;
  another hierarchy would duplicate definitions and make runtime capability
  selection harder to reconcile.
- **Alternatives considered:** Extend the existing profile; add separate
  fixed-wing/rotary/mission profile subclasses.
- **User approval:** Option A approved.
- **Affected systems:** profile definitions, aircraft capability selection,
  mission validation, flight adapters, and configuration.

## D-013 — observation and track authority

- **Date:** 2026-10-02
- **Decision:** Keep world observation and authoritative target tracking on the
  server behind the profile-bounded perception adapter. Client camera/UI data
  can submit a selection intent and render synchronized state; it cannot create
  or own the AI track.
- **Reason:** This preserves the existing UUID validation and server-side
  ownership boundary while giving sensors a bounded input contract.
- **Alternatives considered:** Server-only observation through the adapter;
  client-provided observations as inputs to an authoritative fused track.
- **User approval:** Option A approved (the duplicated D-013 label in the
  response referred to the same recommendation).
- **Affected systems:** world perception, contact tracking, target lock,
  network validation, and HUD projection.

## D-014 — swarm mission operator surface and authority

- **Date:** 2026-10-02
- **Decision:** Provide swarm coordination and mission configuration through a
  dedicated mission-GUI page. The GUI submits validated intent and displays
  server state; the server owns swarm records, membership authorization,
  configuration validation, mission fan-out, task assignment, execution, and
  telemetry. The page must configure self-autonomous swarm mission execution.
- **Reason:** A single operator surface makes swarm setup and monitoring
  discoverable while preserving authoritative tick-driven execution and the
  separation between UI state and AI state.
- **Alternatives considered:** Reuse only chat commands; let the GUI maintain
  coordinator state; use the dedicated page as a client front end to the
  existing server coordinator.
- **User approval:** User-directed continuation: coordination is provided by
  the specific mission GUI page and should enable fully configurable,
  self-autonomous swarm mission execution. The server-authority interpretation
  follows the previously recommended option A and the project's client/server
  ownership requirements.
- **Affected systems:** `GuiMissionControl`/`MissionGuiTab`, mission definition
  transport, server network handlers, `SwarmManager`, `SwarmRecord`, member
  missions, persistence, and telemetry.
- **Implementation checkpoint:** Dedicated page and server-authoritative
  launch/configuration paths implemented. Formation offsets, configured search
  routes, duplicate-target policy, leader failover, shared target selection,
  and patrol route fan-out are connected to the existing mission lifecycle.
  See the dated verification checkpoint in
  `docs/UAV_AI_FRAMEWORK_REWORK_REPORT.md`; live Forge/McHeli behavior is still
  unverified.

## D-015 — fixed-wing and VTOL controller scope

- **Date:** 2026-10-03
- **Status:** Pending user decision before flight-control implementation.
- **Question:** How should Phase 8 handle McHeli planes whose native plane
  definition enables VTOL?
- **Verified evidence:** `MCH_EntityPlane` extends `MCH_EntityAircraft` and
  exposes `canSwitchVtol()`, `getVtolMode()`, `swithVtolMode(boolean)`, and
  `getPlaneInfo()`. `MCH_PlaneInfo` exposes VTOL enable/default and axis
  parameters. The current addon selects controllers by execution profile and
  native helicopter class; it does not separately inspect or transition plane
  VTOL mode. The gameplay server's exact vehicle definitions are unavailable.
- **Option A (recommended for the first fixed-wing pass):** Support only
  fixed-wing aircraft with VTOL disabled. Reject VTOL-enabled plane
  definitions from autonomous missions until transition and hover behavior
  have a dedicated design and runtime validation.
- **Option B:** Add an explicit VTOL-aware mode/controller handoff using the
  verified McHeli API, including server-side transition conditions, mission
  phase policy, and safe failure handling. This expands flight-control scope
  and cannot be trajectory-verified in the current environment.
- **Reason for recommendation:** The API exists, but the loaded vehicle
  definitions and transition timing are unknown. A bounded fixed-wing support
  surface avoids treating native VTOL controls as equivalent to a verified
  rotary-wing controller.
- **Affected systems:** `UavVehicleProfile`, `VehicleCapability`,
  `AirframeController`, `WingManFlightAdapter`, `NativeAircraftControlAdapter`,
  and aircraft mission validation.
