package com.parchie.dto;

import com.parchie.model.Document;

import java.time.Instant;

/**
 * Request/response DTOs for the /api/docs endpoints. One file because each
 * shape is tiny.
 */
public final class DocumentDtos {

    private DocumentDtos() {}

    /** Response: one node in the tree. Frontend builds the tree from a flat
     *  list using parent_id. */
    public record DocumentNode(
            String id,
            String parentId,
            String kind,
            String name,
            String sessionSlug,
            Instant createdAt,
            Instant updatedAt) {

        public static DocumentNode from(Document d, String slug) {
            return new DocumentNode(
                    d.getId().toString(),
                    d.getParentId() == null ? null : d.getParentId().toString(),
                    d.getKind().name(),
                    d.getName(),
                    slug,
                    d.getCreatedAt(),
                    d.getUpdatedAt());
        }
    }

    /** Request: create a folder or a doc. `parentId` is optional (null = root).
     *  `kind` must be either "folder" or "doc". */
    public record CreateRequest(String name, String kind, String parentId) {}

    /** Request: rename. */
    public record RenameRequest(String name) {}

    /** Request: move. `parentId` may be null to move to root. */
    public record MoveRequest(String parentId) {}
}
