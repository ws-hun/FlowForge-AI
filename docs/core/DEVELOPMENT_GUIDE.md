# FlowForge AI - Development Guide

## 1. Purpose

This document defines how we build FlowForge AI.

It is NOT documentation.

It is a set of strict development rules that every contributor and AI agent must follow.

---

## 2. Core Principle

We do not build a backend system.

We build a product.

Every line of code must serve the product experience.

---

## 3. Product-First Rule (MOST IMPORTANT)

Before writing any code, always ask:

"What user problem does this solve?"

If the answer is unclear, do NOT implement it.

Never build features just because they are easy to implement.

---

## 4. Forbidden Patterns

The following patterns are NOT allowed:

❌ CRUD-first design  
❌ Database-driven UI  
❌ Admin dashboard style pages  
❌ Tables as primary UI  
❌ KPI/statistic-driven screens  
❌ Over-engineered enterprise architecture  
❌ Copying Ant Design Pro / Ruoyi / Element Admin style

If a design resembles an admin system, it must be redesigned.

---

## 5. UI Design Rules

All UI must follow:

- Workspace-first layout
- Minimal visual noise
- One primary action per screen
- Large whitespace
- Soft shadows
- Calm visual hierarchy

UI should feel like:

✔ Linear  
✔ Notion  
✔ Cursor  
✔ Apple

NOT:

✘ ERP systems  
✘ Admin panels  
✘ Data management tools

---

## 6. Code Structure Rules

### Frontend

- Components must be reusable and atomic
- Each component should have a single responsibility
- Avoid tightly coupled UI logic
- Keep business logic separate from UI

### Backend

- Keep service layer clean and independent
- No UI-related logic in backend
- API must be product-oriented, not database-oriented

---

## 7. Page Design Rules

Every page must answer ONE of these:

- What can the user create here?
- What can the user execute here?
- What can the user explore here?

If a page only shows data, it is NOT allowed.

---

## 8. Feature Design Rule

Every feature must satisfy:

- Helps user create something
- Or helps user execute an AI workflow
- Or helps user reuse previous work

If a feature is only for "management", it should be removed.

---

## 9. Naming Conventions

Avoid:

- manager
- admin
- dashboard
- listView
- dataTable

Prefer:

- workspace
- flow
- task
- prompt
- agent
- library

---

## 10. AI Integration Rule

AI is not a feature.

AI is the core execution engine.

All AI interactions must be:

- structured
- reusable
- workflow-oriented

Not just chat-based responses.

---

## 11. Iteration Rule

We build in stages:

1. UI-first (make it beautiful)
2. Interaction design (make it usable)
3. Function integration (make it real)
4. Optimization (make it scalable)

Never start from backend implementation.

---

## 12. Definition of Done

A feature is only complete when:

- It improves user creation experience
- It fits the design system
- It does NOT introduce admin-style UI
- It feels like a real product feature

---

## 13. Final Principle

If a design feels like a backend system, reject it.

If a design feels like a product, continue.

Always optimize for:

👉 Clarity  
👉 Creation  
👉 Calmness  
👉 Focus