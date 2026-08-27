# Contributing

Thank you for your interest in contributing! This guide will help you get started.

> This file is synced from [grails-plugin-template](https://github.com/grails-plugins/grails-plugin-template) and
> kept generic on purpose — it should read correctly in every repo built on the template. To lock it and stop
> receiving updates, add a `CONTRIBUTING.md.lock` file next to it.

## Code of Conduct

This project has a Code of Conduct. By participating, you are expected to uphold it. Please report
unacceptable behavior to the project maintainers.

## Getting Started

### Prerequisites

Install [SDKMAN!](https://sdkman.io/) to manage JDK, Gradle, and Groovy versions:

```bash
curl -s "https://get.sdkman.io" | bash
```

### Setting Up the Development Environment

```bash
# Clone the repository
git clone <this repository's URL>
cd <the cloned directory>

# Install the required SDK versions (Java, Gradle, Groovy — see .sdkmanrc)
sdk env install

# Build the project
./gradlew build
```

### Project Structure

```
.
├── plugin/              # The publishable Grails plugin (source + unit tests ONLY)
├── examples/app1/       # Example app with integration tests
├── build-logic/         # Gradle convention plugins (shared build configuration)
├── docs/                # Asciidoctor documentation
└── .agents/skills/      # AI agent skills (.claude is a symlink to .agents)
```

Key architectural rules:

- **Plugin module** contains only plugin source code and unit tests – no integration tests, no example controllers.
- **Example apps** under `examples/` host all integration and functional tests. They depend on the plugin as a real
  consumer would.
- **Convention plugins** in `build-logic/` deduplicate build configuration. Never use `subprojects {}`,
  `allprojects {}`, or `configure()` blocks in the root `build.gradle`.

## Building and Testing

```bash
# Full build (compile + all tests)
./gradlew build

# Plugin unit tests only
./gradlew test

# Integration tests (runs the example app)
./gradlew :app1:integrationTest

# Run the example app locally
./gradlew :app1:bootRun

# Generate documentation
./gradlew docs

# Skip tests
./gradlew build -PskipTests

# Clean build
./gradlew clean build
```

### Code Coverage

The project uses JaCoCo to aggregate coverage data from both plugin unit tests and example app integration tests.

```bash
# Generate the aggregated coverage report
./gradlew jacocoAggregatedReport
```

Reports are generated at:

| Report                          | Location                                                                         |
|---------------------------------|----------------------------------------------------------------------------------|
| Aggregated (unit + integration) | `code-coverage/build/reports/jacoco/jacocoAggregatedReport/html/index.html`      |
| Plugin unit tests               | `plugin/build/reports/jacoco/test/html/index.html`                               |
| App1 integration tests          | `examples/app1/build/reports/jacoco/jacocoIntegrationTestReport/html/index.html` |

The aggregated report is also produced automatically as part of `./gradlew build`, so you can view it after any full
build.

## Making Changes

### Branching Strategy

- Create a feature branch from the current release branch (e.g., `0.1.x`):
    - `feature/short-description` for new features
    - `fix/short-description` for bug fixes
    - `docs/short-description` for documentation changes
    - `refactor/short-description` for refactoring

These branch prefixes are used by [release-drafter](https://github.com/release-drafter/release-drafter) to automatically
categorize changes in release notes.

### Coding Standards

- **Language:** Groovy on Java (see `.sdkmanrc` for exact versions)
- **Framework:** Grails
- **Testing:** Spock Framework on JUnit Platform
- Follow existing code conventions in the project

### Gradle Conventions

- Always use lazy APIs: `tasks.register()`, `tasks.named()`, `configureEach`, `provider {}`
- Never use eager task creation (`tasks.create()`, `project.task()`)
- If two or more subprojects share build logic, extract it into a convention plugin in `build-logic/`

## Submitting a Pull Request

1. **Ensure all tests pass** locally: `./gradlew build`
2. **Write tests** for new functionality:
    - Unit tests go in `plugin/src/test/`
    - Integration tests go in `examples/app1/src/integration-test/`
3. **Update documentation** if you changed behavior or added features (in `docs/src/docs/`)
4. **Push your branch** and open a pull request against the release branch
5. **Fill out the PR template** if the repository provides one

### What to Expect

- CI will run automatically on your PR
- A maintainer will review your changes
- You may be asked to make revisions
- Once approved, a maintainer will merge your PR

## Reporting Issues

- Check this repository's existing issues before creating a new one
- Use the bug report or feature request template if the repository provides one under its "New issue" flow

## Security Vulnerabilities

If you discover a security vulnerability, **do not open a public issue**. Instead, please report it privately to the
project maintainers and include details to help reproduce and assess the issue. Responsible disclosure helps us protect
users while we investigate and prepare a fix.

## License

By contributing to this project, you agree that your contributions will be licensed under
the [Apache License 2.0](LICENSE.txt).
