<div align="center">
  <img src="frontend/src/assets/icons/logo.png" alt="FlowForge AI" width="520" />

  <h3>AI Native Workspace for building executable AI workflows.</h3>
  <p>
    把想法变成可执行、可复用、可持续演进的 AI 工作流。
  </p>

  <p>
    <img alt="Stage" src="https://img.shields.io/badge/Stage-Workflow%20Builder-4F7CFF?style=flat-square" />
    <img alt="Frontend" src="https://img.shields.io/badge/Vue%203-TypeScript-42b883?style=flat-square&logo=vue.js&logoColor=white" />
    <img alt="Backend" src="https://img.shields.io/badge/Spring%20Boot%203-Java%2017-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
    <img alt="Database" src="https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white" />
    <img alt="AI" src="https://img.shields.io/badge/AI-OpenAI%20Compatible-111827?style=flat-square" />
    <img alt="Docker" src="https://img.shields.io/badge/Docker-Ready-2496ED?style=flat-square&logo=docker&logoColor=white" />
  </p>

  <p>
    <a href="#product-overview">Product</a> ·
    <a href="#current-status">Status</a> ·
    <a href="#features">Features</a> ·
    <a href="#architecture">Architecture</a> ·
    <a href="#quick-start">Quick Start</a> ·
    <a href="#api-overview">API</a> ·
    <a href="#roadmap">Roadmap</a>
  </p>
</div>

---

## Product Overview

**FlowForge AI** 是一个 AI Native Workspace，不是 ChatGPT Clone，也不是后台管理系统。

它围绕一个核心目标构建：

> 帮助用户把一个想法，逐步转化为可执行、可复用、可沉淀的 AI Workflow。

```text
Idea -> AI Command -> Structured Result -> Prompt Asset -> Executable Flow -> Reuse
```

FlowForge 关注的不是“管理数据”，而是“创造 AI 工作方式”。用户可以在工作空间中输入任务、执行 AI、保存有效 Prompt、编排 Flow，并把历史结果继续用于下一轮创作。

## What Makes It Different

| Direction | FlowForge AI |
| --- | --- |
| Product Form | AI Native Workspace |
| Core Experience | Create, execute, reuse, compose |
| AI Interaction | Structured task execution, not chat |
| Asset Model | Prompt, Flow, Result, Provider |
| UI Language | Calm, minimal, Apple / Linear / Notion inspired |
| Architecture | Vue 3 + Spring Boot + PostgreSQL |

FlowForge 坚持：

- **Create-first**: 页面优先服务创造，而不是数据管理
- **Workspace-first**: 用户进入的是工作空间，而不是后台系统
- **Reusable AI Work**: 每次有效执行都可以沉淀为 Prompt 或 Flow 资产
- **Workflow-oriented**: AI 是工作流执行引擎，不是单次问答工具
- **Calm UI**: 浅色、留白、克制、低噪音

## Current Status

FlowForge 目前处于 **Stage 3: Workflow Builder** 阶段。

| Stage | Module | Status | Description |
| --- | --- | --- | --- |
| Stage 1 | AI Task Execution | Done | 自然语言任务输入、AI 调用、结构化结果、历史保存 |
| Stage 1 | Provider Vault | Done | 在 UI 中管理 DeepSeek / OpenAI API Key，密钥不写入源码 |
| Stage 2 | Prompt Library | Done | Prompt 创建、编辑、收藏、搜索、变量填充、Starter Pack |
| Stage 2 | Prompt Versioning | Done | Prompt 编辑产生版本快照，支持历史版本恢复 |
| Stage 2 | Prompt Run History | Done | Prompt 关联执行记录可回看 |
| Stage 3 | Workflow Builder | In Progress | Flow 模板、节点编排、Prompt 接入、执行、历史复用 |
| Stage 3 | Prompt-to-Flow Creation | Done | 从已保存 Prompt 或 Starter Prompt 创建保留来源信息的可执行 Flow |
| Stage 3 | Task-to-Flow Creation | Done | 将一次有效 AI Command 执行沉淀为 Prompt，并转化为可继续编辑的 Flow |
| Stage 3 | Flow Result Reuse | Done | Flow 结果可带入下一轮、保存为 Prompt、加入当前 Flow |
| Stage 3 | Node Reuse | Done | Flow 节点可沉淀为 Prompt，也可单独带入 Task 试跑 |
| Stage 3 | Flow Revisions | Done | 每次编辑前保存 Flow 快照，恢复前可预览任意创作节点及其影响范围 |
| Stage 3 | Reproducible Flow Runs | Done | 每次 Flow 执行由服务端根据已保存的节点、目标、Run Brief 和变量值编译；变量可注入 Input / Context / Prompt / AI Task / Output，工作区可在执行前查看同一份服务端输入，历史不受后续编辑或浏览器输入影响 |
| Stage 3 | Configurable AI Execution Guidance | Done | AI Task 节点可保存工作流专属的执行指令，并由服务端编译进预览与真实 AI 调用 |
| Stage 3 | Configurable Output Delivery Focus | Done | Output 节点可定义结果的交付重点，确保同一 Flow 的结果表达可以稳定复用 |
| Stage 3 | Persistent Flow Context | Done | 可在 Flow 中添加独立 Context 节点，将背景、约束或已有材料沉淀为可复用的执行上下文 |
| Stage 3 | Context Ordering | Done | 补充 Context 可在输入区内调整顺序，保存后的排列会直接影响服务端编译给 AI 的上下文顺序 |
| Stage 3 | Flow Run Preflight | Done | Flow Space 与 AI Command 会提示并阻止未填写变量的执行，服务端在调用 Provider 前执行同样校验 |
| Stage 3 | Flow Node Preflight | Done | 空的 Input / Context / Prompt / AI Task / Output 会在画布中标记，真实执行前必须补全内容 |
| Stage 3 | Flow Variable Usage Map | Done | Run Brief 会显示每个变量实际影响的 Input / Context / Prompt / AI Task / Output 节点 |
| Stage 3 | Atomic Flow Variable Rename | Done | 在 Run Brief 中一次重命名变量并同步更新所有使用节点，同时保留本次运行值和修订历史 |
| Stage 3 | Run Snapshot Reuse | Done | 历史运行快照可创建新的可编辑 Flow，并自动带入当次运行上下文 |
| Stage 3 | Run Settings Reuse | Done | 从 Flow Space 或 History 将历史 Run Brief 与仍然匹配的变量值带回原 Flow，快速开始下一次运行 |
| Stage 3 | AI Execution Provenance | Done | 每次执行固定保存真实使用的 AI Provider 与模型，并在结果和历史工作流中展示 |
| Stage 3 | Per-run Token Usage | Done | 从 DeepSeek / OpenAI 响应中读取输入、输出和总 Token，并随运行历史固化 |
| Stage 3 | Exact Historical Rerun | Done | 使用历史中固化的服务端执行输入与 Flow 快照，通过当前 Provider 创建一次新的可比较运行 |
| Stage 3 | Run Lineage & Comparison | Done | 重跑记录保留来源运行关系，并在 History 中并排比较 Provider、Token、摘要与结果 |
| Stage 3 | Historical Result Continuation | Done | 从任意历史结果继续创作，服务端读取固定结果编译新输入并保留继续关系 |
| Stage 3 | Failed Run Recovery | Done | Provider 调用失败时独立保存执行输入、来源、快照和错误信息，并支持从 History 精确重跑 |
| Future | Agents | Preview UI | 产品预留界面，暂未接入真实 Agent Runtime |
| Future | Knowledge Base | Preview UI | 产品预留界面，暂未接入向量检索 |
| Future | Analytics | Preview UI | 轻量洞察预留，暂未做完整数据分析系统 |

## Features

### AI Command Workspace

一个面向 AI 任务执行的命令空间，不是聊天窗口。

| Capability | Status |
| --- | --- |
| 自然语言任务输入 | Done |
| 当前激活 Provider 执行 | Done |
| Summary / Result / Raw JSON 展示 | Done |
| Key Points 前端自动提取 | Done |
| 执行历史保存 | Done |
| AI Provider / Model 执行来源固化 | Done |
| 单次执行 Token 用量记录 | Done |
| 历史运行精确重跑 | Done |
| 重跑来源追踪与结果对比 | Done |
| 历史结果继续创作与来源追踪 | Done |
| 失败执行保存与恢复重跑 | Done |
| 从 Prompt 带入任务 | Done |
| 从 Flow 带入任务 | Done |
| 任务来源上下文提示 | Done |
| 返回来源 Flow / Prompt | Done |
| 脱离来源作为独立任务执行 | Done |
| 当前执行沉淀为 Prompt | Done |
| 当前执行创建可编辑 Flow | Done |

### Provider Vault

API Key 不放在配置文件里，避免上传 GitHub 时泄露密钥。

| Capability | Status |
| --- | --- |
| DeepSeek Provider | Done |
| OpenAI Provider | Done |
| Base URL / Model 配置 | Done |
| 激活当前 Provider | Done |
| Masked Key 回显 | Done |
| 删除 Provider | Done |

### Prompt Library

Prompt Library 是 AI 工作方式资产库，不是普通 Prompt 管理表。

| Capability | Status |
| --- | --- |
| Prompt 创建 / 编辑 / 删除 | Done |
| 分类 / 标签 / 收藏 | Done |
| Gallery 风格卡片浏览 | Done |
| Starter Prompt Pack | Done |
| Prompt 变量识别 | Done |
| Prompt 变量填充 | Done |
| 执行预览 | Done |
| 进入 AI Command Workspace | Done |
| 从 Prompt 创建可执行 Flow | Done |
| Prompt 执行历史 | Done |
| Prompt 版本记录 | Done |
| 历史版本恢复 | Done |

### Workflow Builder

当前重点模块。它是一个轻量 Flow Canvas，用来连接输入、Prompt、AI 执行和结构化输出。

| Capability | Status |
| --- | --- |
| Flow 草稿创建 | Done |
| Flow Templates | Done |
| Product / Engineering / Research / Operations 模板 | Done |
| 模板节点预览 | Done |
| 模板创建 Prompt 节点 | Done |
| Flow 标题和目标编辑 | Done |
| Flow 删除 | Done |
| Flow 变体创建 | Done |
| Input / Prompt / AI Task / Output 节点 | Done |
| 可新增和移除的 Context 节点 | Done |
| Context 节点顺序控制 | Done |
| 从 Prompt Library 添加 Prompt 节点 | Done |
| 从 Prompt 详情创建来源可追溯的 Flow | Done |
| Prompt 节点搜索 | Done |
| Prompt 分类 / 收藏过滤 | Done |
| 防止重复加入同一个 Prompt | Done |
| Prompt 节点编辑 / 删除 / 复制 / 排序 | Done |
| Provider readiness 提示 | Done |
| Run Brief 运行上下文 | Done |
| 服务端执行输入预览 | Done |
| AI Task 执行指令编辑与持久化 | Done |
| AI Task 执行指令参与服务端预览与真实运行 | Done |
| Output 交付重点编辑与持久化 | Done |
| 交付重点参与服务端预览与真实运行 | Done |
| 真实 Flow 运行生命周期反馈（上下文准备 / 单次 AI 调用 / Output 记录） | Done |
| Flow 执行结果展示 | Done |
| Flow 执行历史回看 | Done |
| 历史结果带入当前画布 | Done |
| 最新结果带入下一轮 Run Brief | Done |
| 最新结果保存为 Prompt | Done |
| 最新结果保存并加入当前 Flow | Done |
| 当前节点沉淀为 Prompt 资产 | Done |
| 当前节点单独带入 Task Workspace | Done |
| Flow 变量填写与运行时替换（Input / Context / Prompt / AI Task / Output） | Done |
| Flow 变量影响节点提示 | Done |
| Flow 变量跨节点原子重命名 | Done |
| Flow 变量运行前完整性检查 | Done |
| 带入 AI Command 后继续填写 Flow 变量 | Done |
| Flow 节点内容运行前检查 | Done |
| Flow 创作修订快照 / 恢复前影响预览 | Done |
| 每次 Flow 执行固定保存运行快照 | Done |
| 快照保留节点、Flow 目标、Run Brief 和 Prompt 变量 | Done |
| 服务端从固定快照编译实际 AI 输入 | Done |
| 多个保存的 Input / Context 节点按顺序参与服务端编译 | Done |
| Context 排序同步到服务端实际编译顺序 | Done |
| Flow 带入 Task 后以 Run Brief 继续执行 | Done |
| 从历史运行快照创建新 Flow | Done |
| 新 Flow 自动带入原运行上下文 | Done |
| 历史 Run Brief / 变量值复用到原 Flow | Done |

## Product Modules

| Page | Purpose | Current State |
| --- | --- | --- |
| Workspace | 从一个想法开始创建 AI 工作 | Available |
| AI Command | 执行结构化 AI 任务 | Available |
| Prompt Library | 沉淀和复用 Prompt 资产 | Available |
| Workflows | 编排 Prompt 和 AI 执行为 Flow | Available, active development |
| History | 回看执行历史 | Available |
| API Keys | 管理 AI Provider 密钥 | Available |
| Agents | 智能体产品方向预留 | Preview |
| Knowledge Base | 文档上下文方向预留 | Preview |
| Analytics | 工作洞察方向预留 | Preview |
| Settings | 产品设置入口 | Preview |

## Design Language

FlowForge 的产品 UI 遵循 `docs/core` 下的设计约束。

| Principle | Meaning |
| --- | --- |
| Light Mode First | 暖白背景、柔和灰阶、克制主色 |
| Workspace Layout | 顶部轻导航 + 中央创作区域 |
| Minimal UI Noise | 避免后台系统式密集布局 |
| One Primary Action | 每个页面只强调一个核心动作 |
| Soft Elevation | 12-16px 圆角、柔和阴影 |
| Product-first | 从用户行为设计页面，不从数据库结构设计页面 |

参考气质：

```text
Apple / Linear / Notion / Cursor / Raycast
```

## Architecture

```text
FlowForge AI
├── frontend  Vue 3 + TypeScript + Vite
├── backend   Spring Boot 3 + Java 17
├── database  PostgreSQL
└── ai        OpenAI-compatible HTTP API
```

### Backend

```text
Controller -> Service -> Repository -> Entity
             DTO <-> API Boundary
```

主要服务：

| Service | Responsibility |
| --- | --- |
| `TaskService` | AI 任务执行与历史记录 |
| `TaskFailureRecorder` | 使用独立事务保存失败运行，避免随执行异常回滚 |
| `OpenAiService` | OpenAI-compatible HTTP 调用 |
| `AiApiKeyService` | Provider Key 管理 |
| `PromptService` | Prompt 资产、收藏、版本 |
| `WorkflowService` | Flow 草稿和节点结构 |

核心实体：

```text
tasks
ai_api_keys
prompts
prompt_versions
workflows
flow_versions
```

### Frontend

```text
src
├── api          Axios API clients
├── assets       Logo and visual assets
├── components   Reusable UI components
├── layouts      App shell
├── router       Vue Router
├── stores       Pinia workspace state
├── styles       SCSS tokens and global styles
├── types        TypeScript domain types
└── views        Product pages
```

## Tech Stack

| Layer | Technology |
| --- | --- |
| Frontend | Vue 3, TypeScript, Vite, Vue Router, Pinia |
| UI | Element Plus, SCSS, custom design tokens |
| HTTP | Axios |
| Backend | Java 17, Spring Boot 3.3, Spring Web |
| Persistence | Spring Data JPA, PostgreSQL |
| Validation | Jakarta Bean Validation |
| AI | OpenAI-compatible REST API, DeepSeek / OpenAI |
| Deployment | Docker, Docker Compose, Nginx |

## Quick Start

### Docker Compose

```bash
git clone git@github.com:ws-hun/FlowForge-AI.git
cd "FlowForge AI"
cp .env.example .env
docker compose up --build
```

Services:

```text
Frontend  http://localhost:5173
Backend   http://localhost:8080
Postgres  localhost:5432
```

Default database:

```text
Database  flowforge
Username  flowforge
Password  flowforge
```

### Configure AI Provider

Open:

```text
http://localhost:5173/api-keys
```

DeepSeek default:

```text
Provider  deepseek
Base URL  https://api.deepseek.com
Model     deepseek-chat
```

OpenAI default:

```text
Provider  openai
Base URL  https://api.openai.com/v1
Model     gpt-4o-mini
```

## Local Development

### Requirements

```text
JDK 17+
Maven 3.9+
Node.js 18+
PostgreSQL 16+
```

### Start PostgreSQL Only

```bash
docker compose up postgres
```

### Start Backend

```bash
cd backend
mvn spring-boot:run
```

Backend default:

```text
http://localhost:8080
```

### Start Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend default:

```text
http://localhost:5173
```

Vite dev proxy forwards `/api` to:

```text
http://localhost:8080
```

## Environment

`.env.example` does not contain any real API Key.

```env
POSTGRES_DB=flowforge
POSTGRES_USER=flowforge
POSTGRES_PASSWORD=flowforge

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/flowforge
SPRING_DATASOURCE_USERNAME=flowforge
SPRING_DATASOURCE_PASSWORD=flowforge

FRONTEND_URL=http://localhost:5173
```

AI Provider Key is managed from the Provider Vault UI and stored in PostgreSQL.

## API Overview

### Task

```http
POST /api/tasks/run
POST /api/tasks/{id}/rerun
GET  /api/tasks
```

Request:

```json
{
  "input": "请帮我把一个产品想法拆解为 MVP 方案",
  "promptId": null,
  "flowId": "a-flow-uuid",
  "flowRunContext": "目标用户为早期产品团队，优先输出一周可验证的 MVP。",
  "flowVariableValues": {
    "audience": "产品负责人"
  },
  "continuedFromTaskId": null
}
```

Response:

```json
{
  "summary": "一句话总结",
  "result": "详细结果",
  "raw": "AI 原始返回",
  "provider": "deepseek",
  "model": "deepseek-chat",
  "inputTokens": 820,
  "outputTokens": 430,
  "totalTokens": 1250,
  "rerunOfTaskId": null,
  "continuedFromTaskId": null,
  "executionInput": "服务端实际发送给 AI Provider 的完整输入",
  "taskId": "a-task-uuid",
  "flowRunSnapshot": {
    "flowId": "a-flow-uuid",
    "title": "Idea to MVP",
    "description": "将一个产品想法拆解为可验证的 MVP。",
    "nodes": [],
    "flowUpdatedAt": "2026-07-14T14:30:00",
    "runtimeContext": "目标用户为早期产品团队，优先输出一周可验证的 MVP。",
    "variableValues": {
      "audience": "产品负责人"
    }
  }
}
```

`flowRunSnapshot` 仅在由 `flowId` 发起的运行中返回。服务端从数据库读取当前 Flow 后创建快照，不信任浏览器传入的节点结构；后续编辑、恢复修订或删除来源 Flow 都不会改变这次历史运行的上下文。

`executionInput` 是服务端实际提交给 AI Provider 的输入。Flow 工作区的“查看服务端执行输入”使用同一套编译逻辑，确保用户确认的内容与真实执行一致。

`provider` 与 `model` 是本次执行真正使用的 AI 来源。它们会随 Task 一起持久化，因此之后切换 Provider 或模型不会改写历史执行来源；旧记录没有来源信息时，界面会保持安静并省略该元信息。

`inputTokens`、`outputTokens` 与 `totalTokens` 来自 Provider 的真实 `usage` 响应。FlowForge 会兼容 OpenAI Responses 和 DeepSeek Chat Completions 的字段命名，并在旧记录或 Provider 未返回用量时省略展示。

`POST /api/tasks/{id}/rerun` 不会读取或重新编译当前 Flow，而是复用历史 Task 已固化的服务端执行输入、来源信息和 Flow 快照，再通过当前激活的 Provider 创建一条新运行。这样即使 Flow 后续被编辑，也能对同一份输入进行可比较执行。

重跑生成的新 Task 会通过 `rerunOfTaskId` 指向直接来源运行。History 会基于这条运行谱系提供双文档对比，原运行与本次重跑的 Provider、模型、Token、摘要和结果都保持可见。

从比较界面选择“用此结果继续”后，AI Command 只要求用户填写新的推进方向。请求通过 `continuedFromTaskId` 指向来源 Task，后端从数据库读取其 Summary、Result 和原始来源快照，再编译新的执行输入；浏览器不需要回传或复制完整历史结果。

当 Provider 调用失败时，API 仍按原错误返回 `502 Bad Gateway`，同时使用独立事务在 History 中保存一条 `failed` 运行。失败记录包含服务端执行输入、Provider / Model、Prompt / Flow 来源、运行快照和错误信息，可以直接通过精确重跑恢复。

### Provider

```http
GET    /api/settings/api-keys
POST   /api/settings/api-keys
PATCH  /api/settings/api-keys/{id}/activate
DELETE /api/settings/api-keys/{id}
```

### Prompt

```http
GET    /api/prompts
POST   /api/prompts
PUT    /api/prompts/{id}
PATCH  /api/prompts/{id}/favorite
GET    /api/prompts/{id}/runs
GET    /api/prompts/{id}/versions
POST   /api/prompts/{id}/versions/{versionId}/restore
DELETE /api/prompts/{id}
```

### Flow

```http
GET    /api/flows
POST   /api/flows
PUT    /api/flows/{id}
GET    /api/flows/{id}/runs
POST   /api/flows/{id}/execution-preview
GET    /api/flows/{id}/versions
POST   /api/flows/{id}/versions/{versionId}/restore
DELETE /api/flows/{id}
```

`POST /api/flows/{id}/execution-preview` 只读取已保存的 Flow，不调用 AI Provider，也不会创建任务或写入历史。它接收本次 `runtimeContext` 与 `variableValues`，返回服务端编译的 `executionInput` 和对应的 `flowRunSnapshot`。

## Validation

Frontend:

```bash
cd frontend
npm run typecheck
npm run build
```

Backend:

```bash
cd backend
mvn test
```

Current known non-blocking frontend build warnings:

- Sass legacy JS API deprecation
- Rollup PURE annotation warning from `@vueuse/core`
- Vite chunk size warning

## Security

FlowForge avoids storing real AI API keys in source code.

| Rule | Status |
| --- | --- |
| No API Key in `.env.example` | Done |
| No API Key in `application.yml` | Done |
| API Key managed from UI | Done |
| Masked Key returned to frontend | Done |
| Provider activation stored in DB | Done |

If GitHub Push Protection reports a leaked key, do not bypass it. Remove the secret from Git history, revoke the old key from the provider, and create a new key.

## Troubleshooting

### `ERR_CONNECTION_REFUSED`

Usually the backend is not running.

```bash
curl http://localhost:8080/api/tasks
```

Start backend:

```bash
cd backend
mvn spring-boot:run
```

### Vite proxy error: `/api/... ECONNREFUSED`

Frontend is running, but `localhost:8080` is not reachable.

Check:

- Spring Boot is running
- Backend port is `8080`
- PostgreSQL is running
- Database username/password are correct

### AI execution fails

Check:

- API Key has been added in `/api-keys`
- Provider has been activated
- Base URL is correct
- Model name is valid
- API Key is still active

## Roadmap

### Near Term

- Workflow Builder node experience polish
- 面向多步执行引擎的真实节点追踪
- Prompt / Flow 复用闭环细化
- More complete onboarding and empty states

### Mid Term

- Knowledge Base with real document context
- Agent Runtime prototype
- Workflow Execution Graph
- Prompt version diff
- Fine-grained run logs

### Long Term

- MCP integration
- Plugin ecosystem
- Team workspace
- Role-based collaboration
- Cloud deployment
- Observability and cost control

## Project Documents

Product and design constraints live in `docs/core`.

```text
docs/core/PRODUCT_CONTEXT.md
docs/core/PRODUCT_VISION.md
docs/core/DESIGN_SYSTEM.md
docs/core/DEVELOPMENT_GUIDE.md
docs/core/PRODUCT_ROADMAP.md
```

Architecture reference:

```text
docs/architecture/ARCHITECTURE.md
```

## Design Principle

Every screen in FlowForge must answer one of these questions:

```text
What can the user create here?
What can the user execute here?
What can the user reuse here?
```

If a page only displays data, it is not the core FlowForge experience.

## License

This project is under active development. License to be decided.
