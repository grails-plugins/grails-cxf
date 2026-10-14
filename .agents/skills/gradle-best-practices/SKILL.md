---
name: gradle-best-practices
description: Gradle conventions for template-based Grails plugin repositories — convention plugins in conventions/, lazy configuration APIs, extension configuration, and build structure rules. Use when writing or modifying any build.gradle file or convention plugin.
---

# Gradle Best Practices

## Purpose

This skill covers Gradle best practices for this project, including convention plugins, extension configuration,
lazy APIs, and build structure. Convention plugins remove duplication across subprojects by centralizing shared
build logic. They live in the `conventions/` composite build and are applied by ID in each subproject's `build.gradle`.

## Core Rules

### NEVER configure subprojects from the root build.gradle

The root `build.gradle` must NEVER use `subprojects {}`, `allprojects {}`, or `configure(subprojects.matching {...}) {}`
to apply plugins or configure subproject behavior. This is an antipattern that causes ordering issues, breaks project
isolation, and makes builds harder to reason about.

```groovy
// BAD - Never do this in root build.gradle
subprojects {
    apply plugin: 'groovy'
    dependencies {
        implementation 'org.example:shared-lib:1.0'
    }
}

// BAD - Never do this either
allprojects {
    repositories {
        mavenCentral()
    }
}
```

Instead, create a convention plugin in `conventions/` and apply it in each subproject that needs it:

```groovy
// GOOD - conventions/src/main/groovy/config.compile.gradle
plugins {
    id 'groovy'
}
// shared compilation config here
```

```groovy
// GOOD - plugin/build.gradle
plugins {
    id 'config.compile'
}
```

The ONLY exception is the `config.publish-root` convention plugin, which exists solely as a workaround for a Nexus
publishing bug (https://github.com/gradle-nexus/publish-plugin/issues/310) that requires version/group to be set at the
root level.

### Use the composite build pattern

Convention plugins reside in `conventions/`, which is included as a composite build via `settings.gradle`:

```groovy
pluginManagement {
    includeBuild('./conventions') {
        it.name = 'conventions'
    }
}
```

### Naming convention

Convention plugin files follow the pattern:

```
conventions/src/main/groovy/config.<purpose>.gradle
```

The plugin ID matches the filename (minus the `.gradle` extension). For example:

- `config.compile.gradle` -> plugin ID `config.compile`

### Declare external plugin dependencies in conventions/build.gradle

When a convention plugin applies a third-party plugin, that plugin must be declared as a dependency in
`conventions/build.gradle`. Plugins whose version does not depend on the Grails line are `implementation`
dependencies. The Grails, asset-pipeline and grails-publish Gradle plugins follow the Grails line (7.x for Grails 7 on
Gradle 8, 8.x for Grails 8 on Gradle 9), so they are only `compileOnly`: a runtime dependency would land in the
published POM and force the conventions' `grailsVersion` on every consumer.

```groovy
// conventions/build.gradle
plugins {
    id 'groovy-gradle-plugin'
}

dependencies {
    implementation "com.adarshr:gradle-test-logger-plugin:${testLoggerVersion}"

    compileOnly platform("org.apache.grails:grails-bom:${grailsVersion}")
    compileOnly 'org.apache.grails:grails-gradle-plugins'
    compileOnly 'cloud.wondrify:asset-pipeline-gradle'
    compileOnly 'org.apache.grails.gradle:grails-publish'
}
```

The consuming build provides them at its own `grailsVersion`, on the settings classpath that every project inherits.
This block in `settings.gradle` sits between `grails-plugin-conventions` marker comments and is kept up to date by
files-sync:

```groovy
buildscript {
    repositories {
        maven { url = 'https://repo.grails.org/grails/restricted' }
    }
    dependencies {
        classpath platform("org.apache.grails:grails-bom:${grailsVersion}")
        classpath 'org.apache.grails:grails-gradle-plugins'
        classpath 'cloud.wondrify:asset-pipeline-gradle'
        classpath 'org.apache.grails.gradle:grails-publish'
    }
}
```

`GrailsLineSpec` in `conventions/src/test` builds a consumer on each supported Grails line (the Grails 8 one runs only
on Java 21+).

### Versions inside conventions

`conventions/` is its own build, so it does not see the main build's `gradle.properties`. It has a `gradle.properties`
of its own that pins the versions of the third-party Gradle plugins it builds on (`grailsVersion`,
`asciidoctorVersion`, `testLoggerVersion`) — this is what lets it be published and consumed on its own later.
`grailsVersion` there is only compiled against; at runtime the consumer's `grailsVersion` decides (see above).
Tool versions used by the convention plugins (`checkstyleVersion`, `codenarcVersion`, `jacocoVersion`) have defaults
inside the plugins; a consuming repo overrides them with a property of the same name in its root `gradle.properties`.

The checkstyle/codenarc rule sets are bundled in `conventions/src/main/resources/code-style/` and extracted into each
project's `build/code-style-config/` by `extractCodeStyleConfig`. `config.docs` derives the plugin/docs project paths
from the root project name; override them with `pluginDocs { pluginProject = ':x'; docsProject = ':y' }`.

`conventions/` has TestKit tests (`./gradlew -p conventions test`). Run them with Gradle's embedded Groovy 3, which is
why the build pins the `groovy-3.0` Spock variant.

## Avoid Eager Initialization

Always use lazy/deferred APIs to avoid eagerly resolving tasks or configurations:

```groovy
// GOOD - lazy task configuration
tasks.withType(JavaCompile).configureEach {
    options.encoding = StandardCharsets.UTF_8.name()
}

tasks.named('bootRun', JavaExec) {
    doFirst { /* ... */ }
}

tasks.register('docs') {
    dependsOn(/* ... */)
}

// BAD - eager resolution
tasks.withType(JavaCompile) { // missing .configureEach
    options.encoding = 'UTF-8'
}

task docs { // old task() API is eager
    dependsOn /* ... */
}
```

Key APIs to use:

- `tasks.register()` instead of `task()`
- `tasks.named()` instead of `tasks.getByName()`
- `tasks.withType(X).configureEach {}` instead of `tasks.withType(X) {}`
- `project.provider {}` for lazy values
- `layout.buildDirectory` instead of `buildDir`
- `dependsOn()` method instead of `dependsOn =` setter (setter replaces all dependencies; the method adds to them)
- Do NOT chain `.configure {}` on `tasks.register()` or `tasks.named()` — pass the closure directly to preserve type hints

## Extension Configuration with Type Hints

When configuring project extensions (like publishing metadata or third-party plugin configurations), use
`extensions.configure(Type)` with explicit `it` for type hints and better IDE support:

```groovy
// GOOD - explicit it in extensions.configure() for type hints
extensions.configure(GrailsPublishExtension) {
    it.artifactId = project.name
    it.githubSlug = 'my-org/my-plugin'
    it.license.name = 'Apache-2.0'
    it.title = 'My Plugin'
    it.developers = [name: 'Developer Name']
}
```

Explicit `it` is NOT required in `tasks.named()`, `tasks.register()`, or `configureEach` — these already have typed
delegates:

```groovy
// GOOD - no explicit it needed, delegate is already typed
tasks.withType(Checkstyle).configureEach {
    group = 'verification'
    onlyIf { !project.hasProperty('skipCodeStyle') }
}

tasks.named('bootRun', JavaExec) {
    doFirst {
        jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005")
    }
}
```

**Benefits of `extensions.configure(Type)` with explicit `it`:**

- IDE auto-completion and type-checking for extension properties
- Clearer intent: code readers immediately see the extension type being configured
- Reduces runtime errors from typos in property names

## Composition Over Inheritance

Convention plugins should compose by applying other convention plugins rather than duplicating logic:

```groovy
// example.gradle applies other convention plugins
plugins {
    id 'org.apache.grails.gradle.grails-web'
    id 'org.apache.grails.gradle.grails-gsp'
    id 'config.grails-assets'
    id 'config.app-run'
}
```

## Existing Convention Plugins

| Plugin                           | Purpose                                                                              |
|----------------------------------|--------------------------------------------------------------------------------------|
| `app-run.gradle`                 | Debug flags for `bootRun`                                                            |
| `code-coverage.gradle`           | JaCoCo coverage for project (XML + HTML reports)                                     |
| `code-coverage-aggregate.gradle` | JaCoCo coverage aggregation across subprojects (XML + HTML reports)                  |
| `code-style.gradle`              | Checkstyle + CodeNarc code style checking (configs in `conventions/config/`)         |
| `compile.gradle`                 | Java/Groovy compilation settings (UTF-8, incremental, Java release from `.sdkmanrc`) |
| `docs.gradle`                    | Documentation aggregation (Groovydoc + Asciidoctor)                                  |
| `example-app.gradle`             | Example app config (app-run, compile, grails-assets, testing, grails-web, GSP)       |
| `grails-assets.gradle`           | Asset pipeline with Bootstrap/jQuery WebJars                                         |
| `grails-plugin.gradle`           | Applies the Grails plugin Gradle plugin, disables Spring dependency management       |
| `grails-web-plugin.gradle`       | `grails-plugin` + asset-pipeline packaging, for plugins that ship web assets         |
| `project-metadata.gradle`        | Parses `project.yml` into extra properties (`projectTitle`, `githubOrg`, etc.)       |
| `publish.gradle`                 | Per-project Maven publishing metadata (applies `project-metadata` itself)            |
| `publish-root.gradle`            | Root-level Nexus publishing workaround                                               |
| `testing.gradle`                 | Test framework config (Spock, JUnit Platform, test-logger)                           |

## When to Create a New Convention Plugin

Create a new convention plugin when:

- Two or more subprojects share the same build configuration
- A subproject's `build.gradle` grows beyond applying plugins and declaring dependencies
- You need to enforce a project-wide standard (e.g., code formatting, static analysis)

Keep each convention plugin focused on a single concern. Prefer small, composable plugins over monolithic ones.

## Repository Management

Repositories are managed centrally in `settings.gradle` via `dependencyResolutionManagement`:

```groovy
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        maven { url = 'https://repo.grails.org/grails/restricted' }
    }
}
```

This prevents subprojects from declaring their own repositories, ensuring consistency. The `FAIL_ON_PROJECT_REPOS` mode
enforces this.
