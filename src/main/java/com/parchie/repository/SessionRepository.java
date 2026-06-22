package com.parchie.repository;

import com.parchie.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    Optional<Session> findBySlug(String slug);

    @Modifying
    @Transactional
    @Query("DELETE FROM Session s WHERE s.expiresAt < CURRENT_TIMESTAMP")
    int deleteAllExpired();
}
