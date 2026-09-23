# Phase C: Weapon and payload contract

Phase C makes guided payload support explicit and fail-closed.

## Contract

- A `GuidedWeaponCapability` declares the native behavior and supported
  objective types. `validateObjective` returns an operator-facing rejection
  reason instead of relying on a boolean alone.
- `GuidedWeaponRegistry` resolves exact definition/type pairs first. Native
  type fallback is deterministic by definition and type ordering.
- `tvmissile` remains unsupported for addon AI guidance. Legacy configuration
  cannot re-enable it because registry loading forcibly clears its behavior
  and objective set.
- Projectile disappearance is never treated as impact. It produces
  `OUTCOME_UNKNOWN`.
- Impact and effect confirmation are separate server-authoritative event
  contracts. `MissileGuidanceController.markImpactObserved` and
  `markEffectConfirmed` only record observations supplied by a native/event
  integration; they do not infer success or modify projectile physics.
- `MissionLifecycle` projects those observed outcomes to
  `IMPACT_OBSERVED` and `EFFECT_CONFIRMED`. Without an event, the mission
  remains tracked until it converges to `OUTCOME_UNKNOWN` or timeout.

## Runtime boundary

The repository does not currently prove a McHeli impact callback or effect
callback is wired to these methods. Therefore this phase does not claim live
impact, effect, or payload completion. A Forge/McHeli smoke test remains
required before those outcomes can be considered runtime-proven.
