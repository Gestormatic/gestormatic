# process-mgmt-procedures

Spring Boot microservice that manages **procedure templates** and **procedure cases** for Gestormatic.

---

## Table of contents

1. [Tech stack](#tech-stack)
2. [Local setup](#local-setup)
3. [Environment variables](#environment-variables)
4. [Database schema](#database-schema)
5. [Running the service](#running-the-service)
6. [API reference](#api-reference)
   - [Authentication](#authentication)
   - [Procedure Templates](#procedure-templates)
   - [Procedure Cases](#procedure-cases)
7. [Swagger UI](#swagger-ui)
8. [Running tests](#running-tests)

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.3 |
| Persistence | Spring Data JDBC + PostgreSQL 16 |
| Auth | Supabase JWT (ES256) via Spring OAuth2 Resource Server |
| API docs | Springdoc OpenAPI 2.3.0 (Swagger UI) |
| Tests | JUnit 5 · Mockito · Testcontainers 1.20.4 |

---

## Local setup

### Prerequisites

- Java 21+
- Docker (for Testcontainers and local Supabase)
- A running [Supabase](https://supabase.com) project (or local Supabase CLI)

### 1. Clone the repository

```bash
git clone git@github.com:Gestormatic/gestormatic.git
cd gestormatic/apps/gestormatic-backend/process-mgmt-procedures
```

### 2. Configure environment variables

Copy the example file and fill in your values:

```bash
cp .env.example .env
```

See [Environment variables](#environment-variables) for the full list.

### 3. Create the database schema

Run the `schema.sql` file against your Supabase database once (the service does **not** auto-run migrations in production):

```bash
psql "postgresql://postgres:[PASSWORD]@[HOST]:5432/postgres" \
  -f src/main/resources/schema.sql
```

Or paste the contents directly in the **Supabase SQL Editor**.

### 4. Start the service

```bash
./mvnw -s mvn-settings.xml spring-boot:run
```

The service starts on **port 8081** by default.

---

## Environment variables

| Variable | Description | Example |
|---|---|---|
| `DB_URL` | JDBC connection URL | `jdbc:postgresql://db.xxx.supabase.co:5432/postgres` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `your-db-password` |
| `SUPABASE_URL` | Supabase project URL | `https://xxx.supabase.co` |
| `SUPABASE_JWKS` | Supabase JWKS endpoint | `https://xxx.supabase.co/rest/v1/` |

All variables are loaded from a `.env` file in the project root (ignored by git).

---

## Database schema

All tables live in the `procedures` schema.

```
procedures
├── procedure_templates   — template definitions (DRAFT / ACTIVE / INACTIVE)
├── procedure_steps       — ordered steps within a template
├── required_documents    — documents required per step
├── procedure_members     — gestores and customers linked to a template (OWNER / CUSTOMER)
└── procedure_cases       — concrete case instances opened from active templates
```

### Case status lifecycle

```
RECEIVED ──► IN_REVIEW ──► APPROVED  (terminal)
                  │
                  ▼
            PENDING_DOCS ──► REJECTED (terminal)
                  │
                  └──────► IN_REVIEW
```

---

## Running the service

```bash
# Build
./mvnw -s mvn-settings.xml clean package -DskipTests

# Run
./mvnw -s mvn-settings.xml spring-boot:run

# Or run the JAR directly
java -jar target/procedures-0.0.1-SNAPSHOT.jar
```

---

## API reference

### Authentication

Every endpoint requires a valid **Supabase JWT** in the `Authorization` header:

```
Authorization: Bearer <access_token>
```

The token must contain:
- `app_metadata.tenant_id` — used for multi-tenant isolation (set automatically per tenant)
- `app_metadata.roles` containing `"gestor"` — required for all operations

Obtain the token via the `process-mgmt-login` service (`POST /auth/login`).

---

### Procedure Templates

Base path: `/api/procedures`

#### `POST /api/procedures` — Create a template

Creates a new procedure template in `DRAFT` status. The authenticated gestor is automatically added as `OWNER`.

**Request body:**
```json
{
  "name": "Licencia de obras",
  "description": "Trámite para solicitar licencia de obras menores"
}
```

**Response `201`:**
```json
{
  "id": 1,
  "name": "Licencia de obras",
  "description": "Trámite para solicitar licencia de obras menores",
  "status": "DRAFT",
  "createdBy": "gestor-uid",
  "createdAt": "2026-03-21T10:00:00Z",
  "steps": []
}
```

---

#### `GET /api/procedures` — List templates

Returns all templates for the authenticated gestor's tenant.

**Response `200`:** Array of template objects (without steps).

---

#### `GET /api/procedures/{id}` — Get a template

Returns a template with its full step and document tree.

**Response `200`:**
```json
{
  "id": 1,
  "name": "Licencia de obras",
  "status": "DRAFT",
  "steps": [
    {
      "id": 10,
      "stepOrder": 1,
      "name": "Documentación inicial",
      "description": "Aportar documentos de identidad",
      "requiredDocuments": [
        { "id": 20, "name": "DNI", "description": "Documento nacional de identidad", "mandatory": true }
      ]
    }
  ]
}
```

---

#### `PUT /api/procedures/{id}` — Update a template

Updates name, description or status. Requires `OWNER` membership.

**Request body** (all fields optional):
```json
{
  "name": "Nuevo nombre",
  "description": "Nueva descripción",
  "status": "ACTIVE"
}
```

---

#### `PATCH /api/procedures/{id}/status` — Change template status

Valid values: `DRAFT`, `ACTIVE`, `INACTIVE`.

**Request body:**
```json
{ "status": "ACTIVE" }
```

---

#### `POST /api/procedures/{id}/steps` — Add a step

**Request body:**
```json
{
  "stepOrder": 1,
  "name": "Documentación inicial",
  "description": "Aportar documentos de identidad"
}
```

**Response `201`:** Step object.

---

#### `PUT /api/procedures/{id}/steps/{stepId}` — Update a step

**Request body** (all fields optional):
```json
{
  "stepOrder": 2,
  "name": "Revisión técnica",
  "description": "Revisión por técnico municipal"
}
```

---

#### `POST /api/procedures/{id}/steps/{stepId}/documents` — Add a required document

**Request body:**
```json
{
  "name": "DNI",
  "description": "Documento nacional de identidad",
  "mandatory": true
}
```

**Response `201`:** Document object.

---

#### `GET /api/procedures/{id}/members` — List members

Returns all members of a template.

**Response `200`:**
```json
[
  { "id": 1, "userId": "gestor-uid", "memberRole": "OWNER" },
  { "id": 2, "userId": "customer-uid", "memberRole": "CUSTOMER" }
]
```

---

#### `POST /api/procedures/{id}/members` — Add a member

**Request body:**
```json
{
  "userId": "customer-uid",
  "memberRole": "CUSTOMER"
}
```

Valid roles: `OWNER`, `CUSTOMER`.

**Response `201`:** Member object.

---

#### `DELETE /api/procedures/{id}/members/{userId}` — Remove a member

An `OWNER` cannot remove themselves.

**Response `204`:** No content.

---

### Procedure Cases

Base path: `/api/cases`

#### `POST /api/cases` — Open a case

Creates a new case from an `ACTIVE` template. Initial status is `RECEIVED`.

**Request body:**
```json
{
  "templateId": 1,
  "customerId": "customer-uid",
  "notes": "Solicitud urgente"
}
```

**Response `201`:**
```json
{
  "id": 100,
  "tenantId": "tenant-1",
  "templateId": 1,
  "templateName": "Licencia de obras",
  "customerId": "customer-uid",
  "assignedTo": null,
  "status": "RECEIVED",
  "notes": "Solicitud urgente",
  "createdBy": "gestor-uid",
  "createdAt": "2026-03-21T10:00:00Z",
  "updatedAt": "2026-03-21T10:00:00Z"
}
```

---

#### `GET /api/cases` — List cases

Returns cases for the tenant. All query parameters are optional and combinable.

| Parameter | Type | Description |
|---|---|---|
| `status` | string | Filter by status (`RECEIVED`, `IN_REVIEW`, `PENDING_DOCS`, `APPROVED`, `REJECTED`) |
| `customerId` | string | Filter by customer user ID |
| `assignedTo` | string | Filter by assigned gestor user ID |
| `templateId` | long | Filter by template ID |

**Examples:**
```
GET /api/cases
GET /api/cases?status=RECEIVED
GET /api/cases?assignedTo=gestor-uid&status=IN_REVIEW
GET /api/cases?templateId=1
```

Results are ordered by `createdAt` descending.

---

#### `GET /api/cases/{id}` — Get a case

Returns a single case by ID.

---

#### `PATCH /api/cases/{id}/status` — Change case status

**Allowed transitions:**

| From | To |
|---|---|
| `RECEIVED` | `IN_REVIEW` |
| `IN_REVIEW` | `PENDING_DOCS`, `APPROVED`, `REJECTED` |
| `PENDING_DOCS` | `IN_REVIEW`, `REJECTED` |
| `APPROVED` | _(terminal — no transitions)_ |
| `REJECTED` | _(terminal — no transitions)_ |

**Request body:**
```json
{ "status": "IN_REVIEW" }
```

---

#### `PATCH /api/cases/{id}/assignee` — Assign a case

Assigns (or reassigns) the case to a gestor.

**Request body:**
```json
{ "assignedTo": "gestor-uid" }
```

---

## Swagger UI

Once the service is running, open:

```
http://localhost:8081/swagger-ui.html
```

To authenticate in Swagger UI:
1. Click the **Authorize** button (🔒) at the top right
2. Paste your Supabase `access_token` (without the `Bearer` prefix)
3. Click **Authorize** — all subsequent requests will include the JWT

---

## Running tests

> Docker must be running for integration tests (Testcontainers).

```bash
# All tests
./mvnw -s mvn-settings.xml test

# Unit tests only (no Docker needed)
./mvnw -s mvn-settings.xml test -Dtest="*ServiceTest,*ServiceChangeStatusTest"

# Integration tests only
./mvnw -s mvn-settings.xml test -Dtest="*IntegrationTest,*RepositoryTest,*ControllerTest"
```
