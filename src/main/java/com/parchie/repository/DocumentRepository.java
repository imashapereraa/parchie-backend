package com.parchie.repository;

import com.parchie.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findAllByOwnerId(UUID ownerId);

    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Document> findBySessionId(UUID sessionId);
}
