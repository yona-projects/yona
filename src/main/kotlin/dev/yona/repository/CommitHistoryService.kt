package dev.yona.repository

import dev.yona.domain.project.Project
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
 * @property signature GPG 서명과 서명 대상 바이트(X-4). 서명이 없는 커밋과 Subversion은 `null`이다
 */
data class CommitInfo(
    val id: String, val shortId: String, val shortMessage: String, val message: String, val authorName: String, val authorEmail: String, val authorDate: Date,
    val committerName: String, val committerEmail: String, val commitTime: Date, val parentCount: Int, val signature: dev.yona.domain.gpgkey.SignedPayload? = null
)

/**
 * 커밋 이력과 커밋 하나를 읽는다. v1.6 `GitRepository.getHistory`, `getCommit`(`app/playRepository/GitRepository.java:790-830`)에 해당한다.
 * 저장소 종류별 읽기는 [CommitReader]가 맡고([VcsBackends]가 프로젝트의 종류로 고른다), 이 서비스는 지표를 더한다.
 */
@Service
class CommitHistoryService(
    private val backends: VcsBackends,
    private val metrics: dev.yona.observability.VcsMetrics
) {

    private fun reader(project: Project) = backends.commits(project.vcs)

    /**
     * [revision](없으면 `HEAD`)부터 거슬러 오르는 이력의 [page]번째 쪽(0부터)이다. [path]를 주면 그 경로를 바꾼 커밋만 모은다.
     * 리비전을 풀 수 없으면 `null`이다. 저장소에 커밋이 하나도 없어 `HEAD`를 풀 수 없을 때도 `null`이다(v1.6 `getHistory`가 null을 돌려주고 `NoHeadException` 분기는 도달하지 않는다).
     *
     * 앞 쪽만큼 건너뛰고 [size]개를 읽는다(v1.6은 `page * size + size`개를 읽어 마지막 [size]개를 남겨 마지막 쪽이 앞 쪽과 겹쳤다, R-40).
     * Subversion은 브랜치가 없어 리비전은 보지 않는다.
     */
    fun history(project: Project, page: Int, size: Int, revision: String?, path: String?): List<CommitInfo>? =
        metrics.operation(project.vcs, "history") { reader(project).history(project, page, size, revision, path) }

    /** [revision]의 커밋이다. 없으면 `null`이다(풀 수 없거나 커밋이 아닌 객체 포함). */
    fun commit(project: Project, revision: String): CommitInfo? = metrics.operation(project.vcs, "commit") { reader(project).commit(project, revision) }

    /**
     * 커밋 번호 [ids]에 해당하는 커밋을 한 번에 읽는다(저장소를 한 번만 연다). 풀 수 없거나 커밋이 아닌 번호는 결과에서 빠진다. Subversion은 빈 맵이다.
     * 풀 리퀘스트 커밋 목록처럼 여러 커밋의 서명 판정이 필요할 때 쓴다(X-4).
     */
    fun commits(project: Project, ids: Collection<String>): Map<String, CommitInfo> = reader(project).commits(project, ids)

    /** 커밋의 첫 부모이다. 첫 커밋이면 `null`이다. Subversion은 바로 앞 리비전이다 (v1.6 `getParentCommitOf`). */
    fun parentOf(project: Project, revision: String): CommitInfo? = reader(project).parentOf(project, revision)

    /** 저장소에 커밋이 하나도 없다(`HEAD`를 풀 수 없다). */
    class NoHeadException : RuntimeException("저장소에 커밋이 없습니다.")
}
