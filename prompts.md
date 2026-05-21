# AI Prompts and Agent Usage

## Models and tools used

- ChatGPT - GPT-5.5 Thinking
- Codex coding agent/editor

## Accountability note

I used AI assistance during planning, implementation, debugging, and documentation for the IssueFlow assignment. I reviewed, tested, and validated the generated code and behavior throughout the project. I am fully accountable for the submitted code.

## Project instructions

Project-specific AI guidance was documented in `AGENTS.md`. The guidance instructed Codex to work as a Senior Backend Engineer focused on Java, Spring Boot, REST APIs, PostgreSQL, maintainable backend design, DTO/entity separation, validation, consistent JSON errors, tests after each feature, soft delete for tickets/projects, and optimistic locking where required.

## Main workflow prompts

### Prompt 0 - Project rules and workflow

Set the overall development workflow for the AT&T TDP 2026 IssueFlow home assignment. The prompt established incremental feature-by-feature work, senior backend engineering standards, use of README API tables as the implementation contract, and documentation of AI usage.

### Prompt 1 - Initial project structure

Asked Codex to inspect the Spring Boot starter project, read `README.md`, target Java 25, and create the foundational backend structure: packages, enums, entities, repositories, services, controllers, DTOs, exception handling, and test layout. Business logic was intentionally kept minimal at this stage.

### Prompt 2 - Users API

Implemented the Users API only: create, list, fetch by ID, update full name/role, delete, validation, uniqueness checks, DTO responses, and integration tests.

### Prompt 3 - Projects API

Implemented project creation, listing, fetch by ID, update, soft delete, owner validation, hidden soft-deleted records, DTO responses, validation, and integration tests.

### Prompt 4 - Tickets API basic CRUD

Implemented ticket creation, listing by project, fetch by ID, update, soft delete, DTO responses, project/assignee validation, enum validation, and integration tests.

### Prompt 5 - Ticket lifecycle rules and optimistic locking

Added strict ticket status lifecycle rules, DONE-ticket update lock, informative business-rule errors, ticket optimistic locking with `@Version`, and tests covering valid/invalid transitions and concurrency protection.

### Prompt 6 - Comments API

Implemented comment creation, listing by ticket, content update, deletion, ticket/author validation, comment-ticket ownership checks, comment optimistic locking, and integration tests.

### Prompt 7 - Mentions API

Implemented `@username` parsing in comments, case-insensitive matching, persisted mention associations, mention re-evaluation on comment update, `mentionedUsers` response data, `GET /users/{userId}/mentions`, pagination, and tests.

### Prompt 8 - Audit Log API

Implemented persistent append-only audit logs, `GET /audit-logs` filtering, audit DTOs, invalid filter handling, and audit recording for implemented state-changing actions.

### Prompt 9 - Soft Delete API

Implemented deleted-list and restore endpoints for projects and tickets, idempotent restore behavior, hidden soft-deleted records in standard GET endpoints, restore audit logs, and tests.

### Prompt 10 - Ticket Dependencies API

Implemented add/list/remove ticket dependencies, same-project validation, self-dependency rejection, duplicate handling, unresolved-blocker rule for DONE transitions, dependency audit logs, and tests.

### Prompt 11 - Attachments API

Implemented multipart attachment upload and delete for tickets, 10 MB size validation, allowed content type validation, DB-backed attachment storage, upload/delete audit logs, and tests.

### Prompt 12 - Ticket Export/Import API

Implemented CSV export and import using a CSV library, correct CSV escaping, row-level import validation and error reporting, project/assignee validation, import/export audit logs, and integration tests.

### Prompt 13 - Authentication/JWT API

Implemented login, logout, current user profile, JWT generation/validation, bearer-token security filter, public login/user creation, protected endpoints, in-memory logout deny-list, password encoding, auth audit logs, and security tests.

### Prompt 14 - Auto-Assignment and Workload API

Implemented automatic assignment to the least-loaded DEVELOPER when no assignee is provided, workload calculation and sorting, `GET /projects/{projectId}/workload`, AUTO_ASSIGN audit logs, and tests. Because no project-membership API exists, the documented assignment interpretation is that all DEVELOPER users are candidates for every project.

### Prompt 15 - Auto-Escalation Scheduler

Implemented due-date support, scheduled priority escalation, `isOverdue` behavior, manual priority reset behavior, AUTO_ESCALATE audit logs, scheduler configuration, and service-level tests.

### Prompt 16 - Authorization and final compliance hardening

Hardened authorization by enforcing ADMIN-only access for deleted-list and restore endpoints, improved audit `performedBy` attribution for authenticated state-changing requests, verified endpoint coverage, updated documentation, and added authorization/audit tests.

### Prompt 17 - Final submission readiness

Reviewed README endpoint coverage, `run.md`, AI usage documentation, implementation plan completion, local secret risk, build/test status, and submission readiness.

## Review, debugging, and hardening prompts

During manual review and testing, I used AI to help validate the backend behavior and improve reliability.

The main review prompts focused on:

- Improving input validation and informative JSON error responses.
- Fixing ticket lifecycle behavior found during Postman testing, including backward status transitions and DONE-ticket locking.
- Hardening authentication behavior, including JWT error handling and logout behavior.
- Verifying attachment upload validation, CSV import/export behavior, soft delete behavior, and audit logging.
- Validating the live auto-escalation scheduler with real HTTP requests.
- Preparing final submission documentation and checking that generated files, secrets, and build artifacts were not committed.

These prompts were used to review and strengthen the implementation after each feature was built. The final validation was done using Maven tests, package build, Postman/manual checks, and the HTTP contract script.
## Final validation

For final validation, I asked the AI to help me create an HTTP contract test script that covers all implemented features. The script sends real requests to the running Spring Boot application and checks both allowed requests and bad requests, so I could compare the expected result with the actual API response.

The required assignment implementation is the backend API. In addition to Postman and automated tests, I also created a small local UI/testing helper to manually exercise backend flows more comfortably during development. That UI was used only as a development and manual testing aid and is not part of the submitted backend solution unless explicitly added later.

Backend validation was performed with:

```bash
./mvnw test
./mvnw clean package
python3 scripts/http_contract_check.py --include-scheduler --scheduler-wait-seconds 90
```

Manual API checks were also performed with Postman.

Final HTTP contract result:

- Passed: 80
- Failed: 0
- Skipped: 1

The skipped optimistic-locking-via-HTTP check is expected because the public API does not expose a version field or `If-Match` header. Optimistic locking is still implemented and covered by repository/integration tests for Ticket and Comment.
