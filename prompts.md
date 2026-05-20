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

## Future prompts

Add each future feature prompt here with:

- Prompt number and title
- Model/tool used
- Prompt text or concise summary
- Files changed
- Commands run
