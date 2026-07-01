# FlowForge AI

## Docker

```bash
cp .env.example .env
docker compose up --build
```

Frontend: http://localhost:5173

Backend: http://localhost:8080

## API

```http
POST /api/tasks/run
Content-Type: application/json

{
  "input": "用户输入任务"
}
```

```http
GET /api/tasks
```

## Local Backend

```bash
cd backend
mvn spring-boot:run
```

## AI Provider

API keys are managed in the frontend Provider Vault panel and stored in PostgreSQL.

```http
GET /api/settings/api-keys
POST /api/settings/api-keys
PATCH /api/settings/api-keys/{id}/activate
DELETE /api/settings/api-keys/{id}
```

## Local Frontend

```bash
cd frontend
npm install
npm run dev
```
