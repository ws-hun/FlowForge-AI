# Flow Runtime Contract

## 1. Current Runtime

FlowForge currently executes saved Flows in `single-pass` mode.

The backend reads one immutable Flow snapshot, applies Run Brief variables, compiles every saved node into one exact Provider input, and performs one Provider call.

This is a real and reproducible runtime. It is not yet a node-level execution engine.

## 2. Versioned Contracts

The runtime exposes two independent versions:

- `flow-compiler-v1` defines how the exact Provider input is produced.
- `flow-plan-v4` defines how saved nodes are ordered, what responsibility each node has, which persisted artifact enters and leaves each step, and how the upstream reference was resolved.

The compiler also produces a SHA-256 fingerprint of the exact UTF-8 Provider input. Preview, Provider invocation, persisted Task input, and Run Trace must agree on that value.

## 3. Execution Plan

`flow-plan-v4` uses deterministic linear scheduling.

Each step contains:

- Stable sequence number.
- Saved node ID and node type.
- Runtime operation.
- Direct predecessor dependency.
- Whether the step is the Provider boundary.
- Stable input and output artifact contracts.
- A versioned input resolution method.

The current operations are:

| Node | Operation | Runtime meaning |
| --- | --- | --- |
| Input | `supply-context` | Contributes saved context to the compiled input. |
| Prompt | `supply-instructions` | Contributes reusable instructions to the compiled input. |
| AI Task | `invoke-provider` | The only Provider call boundary. |
| Output | `define-delivery` | Contributes delivery constraints to the same Provider request. |

Each artifact contract has a stable key, semantic type, and storage owner. The Flow objective enters from `flow-snapshot`; every modern node output is owned by an independently persisted `node-artifact` record. A later step can therefore reference the stable key of its predecessor output without copying payloads into the execution plan.

The current input resolution method is `compiled-reference`. It records that a step's upstream contribution was resolved while the complete Flow snapshot was compiled into one Provider input. It does not mean that the runtime loaded the predecessor payload from the artifact table or executed the downstream node separately.

The plan order must match the immutable node snapshot and the persisted node trace order. The number of Provider boundary steps must match `providerCallCount`.

## 4. Node Artifact Records

New direct Flow runs persist one independently addressable database artifact for every planned node output:

- Input and Prompt payloads contain their variable-resolved `compiledContent` with `text/plain` media type.
- AI Task payloads contain the persisted Summary and Result with `text/markdown` media type.
- Output payloads contain the persisted Result document with `text/markdown` media type.
- Every materialized payload is verified against the SHA-256 fingerprint stored in the immutable run trace before persistence.
- Every `flow-plan-v4` artifact records its upstream key, semantic type, storage owner, state, resolution method, and available content fingerprint.
- Provider failures mark the AI Task artifact as `failed` without payload or fingerprint.
- Downstream Output artifacts are marked `skipped` without payload or fingerprint when the Provider did not return a result.
- The `provider-result` artifact at the unique AI Task boundary records the real call status, Provider, model, available token counts, server-measured duration, and sanitized failure message.
- Input, Prompt, and Output artifacts never receive Provider provenance because they are compiled contributions rather than independent calls.

Successful Tasks and their node artifacts are written in the same execution transaction. Failed Tasks and their materialized, failed, or skipped artifacts are written together inside the same `REQUIRES_NEW` failure-recording transaction. An artifact persistence error therefore rolls back the corresponding Task record instead of leaving partial runtime history.

Provider provenance follows the same atomic boundary. A completed Task can only persist completed provenance on a materialized AI Task artifact, while a failed Task can only persist failed provenance on its failed AI Task artifact. Token fields remain null when the Provider omits usage instead of being estimated or invented.

Artifacts can be inspected through `GET /api/tasks/{taskId}/artifacts` and `GET /api/tasks/{taskId}/artifacts/{artifactKey}`. The frontend requests one payload only when the user opens it inside the run trace. Materialized `node-artifact` inputs can be followed back to their persisted upstream payload; `flow-snapshot` inputs remain clearly identified as immutable Flow objective sources.

`GET /api/tasks/{taskId}/artifacts/{artifactKey}/lineage` returns a metadata-only path from the requested node artifact to the Flow snapshot objective. It is intentionally separate from payload inspection and returns `complete` plus a termination reason such as `flow-snapshot`, `legacy-record`, `missing-upstream-artifact`, or `cycle-detected`. A path ending at `flow-snapshot` is an explainability path for the saved run; it does not activate `persisted-artifact` input resolution.

These records are independently addressable and establish the persistence boundary required by a future node engine. The current runtime still does not read an upstream artifact from the database as the runtime input of a downstream node, and the Provider call count remains `1`.

## 5. Persistence And Replay

New direct Flow runs persist their execution plan inside `flowRunTrace` for both successful and failed Provider calls.

Legacy traces without an execution plan remain valid and deserialize with `executionPlan = null`. The frontend labels these records as legacy instead of synthesizing a plan that did not exist at run time.

Legacy `flow-plan-v1` steps remain readable with null artifact contracts. Legacy `flow-plan-v2` plans retain their original `trace-content` and `task-result` storage owners. Legacy `flow-plan-v3` plans retain modern `node-artifact` contracts but have no input resolution field. Artifact records created before lineage or Provider provenance persistence remain readable with null lineage fields and `providerCall = null`. FlowForge never synthesizes missing plans, artifacts, lineage, or Provider calls for old runs.

Exact historical reruns use the stored Task input. They do not recompile the old Flow. When the source trace has a plan, the new replay preserves that immutable plan and records `stored-input-replay` plus the source Task ID.

## 6. Upgrade Boundary

FlowForge must not switch a Flow to `node-sequential` until all of the following are real backend capabilities:

1. Every executable node has an explicit input and output contract.
2. The runtime resolves dependent node inputs from persisted upstream artifacts instead of the single compiled Provider input.
3. Every actual Provider invocation made by the node engine is independently recorded on its owning node, including model, available token usage, duration, failure provenance, and retry attempt identity.
4. Failure policy defines stop, skip, and retry behavior without inventing successful downstream state.
5. Preview returns the same executable plan consumed by the runtime.
6. Historical replay can reproduce the saved plan without consulting the current Flow editor state.

The current single AI Task boundary already preserves the provenance of its one real Provider call. This satisfies inspection for `single-pass`, but it does not satisfy independent node invocation or retry semantics. Until every upgrade condition is real, the product must continue to describe Input, Prompt, and Output as compiled contributions around one AI Task Provider boundary.
