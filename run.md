# IssueFlow Run Guide

Setup, run, and validation notes for the IssueFlow Spring Boot backend.

## Prerequisites

- Java 25
- Maven wrapper included in this repository (`./mvnw`); a separate Maven install is optional
- Docker and Docker Compose

## Start PostgreSQL with Docker Compose

```bash
docker compose up -d
```

## Run Tests

```bash
./mvnw test
```

## Build

```bash
./mvnw clean package
```

## Run the App

```bash
./mvnw spring-boot:run
```

After packaging, the app can also be run with:

```bash
java -jar target/issueflow-*.jar
```

## Authentication

Create an initial user:

```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"username":"lara","email":"laraabuhamad@gmail.com","fullName":"Lara Abuhamad","role":"DEVELOPER"}'
```

Users created through `POST /users` use the default password `secret`.

Login and use the returned bearer token:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"lara","password":"secret"}'
```

Protected requests require:

```bash
Authorization: Bearer <accessToken>
```

In Postman, set the request authorization type to `Bearer Token` and paste the `accessToken` value returned by `POST /auth/login`.

Logout uses an in-memory token deny-list. Logged-out tokens are rejected while the app is running, but the deny-list resets when the app restarts; JWT expiration still applies.

## Authorization

Most endpoints require a valid bearer token. Soft-delete listing and restore endpoints are ADMIN-only:

- `GET /projects/deleted`
- `POST /projects/{projectId}/restore`
- `GET /tickets/deleted?projectId={projectId}`
- `POST /tickets/{ticketId}/restore`

## Auto-Escalation

Overdue tickets are auto-escalated on a configurable schedule using `issueflow.auto-escalation.fixed-delay-ms`.
The scheduler starts after `issueflow.auto-escalation.initial-delay-ms`, which defaults to `10000` ms, and then runs every `60000` ms by default.
When manually testing auto-escalation, wait at least one full scheduler interval after creating overdue tickets.
The escalation logic is also covered directly by service-level tests through `TicketEscalationService.runEscalationCycle()`.

The HTTP contract scheduler check can be run with:

```bash
python3 scripts/http_contract_check.py --include-scheduler --scheduler-wait-seconds 90
```

If an old local Docker database has schema issues after model changes, reset the local database:

```bash
docker compose down -v
docker compose up -d
```

`docker compose down -v` deletes local PostgreSQL data volumes.

## Attachments

Attachments are stored in the database as bytes for this assignment. The API currently returns upload metadata and supports deletion; no separate file download endpoint is implemented.

## Local Defaults

The PostgreSQL credentials and JWT secret in `application.yaml` are local development defaults only. Replace them before using this service outside a local assignment environment.

## Recommended Final Validation

```bash
./mvnw test
./mvnw clean package
python3 scripts/http_contract_check.py --include-scheduler --scheduler-wait-seconds 90
```

Expected result: Maven tests pass, the package build succeeds, and the HTTP contract check reports `Failed: 0`.

## Useful troubleshooting notes

- Confirm Docker is running before starting PostgreSQL.
- If PostgreSQL is already using the configured port, stop the conflicting container or update the Compose configuration.
- If the app fails with `Web server failed to start. Port 8080 was already in use.`, another Spring Boot process is already using the application port. This is different from PostgreSQL port `5432` conflicts.
  - Identify the process using port `8080`: `lsof -i :8080`
  - Stop the conflicting process: `kill -9 <PID>`
  - If the app is already running in another terminal or IntelliJ, stop it with `Ctrl+C` before starting it again.
  - Or change the application port in `application.yaml`:
    ```yaml
    server:
      port: 8081
    ```
- If Maven wrapper permissions fail, run `chmod +x ./mvnw`.
- Check application configuration before changing database credentials.
- A `401 Unauthorized` response usually means the bearer token is missing, invalid, expired, or logged out.
