package com.max.medvedev.githelper.rebaseOnMaster

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.max.medvedev.githelper.GitHelperBundle
import com.max.medvedev.githelper.detectDefaultBranch
import git4idea.GitBranch
import git4idea.actions.branch.GitSingleBranchAction
import git4idea.branch.GitBrancher
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions.addTooltipText
import git4idea.ui.branch.GitBranchPopupActions.getSelectedBranchFullPresentation

/**
 * Action to checkout a local branch and rebase it onto the default branch (master or main) in one step.
 * This action appears in local branch submenus of the branch popup, including the current branch.
 */
class GitCheckoutAndRebaseOnMasterAction : GitSingleBranchAction() {

    override val disabledForRemote: Boolean = true

    override fun isEnabledForRef(ref: GitBranch, repositories: List<GitRepository>): Boolean {
        if (repositories.any { it.isFresh }) return false

        val defaultBranch = detectDefaultBranch(repositories) ?: return false

        // Rebasing the default branch onto itself makes no sense
        return ref.name != defaultBranch
    }

    override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitBranch) {
        val defaultBranch = detectDefaultBranch(repositories) ?: return

        e.presentation.text = GitHelperBundle.message("action.Git.CheckoutAndRebaseOnMaster.text", defaultBranch)

        val description = GitHelperBundle.message(
            "action.Git.CheckoutAndRebaseOnMaster.description",
            getSelectedBranchFullPresentation(reference.name),
            defaultBranch
        )
        e.presentation.description = description
        addTooltipText(e.presentation, description)
    }

    override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitBranch) {
        val defaultBranch = detectDefaultBranch(repositories) ?: return

        // `git rebase <upstream> <branch>` checks out <branch> first and then rebases it onto <upstream>.
        // GitBrancher runs it in the background and handles conflicts, local changes and notifications.
        GitBrancher.getInstance(project).rebase(repositories, defaultBranch, reference.name)
    }
}
