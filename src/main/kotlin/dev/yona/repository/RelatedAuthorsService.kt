package dev.yona.repository

import dev.yona.domain.user.User
import dev.yona.domain.user.UserLookup
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.util.io.NullOutputStream
import org.springframework.stereotype.Component

/** 바뀐 파일이 너무 많아 관련 작성자를 구하지 않기로 했을 때 던진다. */
class LimitExceededException(message: String) : RuntimeException(message)

/**
 * 두 리비전 사이에서 바뀐 줄을 마지막으로 고친 사람들을 찾는다. v1.6 `GitRepository.getRelatedAuthors`
 * (`app/playRepository/GitRepository.java:1065-1221`)에 해당한다. 풀 리퀘스트를 받을 때 그 코드를 고쳐 본 사람에게 알리려고 쓴다.
 *
 * 수정, 삭제된 파일은 이전 리비전에서 `blame`해 지워지거나 바뀐 줄의 마지막 작성자를 모으고, 이름이 바뀐 파일은 이전 경로의 첫 커밋 작성자를 더한다.
 * 파일이 [FILE_LIMIT]개를 넘으면 성능을 위해 [LimitExceededException]이다. 작성자의 이메일에 맞는 사용자가 없으면 건너뛴다.
 */
@Component
class RelatedAuthorsService(private val userLookup: UserLookup) {

    fun find(repository: Repository, revA: String, revB: String): Set<User> {
        val authors = linkedMapOf<Long, User>()
        fun add(user: User?) { user?.id?.let { authors.putIfAbsent(it, user) } }

        RevWalk(repository).use { walk ->
            val commitA = walk.parseCommit(repository.resolve(revA))
            val commitB = walk.parseCommit(repository.resolve(revB))
            val diffs = entries(repository, commitA, commitB)
            if (diffs.size > FILE_LIMIT) {
                throw LimitExceededException(
                    "Reject to get related authors from changes because of performance issue: The changes include ${diffs.size} files and it exceeds our limit of '$FILE_LIMIT' files."
                )
            }
            for (diff in diffs) {
                if (diff.changeType == ChangeType.MODIFY || diff.changeType == ChangeType.DELETE) fromBlame(repository, diff, commitA).forEach { add(it) }
                if (diff.changeType == ChangeType.RENAME) add(firstAuthor(repository, diff.oldPath, commitA))
            }
        }
        return authors.values.toSet()
    }

    private fun entries(repository: Repository, a: RevCommit, b: RevCommit): List<DiffEntry> =
        DiffFormatter(NullOutputStream.INSTANCE).use { formatter ->
            formatter.setRepository(repository)
            formatter.isDetectRenames = true
            formatter.scan(a, b)
        }

    private fun fromBlame(repository: Repository, diff: DiffEntry, start: RevCommit): List<User?> =
        DiffFormatter(NullOutputStream.INSTANCE).use { formatter ->
            formatter.setRepository(repository)
            val edits = formatter.toFileHeader(diff).toEditList()
            val blame = Git(repository).blame().setFilePath(diff.oldPath).setFollowFileRenames(true).setStartCommit(start).call()
            val users = mutableListOf<User?>()
            for (edit in edits) {
                if (edit.type == Edit.Type.REPLACE || edit.type == Edit.Type.DELETE) {
                    for (i in edit.beginA until edit.endA) users += byIdent(blame.getSourceAuthor(i))
                }
            }
            users
        }

    private fun firstAuthor(repository: Repository, path: String, start: RevCommit): User? = RevWalk(repository).use { walk ->
        walk.markStart(start)
        walk.treeFilter = PathFilter.create(path)
        walk.sort(RevSort.REVERSE)
        walk.next()?.let { byIdent(it.authorIdent) }
    }

    private fun byIdent(ident: PersonIdent?): User? = ident?.let { userLookup.findByEmail(it.emailAddress) }

    private companion object {
        /** 이보다 많은 파일이 바뀌면 관련 작성자를 구하지 않는다 (v1.6 `BLAME_FILE_LIMIT`). */
        const val FILE_LIMIT = 10
    }
}
