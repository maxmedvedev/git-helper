package com.max.medvedev.githelper.openConfig

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.max.medvedev.githelper.GitHelperBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the `.git/config` file of a Git repository in the editor.
 *
 * The `.git` directory is hidden from the project view, so the config file is otherwise reachable only
 * through "Open File" dialogs. When the project has several repositories, the one owning the current
 * file is preferred; without such a file the user picks the repository from a popup.
 */
internal class GitOpenConfigAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val data = collectData(e)
        with(e.presentation) {
            isEnabledAndVisible = data != null
            if (data != null) {
                description = GitHelperBundle.message("action.GitHelper.OpenGitConfig.description")
            }
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val data = collectData(e) ?: return

        val repository = data.preferredRepository ?: data.repositories.singleOrNull()
        if (repository != null) {
            openConfig(data.project, repository)
        }
        else {
            chooseRepository(data)
        }
    }

    private fun chooseRepository(data: Data) {
        val byPresentableRoot = data.repositories.associateByTo(
            LinkedHashMap(),
            { it.root.presentableUrl },
        )

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(byPresentableRoot.keys.toList())
            .setTitle(GitHelperBundle.message("action.GitHelper.OpenGitConfig.chooser.title"))
            .setItemChosenCallback { root ->
                byPresentableRoot[root]?.let { repository -> openConfig(data.project, repository) }
            }
            .createPopup()
            .showCenteredInCurrentWindow(data.project)
    }

    private fun openConfig(project: Project, repository: GitRepository) {
        val configPath = repository.repositoryFiles.configFile.toPath()

        // The `.git` directory is not fully watched, so the config file may be unknown to the VFS yet.
        val configFile = runWithModalProgressBlocking(
            project,
            GitHelperBundle.message("action.GitHelper.OpenGitConfig.progress.title"),
        ) {
            withContext(Dispatchers.IO) { configPath.refreshAndFindVirtualFile() }
        }

        if (configFile == null) {
            Messages.showErrorDialog(
                project,
                GitHelperBundle.message("action.GitHelper.OpenGitConfig.error.not.found", configPath.toString()),
                GitHelperBundle.message("action.GitHelper.OpenGitConfig.text"),
            )
            return
        }

        FileEditorManager.getInstance(project).openFile(configFile, true)
    }

    private fun collectData(e: AnActionEvent): Data? {
        val project = e.project ?: return null

        val repositoryManager = GitRepositoryManager.getInstance(project)
        val repositories = repositoryManager.repositories.ifEmpty { return null }

        val currentFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val preferredRepository = currentFile?.let { repositoryManager.getRepositoryForFileQuick(it) }

        return Data(project, repositories, preferredRepository)
    }

    private class Data(
        val project: Project,
        val repositories: List<GitRepository>,
        val preferredRepository: GitRepository?,
    )
}
