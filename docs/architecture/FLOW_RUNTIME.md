# Flow Runtime Contract

## 1. Current Runtime

FlowForge currently executes saved Flows in `single-pass` mode.

The backend reads one immutable Flow snapshot, applies Run Brief variables, compiles every saved node into one exact Provider input, and performs one Provider call.

This is a real and reproducible runtime. It is not yet a node-level execution engine.

## 2. Versioned Contracts

The runtime exposes two independent versions:

- `flow-compiler-v1` defines how the exact Provider input is produced.
- `flow-plan-v1` defines how saved nodes are ordered and what responsibility each node has.

The compiler also produces a SHA-256 fingerprint of the exact UTF-8 Provider input. Preview, Provider invocation, persisted Task input, and Run Trace must agree on that value.

## 3. Execution Plan

`flow-plan-v1` uses deterministic linear scheduling.

Each step contains:

- Stable sequence number.
- Saved node ID and node type.
- Runtime operation.
- Direct predecessor dependency.
- Whether the step is the Provider boundary.

The current operations are:

| Node | Operation | Runtime meaning |
| --- | --- | --- |
| Input | `supply-context` | Contributes saved context to the compiled input. |
| Prompt | `supply-instructions` | Contributes reusable instructions to the compiled input. |
| AI Task | `invoke-provider` | The only Provider call boundary. |
| Output | `define-delivery` | Contributes delivery constraints to the same Provider request. |

The plan order must match the immutable node snapshot and the persisted node trace order. The number of Provider boundary steps must match `providerCallCount`.

## 4. Persistence And Replay

New direct Flow runs persist their execution plan inside `flowRunTrace` for both successful and failed Provider calls.

Legacy traces without an execution plan remain valid and deserialize with `executionPlan = null`. The frontend labels these records as legacy instead of synthesizing a plan that did not exist at run time.

Exact historical reruns use the stored Task input. They do not recompile the old Flow. When the source trace has a plan, the new replay preserves that immutable plan and records `stored-input-replay` plus the source Task ID.

## 5. Upgrade Boundary

FlowForge must not switch a Flow to `node-sequential` until all of the following are real backend capabilities:

1. Every executable node has an explicit input and output contract.
2. Node outputs are persisted independently and can feed dependent nodes.
3. Provider calls are recorded per node with model, token, duration, and error provenance.
4. Failure policy defines stop, skip, and retry behavior without inventing successful downstream state.
5. Preview returns the same executable plan consumed by the runtime.
6. Historical replay can reproduce the saved plan without consulting the current Flow editor state.

Until then, the product must continue to describe Input, Prompt, and Output as compiled contributions around one AI Task Provider boundary.
