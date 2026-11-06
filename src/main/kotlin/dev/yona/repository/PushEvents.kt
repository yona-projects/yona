package dev.yona.repository

import dev.yona.domain.enumeration.State
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.PushedBranch
import dev.yona.domain.project.PushedBranchRepository
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.User
import dev.yona.domain.vcs.RefUpdate
import dev.yona.domain.vcs.RefUpdateType
import dev.yona.domain.vcs.RefUpdates
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.util.Date

/**
 * 푸시가 반영된 뒤에 도는 일이다. v1.6 `RepositoryService.createPostReceiveHook`(`app/playRepository/RepositoryService.java:253-270`)에 해당한다.
 * 저장소 종류와 무관하며 종류마다 다른 것은 푸시를 [RefUpdate] 목록으로 바꾸는 일뿐이다(Mercurial 도입을 위한 공통화 3단계, D-86).
 *
 * 순서대로: 프로젝트의 마지막 푸시 시각을 갱신하고, 최근 푸시한 브랜치를 기록하고, 이슈 언급 이벤트, 풀 리퀘스트 확인, 새 커밋 알림을 요청과 별개로
 * 시작하고([PostReceiveJobs]), 감사 기록을 남긴다. 훅 하나가 실패해도 푸시는 이미 반영됐으므로 로그만 남기고 다음 훅을 계속 돌린다.
 *
 * 감사 로그(X-9, D-63)에는 푸시(`<종류>.push`), 강제 푸시(`<종류>.force_push`), 브랜치와 태그 삭제(`<종류>.ref_deleted`)를 남긴다. 복제와 가져오기는
 * 양이 많아 남기지 않는다. 기록이 실패해도 푸시를 막지 않는다.
 */
@Component
class PushEvents(
    private val projects: ProjectRepository,
    private val pushedBranches: PushedBranchRepository,
    private val pullRequests: PullRequestRepository,
    private val transactions: TransactionTemplate,
    private val postReceiveJobs: PostReceiveJobs,
    private val audit: dev.yona.domain.audit.AuditRecorder,
    private val metrics: dev.yona.observability.VcsMetrics
) {

    /**
     * 반영된 푸시 [updates]의 뒤처리를 한다.
     *
     * @param vcsTag 지표와 감사에 쓰는 종류 이름(`git`, `hg`)
     */
    fun afterPush(project: Project, sender: User?, vcsTag: String, updates: List<RefUpdate>) {
        listOf<Pair<String, (List<RefUpdate>) -> Unit>>(
            "last_pushed" to { updateLastPushedDate(project) },
            "recent_branch" to { updateRecentlyPushedBranch(project, it) },
            "post_receive_jobs" to { postReceiveJobs.afterPush(project, sender, it) },
            "audit" to { recordPushed(project, vcsTag, it) }
        ).forEach { (name, hook) ->
            try { metrics.hook(name) { hook(updates) } } catch (e: Exception) { org.slf4j.LoggerFactory.getLogger(javaClass).warn("푸시 뒤 훅이 실패했다: {}", project.name, e) }
        }
    }

    /** 반영된 푸시를 감사 기록으로 남긴다: 만들거나 갱신한 ref는 한 건, 강제 푸시와 삭제는 ref마다 따로 남긴다. */
    private fun recordPushed(project: Project, vcsTag: String, updates: List<RefUpdate>) {
        updates.forEach { metrics.push(vcsTag, it.ref, "ok") }
        val pushed = updates.filter { it.type != RefUpdateType.DELETE }
        if (pushed.isNotEmpty()) {
            audit.recordIndependent(dev.yona.domain.audit.auditProject("$vcsTag.push", project, mapOf("refs" to pushed.map { refDetail(it) })))
        }
        updates.filter { it.type == RefUpdateType.FORCE_UPDATE }.forEach {
            audit.recordIndependent(dev.yona.domain.audit.auditProject("$vcsTag.force_push", project, refDetail(it)))
        }
        updates.filter { it.type == RefUpdateType.DELETE }.forEach {
            val kind = if (it.ref.startsWith(TAGS)) "tag" else "branch"
            audit.recordIndependent(dev.yona.domain.audit.auditProject("$vcsTag.ref_deleted", project, refDetail(it) + ("kind" to kind)))
        }
    }

    /** 감사 기록의 ref 정보이다. `type`은 Git의 푸시 명령 종류 이름(`CREATE`, `UPDATE`, `UPDATE_NONFASTFORWARD`, `DELETE`)을 그대로 쓴다. */
    private fun refDetail(update: RefUpdate): Map<String, Any?> =
        mapOf("ref" to update.ref, "type" to auditType(update.type), "old" to update.old, "new" to update.new)

    private fun auditType(type: RefUpdateType): String = if (type == RefUpdateType.FORCE_UPDATE) "UPDATE_NONFASTFORWARD" else type.name

    private fun updateLastPushedDate(project: Project) {
        transactions.executeWithoutResult {
            projects.findById(requireNotNull(project.id)).ifPresent {
                it.lastPushedDate = Date()
                projects.save(it)
            }
        }
    }

    /**
     * 오래된 푸시 기록(한 시간 전)을 지우고, 푸시한 브랜치를 기록하고(태그와 이미 풀 리퀘스트가 있는 브랜치는 새로 만들지 않는다),
     * 지운 브랜치의 기록을 지운다 (v1.6 `UpdateRecentlyPushedBranch`).
     */
    private fun updateRecentlyPushedBranch(project: Project, updates: List<RefUpdate>) {
        val projectId = requireNotNull(project.id)
        transactions.executeWithoutResult {
            val threshold = Date(System.currentTimeMillis() - DRAFT_TIME_IN_MILLIS)
            pushedBranches.findByProjectIdOrderByPushedDateDesc(projectId).filter { (it.pushedDate ?: Date(0)) < threshold }.forEach { pushedBranches.delete(it) }
            for (branch in RefUpdates.pushed(updates)) {
                val existing = pushedBranches.findFirstByProjectIdAndName(projectId, branch)
                if (existing != null) {
                    existing.pushedDate = Date()
                    pushedBranches.save(existing)
                } else if (!branch.contains(TAGS) && pullRequests.findByFromProjectIdAndFromBranchAndStateIn(projectId, branch, setOf(State.OPEN, State.REJECTED)).isEmpty()) {
                    pushedBranches.save(PushedBranch(pushedDate = Date(), name = branch, project = projects.getReferenceById(projectId)))
                }
            }
            for (branch in RefUpdates.deleted(updates)) {
                pushedBranches.findFirstByProjectIdAndName(projectId, branch)?.let { pushedBranches.delete(it) }
            }
        }
    }

    private companion object {
        const val TAGS = "refs/tags/"

        /** 푸시한 브랜치를 "최근"으로 보는 시간이다. 한 시간이다 (v1.6 `Project.DRAFT_TIME_IN_MILLIS`). */
        const val DRAFT_TIME_IN_MILLIS = 1000L * 60 * 60
    }
}
