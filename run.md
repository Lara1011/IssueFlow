# IssueFlow Run Guide

Setup, run, and validation notes for the IssueFlow Spring Boot backend.

## Prerequisites

- Java 25
- Maven wrapper included in this repository (`./mvnw`); a separate Maven install is optional
- Docker and Docker Compose

## Quick Start

Recommended terminal command order:

```bash
docker compose up -d
./mvnw test
./mvnw clean package
./mvnw spring-boot:run
```

## Dependency Installation

No separate backend dependency installation step is required. The Maven wrapper downloads Java dependencies automatically when running:

```bash
./mvnw test
./mvnw clean package
```

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

## Run Packaged Jar

After packaging, the app can be run with:

```bash
java -jar target/issueflow-*.jar
```

## Authentication

Create an initial user:

```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","email":"admin@example.com","fullName":"Admin User","role":"ADMIN"}'
```

Users created through `POST /users` use the default password `secret`.

Login and use the returned bearer token:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"secret"}'
```

Protected requests require:

```bash
Authorization: Bearer <accessToken>
```

Postman can optionally be used for manual API testing, but all required setup, build, test, and validation steps can be run from the terminal.

Logout uses an in-memory token deny-list. Logged-out tokens are rejected while the app is running, but the deny-list resets when the app restarts; JWT expiration still applies.

## Optional UI for Manual Testing

The backend can be fully built, tested, and run from this repository without the UI. An optional Vite/React UI was created only as a manual testing and demo helper in a separate repository: `https://github.com/Lara1011/IssueFlow-UI.git`.

To use it, first start the backend:

```bash
docker compose up -d
./mvnw spring-boot:run
```

Then run the UI from its separate folder or clone:

```bash
git clone https://github.com/Lara1011/IssueFlow-UI.git
cd IssueFlow-UI
npm install
npm run dev
```

By default, the UI uses Vite's `/api` proxy to reach `http://localhost:8080`. It can also use `VITE_API_BASE_URL` if configured separately.

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
```

Expected result: Maven tests pass and the package build succeeds.

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
