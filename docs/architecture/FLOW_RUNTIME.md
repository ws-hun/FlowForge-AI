# Flow Runtime Contract

Authentication is a product boundary outside the Flow runtime. A valid workspace-owner session is required before a Flow, Task, Prompt, Provider configuration, or History API can be read or mutated. Runtime records are currently scoped to the single local workspace; this contract does not claim multi-user ownership or team permissions.

## 1. Current Runtime

FlowForge currently executes saved Flows in `single-pass` mode.

The backend reads one immutable Flow snapshot, applies Run Brief variables, compiles every saved node into one exact Provider input, and performs one Provider call.

This is a real and reproducible runtime. It is not yet a node-level execution engine.

## 2. Versioned Contracts

The runtime exposes five independent versions:

- `flow-compiler-v1` defines how the exact Provider input is produced.
- `flow-plan-v5` defines how saved nodes are ordered, what responsibility each node has, which persisted artifact enters and leaves each step, which declared inputs converge at the Provider boundary, and how upstream references were resolved.
- `flow-failure-policy-v1` defines the current terminal behavior: stop the run after Provider failure, skip downstream nodes, allow one attempt, and perform no automatic retry.
- `flow-input-resolution-v1` defines the active input mode, the reserved persisted-artifact mode, and the runtime boundary required before that mode can be enabled.
- `flow-provider-attempt-policy-v1` defines how persisted Provider attempts form one valid chain and where failed-run recovery creates a new run instead of mutating history.

The compiler also produces a SHA-256 fingerprint of the exact UTF-8 Provider input. Preview, Provider invocation, persisted Task input, and Run Trace must agree on that value.

## 3. Execution Plan

`flow-plan-v5` uses deterministic linear scheduling.

Each step contains:

- Stable sequence number.
- Saved node ID and node type.
- Runtime operation.
- Declared node dependencies.
- Whether the step is the Provider boundary.
- Stable input and output artifact contracts.
- An ordered Provider input artifact list on the AI Task boundary.
- A versioned input resolution method.

The current operations are:

| Node | Operation | Runtime meaning |
| --- | --- | --- |
| Input | `supply-context` | Contributes saved context to the compiled input. |
| Prompt | `supply-instructions` | Contributes reusable instructions to the compiled input. |
| AI Task | `invoke-provider` | The only Provider call boundary. |
| Output | `define-delivery` | Contributes delivery constraints to the same Provider request. |

Each artifact contract has a stable key, semantic type, and storage owner. The Flow objective enters from `flow-snapshot`; every modern node output is owned by an independently persisted `node-artifact` record. A later step can therefore reference the stable key of its predecessor output without copying payloads into the execution plan.

The AI Task is the one fan-in boundary. Its `providerInputArtifacts` begin with `flow:objective`, followed by every preceding Input and Prompt output in immutable snapshot order. Its `dependsOnNodeIds` contain those same contribution nodes in the same order. Runtime validation rejects duplicate keys, unknown or reordered artifacts, unsupported source node types, and dependencies that drift from the declared inputs.

This fan-in is a plan contract, not a claim that every part of the compiled request is loaded from an artifact. AI Task execution guidance and Output delivery focus are still read directly from their saved node definitions by the single-pass compiler. The runtime combines those values with the declared objective, Input, and Prompt contributions into one Provider request.

The current input resolution method is `compiled-reference`. It records that a step's upstream contribution was resolved while the complete Flow snapshot was compiled into one Provider input. It does not mean that the runtime loaded the predecessor payload from the artifact table or executed the downstream node separately.

New `flow-plan-v5` previews and traces also carry `flow-input-resolution-v1`. This contract makes the upgrade boundary explicit: `compiled-reference` is active, `persisted-artifact` is defined as a supported future mode, and it remains disabled until the `node-sequential-runtime` can resolve each node input from a persisted upstream artifact. A contract that activates `persisted-artifact` early is rejected by the runtime validator.

The plan order must match the immutable node snapshot and the persisted node trace order. The number of Provider boundary steps must match `providerCallCount`.

New plans embed `flow-failure-policy-v1`. Preview and execution therefore expose the same failure behavior before a run begins and after it becomes immutable. Trace generation and artifact persistence both reject mismatched terminal states: a failed run has one failed AI Task boundary and every later node is `skipped`; a completed run cannot contain failed or skipped nodes. This policy describes the current single Provider attempt and is not a user-configurable retry engine.

## 4. Node Artifact Records

New direct Flow runs persist one independently addressable database artifact for every planned node output:

- Input and Prompt payloads contain their variable-resolved `compiledContent` with `text/plain` media type.
- AI Task payloads contain the persisted Summary and Result with `text/markdown` media type.
- Output payloads contain the persisted Result document with `text/markdown` media type.
- Every materialized payload is verified against the SHA-256 fingerprint stored in the immutable run trace before persistence.
- Every modern artifact records its upstream key, semantic type, storage owner, state, resolution method, and available content fingerprint.
- Provider failures mark the AI Task artifact as `failed` without payload or fingerprint.
- Downstream Output artifacts are marked `skipped` without payload or fingerprint when the Provider did not return a result.
- The `provider-result` artifact at the unique AI Task boundary owns the real Provider call history.
- New v5 runs persist every declared Provider input as an ordered metadata-only reference owned by that AI Task artifact.
- Every new successful or failed run records exactly one independent `initial #1` attempt with status, Provider, model, available token counts, server-measured duration, and sanitized failure message.
- Input, Prompt, and Output artifacts never receive Provider provenance because they are compiled contributions rather than independent calls.

Successful Tasks, node artifacts, Provider input references, and attempts are written in the same execution transaction. Failed Tasks and their materialized, failed, or skipped artifacts use the same complete boundary inside the `REQUIRES_NEW` failure-recording transaction. A persistence error therefore rolls back the corresponding Task record instead of leaving partial runtime history.

`flow_provider_input_references` preserves each v5 AI Task declaration in plan order. A row stores the input contract, materialization state, `compiled-reference` resolution, available content fingerprint, and source Artifact identity for node-backed inputs. The Flow objective points to the immutable snapshot and has no source Artifact. Artifact detail validates that a non-empty reference chain starts at order one with that objective, remains contiguous, belongs to the requested Provider Artifact, and resolves each later item to an earlier same-run Artifact with matching node identity, state, contract, and fingerprint. These rows deliberately contain no payload and are evidence of the compiled fan-in, not proof that the runtime loaded each source payload from PostgreSQL.

Provider attempts follow the same atomic boundary. A completed Task can only persist a completed `initial #1` on a materialized AI Task artifact, while a failed Task can only persist a failed `initial #1` on its failed AI Task artifact. Token fields remain null when the Provider omits usage instead of being estimated or invented. The current runtime never creates `automatic-retry` or `manual-recovery` attempts.

Artifact detail evaluates `flow-provider-attempt-policy-v1` before returning an attempt timeline. Attempt numbers must start at one and remain contiguous, the first trigger must be `initial`, every later attempt must directly reference the immediately preceding failed attempt, and a completed attempt is terminal. The current policy disables both automatic retry and same-artifact manual recovery. Recovering a failed run uses the existing rerun path to create a new immutable Task, preserving the original failed attempt for inspection and comparison.

Artifacts can be inspected through `GET /api/tasks/{taskId}/artifacts` and `GET /api/tasks/{taskId}/artifacts/{artifactKey}`. The collection exposes only the latest attempt as the compatibility `providerCall` summary, resolved in one batch query. Artifact detail additionally returns its ordered `providerInputReferences`, complete `providerAttempts` history, and payload only when the user opens it inside the run trace. Materialized node references can locate their independently persisted source Artifact; `flow-snapshot` inputs remain clearly identified as immutable Flow objective sources.

`GET /api/tasks/{taskId}/artifacts/{artifactKey}/lineage` returns a metadata-only path from the requested node artifact to the Flow snapshot objective. It is intentionally separate from payload and attempt-history inspection, returns only the latest `providerCall` summary, and reports `complete` plus a termination reason such as `flow-snapshot`, `legacy-record`, `missing-upstream-artifact`, or `cycle-detected`. A path ending at `flow-snapshot` is an explainability path for the saved run; it does not activate `persisted-artifact` input resolution.

These records are independently addressable and establish the persistence boundary required by a future node engine. The current runtime still does not read an upstream artifact from the database as the runtime input of a downstream node, and the Provider call count remains `1`.

## 5. Persistence And Replay

New direct Flow runs persist their execution plan inside `flowRunTrace` for both successful and failed Provider calls.

Legacy traces without an execution plan remain valid and deserialize with `executionPlan = null`. The frontend labels these records as legacy instead of synthesizing a plan that did not exist at run time.

Legacy `flow-plan-v1` steps remain readable with null artifact contracts. Legacy `flow-plan-v2` plans retain their original `trace-content` and `task-result` storage owners. Legacy `flow-plan-v3` plans retain modern `node-artifact` contracts but have no input resolution field. Legacy `flow-plan-v4` plans retain input resolution and failure policy while `providerInputArtifacts` remains null. V5 plans created before V10 have no persisted input references and remain readable with an empty list. V7 deterministically backfills V6 Provider provenance as `initial #1`; artifacts created before real Provider provenance remain readable with an empty attempt history and `providerCall = null`. FlowForge never synthesizes missing plans, policies, artifacts, lineage, Provider fan-in, input references, or Provider calls from current state.

Run comparison may compare the ordered `providerInputArtifacts` stored in two immutable v5 plans. Equality means the saved key, type, and storage sequence is identical; it does not mean that payloads were loaded from the Artifact table. If either run lacks a v5 declaration, the structure remains unavailable rather than being inferred from the current Flow.

Exact historical reruns use the stored Task input. They do not recompile the old Flow. When the source trace has a plan, the new replay preserves that immutable plan and records `stored-input-replay` plus the source Task ID.

Failed historical runs use `POST /api/tasks/{id}/recover` to create a new immutable Task. The new Task records `recoveryOfTaskId`, preserves the exact failed input and saved Flow snapshot, and marks its trace as `stored-input-recovery`. A recovery is a new single-pass execution with one Provider call; it never mutates the source Task, its node artifacts, or its Attempt history. The existing rerun endpoint routes failed sources through the same identity so older clients cannot accidentally erase the distinction.

## 6. Upgrade Boundary

FlowForge must not switch a Flow to `node-sequential` until all of the following are real backend capabilities:

1. Every executable node has an explicit input and output contract.
2. The runtime resolves dependent node inputs from persisted upstream artifacts instead of the single compiled Provider input.
3. Every actual Provider invocation made by the node engine is independently recorded on its owning node, including model, available token usage, duration, failure provenance, and retry attempt identity.
4. The node engine extends the current single-pass stop/skip/no-retry baseline with explicit per-node retry attempts, recovery state, and terminal propagation without inventing successful downstream state.
5. Preview returns the same executable plan consumed by the runtime.
6. Historical replay can reproduce the saved plan without consulting the current Flow editor state.

The current single AI Task boundary already preserves the provenance of its one real Provider call as an independently addressable attempt. This establishes attempt identity and history storage for future recovery work, but it does not satisfy independent node invocation or retry semantics. Until every upgrade condition is real, `providerCallCount` remains `1`, no retry attempt is created, and the product must continue to describe Input, Prompt, and Output as compiled contributions around one AI Task Provider boundary.
