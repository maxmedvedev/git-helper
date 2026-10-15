package com.max.medvedev.githelper.showAffected

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogColorManagerFactory
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import com.intellij.vcs.log.ui.frame.VcsLogCommitSelectionListenerForDetails
import com.max.medvedev.githelper.GitHelperBundle
import org.jetbrains.annotations.Nls

/**
 * Shows all files affected by the commit selected in the VCS Log, in a standalone changes browser.
 *
 * The platform only offers this for file history (`VcsHistory.ShowAllAffected` and
 * `Vcs.Log.ShowAllAffected`); this action brings it to the main log, where a whole commit is selected.
 * Modelled after `com.intellij.vcs.log.ui.actions.history.ShowAllAffectedFromHistoryAction`: the change
 * list is built from the details the log already loads, so no extra Git process is needed, and the
 * bottom part of the panel is a `CommitDetailsPanel` — the same commit presentation the Log tool
 * window and the file history panel show, with the hash, the author, the date, the branches and the tags.
 *
 * The changes browser itself is built by [showCommitChanges], which keeps all the internal platform API in one place.
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
            // The action is commit-scoped rather than file-scoped, so there is no path to pre-select.
            details.singleOrNull()?.let { detail ->
                showCommitChanges(data.project, data.logData, detail)
            }
        }
    }

    /**
     * Builds the same commit details panel that the log tool window and the file history panel use.
     * The action already loaded the commit, so the panel gets the details at once and loads nothing.
     */
    private fun createCommitDetailsPanel(
        project: Project,
        logData: VcsLogData,
        detail: VcsFullCommitDetails,
        parent: Disposable,
    ): CommitDetailsListPanel {
        val detailsPanel = CommitDetailsListPanel(project, parent) {
            CommitDetailsPanel { commitId -> VcsProjectLog.showRevisionInMainLog(project, commitId.root, commitId.hash) }
        }
        val colorManager = VcsLogColorManagerFactory.create(logData.logProviders.keys)
        val listener = VcsLogCommitSelectionListenerForDetails(logData, colorManager, detailsPanel, parent)
        listener.onDetailsLoaded(listOf(logData.storage.getCommitIndex(detail.id, detail.root)), listOf(detail))
        return detailsPanel
    }

    private fun collectData(e: AnActionEvent): Data {
        val project = e.project ?: return Data.Invisible
        val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return Data.Invisible
        // Captured here rather than in the requestFullDetails callback: the event is dead by then.
        val logData = e.getData(VcsLogInternalDataKeys.LOG_DATA) ?: return Data.Invisible

        return when (selection.commits.size) {
            0 -> Data.Invisible
            1 -> Data.Enabled(project, logData, selection)
            else -> Data.Disabled(GitHelperBundle.message("action.GitHelper.Log.ShowAllAffected.disabled.multiple.description"))
        }
    }

    private sealed interface Data {
        data object Invisible : Data
        class Disabled(val description: @Nls String) : Data
        class Enabled(val project: Project, val logData: VcsLogData, val selection: VcsLogCommitSelection) : Data
    }

    private companion object {
        /** Part of the dialog height that the changes browser gets; the commit details get the rest. */
        private const val DETAILS_PROPORTION = 0.7f
    }
}
