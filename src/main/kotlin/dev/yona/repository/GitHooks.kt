package dev.yona.repository

import dev.yona.domain.enumeration.State
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.PushedBranch
import dev.yona.domain.project.PushedBranchRepository
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.User
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.transport.PostReceiveHook
import org.eclipse.jgit.transport.PreReceiveHook
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceivePack
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.util.Date

/**
 * 푸시한 명령(`ReceiveCommand`)에서 브랜치 이름을 종류별로 모은다. v1.6 `playRepository.hooks.ReceiveCommandUtil`에 해당한다.
 */
object ReceiveCommands {

    /** 새로 만들었거나 갱신(빨리 감기가 아닌 갱신 포함)한 ref 이름이다. */
    fun pushed(commands: Collection<ReceiveCommand>): Set<String> = refNames(commands, ReceiveCommand.Type.CREATE, ReceiveCommand.Type.UPDATE, ReceiveCommand.Type.UPDATE_NONFASTFORWARD)

    /** 지운 ref 이름이다. */
    fun deleted(commands: Collection<ReceiveCommand>): Set<String> = refNames(commands, ReceiveCommand.Type.DELETE)

    /** 갱신한 ref 이름이다. */
    fun updated(commands: Collection<ReceiveCommand>): Set<String> = refNames(commands, ReceiveCommand.Type.UPDATE, ReceiveCommand.Type.UPDATE_NONFASTFORWARD)

    private fun refNames(commands: Collection<ReceiveCommand>, vararg types: ReceiveCommand.Type): Set<String> =
        commands.filter { it.type in types }.mapTo(LinkedHashSet()) { it.refName }
}

/**
 * 푸시 전후에 도는 훅을 만든다. v1.6 `RepositoryService.createPreReceiveHook`, `createPostReceiveHook`
 * (`app/playRepository/RepositoryService.java:253-270`)에 해당한다.
 *
 * 푸시 전: 내부용으로 예약된 `refs/yobi` 아래 ref에는 밀어 넣지 못하게 거부하고, 규칙 묶음이 막는 푸시를 거부한다.
 * 푸시 뒤(순서대로): 프로젝트의 마지막 푸시 시각을 갱신하고, 최근 푸시한 브랜치를 기록하고, 이슈 언급 이벤트, 풀 리퀘스트 확인, 새 커밋 알림을
 * 요청과 별개로 시작한다([PostReceiveJobs]).
 *
 * 감사 로그(X-9, D-63)에는 푸시(`git.push`), 강제 푸시(`git.force_push`), 브랜치와 태그 삭제(`git.ref_deleted`), 거부된 푸시(`git.push_rejected`)를 남긴다.
 * 복제와 가져오기는 양이 많아 남기지 않는다. 푸시는 이미 반영됐으므로 기록이 실패해도 푸시를 막지 않는다.
 */
@Component
class GitHooks(
    private val projects: ProjectRepository,
    private val pushedBranches: PushedBranchRepository,
    private val pullRequests: PullRequestRepository,
    private val transactions: TransactionTemplate,
    private val postReceiveJobs: PostReceiveJobs,
    private val accessControl: dev.yona.access.AccessControl,
    private val audit: dev.yona.domain.audit.AuditRecorder,
    private val metrics: dev.yona.observability.VcsMetrics
) {

    /**
     * 푸시 전 훅이다. 내부용 `refs/yobi` 아래에는 밀어 넣지 못하게 하고, [project]의 규칙 묶음(`decisions.md` D-45, D-46)이 막는 푸시(삭제, 강제 푸시,
     * 풀 리퀘스트를 요구하는 브랜치에 직접 푸시)를 거부한다. 규칙 묶음이 바이패스를 허용하면 프로젝트 관리자와 사이트 관리자인 [sender]는 그 규칙 묶음을 우회한다.
     * 풀 리퀘스트 병합처럼 서버가 직접 브랜치를 갱신하는 것은 이 검사를 거치지 않는다.
     */
    fun preReceive(project: Project, sender: User?): PreReceiveHook = PreReceiveHook { _, commands ->
        for (command in commands) {
            val ref = command.refName
            if (ref == "refs/yobi" || ref.startsWith("refs/yobi/")) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "refs/yobi/* is reserved for internal use")
                recordRejected(project, ref, "refs/yobi/* is reserved for internal use", "reserved")
                continue
            }
            if (!ref.startsWith(Constants.R_HEADS)) continue
            // 맞는 활성 규칙 묶음마다 따지고, 우회하지 못하는 규칙 묶음이 막으면 거부한다. 우회는 규칙 묶음마다 판정한다(바이패스를 허용한 규칙 묶음만 우회)
            val violated = project.rulesetsFor(ref).mapNotNull { rule -> rule.pushViolation(command.type)?.let { rule to it } }
                .filterNot { (rule, _) -> accessControl.canBypass(sender, project, listOf(rule)) }
            val first = violated.firstOrNull()
            if (first == null) continue
            val rule = first.first
            val reason = first.second
            val branch = ref.removePrefix(Constants.R_HEADS)
            val message = when (reason) {
                "deletion" -> "protected branch: deleting '$branch' is not allowed (ruleset '${rule.name}')"
                "update" -> "protected branch: updates to '$branch' are restricted (ruleset '${rule.name}')"
                "force" -> "protected branch: force pushes to '$branch' are not allowed (ruleset '${rule.name}')"
                else -> "protected branch: direct pushes to '$branch' are not allowed, open a pull request instead (ruleset '${rule.name}')"
            }
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, message)
            // 지표 태그는 고정된 값만 쓴다(규칙 묶음이 막은 이유)
            recordRejected(project, ref, message, if (reason in setOf("deletion", "update", "force")) "ruleset_$reason" else "ruleset_pull_request")
        }
    }

    /** 푸시 뒤 훅이다. 훅 하나가 실패해도 푸시는 이미 반영됐으므로 로그만 남기고 다음 훅을 계속 돌린다. */
    fun postReceive(project: Project, sender: User?): PostReceiveHook = PostReceiveHook { _: ReceivePack, commands ->
        listOf<Pair<String, (Collection<ReceiveCommand>) -> Unit>>(
            "last_pushed" to { updateLastPushedDate(project) },
            "recent_branch" to { updateRecentlyPushedBranch(project, it) },
            "post_receive_jobs" to { postReceiveJobs.afterPush(project, sender, it) },
            "audit" to { recordPushed(project, it) }
        ).forEach { (name, hook) ->
            try { metrics.hook(name) { hook(commands) } } catch (e: Exception) { org.slf4j.LoggerFactory.getLogger(javaClass).warn("푸시 뒤 훅이 실패했다: {}", project.name, e) }
        }
    }

    /** 거부한 푸시를 감사 기록으로 남긴다. 실패해도 푸시 처리에는 영향이 없다. */
    private fun recordRejected(project: Project, ref: String, reason: String, kind: String) {
        metrics.push("git", ref, "rejected", kind)
        audit.recordIndependent(dev.yona.domain.audit.auditProject("git.push_rejected", project, mapOf("ref" to ref, "reason" to reason), dev.yona.domain.audit.AuditOutcome.DENIED))
    }

    /** 반영된 푸시를 감사 기록으로 남긴다: 만들거나 갱신한 ref는 한 건, 강제 푸시와 삭제는 ref마다 따로 남긴다. */
    private fun recordPushed(project: Project, commands: Collection<ReceiveCommand>) {
        commands.forEach { metrics.push("git", it.refName, "ok") }
        val pushed = commands.filter { it.type != ReceiveCommand.Type.DELETE }
        if (pushed.isNotEmpty()) {
            audit.recordIndependent(dev.yona.domain.audit.auditProject("git.push", project, mapOf("refs" to pushed.map { refDetail(it) })))
        }
        commands.filter { it.type == ReceiveCommand.Type.UPDATE_NONFASTFORWARD }.forEach {
            audit.recordIndependent(dev.yona.domain.audit.auditProject("git.force_push", project, refDetail(it)))
        }
        commands.filter { it.type == ReceiveCommand.Type.DELETE }.forEach {
            val kind = if (it.refName.startsWith(Constants.R_TAGS)) "tag" else "branch"
            audit.recordIndependent(dev.yona.domain.audit.auditProject("git.ref_deleted", project, refDetail(it) + ("kind" to kind)))
        }
    }

    private fun refDetail(command: ReceiveCommand): Map<String, Any?> =
        mapOf("ref" to command.refName, "type" to command.type.name, "old" to command.oldId.name, "new" to command.newId.name)

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
    private fun updateRecentlyPushedBranch(project: Project, commands: Collection<ReceiveCommand>) {
        val projectId = requireNotNull(project.id)
        transactions.executeWithoutResult {
            val threshold = Date(System.currentTimeMillis() - DRAFT_TIME_IN_MILLIS)
            pushedBranches.findByProjectIdOrderByPushedDateDesc(projectId).filter { (it.pushedDate ?: Date(0)) < threshold }.forEach { pushedBranches.delete(it) }
            for (branch in ReceiveCommands.pushed(commands)) {
                val existing = pushedBranches.findFirstByProjectIdAndName(projectId, branch)
                if (existing != null) {
                    existing.pushedDate = Date()
                    pushedBranches.save(existing)
                } else if (!branch.contains(Constants.R_TAGS) && pullRequests.findByFromProjectIdAndFromBranchAndStateIn(projectId, branch, setOf(State.OPEN, State.REJECTED)).isEmpty()) {
                    pushedBranches.save(PushedBranch(pushedDate = Date(), name = branch, project = projects.getReferenceById(projectId)))
                }
            }
            for (branch in ReceiveCommands.deleted(commands)) {
                pushedBranches.findFirstByProjectIdAndName(projectId, branch)?.let { pushedBranches.delete(it) }
            }
        }
    }

    private companion object {
        /** 푸시한 브랜치를 "최근"으로 보는 시간이다. 한 시간이다 (v1.6 `Project.DRAFT_TIME_IN_MILLIS`). */
        const val DRAFT_TIME_IN_MILLIS = 1000L * 60 * 60
    }
}
