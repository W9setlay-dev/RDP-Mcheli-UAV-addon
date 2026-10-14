# Machine-vision architecture

## Ownership

The pixel pipeline is a local client observation and presentation feature.
The server remains authoritative for mission state, target identity, scan
reports, and aircraft behavior. Client detections never create or change a
mission observation, lock, or report.

## Data flow

```text
McHeli UAV feed render
    -> RenderWorldLastEvent while RENDERING_FEED
    -> downscale framebuffer + asynchronous PBO readback
    -> owned VisualFrame (sequence, camera, source, capture time)
    -> bounded single-worker queue
    -> detector + image-space tracker
    -> immutable PerceptionSnapshot
    -> Assist widget callback and/or Diagnostics HUD
```

The mission monitor reads a separate local status snapshot and places it
beside the currently displayed server telemetry. This is a presentation-only
context link; there is no network message or server persistence for pixel
results.

## Server-authoritative mission observation

Mission target qualification is a separate server-side path. The server checks
that the observer and target share the live world and dimension, applies the
configured range and field-of-view cone (90 degrees by default, using the
aircraft look vector), verifies the sightline is loaded, and rejects an
obstructed block ray. Client pixel detections do not satisfy these checks and
are never sent as mission evidence. A persisted target must be observed again
by the server before it can be used as a current target.

The existing target-telemetry packet carries the server contact's visibility
and lifecycle. The native mission monitor renders that as `FRESH`, `STALE`,
`POINT`, or `NONE`; mission rows separately show when strike authorization is
required or has been granted. The client remains presentation-only.

The aircraft look vector is not verified as the McHeli camera/gimbal direction;
the field-of-view model is therefore a server-side gameplay constraint, not a
claim of calibrated camera geometry. Runtime calibration remains outstanding.

## Observation-session stages

Each active feed interval receives a random, client-local session identifier.
Frame sequence values are monotonic for the lifetime of the capture instance.
The immutable status view reports:

| Field | Meaning |
|---|---|
| `capturedSequence` | Latest frame whose readback command and fence were issued |
| `processedSequence` | Latest matching-camera result published by the worker |
| `presentedSequence` | Latest fresh processed result rendered by Assist or Diagnostics |
| `stage` | Feed/capture/process/presentation status, status-only render, failure, or stop |
| `pipelineState` / `reason` | Current worker state and its diagnostic explanation |

A sequence of `-1` means that stage has not completed in this session. A
status-only panel is not represented as a presented result. Results from a
different camera or a sequence that was not captured in the current session
are rejected from the status link.

## Failure and observability

Capture errors continue to set the worker's explicit `UNAVAILABLE` state;
detector exceptions set `DEGRADED`. The stage view and Diagnostics HUD expose
those states and their reason. When debug logging is enabled, stable
`[DEBUG][Vision] event=...` records correlate the abbreviated session ID and
frame sequence. Frame-stage logs are emitted for the first event and each
25th sequence, rather than once per captured frame. Session start/stop and
failure events are not sampled.

Diagnostics mode draws on the global HUD after normal render events. Assist
draws on the active feed widget after its UI render callback. F7 cycles
presentation only. `perception.enabled` gates the capture pipeline and
`perception.overlayEnabled` independently gates Diagnostics.

## Lifecycle

- The feed render event starts a local observation session and records a
  successful PBO/fence submission as captured.
- Client ticks observe published worker snapshots.
- Assist/Diagnostics rendering records a presented sequence only when it is
  the current processed operational result; expired/no-result status panels
  are recorded as status-only.
- Config disable, feed timeout, and client-world unload stop the local session
  and clear worker/GPU state through the existing client-thread cleanup path.

## Runtime contract

The current implementation detects one narrow image feature: tall, saturated
magenta pixel runs. It does not perform semantic scene understanding, entity
identity, world-coordinate localization, or authoritative sensing. Synthetic
pixel fixtures test the algorithm; game imagery and the complete render path
remain runtime-validation requirements.

## Linkage and shader diagnostics

The feed-widget lookup in the project source uses explicit widget-tree
traversal; it does not call the incompatible `WidgetTree.findFirst` return
descriptor found in the older installed addon artifact. The supplied crash
stack identifies that linkage error as the client failure.

The runtime-matching McHeliCE 1.5.1 JAR contains `nightvision` and `thermal`
post-processing shaders whose JSON declares `InSize`, while their vertex
shaders route that uniform only into a `oneTexel` varying not consumed by the
fragment stage. That can let the shader linker remove the uniform and fits the
`ShaderManager` warning. The installed SRParasites archive also contains a
post-process shader declaring `InSize`, so this is a plausible source rather
than a verified runtime attribution; the generic warning names no program.
The warning is not evidence that the RDP vision overlay caused the separate
`WidgetTree.findFirst` crash.
