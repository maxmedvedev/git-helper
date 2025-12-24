// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.max.medvedev.githelper.checkoutAndDelete

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.max.medvedev.githelper.GitHelperBundle
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.GitTag
import git4idea.actions.branch.GitSingleBranchAction
import git4idea.branch.GitBrancher
import git4idea.config.GitSharedSettings
import git4idea.repo.GitRefUtil
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions.*

/**
 * Action to checkout a branch and delete the current branch.
 * This action appears in non-current branch submenus and allows switching to that branch while deleting the current one.
 */
class GitDeleteCurrentBranchAndSwitchAction : GitSingleBranchAction() {

    override fun isEnabledForRef(ref: GitBranch, repositories: List<GitRepository>): Boolean {
        // Only enable if this branch is NOT the current branch in any repository
        if (isCurrentRefInAnyRepo(ref, repositories)) {
            return false
        }

        // Get all current branch names (excluding null/detached HEAD)
        val currentBranchList = repositories.map { it.currentBranch?.name }
        if (currentBranchList.contains(null)) {
            return false
        }

        val currentBranches = currentBranchList.filterNotNull().toSet()

        // Only enable if all repositories are on the SAME current branch
        if (currentBranches.size != 1) {
            return false
        }

        // Check if that single current branch is protected
        val currentBranch = currentBranches.first()
        val project = repositories.firstOrNull()?.project ?: return false
        val settings = GitSharedSettings.getInstance(project)

        return !settings.isBranchProtected(currentBranch)
    }

    override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitBranch) {
        // Use helper functions to properly handle multi-repository scenarios
        val currentBranchPresentation = getCurrentBranchTruncatedPresentation(project, repositories)

        e.presentation.text = GitHelperBundle.message("action.Git.DeleteCurrentBranchAndSwitch.text", currentBranchPresentation)

        val description = GitHelperBundle.message(
            "action.Git.DeleteCurrentBranchAndSwitch.description",
            getSelectedBranchFullPresentation(reference.name),
            getCurrentBranchFullPresentation(project, repositories)
        )
        e.presentation.description = description
        addTooltipText(e.presentation, description)
    }

    override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitBranch) {
        val targetBranch = reference.name  // The clicked branch

        // At this point, isEnabledForRef() has ensured all repositories are on the same branch
        val currentBranch = repositories.firstOrNull()?.currentBranch?.name ?: return

        // Execute operation for all repositories
        executeOperation(project, repositories, currentBranch, targetBranch)
    }

    private fun executeOperation(
        project: Project,
        repositories: List<GitRepository>,
        currentBranch: String,
        targetBranch: String
    ) {
        object : Task.Backgroundable(
            project,
            GitHelperBundle.message("action.Git.DeleteCurrentBranchAndSwitch.progress.title", currentBranch, targetBranch),
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                val gitBrancher = GitBrancher.getInstance(project)
                gitBrancher.checkout(targetBranch, false, repositories, {
                    gitBrancher.deleteBranch(currentBranch, repositories)
                })
            }
        }.queue()
    }

    // copied from git4idea.actions.ref.GitSingleRefAction.Companion.isCurrentRefInAnyRepo$intellij_vcs_git
    internal fun isCurrentRefInAnyRepo(ref: GitReference, repositories: List<GitRepository>) = repositories.any {
        when(ref) {
            is GitLocalBranch -> it.currentBranch == ref
            is GitTag -> it.state == Repository.State.DETACHED && GitRefUtil.getCurrentReference(it) == ref
            else -> false
        }
    }
}
