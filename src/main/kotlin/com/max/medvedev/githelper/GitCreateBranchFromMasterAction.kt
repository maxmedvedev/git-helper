package com.max.medvedev.githelper

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor
import com.intellij.util.textCompletion.TextCompletionProviderBase
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBUI
import fleet.util.associateWithNotNull
import git4idea.branch.GitBrancher
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.validators.GitRefNameValidator
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal class GitCreateBranchFromMasterAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        when (val data = collectData(e)) {
            is Data.Enabled -> createNewBranch(data)
            is Data.Disabled, Data.Invisible -> {}
        }
    }

    private fun createNewBranch(data: Data.Enabled) {
        // Capture current branches before showing dialog
        val currentBranches = data.repositories.associateWithNotNull { it.currentBranch }

        val dialog = CreateBranchFromMasterDialog(data.project, data.repositories, data.defaultBranch)
        if (!dialog.showAndGet()) return

        val branchName = dialog.branchName
        if (branchName.isBlank()) return

        val brancher = GitBrancher.getInstance(data.project)

        // First, checkout the new branch
        brancher.checkoutNewBranchStartingFrom(
            branchName,
            data.defaultBranch,
            data.repositories,
            {
                // After successful checkout, delete the old branch if requested
                if (dialog.shouldDeleteCurrentBranch && currentBranches.isNotEmpty()) {
                    currentBranches.forEach { (repo, branch) ->
                        brancher.deleteBranch(branch.name, listOf(repo))
                    }
                }
            }
        )
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        when (val data = collectData(e)) {
            is Data.Invisible -> e.presentation.isEnabledAndVisible = false
            is Data.Disabled -> {
                e.presentation.isVisible = true
                e.presentation.isEnabled = false
                e.presentation.description = data.description
            }

            is Data.Enabled -> {
                e.presentation.isEnabledAndVisible = true
                e.presentation.description =
                    GitHelperBundle.message("action.Git.CreateBranchFromMaster.description", data.defaultBranch)
            }
        }
    }

    private sealed class Data {
        data object Invisible : Data()
        class Disabled(val description: @Nls String) : Data()
        class Enabled(
            val project: Project,
            val repositories: List<GitRepository>,
            val defaultBranch: String,
        ) : Data()
    }

    private fun collectData(e: AnActionEvent): Data {
        val project = e.project ?: return Data.Invisible

        val repositoryManager = GitRepositoryManager.getInstance(project)
        val repositories = repositoryManager.repositories.toList().ifEmpty { return Data.Invisible }

        // Check for fresh repositories (no commits yet)
        if (repositories.any { it.isFresh }) {
            return Data.Disabled(GitHelperBundle.message("action.Git.CreateBranchFromMaster.disabled.fresh.description"))
        }

        // Detect default branch across all repositories
        val defaultBranch = detectDefaultBranch(repositories)
            ?: return Data.Disabled(GitHelperBundle.message("action.Git.CreateBranchFromMaster.disabled.no.default.description"))

        return Data.Enabled(project, repositories, defaultBranch)
    }

    /**
     * Detects the default branch (master or main) across all repositories.
     * Returns the branch name if ALL repositories have the same default branch,
     * otherwise returns null.
     *
     * Priority: Check for "master" first, then "main".
     * This ensures consistent behavior when repositories have both branches.
     */
    private fun detectDefaultBranch(repositories: List<GitRepository>): String? {
        return listOf("master", "main")
            .firstOrNull { branchName ->
                repositories.all { repo -> repo.branches.findLocalBranch(branchName) != null }
            }
    }
}

/**
 * Dialog for creating a new branch from the default branch (master/main).
 * Provides branch name completion based on existing branch prefixes.
 */
private class CreateBranchFromMasterDialog(
    private val project: Project,
    private val repositories: List<GitRepository>,
    defaultBranch: String
) : DialogWrapper(project) {

    var branchName: String = ""
        private set

    var shouldDeleteCurrentBranch: Boolean = false
        private set

    private val validator = GitRefNameValidator.getInstance()
    private lateinit var textField: TextFieldWithCompletion

    init {
        title = GitHelperBundle.message("action.Git.CreateBranchFromMaster.dialog.title", defaultBranch)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val completionProvider = BranchPrefixCompletionProvider(repositories)
        textField = TextFieldWithCompletion(
            /* project = */ project,
            /* provider = */ completionProvider,
            /* value = */ "",
            /* oneLineMode = */ true,
            /* autoPopup = */ true,
            /* forceAutoPopup = */ false,
            /* showHint = */ false
        )
            .apply {
                minimumSize = JBUI.size(360, 0)
                addDocumentListener(object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        branchName = textField.text
                    }
                })
            }

        return panel {
            row {
                label(GitHelperBundle.message("action.Git.CreateBranchFromMaster.dialog.message"))
            }
            row {
                cell(textField)
                    .align(AlignX.FILL)
                    .focused()
                    .applyToComponent {
                        selectAll()
                    }
            }
            row {
                checkBox(GitHelperBundle.message("action.Git.CreateBranchFromMaster.dialog.delete.current.branch"))
                    .applyToComponent {
                        isSelected = false
                    }
                    .onChanged { checkBox ->
                        shouldDeleteCurrentBranch = checkBox.isSelected
                    }
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        val cleanedName = validator.cleanUpBranchName(branchName).trim()
        return if (validator.checkInput(cleanedName)) null
        else ValidationInfo(
            GitHelperBundle.message("action.Git.CreateBranchFromMaster.error.invalid.name", branchName),
            textField
        )
    }

    override fun getPreferredFocusedComponent() = textField
}

/**
 * Completion provider that suggests branch prefixes (directories) from existing branches.
 * When the user types, it shows existing branch prefixes like "feature/", "bugfix/", etc.
 */
private class BranchPrefixCompletionProvider(repositories: List<GitRepository>) : TextCompletionProviderBase<String>(
    DefaultTextCompletionValueDescriptor.StringValueDescriptor(),
    emptyList(),
    false
), DumbAware {

    private val branchPrefixes: List<String>

    init {
        val allBranches = repositories.asSequence()
            .flatMap { it.branches.localBranches }
            .map { it.name }
            .toList()

        branchPrefixes = collectBranchPrefixes(allBranches)
    }

    override fun getValues(
        parameters: CompletionParameters,
        prefix: String,
        result: CompletionResultSet
    ): Collection<String> {
        // Return branch prefixes for auto-popup completion
        return branchPrefixes
    }

    /**
     * Collects all unique branch prefixes (directories) from branch names.
     * For example, from "feature/foo" and "feature/bar", extracts "feature/".
     */
    private fun collectBranchPrefixes(branchNames: List<String>): List<String> {
        val prefixes = mutableSetOf<String>()

        for (branchName in branchNames) {
            if (branchName.contains('/')) {
                var index = 0
                while (index < branchName.length) {
                    val end = branchName.indexOf('/', index)
                    if (end == -1) break
                    prefixes += branchName.substring(0, end + 1)
                    index = end + 1
                }
            }
        }

        return prefixes.sorted()
    }
}
