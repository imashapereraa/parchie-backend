# Parchie — Build Plan

*Epics → Features → Build Sequence*

---

## Epic 1: Project Foundation

Everything before you write a single feature. Dev environment, project structure, CI, deployment pipeline. Do this first so every subsequent feature ships to a running app.

### Features

**1.1 — Spring Boot project scaffold**
- Initialize Spring Boot 3 project (Java 21, Gradle or Maven)
- Dependencies: spring-boot-starter-web, spring-boot-starter-websocket, spring-boot-starter-data-jpa, postgresql driver
- Application config for dev/prod profiles
- Health check endpoint (`GET /api/health`)
- Dockerfile for the backend

**1.2 — PostgreSQL setup**
- Docker Compose with Postgres service
- JPA entity for `Session` (id, created_at, last_active_at, ttl_days, is_locked, password_hash, encrypted_state)
- Flyway or Liquibase migration for the sessions table
- Spring Data JPA repository interface

**1.3 — React frontend scaffold**
- Vite + React + TypeScript project
- Tailwind CSS configured
- Basic routing: landing page (`/`), session page (`/s/:id`)
- Proxy config for local API calls to Spring backend
- Dockerfile for the frontend (or just static build)

**1.4 — Deployment pipeline**
- Frontend deploys to Cloudflare Pages (connect Git repo)
- Backend deploys to VPS or Railway (Docker image)
- Managed Postgres provisioned (Railway/Neon)
- Environment variables and secrets configured
- Domain pointed (`parchie.app`)

### Build Sequence

```
1.1 Spring scaffold + health endpoint
 └─► 1.2 Add Postgres + Session entity + migration
      └─► 1.3 React scaffold + routing
           └─► 1.4 Deploy both, verify end-to-end
```

**Milestone: App is live. Landing page loads. Health check returns 200. Database is connected.**

---

## Epic 2: Session Lifecycle

Create, retrieve, and expire sessions. The core server-side loop that everything else depends on.

### Features

**2.1 — Session creation**
- `POST /api/sessions` → creates a new session, returns `{ id, created_at, ttl_days }`
- UUID generated server-side
- `last_active_at` set to now
- No request body needed (all defaults)

**2.2 — Session retrieval**
- `GET /api/sessions/:id` → returns session metadata (not the encrypted blob)
- 404 if session doesn't exist or is expired
- Response includes: id, created_at, last_active_at, ttl_days, is_locked

**2.3 — Session state persistence**
- `PUT /api/sessions/:id/state` → accepts encrypted Yjs state blob
- `GET /api/sessions/:id/state` → returns the encrypted blob
- Binary request/response (application/octet-stream)
- Updates `last_active_at` on every write

**2.4 — TTL expiry job**
- `@Scheduled` cron job runs every hour
- Deletes sessions where `last_active_at + ttl_days < now`
- Logs count of expired sessions

**2.5 — Frontend session flow**
- Landing page: "Start writing" button calls `POST /api/sessions`, redirects to `/s/:id#key`
- Session page: fetches metadata on load, shows 404 if expired
- Shareable URL displayed with copy button

### Build Sequence

```
2.1 POST create session
 └─► 2.2 GET session metadata
      └─► 2.3 PUT/GET encrypted state blob
           └─► 2.4 Scheduled TTL cleanup
                └─► 2.5 Wire frontend: create → redirect → load
```

**Milestone: You can create a session, get a link, open it, and sessions auto-expire.**

---

## Epic 3: Real-Time WebSocket Relay

The Spring backend becomes a Yjs sync relay. Clients connect, send binary CRDT messages, and the server broadcasts to all peers in the same session.

### Features

**3.1 — WebSocket endpoint**
- Spring WebSocket config: register handler at `/ws/sessions/:id`
- Use `BinaryWebSocketHandler` (not STOMP — Yjs sends binary frames)
- On connect: add client to session's connection set
- On disconnect: remove client
- On message: broadcast to all other clients in the same session

**3.2 — Yjs client provider**
- Install `yjs` and `y-websocket` on the frontend
- Create a `Y.Doc` per session
- Connect `y-websocket` provider to `ws://backend/ws/sessions/:id`
- Verify: open two browser tabs, confirm sync messages flow

**3.3 — Connection awareness**
- Enable `y-protocols/awareness` on the provider
- Each client sets a random username and color on connect
- Awareness state synced through the same WebSocket

**3.4 — State snapshotting**
- On a debounced interval (every 5s of inactivity), client encodes `Y.encodeStateAsUpdate(doc)` and PUTs it to the REST endpoint (2.3)
- On session load, client fetches the saved blob and applies it with `Y.applyUpdate(doc, blob)`
- This is the persistence layer — WebSocket is ephemeral, REST + Postgres is durable

### Build Sequence

```
3.1 Spring WebSocket handler (binary relay)
 └─► 3.2 Yjs client connects, two tabs sync
      └─► 3.3 Awareness protocol (usernames, colors)
           └─► 3.4 Snapshot to REST → load from REST
```

**Milestone: Two browser tabs sync in real time. Close both, reopen, document is still there.**

---

## Epic 4: Collaborative Rich Text Editor

Wire Tiptap into the Yjs document so multiple users can type together with live cursors.

### Features

**4.1 — Tiptap editor setup**
- Install `@tiptap/react`, `@tiptap/starter-kit`
- Render basic editor on the session page
- Toolbar: bold, italic, headings (H1–H3), bullet list, ordered list, code block, link
- Editor takes full left panel of split view

**4.2 — Tiptap + Yjs collaboration**
- Install `@tiptap/extension-collaboration`, `@tiptap/extension-collaboration-cursor`
- Bind Tiptap to `Y.XmlFragment("tiptap")` in the shared `Y.Doc`
- Collaboration cursor extension reads from awareness protocol
- Verify: two tabs, both editors sync content and show each other's cursors

**4.3 — Cursor presence UI**
- Colored cursors with username labels
- User list / avatar bar showing who's connected
- Colors assigned from a predefined palette (no duplicates in small sessions)

**4.4 — Markdown toggle**
- "Markdown" button switches to a CodeMirror 6 instance
- CM6 bound to `Y.Text("markdown")` — separate shared type, or serialize Tiptap content to markdown
- Decision point: two-way sync (complex) vs one-way export (simpler for MVP)
- Recommendation for MVP: Tiptap is the source of truth. Markdown mode is read-only or a one-time export. Full two-way sync is v1.1.

### Build Sequence

```
4.1 Tiptap standalone editor with toolbar
 └─► 4.2 Bind to Yjs, verify two-tab collab
      └─► 4.3 Cursor presence with names and colors
           └─► 4.4 Markdown toggle (read-only for MVP)
```

**Milestone: Multiple users type together. Live cursors visible. Toolbar formatting works.**

---

## Epic 5: Theming & Preview

The right panel: live preview of the document with CSS themes applied.

### Features

**5.1 — Preview panel**
- Split view layout: editor (left), preview (right)
- Preview renders Tiptap content as HTML inside an iframe or sandboxed div
- Updates live as the user types

**5.2 — Built-in themes**
- 5 CSS files: Minimal, Noir, Academic, Terminal, Magazine
- Theme selector dropdown in the toolbar area
- Selecting a theme injects its CSS into the preview
- Theme choice stored in the Yjs doc (shared across collaborators)

**5.3 — CSS editor**
- CodeMirror 6 instance with CSS language mode
- Shows the raw CSS of the selected theme
- Edits update the preview in real time
- Custom CSS stored in the Yjs doc so collaborators see the same styles

**5.4 — Responsive preview**
- Toggle preview width: desktop / tablet / mobile
- Preview iframe resizes accordingly

### Build Sequence

```
5.1 Split view + live HTML preview
 └─► 5.2 Theme selector + 5 built-in themes
      └─► 5.3 CSS editor (CM6) with live preview
           └─► 5.4 Responsive preview toggles
```

**Milestone: Write on the left, see styled output on the right. Switch themes. Edit CSS. All synced.**

---

## Epic 6: E2E Encryption

The server never sees your content. Encryption key lives in the URL fragment.

### Features

**6.1 — Key generation**
- On session creation, generate a 256-bit AES-GCM key using Web Crypto API
- Encode key as base64url, append to URL as fragment: `/s/abc123#key`
- Fragment is never sent to the server

**6.2 — Encrypt before sending**
- Before any state snapshot (feature 3.4), encrypt the Yjs update blob with the key
- Before any WebSocket message, encrypt the binary frame
- Use AES-256-GCM with a random IV per message

**6.3 — Decrypt on receive**
- On loading saved state, decrypt the blob before applying to Y.Doc
- On receiving WebSocket messages, decrypt before passing to Yjs
- If decryption fails (wrong key), show an error — don't render garbage

**6.4 — Key sharing UX**
- "Copy link" button copies the full URL including fragment
- Warning if someone tries to share the URL without the fragment
- Optional: QR code for in-person sharing

### Build Sequence

```
6.1 Key generation + URL fragment
 └─► 6.2 Encrypt outgoing (snapshots + WS messages)
      └─► 6.3 Decrypt incoming (load + WS messages)
           └─► 6.4 Copy link UX + key validation
```

**Milestone: Server stores only encrypted blobs. Open the link with the key → content loads. Without the key → nothing.**

---

## Epic 7: Export

Get your work out of Parchie.

### Features

**7.1 — Copy styled HTML**
- Primary action button: "Copy HTML"
- Takes the preview content (with theme CSS inlined) and copies to clipboard
- Toast confirmation

**7.2 — Download HTML file**
- "Download .html" button
- Self-contained file: full HTML document with CSS inlined in a `<style>` tag
- Opens correctly in any browser

**7.3 — Download markdown**
- "Download .md" button
- Serialize Tiptap content to markdown
- Clean, standard markdown output

**7.4 — Print / PDF**
- "Print" button triggers `window.print()` on the preview
- Print stylesheet ensures clean output
- PDF via the browser's native print-to-PDF

### Build Sequence

```
7.1 Copy styled HTML to clipboard
 └─► 7.2 Download .html file
      └─► 7.3 Download .md source
           └─► 7.4 Print stylesheet + print action
```

**Milestone: Users can get their work out in 4 ways. The styled HTML looks exactly like the preview.**

---

## Epic 8: Session Controls (v1.1)

Post-MVP features for managing sessions.

### Features

**8.1 — Extend TTL**
- UI control to extend session by 7/30/90 days
- `PATCH /api/sessions/:id` updates `ttl_days`

**8.2 — Lock session**
- Toggle to make session read-only
- `PATCH /api/sessions/:id` sets `is_locked = true`
- Server rejects WebSocket writes when locked
- Editors become read-only, preview still works

**8.3 — Password protection**
- Set a password on the session
- `PATCH /api/sessions/:id` stores bcrypt hash
- On join, client prompts for password, sends to `POST /api/sessions/:id/auth`
- Server returns a short-lived token, client includes in WebSocket handshake

### Build Sequence

```
8.1 Extend TTL
 └─► 8.2 Lock to read-only
      └─► 8.3 Password protection
```

---

## Epic 9: Excalidraw Integration (v1.2+)

Add a collaborative drawing canvas alongside the text editor.

### Features

**9.1 — Excalidraw embed**
- Install `@excalidraw/excalidraw`
- New tab or panel: "Text" / "Draw"
- Excalidraw component renders in the draw panel

**9.2 — Collaborative drawing via Yjs**
- Install `y-excalidraw`
- Bind Excalidraw to `Y.Array("excalidraw")` in the shared `Y.Doc`
- Same WebSocket, same awareness, same encryption
- Verify: two tabs, drawing syncs

**9.3 — Export drawings**
- Export Excalidraw canvas as SVG or PNG
- Optionally embed into the styled HTML export
- Download as standalone `.excalidraw` file

### Build Sequence

```
9.1 Embed Excalidraw component
 └─► 9.2 Wire to Yjs for real-time collab
      └─► 9.3 Export integration
```

---

## Recommended Build Order (Full Sequence)

```
Epic 1: Foundation
│   1.1 → 1.2 → 1.3 → 1.4
│
Epic 2: Session Lifecycle
│   2.1 → 2.2 → 2.3 → 2.4 → 2.5
│
Epic 3: WebSocket Relay          ← this is the hardest part
│   3.1 → 3.2 → 3.3 → 3.4
│
Epic 4: Rich Text Editor
│   4.1 → 4.2 → 4.3 → 4.4
│
Epic 5: Theming & Preview
│   5.1 → 5.2 → 5.3 → 5.4
│
Epic 6: E2E Encryption           ← can be done in parallel with 5
│   6.1 → 6.2 → 6.3 → 6.4
│
Epic 7: Export
│   7.1 → 7.2 → 7.3 → 7.4
│
═══ MVP COMPLETE ═══
│
Epic 8: Session Controls (v1.1)
│   8.1 → 8.2 → 8.3
│
Epic 9: Excalidraw (v1.2+)
│   9.1 → 9.2 → 9.3
```

### Parallelism Opportunities

- **Epics 5 and 6** can be built in parallel — theming is purely frontend, encryption touches the sync layer
- **Features 5.2 (themes)** can have the CSS files designed by someone else while you build the selector
- **Epic 7 (export)** is mostly standalone frontend work once the preview exists

### Estimated Effort (Solo Developer)

| Epic | Estimate |
|---|---|
| 1. Foundation | 2–3 days |
| 2. Session Lifecycle | 2–3 days |
| 3. WebSocket Relay | 4–5 days |
| 4. Rich Text Editor | 3–4 days |
| 5. Theming & Preview | 3–4 days |
| 6. E2E Encryption | 3–4 days |
| 7. Export | 2–3 days |
| **MVP Total** | **~3–4 weeks** |
| 8. Session Controls | 2–3 days |
| 9. Excalidraw | 3–5 days |

### Risk Areas

- **Epic 3 (WebSocket relay)** — getting Spring's binary WebSocket handler to correctly relay Yjs sync protocol messages is the riskiest part. Prototype this early. If it fights you, consider a tiny `y-websocket` Node server as the relay and keep Spring for REST only.
- **Epic 6 (encryption)** — encrypting/decrypting every WebSocket frame adds latency. Benchmark early. You may want to encrypt only the persistence snapshots and trust the WebSocket connection (TLS) for real-time messages.
- **Feature 4.4 (markdown toggle)** — two-way sync between Tiptap's ProseMirror model and raw markdown is genuinely hard. Keep it read-only for MVP.