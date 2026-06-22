# ADR-0004: Excalidraw drawing canvas is post-MVP

## Status

Accepted

## Context

The tech stack includes @excalidraw/excalidraw as a collaborative drawing canvas, synced via `y-excalidraw` through the same `Y.Doc`. This would let users embed drawings alongside rich text in a session.

Adding Excalidraw to the MVP has significant implications:

- The `Y.Doc` needs additional shared types for drawing state alongside text.
- The export pipeline must handle rendering canvas output into the styled HTML/PDF.
- Presence needs to work across three surfaces (Tiptap, CodeMirror, Excalidraw).
- It's a large React component with its own state management and performance characteristics.

None of this is hard, but it's a lot of surface area for a v1.0 that's primarily a learning project.

## Decision

Excalidraw is **post-MVP** scope. The v1.0 MVP focuses on collaborative text editing, theming, and export. Drawing canvas will be added in a later version (tentatively v1.2 or v1.3).

The architecture should not prevent this — the `Y.Doc` can be extended with new shared types without breaking existing state, and the backend relay is content-agnostic (it forwards encrypted blobs regardless of what's inside).

## Consequences

- **Smaller MVP.** Fewer moving parts to build, test, and debug for v1.0.
- **No backend impact when it's added later.** The relay doesn't care what's in the encrypted blobs. Excalidraw is purely a frontend addition.
- **Y.Doc schema needs to be forward-compatible.** The frontend should use namespaced shared types (e.g., `doc.getXmlFragment('tiptap')`) so adding `doc.getMap('excalidraw')` later doesn't conflict.
