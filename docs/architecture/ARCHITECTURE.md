# FlowForge AI - Architecture

## 1. System Overview

FlowForge is a modular AI workspace platform built with:

- Frontend: Vue 3 + TypeScript
- Backend: Spring Boot + Java 17
- Database: PostgreSQL
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