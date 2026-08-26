# FlowForge AI - Architecture

## 1. System Overview

FlowForge is a modular AI workspace platform built with:

- Frontend: Vue 3 + TypeScript
- Backend: Spring Boot + Java 17
- Database: PostgreSQL
- Schema migrations: Flyway
- AI Layer: OpenAI-compatible APIs

---

## 2. Architecture Principles

- Separation of concerns
- Modular design
- UI independent from business logic
- API-first backend
- Component-driven frontend

---

## 3. Frontend Structure

src/
  assets/
  components/
  layouts/
  views/
  router/
  stores/
  services/
  styles/

---

## 4. Core Modules

### Workspace Module
- Main entry of product
- Idea input and execution

### Task Module
- AI task execution
- Result rendering

### Prompt Library
- Prompt storage and reuse

### Workflow Module
- Workflow definition (future canvas)

### History Module
- Execution history tracking

---

## 5. Backend Structure

controller/
service/
repository/
entity/
dto/
config/

Authentication is implemented as a small boundary around the single local workspace: `AuthController` exposes first-run setup, login, status, and logout; `AuthService` hashes passwords with BCrypt and stores only SHA-256 session token digests; `AuthWebConfig` protects every non-public `/api/**` route and validates non-GET request origins. Existing creative assets intentionally remain workspace-scoped until a future multi-user tenancy design can add ownership and permission contracts consistently.

Public authentication routes use an exact allowlist rather than the `/api/auth/**` prefix. Owner profile and password endpoints require the same workspace session as creative APIs. A password change verifies the current BCrypt credential, deletes every existing session for the owner, and issues one fresh cookie so stale browsers cannot retain access.

Flow execution compilation is isolated in `FlowExecutionCompiler`. It converts one immutable Flow snapshot into the exact Provider input, execution mode, call count, compiler version, SHA-256 input fingerprint, structured preview sections, and a versioned deterministic node execution plan used by both preview and execution paths.

The current `flow-plan-v4` contract uses linear scheduling and identifies Input, Prompt, AI Task, and Output responsibilities together with stable input/output artifact contracts and a versioned input resolution method. AI Task is the only Provider boundary, so the number of boundary steps must remain equal to the persisted Provider call count. New plans also carry `flow-failure-policy-v1`: stop the run on Provider failure, skip downstream nodes, make one attempt, and perform no automatic retry. See [FLOW_RUNTIME.md](./FLOW_RUNTIME.md).

The same plan carries `flow-input-resolution-v1`: the active mode is `compiled-reference`; `persisted-artifact` is declared but disabled behind the future `node-sequential-runtime`. This keeps the architectural migration explicit in preview and trace payloads without changing current runtime behavior.

Each modern node trace is paired with an independently persisted `flow_node_artifacts` record. Materialized Input and Prompt artifacts store variable-resolved text, AI Task artifacts store Summary plus Result, and Output artifacts store the Result document. Payload fingerprints must match the immutable trace before the transaction can commit. Current artifacts also preserve upstream key, contract, state, resolution, and content fingerprint so lineage can be navigated without inventing old history. Failed and skipped nodes never receive fabricated payloads or fingerprints, while legacy v1, v2, and v3 records remain readable with genuinely absent fields.

The `provider-result` artifact at the single AI Task boundary also preserves the real Provider invocation provenance. Each new run writes exactly one independent `flow_provider_attempts` record as `initial #1`, including completion state, Provider, model, available input/output/total tokens, server-measured duration, and a sanitized failure message. Successful and failed attempts are committed atomically with their Task and artifacts. Token values remain null when the Provider does not report usage. Input, Prompt, and Output artifacts never receive fabricated attempts.

Artifact detail exposes `flow-provider-attempt-policy-v1`. The policy validates contiguous attempt numbering, immediate predecessor identity, and the rule that only a failed attempt can transition to a retry or recovery attempt. The current runtime disables automatic retry and same-artifact recovery; failed-run recovery creates a new immutable Task instead of mutating the original artifact chain.

Artifact detail returns the complete ordered attempt history on demand. Artifact collections and lineage return only the latest `providerCall` compatibility summary and resolve those summaries in one batch query rather than one query per artifact. V7 deterministically backfills provenance written by V6 as `initial #1`; records that predate real provenance remain absent rather than being reconstructed from current configuration.

`FlowNodeArtifactService` owns atomic artifact materialization and validates that every current upstream reference resolves to an earlier planned output. `FlowNodeArtifactQueryService` exposes ordered metadata, one addressable payload at a time, and a metadata-only lineage path with explicit termination states. Successful runs write Task and artifacts in the execution transaction. `TaskFailureRecorder` writes failed Task and artifact states together in one `REQUIRES_NEW` transaction so partial failure history cannot survive.

The only current input resolution is `compiled-reference`: lineage describes how the immutable snapshot contributed to one compiled Provider request. The runtime does not load each predecessor payload from PostgreSQL and still performs exactly one Provider call. `FlowExecutionCompiler` validates the persisted failure policy against node terminal states before trace or artifact persistence. Attempt persistence makes the existing boundary inspectable and prepares a future retry/recovery identity model, but the runtime creates no retry attempts, keeps `providerCallCount = 1`, and does not activate `node-sequential` execution.

Direct Flow execution assigns the persisted Task UUID before the Provider request. The same UUID becomes the run identity in successful or failed traces, while exact reruns record `stored-input-replay` and the immutable source Task UUID instead of presenting the replay as a newly compiled Flow input.

When Provider execution fails, `TaskService` persists the failed Task in a separate transaction and attaches its UUID to the `AiExecutionException` only after that write succeeds. The `502` response can therefore expose an optional `runId` that always refers to a real recoverable History entry.

The runtime contract test suite requires one saved Flow snapshot and Run Brief to produce byte-for-byte identical preview, Provider, persisted Task, and response inputs. It also requires the preview fingerprint and persisted trace fingerprint to match, the preview and trace execution plans to be identical, plan step order to match persisted node trace order, and persisted node payloads to match their trace fingerprints.

---

## 6. API Design Principles

- RESTful APIs
- Clear separation of domain logic
- No UI coupling
- Stateless services

---

## 7. Data Model Principles

- Avoid over-normalization in early stage
- Optimize for iteration speed
- Keep schema flexible
- Version every PostgreSQL schema change with an immutable Flyway migration
- Keep Hibernate in validation mode; application startup must never mutate schema implicitly

---

## 8. AI Integration Layer

AI services are abstracted:

AIService →
  OpenAI
  Claude
  Local LLM
  Future MCP providers

---

## 9. Scalability Vision

Future expansions:

- Workflow Engine
- Plugin System
- MCP Integration
- Multi-user support
- Cloud deployment

---

## 10. Development Rules

- Do NOT design UI from database structure
- Always design from user workflow first
- Avoid admin-style API exposure
- Keep frontend product-driven, not data-driven
