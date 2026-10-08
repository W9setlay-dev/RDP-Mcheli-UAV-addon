# Phase E: Operator UI and HUD alignment

Phase E makes client presentation reflect the server-owned mission lifecycle.

## Contract

- Client controls remain intent requests. Button enablement is only a
  presentation aid; every packet is still validated on the server.
- Terminal missions and `OUTCOME_UNKNOWN` missions are not presented as
  actionable.
- The `ACTIVE` filter excludes terminal and unknown-outcome records.
- Explicitly unsupported weapon capabilities are not offered as guided
  controls.
- HUD telemetry may continue to show a terminal result, but target markers are
  hidden after completion, abort, failure, or unknown outcome.
- Target identity remains the server-provided UUID; the client does not
  reacquire or replace targets.
- Entity reconnaissance/attack creation now uses the versioned mission
  definition packet. The older creation packet remains available for
  compatibility, but is no longer a competing GUI path.
- GUI labels distinguish `Abort mission` from `Archive selected/all`; archive
  releases active resources but retains the server audit record.
- Mission lists are owner-filtered on the server and sent in bounded
  11-record pages. The page request is presentation intent only; the server
  clamps it to the current result set and returns the authoritative page count.
  This keeps the packet decoder bound aligned with the server output without
  dropping older mission history.

## Runtime boundary

This phase validates state presentation and build behavior only. It does not
claim client/server packet race validation or live visual verification in a
Forge client.
