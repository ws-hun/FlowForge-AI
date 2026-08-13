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

Flow execution compilation is isolated in `FlowExecutionCompiler`. It converts one immutable Flow snapshot into the exact Provider input, execution mode, call count, compiler version, SHA-256 input fingerprint, and structured preview sections used by both preview and execution paths.

Direct Flow execution assigns the persisted Task UUID before the Provider request. The same UUID becomes the run identity in successful or failed traces, while exact reruns record `stored-input-replay` and the immutable source Task UUID instead of presenting the replay as a newly compiled Flow input.

When Provider execution fails, `TaskService` persists the failed Task in a separate transaction and attaches its UUID to the `AiExecutionException` only after that write succeeds. The `502` response can therefore expose an optional `runId` that always refers to a real recoverable History entry.

The runtime contract test suite requires one saved Flow snapshot and Run Brief to produce byte-for-byte identical preview, Provider, persisted Task, and response inputs. It also requires the preview fingerprint and persisted trace fingerprint to match, preventing preview and execution paths from drifting independently.

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
