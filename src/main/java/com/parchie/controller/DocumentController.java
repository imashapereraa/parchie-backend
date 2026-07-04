package com.parchie.controller;

import com.parchie.dto.DocumentDtos.CreateRequest;
import com.parchie.dto.DocumentDtos.DocumentNode;
import com.parchie.dto.DocumentDtos.MoveRequest;
import com.parchie.dto.DocumentDtos.RenameRequest;
import com.parchie.exception.InvalidTreeMoveException;
import com.parchie.model.Document;
import com.parchie.model.User;
import com.parchie.service.DocumentService;
import com.parchie.web.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/docs")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    // returns the user's entire tree as a flat list. The frontend builds the
     // parent-child tree itself — keeps the API one round-trip, and lets us
     // rerender locally on rename/move without re-fetching. */
    @GetMapping
    public List<DocumentNode> list(@AuthenticatedUser User user) {
        List<Document> docs = documentService.listOwned(user.getId());
        Map<UUID, String> slugs = documentService.resolveSessionSlugs(docs);
        return docs.stream()
                .map(d -> DocumentNode.from(d, slugs.get(d.getSessionId())))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentNode create(@AuthenticatedUser User user, @RequestBody CreateRequest body) {
        UUID parentId = parseOptionalUuid(body.parentId());
        Document.Kind kind = parseKind(body.kind());
        Document d = switch (kind) {
            case folder -> documentService.createFolder(user.getId(), body.name(), parentId);
            case doc    -> documentService.createDoc(user.getId(), body.name(), parentId);
        };
        Map<UUID, String> slugs = documentService.resolveSessionSlugs(List.of(d));
        return DocumentNode.from(d, slugs.get(d.getSessionId()));
    }

    @PatchMapping("/{id}/name")
    public DocumentNode rename(
            @AuthenticatedUser User user,
            @PathVariable String id,
            @RequestBody RenameRequest body) {
        Document d = documentService.rename(user.getId(), UUID.fromString(id), body.name());
        Map<UUID, String> slugs = documentService.resolveSessionSlugs(List.of(d));
        return DocumentNode.from(d, slugs.get(d.getSessionId()));
    }

    @PutMapping("/{id}/parent")
    public DocumentNode move(
            @AuthenticatedUser User user,
            @PathVariable String id,
            @RequestBody MoveRequest body) {
        UUID newParent = parseOptionalUuid(body.parentId());
        Document d = documentService.move(user.getId(), UUID.fromString(id), newParent);
        Map<UUID, String> slugs = documentService.resolveSessionSlugs(List.of(d));
        return DocumentNode.from(d, slugs.get(d.getSessionId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticatedUser User user, @PathVariable String id) {
        documentService.delete(user.getId(), UUID.fromString(id));
    }

    private static UUID parseOptionalUuid(String value) {
        if (value == null || value.isBlank()) return null;
        return UUID.fromString(value);
    }

    private static Document.Kind parseKind(String kind) {
        if (kind == null) {
            throw new InvalidTreeMoveException("kind is required (\"folder\" or \"doc\")");
        }
        try {
            return Document.Kind.valueOf(kind);
        } catch (IllegalArgumentException e) {
            throw new InvalidTreeMoveException("Invalid kind: " + kind);
        }
    }
}
