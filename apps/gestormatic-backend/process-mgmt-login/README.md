# process-mgmt-login (backend auth service)

Backend service for Supabase Auth + role/tenant management in PostgreSQL.

## Overview

- Authentication is handled by Supabase (client side).
- This service **verifies** JWTs and extracts `tenant_id` + `roles` from `app_metadata`.
- Roles are **officially stored in DB** and then synchronized back into Supabase claims.
- User profile fields (`email`, `display_name`) are synced from JWT claims on every `/auth/me` call.

## Requirements

- Supabase project with Auth enabled.
- JWT signing keys (JWKS) available.
- PostgreSQL database (can be **Supabase Postgres**).

## Environment variables

```bash
# Supabase
SUPABASE_URL="https://<project-ref>.supabase.co"
SUPABASE_SERVICE_ROLE_KEY="<service_role_key>"
# Optional: derived from SUPABASE_URL if omitted
SUPABASE_JWKS_URL="https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json"
# Optional: for projects/tokens signed with HS256
SUPABASE_JWT_SECRET="<supabase_jwt_secret>"

# Database (can be Supabase Postgres)
DB_URL="jdbc:postgresql://<host>:5432/<db_name>"
DB_USERNAME="<db_user>"
DB_PASSWORD="<db_password>"

# Bootstrap admin (one-time setup)
BOOTSTRAP_ENABLED=true
BOOTSTRAP_UID="<supabase_user_uuid>"
BOOTSTRAP_TENANT_ID="acme"
BOOTSTRAP_ROLES="admin"
```

## Database & migrations

This project uses SQL init scripts.
- Schema: `src/main/resources/schema.sql`

If you want to use Supabase Postgres:
1. Open **Supabase Dashboard → Project Settings → Database**
2. Copy host, database, user, password
3. Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

## Bootstrap the first admin

1. Create a user in Supabase (Authentication → Users).
2. Copy the user **UID**.
3. Set `BOOTSTRAP_*` vars and start the service.
4. Disable bootstrap after first run:

```bash
export BOOTSTRAP_ENABLED=false
```

Bootstrap will:
- Ensure roles exist in DB
- Sync `tenant_id` + `roles` into Supabase `app_metadata`
- Create the user in DB (if missing)

## Endpoints

### Public

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/auth/health` | Health check |
| `POST` | `/auth/signup` | Register a new user |

### Authenticated

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/auth/me` | Authenticated user profile |

### Admin (requires `admin` role)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/admin/users/claims` | Set `tenant_id` + roles and sync Supabase claims |
| `PUT` | `/admin/users/{uid}/password` | Change password for a user |
| `PUT` | `/admin/users/{uid}/roles` | Assign roles and sync Supabase claims |
| `GET` | `/admin/roles` | List active roles for tenant |
| `POST` | `/admin/roles` | Create a role |
| `PUT` | `/admin/roles/{name}` | Rename a role |
| `DELETE` | `/admin/roles/{name}` | Deactivate a role (soft delete) |

---

### POST /auth/signup

Creates a new user in Supabase and the local `users` table. All fields are required.

**Request:**
```json
{
  "email": "user@example.com",
  "password": "secret123",
  "displayName": "John Doe",
  "tenantId": "acme"
}
```

**Response `201 Created`:**
```json
{
  "uid": "<supabase_user_uuid>",
  "email": "user@example.com",
  "displayName": "John Doe"
}
```

**Error `400 Bad Request`** (any required field missing or blank):
```json
{
  "error": "invalid_request",
  "message": "displayName is required"
}
```

> After signup, the user receives a confirmation email from Supabase. Once confirmed, they
> can sign in using the Supabase client SDK and call protected endpoints with the access token.

---

### POST /admin/users/claims

```json
{
  "uid": "<supabase_user_uuid>",
  "tenantId": "acme",
  "roles": ["gestor"]
}
```

### PUT /admin/users/{uid}/password

Changes the Supabase password for the given user. Uses the service role key server-side — the target user does not need to be logged in.

**Request:**
```json
{
  "password": "nueva_contraseña"
}
```

**Response `204 No Content`** — password updated successfully.

**Error `400 Bad Request`:**
```json
{
  "error": "invalid_request",
  "message": "password is required"
}
```

---

### PUT /admin/users/{uid}/roles

```json
{
  "roles": ["gestor"]
}
```

### POST /admin/roles

```json
{
  "name": "gestor"
}
```

### PUT /admin/roles/{name}

```json
{
  "name": "gestor_updated"
}
```

## User profile sync

When a user calls `GET /auth/me`, the service automatically syncs `email` and `display_name`
from the JWT `user_metadata` into the local `users` table if they have changed. No extra
call is needed — the sync is transparent on every authenticated request.

The `display_name` field maps to `user_metadata.display_name` in the Supabase JWT.

## Claims format (JWT)

`app_metadata`:
```json
{
  "tenant_id": "acme",
  "roles": ["admin", "gestor"]
}
```

`user_metadata`:
```json
{
  "display_name": "John Doe"
}
```

## Swagger / OpenAPI

- UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`

## Tests

```bash
./mvnw test
```

Test coverage includes:
- **Unit tests** (`AuthServiceSignUpTest`): signup field validation, profile sync logic
- **Integration tests** (`SignUpIntegrationTest`): HTTP layer for `POST /auth/signup`
- **Integration tests** (`AuthMeIntegrationTest`): role merging on `GET /auth/me`
- **Integration tests** (`AdminClaimsIntegrationTest`, `AdminRoleIntegrationTest`): admin endpoints

## Notes

- The Service Role Key is sensitive: keep it server-side only.
- Do not call protected endpoints with `service_role` or `anon` keys as Bearer token —
  use the user access token returned by Supabase Auth after sign-in.
- Clients must refresh tokens after claims change (roles/tenant update).
- CORS is currently open (`*`). Restrict to your frontend origin(s) in production.
