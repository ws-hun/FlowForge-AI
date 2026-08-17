# Flow Runtime Contract

## 1. Current Runtime

FlowForge currently executes saved Flows in `single-pass` mode.

The backend reads one immutable Flow snapshot, applies Run Brief variables, compiles every saved node into one exact Provider input, and performs one Provider call.

This is a real and reproducible runtime. It is not yet a node-level execution engine.

## 2. Versioned Contracts

The runtime exposes two independent versions:

- `flow-compiler-v1` defines how the exact Provider input is produced.
- `flow-plan-v2` defines how saved nodes are ordered, what responsibility each node has, and which persisted artifact enters and leaves each step.

The compiler also produces a SHA-256 fingerprint of the exact UTF-8 Provider input. Preview, Provider invocation, persisted Task input, and Run Trace must agree on that value.

## 3. Execution Plan

`flow-plan-v2` uses deterministic linear scheduling.

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

Each artifact contract has a stable key, semantic type, and storage owner. The current owners are `flow-snapshot`, `trace-content`, and `task-result`; they point to records FlowForge actually persists rather than creating duplicate payloads inside the plan.

The plan order must match the immutable node snapshot and the persisted node trace order. The number of Provider boundary steps must match `providerCallCount`.

## 4. Node Artifact Records

New direct Flow runs persist one output artifact record with each node trace:

- Input and Prompt fingerprints are calculated from their actual variable-resolved `compiledContent`.
- AI Task fingerprints are calculated from the persisted Summary and Result.
- Output fingerprints are calculated from the persisted Result document.
- Provider failures mark the AI Task artifact as `failed` without a content fingerprint.
- Downstream Output artifacts are marked `skipped` when the Provider did not return a result.

Artifact records contain a key, type, storage owner, state, and SHA-256 fingerprint. The source content remains in the existing Flow snapshot, node trace, or Task Result so immutable history does not store redundant copies.

These records prove what was materialized by the current runtime. They are not yet independently addressable intermediate payloads and do not yet drive downstream node execution.

## 5. Persistence And Replay

New direct Flow runs persist their execution plan inside `flowRunTrace` for both successful and failed Provider calls.

Legacy traces without an execution plan remain valid and deserialize with `executionPlan = null`. The frontend labels these records as legacy instead of synthesizing a plan that did not exist at run time.

Legacy `flow-plan-v1` steps remain readable with null artifact contracts, and legacy node traces remain readable with null output artifact records.

Exact historical reruns use the stored Task input. They do not recompile the old Flow. When the source trace has a plan, the new replay preserves that immutable plan and records `stored-input-replay` plus the source Task ID.

## 6. Upgrade Boundary

FlowForge must not switch a Flow to `node-sequential` until all of the following are real backend capabilities:

1. Every executable node has an explicit input and output contract.
2. Node outputs are persisted independently and can feed dependent nodes.
3. Provider calls are recorded per node with model, token, duration, and error provenance.
4. Failure policy defines stop, skip, and retry behavior without inventing successful downstream state.
5. Preview returns the same executable plan consumed by the runtime.
6. Historical replay can reproduce the saved plan without consulting the current Flow editor state.

Until then, the product must continue to describe Input, Prompt, and Output as compiled contributions around one AI Task Provider boundary.
