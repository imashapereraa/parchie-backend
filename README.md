# parchie-backend

REST API for Parchie, a collaborative document editor. Handles user accounts, bearer-token authentication, anonymous sessions, and a per-user folder/document tree. The other two services are [parchie-frontend](https://github.com/imashapereraa/parchie-frontend) (browser client) and [parchie-collab](https://github.com/imashapereraa/parchie-collab) (WebSocket sync server).

## Architecture

```
Browser
  |
  |-- POST /api/auth/login -----> AuthController
  |-- GET  /api/docs -----------> DocumentController  --> DocumentService --> Postgres
  |-- POST /api/sessions -------> SessionController   --> SessionService
  |
  `-- WebSocket relay ----------> SessionRelayHandler (proxies to parchie-collab)
```

The backend owns everything except the actual document content. Document content (the Yjs state) lives in parchie-collab and is persisted to a separate `session_states` table that the collab server writes directly. The backend's `sessions` table just tracks metadata — TTL, lock state, password hash, owner.

## Stack

- Java 21
- Spring Boot 3.4 (Web, JPA, WebSocket, Actuator)
- PostgreSQL with Flyway migrations
- Hibernate / Spring Data JPA
- BCrypt for password hashing (jbcrypt 0.4)
- Testcontainers + Cucumber for integration tests

## Database schema

Four migrations, applied in order by Flyway on startup.

```
users
  id            UUID PK
  username      VARCHAR(64)   unique (case-insensitive index)
  password_hash VARCHAR(72)
  created_at    TIMESTAMPTZ

auth_tokens
  token         VARCHAR(64)   PK (opaque bearer token)
  user_id       UUID          FK -> users
  created_at    TIMESTAMPTZ
  expires_at    TIMESTAMPTZ   (30-day TTL, swept by a scheduled task)

sessions
  id            UUID PK
  slug          VARCHAR       short URL-safe alias
  owner_id      UUID          FK -> users, nullable (null = anonymous)
  created_at    TIMESTAMPTZ
  last_active_at TIMESTAMPTZ
  ttl_days      INTEGER       (ignored when owner_id is set)
  is_locked     BOOLEAN
  password_hash VARCHAR(72)   nullable

documents                     (per-user folder/doc tree, adjacency list)
  id            UUID PK
  owner_id      UUID          FK -> users
  parent_id     UUID          FK -> documents, nullable (null = root)
  kind          VARCHAR(16)   'folder' or 'doc'
  name          VARCHAR(255)
  session_id    UUID          FK -> sessions, null for folders
  created_at    TIMESTAMPTZ
  updated_at    TIMESTAMPTZ
```

Anonymous sessions (no `owner_id`) expire after `ttl_days` of inactivity. Owned sessions are permanent — their `expires_at` is pushed to a far-future sentinel so the collab server's TTL cleanup never touches them.

## API

### Auth

```
POST /api/auth/register   { username, password } -> { token, expiresAt, user }
POST /api/auth/login      { username, password } -> { token, expiresAt, user }
POST /api/auth/logout     Authorization: Bearer <token>
GET  /api/auth/me         -> { id, username } or 401
```

### Sessions (anonymous)

```
POST   /api/sessions              -> { id, slug, ... }
GET    /api/sessions/:id          -> session metadata
PATCH  /api/sessions/:id          update settings (lock, password, TTL)
GET    /api/sessions/:id/state    -> raw Yjs state bytes (proxied from collab)
PUT    /api/sessions/:id/state    upload Yjs state bytes
```

### Documents (authenticated)

```
GET    /api/docs                  list all nodes (flat, tree built client-side)
POST   /api/docs                  { kind, name, parentId? } -> DocumentNode
PATCH  /api/docs/:id/name         { name } -> DocumentNode
PUT    /api/docs/:id/parent       { parentId? } -> DocumentNode
DELETE /api/docs/:id
```

## Running locally

```bash
# start Postgres (the pom.xml includes spring-boot-docker-compose, so if you
# have a docker-compose.yml with a postgres service it starts automatically)
./mvnw spring-boot:run
```

The app listens on port 8080. Set the following in `application-local.properties`:

```
spring.datasource.url=jdbc:postgresql://localhost:5432/parchie
spring.datasource.username=parchie
spring.datasource.password=parchie
```

## Tests

```bash
./mvnw test
```

Integration tests spin up a real Postgres instance via Testcontainers. BDD scenarios (Cucumber) cover session lifecycle, settings, and the WebSocket relay.
