package com.max.medvedev.githelper.rebaseOnMaster

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.max.medvedev.githelper.GitHelperBundle
import com.max.medvedev.githelper.detectDefaultBranch
import git4idea.branch.GitBrancher
import git4idea.fetch.GitFetchSupport
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

/**
 * Fetches the default branch (master or main) from its upstream remote and rebases the current branch onto it.
 *
 * The local default branch is updated the same way the platform's "Update" branch action does it for
 * non-current branches: by fetching `<remote branch>:<local branch>`, which only succeeds on fast-forward.
 * The rebase itself is delegated to [GitBrancher], so conflicts, local changes and notifications are handled
 * by IntelliJ's regular rebase flow.
 */
internal class GitRebaseOnFreshMasterAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val data = collectData(e)
    with(e.presentation) {
      isEnabledAndVisible = data != null
      if (data != null) {
        text = GitHelperBundle.message("action.Git.RebaseOnFreshMaster.text", data.defaultBranch)
        description = GitHelperBundle.message("action.Git.RebaseOnFreshMaster.description", data.defaultBranch)
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val data = collectData(e) ?: return

    object : Task.Backgroundable(
      data.project,
      GitHelperBundle.message("action.Git.RebaseOnFreshMaster.progress.title", data.defaultBranch),
      true
    ) {
      override fun run(indicator: ProgressIndicator) {
        val fetchSupport = GitFetchSupport.fetchSupport(data.project)
        for (repository in data.repositories) {
          val trackInfo = repository.getBranchTrackInfo(data.defaultBranch) ?: return
          val refspec = "${trackInfo.remoteBranch.nameForRemoteOperations}:${data.defaultBranch}"

          val fetchResult = fetchSupport.fetch(repository, trackInfo.remote, refspec)
          if (!fetchResult.showNotificationIfFailed()) return
        }

        GitBrancher.getInstance(data.project).rebase(data.repositories, data.defaultBranch)
      }
    }.queue()
  }

  private fun collectData(e: AnActionEvent): Data? {
    val project = e.project ?: return null

    val repositories = GitRepositoryManager.getInstance(project).repositories.ifEmpty { return null }
    if (repositories.any { it.isFresh }) return null

    val defaultBranch = detectDefaultBranch(repositories) ?: return null

    // The default branch must have an upstream to be fetched from
    if (repositories.any { it.getBranchTrackInfo(defaultBranch) == null }) return null

    // The default branch can't be rebased onto itself, and a detached HEAD has nothing to rebase
    if (repositories.any { it.currentBranch == null || it.currentBranch?.name == defaultBranch }) return null

    return Data(project, repositories, defaultBranch)
  }

  private class Data(
    val project: Project,
    val repositories: List<GitRepository>,
    val defaultBranch: String,
  )
}
