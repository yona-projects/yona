package dev.yona.repository

import dev.yona.domain.project.Project
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.springframework.stereotype.Service
import java.util.Date

/**
 * 커밋 한 건의 정보이다. v1.6 `playRepository.GitCommit`에 해당한다.
 *
 * @property id 커밋 전체 번호
 * @property shortId 7자리 번호
 * @property shortMessage 메시지의 첫 문단(줄바꿈은 공백)
 * @property message 메시지 전체
 * @property parentCount 부모 커밋 수. 0이면 첫 커밋이다
 */
data class CommitInfo(
    val id: String, val shortId: String, val shortMessage: String, val message: String, val authorName: String, val authorEmail: String, val authorDate: Date,
    val committerName: String, val committerEmail: String, val commitTime: Date, val parentCount: Int
)

/**
 * 커밋 이력과 커밋 하나를 읽는다. v1.6 `GitRepository.getHistory`, `getCommit`(`app/playRepository/GitRepository.java:790-830`)에 해당한다.
 */
@Service
class CommitHistoryService(private val repositories: RepositoryService, private val svn: dev.yona.repository.svn.SvnRepositories) {

    private fun isSvn(project: Project) = project.vcs == RepositoryService.VCS_SUBVERSION

    /** Subversion 커밋을 Git 커밋과 같은 모양으로 바꾼다. 이메일은 없고 작성자는 로그인 ID이다 (v1.6 `SvnCommit`). */
    private fun info(commit: dev.yona.repository.svn.SvnCommit) = CommitInfo(
        id = commit.id, shortId = commit.shortId, shortMessage = commit.shortMessage, message = commit.message.orEmpty(), authorName = commit.authorName.orEmpty(),
        authorEmail = "", authorDate = commit.date ?: Date(0), committerName = commit.authorName.orEmpty(), committerEmail = "", commitTime = commit.date ?: Date(0),
        parentCount = commit.parentCount
    )

    /**
     * [revision](없으면 `HEAD`)부터 거슬러 오르는 이력의 [page]번째 쪽(0부터)이다. [path]를 주면 그 경로를 바꾼 커밋만 모은다.
     * 리비전을 풀 수 없으면 `null`이다. 저장소에 커밋이 하나도 없어 `HEAD`를 풀 수 없을 때도 `null`이다(v1.6 `getHistory`가 null을 돌려주고 `NoHeadException` 분기는 도달하지 않는다).
     *
     * v1.6처럼 `page * size + size`개를 읽어 마지막 [size]개를 남긴다.
     */
    fun history(project: Project, page: Int, size: Int, revision: String?, path: String?): List<CommitInfo>? {
        // Subversion은 브랜치가 없어 리비전은 보지 않고, 경로를 정하면 그 경로를 바꾼 리비전만 모은다 (v1.6 `SVNRepository.getHistory`).
        if (isSvn(project)) return svn.history(project.owner.orEmpty(), project.name.orEmpty(), page, size, path?.takeIf { it.isNotEmpty() }).map { info(it) }
        return gitHistory(project, page, size, revision, path)
    }

    private fun gitHistory(project: Project, page: Int, size: Int, revision: String?, path: String?): List<CommitInfo>? = repositories.openRepository(project).use { repository ->
        val startId = (if (revision == null) repository.resolve(Constants.HEAD) else repository.resolve(revision)) ?: return null
        val start = RevWalk(repository).use { it.parseCommit(startId) }
        val log = Git(repository).log().add(start)
        if (!path.isNullOrEmpty()) log.addPath(path)
        val recent = ArrayDeque<RevCommit>()
        for (commit in log.setMaxCount(page * size + size).call()) {
            if (recent.size >= size) recent.removeFirst()
            recent.addLast(commit)
        }
        recent.map { info(it) }
    }

    /** [revision]의 커밋이다. 없으면 `null`이다(풀 수 없거나 커밋이 아닌 객체 포함). */
    fun commit(project: Project, revision: String): CommitInfo? {
        if (isSvn(project)) return revision.toLongOrNull()?.let { svn.commit(project.owner.orEmpty(), project.name.orEmpty(), it) }?.let { info(it) }
        return gitCommit(project, revision)
    }

    private fun gitCommit(project: Project, revision: String): CommitInfo? = repositories.openRepository(project).use { repository ->
        val id = repository.resolve(revision) ?: return null
        try { RevWalk(repository).use { info(it.parseCommit(id)) } } catch (e: MissingObjectException) { null }
    }

    /** 커밋의 첫 부모이다. 첫 커밋이면 `null`이다. Subversion은 바로 앞 리비전이다 (v1.6 `getParentCommitOf`). */
    fun parentOf(project: Project, revision: String): CommitInfo? {
        // Subversion의 부모는 바로 앞 리비전이다 (v1.6 `SVNRepository.getParentCommitOf`).
        if (isSvn(project)) return revision.toLongOrNull()?.let { svn.commit(project.owner.orEmpty(), project.name.orEmpty(), it - 1) }?.let { info(it) }
        return gitParentOf(project, revision)
    }

    private fun gitParentOf(project: Project, revision: String): CommitInfo? = repositories.openRepository(project).use { repository ->
        val id = repository.resolve(revision) ?: return null
        RevWalk(repository).use { walk ->
            val commit = walk.parseCommit(id)
            if (commit.parentCount > 0) info(walk.parseCommit(commit.getParent(0).id)) else null
        }
    }

    private fun info(commit: RevCommit): CommitInfo = CommitInfo(
        id = commit.name, shortId = commit.abbreviate(7).name(), shortMessage = commit.shortMessage, message = commit.fullMessage,
        authorName = commit.authorIdent.name, authorEmail = commit.authorIdent.emailAddress, authorDate = commit.authorIdent.`when`,
        committerName = commit.committerIdent.name, committerEmail = commit.committerIdent.emailAddress, commitTime = Date(commit.commitTime * 1000L), parentCount = commit.parentCount
    )

    /** 저장소에 커밋이 하나도 없다(`HEAD`를 풀 수 없다). */
    class NoHeadException : RuntimeException("저장소에 커밋이 없습니다.")
}
