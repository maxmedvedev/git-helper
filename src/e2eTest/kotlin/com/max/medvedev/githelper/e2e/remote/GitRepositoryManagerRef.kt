package com.max.medvedev.githelper.e2e.remote

import com.intellij.driver.client.Remote

/** A project service of the Git plugin; on the backend in split mode. */
@Remote("git4idea.repo.GitRepositoryManager", plugin = "Git4Idea")
interface GitRepositoryManagerRef {
    fun getRepositories(): List<GitRepositoryRef>
}
