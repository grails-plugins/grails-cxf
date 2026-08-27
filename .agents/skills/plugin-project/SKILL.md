---
name: plugin-project
description: Scope rules for the plugin/ subproject — plugin source code and unit tests only. Use when adding code or tests to the plugin module, or deciding whether something belongs in the plugin or in an example app.
---

# Plugin Project Best Practices

## Purpose

The `plugin/` directory contains the Grails plugin artifact. It is the only publishable library in this repository. It
must contain ONLY the plugin source code and unit tests; nothing else.

## Core Rules

### Plugin project contains ONLY plugin code and unit tests

The plugin project (`plugin/`) must contain:

- Plugin source code under `src/main/groovy/` and `grails-app/`
- Unit tests under `src/test/groovy/`
- Plugin configuration files under `grails-app/conf/`

The plugin project must NOT contain:

- Integration tests (these belong in example apps under `examples/`)
- Functional tests (these belong in example apps under `examples/`)
- Example controllers, views, or domain classes
- Test controllers or test-specific artifacts
- Application-level configuration not related to the plugin (e.g., database config, asset config)

### Why: separation of concerns

Keeping integration/functional tests out of the plugin project ensures:

1. The plugin artifact is clean – no test dependencies or test code leaks into the published JAR
2. Tests that require a running Grails application exercise the plugin as a real consumer would
3. The plugin's API surface is validated from the outside, not the inside
4. Different example apps can test different configurations of the plugin

## Project Structure

```
plugin/
├── build.gradle                          # Only convention plugins + dependencies
├── grails-app/
│   └── conf/
│       └── application.yml               # Plugin-specific config defaults
└── src/
    ├── main/groovy/                      # Core plugin classes
    │   └── grails/plugins/myplugin/
    │       └── MyPluginGrailsPlugin.groovy
    └── test/groovy/                      # Unit tests ONLY
        └── grails/plugins/myplugin/
            └── MyPluginSpec.groovy
```

This template's own placeholder plugin follows the same shape: a single `PluginTemplateGrailsPlugin` class under
`plugin/src/main/groovy/grails/plugins/template/`. Real plugins will add whatever additional classes, packages
(`config/`, `core/`, interceptors, filters, etc.) their functionality needs -- the rule is about what's excluded
(no integration/functional tests, no example controllers or views), not about a fixed package layout.

## build.gradle Pattern

The plugin's `build.gradle` should be minimal -- apply convention plugins and declare dependencies:

```groovy
plugins {
    id 'config.compile'
    id 'config.testing'
    id 'config.grails-plugin'
    id 'config.publish'
}

version = projectVersion
group = projectGroup

dependencies {

    profile 'org.apache.grails.profiles:web-plugin'
    console 'org.apache.grails:grails-console'

    compileOnly platform("org.apache.grails:grails-bom:$grailsVersion")
    compileOnly 'org.apache.grails:grails-dependencies-starter-web'

    testImplementation platform("org.apache.grails:grails-bom:$grailsVersion")
    testImplementation 'org.apache.grails:grails-dependencies-starter-web'
    testImplementation 'org.apache.grails:grails-dependencies-test'
}
```

Key patterns:

- Use `compileOnly` for framework dependencies the consuming application will provide
- Use `testImplementation` for test-only dependencies
- Apply `config.publish` to configure Maven publishing metadata
- NEVER add custom task configuration here – move it to a convention plugin

## Unit Test Guidelines

Unit tests in the plugin project test individual classes in isolation:

- Test domain logic, validation, and data structures the plugin provides
- Test utility/helper classes in isolation
- Use Spock Framework with `@Unroll` for data-driven tests
- Do NOT start the Grails application context for unit tests
- Do NOT make HTTP requests in unit tests
- Do NOT test controller actions, interceptors, or filters end-to-end in the plugin project

### What belongs in unit tests

- Validation and error cases on plugin domain/value classes
- Business logic in isolation (no Spring context, no running server)
- Equals/hashCode contracts, serialization round-trips
- Output formatting of pure functions/methods

### What does NOT belong in unit tests

- Testing that a feature is observable end-to-end over HTTP (integration test)
- Testing that filters/interceptors/controllers wire up correctly in a running app (integration test)
- Testing behavior with GSP views, JSON rendering, or static assets (functional test)

## Plugin Descriptor

The `<MyPlugin>GrailsPlugin` class (e.g. `PluginTemplateGrailsPlugin` in this template) extends `grails.plugins.Plugin`
and exposes metadata about the plugin to the Grails framework -- `title`, `description`, `documentation`, `license`,
`organization`, `issueManagement`, `scm`, and the plugin's `grailsVersion` compatibility range.

## Dependency Scoping

- **`compileOnly`**: Framework dependencies the host app provides (Grails web, servlet API)
- **`implementation`**: Dependencies the plugin bundles and needs at runtime (use sparingly)
- **`testImplementation`**: Test framework dependencies (Spock, grails-testing-support)
- **`console`**: Grails console support
- **`profile`**: The Grails profile (web-plugin for plugins)

Avoid `implementation` for Grails/Spring/Servlet dependencies -- the consuming application provides these.
