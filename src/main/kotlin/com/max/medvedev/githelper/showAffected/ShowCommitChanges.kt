package com.max.medvedev.githelper.showAffected

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog
import com.intellij.openapi.vcs.changes.ui.LoadingCommittedChangeListPanel
import com.intellij.openapi.vcs.changes.ui.LoadingCommittedChangeListPanel.ChangelistData
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogColorManagerFactory
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import com.intellij.vcs.log.ui.frame.VcsLogCommitSelectionListenerForDetails
import com.intellij.vcs.log.util.VcsLogUtil
import com.max.medvedev.githelper.GitHelperBundle
import javax.swing.JComponent

/*
 * Candidates for public platform API.
 *
 * Everything that touches @ApiStatus.Internal platform code is kept in this file, behind two functions that only take
 * and return public types. Both are lifted from `com.intellij.vcs.log.ui.actions.history.ShowAllAffectedFromHistoryAction`,
 * so once the platform exposes them (e.g. in `VcsLogUtil`), that action can call them too, and this file can be deleted.
 *
 * Internal API used here:
 *  - `LoadingCommittedChangeListPanel(Project, Function<Disposable, JComponent>)`
 *  - `LoadingCommittedChangeListPanel.setCommitDetailsProportion`
 *  - `CommitDetailsPanel`
 *  - `VcsLogCommitSelectionListenerForDetails`, including `onDetailsLoaded`
 */

/**
 * Shows the changes of [detail] in a changes browser: the tree of the affected files, with the commit details panel of the
 * VCS Log under it. It opens as a tab of the changes browser tool window, or as a dialog if the IDE is set up so.
 *
 * @param toSelect the file to select in the changes tree once it is loaded, if any
 */
@RequiresEdt
internal fun showCommitChanges(
    project: Project,
    logData: VcsLogData,
    detail: VcsFullCommitDetails,
    toSelect: FilePath? = null,
) {
    // The platform version would use VcsLogBundle's "dialog.title.paths.affected.by.commit", which reads the same.
    val title = GitHelperBundle.message("action.GitHelper.Log.ShowAllAffected.dialog.title", detail.id.toShortString())

    val panel = LoadingCommittedChangeListPanel(project) { parent ->
        createCommitDetailsComponent(project, logData, detail, parent)
    }
    panel.setCommitDetailsProportion(DETAILS_PROPORTION)
    panel.loadChangesInBackground { ChangelistData(VcsLogUtil.createCommittedChangeList(detail), toSelect) }

    // The tab (or the dialog) owns the panel, which in turn owns the details panel and its selection listener.
    ChangeListViewerDialog.show(project, title, panel)
}

/**
 * Builds the same commit details panel that the log tool window and the file history panel use: the hash, the author,
 * the date, the branches and the tags of [detail], with links that navigate to the main log.
 * The details are already loaded, so the panel shows them at once and loads nothing.
 *
 * @param parent disposes the panel and the listener that feeds it
 */
@RequiresEdt
internal fun createCommitDetailsComponent(
    project: Project,
    logData: VcsLogData,
    detail: VcsFullCommitDetails,
    parent: Disposable,
): JComponent {
    val detailsPanel = CommitDetailsListPanel(project, parent) {
        CommitDetailsPanel { commitId -> VcsProjectLog.showRevisionInMainLog(project, commitId.root, commitId.hash) }
    }
    val colorManager = VcsLogColorManagerFactory.create(logData.logProviders.keys)
    val listener = VcsLogCommitSelectionListenerForDetails(logData, colorManager, detailsPanel, parent)
    listener.onDetailsLoaded(listOf(logData.storage.getCommitIndex(detail.id, detail.root)), listOf(detail))
    return detailsPanel
}

/** Part of the panel height that the changes browser gets; the commit details get the rest. */
private const val DETAILS_PROPORTION = 0.7f
