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

## Test

```bash
./mvnw test
```

## Useful troubleshooting notes

- Confirm Docker is running before starting PostgreSQL.
- If PostgreSQL is already using the configured port, stop the conflicting container or update the Compose configuration.
- If Maven wrapper permissions fail, run `chmod +x ./mvnw`.
- Check application configuration before changing database credentials.
