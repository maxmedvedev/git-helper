package com.max.medvedev.githelper.deletePrevious

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.max.medvedev.githelper.GitHelperBundle
import git4idea.branch.GitBrancher
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitSharedSettings
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

/**
 * Deletes the branch that was checked out before the current one — the branch `git checkout -` would switch to.
 *
 * The previous branch is resolved from the reflog via `@{-1}`, so checkouts made outside the IDE count too.
 * Since that requires running Git, it happens in [actionPerformed] rather than in [update].
 * The deletion itself is delegated to [GitBrancher], so the regular "not fully merged" confirmation and
 * the "Restore" notification are preserved.
 */
internal class GitDeletePreviousBranchAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = collectData(e) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val data = collectData(e) ?: return

    object : Task.Backgroundable(
      data.project,
      GitHelperBundle.message("action.Git.DeletePreviousBranch.progress.title"),
      true
    ) {
      override fun run(indicator: ProgressIndicator) {
        val previousBranches = data.repositories.map { findPreviousBranch(it) }
        if (previousBranches.contains(null)) {
          notifyError(data.project, GitHelperBundle.message("action.Git.DeletePreviousBranch.error.no.previous"))
          return
        }
        val previousBranch = previousBranches.distinct().singleOrNull()
        if (previousBranch == null) {
          notifyError(data.project, GitHelperBundle.message("action.Git.DeletePreviousBranch.error.not.same"))
          return
        }

        val error = when {
          data.repositories.any { it.branches.findLocalBranch(previousBranch) == null } ->
            GitHelperBundle.message("action.Git.DeletePreviousBranch.error.not.found", previousBranch)
          data.repositories.any { it.currentBranch?.name == previousBranch } ->
            GitHelperBundle.message("action.Git.DeletePreviousBranch.error.current", previousBranch)
          GitSharedSettings.getInstance(data.project).isBranchProtected(previousBranch) ->
            GitHelperBundle.message("action.Git.DeletePreviousBranch.error.protected", previousBranch)
          else -> null
        }
        if (error != null) {
          notifyError(data.project, error)
          return
        }

        GitBrancher.getInstance(data.project).deleteBranch(previousBranch, data.repositories)
      }
    }.queue()
  }

  /**
   * Returns the short name of the previously checked out ref, or null if there is none.
   * For a detached HEAD in the past, Git prints a commit hash, which is then rejected as not a local branch.
   */
  private fun findPreviousBranch(repository: GitRepository): String? {
    val handler = GitLineHandler(repository.project, repository.root, GitCommand.REV_PARSE)
    handler.setSilent(true)
    handler.addParameters("--abbrev-ref", "@{-1}")
    val result = Git.getInstance().runCommand(handler)
    if (!result.success()) return null
    return result.output.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
  }

  private fun notifyError(project: Project, message: String) {
    VcsNotifier.getInstance(project).notifyError(
      "git.helper.delete.previous.branch.failed",
      GitHelperBundle.message("action.Git.DeletePreviousBranch.error.title"),
      message
    )
  }

  private fun collectData(e: AnActionEvent): Data? {
    val project = e.project ?: return null

    val repositories = GitRepositoryManager.getInstance(project).repositories.ifEmpty { return null }
    if (repositories.any { it.isFresh }) return null

    return Data(project, repositories)
  }

  private class Data(
    val project: Project,
    val repositories: List<GitRepository>,
  )
}
