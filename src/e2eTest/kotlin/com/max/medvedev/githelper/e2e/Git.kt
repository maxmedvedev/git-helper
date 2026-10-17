package com.max.medvedev.githelper.e2e

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.client.utility
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.ActionCenter
import com.intellij.driver.sdk.invokeGlobalBackendAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitFor
import com.max.medvedev.githelper.e2e.remote.GitRepositoryManagerRef
import com.max.medvedev.githelper.e2e.remote.GitRepositoryRef
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the `git` command line in a repository, from the test's JVM: to set it up before the IDE starts, to make changes
 * "in the terminal" while it runs (followed by [refreshGitRepositories]), and to check what the IDE did.
 */
class GitRepo(private val dir: Path) {
    fun git(vararg args: String): String {
        val process = ProcessBuilder("git", *args)
            .directory(dir.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(1, TimeUnit.MINUTES)) { "git ${args.joinToString(" ")} timed out" }
        check(process.exitValue() == 0) { "git ${args.joinToString(" ")} failed:\n$output" }
        return output.trim()
    }

    /** A repository on `master` with one commit; the IDE's `.idea` is excluded, so the working tree stays clean. */
    fun init() {
        git("init", "--initial-branch=master")
        git("config", "user.name", "E2E Test")
        git("config", "user.email", "e2e@example.com")
        git("config", "commit.gpgsign", "false")
        dir.resolve(".git/info/exclude").toFile().appendText("\n.idea/\n")
        dir.resolve("README.md").toFile().writeText("Test repository\n")
        git("add", "README.md")
        git("commit", "-m", "Initial commit")
    }

    val localBranches: List<String>
        get() = git("for-each-ref", "--format=%(refname:short)", "refs/heads/").lines().filter { it.isNotEmpty() }.sorted()

    val currentBranch: String
        get() = git("symbolic-ref", "--short", "HEAD")
}

/** The IDE's Git repositories of the project, on the backend, where Git runs in split mode. */
fun Driver.gitRepositories(): List<GitRepositoryRef> =
    service<GitRepositoryManagerRef>(singleProject(RdTarget.BACKEND), RdTarget.BACKEND).getRepositories()

/**
 * Makes the IDE read the state of its repositories again after a change from the command line. The IDE would notice it
 * on its own from the file watcher, or when its window gets activated, which the test IDE's window never does.
 */
fun Driver.refreshGitRepositories(expectedCurrentBranch: String, timeout: Duration = 30.seconds) {
    for (repository in gitRepositories()) repository.update()
    waitFor("The IDE sees $expectedCurrentBranch as the current branch", timeout, interval = 250.milliseconds) {
        gitRepositories().all { it.getCurrentBranchName() == expectedCurrentBranch }
    }
}

/**
 * Runs an action of the plugin with the project's frame as its context. The plugin depends on the Git plugin, which runs
 * on the backend, so in split mode its actions are the backend's (the JetBrains Client shows them from there).
 */
fun Driver.runGitHelperAction(actionId: String) {
    invokeGlobalBackendAction(actionId)
}

/** The texts (`title: content`) of the notifications shown for the project, which the backend's Git operations post. */
fun Driver.notificationTexts(): List<String> =
    utility<ActionCenter>(RdTarget.BACKEND).getNotifications(singleProject(RdTarget.BACKEND))
        .map { "${it.getTitle()}: ${it.getContent()}" }

/** Waits for a notification whose text contains [text]. */
fun Driver.waitForNotification(text: String, timeout: Duration = 30.seconds) {
    waitFor("A notification with '$text'", timeout, interval = 250.milliseconds, errorMessage = {
        "No notification with '$text'; shown: ${notificationTexts()}"
    }) {
        notificationTexts().any { text in it }
    }
}
