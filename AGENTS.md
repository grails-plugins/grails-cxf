# AGENTS.md - grails-cxf

## Project Overview

This is the **Grails CXF Plugin** — a Grails plugin that brings easy exposure of service and endpoint classes as
Apache CXF SOAP Services to Grails.

- **Language:** Groovy 4.0.30 on Java 17
- **Framework:** Grails 7.x
- **Build System:** Gradle 8.14.4 (with wrapper)
- **Current Version:** 5.0.0-RC1
- **License:** Apache 2.0

## Skill Files (Best Practices)

Detailed best practices are documented as skills in `.agents/skills/` (`.claude` is a symlink to `.agents`):

| Skill                                                                                   | Purpose                                                    |
|------------------------------------------------------------------------------------------|------------------------------------------------------------|
| [`repository-structure`](.agents/skills/repository-structure/SKILL.md)                    | Canonical directory layout and architectural rules         |
| [`gradle-best-practices`](.agents/skills/gradle-best-practices/SKILL.md)                  | Gradle best practices, convention plugins, and idioms      |
| [`plugin-project`](.agents/skills/plugin-project/SKILL.md)                                | Plugin project scope: source code + unit tests only        |
| [`example-apps`](.agents/skills/example-apps/SKILL.md)                                    | Example app patterns: integration & functional tests       |
| [`enhance-plugin-with-template`](.agents/skills/enhance-plugin-with-template/SKILL.md)    | Migrate an existing plugin onto this template structure    |

**Read these skill files before making structural changes to the repository.**

## Critical Rules

1. **NEVER add code to the root `build.gradle` to configure subprojects.** No `subprojects {}`, `allprojects {}`, or
   `configure()` blocks. All shared configuration goes through convention plugins in `build-logic/`.
2. **The plugin project contains ONLY plugin code and unit tests.** No integration tests, no functional tests, no
   example controllers or views.
3. **Example apps under `examples/` host all integration and functional tests.** They depend on the plugin via
   `implementation project(':cxf')` and test it as a real consumer would.
4. **Use Gradle convention plugins to deduplicate.** If two or more subprojects share build logic, extract it into a
   convention plugin in `build-logic/`.
5. **Always use lazy Gradle APIs** to avoid eager initialization (`tasks.register()`, `tasks.named()`, `configureEach`,
   `provider {}`).

## Repository Structure

```
grails-cxf/
├── .agents/skills/      # Agent skill files (.claude is a symlink to .agents)
├── plugin/              # Core Grails plugin (artifact: cxf)
│   ├── grails-app/      #   Plugin conf, i18n, and BootStrap
│   └── src/main/        #   Plugin source code (CxfGrailsPlugin, endpoint registration utils)
├── examples/app1/        # Example Grails app exercising a SOAP endpoint
│   ├── grails-app/       #   A DemoService annotated with @GrailsCxfEndpoint
│   └── src/integration-test/  # Integration test hitting the generated WSDL
├── docs/                # Asciidoctor documentation
├── build-logic/         # Gradle convention plugins (composite build)
├── .github/workflows/   # CI, release, and release-notes workflows
├── build.gradle         # Root build file (docs + root-publish ONLY)
├── settings.gradle      # Multi-project settings
└── gradle.properties    # Version properties
```

## Build and Test Commands

```bash
# Full build (compile + test)
./gradlew build

# Run only unit tests (plugin module)
./gradlew :cxf:test

# Run integration tests (example app)
./gradlew :app1:integrationTest

# Skip tests
./gradlew build -PskipTests

# Run the example app
./gradlew :app1:bootRun

# Generate documentation
./gradlew docs

# Clean build
./gradlew clean build

# Run code style checks only
./gradlew codeStyle

# Skip code style checks
./gradlew build -PskipCodeStyle
```

## SDK Requirements

Use SDKMAN to install the correct tool versions (see `.sdkmanrc`):

- Java: `17.0.18-librca`
- Gradle: `8.14.4`
- Groovy: `4.0.30`

Run `sdk env install` to set up the environment.

## Architecture

The plugin wires Grails service/endpoint classes up as Apache CXF SOAP endpoints:

1. **`CxfGrailsPlugin`** (`plugin/src/main/groovy/grails/cxf/`) registers the `CXFServlet` at a configurable servlet
   mapping (default `/services/*`) and wires a Spring `Bus`.
2. **`EndpointRegistrationUtil`** (`plugin/src/main/groovy/org/grails/cxf/utils/`) scans the application context for
   beans annotated with `@GrailsCxfEndpoint`, and publishes each as a CXF `EndpointImpl`, applying the address,
   service/port names, SOAP 1.2 binding, WSDL location, properties, and interceptors configured on the annotation.
3. **`GrailsCxfEndpoint`** (`plugin/src/main/groovy/org/grails/cxf/utils/`) is the annotation used to mark and
   configure a service or endpoint class for SOAP exposure.

### Core Classes

| Class / Interface          | Location                                                | Purpose                                    |
|-----------------------------|----------------------------------------------------------|---------------------------------------------|
| `CxfGrailsPlugin`           | `plugin/src/main/groovy/grails/cxf/`                      | Plugin descriptor; wires servlet and bus    |
| `EndpointRegistrationUtil`  | `plugin/src/main/groovy/org/grails/cxf/utils/`             | Publishes annotated beans as CXF endpoints  |
| `GrailsCxfEndpoint`         | `plugin/src/main/groovy/org/grails/cxf/utils/`             | Annotation for exposing SOAP endpoints      |
| `EndpointType`              | `plugin/src/main/groovy/org/grails/cxf/utils/`             | Enum of supported endpoint exposure types   |

## Configuration

The CXF servlet mapping can be overridden via the `cxf.servlet.mapping` config key (must end in `/*`). See
[`docs/src/docs/usage/servletMapping.adoc`](docs/src/docs/usage/servletMapping.adoc).

## Testing

### Unit Tests (`plugin/src/test/`)

Unit tests use the **Spock Framework** and run on JUnit Platform. They mock the Spring `ApplicationContext` and
verify endpoint wiring logic in `EndpointRegistrationUtil` without booting a Grails application.

### Integration / Functional Tests (`examples/app1/`)

`examples/app1` hosts a `DemoService` annotated with `@GrailsCxfEndpoint` for exercising the plugin as a real
consumer would. Integration tests boot the application and verify the SOAP endpoint (e.g. by fetching its WSDL).

## Build-Logic Convention Plugins

Convention plugins in `build-logic/src/main/groovy/` standardize build configuration:

| Plugin                 | Purpose                                                                              |
|------------------------|--------------------------------------------------------------------------------------|
| `app-run.gradle`       | Debug flags for `bootRun`                                                            |
| `compile.gradle`       | Java/Groovy compilation settings (UTF-8, incremental, Java release from `.sdkmanrc`) |
| `docs.gradle`          | Documentation aggregation (Groovydoc + Asciidoctor)                                  |
| `example-app.gradle`   | Example app config (grails-web, GSP, assets)                                         |
| `grails-assets.gradle` | Asset pipeline with Bootstrap/jQuery WebJars                                         |
| `grails-plugin.gradle` | Grails plugin application                                                            |
| `publish.gradle`       | Per-project Maven publishing metadata                                                |
| `publish-root.gradle`  | Root-level Nexus publishing workaround                                               |
| `testing.gradle`       | Test framework config (Spock, JUnit Platform, test-logger)                           |

## CI/CD

- **CI** (`.github/workflows/ci.yml`): Builds and tests on push/PR; publishes snapshots to Maven Central Snapshots on
  push to release branches.
- **Release** (`.github/workflows/release.yml`): multi-stage pipeline triggered by GitHub release — stage artifacts,
  release to Maven Central, publish docs to GitHub Pages, bump version.
- **Release Notes** (`.github/workflows/release-notes.yml`): Auto-drafts release notes using release-drafter with
  category labels.

## Code Conventions

- Groovy source files use standard Grails conventions (services and taglibs in `grails-app/`, other classes in
  `src/main/groovy/`).
- **Use `def` for local variables** where the type is inferred from the right-hand side (e.g., constructor calls,
  method calls, casts, factory methods). Explicit types should only be used for local variables when the type cannot
  be inferred or when needed for `@CompileStatic` compilation. This applies to both production code and tests.
- When writing Gradle, always use the latest best practices to avoid eager initialization.
- CXF/JAX-WS annotations use the `jakarta.*` namespace (Jakarta EE 9+), matching Grails 7's Spring Boot 3 baseline —
  not `javax.*`.
