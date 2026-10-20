package com.github.yonaprojects.yona.domain.vcs

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.blame.BlameResult
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.EditList
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.util.io.NullOutputStream

// yona playRepository/GitRepository.java의 BLAME_FILE_LIMIT. 변경 파일이 이보다 많으면 blame이 너무 느려 포기한다.
const val BLAME_FILE_LIMIT = 10

class LimitExceededException(message: String) : Exception(message)

// yona GitRepository.getRelatedAuthors(repository, revA, revB) 대응 — 두 리비전 사이의 diff에서
// - 수정(MODIFY)/삭제(DELETE)된 파일은 바뀌거나 지워지는 줄(Edit REPLACE/DELETE)을 revA 기준으로 blame해 그 줄을 마지막으로 쓴 사람들을,
// - 이름이 바뀐(RENAME) 파일은 옛 경로를 처음 만든 커밋의 작성자를
// 모은다. 사용자 매핑은 호출부가 이메일로 한다(yona User.findByEmail).
fun relatedAuthorEmails(repository: Repository, revA: String, revB: String): Set<String> {
    val emails = linkedSetOf<String>()
    RevWalk(repository).use { revWalk ->
        val commitA = revWalk.parseCommit(repository.resolve(revA))
        val commitB = revWalk.parseCommit(repository.resolve(revB))
        val diffs = diffEntries(repository, commitA, commitB)
        if (diffs.size > BLAME_FILE_LIMIT) {
            throw LimitExceededException(
                "Reject to get related authors from changes because of performance issue: " +
                    "The changes include ${diffs.size} files and it exceeds our limit of '$BLAME_FILE_LIMIT' files."
            )
        }
        for (diff in diffs) {
            when (diff.changeType) {
                DiffEntry.ChangeType.MODIFY, DiffEntry.ChangeType.DELETE ->
                    emails.addAll(authorEmailsFromDiffEntry(repository, diff, commitA))
                DiffEntry.ChangeType.RENAME ->
                    firstCommitAuthorEmail(repository, diff.oldPath, commitA)?.let { emails.add(it) }
                else -> {}
            }
        }
    }
    return emails
}

private fun diffEntries(repository: Repository, commitA: RevCommit, commitB: RevCommit): List<DiffEntry> =
    DiffFormatter(NullOutputStream.INSTANCE).use { formatter ->
        formatter.setRepository(repository)
        formatter.isDetectRenames = true
        formatter.scan(commitA, commitB)
    }

// 지워지거나 바뀌는 줄(Edit REPLACE/DELETE)의 원 작성자 — blame 결과의 A쪽(옛 파일) 줄 번호로 조회한다.
private fun authorEmailsFromDiffEntry(repository: Repository, diff: DiffEntry, start: RevCommit): Set<String> {
    val edits: EditList = DiffFormatter(NullOutputStream.INSTANCE).use { formatter ->
        formatter.setRepository(repository)
        formatter.toFileHeader(diff).toEditList()
    }
    val blame: BlameResult = Git(repository).use { git ->
        git.blame().setFilePath(diff.oldPath).setFollowFileRenames(true).setStartCommit(start).call()
    } ?: return emptySet()
    val emails = linkedSetOf<String>()
    for (edit in edits) {
        if (edit.type == Edit.Type.REPLACE || edit.type == Edit.Type.DELETE) {
            for (line in edit.beginA until edit.endA) {
                blame.getSourceAuthor(line)?.emailAddress?.let { emails.add(it) }
            }
        }
    }
    return emails
}

// yona getAuthorFromFirstCommit — start에서 거슬러 올라가며 해당 경로를 처음 만든(가장 오래된) 커밋의 작성자.
private fun firstCommitAuthorEmail(repository: Repository, path: String, start: RevCommit): String? =
    RevWalk(repository).use { revWalk ->
        revWalk.markStart(revWalk.parseCommit(start))
        revWalk.treeFilter = PathFilter.create(path)
        revWalk.sort(RevSort.REVERSE)
        revWalk.next()?.authorIdent?.emailAddress
    }
