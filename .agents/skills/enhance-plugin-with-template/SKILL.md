---
name: enhance-plugin-with-template
description: Migrate an existing Grails plugin repository onto the grails-plugin-template build structure (plugin/, examples/, docs/, build-logic/, GitHub Actions CI/CD, Maven Central publishing). Use when asked to migrate, modernise, or restructure a Grails plugin to the template layout.
---

# Enhance a Grails Plugin with the Template Structure

Migrate an existing Grails plugin repository onto the `grails-plugin-template` structure while
keeping all plugin source code intact. This playbook is distilled from the real migration of
`gpc/grails-jasypt` (branch `chore/prepare-release`) — including the mistakes found and fixed
after the first attempt, listed under [Pitfalls](#pitfalls).

## Prerequisites

- A checkout of the target plugin repository. Work on a dedicated branch (e.g. `chore/migrate-to-template`).
- A checkout of `grails-plugin-template` (this repo) to copy files from. If not available locally,
  fetch files from `https://github.com/grails-plugins/grails-plugin-template`.
- The plugin must target Grails 7.x. If it doesn't, upgrade it first — that is a separate task;
  do not mix a Grails upgrade into the migration commit.

## Phase 0 — Survey the plugin

Before changing anything, record:

1. Plugin artifact ID, group, and current version (check `build.gradle`, `gradle.properties`, old publish config).
2. Where the plugin source lives (may be repo root with `grails-app/` + `src/`, or already a subproject).
3. Where integration-test / sample apps live (e.g. `src/test/projects/<name>` — these become `examples/<name>`).
4. Existing docs (often a long `README.md` — its content moves to `docs/src/docs/*.adoc`).
5. Legacy build machinery to delete: `.travis.yml`, bintray/nexus publish scripts, clover configs,
   embedded wrappers inside test projects (`gradlew*`, `grailsw*`, `grails-wrapper.jar`,
   nested `settings.gradle` / `gradle.properties` / `gradle/` directories).
6. Whether a `gh-pages` branch exists (needed for docs publishing and the version index).
7. Contributors (GitHub logins + display names) and released versions (git tags) — needed for `project.yml`.

## Phase 1 — Copy infrastructure files from the template

Copy these **verbatim**; never hand-edit them in the plugin repo afterwards (they are overwritten
by the automated template sync):

```
.github/workflows/          # EXCLUDE files-sync.yml (template-only)
.github/scripts/            # EXCLUDE prepare-sync-matrix.groovy (template-only)
.github/dependabot.yml
.github/renovate.json
.github/release-drafter.yml
.github/dependency-graph/external-references.yml   # then set your purl, see Phase 7
.agents/                    # agent skills; then symlink: ln -s .agents .claude
build-logic/
gradle/                     # wrapper jar + properties
gradlew                     # keep executable bit (chmod +x)
gradlew.bat
.editorconfig
.sdkmanrc
CONTRIBUTING.md
LICENSE.txt
```

Also copy `AGENTS.md`, `docs/build.gradle`, `docs/src/docs/index.tmpl`, and `code-coverage/build.gradle`
as starting points — `AGENTS.md` gets customised in Phase 7; `index.tmpl` must stay untouched.

## Phase 2 — Restructure the source tree

Target layout:

```
<repo>/
├── plugin/              # plugin code + unit tests ONLY
│   ├── grails-app/
│   └── src/
├── examples/<name>/     # example app(s): integration & functional tests
├── docs/                # Asciidoctor documentation module
├── build-logic/         # copied — do not edit
├── gradle/              # copied — do not edit
├── code-coverage/       # aggregation module (build.gradle only)
├── build.gradle
├── settings.gradle
├── gradle.properties
└── project.yml
```

- `git mv` plugin sources (`grails-app/`, `src/`) into `plugin/`.
- `git mv` each sample/test app to `examples/<name>/` and **delete its embedded build machinery**:
  nested `gradlew*`, `grailsw*`, `*-wrapper.jar`, `settings.gradle`, `gradle.properties`, `gradle/` dir.
  Example apps are subprojects of the root build, not standalone builds.
- Delete legacy CI/publishing files (`.travis.yml`, bintray scripts, etc.).

## Phase 3 — Root build files

**`settings.gradle`** — copy from the template, then change only the three names:

```groovy
rootProject.name = 'grails-my-plugin-root'

include('plugin')
project(':plugin').name = 'grails-my-plugin'        // = published artifact ID
include('docs')
project(':docs').name = 'grails-my-plugin-docs'
include('code-coverage')
```

Keep the rest (pluginManagement, `includeBuild('./build-logic')`, buildCache, examples
auto-discovery, dependencyResolutionManagement) exactly as in the template.

**`gradle.properties`** — copy from the template and set:

```properties
projectVersion=X.Y.Z-SNAPSHOT       # next version of THIS plugin
projectGroup=io.github.gpc          # your Maven group — read by config.publish-root,
                                     # plugin/build.gradle and docs/build.gradle
grailsVersion=7.0.12                # or current 7.x release
projectsToPublish=grails-my-plugin  # MUST match plugin project name in settings.gradle
# keep tool versions (checkstyle/codenarc/jacoco/asciidoctor/testLogger),
# build-scan settings, and org.gradle.* properties from the template
```

The Java version comes from `.sdkmanrc` (via `config.compile`), not from `gradle.properties` — just
copy `.sdkmanrc` from the template and set the versions there.

**Root `build.gradle`** — minimal; composition only:

```groovy
plugins {
    id 'idea'
    id 'config.docs'
    id 'config.publish-root'
}
```

**`code-coverage/build.gradle`**:

```groovy
plugins {
    id 'config.code-coverage-aggregate'
}
```

## Phase 4 — `project.yml`

Create it at the repo root. It is the single source of metadata — it drives Maven POM metadata
(via `config.publish` + `config.project-metadata`), docs, release notes, and the version index:

```yaml
---
project:
  name: "grails-my-plugin"                 # artifact ID
  title: "Grails My Plugin"
  description: "One-paragraph description used in the POM and docs index."
  org: "Grails Plugins Collective"         # or "Grails Plugins"
github:
  org: "gpc"                               # gpc or grails-plugins
  project: "my-plugin-repo-name"
license:
  name: "Apache-2.0"
contributors:                              # GitHub login: display name
  someLogin: "Some Name"
versions:
  current: "X.Y.Z"                         # latest released version
  previous:                                # released versions, newest first
    - "X.Y.Z"
  ignore:                                  # gh-pages dirs to exclude from the index
    - "snapshot"
```

`contributors` and `versions` are auto-maintained by workflows after the initial values are set.

## Phase 5 — `plugin/build.gradle`

Apply convention plugins; do **not** add a manual `GrailsPublishExtension` block — publishing
metadata comes from `project.yml` through `config.publish`:

```groovy
plugins {
    id 'config.code-coverage'
    id 'config.code-style'
    id 'config.compile'
    id 'config.grails-plugin'      // or 'config.grails-web-plugin' — see below
    id 'config.project-metadata'
    id 'config.publish'
    id 'config.testing'
}

version = projectVersion
group = projectGroup           // from gradle.properties, not hardcoded here

dependencies {
    profile 'org.apache.grails.profiles:web-plugin'
    console 'org.apache.grails:grails-console'

    compileOnly platform("org.apache.grails:grails-bom:$grailsVersion")
    compileOnly 'org.apache.grails:grails-dependencies-starter-web'

    // ── plugin-specific dependencies go here ──

    testImplementation platform("org.apache.grails:grails-bom:$grailsVersion")
    testImplementation 'org.apache.grails:grails-dependencies-starter-web'
    testImplementation 'org.apache.grails:grails-dependencies-test'
}
```

Use `config.grails-plugin` if the plugin ships no web assets of its own. Use
`config.grails-web-plugin` instead (composes `config.grails-plugin` with asset-pipeline packaging,
`packagePlugin = true`) if it ships CSS/JS/images under `grails-app/assets/` — this template's own
`plugin/build.gradle` uses `config.grails-web-plugin` for exactly that reason.

Framework/GORM dependencies the consuming app provides should be `compileOnly`; libraries the
plugin bundles stay `implementation`.

Remember to update the Grails version in the plugin descriptor (`grailsVersion = "the version being upgraded to"`) 

## Phase 6 — Example app and docs

**`examples/<name>/build.gradle`** — apply `config.example-app`, depend on the plugin by project
path, then a standard Grails 7 app dependency block:

```groovy
plugins {
    id 'config.example-app'
}

dependencies {
    implementation project(':grails-my-plugin')
    // standard grails-app dependencies: grails-bom platform, spring-boot starters,
    // grails-web-boot, hibernate, h2, geb test fixtures, etc.
}
```

**Docs module** — copy `docs/build.gradle` from the template unmodified; it derives the plugin's
Maven group from `projectGroup` and its project path from its own name (`<x>-docs` → `:x`), so
nothing in it needs editing. Write the content under `docs/src/docs/`:

- `index.adoc` — title + author, then `include::` the section files
- `introduction.adoc` + `introduction/` (license, source code, acknowledgements)
- `usage.adoc` + `usage/` (installation, configuration, plugin-specific chapters)
- `releaseNotes.adoc`

Move the long-form content out of the old `README.md` into these files. Verify with `./gradlew docs`.

## Phase 7 — Plugin-specific customisation sweep

After copying, template files still reference the template itself. Search and fix:

```
grep -rn "grails-plugin-template\|PluginTemplateGrailsPlugin\|app1" \
  --include="*.md" --include="*.yml" --include="*.gradle" --include="*.groovy" .
```

- `AGENTS.md` — project overview, artifact names, example-app paths, current version. **Safe to
  customise** (not synced).
- `README.md` — rewrite short (see Phase 8). **Safe to customise** (not synced).
- `.github/dependency-graph/external-references.yml` — set `purl: pkg:maven/<group>/<artifactId>`.
- `.agents/skills/` and `CONTRIBUTING.md` — ⚠️ these ARE overwritten by the template sync; keep edits
  minimal and expect them to be reverted by sync PRs. Prefer putting plugin-specific guidance in
  `AGENTS.md` instead.

## Phase 8 — Rewrite `README.md`

The README shrinks to: badges (Maven Central, License, CI), a one-paragraph description, an
installation snippet, and a link to the published docs site
(`https://<org>.github.io/<repo>/`). Everything else lives in `docs/`.

## Phase 9 — Register for template sync

In the **grails-plugin-template repo** (not the plugin repo), add the plugin to
`.github/projects.yml`:

```yaml
projects:
  gpc:
    - my-plugin        # → github.com/gpc/my-plugin
```

## Phase 10 — Verify

1. `sdk env install` — tool versions from `.sdkmanrc`.
2. `./gradlew build` — plugin unit tests + example-app integration tests.
3. `./gradlew docs` — Asciidoctor output under `build/docs`.
4. Re-run the Phase 7 grep — zero template references left outside `build-logic/`, `.agents/`, `gradle/`.
5. Ensure a `gh-pages` branch exists (create an orphan one if not) — docs publishing and the
   version index push to it.
6. Open a PR and **watch the full CI run**, including the `publish` and `update-index` jobs — most
   migration mistakes only surface there (see Pitfalls).
7. If the repo pre-dates its move into `gpc`/`grails-plugins`, check its repository-level Actions
   secrets and remove any that duplicate an org secret (signing, Nexus/Sonatype credentials) before
   the first release — see Pitfall 7.

## Pitfalls

Every one of these happened during the grails-jasypt migration:

1. **Stray template references** — `AGENTS.md`, `CONTRIBUTING.md`, `.agents/skills/`, and
   `external-references.yml` still described the template after the migration commit. The Phase 7
   grep is not optional.
2. **CI only fails at the end** — the `update-index` job runs after `publish`, so a broken
   config surfaces only on the first real PR run. Watch the whole pipeline, not just build+test.
3. **Old README duplication** — don't keep usage docs in both README and `docs/`; they will drift.
   Move, don't copy.
4. **Example apps with embedded wrappers** — leftover `gradlew`/`grailsw`/`settings.gradle` inside
   `examples/<name>/` breaks the multi-project build in confusing ways. Delete them all.
5. **`projectsToPublish` mismatch** — it must exactly equal the plugin project name set in
   `settings.gradle`, or nothing gets published without an error you'd notice.
6. **`project.yml` versions block** — seed `versions.current`/`previous` from real git tags;
   `ignore` should list non-release gh-pages directories (`snapshot`, stale `x.y.x` folders).
7. **Stale repository-level secrets shadow org secrets** — a plugin repo that pre-dates its move
   into `gpc`/`grails-plugins` may still have its own repository secrets (`SIGNING_KEY`,
   `SIGNING_PASSPHRASE`, `SECRING_FILE`, `NEXUS_PUBLISH_USERNAME`/`PASSWORD`, etc.) from its old,
   independent publishing setup. Repository-level secrets always take precedence over
   organization-level secrets of the same name, so the release workflow silently signs/publishes
   with the wrong (often stale or revoked) credentials instead of the org's shared ones — surfacing
   as a confusing signing or auth failure on the first real release. Check
   **Settings → Secrets and variables → Actions** on the plugin repo itself and delete any
   publishing-related secrets that duplicate an org secret before cutting the first release.
8. **Duplicate Sonatype staging repos after a re-run** — `release.yml` identifies the staging
   repository to close/release by a description built from `github.run_id`. Re-running failed jobs
   on the same workflow run keeps `run_id` unchanged, so if an earlier attempt got far enough to
   create a staging repo before failing, the retry creates a *second* repo with an identical
   description. `findSonatypeStagingRepository` then errors with "Too many repositories found" and
   refuses to pick one. Fixed by appending `github.run_attempt` (which does increment on a re-run)
   to `NEXUS_PUBLISH_DESCRIPTION` in both the `publish` and `release` jobs, so each attempt gets a
   distinguishable description. If you hit this before the fix is in place, note that Sonatype's
   Central Portal "Deployments" UI and the legacy `ossrh-staging-api.central.sonatype.com` staging
   API are different backends — dropping a deployment in the portal UI does not remove the
   corresponding entry from the legacy staging API that `gradle-nexus-publish-plugin` reads. You
   must drop stale repos via that legacy API directly (`POST .../service/local/staging/bulk/drop`
   with a `stagedRepositoryIds` array), and that API also seems to require an explicit
   `Content-Type: application/json` header on every request, including GETs, or it responds with a
   generic 400 "No content-type header provided".
