package dev.yona.repository.svn

import dev.yona.domain.project.Project
import dev.yona.repository.CommitInfo
import dev.yona.repository.CommitReader
import org.springframework.stereotype.Component
import java.util.Date

/**
 * Subversion 저장소의 커밋을 읽는 [CommitReader] 구현이다. Subversion 커밋을 Git 커밋과 같은 [CommitInfo] 모양으로 바꾼다. 이메일은 없고
 * 작성자는 로그인 ID이다(v1.6 `SvnCommit`).
 */
@Component
class SvnCommitReader(private val svn: SvnRepositories) : CommitReader {

    private fun info(commit: SvnCommit) = CommitInfo(
        id = commit.id, shortId = commit.shortId, shortMessage = commit.shortMessage, message = commit.message.orEmpty(), authorName = commit.authorName.orEmpty(),
        authorEmail = "", authorDate = commit.date ?: Date(0), committerName = commit.authorName.orEmpty(), committerEmail = "", commitTime = commit.date ?: Date(0),
        parentCount = commit.parentCount
    )

    /** Subversion은 브랜치가 없어 리비전은 보지 않고, 경로를 정하면 그 경로를 바꾼 리비전만 모은다 (v1.6 `SVNRepository.getHistory`). */
    override fun history(project: Project, page: Int, size: Int, revision: String?, path: String?): List<CommitInfo>? =
        svn.history(project.owner.orEmpty(), project.name.orEmpty(), page, size, path?.takeIf { it.isNotEmpty() }).map { info(it) }

    override fun commit(project: Project, revision: String): CommitInfo? =
        revision.toLongOrNull()?.let { svn.commit(project.owner.orEmpty(), project.name.orEmpty(), it) }?.let { info(it) }

    /** 한 번에 여러 커밋을 읽는 기능은 Subversion에 없다(서명 판정에 쓰이는 Git 전용 기능이다). */
    override fun commits(project: Project, ids: Collection<String>): Map<String, CommitInfo> = emptyMap()

    /**
     * Subversion의 부모는 바로 앞 리비전이다(v1.6 `SVNRepository.getParentCommitOf`). v1.6은 첫 리비전에서도 리비전 0을 읽으려 했다(R-62-9).
     * 첫 리비전은 부모가 없다.
     */
    override fun parentOf(project: Project, revision: String): CommitInfo? =
        revision.toLongOrNull()?.takeIf { it > 1 }?.let { svn.commit(project.owner.orEmpty(), project.name.orEmpty(), it - 1) }?.let { info(it) }
}
