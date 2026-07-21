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
- Immutable run snapshots with source, Provider, model, and token usage.
- Reuse through run settings, snapshot branching, result-to-Prompt, exact historical reruns, run comparison, and result continuation.

Current Stage 3 priorities:

1. Make Flow creation and editing calmer and more predictable.
2. Preserve complete execution context for comparison and reuse.
3. Improve the Prompt / Flow / Result reuse loop.
4. Introduce true node-level execution only when the backend runtime is real.

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
