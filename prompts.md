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

## Prompt 9 – Soft Delete API

Summary of prompt:

- Implement only Soft Delete API endpoints for projects and tickets.
- Add `GET /projects/deleted` and `POST /projects/{projectId}/restore`.
- Add `GET /tickets/deleted?projectId={projectId}` and `POST /tickets/{ticketId}/restore`.
- Use existing `deletedAt` soft-delete fields.
- Keep standard project and ticket GET endpoints hiding soft-deleted records.
- Return deleted records sorted by `deletedAt` descending, then `id` descending.
- Make restore idempotent for non-deleted projects and tickets.
- Reject ticket restore when the ticket's project is soft-deleted with an informative `400`.
- Record audit logs for `RESTORE PROJECT` and `RESTORE TICKET`.
- Add informative `400` handling for missing required request parameters.
- Do not implement dependencies, attachments, import/export, authentication/JWT, auto-assignment, or auto-escalation.
- Add integration tests for deleted lists, restores, idempotent restores, standard hidden behavior, project-deleted ticket restore rejection, audit logging, and existing test regression.
- Run `./mvnw test` and mark Soft Delete implementation/tests complete only if tests pass.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/controller/ProjectController.java`
- `src/main/java/com/att/tdp/issueflow/controller/TicketController.java`
- `src/main/java/com/att/tdp/issueflow/exception/GlobalExceptionHandler.java`
- `src/main/java/com/att/tdp/issueflow/repository/ProjectRepository.java`
- `src/main/java/com/att/tdp/issueflow/repository/TicketRepository.java`
- `src/main/java/com/att/tdp/issueflow/service/ProjectService.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketService.java`
- `src/test/java/com/att/tdp/issueflow/controller/SoftDeleteControllerIntegrationTest.java`
- `prompts.md`
- `implementation-plan.md`

Command run:

- `./mvnw test`

## Prompt 10 – Ticket Dependencies API

Summary of prompt:

- Implement only Ticket Dependencies API.
- Add `POST /tickets/{ticketId}/dependencies`, `GET /tickets/{ticketId}/dependencies`, and `DELETE /tickets/{ticketId}/dependencies/{blockerId}`.
- Use existing `TicketDependency` entity and dependency DTOs.
- Validate required `blockedBy` in add dependency requests.
- Verify both tickets exist, are not soft-deleted, and belong to the same project.
- Reject self-dependencies and cross-project dependencies with informative errors.
- Make duplicate add and missing remove operations idempotent.
- List blocker tickets using response shape `{ id, title, status }`, excluding soft-deleted blockers.
- Record audit logs for `ADD_DEPENDENCY` and `REMOVE_DEPENDENCY`.
- Enforce the DONE blocker rule in `TicketService`: a ticket cannot move to `DONE` while any non-deleted blocker is not `DONE`.
- Keep existing strict lifecycle behavior unchanged.
- Do not implement attachments, import/export, authentication/JWT, auto-assignment, or auto-escalation.
- Add integration tests for dependency CRUD, validation, same-project checks, self-dependency, duplicate handling, soft-deleted blockers, audit logs, and DONE transition blocking.
- Run `./mvnw test` and mark Ticket Dependencies implementation/tests complete only if tests pass.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/controller/TicketDependencyController.java`
- `src/main/java/com/att/tdp/issueflow/dto/AddDependencyRequest.java`
- `src/main/java/com/att/tdp/issueflow/repository/TicketDependencyRepository.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketDependencyService.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketService.java`
- `src/test/java/com/att/tdp/issueflow/controller/TicketDependencyControllerIntegrationTest.java`
- `prompts.md`
- `implementation-plan.md`

Command run:

- `./mvnw test`

## Prompt 11 – Attachments API

Summary of prompt:

- Implement only the Attachments API.
- Add `POST /tickets/{ticketId}/attachments` for multipart upload using field name `file`.
- Add `DELETE /tickets/{ticketId}/attachments/{attachmentId}` for attachment deletion.
- Verify the ticket exists and is not soft-deleted before upload or delete.
- Validate uploaded files are present, non-empty, no larger than 10 MB, and one of the allowed content types: `image/png`, `image/jpeg`, `application/pdf`, or `text/plain`.
- Store attachment metadata and file bytes in the database because the current API only requires metadata response and deletion.
- Return attachment upload responses with `id`, `ticketId`, `filename`, and `contentType`.
- Record audit logs for `UPLOAD_ATTACHMENT` and `DELETE_ATTACHMENT`.
- Return informative errors for missing tickets, empty or missing files, unsupported content type, oversized files, missing attachments, and attachments belonging to another ticket.
- Do not implement import/export, authentication/JWT, auto-assignment, or auto-escalation.
- Add MockMvc multipart integration tests for successful uploads, validation failures, deletion behavior, audit logging, and existing test regression.
- Run `./mvnw test` and mark Attachments implementation/tests complete only if tests pass.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/controller/AttachmentController.java`
- `src/main/java/com/att/tdp/issueflow/entity/Attachment.java`
- `src/main/java/com/att/tdp/issueflow/exception/GlobalExceptionHandler.java`
- `src/main/java/com/att/tdp/issueflow/repository/AttachmentRepository.java`
- `src/main/java/com/att/tdp/issueflow/service/AttachmentService.java`
- `src/test/java/com/att/tdp/issueflow/controller/AttachmentControllerIntegrationTest.java`
- `prompts.md`
- `implementation-plan.md`

Command run:

- `./mvnw test`

## Prompt 12 – Ticket Export/Import API

Summary of prompt:

- Implement only CSV ticket export/import.
- Add `GET /tickets/export?projectId={id}` returning a `text/csv` attachment named `tickets-project-{projectId}.csv`.
- Export only non-deleted tickets for an active project with header `id,title,description,status,priority,type,assigneeId`.
- Use Apache Commons CSV so commas, quotes, and newlines inside fields are handled correctly.
- Add `POST /tickets/import` accepting multipart `file` and form field `projectId`.
- Verify the target project exists and is not soft-deleted before export/import.
- Parse import CSV rows with columns `title`, `description`, `status`, `priority`, `type`, and `assigneeId`, ignoring any uploaded `id` column.
- Create imported tickets under the multipart `projectId`; do not trigger auto-assignment, authentication, or auto-escalation.
- Continue processing after row failures and return `{ created, failed, errors }` with row-level messages.
- Validate title, ticket enums, and optional assignee IDs for imported rows.
- Record audit logs for `EXPORT` and `IMPORT` with entity type `TICKET` and entity id equal to the project id.
- Add MockMvc integration tests for export content, CSV escaping, non-deleted filtering, import success, row failures, enum validation, invalid assignees, missing projects/files, audit logs, and existing test regression.
- Run `./mvnw test` and mark Export/Import implementation/tests complete only if tests pass.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/controller/TicketCsvController.java`
- `src/main/java/com/att/tdp/issueflow/repository/TicketRepository.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketCsvService.java`
- `src/test/java/com/att/tdp/issueflow/controller/TicketCsvControllerIntegrationTest.java`
- `prompts.md`
- `implementation-plan.md`

Command run:

- `./mvnw test`

## Prompt 13 – Authentication/JWT API

Summary of prompt:

- Implement only Authentication/JWT.
- Add Spring Security and protect all endpoints except `POST /auth/login` and public `POST /users` for initial user creation/testing.
- Add `POST /auth/login`, `POST /auth/logout`, and `GET /auth/me`.
- Generate signed HMAC-SHA256 JWT access tokens with configurable secret and 3600-second default expiration.
- Validate bearer tokens with a JWT filter and return consistent JSON `401` errors for missing, invalid, expired, and logged-out tokens.
- Add in-memory token deny-list logout invalidation.
- Add password hashing with `PasswordEncoder`.
- Keep the existing `POST /users` request contract unchanged and default created users to password `secret`.
- Do not include passwords in response DTOs.
- Record audit logs for successful `LOGIN` and `LOGOUT`.
- Update existing controller tests to run with mock authenticated users where they are not testing JWT behavior.
- Add authentication integration tests for registration, login success/failure, protected endpoints, current user profile, logout invalidation, and audit logs.
- Update `run.md` with manual authentication steps.
- Run `./mvnw test` and mark Authentication/JWT implementation/tests complete only if tests pass.

Relevant files created or updated from this prompt:

- `pom.xml`
- `src/main/java/com/att/tdp/issueflow/config/SecurityConfig.java`
- `src/main/java/com/att/tdp/issueflow/controller/AuthController.java`
- `src/main/java/com/att/tdp/issueflow/exception/AuthenticationFailedException.java`
- `src/main/java/com/att/tdp/issueflow/exception/GlobalExceptionHandler.java`
- `src/main/java/com/att/tdp/issueflow/repository/UserRepository.java`
- `src/main/java/com/att/tdp/issueflow/security`
- `src/main/java/com/att/tdp/issueflow/service/AuthService.java`
- `src/main/java/com/att/tdp/issueflow/service/UserService.java`
- `src/main/resources/application.yaml`
- `src/test/resources/application.yaml`
- `src/test/java/com/att/tdp/issueflow/controller/AuthControllerIntegrationTest.java`
- Existing controller integration tests
- `run.md`
- `prompts.md`
- `implementation-plan.md`

Command run:

- `./mvnw test`

## Prompt 14 – Auto-Assignment and Workload API

Summary of prompt:

- Implement only Auto-Assignment and Workload API.
- On `POST /tickets`, auto-assign a ticket only when `assigneeId` is omitted.
- Use all users with role `DEVELOPER` as project candidates because the README does not define a project membership API; exclude `ADMIN` users.
- Calculate workload as the count of non-deleted, non-`DONE` tickets assigned to each developer within the target project.
- Choose the developer with the lowest workload, breaking ties by oldest registration order / smallest user id.
- Leave tickets unassigned without error when no developer exists.
- Do not trigger auto-assignment on ticket update; explicit `assigneeId` values on create or update override assignment behavior.
- Add `GET /projects/{projectId}/workload`, returning all developers with `userId`, `username`, and `openTicketCount`, sorted by workload then user id.
- Record `AUTO_ASSIGN` audit logs with actor `SYSTEM` only when the system assigns a developer.
- Keep existing authentication behavior and require JWT like other protected endpoints.
- Add integration tests for assignment selection, admin exclusion, tie-breaking, no-developer behavior, explicit overrides, workload sorting/counting, audit logs, project validation, and regression.
- Run `./mvnw test` and mark Auto-assignment implementation/tests complete only if tests pass.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/controller/ProjectController.java`
- `src/main/java/com/att/tdp/issueflow/repository/TicketRepository.java`
- `src/main/java/com/att/tdp/issueflow/repository/UserRepository.java`
- `src/main/java/com/att/tdp/issueflow/service/ProjectService.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketService.java`
- `src/main/java/com/att/tdp/issueflow/service/WorkloadService.java`
- `src/test/java/com/att/tdp/issueflow/controller/AutoAssignmentWorkloadIntegrationTest.java`
- `src/test/java/com/att/tdp/issueflow/controller/AuditLogControllerIntegrationTest.java`
- `prompts.md`
- `implementation-plan.md`

Command run:

- `./mvnw test`

## Prompt 15 – Auto-Escalation Scheduler

Summary of prompt:

- Implement only Auto-Escalation scheduler behavior.
- Keep changes focused on ticket `dueDate`, `isOverdue`, priority escalation, scheduler/service logic, audit logs, and tests.
- Persist `isOverdue` on tickets and return it in ticket responses.
- Continue accepting optional `dueDate` on ticket creation and now allow `dueDate` updates through `PATCH /tickets/{ticketId}`.
- Keep `projectId` and `type` non-updatable and keep DONE-ticket update restrictions unchanged.
- Add `TicketEscalationService` with public `runEscalationCycle()` for tests and a scheduled wrapper for runtime.
- Enable scheduling and make the escalation interval configurable through `issueflow.auto-escalation`.
- Escalate only active, non-DONE, non-deleted tickets with a due date before the current time.
- Promote overdue priorities one level per cycle: `LOW -> MEDIUM -> HIGH -> CRITICAL`.
- Set `isOverdue = true` once an overdue ticket is `CRITICAL`; do not escalate beyond `CRITICAL`.
- Clear `isOverdue` when a user manually changes priority through PATCH.
- Record `AUTO_ESCALATE` audit logs with actor `SYSTEM` when escalation changes priority or `isOverdue`.
- Add integration tests for due date create/update behavior, escalation levels, exclusions, status preservation, manual priority reset, audit logging, and duplicate audit prevention.
- Run `./mvnw test` and mark Auto-escalation implementation/tests complete only if tests pass.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/IssueFlowApplication.java`
- `src/main/java/com/att/tdp/issueflow/dto/UpdateTicketRequest.java`
- `src/main/java/com/att/tdp/issueflow/entity/Ticket.java`
- `src/main/java/com/att/tdp/issueflow/repository/TicketRepository.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketEscalationService.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketService.java`
- `src/main/resources/application.yaml`
- `src/test/resources/application.yaml`
- `src/test/java/com/att/tdp/issueflow/controller/TicketControllerIntegrationTest.java`
- `src/test/java/com/att/tdp/issueflow/service/TicketEscalationServiceIntegrationTest.java`
- `run.md`
- `prompts.md`
- `implementation-plan.md`

Command run:

- `./mvnw test`

## Prompt 16 – Authorization and final compliance hardening

Summary of prompt:

- Implement only authorization hardening and final compliance cleanup.
- Enforce ADMIN-only access for soft-delete list and restore endpoints:
  - `GET /projects/deleted`
  - `POST /projects/{projectId}/restore`
  - `GET /tickets/deleted?projectId={projectId}`
  - `POST /tickets/{ticketId}/restore`
- Return consistent JSON `403 Forbidden` responses with a useful message when non-admin users access ADMIN-only endpoints.
- Add `CurrentUserProvider` so authenticated state-changing requests can record audit `performedBy` from the JWT principal without making controllers responsible for audit details.
- Apply authenticated `performedBy` attribution to project, ticket, comment update/delete, dependency, attachment, and CSV import/export actions.
- Preserve request-body author attribution for comment creation.
- Keep SYSTEM actions unchanged: `AUTO_ASSIGN` and `AUTO_ESCALATE` continue using actor `SYSTEM` and `performedBy = null`.
- Verify controller mappings cover all README endpoint groups: users, auth, projects, tickets, comments, audit logs, dependencies, attachments, mentions, workload, and CSV import/export.
- Update `run.md` with Docker PostgreSQL startup, tests, app run, first user creation, login, Bearer token usage in Postman, default password, scheduler interval, and DB-backed attachment storage notes.
- Add JWT-based integration tests for ADMIN and DEVELOPER access to deleted list/restore endpoints and authenticated audit attribution.
- Run `./mvnw test` and mark final cleanup implementation/tests complete only if tests pass.

Relevant files created or updated from this prompt:

- `src/main/java/com/att/tdp/issueflow/config/SecurityConfig.java`
- `src/main/java/com/att/tdp/issueflow/security/CurrentUserProvider.java`
- `src/main/java/com/att/tdp/issueflow/security/JsonAccessDeniedHandler.java`
- `src/main/java/com/att/tdp/issueflow/service/AttachmentService.java`
- `src/main/java/com/att/tdp/issueflow/service/CommentService.java`
- `src/main/java/com/att/tdp/issueflow/service/ProjectService.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketCsvService.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketDependencyService.java`
- `src/main/java/com/att/tdp/issueflow/service/TicketService.java`
- `src/test/java/com/att/tdp/issueflow/controller/AuthorizationHardeningIntegrationTest.java`
- `src/test/java/com/att/tdp/issueflow/controller/SoftDeleteControllerIntegrationTest.java`
- `run.md`
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
