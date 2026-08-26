package com.max.medvedev.githelper.showAffected

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog
import com.intellij.openapi.vcs.changes.ui.LoadingCommittedChangeListPanel
import com.intellij.openapi.vcs.changes.ui.LoadingCommittedChangeListPanel.ChangelistData
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.util.VcsLogUtil
import com.max.medvedev.githelper.GitHelperBundle
import org.jetbrains.annotations.Nls

/**
 * Shows all files affected by the commit selected in the VCS Log, in a standalone changes browser.
 *
 * The platform only offers this for file history (`VcsHistory.ShowAllAffected` and
 * `Vcs.Log.ShowAllAffected`); this action brings it to the main log, where a whole commit is selected.
 * Modelled after `com.intellij.vcs.log.ui.actions.history.ShowAllAffectedFromHistoryAction`: the change
 * list is built from the details the log already loads, so no extra Git process is needed.
 */
internal class GitShowAllAffectedFromLogAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        with(e.presentation) {
            when (val data = collectData(e)) {
                Data.Invisible -> isEnabledAndVisible = false

                is Data.Disabled -> {
                    isVisible = true
                    isEnabled = false
                    description = data.description
                }

                is Data.Enabled -> {
                    isEnabledAndVisible = true
                    description = GitHelperBundle.message("action.GitHelper.Log.ShowAllAffected.description")
                }
            }
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val data = collectData(e) as? Data.Enabled ?: return

        // Loads on a pooled thread and calls back on the EDT; the cached details may still be a placeholder.
        data.selection.requestFullDetails { details ->
            details.singleOrNull()?.let { detail -> showAffectedFiles(data.project, detail) }
        }
    }

    private fun showAffectedFiles(project: Project, detail: VcsFullCommitDetails) {
        val title = GitHelperBundle.message(
            "action.GitHelper.Log.ShowAllAffected.dialog.title",
            detail.id.toShortString(),
        )

        val panel = LoadingCommittedChangeListPanel(project)
        // The action is commit-scoped rather than file-scoped, so there is no path to pre-select.
        panel.loadChangesInBackground { ChangelistData(VcsLogUtil.createCommittedChangeList(detail), null) }

        ChangeListViewerDialog.show(project, title, panel)
    }

    private fun collectData(e: AnActionEvent): Data {
        val project = e.project ?: return Data.Invisible
        val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return Data.Invisible

        return when (selection.commits.size) {
            0 -> Data.Invisible
            1 -> Data.Enabled(project, selection)
            else -> Data.Disabled(GitHelperBundle.message("action.GitHelper.Log.ShowAllAffected.disabled.multiple.description"))
        }
    }

    private sealed interface Data {
        data object Invisible : Data
        class Disabled(val description: @Nls String) : Data
        class Enabled(val project: Project, val selection: VcsLogCommitSelection) : Data
    }
}
