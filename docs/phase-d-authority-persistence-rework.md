# Phase D: Authority and persistence reconciliation

Phase D keeps operator actions server-authoritative and makes restored mission
ownership converge without duplicate launches.

## Changes

- Mission queue restoration now recognizes membership correctly. Queue
  insertion is idempotent, so repeated world-load hooks cannot dispatch the
  same mission twice.
- Restored queued missions are now actually re-enqueued after world reload;
  the restoration guard rejects only missions already present in memory.
- Active persisted missions re-claim their durable target UUID during manager
  restoration. A conflicting target claim fails explicitly with
  `TARGET_RESTORE_CONFLICT` instead of allowing two missions to act on one
  target.
- All terminal states, including `AIRCRAFT_LOST`, are excluded from restored
  assignment reservations.
- Clearing missions aborts and releases them but retains terminal records,
  transition reasons, and failure/audit state in `WorldSavedData`.
- Existing packet handlers continue to receive client intent only; mission
  lookup, owner checks, aircraft control checks, capability checks, and state
  transitions execute on the server thread.

## Runtime boundary

This phase proves source-level and test-level reconciliation only. It does not
prove a live Forge server reload, packet replay, or multi-client race. Those
remain part of the dedicated runtime smoke validation.
