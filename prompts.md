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

## Future prompts

Add each future feature prompt here with:

- Prompt number and title
- Model/tool used
- Prompt text or concise summary
- Files changed
- Commands run
