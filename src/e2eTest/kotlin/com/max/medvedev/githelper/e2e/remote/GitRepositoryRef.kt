package com.max.medvedev.githelper.e2e.remote

import com.intellij.driver.client.Remote

@Remote("git4idea.repo.GitRepository", plugin = "Git4Idea")
interface GitRepositoryRef {
    /** The current branch, or null for a detached HEAD. */
    fun getCurrentBranchName(): String?

    /** Reads the repository's state from `.git` again. */
    fun update()
}
