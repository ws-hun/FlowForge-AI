# Flow Runtime Contract

## 1. Current Runtime

FlowForge currently executes saved Flows in `single-pass` mode.

The backend reads one immutable Flow snapshot, applies Run Brief variables, compiles every saved node into one exact Provider input, and performs one Provider call.

This is a real and reproducible runtime. It is not yet a node-level execution engine.

## 2. Versioned Contracts

The runtime exposes two independent versions:

- `flow-compiler-v1` defines how the exact Provider input is produced.
- `flow-plan-v3` defines how saved nodes are ordered, what responsibility each node has, and which persisted artifact enters and leaves each step.

The compiler also produces a SHA-256 fingerprint of the exact UTF-8 Provider input. Preview, Provider invocation, persisted Task input, and Run Trace must agree on that value.

## 3. Execution Plan

`flow-plan-v3` uses deterministic linear scheduling.

Each step contains:

- Stable sequence number.
- Saved node ID and node type.
- Runtime operation.
- Direct predecessor dependency.
- Whether the step is the Provider boundary.
- Stable input and output artifact contracts.

The current operations are:

| Node | Operation | Runtime meaning |
| --- | --- | --- |
| Input | `supply-context` | Contributes saved context to the compiled input. |
| Prompt | `supply-instructions` | Contributes reusable instructions to the compiled input. |
| AI Task | `invoke-provider` | The only Provider call boundary. |
| Output | `define-delivery` | Contributes delivery constraints to the same Provider request. |

Each artifact contract has a stable key, semantic type, and storage owner. The Flow objective enters from `flow-snapshot`; every modern node output is owned by an independently persisted `node-artifact` record. A later step can therefore reference the stable key of its predecessor output without copying payloads into the execution plan.

The plan order must match the immutable node snapshot and the persisted node trace order. The number of Provider boundary steps must match `providerCallCount`.

## 4. Node Artifact Records

New direct Flow runs persist one independently addressable database artifact for every planned node output:

- Input and Prompt payloads contain their variable-resolved `compiledContent` with `text/plain` media type.
- AI Task payloads contain the persisted Summary and Result with `text/markdown` media type.
- Output payloads contain the persisted Result document with `text/markdown` media type.
- Every materialized payload is verified against the SHA-256 fingerprint stored in the immutable run trace before persistence.
- Provider failures mark the AI Task artifact as `failed` without payload or fingerprint.
- Downstream Output artifacts are marked `skipped` without payload or fingerprint when the Provider did not return a result.

Successful Tasks and their node artifacts are written in the same execution transaction. Failed Tasks and their materialized, failed, or skipped artifacts are written together inside the same `REQUIRES_NEW` failure-recording transaction. An artifact persistence error therefore rolls back the corresponding Task record instead of leaving partial runtime history.

Artifacts can be inspected through `GET /api/tasks/{taskId}/artifacts` and `GET /api/tasks/{taskId}/artifacts/{artifactKey}`. The frontend requests one payload only when the user opens it inside the run trace.

These records are independently addressable and establish the persistence boundary required by a future node engine. The current runtime still does not read an upstream artifact as the runtime input of a downstream node.

## 5. Persistence And Replay

New direct Flow runs persist their execution plan inside `flowRunTrace` for both successful and failed Provider calls.

Legacy traces without an execution plan remain valid and deserialize with `executionPlan = null`. The frontend labels these records as legacy instead of synthesizing a plan that did not exist at run time.

Legacy `flow-plan-v1` steps remain readable with null artifact contracts. Legacy `flow-plan-v2` plans retain their original `trace-content` and `task-result` storage owners, and legacy node traces remain readable with null output artifact records. FlowForge never synthesizes missing database artifacts for old runs.

Exact historical reruns use the stored Task input. They do not recompile the old Flow. When the source trace has a plan, the new replay preserves that immutable plan and records `stored-input-replay` plus the source Task ID.

## 6. Upgrade Boundary

FlowForge must not switch a Flow to `node-sequential` until all of the following are real backend capabilities:

1. Every executable node has an explicit input and output contract.
2. The runtime resolves dependent node inputs from persisted upstream artifacts instead of the single compiled Provider input.
3. Provider calls are recorded per node with model, token, duration, and error provenance.
4. Failure policy defines stop, skip, and retry behavior without inventing successful downstream state.
5. Preview returns the same executable plan consumed by the runtime.
6. Historical replay can reproduce the saved plan without consulting the current Flow editor state.

Until then, the product must continue to describe Input, Prompt, and Output as compiled contributions around one AI Task Provider boundary.
