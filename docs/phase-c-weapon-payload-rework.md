# Phase C: Weapon and payload contract

Phase C makes guided payload support explicit and fail-closed.

## Contract

- A `GuidedWeaponCapability` declares the native behavior and supported
  objective types. `validateObjective` returns an operator-facing rejection
  reason instead of relying on a boolean alone.
- `GuidedWeaponRegistry` resolves exact definition/type pairs first. Native
  type fallback is deterministic by definition and type ordering.
- `tvmissile` now resolves to `COORDINATE_TV_LASER` for entity and square
  objectives. Legacy `UNSUPPORTED` configuration values are normalized to the
  native TV contract. A transformer hook applies the server mission target at
  `MCH_EntityTvMissile.onUpdateMotion()` before McHeli's camera/wire update and
  then delegates steering to `onLaserGuide()`. Exact JAR hook shape is tested;
  in-game trajectory and impact remain unverified.
- Projectile disappearance is never treated as impact. It produces
  `OUTCOME_UNKNOWN`.
- Impact and effect confirmation are separate server-authoritative event
  contracts. `MissileGuidanceController.markImpactObserved` and
  `markEffectConfirmed` only record observations supplied by a native/event
  integration; they do not infer success or modify projectile physics.
- Native self-destruct fire acceptance is also separate from detonation. The
  mission persists `kamikazePayloadLaunched` and prevents duplicate firing,
  while `KAMIKADZE_DETONATED` remains reserved for an explicit observed effect.
- `MissionLifecycle` projects those observed outcomes to
  `IMPACT_OBSERVED` and `EFFECT_CONFIRMED`. Without an event, the mission
  remains tracked until it converges to `OUTCOME_UNKNOWN` or timeout.

## Runtime boundary

The captured CRM runtime log proves native WingMan route acceptance and a
`FLY_TO reached` report, but it showed no `IMPACT_OBSERVED` or
`EFFECT_CONFIRMED` event for the kamikaze path. It therefore does not prove
impact, effect, or payload completion. A fresh Forge/McHeli smoke test with an
observable native effect callback remains required before those outcomes can be
considered runtime-proven.
