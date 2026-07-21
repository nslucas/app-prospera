# Repository Guidelines

## Project Structure & Module Organization

Prospera is a Java 17 Spring Boot REST API. Production code lives under `src/main/java/com/example/prospera`, organized into `Resources` (HTTP controllers), `Services` (business logic), `Entities`, `DTO`, `repositories`, `Exceptions`, `Config`, and `Infra/Security`. Runtime configuration and versioned Flyway SQL migrations are in `src/main/resources`; add schema changes as a new `db/migration/V<next>__Description.sql` file. Tests mirror the application packages under `src/test/java`. API handoff notes live in `docs/`, while `collection.json` contains request examples.

## Build, Test, and Development Commands

Use the checked-in Maven wrapper so contributors share the same Maven version.

- `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`) starts the API on port 8080.
- `./mvnw test` runs the JUnit test suite.
- `./mvnw clean package` compiles, tests, and creates the executable JAR in `target/`.
- `docker compose up --build` starts the API and MySQL; first copy `.env.example` to `.env` and replace its secrets.
- `docker compose down` stops the local stack without deleting database data.

Swagger UI is available at `http://localhost:8080/swagger-ui.html` while the API runs.

## Coding Style & Naming Conventions

Follow the existing Java style: four-space indentation, braces on the declaration line, constructor injection, and one public type per file. Use `PascalCase` for classes and records, `lowerCamelCase` for methods and variables, and `UPPER_SNAKE_CASE` for constants. Preserve the repository's existing package capitalization when adding nearby code. Keep controllers focused on HTTP concerns, business rules in services, and persistence queries in repositories. No automatic formatter or linter is configured, so match surrounding code and organize imports before committing.

## Testing Guidelines

Tests use JUnit 5, Mockito, and Spring Security test utilities through `spring-boot-starter-test`. Name test classes `*Test` and methods after observable behavior, for example `findByIdCannotReadAnotherUsersAccount`. Add focused service tests for business rules and resource/security tests for endpoint contracts. There is no configured coverage threshold; nevertheless, cover successful paths, validation failures, and authorization boundaries. Run `./mvnw test` before opening a pull request.

## Commit & Pull Request Guidelines

History follows Conventional Commits, commonly `feat: ...` and scoped forms such as `feat(budgets): ...`. Use an imperative, concise subject (`fix(auth): reject expired tokens`) and keep each commit focused. Pull requests should explain the behavior change, mention database migrations or configuration additions, link relevant issues, and include test evidence. For API changes, document affected endpoints and provide sample requests/responses; attach screenshots only for rendered documentation or UI-facing changes.

## Security & Configuration

Never commit `.env`, JWT/VAPID keys, database passwords, or production host details. Prefer environment-variable overrides documented in `.env.example`, and review authentication, ownership checks, CORS, and forwarded-header handling whenever exposing a new endpoint.
