# FlowForge AI - Product Roadmap

This document defines the active product direction for FlowForge.

FlowForge is an AI Native Workspace. The roadmap must always move the product toward creation, execution, reuse, and workflow composition.

---

## Current Product Stage

FlowForge is currently in:

Stage 3: Workflow Builder

Stage 1 and Stage 2 are now the reusable foundation:

- Stage 1 provides structured AI task execution, Provider configuration, provenance, token usage, and history.
- Stage 2 provides reusable Prompt assets, variables, history, and version recovery.
- Stage 3 connects those assets into persisted, executable Flows.

The current Workflow runtime compiles a saved Flow snapshot into one deterministic Provider request. It must not be presented as a true multi-step engine until node-level execution is implemented on the backend.

---

## Stage 1: AI Task Execution

Goal:

Users can describe a task, execute it through an AI provider, receive structured output, and keep execution history.

Done means:

- A user can run an AI task.
- The result is structured enough to review.
- The execution is saved as history.
- API keys are configured outside source code.

Stage 1 is the foundation, not the final product shape.

---

## Stage 2: Prompt Library

Goal:

Users can save, discover, improve, and reuse proven AI work patterns.

The library should feel like a creative asset space, not a data management screen.

Core user jobs:

- Save a useful Prompt as a reusable asset.
- Find the right Prompt quickly.
- Fill Prompt variables for the current context.
- Send the prepared Prompt into AI Command Workspace.
- Use execution results to improve future Prompts.

Stage 2 priority order:

1. Prompt reuse flow
2. Prompt variable filling
3. Prompt detail and preview
4. Starter Prompt quality
5. Prompt usage history
6. Prompt versioning

Out of scope for Stage 2:

- Dense management tables
- Team permission systems
- Analytics-first screens
- Complex workflow graphs

---

## Stage 3: Workflow Builder

Goal:

Users can connect Prompts, AI tasks, knowledge context, and output steps into executable flows.

Prompt assets from Stage 2 become Workflow nodes.

The first Workflow Builder should be a calm canvas, not an automation admin system.

Current Stage 3 capabilities:

- Persisted Input, Context, Prompt, AI Task, and Output nodes.
- Flow variables with preflight validation and atomic rename.
- Server-compiled execution previews.
- Immutable run snapshots with source, Provider, model, token usage, and execution duration.
- Reuse through run settings, snapshot branching, result-to-Prompt, exact historical reruns, run comparison, and result continuation.
- Prompt assets created from results or Flow nodes preserve their immutable source and can reopen the original work.
- Historical Prompt revisions can branch into independent assets without replacing the current Prompt.
- Prompt revision previews explain field, tag, variable, and content changes before restore or branching, and compare against the visible editor state when local edits are still pending.
- Prompt creation and editing guard unsaved work across dialog close, navigation, and browser unload.
- Historical Flow revisions can branch into independent, source-linked Flow assets without restoring over the current draft.
- Flow revision previews compare against the visible editor state, include pending local edits, and distinguish true node reordering from simple node additions or removals.
- Flow run snapshots preserve derived Flow lineage so historical results remain explainable after further editing.
- Failed Provider executions preserve their context and remain recoverable from History.
- Workspace continuation paths reopen the active Flow, continue the latest successful Result, and deep-link into recent Prompt assets without turning the home screen into a dashboard.
- Global creation search provides one calm entry point for AI Command, Flow Space, Prompt assets, and immutable Results without introducing dense navigation.
- Local workspace identity preferences keep Settings, Profile, and the global shell consistent, while explicitly reporting session-only fallback when browser persistence is unavailable and never pretending that remote synchronization exists.
- Future modules remain outside the primary navigation and use honest workspace boundaries that route users back to currently executable creation paths instead of presenting fake Agent, Knowledge, or Analytics state.
- Route-level code splitting and scoped Element Plus registration keep the calm workspace shell lightweight while loading complex creation surfaces only when opened.
- Docker readiness follows the real dependency chain from PostgreSQL to the backend database probe and then to the Nginx frontend proxy.
- The global shell surfaces one calm readiness signal that combines backend/database health with active Provider availability and remains actionable without dashboard noise.
- Provider Vault protects destructive actions with explicit confirmation, preserves unsaved key input when a save request fails, and returns client-correct `400` / `404` errors for invalid or missing configurations.
- Provider Base URLs are normalized and validated as safe absolute HTTP/HTTPS endpoints while preserving legitimate custom OpenAI-compatible hosts.
- Provider API Keys are encrypted at rest with AES-256-GCM, using a Git-ignored local key file or an injected production key while preserving legacy plaintext readability during migration.
- PostgreSQL schema changes are versioned through Flyway while Hibernate remains in validation mode, making local and Docker upgrades deterministic without changing the workspace experience.
- Saved Flow definitions enforce unique node identity, supported node types, and the honest single-call Input / Prompt / AI Task / Output execution shape before a revision can enter the workspace.
- Flow execution compilation is isolated as one deterministic server module shared by preview, readiness checks, persisted traces, and the current single Provider call; preview and trace contracts explicitly identify this runtime as `single-pass`.
- Flow execution previews and persisted run traces expose a versioned compiler contract and SHA-256 fingerprint of the exact Provider input, while legacy runs keep unknown compiler versions honest and exact reruns fingerprint the stored input without recompiling it.
- Direct Flow runs receive their persisted Task identity before Provider execution, so successful and failed traces use one stable run ID; exact historical reruns are explicitly marked as stored-input replays and retain the source run identity without changing legacy traces.
- Provider gateway errors return the saved failed run identity only after failure persistence succeeds, allowing AI Command and Flow Space to reopen the exact immutable failure context instead of locating it through timestamps or transient client state.
- Run comparison explains whether two results used the same Provider input, preferring persisted SHA-256 fingerprints and explicitly falling back to exact stored input text for legacy records instead of overstating verification.
- Runtime contract tests lock preview and execution to the same exact Provider input and fingerprint, preserve modern run identity through JSON round trips, and keep legacy trace fields explicitly unknown until a real runtime supplied them.
- Flow previews and immutable run traces now share `flow-plan-v4`, a deterministic linear node plan that records dependencies, node responsibilities, independently addressable artifact contracts, `compiled-reference` input resolution, and the single AI Task Provider boundary while keeping legacy plans readable.
- Successful and failed Flow runs atomically persist one `node-artifact` record per planned node. Materialized payloads are fingerprint-verified and inspectable inside the run trace, while skipped and failed outputs remain explicitly payload-free.
- Current node artifacts preserve navigable upstream lineage with key, contract, storage, state, resolution, and available content fingerprint. This explains the single compiled request without claiming that downstream nodes already load persisted payloads or invoke the Provider independently.
- Flow Space explains each selected node's runtime role and predecessor context during creation, so users can design the execution path without mistaking compiled Input, Prompt, or Output nodes for independent model calls.
- Provider HTTP calls use explicit configurable connect and read timeouts, convert transport failures into stable gateway errors, and preserve failed runs for recovery instead of hanging the workspace indefinitely.
- Provider HTTP status failures are translated into actionable authentication, rate-limit, timeout, request, or availability messages without exposing raw upstream response bodies to the workspace or History.
- Provider results that violate the string contract with a JSON object or array are deterministically converted into readable Markdown while preserving the Provider response for raw inspection.
- Every successful Result document can be copied or downloaded as portable Markdown from AI Command, Flow Space, comparison, and History surfaces, carrying available Provider, model, token, and duration provenance with it.
- Historical Result strings produced before structured formatting are detected at presentation time, rendered as readable documents, and keep their original JSON available without mutating immutable run data.
- Result rendering preserves headings, grouped lists, fenced code, inline emphasis, code tokens, quotes, and document dividers through a tested safe-text parser instead of flattening useful AI output structure.
- Provider Vault can verify any saved configuration against its `/models` endpoint without creating a Task run, exposing plaintext credentials, or pretending the result is persisted health telemetry.
- Prompt and Flow mutation endpoints preserve REST semantics by separating missing assets (`404`) and invalid input (`400`) from actual Provider gateway failures (`502`).
- Global error handling isolates `AiExecutionException` as `502`, reports internal state failures as non-leaking `500` responses, and keeps missing Task execution sources on `404`.
- Flow asset deep links restore a specific saved Flow from Workspace, Task, Prompt, History, revision, and snapshot reuse paths while preserving unsaved-edit protection.
- Flow Space restores the last active Flow across reloads, repairs stale selections after deletion, and remains usable when browser persistence is unavailable.
- Prompt asset deep links keep Library cards, source Prompt navigation, AI Command return paths, revision branches, Drawer state, and browser history aligned to one saved Prompt.
- Result deep links restore and focus one immutable run from AI Command, Prompt, Flow, lineage, and History while keeping source Prompt and Flow assets reachable.
- Any successful historical Result can become a source-linked reusable Prompt pattern or a new editable Flow without first replacing the current AI Command result.
- Prompt-backed Flow nodes expose their Library source, distinguish synchronized snapshots from diverged content, and require explicit revision-preserving replacement to adopt the latest Prompt.
- Flow node deep links restore a specific node together with its Flow, allowing Prompt and Result provenance paths to return to the exact creation context.
- Flow run deep links restore a specific immutable run together with its Flow and node, including older runs outside the recent-run window.
- Every historical run with an available Flow can reopen that run directly in Flow Space, even when the older record predates persisted node traces.
- Run Brief drafts preserve per-Flow runtime context and variable values locally across Flow switching and browser reloads without presenting unexecuted input as server history.
- A useful Run Brief can become a persisted Context node in one action, moving temporary execution knowledge into the reusable Flow without duplicating it in the next run.
- Flow Run Brief edits remain synchronized when work moves between Flow Space and AI Command, including return, detach, and execution paths.
- Flow Space and AI Command share one server-compiled execution preview, so users can inspect the exact Provider input from either workspace before running.
- Execution previews expose the ordered Flow contributions, readiness issues, and final Raw input while remaining one deterministic Provider request.
- Persisted Flow Run Traces preserve the server-authored state of every saved node for successful and failed direct Flow runs, while explicitly recording that the current runtime uses one shared Provider call.
- Historical Flow Run snapshots explain how the visible Flow editor state and Run Brief have changed since execution across goals, saved or pending node content, membership, order, runtime context, and variables without altering the immutable result.
- Reusing historical Flow Run settings remains one action for empty or unchanged briefs, while protecting meaningful current context with an explicit replacement decision.
- Failed Flow runs can be reopened in Flow Space, restore their persisted node states, rerun the exact historical input, and compare the immutable failure with its recovery run.
- Every node-backed execution section can reopen its exact Flow node from Flow Space or AI Command, keeping inspection connected to editing.
- Preview readiness issues are actionable: missing variables focus their Run Brief field and incomplete nodes reopen their exact Inspector context.
- History uses readable run titles while preserving the exact stored server input for inspection, copying, failed-run recovery, and run comparison.
- History remains a calm timeline while supporting local discovery across run content, source assets, Provider metadata, Flow runs, Prompt runs, and failures.
- Any historical server input can branch into an editable AI Command variant with explicit run lineage and without inheriting a stale Flow snapshot.
- Flow mutations use server revisions and row-level serialization so concurrent browser sessions cannot silently overwrite a newer Flow, restore an old revision over fresh work, or delete an unseen update.
- Pending Flow metadata and node edits are committed as one complete mutation before navigation, preview, or execution, so one user decision produces one predictable revision instead of intermediate snapshots.
- Prompt mutations use the same revision contract as Flows, preserving local editor input on conflict while preventing stale edits, favorite changes, restores, or deletes from silently replacing a newer creative asset.
- AI Command drafts preserve unexecuted input, source identity, Flow variables, and continuation intent locally across reloads without creating false server History; missing Flow or Result sources degrade to an independent task while retaining usable input.
- Prompt editor drafts reopen unfinished local creation after reload, rebase against the latest asset revision when possible, and become a new Prompt when the original asset no longer exists.
- Prompt preparation drafts preserve variable context per saved Prompt across drawer close, asset switching, and browser reload, then clear after the prepared work enters AI Command.
- Prompt preparation blocks unresolved variables before AI Command handoff, keeping placeholders from becoming accidental Provider input.
- New Flow creation drafts preserve an unfinished idea and selected template locally, keeping pre-asset creation continuous without producing false server state.
- Flow templates provide reusable node structure without replacing a user's custom creation intent, and remain attached until the user explicitly removes them.
- Flow editor drafts restore unfinished metadata and node work to its exact creation context, rebase safely on newer revisions, and can become an independent recovery copy when the original Flow or node no longer exists.

Current Stage 3 priorities:

1. Make Flow creation and editing calmer and more predictable.
2. Preserve complete execution context for comparison and reuse.
3. Improve the Prompt / Flow / Result reuse loop.
4. Design a future `persisted-artifact` resolution contract without changing the honest `single-pass` behavior of existing runs.
5. Introduce true node-level execution only after per-node Provider provenance and explicit stop, skip, and retry policy are real backend contracts.

---

## Product Decision Rules

Before building a feature, ask:

1. Does this help the user create something?
2. Does this help the user execute AI work?
3. Does this help the user reuse previous work?
4. Does the UI feel like a workspace instead of an admin panel?

If the answer is no, do not build it yet.

---

## Current North Star

Turn a user's idea into a reusable, executable AI workflow.
