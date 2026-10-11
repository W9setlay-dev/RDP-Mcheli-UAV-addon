# Machine-vision execution plan

## Objective

Make client vision execution observable from feed session through overlay
presentation, and expose its local status beside mission telemetry without
changing server authority or persisting client observations.

## Execution

1. Preserve the established McHeli framebuffer source, bounded readback, worker,
   detector, tracker, and rendering architecture.
2. Record a client-local session ID and captured/processed/presented sequences.
3. Surface that status in Diagnostics and the mission monitor with an explicit
   context-only/non-authoritative label.
4. Add debug-gated, sampled lifecycle events and tests for stage transitions,
   stale/mismatched results, and session termination.
5. Update architecture, audit, operator documentation, and runtime test
   procedure.
6. Run fresh focused tests, full tests/build, diff checks, and inspect the
   local launch evidence. Keep live Minecraft acceptance separate.

## Out of scope

- Mission dispatch, movement/control authority, control leases, and mission
  lifecycle changes.
- Sending pixels or detections over the network or incorporating them into
  authoritative target/scan/report state.
- Replacing the existing narrow detector with general machine vision.
- Claiming live framebuffer, detection, overlay, or performance success without
  supplied runtime evidence.

## Acceptance

- Each displayed session identifies capture, processing, and presentation
  sequence independently; `-1` means no completed stage.
- Results are associated only with a matching camera and captured sequence.
- Mission UI identifies the status as local context, not server telemetry or
  mission evidence.
- Tests and builds report actual execution results; manual acceptance remains
  pending until a matching runtime log and screenshot are reviewed.
