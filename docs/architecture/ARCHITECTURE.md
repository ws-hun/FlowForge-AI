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

Flow execution compilation is isolated in `FlowExecutionCompiler`. It converts one immutable Flow snapshot into the exact Provider input, execution mode, call count, compiler version, SHA-256 input fingerprint, structured preview sections, and a versioned deterministic node execution plan used by both preview and execution paths.

The current `flow-plan-v3` contract uses linear scheduling and identifies Input, Prompt, AI Task, and Output responsibilities together with stable input/output artifact contracts. AI Task is the only Provider boundary, so the number of boundary steps must remain equal to the persisted Provider call count. See [FLOW_RUNTIME.md](./FLOW_RUNTIME.md).

Each modern node trace is paired with an independently persisted `flow_node_artifacts` record. Materialized Input and Prompt artifacts store variable-resolved text, AI Task artifacts store Summary plus Result, and Output artifacts store the Result document. Payload fingerprints must match the immutable trace before the transaction can commit. Failed and skipped nodes never receive fabricated payloads or fingerprints, while legacy v1 and v2 records remain readable without synthesized artifacts.

`FlowNodeArtifactService` owns atomic artifact materialization, while `FlowNodeArtifactQueryService` exposes ordered metadata and one addressable payload at a time. Successful runs write Task and artifacts in the execution transaction. `TaskFailureRecorder` writes failed Task and artifact states together in one `REQUIRES_NEW` transaction so partial failure history cannot survive.

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
