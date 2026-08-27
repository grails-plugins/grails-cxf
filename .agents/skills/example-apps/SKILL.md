---
name: example-apps
description: Patterns for example applications under examples/ — hosting integration and functional tests, depending on the plugin as a real consumer, and app structure. Use when creating or modifying an example app, or deciding where a test belongs.
---

# Example Application Best Practices

## Purpose

Example applications under `examples/` serve as both integration/functional test harnesses and usage demonstrations for
the plugin. They are real Grails applications that depend on the plugin project, exercising it exactly as an end user
would.

## Core Rules

### All integration and functional tests belong in example apps

The plugin project (`plugin/`) must contain ONLY unit tests. Any test that requires:

- A running Grails application context
- HTTP requests and responses
- Controller/interceptor/filter behavior in a live server
- View rendering (GSP, JSON, text)
- Static asset serving
- Database interaction
- Multi-component interaction

...belongs in an example app under `examples/`.

### Example apps depend on the plugin as a project dependency

Each example app declares the plugin as a regular dependency, just as an external consumer would:

```groovy
dependencies {
    implementation project(':my-plugin')
}
```

This ensures the plugin is tested through its public API and packaging, not its internals.

### Multiple example apps can test different scenarios

The `examples/` directory can contain more than one app. Different apps can test:

- Different Grails configurations
- Different database backends
- Different view technologies
- Edge cases or unusual setups
- Performance scenarios

All apps under `examples/` are auto-discovered by the root `settings.gradle`:

```groovy
file('examples').listFiles({ it.directory } as FileFilter).each {
    include(it.name)
    project(":$it.name").projectDir = file("examples/$it.name")
}
```

Coverage aggregation works differently: `code-coverage/build.gradle` only applies the `config.code-coverage-aggregate`
convention plugin, which pulls in every subproject in `rootProject.subprojects` that applies `config.code-coverage` --
it does not scan `examples/` directly. As of now, `config.example-app.gradle` does not apply `config.code-coverage`,
so newly added example apps are auto-discovered as projects but are NOT automatically part of
`jacocoAggregatedReport` -- that convention plugin would need to be added to `config.example-app.gradle` (or to the
app's own `build.gradle`) for its coverage to be aggregated.

## Project Structure

A typical example app looks like this (this template's own `examples/app1/` currently has no `src/integration-test/`
directory yet -- add one following this layout when the app needs integration tests):

```
examples/app1/
├── build.gradle
├── grails-app/
│   ├── conf/
│   │   ├── application.yml           # App config (enables plugin, DB, etc.)
│   │   └── logback.xml
│   ├── controllers/app1/
│   │   ├── FeatureTestController.groovy   # Controllers that exercise the plugin
│   │   └── UrlMappings.groovy
│   ├── init/app1/
│   │   └── Application.groovy
│   ├── views/
│   │   ├── featureTest/           # GSP views for testing the plugin's view-layer behavior
│   │   ├── layouts/main.gsp
│   │   └── ...
│   └── assets/                        # Static assets for testing asset-related behavior
└── src/
    └── integration-test/groovy/app1/
        └── FeatureIntegrationSpec.groovy   # Integration tests
```

## build.gradle Pattern

Example apps apply convention plugins and declare their own dependencies:

```groovy
plugins {
    id 'config.example-app'
}

version = projectVersion
group = 'app1'

dependencies {
    // The plugin under test
    implementation project(':my-plugin')

    // Standard Grails app dependencies
    implementation platform("org.apache.grails:grails-bom:$grailsVersion")
    implementation 'org.apache.grails:grails-core'
    implementation 'org.apache.grails:grails-web-boot'
    // ... other standard dependencies

    // Integration test dependencies
    integrationTestImplementation testFixtures('org.apache.grails:grails-geb')

    // Unit test dependencies (for any app-level unit tests)
    testImplementation 'org.apache.grails:grails-testing-support-web'
    testImplementation 'org.spockframework:spock-core'
}
```

Key patterns:

- Apply `example-app` convention plugin
- Depend on the plugin via `project(':my-plugin')`
- NEVER apply `config.publish` -- example apps are not published
- NEVER apply `config.grails-plugin` / `config.grails-web-plugin` -- example apps are applications, not plugins

## Integration Test Guidelines

Integration tests run against a live embedded Grails server using the `@Integration` annotation:

```groovy
@Integration
class FeatureIntegrationSpec extends Specification {

    @Shared
    RestTemplate restTemplate = new RestTemplate()

    private String getBaseUrl() {
        "http://localhost:${serverPort}"
    }

    private ResponseEntity<String> doGet(String path) {
        restTemplate.exchange("${baseUrl}${path}", HttpMethod.GET, null, String)
    }

    void "controller exercising the plugin behaves as expected"() {
        when:
        def response = doGet('/featureTest/index')

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains('expected content')
    }
}
```

### What to test in integration tests

- The plugin's behavior is observable through real HTTP requests/responses
- Different response types (GSP views, JSON, plain text) all behave correctly with the plugin applied
- The plugin's public configuration options (e.g., enabling/disabling a feature) take effect in a running app
- Plugin behavior under different controller patterns relevant to the plugin's feature set
- Interaction between the plugin and other components (filters, interceptors, views) in a live server

### Integration test patterns

1. **Use `RestTemplate` or similar HTTP client** -- test real HTTP round-trips
2. **Verify externally observable behavior, not internals** -- assert on responses/headers, not internal class state
3. **Use realistic thresholds, not brittle exact values** -- when asserting timing- or size-based behavior, prefer
   ranges over exact numbers
4. **Test edge cases** -- static assets, JSON responses, redirects, errors
5. **Extract helper methods** -- centralize repeated response parsing logic

### Test organization

- Place integration tests under `src/integration-test/groovy/<package>/`
- Name test classes with `*IntegrationSpec` or `*FunctionalSpec` suffix
- Group related tests in a single spec class when they share setup
- Use `@Shared` for expensive objects like `RestTemplate`

## Test Controllers and Views

Example apps should include purpose-built controllers and views that exercise the plugin's features -- what these
look like depends entirely on what the plugin does. For example, a controller/view combination for each behavior
the plugin adds or modifies, plus variants covering the different response types (GSP views, JSON, plain text) the
plugin needs to support.

These are test fixtures that live in the example app, NOT in the plugin project.

## Running Tests

```bash
# Run integration tests for app1
./gradlew :app1:integrationTest

# Run all tests (unit + integration) across all projects
./gradlew build

# Run the example app interactively
./gradlew :app1:bootRun
```
