package com.max.medvedev.githelper.e2e

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.waitFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * `Git.DeletePreviousBranch` deletes the branch `git checkout -` would switch to, including after checkouts made from
 * the command line, and refuses with a notification when that branch is gone or protected.
 */
class GitDeletePreviousBranchTest {
    @ParameterizedTest
    @EnumSource(IdeRunMode::class)
    fun `deletes the previously checked out branch`(mode: IdeRunMode) = runIdeWithPlugin(
        "GitDeletePreviousBranchTest",
        mode,
        setUpProject = { dir ->
            // Checked out from the terminal before the IDE starts: on master, with feature as the previous branch
            GitRepo(dir).apply {
                init()
                git("checkout", "-b", "feature")
                git("checkout", "master")
            }
        },
    ) { projectDir ->
        val repo = GitRepo(projectDir)
        waitForProjectReady()

        step("Deletes the previous branch") {
            runGitHelperAction(ACTION_ID)
            waitFor("feature is deleted", 30.seconds, interval = 250.milliseconds) {
                "feature" !in repo.localBranches
            }
            assertEquals(listOf("master"), repo.localBranches)
            assertEquals("master", repo.currentBranch)
        }

        step("Refuses when the previous branch no longer exists") {
            runGitHelperAction(ACTION_ID)
            waitForNotification("No previously checked out branch found")
            assertEquals(listOf("master"), repo.localBranches)
        }

        step("Refuses a protected previous branch") {
            repo.git("checkout", "-b", "topic")
            refreshGitRepositories(expectedCurrentBranch = "topic")
            runGitHelperAction(ACTION_ID)
            waitForNotification("'master' is a protected branch")
            assertEquals(listOf("master", "topic"), repo.localBranches)
            assertEquals("topic", repo.currentBranch)
        }

        step("Deletes a previous branch checked out from the terminal while the IDE runs") {
            repo.git("checkout", "-b", "next")
            refreshGitRepositories(expectedCurrentBranch = "next")
            runGitHelperAction(ACTION_ID)
            waitFor("topic is deleted", 30.seconds, interval = 250.milliseconds) {
                "topic" !in repo.localBranches
            }
            assertEquals(listOf("master", "next"), repo.localBranches)
            assertEquals("next", repo.currentBranch)
        }
    }

    private companion object {
        const val ACTION_ID = "Git.DeletePreviousBranch"
    }
}
