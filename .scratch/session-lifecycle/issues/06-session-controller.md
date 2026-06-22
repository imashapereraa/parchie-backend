# 06 — Session REST Controller

Status: done

## What

Create the REST controller in `com.parchie.controller` that exposes session endpoints.

## Requirements

- [ ] `POST /api/sessions` — create a session, return `201 Created` + `SessionMetadataDto`
- [ ] `GET /api/sessions/{id}` — return `200` + `SessionMetadataDto`
- [ ] `PATCH /api/sessions/{id}` — accept `SessionSettingsDto` JSON body, return `200` + `SessionMetadataDto`
- [ ] `GET /api/sessions/{id}/state` — return encrypted blob as `application/octet-stream` (`200`), or `204 No Content` if the blob is null
- [ ] `PUT /api/sessions/{id}/state` — accept `application/octet-stream` body, save it, return `204 No Content`

## Details

- `@RestController` at the class level, base path `@RequestMapping("/api/sessions")`
- Spring auto-converts `{id}` to `UUID`
- The state endpoints deal with raw bytes — use `MediaType.APPLICATION_OCTET_STREAM_VALUE` on the relevant `produces`/`consumes`
- Constructor injection for `SessionService`
- `SessionNotFoundException` and `SessionLockedException` already carry `@ResponseStatus`, so no extra `@ExceptionHandler` is needed in this pass
- Unknown / expired session ID → `404` (from `SessionNotFoundException`)
- Locked session + `PUT /state` → `403` (from `SessionLockedException`)

## Depends on

- #04 (DTOs)
- #05 (SessionService)

## File

`src/main/java/com/parchie/controller/SessionController.java`
