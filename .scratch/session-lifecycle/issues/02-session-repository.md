# 02 — Session Repository

Status: done

## What

Create a Spring Data JPA repository for `Session` in `com.parchie.repository`.

V2 collapsed the schema (see `V2__collapse_sessions_and_expires_at.sql` and ADR-0005), so there is no longer a `session_states` table. `Session` owns `encrypted_state` and `expires_at` directly, so a single repository is enough.

## SessionRepository

- [ ] Interface extending `JpaRepository<Session, UUID>`
- [ ] Custom method `int deleteAllExpired()` that deletes sessions whose `expiresAt` is in the past
- [ ] Annotate with `@Modifying`, `@Transactional`, and `@Query("DELETE FROM Session s WHERE s.expiresAt < CURRENT_TIMESTAMP")`
- [ ] Return `int` so the cleanup job (issue 05) can log the count

```java
@Modifying
@Transactional
@Query("DELETE FROM Session s WHERE s.expiresAt < CURRENT_TIMESTAMP")
int deleteAllExpired();
```

`@Transactional` (Spring's, not Jakarta's) on the method means the delete opens its own transaction when called outside a service-level `@Transactional` boundary — otherwise Spring Data rejects the modifying query with `InvalidDataAccessApiUsage`.

## Hints

- JPQL refers to the entity (`Session`) and field (`s.expiresAt`), not the table/column names.
- `@Modifying` is required for any DML query (insert/update/delete).
- `JpaRepository` already gives `save`, `findById`, `findAll`, `deleteAll`, etc. — no custom methods needed for basic CRUD.

## Depends on

- #01 (Session entity)

## Files

- `src/main/java/com/parchie/repository/SessionRepository.java`
