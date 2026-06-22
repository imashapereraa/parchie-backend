package com.parchie.repository;

import com.parchie.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    Optional<Session> findBySlug(String slug);

    @Query("SELECT s FROM Session s WHERE s.expiresAt < :now")
    List<Session> findAllExpiredBefore(Instant now);
}
