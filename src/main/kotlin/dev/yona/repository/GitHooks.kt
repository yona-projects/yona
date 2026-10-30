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
 * 푸시 전: 내부용으로 예약된 `refs/yobi` 아래 ref에는 밀어 넣지 못하게 거부하고, 브랜치 보호 규칙이 막는 푸시를 거부한다.
 * 푸시 뒤(순서대로): 프로젝트의 마지막 푸시 시각을 갱신하고, 최근 푸시한 브랜치를 기록하고, 이슈 언급 이벤트, 풀 리퀘스트 확인, 새 커밋 알림을
 * 요청과 별개로 시작한다([PostReceiveJobs]).
 */
@Component
class GitHooks(
    private val projects: ProjectRepository,
    private val pushedBranches: PushedBranchRepository,
    private val pullRequests: PullRequestRepository,
    private val transactions: TransactionTemplate,
    private val postReceiveJobs: PostReceiveJobs,
    private val accessControl: dev.yona.access.AccessControl
) {

    /**
     * 푸시 전 훅이다. 내부용 `refs/yobi` 아래에는 밀어 넣지 못하게 하고, [project]의 브랜치 보호 규칙(`decisions.md` D-45)이 막는 푸시(삭제, 강제 푸시,
     * 풀 리퀘스트를 요구하는 브랜치에 직접 푸시)를 거부한다. 규칙이 "관리자에게도 적용"이 아니면 프로젝트 관리자와 사이트 관리자인 [sender]는 규칙을 우회한다.
     * 풀 리퀘스트 병합처럼 서버가 직접 브랜치를 갱신하는 것은 이 검사를 거치지 않는다.
     */
    fun preReceive(project: Project, sender: User?): PreReceiveHook = PreReceiveHook { _, commands ->
        for (command in commands) {
            val ref = command.refName
            if (ref == "refs/yobi" || ref.startsWith("refs/yobi/")) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "refs/yobi/* is reserved for internal use")
                continue
            }
            if (!ref.startsWith(Constants.R_HEADS)) continue
            val rule = project.protectionFor(ref) ?: continue
            val reason = rule.pushViolation(command.type) ?: continue
            if (accessControl.canBypassReview(sender, project, rule)) continue
            val branch = ref.removePrefix(Constants.R_HEADS)
            val message = when (reason) {
                "deletion" -> "protected branch: deleting '$branch' is not allowed"
                "force" -> "protected branch: force pushes to '$branch' are not allowed"
                else -> "protected branch: direct pushes to '$branch' are not allowed, open a pull request instead"
            }
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, message)
        }
    }

    /** 푸시 뒤 훅이다. 훅 하나가 실패해도 푸시는 이미 반영됐으므로 로그만 남기고 다음 훅을 계속 돌린다. */
    fun postReceive(project: Project, sender: User?): PostReceiveHook = PostReceiveHook { _: ReceivePack, commands ->
        listOf<(Collection<ReceiveCommand>) -> Unit>(
            { updateLastPushedDate(project) },
            { updateRecentlyPushedBranch(project, it) },
            { postReceiveJobs.afterPush(project, sender, it) }
        ).forEach { hook ->
            try { hook(commands) } catch (e: Exception) { org.slf4j.LoggerFactory.getLogger(javaClass).warn("푸시 뒤 훅이 실패했다: {}", project.name, e) }
        }
    }

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
