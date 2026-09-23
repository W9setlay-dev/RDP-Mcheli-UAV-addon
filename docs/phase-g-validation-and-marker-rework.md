# Phase G: Validation and marker rework

This phase tightens three production boundaries:

- Square-strike mission definitions validate numeric, finite coordinates before
  the client submits them. The server repeats finite-value, range, size, and
  profile checks; malformed profile metadata is rejected explicitly rather
  than relying on Java assertions.
- Malformed selected-aircraft UUIDs in a persisted `MissionDefinition` now
  fail decoding instead of being silently discarded.
- The target HUD uses Minecraft's active render-view entity, rejects
  cross-dimension telemetry, and suppresses a marker when a loaded block
  occludes the server-designated position. The client remains presentation
  only; the server telemetry position and dimension remain authoritative.
- One-way square-strike missions now invoke the existing bounded
  `SquareStrikePlan` through `WingManFlightAdapter.armSquareStrike`; reusable
  square strikes continue through the verified guided-weapon path and are
  rejected when that path is unavailable.

The validation, persistence, and square-plan behavior is covered by
deterministic tests. The marker path is source/build validated only; no
Forge/McHeli runtime smoke test was performed in this phase.

## Plan validation result

The phase plan is now internally consistent for source-level behavior:

- lifecycle loss outcomes do not claim unobserved detonation;
- square-strike route construction is connected to the production lifecycle;
- reusable square strikes require the guided-weapon path;
- one-way square strikes require the configured terminal payload path;
- point and square targets remain distinct coordinate objectives;
- client marker rendering does not become targeting authority.

The plan is **not** complete for runtime acceptance. Packet delivery, native
WingMan movement, collision ordering, projectile/effect callbacks, GUI
rendering at supported scales, and restart behavior still require an isolated
Forge/McHeli smoke test.
