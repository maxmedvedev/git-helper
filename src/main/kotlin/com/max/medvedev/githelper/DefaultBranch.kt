package com.max.medvedev.githelper

import git4idea.repo.GitRepository

/**
 * Detects the default branch (master or main) across all repositories.
 * Returns the branch name if ALL repositories have the same default branch,
 * otherwise returns null.
 *
 * Priority: Check for "master" first, then "main".
 * This ensures consistent behavior when repositories have both branches.
 */
internal fun detectDefaultBranch(repositories: List<GitRepository>): String? {
    return listOf("master", "main")
        .firstOrNull { branchName ->
            repositories.all { repo -> repo.branches.findLocalBranch(branchName) != null }
        }
}
