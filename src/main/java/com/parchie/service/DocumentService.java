package com.parchie.service;

import com.parchie.exception.DocumentNameConflictException;
import com.parchie.exception.DocumentNotFoundException;
import com.parchie.exception.InvalidTreeMoveException;
import com.parchie.model.Document;
import com.parchie.model.Session;
import com.parchie.repository.DocumentRepository;
import com.parchie.repository.SessionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// tree CRUD over the per-user document graph.
// permissioning: every read/write filters by ownerId. We never return a row
// the caller doesn't own; "not yours" and "doesn't exist" both surface the
// same 404 to avoid leaking the presence of other users' nodes.
// validation that the schema can't express:
// - parent must be a folder (a doc can't contain children)
// - move target can't be a descendant of the node being moved (cycles)
// - name uniqueness within parent is enforced by a partial unique index; we
// translate the DB exception into a clean 409.
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final SessionRepository sessionRepository;
    private final SessionService sessionService;

    public DocumentService(
            DocumentRepository documentRepository,
            SessionRepository sessionRepository,
            SessionService sessionService) {
        this.documentRepository = documentRepository;
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
    }

    public List<Document> listOwned(UUID ownerId) {
        return documentRepository.findAllByOwnerId(ownerId);
    }

    public Document requireOwned(UUID id, UUID ownerId) {
        return documentRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Transactional
    public Document createFolder(UUID ownerId, String name, UUID parentId) {
        validateName(name);
        validateParent(ownerId, parentId);
        Document d = new Document();
        d.setOwnerId(ownerId);
        d.setParentId(parentId);
        d.setKind(Document.Kind.folder);
        d.setName(name.trim());
        return save(d);
    }

    @Transactional
    public Document createDoc(UUID ownerId, String name, UUID parentId) {
        validateName(name);
        validateParent(ownerId, parentId);
        Session session = sessionService.createOwnedSession(ownerId);
        Document d = new Document();
        d.setOwnerId(ownerId);
        d.setParentId(parentId);
        d.setKind(Document.Kind.doc);
        d.setName(name.trim());
        d.setSessionId(session.getId());
        return save(d);
    }

    @Transactional
    public Document rename(UUID ownerId, UUID id, String newName) {
        validateName(newName);
        Document d = requireOwned(id, ownerId);
        d.setName(newName.trim());
        return save(d);
    }

    @Transactional
    public Document move(UUID ownerId, UUID id, UUID newParentId) {
        Document d = requireOwned(id, ownerId);
        if (newParentId != null) {
            if (newParentId.equals(id)) {
                throw new InvalidTreeMoveException("Cannot move a node into itself");
            }
            Document parent = requireOwned(newParentId, ownerId);
            if (parent.getKind() != Document.Kind.folder) {
                throw new InvalidTreeMoveException("Parent must be a folder");
            }
            if (isDescendantOf(ownerId, parent, id)) {
                throw new InvalidTreeMoveException("Cannot move a folder into its own descendant");
            }
        }
        d.setParentId(newParentId);
        return save(d);
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        Document d = requireOwned(id, ownerId);
        // cascade is set on the FK so the subtree goes with it. The linked
        // session for this row (if it's a doc) is left to expire via the
        // sessions.expires_at sweep — for owned sessions that sentinel is
        // year 9999, so we clean them up explicitly here.
        if (d.getKind() == Document.Kind.doc && d.getSessionId() != null) {
            sessionRepository.deleteById(d.getSessionId());
        }
        documentRepository.delete(d);
    }

    // session slug for a doc node, or null for folder nodes. Used by the
     // controller to project each Document into a DTO without N+1 queries. */
    public Map<UUID, String> resolveSessionSlugs(List<Document> docs) {
        // always a HashMap so callers can do `slugs.get(d.getSessionId())` for
        // folder rows (sessionId == null) without an NPE — Map.of() rejects
        // null keys, which used to 500 the list endpoint for any tree where
        // every visible row was a folder.
        Map<UUID, String> out = new HashMap<>();
        List<UUID> sessionIds = docs.stream()
                .map(Document::getSessionId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (sessionIds.isEmpty()) return out;
        sessionRepository.findAllById(sessionIds).forEach(s -> out.put(s.getId(), s.getSlug()));
        return out;
    }

    private Document save(Document d) {
        try {
            return documentRepository.save(d);
        } catch (DataIntegrityViolationException e) {
            // partial unique index on (owner_id, parent_id, lower(name)) fires
            // on duplicates within the same folder.
            throw new DocumentNameConflictException(d.getName());
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidTreeMoveException("Name cannot be blank");
        }
        if (name.length() > 255) {
            throw new InvalidTreeMoveException("Name too long");
        }
        // disallow slashes so future path-based UIs don't have to escape.
        if (name.contains("/") || name.contains("\\")) {
            throw new InvalidTreeMoveException("Name cannot contain slashes");
        }
    }

    private void validateParent(UUID ownerId, UUID parentId) {
        if (parentId == null) return;
        Document parent = requireOwned(parentId, ownerId);
        if (parent.getKind() != Document.Kind.folder) {
            throw new InvalidTreeMoveException("Parent must be a folder");
        }
    }

    // true if `candidateAncestorId` lies on the parent-chain of `node`. Walks
     // upward via parentId. Bounded by the size of the user's tree so cycles
     // in malformed data can't hang us. */
    private boolean isDescendantOf(UUID ownerId, Document node, UUID candidateAncestorId) {
        UUID cursor = node.getParentId();
        int hops = 0;
        int maxHops = (int) documentRepository.findAllByOwnerId(ownerId).size() + 1;
        while (cursor != null && hops++ < maxHops) {
            if (cursor.equals(candidateAncestorId)) return true;
            Optional<Document> next = documentRepository.findByIdAndOwnerId(cursor, ownerId);
            if (next.isEmpty()) return false;
            cursor = next.get().getParentId();
        }
        return false;
    }
}
