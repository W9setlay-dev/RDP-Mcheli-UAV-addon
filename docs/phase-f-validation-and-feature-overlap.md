# Phase F: Validation and feature-overlap decisions

## GUI overlap audit

| Surface | Finding | Decision |
| --- | --- | --- |
| Entity Recon/Attack and Versioned Launch | Two GUI creation paths expressed the same mission intent. | Merged GUI creation into `LaunchMissionDefinition`; retained the legacy packet for compatibility. |
| Entity Lock and Threat Suggestions | Both select a target, but one sends an entity UUID and the other sends a registry assignment. | Kept both because they have different authoritative inputs; labels and server validation remain distinct. |
| Abort and Clear | Both can stop an active mission, but clear also releases resources and previously looked destructive. | Kept both; renamed clear to Archive and documented audit retention. |
| Clear lock and Take player control | Same server request in different modes. | Kept one semantic action and renamed it `Release AI control` in guided mode. |
| Square Strike and Point | Different payload intent: square strike is an attack objective; point is a non-attack point task. | Kept separate. |
| Guided weapon adoption and reassignment | Adoption attaches an untracked native projectile; reassignment changes an existing objective. | Kept separate. |

## Validation status

- Source review: complete.
- Focused tests: passed.
- Full Gradle test suite: passed.
- Full Gradle build: passed.
- Diff whitespace check: passed.
- Forge/McHeli client/server runtime smoke test: not attempted.

The remaining runtime work must verify packet delivery, GUI rendering,
server-side rejection, mission reload, and physical/native projectile
observation in an isolated instance. Compile and unit results do not prove
those behaviors.
