# FlowForge AI

FlowForge AI is an AI Native Workspace for turning ideas into executable, reusable AI workflows.

它不是 ChatGPT Clone，也不是后台管理系统。FlowForge 的目标是提供一个安静、现代、可持续演进的 AI 工作空间：用户从一个想法开始，执行 AI 任务，沉淀 Prompt 资产，再把这些资产编排成可复用的 Flow。

## Product Positioning

**FlowForge AI = AI Workflow Creation Platform**

核心路径：

```text
Idea -> AI Command -> Structured Result -> Prompt Asset -> Executable Flow -> Reuse
```

当前产品方向遵循：

- Create-first: 先创造，再管理
- Workspace-first: 页面围绕工作流创作，不围绕数据表
- Calm UI: 浅色、克制、留白、低噪音
- Reusable AI Work: 把一次有效执行沉淀为可复用资产
- Workflow-oriented: AI 不是聊天窗口，而是工作流执行引擎

## Tech Stack

### Frontend

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Axios
- Element Plus
- SCSS

### Backend

- Java 17
- Spring Boot 3.3
- Spring Web
- Spring Data JPA
- Bean Validation
- PostgreSQL
- Lombok

### AI Provider

- OpenAI-compatible HTTP API
- DeepSeek supported
- OpenAI supported
- API Key managed in UI and stored in PostgreSQL
- No AI SDK dependency

### Deployment

- Docker
- Docker Compose
- Nginx for frontend container
- PostgreSQL 16 container

## Current Development Status

FlowForge 已经从最初的 AI Task MVP 演进到 **Workflow Builder 阶段**。下面是当前真实进度。

| Stage | Module | Status | Notes |
| --- | --- | --- | --- |
| Stage 1 | AI Task Execution | Done | 可输入任务、调用 AI Provider、返回结构化结果、保存历史 |
| Stage 1 | Provider Vault | Done | 前端管理 API Key，支持 DeepSeek / OpenAI，密钥不写入源码 |
| Stage 2 | Prompt Library | Done | Prompt 创建、编辑、收藏、搜索、Starter Pack、变量填充、执行预览 |
| Stage 2 | Prompt Versioning | Done | Prompt 编辑产生版本快照，支持历史版本恢复 |
| Stage 2 | Prompt Run History | Done | 可查看 Prompt 相关执行记录 |
| Stage 3 | Workflow Builder | In Progress | Flow 草稿、节点编排、Prompt 接入、执行、历史复用已可用 |
| Stage 3 | Flow Result Reuse | Done | Flow 结果可带入下一轮、保存为 Prompt、保存并加入 Flow |
| Stage 3 | Node Reuse | Done | Flow 节点可沉淀为 Prompt，也可单独带入 Task 试跑 |
| Future | Agents | Preview UI | 当前为产品预留界面，尚未接入真实 Agent Runtime |
| Future | Knowledge Base | Preview UI | 当前为信息架构占位，尚未接入向量检索 |
| Future | Analytics | Preview UI | 当前为轻量洞察，尚未做完整数据分析系统 |

## Core Features

### 1. AI Command Workspace

面向任务执行的 AI 命令空间，不是聊天界面。

已支持：

- 输入自然语言任务
- 选择当前激活 Provider
- 调用 AI 生成结构化结果
- 展示 Summary / Key Points / Result / Raw JSON
- Key Points 由前端文档组件从 Result 中提取
- 自动保存执行历史
- 从 Prompt 或 Flow 带入任务来源

核心接口：

```http
POST /api/tasks/run
GET  /api/tasks
```

### 2. Provider Vault

API Key 不放在配置文件中，避免上传 GitHub 时泄露密钥。

已支持：

- 在前端添加 API Key
- DeepSeek / OpenAI Provider 切换
- Base URL 和 Model 配置
- 激活当前 Provider
- 删除 Provider
- 密钥保存后只回显 masked key

核心接口：

```http
GET    /api/settings/api-keys
POST   /api/settings/api-keys
PATCH  /api/settings/api-keys/{id}/activate
DELETE /api/settings/api-keys/{id}
```

### 3. Prompt Library

Prompt Library 不是 Prompt 管理表，而是 AI 工作方式资产库。

已支持：

- Prompt 创建、编辑、删除
- 分类、标签、收藏
- Gallery 风格卡片浏览
- Starter Prompt Pack
- Prompt 变量识别与填充
- 执行预览
- 一键进入 AI Command Workspace
- Prompt 执行历史
- Prompt 版本记录
- 历史版本恢复

核心接口：

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

### 4. Workflow Builder

Workflow Builder 是当前重点阶段。它不是自动化后台，而是一个轻量 Flow Canvas，用来把 Prompt、输入、AI 执行和输出连接起来。

已支持：

- 创建 Flow 草稿
- 编辑 Flow 标题和目标
- 删除 Flow
- 创建 Flow 变体
- Flow 节点画布
- Input / Prompt / AI Task / Output 节点结构
- 从 Prompt Library 添加 Prompt 节点
- Prompt 节点搜索、分类过滤、收藏过滤
- 防止重复加入同一个 Prompt
- Prompt 节点编辑、删除、复制、排序
- Flow 执行前 Provider readiness 提示
- Run Brief 运行上下文
- AI 输入预览
- Flow 执行过程状态
- Flow 执行结果文档化展示
- Flow 执行历史回看
- 历史结果带入当前画布
- 最新结果带入下一轮 Run Brief
- 最新结果保存为 Prompt
- 最新结果保存并加入当前 Flow
- 当前节点沉淀为 Prompt 资产
- 当前节点单独带入 Task Workspace 试跑

核心接口：

```http
GET    /api/flows
POST   /api/flows
PUT    /api/flows/{id}
GET    /api/flows/{id}/runs
DELETE /api/flows/{id}
```

## Product Experience

FlowForge 的界面方向是 **AI Native Workspace**：

- 顶部轻导航
- 中央 Workspace
- 浅色暖白背景
- 柔和阴影
- 卡片和文档式结果展示
- 少表格、少后台感
- 强调创建、执行和复用

设计参考：

- Apple
- Linear
- Notion
- Cursor
- Raycast

项目中 `docs/core` 是产品和设计约束的最高优先级文档：

```text
docs/core/PRODUCT_CONTEXT.md
docs/core/PRODUCT_VISION.md
docs/core/DESIGN_SYSTEM.md
docs/core/DEVELOPMENT_GUIDE.md
docs/core/PRODUCT_ROADMAP.md
```

## Project Structure

```text
FlowForge AI
├── backend
│   ├── src/main/java/com/flowforge/ai
│   │   ├── config
│   │   ├── controller
│   │   ├── dto
│   │   ├── entity
│   │   ├── repository
│   │   └── service
│   ├── src/main/resources/application.yml
│   ├── Dockerfile
│   └── pom.xml
├── frontend
│   ├── src
│   │   ├── api
│   │   ├── assets
│   │   ├── components
│   │   ├── layouts
│   │   ├── router
│   │   ├── stores
│   │   ├── styles
│   │   ├── types
│   │   └── views
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── docs
│   ├── architecture
│   └── core
├── docker-compose.yml
├── .env.example
└── README.md
```

## Backend Architecture

后端采用清晰分层：

```text
Controller -> Service -> Repository -> Entity
             DTO <-> API Boundary
```

主要模块：

- `TaskService`: AI 任务执行与历史记录
- `OpenAiService`: OpenAI-compatible HTTP 调用层
- `AiApiKeyService`: Provider 密钥管理
- `PromptService`: Prompt 资产、版本和收藏
- `WorkflowService`: Flow 草稿与节点结构

数据库实体：

- `tasks`
- `ai_api_keys`
- `prompts`
- `prompt_versions`
- `workflows`

## Frontend Architecture

前端采用 Vue 3 + TypeScript 的模块化结构：

- `views`: 页面级 Workspace
- `components`: 可复用展示组件
- `stores`: Pinia 工作空间状态
- `api`: Axios REST API 封装
- `types`: 前端领域类型
- `styles`: Design Tokens 和全局 SCSS

核心页面：

- `WorkspaceView`: AI Workspace Landing
- `TasksView`: AI Command Workspace
- `PromptLibraryView`: Prompt 资产库
- `WorkflowsView`: Flow Canvas / Workflow Builder
- `HistoryView`: 历史记录
- `ApiKeysView`: Provider Vault
- `AgentsView`: Agent 产品预留
- `KnowledgeBaseView`: Knowledge 产品预留
- `AnalyticsView`: 轻量洞察预留

## Quick Start with Docker

### 1. Clone project

```bash
git clone git@github.com:ws-hun/FlowForge-AI.git
cd "FlowForge AI"
```

### 2. Create env file

```bash
cp .env.example .env
```

默认数据库配置：

```text
Database: flowforge
Username: flowforge
Password: flowforge
Port: 5432
```

### 3. Start all services

```bash
docker compose up --build
```

访问地址：

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080
Postgres: localhost:5432
```

### 4. Configure AI Provider

进入前端：

```text
http://localhost:5173/api-keys
```

添加 DeepSeek 或 OpenAI API Key，并激活 Provider。

DeepSeek 默认配置：

```text
Provider: deepseek
Base URL: https://api.deepseek.com
Model: deepseek-chat
```

OpenAI 默认配置：

```text
Provider: openai
Base URL: https://api.openai.com/v1
Model: gpt-4o-mini
```

## Local Development

### Requirements

- JDK 17+
- Maven 3.9+
- Node.js 18+
- PostgreSQL 16+

### Start PostgreSQL

可以直接使用 Docker Compose 只启动数据库：

```bash
docker compose up postgres
```

或者使用本机 PostgreSQL，并创建数据库：

```sql
CREATE DATABASE flowforge;
```

### Start Backend

```bash
cd backend
mvn spring-boot:run
```

后端默认读取：

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/flowforge
SPRING_DATASOURCE_USERNAME=flowforge
SPRING_DATASOURCE_PASSWORD=flowforge
FRONTEND_URL=http://localhost:5173
```

### Start Frontend

```bash
cd frontend
npm install
npm run dev
```

Vite dev server:

```text
http://localhost:5173
```

前端开发环境通过 Vite proxy 转发 `/api` 到：

```text
http://localhost:8080
```

## Environment Variables

`.env.example` 不包含任何真实 API Key。

```env
POSTGRES_DB=flowforge
POSTGRES_USER=flowforge
POSTGRES_PASSWORD=flowforge

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/flowforge
SPRING_DATASOURCE_USERNAME=flowforge
SPRING_DATASOURCE_PASSWORD=flowforge

FRONTEND_URL=http://localhost:5173
```

AI Provider Key 通过前端 Provider Vault 写入数据库，不需要放入 `.env` 或 `application.yml`。

## API Overview

### Task

```http
POST /api/tasks/run
Content-Type: application/json

{
  "input": "请帮我把一个产品想法拆解为 MVP 方案",
  "promptId": null,
  "flowId": null
}
```

Response:

```json
{
  "summary": "一句话总结",
  "result": "详细结果",
  "raw": "AI 原始返回"
}
```

前端会基于 `result` 自动渲染文档结构，并提取关键要点展示。

### Prompt

```http
GET /api/prompts
POST /api/prompts
PUT /api/prompts/{id}
PATCH /api/prompts/{id}/favorite
GET /api/prompts/{id}/runs
GET /api/prompts/{id}/versions
POST /api/prompts/{id}/versions/{versionId}/restore
DELETE /api/prompts/{id}
```

### Flow

```http
GET /api/flows
POST /api/flows
PUT /api/flows/{id}
GET /api/flows/{id}/runs
DELETE /api/flows/{id}
```

### Provider

```http
GET /api/settings/api-keys
POST /api/settings/api-keys
PATCH /api/settings/api-keys/{id}/activate
DELETE /api/settings/api-keys/{id}
```

## Validation

前端验证命令：

```bash
cd frontend
npm run typecheck
npm run build
```

后端验证命令：

```bash
cd backend
mvn test
```

当前前端构建中可能出现的非阻塞 warning：

- Sass legacy JS API deprecation
- Rollup PURE annotation warning from `@vueuse/core`
- Vite chunk size warning

这些不影响当前构建产物。

## Security Notes

- 不在源码中保存真实 API Key
- `.env.example` 只保留本地开发示例
- Provider Key 通过 UI 提交到后端
- 后端只向前端返回 masked key
- GitHub Push Protection 可阻止误提交密钥

如果曾经把真实 API Key 提交到 Git 历史，请立即：

1. 删除并重写 Git 历史中的密钥
2. 到 Provider 平台撤销旧 Key
3. 重新生成新 Key
4. 确认 GitHub Push Protection 不再报错

## Troubleshooting

### Frontend shows `ERR_CONNECTION_REFUSED`

通常是后端没有启动。

检查：

```bash
curl http://localhost:8080/api/tasks
```

如果连接失败，启动后端：

```bash
cd backend
mvn spring-boot:run
```

### Vite proxy error: `/api/... ECONNREFUSED`

说明前端启动了，但 `localhost:8080` 后端不可访问。

处理：

- 确认 Spring Boot 正在运行
- 确认端口是 `8080`
- 确认 PostgreSQL 已启动
- 确认数据库账号密码正确

### AI execution fails

检查：

- 是否已经在 `/api-keys` 添加 API Key
- 是否激活了 Provider
- Base URL 是否正确
- Model 是否存在
- API Key 是否有效

### GitHub push blocked by secret scanning

不要点击绕过，应该从 Git 历史中移除密钥并撤销旧 Key。

FlowForge 当前设计已经把 API Key 从配置文件迁移到数据库管理，正常开发不需要把密钥写入源码。

## Roadmap

### Near Term

- 完善 Workflow Builder 节点体验
- 增加 Flow 模板
- 优化 Flow 执行结果结构化展示
- 增强 Prompt 和 Flow 之间的复用闭环
- 增加更完整的空状态和引导体验

### Mid Term

- Knowledge Base 接入真实文档上下文
- Agent Runtime 原型
- Workflow Execution Graph
- 更细粒度的运行日志
- Prompt 版本对比
- Flow 版本管理

### Long Term

- MCP Integration
- Plugin Ecosystem
- Team Workspace
- Role-based Collaboration
- Cloud Deployment
- Observability and Cost Control

## Design Principle

FlowForge 的每个页面都必须回答一个问题：

```text
用户在这里创造什么？
用户在这里执行什么？
用户在这里复用什么？
```

如果一个功能只是在展示数据，它就不应该成为核心体验。

## License

This project is currently under active development. License to be decided.
