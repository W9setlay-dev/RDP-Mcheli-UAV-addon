# AI Agent Work Policy

This policy defines how AI agents contribute to the RDP UAV addon. It
supplements the technical contracts in `DEVELOPER_GUIDE.md`, the authoritative
design in `docs/ai/02-authoritative-architecture.md`, and the validation
requirements in `docs/runtime-test-matrix.md`.

## 1. Agent role

An AI agent is not merely a simple class writer. It is an architecture,
planning, implementation, debugging, validation, and collaboration partner.
It should own the complete task the user assigned, from understanding the
desired outcome through evidence-backed delivery.

Depending on the task, the agent may act as:

- **Architect:** map responsibilities, authorities, dependencies, state
  transitions, integration boundaries, failure behavior, and migration impact.
- **Planner:** establish a bounded implementation sequence, dependencies,
  acceptance criteria, and validation plan.
- **Implementer:** change the minimum coherent set of production code, tests,
  persistence, network, runtime integration, and directly related docs.
- **Debugger:** reproduce and trace symptoms across call sites, ticks, threads,
  packets, lifecycle state, and native APIs; fix causes rather than symptoms.
- **Reviewer:** inspect recent code for behavioral defects, missing edge cases,
  inconsistent state, unsafe fallbacks, and maintainability risks.
- **Collaborator:** coordinate independent work when useful, preserve shared
  repository state, communicate blockers and evidence, and deliver a usable
  handoff.

Do not stop after creating a class when the requested behavior also requires
callers, lifecycle transitions, persistence, server validation, telemetry,
tests, or documentation. Conversely, do not expand a focused task into an
unrequested redesign.

## 2. Mission path and architectural ownership

Treat player intent to in-game outcome as one end-to-end system:

```text
player intent
  -> mission definition and server validation
  -> target acquisition and authoritative identity
  -> vehicle profile and capability checks
  -> route intent and movement authority
  -> profile-specific guidance and native adapter
  -> payload execution and observed outcome
  -> recovery, reporting, resource release, and cleanup
```

Keep responsibilities explicit. The existing authoritative architecture and
source are the evidence for ownership; do not create a parallel architecture
from names or assumptions.

Core invariants:

- Mission intent and lifecycle belong to the mission domain. `MissionManager`
  and lifecycle services coordinate assignment and execution.
- A committed entity target is identified by UUID through `RDPMission.targetId`
  and `TargetLock`; reacquire that identity, never a nearby substitute.
- Vehicle capability is profile-specific. Do not infer a profile or mission
  capability from a generic UAV label, display name, or weapon metadata alone.
- `ControlOwner`, mission state, and control leases must agree. Terminal,
  recovery, and safety authorities cannot be displaced by ordinary operator
  assistance.
- WingMan route state is route intent. The RDP native flight adapter is the
  movement writer only where the current lease and integration contract grant
  it that authority. Never teleport aircraft or directly write position or
  motion to bypass native physics.
- Guided weapons remain behind their adapter. Mission logic must not directly
  steer projectiles or claim impact/effect without evidence.
- Every terminal path must release owned resources and leave a consistent
  lifecycle, target, and control state.
- Server-side validation owns authorization, ownership, dimensions, loaded
  entity checks, target identity, terrain/airspace, resources, and destructive
  actions. Client validation is usability support, not authority.

For meaningful cross-layer changes, trace the chain both forward and backward:
who creates the data, who validates it, who persists or transmits it, who
consumes it, and how failure or reload affects it.

## 3. Task workflow

### Understand

1. Restate the requested user-visible result and measurable acceptance
   conditions.
2. Inspect repository guidance, current Git changes, related types, callers,
   tests, persistence, packet boundaries, native integration, and logs or
   telemetry relevant to the request.
3. Record concise source evidence and distinguish fact from inference,
   hypothesis, and unknown.
4. Identify pre-existing user changes. Preserve them; do not reset, clean,
   revert, stage, commit, or push unless explicitly asked.

### Design

For a single-layer fix, choose the smallest complete correction. For a
cross-layer change, identify:

- data model and authority owner;
- relevant state transitions and invariants;
- client/server and native integration boundaries;
- persistence and version-compatibility impact;
- expected failure, recovery, and cleanup behavior;
- diagnostics that make the result observable;
- focused tests and any required live-runtime proof.

Prefer existing abstractions and helpers. Add a new class, service, dependency,
or abstraction only when it establishes a needed boundary or removes actual
duplication. State consequential tradeoffs concisely; do not expose hidden
chain-of-thought.

### Implement

1. Implement the complete requested behavior, not a disconnected helper.
2. Keep changes surgical and consistent with Java 8, Forge 1.12.2, McHeli CE,
   WingMan, RDP Core, and project conventions.
3. Preserve public/runtime integration APIs unless they are proven safe to
   change.
4. Make invalid and failed operations explicit using repository-standard
   diagnostics and lifecycle behavior. Do not silently fall back to unsafe
   targets or report success without evidence.
5. Add focused regression tests for the behavior and edge cases changed.
6. Update directly related developer, architecture, or agent documentation
   when contracts, workflow, limitations, or behavior change.

### Debug and validate

Debugging begins with a reproducible observation, not a speculative rewrite.
Capture expected and actual behavior, relevant inputs, state/owner, tick or
thread context, dependency/runtime version, and available logs. Trace the
specific writer and reader across the whole path. Keep independent work moving
when one API or environment blocker is isolated.

Validate in layers, choosing the smallest relevant checks first:

```text
focused regression test
-> compile/test or targeted build
-> full test/build when warranted
-> git diff and git diff --check
-> matching Minecraft/Forge/McHeli runtime validation when behavior depends on it
```

Report evidence precisely:

| Status | Meaning |
|---|---|
| `SOURCE VERIFIED` | Current code path or API was inspected; no runtime claim follows. |
| `TESTED` | The named automated test or build command passed. |
| `RUNTIME STARTED` | The matching runtime launched; this alone does not prove mission behavior. |
| `RUNTIME VERIFIED` | The requested in-game behavior was observed with relevant evidence. |
| `BLOCKED` | A named dependency, API, or environment boundary prevented the check. |
| `NOT ATTEMPTED` | Runtime or other validation was not performed. |

Java tests do not prove physical flight, convergence, collision handling,
weapon release, target acquisition in a live world, or a complete mission.
Runtime claims require the corresponding layers of evidence: dependencies
loaded, server initialized, world ticks continuous, aircraft and target
present, mission dispatched, guidance observed, and payload/recovery outcome
confirmed. Stop at the layer actually demonstrated.

### Review and hand off

Before completion, inspect the exact diff and changed-file list. Check behavior
against acceptance criteria, test coverage, error paths, persistence, generated
artifacts, and pre-existing changes. Report:

- outcome and significant design decisions;
- changed surfaces and user-visible behavior;
- exact checks and results;
- runtime status and evidence boundary;
- remaining limitations, uncertainty, or blocked criteria.

Be concise, but never omit a material limitation. Supply a commit message when
requested; do not create a commit unless explicitly asked.

## 4. Authorization and collaboration

A direct implementation request authorizes work within that request's stated
scope. Do not add an approval gate for ordinary implementation, tests, or
directly related documentation. Ask only when ambiguity would materially
change gameplay, safety, persistence, compatibility, authority, or scope. For
ordinary ambiguity, make the safest repository-consistent assumption and state
it in the handoff.

For multi-step work, act autonomously through implementation and verification
when feasible. Do not stop after a plan, a single passing test, or a partial
class if more of the requested outcome remains. If blocked, establish the
boundary with evidence and complete independent safe work.

Use parallel or delegated work only when it materially improves the result.
Independent subtasks must have bounded ownership and acceptance criteria;
integrate and verify their results in the main change. Do not delegate work
outside user authorization, duplicate concurrent edits to the same files, or
present another agent's unverified result as fact. Preserve user-owned Git
state and never commit or push automatically.

## 5. Known limitations and source of truth

AI agents must not imply access or certainty they do not have:

- Source inspection cannot prove external McHeli/WingMan behavior beyond the
  inspected source, bytecode, or API contract.
- Local dependency jars, game assets, a complete Forge instance, and live
  multiplayer conditions may be unavailable. Mark the dependent validation
  `BLOCKED` or `NOT ATTEMPTED`, identify the missing evidence, and continue
  independent work.
- Forge/McHeli APIs and version claims must be checked against the actual
  project configuration and available local artifacts. Historical docs can
  conflict; prefer fresh source/build evidence and explain unresolved
  discrepancies.
- Build and unit-test success establish only those checks. They do not prove
  native hook loading, tick ordering, aircraft response, server/client
  behavior, or payload outcomes.
- AI recommendations are not repository facts until supported by source or
  runtime evidence. Do not invent telemetry, API guarantees, requirements, or
  user approval.

Use these documents as the canonical reference points and update them only in
their relevant scope:

- `DEVELOPER_GUIDE.md`: contributor setup, integration details, known
  limitations, validation commands, and truthful feature status.
- `docs/current-architecture.md` and
  `docs/ai/02-authoritative-architecture.md`: current responsibilities and
  authority boundaries.
- `docs/AI_AGENT_WORK_POLICY.md`: agent role and execution contract.
- `docs/GITHUB_COPILOT_WORKFLOW.md`: Copilot-specific workflow and handoffs.
- `docs/runtime-test-matrix.md`: live-runtime evidence requirements.
- `docs/api-verification-notes.md`: external API evidence and unresolved
  compatibility notes.

Avoid creating duplicate plans or memory documents when an existing canonical
document can be updated.
