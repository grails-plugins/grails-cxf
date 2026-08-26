#!/usr/bin/env groovy
// Self-check for repositories built on grails-plugin-template.
//
// Run it locally with:
//   groovy .github/scripts/verify-repository.groovy
//
// It works both in the template repo itself and in any repo that received a
// files-sync.yml sync from it — template-only checks are gated on the
// presence of .github/projects.yml, which only the template repo has.
//
// Exit code is non-zero if any error was found. Warnings are printed but do
// not fail the run unless VERIFY_STRICT=1 is set, in which case they are
// escalated to errors too.
@Grab('org.apache.groovy:groovy-yaml')
import groovy.yaml.YamlSlurper

def errors = []
def warnings = []

def err = { String msg -> errors << msg }
def warn = { String msg -> warnings << msg }

def isTemplate = new File('.github/projects.yml').exists()
println "Mode: ${isTemplate ? 'template' : 'child repo'}"

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

def readText = { String path ->
    def f = new File(path)
    f.exists() ? f.text : null
}

def loadYaml = { String path ->
    def f = new File(path)
    if (!f.exists()) return null
    try {
        return new YamlSlurper().parse(f)
    } catch (Exception e) {
        err("${path} is not valid YAML: ${e.message}")
        return null
    }
}

// ---------------------------------------------------------------------------
// 1. Required files
// ---------------------------------------------------------------------------

def requiredFiles = [
    'project.yml',
    'settings.gradle',
    'gradle.properties',
    '.sdkmanrc',
    'gradle/wrapper/gradle-wrapper.properties',
    'build-logic/settings.gradle',
    'docs/build.gradle',
    'docs/src/docs/index.tmpl',
    '.github/dependency-graph/external-references.yml',
]
requiredFiles.each { path ->
    if (!new File(path).exists()) {
        err("Missing required file: ${path}")
    }
}

def settingsGradleText = readText('settings.gradle') ?: ''
def gradlePropertiesFile = new File('gradle.properties')
def gradleProperties = new Properties()
if (gradlePropertiesFile.exists()) {
    gradlePropertiesFile.withInputStream { gradleProperties.load(it) }
}
def projectYml = (new File('project.yml').exists() ? loadYaml('project.yml') : null) as Map

// ---------------------------------------------------------------------------
// 2/3. project.yml required keys, and project.name / settings.gradle /
// projectsToPublish consistency (the "nothing publishes, silently" pitfall)
// ---------------------------------------------------------------------------

if (projectYml != null) {
    def requiredKeys = [
        ['project', 'name'], ['project', 'title'], ['project', 'description'], ['project', 'org'],
        ['github', 'org'], ['github', 'project'],
        ['license', 'name'],
        ['versions', 'current'],
    ]
    requiredKeys.each { path ->
        def value = path.inject(projectYml) { acc, key -> (acc instanceof Map) ? acc[key] : null }
        if (value == null || value == '') {
            err("project.yml is missing ${path.join('.')}")
        }
    }

    def pluginNameMatch = settingsGradleText =~ /project\(['"]:plugin['"]\)\.name\s*=\s*['"]([^'"]+)['"]/
    def pluginName = pluginNameMatch.find() ? pluginNameMatch.group(1) : null
    def projectName = projectYml.project?.name as String

    if (!pluginName) {
        warn("settings.gradle has no project(':plugin').name = '...' assignment — cannot cross-check project.yml's project.name against it")
    } else if (projectName && projectName != pluginName) {
        err("project.yml project.name ('${projectName}') does not match settings.gradle's project(':plugin').name ('${pluginName}')")
    }

    def projectsToPublish = (gradleProperties.getProperty('projectsToPublish') ?: '')
        .tokenize(',').collect { it.trim() }.findAll()
    if (pluginName && !projectsToPublish.contains(pluginName)) {
        err("gradle.properties projectsToPublish (${projectsToPublish}) does not include the plugin project name " +
            "('${pluginName}') — this project would silently never publish")
    }

    // github.org/github.project vs. the actual repository, when we can tell
    def githubOrg = projectYml.github?.org as String
    def githubProject = projectYml.github?.project as String
    def actualRepo = System.getenv('GITHUB_REPOSITORY')
    if (!actualRepo) {
        try {
            def remoteUrl = 'git remote get-url origin'.execute().text.trim()
            def m = remoteUrl =~ /[:\/]([^\/]+)\/([^\/]+?)(\.git)?$/
            if (m.find()) {
                actualRepo = "${m.group(1)}/${m.group(2)}"
            }
        } catch (Exception ignored) {
            // no git, or no origin remote — skip this check
        }
    }
    if (actualRepo && githubOrg && githubProject) {
        def expected = "${githubOrg}/${githubProject}"
        if (!actualRepo.equalsIgnoreCase(expected)) {
            err("project.yml github.org/github.project ('${expected}') does not match the actual repository ('${actualRepo}')")
        }
    }

    // external-references.yml purl vs. projectGroup/project.name
    def extRefFile = new File('.github/dependency-graph/external-references.yml')
    if (extRefFile.exists() && pluginName) {
        def extRef = loadYaml('.github/dependency-graph/external-references.yml')
        def projectGroup = gradleProperties.getProperty('projectGroup')
        def purl = extRef?.references?.find { it.purl }?.purl as String
        if (projectGroup && purl) {
            def expectedPurl = "pkg:maven/${projectGroup}/${pluginName}"
            if (purl != expectedPurl) {
                err(".github/dependency-graph/external-references.yml purl ('${purl}') does not match " +
                    "projectGroup/plugin name ('${expectedPurl}')")
            }
        } else if (!projectGroup) {
            warn("gradle.properties has no projectGroup — cannot cross-check external-references.yml's purl")
        }
    }
}

// ---------------------------------------------------------------------------
// 6. gradle.properties required keys
// ---------------------------------------------------------------------------

def requiredGradleProperties = [
    'projectVersion', 'projectGroup', 'grailsVersion', 'projectsToPublish',
    'ciBuildScanPublish', 'ciBuildScanTermsOfUseUrl', 'ciBuildScanTermsOfUseAgree',
    'checkstyleVersion', 'codenarcVersion', 'jacocoVersion',
    'asciidoctorVersion', 'testLoggerVersion',
]
if (gradlePropertiesFile.exists()) {
    requiredGradleProperties.each { key ->
        if (!gradleProperties.getProperty(key)) {
            err("gradle.properties is missing '${key}'")
        }
    }
}

// ---------------------------------------------------------------------------
// 7. .sdkmanrc defines java/gradle/groovy, and its gradle version matches the
// wrapper's distributionUrl
// ---------------------------------------------------------------------------

def sdkmanrcFile = new File('.sdkmanrc')
if (sdkmanrcFile.exists()) {
    def sdkmanrc = new Properties()
    sdkmanrcFile.withInputStream { sdkmanrc.load(it) }
    ['java', 'gradle', 'groovy'].each { key ->
        if (!sdkmanrc.getProperty(key)) {
            err(".sdkmanrc is missing '${key}='")
        }
    }
    def wrapperPropsFile = new File('gradle/wrapper/gradle-wrapper.properties')
    if (wrapperPropsFile.exists() && sdkmanrc.getProperty('gradle')) {
        def wrapperProps = new Properties()
        wrapperPropsFile.withInputStream { wrapperProps.load(it) }
        def distUrl = wrapperProps.getProperty('distributionUrl') ?: ''
        def m = distUrl =~ /gradle-([0-9.]+)-/
        if (m.find() && m.group(1) != sdkmanrc.getProperty('gradle')) {
            err(".sdkmanrc gradle version ('${sdkmanrc.getProperty('gradle')}') does not match the wrapper's " +
                "distributionUrl version ('${m.group(1)}')")
        }
    }
}

// ---------------------------------------------------------------------------
// 8. rootProject.name ends in -root; docs project is <plugin>-docs
// ---------------------------------------------------------------------------

def rootNameMatch = settingsGradleText =~ /rootProject\.name\s*=\s*['"]([^'"]+)['"]/
def rootName = rootNameMatch.find() ? rootNameMatch.group(1) : null
if (rootName && !rootName.endsWith('-root')) {
    err("settings.gradle rootProject.name ('${rootName}') does not end in '-root' — " +
        "config.docs.gradle derives sibling project paths from this suffix")
}

def docsNameMatch = settingsGradleText =~ /project\(['"]:docs['"]\)\.name\s*=\s*['"]([^'"]+)['"]/
def docsName = docsNameMatch.find() ? docsNameMatch.group(1) : null
def pluginNameForDocs = (settingsGradleText =~ /project\(['"]:plugin['"]\)\.name\s*=\s*['"]([^'"]+)['"]/)
def pluginNameStr = pluginNameForDocs.find() ? pluginNameForDocs.group(1) : null
if (docsName && pluginNameStr && docsName != "${pluginNameStr}-docs") {
    err("settings.gradle project(':docs').name ('${docsName}') is not '${pluginNameStr}-docs' — " +
        "config.docs.gradle assumes this naming convention")
}

// ---------------------------------------------------------------------------
// 9. Every config.* id used anywhere resolves to a build-logic file
// ---------------------------------------------------------------------------

def buildLogicDir = new File('build-logic/src/main/groovy')
if (buildLogicDir.exists()) {
    def availableIds = buildLogicDir.listFiles({ f -> f.name.endsWith('.gradle') } as FileFilter)
        *.name.collect { it - '.gradle' } as Set

    def configIdPattern = ~/(?:id\s+['"]|apply\s*\(?\s*plugin:\s*['"]|pluginManager\.apply\(\s*['"])(config\.[a-zA-Z0-9.\-]+)['"]/
    new File('.').eachFileRecurse { f ->
        if (!f.isFile() || f.name != 'build.gradle') return
        if (f.path.contains('/build/') || f.path.contains('/.gradle/') || f.path.startsWith('./build-logic')) return
        def text = f.text
        (text =~ configIdPattern).each { match ->
            def id = match[1]
            if (!availableIds.contains(id)) {
                err("${f.path} applies '${id}', which has no build-logic/src/main/groovy/${id}.gradle")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 10. index.tmpl uses ${TOKEN} syntax, has every required token, and no
// leftover @TOKEN@ style placeholders
// ---------------------------------------------------------------------------

def indexTmplFile = new File('docs/src/docs/index.tmpl')
if (indexTmplFile.exists()) {
    def tmplText = indexTmplFile.text
    def requiredTokens = [
        'LATEST_VERSION', 'OTHER_VERSIONS_OPTIONS', 'GITHUB_REPO_URL', 'GITHUB_ORG_URL',
        'PROJECT_TITLE', 'PROJECT_DESCRIPTION', 'PROJECT_ORG', 'REPO_SLUG',
    ]
    requiredTokens.each { token ->
        if (!tmplText.contains('${' + token + '}')) {
            err("docs/src/docs/index.tmpl does not use \${${token}} — update-versions.groovy substitutes this token")
        }
    }
    if (!tmplText.contains('HAS_VERSIONS')) {
        err("docs/src/docs/index.tmpl does not reference HAS_VERSIONS — update-versions.groovy substitutes this token")
    }
    def staleTokens = (tmplText =~ /@[A-Z_]{2,}@/).collect { it }.unique()
    if (staleTokens) {
        err("docs/src/docs/index.tmpl still has @TOKEN@-style placeholders (${staleTokens.join(', ')}) — " +
            "update-versions.groovy substitutes \${TOKEN} form only, so these will render literally")
    }
}

// ---------------------------------------------------------------------------
// 11. Root build.gradle has no subprojects{}/allprojects{}/configure(
// ---------------------------------------------------------------------------

def rootBuildGradleText = readText('build.gradle') ?: ''
['subprojects\\s*\\{', 'allprojects\\s*\\{', 'configure\\s*\\('].each { pattern ->
    if (rootBuildGradleText =~ pattern) {
        err("Root build.gradle contains '${pattern.replace('\\\\', '')}' — shared configuration belongs in a " +
            "build-logic/ convention plugin, not the root build script")
    }
}

// ---------------------------------------------------------------------------
// 12. plugin/ has no integration tests
// ---------------------------------------------------------------------------

if (new File('plugin/src/integration-test').exists()) {
    err("plugin/src/integration-test exists — plugin/ must contain only source and unit tests; " +
        "integration tests belong under examples/<app>/src/integration-test")
}
new File('plugin').eachFileRecurse { f ->
    if (f.isFile() && f.name.endsWith('.groovy') && f.text.contains('@Integration')) {
        err("${f.path} uses @Integration inside plugin/ — integration tests belong under examples/<app>/")
    }
}

// ---------------------------------------------------------------------------
// 13/14. Workflow YAML validity, job keys, and local `uses:` references
// ---------------------------------------------------------------------------

def validJobKeys = [
    'name', 'permissions', 'needs', 'if', 'runs-on', 'environment', 'concurrency', 'outputs', 'env',
    'defaults', 'steps', 'timeout-minutes', 'strategy', 'continue-on-error', 'container', 'services',
    'uses', 'with', 'secrets',
] as Set

def workflowDirs = ['.github/workflows', '.github/sync/workflows']
def allWorkflowFiles = []
workflowDirs.each { dir ->
    def d = new File(dir)
    if (d.exists()) {
        d.eachFile { f -> if (f.name.endsWith('.yml') || f.name.endsWith('.yaml')) allWorkflowFiles << f }
    }
}

allWorkflowFiles.each { f ->
    def parsed
    try {
        parsed = new YamlSlurper().parse(f)
    } catch (Exception e) {
        err("${f.path} is not valid YAML: ${e.message}")
        return
    }
    (parsed?.jobs ?: [:]).each { jobId, job ->
        if (!(job instanceof Map)) return
        def badKeys = job.keySet() - validJobKeys
        if (badKeys) {
            err("${f.path} job '${jobId}' has invalid top-level key(s) ${badKeys} — " +
                "likely misplaced (e.g. 'matrix' must be nested under 'strategy:', not a sibling of it)")
        }
    }

    def usesLocalPattern = ~/uses:\s*\.\/\.github\/workflows\/(\S+)/
    (f.text =~ usesLocalPattern).each { match ->
        def referenced = match[1]
        if (!new File(".github/workflows/${referenced}").exists()) {
            err("${f.path} references 'uses: ./.github/workflows/${referenced}', which does not exist there " +
                "(reusable workflow references only resolve within .github/workflows/)")
        }
    }
}

// ---------------------------------------------------------------------------
// 15. No leftover template identifiers (child repos only — the template
// legitimately contains all of these)
// ---------------------------------------------------------------------------

if (!isTemplate) {
    def excludedDirs = ['./build-logic', './.agents', './gradle', './.git']
    def headerPattern = ~/(WARNING: Do not edit this file directly|maintained in the grails-plugin-template repository|grails-plugin-template\/tree\/main)/
    def identifierPattern = ~/(grails-plugin-template|PluginTemplateGrailsPlugin|grails\/plugins\/template|app1)/
    new File('.').eachFileRecurse { f ->
        if (!f.isFile()) return
        if (excludedDirs.any { f.path.startsWith(it) }) return
        if (f.path.contains('/build/')) return
        def text
        try {
            text = f.text
        } catch (Exception ignored) {
            return // binary file
        }
        text.eachLine { line ->
            if ((line =~ identifierPattern) && !(line =~ headerPattern)) {
                warn("${f.path} still references template-specific content: ${(line =~ identifierPattern)[0]}")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Template-only checks
// ---------------------------------------------------------------------------

if (isTemplate) {
    if (new File('.github/workflows/release.yml').exists()) {
        err(".github/workflows/release.yml exists — it must live under .github/sync/workflows/ so the template " +
            "itself never runs the child-only release pipeline")
    }
    if (!new File('.github/sync/workflows/release.yml').exists()) {
        err(".github/sync/workflows/release.yml is missing — release.yml should be a child-only workflow synced " +
            "from there")
    }

    def workflowNames = new File('.github/workflows').exists() ?
        new File('.github/workflows').listFiles({ f -> f.name.endsWith('.yml') } as FileFilter)*.name as Set : []
    def syncWorkflowNames = new File('.github/sync/workflows').exists() ?
        new File('.github/sync/workflows').listFiles({ f -> f.name.endsWith('.yml') } as FileFilter)*.name as Set : []
    def collisions = workflowNames.intersect(syncWorkflowNames)
    if (collisions) {
        err("The same workflow filename(s) ${collisions} exist in both .github/workflows/ and " +
            ".github/sync/workflows/ — only one can be the source of truth")
    }

    def filesSyncText = readText('.github/workflows/files-sync.yml') ?: ''
    def syncSubdirs = new File('.github/sync').exists() ?
        new File('.github/sync').listFiles({ f -> f.isDirectory() } as FileFilter)*.name : []
    syncSubdirs.each { subdir ->
        if (!filesSyncText.contains("sync/${subdir}")) {
            err(".github/sync/${subdir}/ exists but files-sync.yml does not appear to copy it — " +
                "anything placed there would never reach a child repo")
        }
    }

    def ciYmlText = readText('.github/workflows/ci.yml') ?: ''
    def repoGuard = "github.repository != 'grails-plugins/grails-plugin-template'"
    def ciYaml = loadYaml('.github/workflows/ci.yml')
    ['publish', 'update-index'].each { jobId ->
        def job = ciYaml?.jobs?.get(jobId)
        def ifCondition = job?.'if' as String
        if (job != null && (ifCondition == null || !ifCondition.contains(repoGuard))) {
            err("ci.yml job '${jobId}' does not guard against running in the template repo itself " +
                "(expected its 'if:' to contain \"${repoGuard}\")")
        }
    }

    if (!filesSyncText.contains("exclude='files-sync.yml'") && !filesSyncText.contains('exclude=\'files-sync.yml\'')) {
        err("files-sync.yml no longer excludes itself from the workflows it copies — it would sync itself into " +
            "every child repo")
    }
    if (!filesSyncText.contains('prepare-sync-matrix.groovy')) {
        warn("files-sync.yml does not mention excluding prepare-sync-matrix.groovy from the scripts it copies")
    }

    // Paths that files-sync.yml's rsync/maybe_copy calls draw from source/ must
    // actually exist in the template, or every sync PR silently omits them.
    def syncedSourcePaths = [
        '.github/workflows', '.github/scripts', 'build-logic', 'gradle',
        '.agents', 'docs/src/docs/index.tmpl',
        '.editorconfig', '.sdkmanrc', 'CONTRIBUTING.md', 'LICENSE.txt', 'gradlew.bat', 'gradlew',
        '.github/dependabot.yml', '.github/renovate.json', '.github/release-drafter.yml',
    ]
    syncedSourcePaths.each { path ->
        if (!new File(path).exists()) {
            err("files-sync.yml expects to copy '${path}', but it does not exist in this repo")
        }
    }

    def projectsYaml = loadYaml('.github/projects.yml')
    def seen = [] as Set
    (projectsYaml?.projects ?: [:]).each { org, repoList ->
        (repoList ?: []).each { entry ->
            def repo = entry instanceof Map ? entry.repo : entry
            if (!repo) {
                err(".github/projects.yml has a malformed entry under '${org}': ${entry}")
                return
            }
            def key = "${org}/${repo}"
            if (!seen.add(key)) {
                err(".github/projects.yml lists '${key}' more than once")
            }
        }
    }
} else {
    // A child repo should never itself define .github/projects.yml or a
    // .github/sync/ tree — those are template-only concepts.
    if (new File('.github/sync').exists()) {
        warn(".github/sync/ exists in a non-template repo — this directory has no meaning outside the template " +
            "and is not something files-sync.yml copies")
    }
}

// ---------------------------------------------------------------------------
// Report
// ---------------------------------------------------------------------------

def strict = System.getenv('VERIFY_STRICT') == '1'

println ''
if (warnings) {
    println "Warnings (${warnings.size()}):"
    warnings.each { println "  ⚠ ${it}" }
}
if (errors) {
    println "Errors (${errors.size()}):"
    errors.each { println "  ✗ ${it}" }
}
if (!errors && !warnings) {
    println 'verify-repository: all checks passed'
}

if (strict && warnings) {
    println ''
    println "VERIFY_STRICT=1 — treating ${warnings.size()} warning(s) as error(s)"
    errors.addAll(warnings)
}

if (errors) {
    println ''
    println "verify-repository: FAILED with ${errors.size()} error(s)"
    System.exit(1)
} else {
    System.exit(0)
}
