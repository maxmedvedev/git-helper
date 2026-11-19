package com.max.medvedev.githelper.commitLinks

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsProjectLog
import com.max.medvedev.githelper.GitHelperBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens the commit of a [CommitHashFilter] link in the VCS Log. */
@Service(Service.Level.PROJECT)
internal class CommitHashNavigator(private val project: Project, private val scope: CoroutineScope) {
    fun showCommit(hash: String) {
        scope.launch {
            if (!showInFirstLogThatHolds(hash)) {
                LOG.info("No VCS Log of an open project holds the commit $hash")
                VcsNotifier.getInstance(project).notifyError(
                    "git.helper.commit.link.not.found",
                    GitHelperBundle.message("commit.link.not.found.title"),
                    GitHelperBundle.message("commit.link.not.found.message", hash),
                )
            }
        }
    }

    /**
     * Searches the log of [project], then the logs of the other open projects. The search waits for the log of
     * [project] to load. It does not create the log of another project, so it reads only the logs that exist.
     */
    private suspend fun showInFirstLogThatHolds(hash: String): Boolean {
        for (candidate in candidateProjects(project)) {
            if (!VcsProjectLog.isAvailable(candidate)) continue
            val logManager = if (candidate == project) VcsProjectLog.awaitLogIsReady(candidate)
            else VcsProjectLog.getInstance(candidate).logManager
            val data = logManager?.dataManager ?: continue

            val commitId = withContext(Dispatchers.IO) { resolveCommitId(data, hash) } ?: continue
            if (candidate != project) {
                withContext(Dispatchers.EDT) { ProjectUtil.focusProjectWindow(candidate, true) }
            }
            // `showRevisionAsync` uses a log tab that holds the commit, or a tab without filters.
            return VcsProjectLog.getInstance(candidate).showRevisionAsync(commitId.root, commitId.hash, null).await()
        }
        return false
    }
}

/**
 * The commit that a root of [data] holds under [hash], or null. A full hash is read from the log storage. A short
 * hash needs the log provider of the root to expand it, and that runs a Git command.
 */
private fun resolveCommitId(data: VcsLogData, hash: String): CommitId? {
    if (hash.length == FULL_HASH_LENGTH) {
        val fullHash = HashImpl.build(hash)
        return data.logProviders.keys.map { root -> CommitId(fullHash, root) }.firstOrNull { data.storage.containsCommit(it) }
    }
    for ((root, provider) in data.logProviders) {
        val fullHash = try {
            provider.resolveReference(hash, root)
        }
        catch (e: VcsException) {
            LOG.debug("Cannot resolve $hash in $root", e)
            null
        } ?: continue
        return CommitId(fullHash, root)
    }
    return null
}

private val LOG = logger<CommitHashNavigator>()
