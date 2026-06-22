# 08 — Blackbox Cucumber tests

Status: done

## What

Re-scoped from service-level whitebox (already covered by `SessionServiceTest`) to **blackbox** end-to-end coverage through the REST API and WebSocket relay using Cucumber.

## Requirements

- [x] Cucumber + JUnit Platform suite runner wired to `@SpringBootTest(RANDOM_PORT)` with Testcontainers Postgres
- [x] `@ScenarioScope` shared context for session aliases, WS clients, and last REST response
- [x] `@Before` hook truncates the database between scenarios
- [x] Feature: session lifecycle (create / fetch metadata / 404 on missing)
- [x] Feature: encrypted state (PUT → GET round-trip / 204 on fresh / 403 when locked / 404 on missing)
- [x] Feature: settings (PATCH locked, password, 404 on missing)
- [x] Feature: WebSocket relay (broadcast / no-echo / session isolation / unknown-session refused)

## Details

- Cucumber 7.x with `cucumber-spring`, glue at `com.parchie.bdd`
- REST steps use the JDK `HttpClient` so PATCH and octet-stream PUT/GET work without extra dependencies
- WebSocket steps use `StandardWebSocketClient` and `SessionRelayHandler#peerCount` to synchronise on server-side peer registration before sending
- The existing whitebox `SessionServiceTest` is retained alongside the new blackbox suite

## Files

- `src/test/java/com/parchie/bdd/CucumberRunner.java`
- `src/test/java/com/parchie/bdd/CucumberSpringConfig.java`
- `src/test/java/com/parchie/bdd/ScenarioContext.java`
- `src/test/java/com/parchie/bdd/Hooks.java`
- `src/test/java/com/parchie/bdd/Session*Steps.java`, `WebSocketRelaySteps.java`, `HexBytes.java`
- `src/test/resources/features/*.feature`
- `src/test/resources/junit-platform.properties`
