package com.max.medvedev.githelper.commitLinks

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.InvisibleHyperlinkFilterProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsProjectLog

/**
 * Makes a commit hash in the output of a terminal or a console a link to the VCS Log.
 *
 * The link is invisible: it shows only under the mouse pointer. The filter runs only for the hovered line, on a
 * background thread, so it can check the log storage. The reworked terminal and the Air TUI use this extension point.
 */
internal class CommitHashFilterProvider : InvisibleHyperlinkFilterProvider {
    override fun getFilters(project: Project, scope: GlobalSearchScope): List<Filter> = listOf(CommitHashFilter(project))
}

/**
 * Links each word of [findCommitHashes] in a line.
 *
 * A full hash gets a link only when the log storage of an open project holds it. A short hash needs a Git command to
 * expand, so the link takes its shape alone, and the click expands it.
 */
internal class CommitHashFilter(private val project: Project) : Filter {
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val ranges = findCommitHashes(line)
        if (ranges.isEmpty() || !anyLogAvailable()) return null

        val lineStart = entireLength - line.length
        val items = ranges.mapNotNull { range ->
            val hash = line.substring(range)
            if (hash.length == FULL_HASH_LENGTH && !isStoredInAnyLog(hash)) return@mapNotNull null
            Filter.ResultItem(lineStart + range.first, lineStart + range.last + 1, CommitHashHyperlinkInfo(hash))
                .also { it.isInvisibleLink = true }
        }
        return if (items.isEmpty()) null else Filter.Result(items)
    }

    private fun anyLogAvailable(): Boolean = candidateProjects(project).any { VcsProjectLog.isAvailable(it) }

    /** A log that is not loaded yet cannot answer. Then the click decides. */
    private fun isStoredInAnyLog(fullHash: String): Boolean {
        val hash = HashImpl.build(fullHash)
        val logs = candidateProjects(project).mapNotNull { VcsProjectLog.getInstance(it).dataManager }
        if (logs.isEmpty()) return true
        return logs.any { data -> data.logProviders.keys.any { root -> data.storage.containsCommit(CommitId(hash, root)) } }
    }
}

private class CommitHashHyperlinkInfo(private val hash: String) : HyperlinkInfo {
    override fun navigate(project: Project) {
        project.service<CommitHashNavigator>().showCommit(hash)
    }
}

/**
 * The projects to search for a commit: [project] first, then the other open projects.
 *
 * The terminal of an Agent Sessions window belongs to a project without the repository of the session. The
 * repository is then in another open project.
 */
internal fun candidateProjects(project: Project): List<Project> {
    val others = ProjectManager.getInstance().openProjects.filter { it != project && !it.isDisposed }
    return listOf(project) + others
}

internal const val FULL_HASH_LENGTH = 40
private const val MIN_HASH_LENGTH = 7

/**
 * A word of 7 to 40 hexadecimal digits with at least one letter: the shape of a Git commit hash.
 *
 * A digit-only word is a number, a date or an id far more often than a commit. A hex word inside a longer token, like
 * a path, a URL, a UUID or `0x1f2e3d4c`, is not a hash.
 */
private val HASH_WORD =
    Regex("""(?<![\w/\\#:-])(?<!\w\.)[0-9a-fA-F]{$MIN_HASH_LENGTH,$FULL_HASH_LENGTH}(?![\w/\\-])(?!\.\w)""")

internal fun findCommitHashes(line: String): List<IntRange> {
    return HASH_WORD.findAll(line)
        .filter { match -> match.value.any { it.isLetter() } }
        .map { it.range }
        .toList()
}
