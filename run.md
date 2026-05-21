# Run Guide

## Prerequisites

- Java 25
- Maven
- Docker and Docker Compose

## Start PostgreSQL with Docker Compose

```bash
docker compose up -d
```

## Build

```bash
./mvnw clean package
```

## Run

```bash
./mvnw spring-boot:run
```

## Authentication

Create an initial user:

```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"username":"jdoe","email":"jdoe@example.com","fullName":"John Doe","role":"DEVELOPER"}'
```

Users created through `POST /users` use the default password `secret`.

Login and use the returned bearer token:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"jdoe","password":"secret"}'
```

Protected requests require:

```bash
Authorization: Bearer <accessToken>
```

## Auto-Escalation

Overdue tickets are auto-escalated on a configurable schedule using `issueflow.auto-escalation.fixed-delay-ms`.
The escalation logic is also covered directly by service-level tests through `TicketEscalationService.runEscalationCycle()`.

## Test

```bash
./mvnw test
```

## Useful troubleshooting notes

- Confirm Docker is running before starting PostgreSQL.
- If PostgreSQL is already using the configured port, stop the conflicting container or update the Compose configuration.
- If Maven wrapper permissions fail, run `chmod +x ./mvnw`.
- Check application configuration before changing database credentials.
- A `401 Unauthorized` response usually means the bearer token is missing, invalid, expired, or logged out.
