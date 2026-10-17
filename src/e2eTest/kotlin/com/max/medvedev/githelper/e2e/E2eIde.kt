package com.max.medvedev.githelper.e2e

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.areIndicatorsVisible
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.splitMode
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.driver.remoteDev.RemDevDriverRunner
import com.intellij.ide.starter.driver.engine.DriverRunner
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Assertions.assertEquals
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.walk
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** The plugin id. */
const val PLUGIN_ID = "max.medvedev.git-helper"

/**
 * How the IDE runs: as one process, or as a backend and a JetBrains Client (split mode). The Starter of our platform
 * (253) has no such type (newer ones have `IdeRunMode`), so [apply] sets up what it would.
 */
enum class IdeRunMode {
    MONOLITH,
    SPLIT;

    /**
     * Starter picks the test container by the global `splitMode` setting, when the context is created, and the Driver
     * runner from its DI container, when the IDE starts, with the one for a single process by default: in split mode
     * that starts only the backend, and the Driver talks to it.
     */
    fun apply() {
        ConfigurationStorage.splitMode(this == SPLIT)
        di = if (this == SPLIT) {
            DI {
                extend(defaultDi, allowOverride = true)
                bindSingleton<DriverRunner> { RemDevDriverRunner() }
            }
        } else {
            defaultDi
        }
    }

    private companion object {
        /** Starter's own DI container, before a split run replaced it. */
        val defaultDi: DI = di
    }
}

/**
 * Starts IntelliJ IDEA (the build we compile against, passed by the `e2eTest` task) with the plugin zip on a fresh empty
 * project, runs [test] with the Driver and closes the IDE. Then fails if the IDE logged an error from our plugin, see
 * [assertNoPluginErrors].
 *
 * [setUpProject] fills the project directory before the IDE starts, e.g. creates a Git repository in it ([GitRepo]).
 *
 * In [IdeRunMode.SPLIT], Starter starts the IDE as a backend and a JetBrains Client that joins it, each with the plugin
 * installed, and the Driver of [test] talks to the frontend: calls go there unless a `@Remote` or a call names
 * `RdTarget.BACKEND`. The run's directories get `-split` appended, so both modes of a test can run in one session.
 */
fun runIdeWithPlugin(
    testName: String,
    mode: IdeRunMode,
    setUpProject: (Path) -> Unit = {},
    test: Driver.(project: Path) -> Unit,
) {
    mode.apply()
    try {
        val runName = testName + if (mode == IdeRunMode.SPLIT) "-split" else ""
        val projectDir = createProject(runName, setUpProject)
        val context = newContext(runName, projectDir)
        context.runIdeWithDriver().useDriverAndCloseIde { test(projectDir) }
        assertNoPluginErrors(context)
    } finally {
        IdeRunMode.MONOLITH.apply()
    }
}

private fun newContext(testName: String, projectDir: Path): IDETestContext {
    val testCase = TestCase(IdeProductProvider.IU, LocalProjectInfo(projectDir))
        .withBuildNumber(systemProperty("e2e.ide.build"))
    val context = Starter.newContext(testName, testCase)
    val pluginZip = Path(systemProperty("path.to.build.plugin"))
    for (side in context.sides()) {
        // Unpacks its model under a visible indicator for 10-13 s after indexing, which every wait for the project blocks on
        PluginConfigurator(side).disablePlugins(FULL_LINE_PLUGIN_ID)
        PluginConfigurator(side).installPluginFromPath(pluginZip)
    }
    return context
}

/** The context and, in split mode, the frontend's, which has its own config and plugins directories (`<test>/frontend`). */
private fun IDETestContext.sides(): List<IDETestContext> =
    if (this is IDERemDevTestContext) listOf(this, frontendIDEContext) else listOf(this)

/** Full Line Code Completion, bundled with IntelliJ IDEA Ultimate. */
private const val FULL_LINE_PLUGIN_ID = "org.jetbrains.completion.full.line"

private fun systemProperty(name: String): String =
    System.getProperty(name) ?: error("No system property $name; run the tests with `./gradlew e2eTest`")

/**
 * A fresh project directory for each run, since the tests change it and the IDE writes its settings there. Its
 * `.idea/vcs.xml` maps the project root to Git, so that the IDE picks up a repository [setUpProject] creates there.
 */
@OptIn(ExperimentalPathApi::class)
private fun createProject(testName: String, setUpProject: (Path) -> Unit): Path {
    val dir = GlobalPaths.instance.testHomePath / "projects" / testName.replace(Regex("[^\\w.-]"), "_")
    dir.deleteRecursively()
    (dir / ".idea").createDirectories()
    (dir / ".idea" / "vcs.xml").toFile().writeText(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="VcsDirectoryMappings">
            <mapping directory="${'$'}PROJECT_DIR${'$'}" vcs="Git" />
          </component>
        </project>
        """.trimIndent()
    )
    setUpProject(dir)
    return dir
}

/**
 * Waits until the project is open, the IDE has found its Git repository, and the project has been quiet for
 * [PROJECT_SETTLE_TIME] with no indicators in the status bar. The SDK's `waitForIndicators` waits for 10 s instead.
 * In split mode the repositories live on the backend; the frontend's status bar mirrors the backend's.
 */
fun Driver.waitForProjectReady(timeout: Duration = 5.minutes) {
    step("Wait for the project") {
        waitForProjectOpen(timeout)
        waitFor("The IDE has found the Git repository", timeout, interval = 250.milliseconds) {
            gitRepositories().isNotEmpty()
        }
        var quietSince: TimeMark? = null
        waitFor("The project is quiet for $PROJECT_SETTLE_TIME", timeout, interval = 250.milliseconds) {
            if (areIndicatorsVisible(singleProject())) {
                quietSince = null
                return@waitFor false
            }
            val since = quietSince ?: TimeSource.Monotonic.markNow().also { quietSince = it }
            since.elapsedNow() >= PROJECT_SETTLE_TIME
        }
    }
}

private val PROJECT_SETTLE_TIME = 2.seconds

/**
 * Fails if an `idea.log` of the run (in split mode also the frontend's, under `<test>/frontend`) has an error record
 * from our code or blaming our plugin, or a `NoClassDefFoundError`, so that wiring and threading bugs fail a test even
 * when the result looks right. `Logger.error` writes a `SEVERE` record (see [logErrors]); records of other plugins pass.
 */
private fun assertNoPluginErrors(context: IDETestContext) {
    val errors = context.paths.testHome.walk()
        .filter { it.name == "idea.log" && it.isRegularFile() }
        .flatMap { logErrors(it.readLines()) }
        .filter { error -> PLUGIN_ERROR_MARKERS.any { it in error } }
        .toList()
    assertEquals(emptyList<String>(), errors, "Errors from the plugin in idea.log")
}

private val PLUGIN_ERROR_MARKERS = listOf(
    "com.max.medvedev.githelper",
    PLUGIN_ID,
    "Plugin to blame: Advanced Git Features",
    "NoClassDefFoundError",
)

/**
 * The start of a log line up to the message, e.g. `2026-09-28 12:00:00,000 [   1234] SEVERE - #c.i.Foo - `. Group 1
 * is the level and group 2 the logger.
 */
private val LOG_LINE_HEAD = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3} \[ *\d+] +(\w+) - (\S*) - """)

private val ERROR_LEVELS = setOf("SEVERE", "ERROR")

/**
 * The lines `Logger.error` writes after an error's message and stack trace, with the same level and logger as the
 * message (the time may differ by a few milliseconds).
 */
private val ERROR_TRAILER_PREFIXES = listOf("IntelliJ IDEA ", "JDK: ", "OS: ", "Plugin to blame: ", "Last Action: ")

/**
 * The error records of a log, each with its stack trace (the lines without a head) and its trailer
 * ([ERROR_TRAILER_PREFIXES], e.g. `Plugin to blame: ...`). The IDE writes `Logger.error` as JUL `SEVERE`
 * (`IdeaLogRecordFormatter` renames only `WARNING`); `ERROR` is matched too.
 */
private fun logErrors(lines: List<String>): List<String> {
    val errors = mutableListOf<String>()
    var current: StringBuilder? = null
    var currentLogger: String? = null
    for (line in lines) {
        val head = LOG_LINE_HEAD.find(line)
        when {
            head == null -> current?.append('\n')?.append(line)
            current != null &&
                head.groupValues[1] in ERROR_LEVELS &&
                head.groupValues[2] == currentLogger &&
                ERROR_TRAILER_PREFIXES.any { line.startsWith(it, head.value.length) }
                -> current.append('\n').append(line)

            else -> {
                current?.let { errors += it.toString() }
                val isError = head.groupValues[1] in ERROR_LEVELS
                current = if (isError) StringBuilder(line) else null
                currentLogger = if (isError) head.groupValues[2] else null
            }
        }
    }
    current?.let { errors += it.toString() }
    return errors
}
