# AI Prompts and Agent Usage

## Models/tools used

- ChatGPT - GPT-5.5 Thinking
- Codex coding agent/editor

## Prompt 0 - Project rules and workflow

Summary of initial instructions:

- Act as a Senior Backend Engineer and Spring Boot mentor for the AT&T TDP 2026 IssueFlow home assignment.
- Prioritize clean architecture, readable code, simple maintainable patterns, and strong test coverage.
- Work incrementally and avoid large risky changes.
- Treat the repository owner as accountable for every change by explaining important decisions briefly.
- Before implementing application features, create project guidance and planning documentation.
- Use ChatGPT GPT-5.5 Thinking for planning, prompt design, architecture review, and documentation help.
- Use Codex as the main coding agent/editor inside the project.
- Do not implement application features until explicitly requested.

Relevant files created from this prompt:

- `AGENTS.md`
- `prompts.md`
- `implementation-plan.md`
- `run.md`

## Prompt 1 – Initial project structure

Summary of prompt:

- Read `README.md` carefully and treat its API tables as the implementation contract.
- Inspect the existing Spring Boot starter structure, Maven configuration, application configuration, Docker Compose file, package names, and existing classes.
- Propose the package structure before editing.
- Create foundational packages for config, controller, DTOs, entities, enums, exceptions, repositories, security, services, scheduler, utilities, and test layout.
- Create the core enums, initial JPA entities, repository interfaces, basic services, basic controllers, placeholder DTOs, and global exception handling needed for the full IssueFlow assignment.
- Keep implementations minimal and safe, without JWT logic or business logic.
- Target Java 25 and ensure the project compiles and tests pass.
- Upgrade Spring Boot within the 3.x line if needed for Java 25 class-file compatibility.

Relevant files created or updated from this prompt:

- `pom.xml`
- `src/main/java/com/att/tdp/issueflow/config`
- `src/main/java/com/att/tdp/issueflow/controller`
- `src/main/java/com/att/tdp/issueflow/dto`
- `src/main/java/com/att/tdp/issueflow/entity`
- `src/main/java/com/att/tdp/issueflow/enums`
- `src/main/java/com/att/tdp/issueflow/exception`
- `src/main/java/com/att/tdp/issueflow/repository`
- `src/main/java/com/att/tdp/issueflow/security`
- `src/main/java/com/att/tdp/issueflow/service`
- `src/main/java/com/att/tdp/issueflow/scheduler`
- `src/main/java/com/att/tdp/issueflow/util`
- `src/test/java/com/att/tdp/issueflow/controller`
- `src/test/java/com/att/tdp/issueflow/service`
- `src/test/java/com/att/tdp/issueflow/repository`
- `prompts.md`
- `implementation-plan.md`

## Prompt 2 – Users API

Summary of prompt:

- Implement only the Users API feature from `README.md`.
- Do not implement authentication/JWT, projects, tickets, comments, or other features yet.
- Add request validation for username, email, full name, and role.
- Keep controllers thin and place business logic in `UserService`.
- Use DTOs rather than exposing JPA entities.
- Check duplicate username and email during user creation.
- Return consistent 404-style JSON errors for missing users through the global exception handler.
- Hard delete users, since soft delete is required only for tickets and projects.
- Add MockMvc integration tests for create, list, get by ID, update, delete, validation, duplicate checks, and not found.
- Run `./mvnw test` and mark the Users API plan item complete only if tests pass.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/controller/UserController.java`
- `src/main/java/com/att/tdp/issueflow/dto/UpdateUserRequest.java`
- `src/main/java/com/att/tdp/issueflow/repository/UserRepository.java`
- `src/main/java/com/att/tdp/issueflow/service/UserService.java`
- `src/test/java/com/att/tdp/issueflow/controller/UserControllerIntegrationTest.java`
- `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
- `prompts.md`
- `implementation-plan.md`

## Prompt 2.1 – Users API validation error fix

Summary of prompt:

- Fix only Users API validation and error handling behavior.
- Ensure invalid role values such as `MANAGER` return `400 Bad Request` with an informative message.
- Keep the response in the existing global JSON error format.
- Handle Jackson enum parse errors through `GlobalExceptionHandler`.
- Keep accepted roles limited to `ADMIN` and `DEVELOPER`.
- Improve validation messages for invalid email, blank username, blank full name, and missing role.
- Verify update behavior only changes `fullName` and `role`, and ignores extra `username` or `email` fields.
- Add or update tests for invalid role, invalid email, update behavior, and extra update fields.
- Run `./mvnw test`.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/dto/CreateUserRequest.java`
- `src/main/java/com/att/tdp/issueflow/dto/UpdateUserRequest.java`
- `src/main/java/com/att/tdp/issueflow/exception/GlobalExceptionHandler.java`
- `src/test/java/com/att/tdp/issueflow/controller/UserControllerIntegrationTest.java`
- `prompts.md`

## Prompt 3 – Projects API

Summary of prompt:

- Implement only the Projects API feature from `README.md`.
- Do not implement tickets, comments, authentication/JWT, restore endpoints, or other advanced features.
- Use DTOs and keep JPA entities out of controller responses.
- Keep `ProjectController` thin and place business logic in `ProjectService`.
- Validate project creation with required non-blank `name` and required `ownerId`.
- Validate project update so at least one of `name` or `description` is provided, and a provided `name` is not blank.
- Verify `ownerId` refers to an existing user when creating a project.
- Return clear 404-style JSON errors for missing owners, missing projects, and soft-deleted projects.
- Implement project deletion as soft delete by setting `deletedAt`.
- Keep standard project GET endpoints limited to non-deleted projects.
- Do not implement `/projects/deleted` or `/projects/{id}/restore` yet.
- Add MockMvc integration tests for create, owner not found, list filtering, get by ID, update, soft delete, hidden deleted records, and validation errors.
- Run `./mvnw test` and mark Projects API complete only if tests pass.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/controller/ProjectController.java`
- `src/main/java/com/att/tdp/issueflow/dto/CreateProjectRequest.java`
- `src/main/java/com/att/tdp/issueflow/repository/ProjectRepository.java`
- `src/main/java/com/att/tdp/issueflow/service/ProjectService.java`
- `src/test/java/com/att/tdp/issueflow/controller/ProjectControllerIntegrationTest.java`
- `prompts.md`
- `implementation-plan.md`

## Prompt 4 – Tickets API basic CRUD

Summary of prompt:

- Implement only the basic Tickets API CRUD feature from `README.md`.
- Do not implement lifecycle restrictions, dependencies, comments, attachments, import/export, authentication/JWT, auto-assignment, or auto-escalation yet.
- Use DTOs and keep JPA entities out of controller responses.
- Keep `TicketController` thin and place business logic in `TicketService`.
- Validate ticket creation with required `title`, `status`, `priority`, `type`, and `projectId`.
- Allow optional `description`, `assigneeId`, and `dueDate`.
- Validate ticket updates as partial updates for `title`, `description`, `status`, `priority`, `assigneeId`, and `dueDate`; do not allow updates to `type` or `projectId`.
- Verify `projectId` refers to an existing non-deleted project when creating or listing tickets.
- Verify `assigneeId` refers to an existing user when provided.
- Return clear 404-style JSON errors for missing projects, assignees, and tickets.
- Implement ticket deletion as soft delete by setting `deletedAt`.
- Keep ticket GET endpoints limited to non-deleted tickets.
- Return informative enum parse errors for invalid `status`, `priority`, and `type` values.
- Keep `isOverdue` as `false` for now because escalation logic is a later feature.
- Add MockMvc integration tests for create, create without assignee, missing project, missing assignee, list filtering, get by ID, update, soft delete, hidden deleted records, invalid enums, and validation errors.
- Run `./mvnw test` and mark Tickets API basic CRUD implementation/tests complete only if tests pass.

Relevant files created or updated from this prompt:

- `pom.xml`
- `src/main/java/com/att/tdp/issueflow/controller/TicketController.java`
- `src/main/java/com/att/tdp/issueflow/dto/CreateTicketRequest.java`
- `src/main/java/com/att/tdp/issueflow/dto/UpdateTicketRequest.java`
- `src/main/java/com/att/tdp/issueflow/dto/TicketResponse.java`
- `src/main/java/com/att/tdp/issueflow/entity/Ticket.java`
- `src/main/java/com/att/tdp/issueflow/exception/GlobalExceptionHandler.java`
- `src/main/java/com/att/tdp/issueflow/repository/TicketRepository.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketService.java`
- `src/main/resources/application.yaml`
- `src/test/resources/application.yaml`
- `src/test/java/com/att/tdp/issueflow/controller/TicketControllerIntegrationTest.java`
- `prompts.md`
- `implementation-plan.md`

Command run:

- `./mvnw test`

## Prompt 4.1 – Ticket update lifecycle rules and informative errors fix

Summary of prompt:

- Fix only Tickets API update behavior after manual Postman testing found that DONE tickets could still be updated.
- Enforce that a ticket cannot be updated once its existing status is `DONE`.
- Enforce forward-only lifecycle transitions: `TODO -> IN_PROGRESS -> IN_REVIEW -> DONE`.
- Reject backward transitions with one clear JSON error message.
- Allow same-status updates only when the current status is not `DONE`.
- Limit `PATCH /tickets/{ticketId}` request fields to `title`, `description`, `status`, `priority`, and `assigneeId`.
- Do not allow `projectId`, `type`, or `dueDate` to change through the ticket update endpoint.
- Keep controller logic thin and put lifecycle rules in `TicketService`.
- Return business-rule failures as clear `409 Conflict` JSON errors.
- Add MockMvc integration tests for DONE locking, backward transitions, allowed forward transitions, same-status updates, ignored fields, and error messages.
- Tighten error assertions so the JSON `message` field is non-empty and mentions `DONE` or both lifecycle statuses where required.
- Run `./mvnw test`.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/dto/UpdateTicketRequest.java`
- `src/main/java/com/att/tdp/issueflow/exception/BusinessRuleException.java`
- `src/main/java/com/att/tdp/issueflow/exception/GlobalExceptionHandler.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketService.java`
- `src/test/java/com/att/tdp/issueflow/controller/TicketControllerIntegrationTest.java`
- `prompts.md`
- `implementation-plan.md`

Command run:

- `./mvnw test`

## Prompt 5 – Ticket lifecycle rules and optimistic locking

Summary of prompt:

- Implement only ticket lifecycle rules and concurrency protection.
- Keep comments, dependencies, attachments, import/export, authentication/JWT, auto-assignment, and auto-escalation out of scope.
- Enforce strict step-by-step lifecycle movement for ticket updates: `TODO -> IN_PROGRESS -> IN_REVIEW -> DONE`.
- Allow same-status updates only when the ticket is not `DONE`.
- Reject skipped transitions such as `TODO -> IN_REVIEW`, `TODO -> DONE`, and `IN_PROGRESS -> DONE`.
- Reject backward transitions such as `IN_PROGRESS -> TODO`, `IN_REVIEW -> TODO`, and `IN_REVIEW -> IN_PROGRESS`.
- Reject every update to an existing `DONE` ticket, including `DONE -> DONE`.
- Return lifecycle business-rule failures as `400 Bad Request` with one clear main message.
- Include both current and requested statuses in invalid transition messages, plus the allowed next status.
- Keep `projectId` and `type` ignored for ticket update requests.
- Keep `dueDate` ignored for ticket update requests until the later auto-escalation feature.
- Use the existing `@Version` field on `Ticket` for JPA optimistic locking.
- Map optimistic locking failures to `409 Conflict` with an informative reload-and-retry message.
- Add integration tests for successful transitions, invalid transitions, DONE lock cases, ignored fields including `dueDate`, `@Version`, stale entity updates, and optimistic-locking error mapping.
- Run `./mvnw test` and mark Ticket lifecycle rules and optimistic locking complete only if tests pass.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/dto/UpdateTicketRequest.java`
- `src/main/java/com/att/tdp/issueflow/exception/GlobalExceptionHandler.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketService.java`
- `src/test/java/com/att/tdp/issueflow/controller/TicketControllerIntegrationTest.java`
- `src/test/java/com/att/tdp/issueflow/exception/GlobalExceptionHandlerTest.java`
- `src/test/java/com/att/tdp/issueflow/repository/TicketOptimisticLockingIntegrationTest.java`
- `prompts.md`
- `implementation-plan.md`

Command run:

- `./mvnw test`

## Prompt 5.1 – Ticket PATCH field contract correction

Summary of prompt:

- Correct `PATCH /tickets/{ticketId}` so it may update only `title`, `description`, `status`, `priority`, and `assigneeId`.
- Ensure `projectId`, `type`, and `dueDate` do not change through the ticket update endpoint.
- Keep `dueDate` supported for ticket creation and responses, but not for updates yet.
- Keep lifecycle and optimistic locking behavior unchanged.
- Update tests so a PATCH body containing `dueDate` is accepted as ignored JSON and leaves the stored due date unchanged.
- Run `./mvnw test`.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/dto/UpdateTicketRequest.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketService.java`
- `src/test/java/com/att/tdp/issueflow/controller/TicketControllerIntegrationTest.java`
- `prompts.md`

Command run:

- `./mvnw test`

## Prompt 6 – Comments API

Summary of prompt:

- Implement only the Comments API feature from `README.md`.
- Do not implement Mentions API yet, but include `mentionedUsers` as an empty list in comment responses.
- Do not implement audit logs, dependencies, attachments, import/export, authentication/JWT, auto-assignment, or auto-escalation.
- Use the existing `Comment` entity and its `@Version` field for optimistic locking.
- Use DTOs rather than exposing JPA entities from controllers.
- Add endpoints under `/tickets/{ticketId}/comments` for list, create, update, and delete.
- Validate comment creation with required `authorId` and non-blank `content`.
- Validate comment update with required non-blank `content`.
- Verify the target ticket exists and is not soft-deleted.
- Verify the author exists when adding a comment.
- Verify comments exist and belong to the `ticketId` in the URL before update/delete.
- Hard delete comments.
- Return informative errors for missing tickets, missing authors, missing comments, comments attached to a different ticket, and invalid content.
- Use the existing optimistic-locking exception handling for comment edit conflicts.
- Add MockMvc integration tests for create, list, update, delete, validation, not-found cases, wrong-ticket cases, and empty `mentionedUsers`.
- Add repository-level optimistic locking coverage for stale comment updates.
- Run `./mvnw test` and mark Comments API implementation/tests complete only if tests pass.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/controller/CommentController.java`
- `src/main/java/com/att/tdp/issueflow/dto/CreateCommentRequest.java`
- `src/main/java/com/att/tdp/issueflow/dto/UpdateCommentRequest.java`
- `src/main/java/com/att/tdp/issueflow/repository/CommentRepository.java`
- `src/main/java/com/att/tdp/issueflow/service/CommentService.java`
- `src/test/java/com/att/tdp/issueflow/controller/CommentControllerIntegrationTest.java`
- `src/test/java/com/att/tdp/issueflow/repository/CommentOptimisticLockingIntegrationTest.java`
- `prompts.md`
- `implementation-plan.md`

Command run:

- `./mvnw test`

## Prompt 7 – Mentions API

Summary of prompt:

- Implement only the Mentions feature.
- Parse `@username` references in comment content.
- Match usernames case-insensitively and ignore unknown usernames.
- Persist mention associations without duplicate rows for repeated usernames in the same comment.
- Populate `mentionedUsers` in existing comment responses.
- Re-evaluate mentions when a comment is updated, adding new mentions and removing mentions no longer present.
- Add `GET /users/{userId}/mentions` with `page` and `pageSize` query parameters.
- Return mentioned comments newest first with response shape `{ data, total, page }`.
- Validate mentioned user existence and page parameters with informative errors.
- Keep comments optimistic locking behavior unchanged.
- Do not implement audit logs, dependencies, attachments, import/export, authentication/JWT, auto-assignment, or auto-escalation.
- Add integration tests for parsing, case-insensitive matching, unknown users, duplicate mentions, comment response population, update re-sync, mention listing, ordering, pagination, not-found, and invalid paging.
- Run `./mvnw test` and mark Mentions implementation/tests complete only if tests pass.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/controller/MentionController.java`
- `src/main/java/com/att/tdp/issueflow/repository/CommentMentionRepository.java`
- `src/main/java/com/att/tdp/issueflow/repository/CommentRepository.java`
- `src/main/java/com/att/tdp/issueflow/repository/UserRepository.java`
- `src/main/java/com/att/tdp/issueflow/service/CommentService.java`
- `src/main/java/com/att/tdp/issueflow/service/MentionService.java`
- `src/test/java/com/att/tdp/issueflow/controller/CommentControllerIntegrationTest.java`
- `src/test/java/com/att/tdp/issueflow/controller/MentionControllerIntegrationTest.java`
- `prompts.md`
- `implementation-plan.md`

Command run:

- `./mvnw test`

## Prompt 8 – Audit Log API

Summary of prompt:

- Implement only Audit Log functionality.
- Add `GET /audit-logs` with optional filters for `entityType`, `entityId`, `action`, and `actor`.
- Return audit logs as DTOs sorted by `timestamp` descending, then `id` descending.
- Keep audit logs append-only with no update or delete endpoint.
- Add reusable `AuditLogService.record(...)`.
- Record audit logs for existing state-changing actions in Users, Projects, Tickets, and Comments.
- Use `AuditActor.USER` for existing state-changing actions.
- Use `performedBy = authorId` for comment create, update, and delete; use `null` for users/projects/tickets until authentication exists.
- Add informative `400` handling for invalid enum query parameter filters.
- Keep existing API response shapes unchanged.
- Do not implement dependencies, attachments, import/export, authentication/JWT, auto-assignment, or auto-escalation.
- Add integration tests for audit recording, retrieval sorting, filters, invalid enum filters, and existing test regression.
- Run `./mvnw test` and mark Audit Log implementation/tests complete only if tests pass.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/controller/AuditLogController.java`
- `src/main/java/com/att/tdp/issueflow/exception/GlobalExceptionHandler.java`
- `src/main/java/com/att/tdp/issueflow/repository/AuditLogRepository.java`
- `src/main/java/com/att/tdp/issueflow/service/AuditLogService.java`
- `src/main/java/com/att/tdp/issueflow/service/UserService.java`
- `src/main/java/com/att/tdp/issueflow/service/ProjectService.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketService.java`
- `src/main/java/com/att/tdp/issueflow/service/CommentService.java`
- `src/test/java/com/att/tdp/issueflow/controller/AuditLogControllerIntegrationTest.java`
- `prompts.md`
- `implementation-plan.md`

Command run:

- `./mvnw test`

## Future prompts

Add each future feature prompt here with:

- Prompt number and title
- Model/tool used
- Prompt text or concise summary
- Files changed
- Commands run
