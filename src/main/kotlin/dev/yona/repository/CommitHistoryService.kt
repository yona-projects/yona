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
class CommitHistoryService(private val repositories: RepositoryService) {

    /**
     * [revision](없으면 `HEAD`)부터 거슬러 오르는 이력의 [page]번째 쪽(0부터)이다. [path]를 주면 그 경로를 바꾼 커밋만 모은다.
     * 리비전을 풀 수 없으면 `null`이다. 저장소에 커밋이 하나도 없으면 [NoHeadException]이다.
     *
     * v1.6처럼 `page * size + size`개를 읽어 마지막 [size]개를 남긴다.
     */
    fun history(project: Project, page: Int, size: Int, revision: String?, path: String?): List<CommitInfo>? = repositories.openRepository(project).use { repository ->
        val startId = (if (revision == null) repository.resolve(Constants.HEAD) else repository.resolve(revision)) ?: return if (revision == null) throw NoHeadException() else null
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
    fun commit(project: Project, revision: String): CommitInfo? = repositories.openRepository(project).use { repository ->
        val id = repository.resolve(revision) ?: return null
        try { RevWalk(repository).use { info(it.parseCommit(id)) } } catch (e: MissingObjectException) { null }
    }

    /** 커밋의 첫 부모이다. 첫 커밋이면 `null`이다 (v1.6 `getParentCommitOf`). */
    fun parentOf(project: Project, revision: String): CommitInfo? = repositories.openRepository(project).use { repository ->
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
