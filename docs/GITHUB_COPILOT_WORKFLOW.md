# GitHub Copilot workflow: RDP UAV architecture and implementation

This runbook turns the RDP UAV redesign brief into a staged, evidence-based
workflow for the GitHub Copilot app. It is designed for the repository's
existing **RDP UAV Architect** custom agent and the Plan/Agent controls visible
in the app. Keep the original master prompt as the long-term requirements
source; use this runbook to control how Copilot executes it.

The agent's role, architecture invariants, end-to-end task loop, collaboration
rules, and known limitations are defined in the canonical
[AI Agent Work Policy](AI_AGENT_WORK_POLICY.md). This runbook covers the
Copilot-specific workflow and validation handoff.

The multi-phase flow below is intended for a broad architecture redesign or
when the user explicitly asks for staged discovery and planning. It is not a
mandatory gate for ordinary bug fixes, reviews, documentation changes, or
bounded feature requests. For those tasks, follow the current request and the
canonical work policy directly.

## Outcome and guardrails

The goal is to move the addon toward a coherent Minecraft 1.12.2 / Forge /
McHeli CE autonomy architecture through reviewed, testable increments. The
project requirements include mission and decision ownership, vehicle profiles,
perception and target tracking, navigation and guidance, flight control,
McHeli adapters, payloads, networking, swarm coordination, operator UI,
diagnostics, recovery, and live-runtime validation.

Treat PX4, ROS 2/Nav2, ArduPilot, and MAVLink as sources of ideas only. Verify
that each proposed boundary fits this repository, Java 8, Forge 1.12.2, McHeli
CE, WingMan, and RDP Core. Do not introduce frameworks, dependencies, class
families, or machine learning without a demonstrated need.

Follow the authorization in the user's current request. A request to inspect,
plan, explain, or review without asking for implementation remains read-only.
A direct implementation request authorizes work within its stated scope,
including focused tests and directly related documentation; do not add an
approval gate for those steps. Ask before expanding scope or when an unresolved
choice materially affects gameplay, safety, persistence, compatibility, or
authority. Resolve ordinary ambiguity using the safest repository-consistent
assumption and state it in the handoff.

## Copilot app setup

1. Open the `rdp-uav-addon` repository/workspace shown in the app.
2. Select the existing **RDP UAV Architect** custom agent.
3. Start in **Plan** mode. The provided app screenshot already shows both the
   Plan control and RDP UAV Architect selection.
4. Provide the full original master prompt in this Copilot chat as requirements
   context, then paste the Phase 0 prompt below. The attachment supplied to
   Codex is not automatically available to the separate Copilot app. If you
   cannot attach or paste it, the requirements summary and coverage checklist in
   this runbook are the available brief; Copilot must label missing specifics
   UNKNOWN instead of inventing them.
5. Keep the first run read-only. If sending the master prompt as a separate
   message, prefix it with: “Treat this as requirements only. Stay in Plan mode;
   do not change repository files. Wait for my Phase 0 discovery instructions.”
6. Review the evidence and open questions. If continuing this staged redesign,
   resolve material architecture choices and identify the next bounded slice.
7. Use **Agent** mode for the work authorized by the user's request. Keep the
   same chat for continuity when practical; start a new chat only if its first
   message includes the scope and links/files needed to restore state.

The custom agent's tool access does not expand the user's requested scope.
Inspect and plan when asked to inspect or plan; implement when asked to
implement; do not infer authorization for unrelated redesign or repository
operations.

GitHub describes Plan mode as researching a task and drafting an implementation
plan before code changes, and Agent mode as carrying out a multi-step task with
repository edits and commands. See [Using Copilot agents](https://docs.github.com/en/copilot/how-tos/copilot-in-your-ide/use-copilot-agents)
and [Copilot Chat modes](https://docs.github.com/en/copilot/how-tos/chat-with-copilot/chat-in-ide).

## Workflow at a glance

| Stage | Copilot mode | Allowed work | Gate to continue |
| --- | --- | --- | --- |
| 0. Discover | Plan or read-only mode | Repository and evidence audit | Scope and evidence are clear; ask only about consequential uncertainty |
| 1. Align architecture | Plan | Compare current design with target; resolve major choices and produce a migration plan when requested | Decisions stay within user scope; seek direction for material unresolved choices |
| 2. Prepare baseline | Agent for authorized implementation | Establish current Git/build/test baseline and safe change boundary | Baseline and file overlap are understood; preserve pre-existing user changes |
| 3. Implement one slice | Agent | Complete the requested behavior with tests and related docs within scope | Acceptance criteria pass or a real blocker is reported |
| 4. Review and runtime | Ask/Plan for review; Agent for checks/fixes within requested scope | Inspect diff, regressions, runtime evidence, and remaining gaps | Report verified outcome or a concrete blocker |
| 5. Resume next slice | Plan then Agent as needed | Re-read project state and continue from the first unfinished requested task | Confirm authorization before expanding material scope |

The full feature backlog is not one implementation phase. Work through it in
dependency-aware slices, preserving existing behavior until a replacement is
approved and verified.

## Phase 0: copy/paste prompt for read-only discovery

Paste this while **Plan** mode is selected:

```text
PHASE 0 — READ-ONLY ARCHITECTURE DISCOVERY

You are the RDP UAV Architect for this repository. Use the original RDP UAV
redesign brief if it is available in this Copilot chat as the requirements
source and this request as the execution gate. If it is unavailable, use the
requirements coverage checklist in this runbook and report missing details as
UNKNOWN. Start in discovery only.

HARD BOUNDARY: Do not edit, create, delete, rename, format, stage, commit, or
revert any repository file. Do not run build, test, clean, deployment, or other
commands that create/change project artifacts. Do not switch branches or
worktrees. Read-only search and inspection are allowed. Do not begin
implementation. Stop after the report. Continue only if the user asks to proceed
or otherwise authorizes implementation.

First establish the present checkout state using read-only Git inspection.
Preserve every pre-existing user change. In particular, the checkout was
observed with local modifications to:
- src/main/java/net/vas/rdp/uav/mission/MissionCoordinator.java
- src/main/java/net/vas/rdp/uav/mission/RDPMission.java
- src/main/java/net/vas/rdp/uav/swarm/SwarmManager.java
- src/main/java/net/vas/rdp/uav/swarm/SwarmTargetRegistry.java
- src/test/java/net/vas/rdp/uav/SwarmManagerContractTest.java
- src/test/java/net/vas/rdp/uav/SwarmTargetRegistryTest.java
- src/test/java/net/vas/rdp/uav/mission/MissionLifecycleContractTest.java
This is a snapshot, not a claim that the checkout is still in that state. Recheck
it. Do not overwrite, stage, stash, discard, or claim these changes as yours.
If any proposed work would overlap them, flag the overlap and ask before edits.

Inspect the current source and actual build/dependency configuration. Read the
relevant existing project documentation, including current-architecture.md,
proposed-architecture.md, implementation-source-matrix.md,
runtime-test-matrix.md, baseline-report.md, the phase documents, and the existing
.github/agents/rdp-uav-architect.agent.md. Check whether project-state documents
or other newer records supersede these. Treat documentation and prior chat as
hypotheses: resolve conflicts against the present checkout and direct evidence.
Do not create substitute state files in this read-only phase.

Reconstruct, with file/class evidence:
1. Repository inventory, build/toolchain/dependency facts, and existing tests.
2. Module and dependency map; Forge lifecycle and common/client/server split.
3. Mission ownership, lifecycle, persistence, and current state transitions.
4. Target acquisition, identity/lock, tracking, perception, and HUD/camera flow.
5. Decision/tasking, navigation, guidance, fixed-wing/helicopter control, and
   the actual movement authority.
6. McHeli/WingMan and guided-weapon/payload integration boundaries; verify
   referenced APIs from local source/jars or label them UNKNOWN.
7. Network request/authority/synchronization, operator actions, and validation.
8. Swarm state, membership, leadership, allocation, communications, and
   persistence.
9. Existing safety/recovery, diagnostics/telemetry, tests, and runtime evidence.
10. Duplicated, overlapping, dead, suspicious, or weakly verified designs,
    with evidence and confidence rather than assumptions.

For each important statement label it FACT (direct evidence), INFERENCE,
HYPOTHESIS, UNKNOWN, or BLOCKED. Give paths and symbols for facts. Distinguish
source-level, test/build, API/jar, and live Minecraft runtime evidence. Do not
claim a test/build/runtime command passed unless you ran it; in this phase do
not run artifact-producing validation commands.

Deliver a concise but complete report with:
- current architecture and data/control-flow diagrams;
- verified facts and important unknowns;
- documentation/build-version contradictions that need reconciliation;
- current Git state and files whose changes must be protected;
- architecture risks and a current-to-target responsibility mapping;
- a dependency-ordered, incremental phase proposal (not a rewrite checklist);
- a verification matrix separating unit/source, build, and live runtime checks;
- substantial decisions as numbered questions, with evidence, options, tradeoffs,
  and an engineering recommendation clearly labeled as a recommendation;
- one exact recommended next phase, its scope, exclusions, acceptance criteria,
  affected areas, and verification plan.

Do not ask questions whose answer is already established in current project
records. Group related decisions to reduce back-and-forth, but never choose a
material gameplay, authority, persistence, API, or architecture policy silently.
Finish by stating that no files were changed and wait for my approval.
```

## Phase 1: architecture alignment

After reading Phase 0, keep Plan mode selected. Ask Copilot to refine only the
unresolved decisions and plan the first migration slice. The plan should map
each responsibility as:

```text
current class/flow -> verified responsibility -> target responsibility
-> smallest migration step -> regression/acceptance check
```

The plan must keep mission intent separate from execution; keep server-owned
target identity separate from client camera/HUD observation; identify exactly
who owns mission state, target lock, path/setpoint, movement writes, and payload
execution; and isolate legacy McHeli/WingMan behavior behind verified adapter
boundaries. Profiles describe capabilities; operational roles describe current
assignment. Fixed-wing and rotary-wing behavior must be justified separately.

When the user requests staged architecture alignment, ask Copilot to produce a
decision and implementation packet before coding:

- chosen phase and explicit in/out-of-scope list;
- decisions resolved, with decision IDs and user approval state;
- files/modules expected to change and any overlap with existing local edits;
- ordered tasks and dependency links;
- measurable acceptance criteria;
- focused and regression checks, expected commands, and runtime requirements;
- rollback/recovery considerations for risky behavior changes;
- project documents that need an update, reusing existing docs where practical.

Use an explicit reply such as:

```text
APPROVE PHASE 1 AND PHASE 2 PLAN.
Approved decisions: D-001, D-002.
Approved implementation scope: [specific behavior/files].
Excluded: [specific behavior/files].
Preserve the existing local modifications. Stop and ask if the approved work
overlaps them or requires a new material decision.
```

Edit approval is not approval to commit, push, merge, deploy, change dependencies,
or delete old systems. Those remain separate user decisions unless explicitly
included in the approval.

## Phase 2: establish a safe baseline

Once the user requests implementation of a bounded slice, switch to Agent mode
and require Copilot to:

1. Re-read the agreed project records and exact approval message.
2. Capture `git status --short --branch`, `git diff --stat`, and the existing
   diff before editing. Treat pre-existing modifications as user-owned.
3. Compare the approved file list against the dirty paths. If they overlap,
   stop and ask how to proceed; do not resolve the conflict by guessing.
4. Verify current Gradle, Java, Forge, McHeli CE, RDP Core, and test setup from
   checked-in files and installed artifacts. Reconcile historical documents;
   do not copy old version or test claims into the new baseline.
5. Run relevant baseline checks. Record exact
   commands and results; distinguish environment failures from source failures.
6. Report the safe edit boundary and proceed within the user's requested scope.

Do not automatically switch branch/worktree, stage files, commit, push, or
discard changes. If isolation is needed to protect existing work, explain the
specific overlap and offer safe choices before taking that action.

## Phase 3: implement the requested slice

For each task, follow this loop:

1. Reconfirm the task and acceptance criteria from the user's request or
   approved plan.
2. Inspect the target class, callers, dependencies, state ownership, mutation
   sites, lifecycle/thread/tick side, runtime usage, and existing tests.
3. Verify external APIs from the actual local jar/source when they matter. If
   unavailable, mark UNKNOWN and stop only the dependent task; continue
   independent work within the user's scope where safe.
4. State the intended behavior change and files to touch. Keep each change
   cohesive and narrow. Do not add unrelated cleanup or speculative abstractions.
5. Implement; add or update tests only for the requested behavior and repository
   conventions. Run focused checks first, then the agreed regression/build
   checks. Do not treat source tests as live flight proof.
6. Inspect `git diff` and `git diff --check`; verify no pre-existing change was
   lost, staged, or silently included. Do not use reset/clean/revert on user work.
7. Update existing relevant design, phase, and verification records when the
   task changes their documented contracts. Avoid overlapping project-memory
   files; identify a canonical location and link to existing records.
8. Continue through all tasks in the requested scope. At a genuine blocker,
   classify it (architecture, API, build, runtime, environment, or data), record
   evidence, and continue independent work within scope. Ask only for decisions
   that materially affect behavior, safety, compatibility, authority, or scope.

Important decisions include module boundaries, mission/target ownership,
state-machine semantics, client/server authority, network contracts, flight
control strategy, McHeli compatibility hooks, payload semantics, swarm
authority/allocation, persistence, thread/tick behavior, dependencies, and
removal of apparently unused systems. Record each approved decision with an ID,
date, evidence, alternatives, rationale, affected areas, and approval.

## Phase 4: debug, verify, and review

Debug from a reproducible symptom and evidence. Record reproduction steps,
expected/actual behavior, logs/telemetry, environment, and whether the issue is
source-, test-, build-, API-, or runtime-proven. Fix the cause within the
user-requested scope; do not paper over a failed native handoff with a fake
success state.

Validation should be layered and reported separately:

1. Focused unit/contract tests for changed logic.
2. Full project tests and build where the exact toolchain/dependencies permit.
3. Git diff and whitespace review; check accidental/generated files and scope.
4. Forge/McHeli client/server runtime checks for behavior that depends on ticks,
   entity physics, packets, rendering, native routes, weapons, or persistence.
5. Long-running or regression checks where the behavior warrants them.

For each check report the exact command or procedure, result, evidence, and
limitation. Use `PASS`, `FAIL`, `NOT RUN`, or `BLOCKED`; distinguish
`IMPLEMENTED`, `TESTED`, and `RUNTIME VERIFIED`. Never infer a successful mission,
flight path, targeting result, damage event, weapon release, HUD display, or
network behavior from compilation alone.

The final phase report must include outcome, approved scope, files changed,
behavior changed, checks and results, runtime status, known issues/unknowns,
remaining backlog, and the exact next task. Do not report completion while any
approved acceptance criterion remains unverified or unresolved.

## Phase 5: resume without resetting context

At the beginning of every resumed session, in Plan mode, ask Copilot to read the
latest relevant architecture/phase/verification records, inspect current Git
status and diff, identify the latest approved phase and decisions, and name the
first unfinished task. It must not restart discovery or redo completed work
unless new evidence invalidates it or the user asks.

At a meaningful checkpoint, keep one concise execution ledger in the existing
phase record (or another user-approved canonical project record):

```text
CURRENT PHASE / APPROVAL:
COMPLETED:
IN PROGRESS:
PENDING:
BLOCKED:
FILES CHANGED:
VERIFICATION:
APPROVED DECISIONS:
NEXT EXACT ACTION:
```

If no canonical status/decision record exists, propose where to keep it before
creating another project-state document.

## Requirements coverage checklist

Use this to keep the original prompt's backlog visible without forcing it into
one release. The discovery report should map each item to existing evidence,
proposed owner, dependencies, and a possible phase:

- mission lifecycle, task/decision handling, patrol, search, route planning,
  altitude, recovery, and mission persistence;
- UAV capabilities/profiles and distinct fixed-wing, rotary-wing, VTOL, and
  long-endurance constraints;
- sensors, observations, target detection/classification, authoritative lock,
  track continuity, data fusion, and world model;
- client input override, camera observation, lock overlay/HUD, and strict
  server/client ownership;
- navigation, guidance, maneuvers, flight-control authority, McHeli/WingMan
  adapter contracts, and guided missile/TV missile support boundaries;
- swarm membership, roles, formation, task allocation, cooperative search,
  communication, persistence, and degraded-mode behavior;
- payload control, SelfDestruct compatibility, failure handling, and any
  Mixin/coremod work only after actual injection points are verified and the
  user approves the strategy;
- telemetry, debugging, deterministic tests, build checks, and live runtime
  validation.

For every item, state whether it is implemented, partial, proposed, unsupported,
blocked, or unknown, with evidence. Do not mistake a class existing for an
end-to-end feature being operational.

## Evidence and communication rules

- Label claims `FACT`, `INFERENCE`, `HYPOTHESIS`, `UNKNOWN`, or `BLOCKED` when
  the distinction affects a decision.
- Cite repository paths and symbols for source facts; cite exact commands,
  output, jar/class, or runtime logs for validation claims.
- Ask a small, grouped set of concrete architecture questions with meaningful
  options and tradeoffs. Do not repeat decisions already approved in project
  records.
- Give recommendations as recommendations, never as verified facts.
- Keep progress reports concise at checkpoints; reserve exhaustive inventories
  for the architecture report and final phase review.
- Continue through the requested tasks; do not stop after the first passing
  test or a trivial subtask. Pause only at a real authorization gate or blocker.

## Repository observations when this runbook was prepared

These observations help Copilot know what to verify; they are not a replacement
for fresh inspection:

- The repository already has `.github/agents/rdp-uav-architect.agent.md` and
  substantial architecture, phase, baseline, and runtime documents under
  `docs/`. Reuse and reconcile them before proposing duplicates.
- The current checkout was `main`, 25 commits ahead of `origin/main`, with the
  seven modified source/test files listed in Phase 0. This may change before the
  workflow is used.
- Existing reports are not a single unquestionable baseline: for example,
  historical documents contain differing Forge version and validation claims.
  Verify `build.gradle`, local dependencies, current Git state, and any recent
  evidence before adopting a version or claiming tests passed.
