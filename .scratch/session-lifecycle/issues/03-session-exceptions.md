# 03 — Session Exceptions

Status: done

## What

Create custom exception classes in `com.parchie.exception`.

## Requirements

- [ ] `SessionNotFoundException` — thrown when a session ID doesn't exist or is expired
  - Extends `RuntimeException`
  - Annotated with `@ResponseStatus(HttpStatus.NOT_FOUND)`
  - Constructor takes a `UUID` and produces a message like `"Session not found: <id>"`
- [ ] `SessionLockedException` — thrown when trying to write to a locked session
  - Extends `RuntimeException`
  - Annotated with `@ResponseStatus(HttpStatus.FORBIDDEN)`
  - Constructor takes a `UUID`

## Hints

- `@ResponseStatus` makes Spring automatically return the right HTTP status when these are thrown from a controller — no need for a separate exception handler

## Files

- `src/main/java/com/parchie/exception/SessionNotFoundException.java`
- `src/main/java/com/parchie/exception/SessionLockedException.java`
