# Parchie — PRD

*Share a link. Write together. Export something beautiful.*

---

## What is it?

A real-time collaborative editor. Share a link, anyone can join and type together. When you're done, pick a CSS theme and export styled HTML. No account needed.

The mascot is a champagne loaf cat sitting on a note. The cat is called Parchie.

---

## The problem it solves

You want to write something with someone quickly and have it look good at the end. Google Docs needs everyone to have an account. HackMD needs signup. Slack is just chat. There's no tool that's just: open a link, write together, make it look nice, done.

---

## How it works

1. Open the app → a session is created with a unique link
2. Share the link → others join, no account needed
3. Everyone types in the same document in real time
4. Pick a CSS theme, preview the styled output
5. Copy or download the styled HTML
6. The session link stays alive for 7 days after the last edit

---

## Editor

- Rich text by default — toolbar with bold, italic, headings, lists, code blocks, links
- Markdown mode toggle for power users
- Live coloured cursors showing who's typing where
- Split view: editor on the left, themed preview on the right

---

## Sessions

- Created instantly, no account required
- Identified by a unique URL with the encryption key in the fragment (`parchie.app/s/abc123#key`)
- Persist for 7 days after last edit, then expire
- E2E encrypted — the server relays encrypted blobs and can't read content
- Optional: extend TTL, lock to read-only, password protect

---

## Theming & Export

**Built-in themes:** Minimal, Noir, Academic, Terminal, Magazine

**CSS editor:** view and edit the raw CSS directly, live preview updates instantly

**Export options:**
- Copy styled HTML (primary action)
- Download `.html` file (self-contained, CSS inlined)
- Download `.md` source
- Print / PDF via browser

---

## Tech Stack

### Frontend
- React + TypeScript (Vite)
- Tiptap — WYSIWYG editor with markdown underneath
- Yjs — CRDT real-time sync + cursor presence
- CodeMirror 6 — markdown raw mode + CSS editor
- Tailwind — app chrome only
- Web Crypto API — client-side AES-256-GCM encryption

### Backend (Spring Boot)
- Spring WebSocket + STOMP — real-time message relay
- Spring Web MVC — REST API
- Spring Data JPA — session metadata
- Spring Scheduler — TTL cleanup job
- PostgreSQL — session storage
- Docker Compose — local dev

### Infrastructure
- Frontend: Cloudflare Pages
- Backend: single VPS (Hetzner/Railway) to start
- Database: managed Postgres

---

## Data Model

One entity. That's it.

```
Session
├── id: UUID
├── created_at: timestamp
├── last_active_at: timestamp
├── ttl_days: int (default 7)
├── is_locked: boolean
├── password_hash: varchar (nullable)
└── encrypted_state: blob
```

No users. No auth. Sessions are the only thing the server knows about.

---

## Roadmap

**v1.0 — MVP**
- Session creation + sharing
- Real-time collaborative rich text editing
- Markdown toggle
- 5 CSS themes + CSS editor
- Styled HTML export
- E2E encryption

**v1.1 — Session controls**
- Extend TTL
- Lock session (read-only)
- Password protection

**v1.2 — AI paste**
- Import AI-generated markdown in one click
- Clean it up together, style it, export

**v1.3 — Theme gallery**
- Community CSS themes
- One-click apply

**v1.4 — Accounts (optional)**
- For users who want sessions that never expire
- Free sessions still expire at 7 days
- Joining is always free and always anonymous

---

## What this project is for

Learning. Specifically:

- Java Spring Boot (WebSocket, REST, JPA, scheduling)
- Real-time systems (CRDT, Yjs, WebSocket relay)
- Frontend collaboration (Tiptap, cursor presence)
- E2E encryption in the browser
- Deploying a full-stack app end to end

The product being genuinely useful is a bonus.
