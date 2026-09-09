package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.event.HgPostReceiveEvent
import com.github.yonaprojects.yona.domain.event.RelatedPullRequestMergeEvent
import com.github.yonaprojects.yona.domain.gpgkey.GpgSignatureVerifier
import com.github.yonaprojects.yona.domain.gpgkey.GpgVerificationStatus
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.api.HgHook
import io.github.search5.hg4j.lib.HgRepository as NativeHgRepository
import io.github.search5.hg4j.storage.Revlog
import io.github.search5.hg4j.util.NodeIdUtil
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import java.io.File
import java.time.Duration
import java.time.Instant

private const val BOOKMARKS_NAMESPACE = "bookmarks"
private val HG_RECENTLY_PUSHED_WINDOW: Duration = Duration.ofHours(1)

// git 쪽 domain/vcs/GitPushHooks.kt(BranchProtectionPreReceiveHook/
// YonaPostReceiveHook)에 정확히 대응하는 Mercurial 버전. Mercurial의 실제 "브랜치 이동" 이벤트는
// unbundle(changegroup 반영)이 아니라 pushkey(namespace="bookmarks") wire 명령에서만 일어난다 —
// hg4j에 새로 추가한 Wire1Commands.pushkey(repo, args, prePushkeyHooks, postPushkeyHooks)/
// HgHttpWireServer.registerPre|PostPushkeyHook()/HgSshWireServer.registerPre|PostPushkeyHook()가
// 그 지점의 훅이다(HgHook.run(Map<String,Object>) — context: namespace/key(북마크 이름)/old(이전
// 노드 hex, 없으면 "")/new(새 노드 hex, 없으면 "")/repository(네이티브 io.github.search5.hg4j.
// lib.HgRepository) — Git의 ReceiveCommand(refName/oldId/newId)와 동등한 정보량).
//
// 설계 결정(HgRepository.kt 참고)에 따라 yona의 git 스타일 "브랜치"는 Mercurial의
// bookmark에 매핑되므로, 이 파일의 모든 판정은 namespace가 "bookmarks"일 때만 의미를 가진다
// (다른 namespace의 pushkey — 예: phases — 는 그냥 통과시킨다).

enum class HgBookmarkChangeType { CREATE, UPDATE, UPDATE_NONFASTFORWARD, DELETE }

// yona domain/vcs/PushedBranch.kt 등 VCS 무관 소비자에게 "북마크 X가 A에서 B로 이동했다"를
// 전달하는 값 객체 — Git의 ReceiveCommand에 대응.
data class HgBookmarkMove(
    val name: String,
    val oldNodeHex: String,
    val newNodeHex: String,
    val changeType: HgBookmarkChangeType
)

// hg4j의 porcelain HgCommit(io.github.search5.hg4j.api.HgCommit)에는 부모 리비전이 노출되지
// 않는다(HgCommit.java — revision/nodeId/author/message 등 값 필드만 있음, ParentsCommand도
// 작업 디렉터리 부모만 노출) — 그래서 git의 RevWalk.markUninteresting()에 대응하는 조상 관계
// 판정은 Wire2Commands.changelog()와 동일한 저수준 Revlog(00changelog.i/.d)를 직접 열어
// IndexRecord.getParent1()/getParent2()로 계산한다(hg4j 자신도 이미 이 저수준 API로 동일한
// 계산을 한다 — 새 API를 hg4j에 추가할 필요가 없다).
internal object HgBookmarkAncestry {
    fun changelogRevlogOf(nativeRepo: NativeHgRepository): Revlog {
        val idx = File(nativeRepo.storeDir, "00changelog.i")
        val dat = File(nativeRepo.storeDir, "00changelog.d")
        return nativeRepo.getRevlog(idx, dat)
    }

    private fun revisionOf(revlog: Revlog, hex: String): Int {
        if (hex.isBlank()) return -1
        return try {
            revlog.findRevision(NodeIdUtil.fromHex(hex))
        } catch (e: Exception) {
            -1
        }
    }

    private fun ancestorsInclusive(revlog: Revlog, rev: Int): Set<Int> {
        if (rev < 0) return emptySet()
        val visited = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        stack.addLast(rev)
        while (stack.isNotEmpty()) {
            val r = stack.removeLast()
            if (r < 0 || !visited.add(r)) continue
            val rec = revlog.getIndexRecord(r)
            stack.addLast(rec.parent1)
            stack.addLast(rec.parent2)
        }
        return visited
    }

    // Git의 ReceiveCommand.Type(CREATE/UPDATE/UPDATE_NONFASTFORWARD/DELETE) 대응 판정.
    fun classify(revlog: Revlog, oldHex: String, newHex: String): HgBookmarkChangeType {
        val oldRev = revisionOf(revlog, oldHex)
        val newRev = revisionOf(revlog, newHex)
        return when {
            oldRev < 0 && newRev >= 0 -> HgBookmarkChangeType.CREATE
            newRev < 0 -> HgBookmarkChangeType.DELETE
            ancestorsInclusive(revlog, newRev).contains(oldRev) -> HgBookmarkChangeType.UPDATE
            else -> HgBookmarkChangeType.UPDATE_NONFASTFORWARD
        }
    }

    // git의 "oldId..newId" 범위(oldId가 zeroId면 newId부터 도달 가능한 전체 커밋) 대응 — 이
    // 북마크 이동이 새로 들여오는 리비전 번호 목록(오래된 순). old가 new의 조상이 아니어도(강제
    // push) "old의 조상집합에 속하지 않는, new로부터 도달 가능한" 리비전들로 근사한다 — 실제
    // hg 자신도 force-push된 changeset을 그냥 "새 changeset"으로 취급한다.
    fun newlyIntroducedRevisions(revlog: Revlog, oldHex: String, newHex: String): List<Int> {
        val newRev = revisionOf(revlog, newHex)
        if (newRev < 0) return emptyList()
        val oldRev = revisionOf(revlog, oldHex)
        val excluded = ancestorsInclusive(revlog, oldRev)
        val result = LinkedHashSet<Int>()
        val visited = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        stack.addLast(newRev)
        while (stack.isNotEmpty()) {
            val r = stack.removeLast()
            if (r < 0 || !visited.add(r) || excluded.contains(r)) continue
            result.add(r)
            val rec = revlog.getIndexRecord(r)
            stack.addLast(rec.parent1)
            stack.addLast(rec.parent2)
        }
        return result.sorted()
    }
}

/**
 * git의 BranchProtectionPreReceiveHook(GitPushHooks.kt)과 최대한 동일한
 * 검사 순서/사유 메시지를 구현한 Mercurial(pushkey) 버전. yona의 "브랜치" == bookmark이므로
 * pushkey의 key(bookmark 이름)를 ProtectedBranch.branchPattern에 매칭한다.
 *
 * git과의 차이: (1) 판정 결과를 command.setResult()가 아니라 HgHook.run()의 boolean 반환값으로
 * 전달한다 — 실제 hg wire pushkey 응답 자체가 "성공/실패" 두 값뿐이라(Wire1Commands.pushkey()의
 * javadoc 참고, "output is always empty") 거부 사유 문자열이 클라이언트까지 전달되지 않는다(로그로만
 * 남긴다) — 이는 지어낸 제약이 아니라 실제 hg wire protocol 자체의 한계다("clone/fetch 거부처럼
 * 지저분한 메시지"와 동일한 선례). (2) CREATE/UPDATE/
 * UPDATE_NONFASTFORWARD/DELETE 판정에 JGit의 ReceiveCommand.Type 대신 [HgBookmarkAncestry]를 쓴다.
 */
class HgBranchProtectionPrePushkeyHook(
    private val project: Project,
    private val pusher: User?,
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val gpgSignatureVerifier: GpgSignatureVerifier
) : HgHook {
    private val logger = LoggerFactory.getLogger(HgBranchProtectionPrePushkeyHook::class.java)

    override fun run(context: MutableMap<String, Any>): Boolean {
        if (context["namespace"] != BOOKMARKS_NAMESPACE) return true

        val projectId = project.id ?: return true
        val rules = protectedBranchRepository.findByProjectId(projectId)
        if (rules.isEmpty()) return true

        val branch = context["key"] as? String ?: return true
        val rule = rules.firstOrNull { it.matches(branch) } ?: return true

        if (rule.adminsCanBypass && isProjectManager()) return true

        val nativeRepo = context["repository"] as? NativeHgRepository ?: return true
        val oldHex = (context["old"] as? String).orEmpty()
        val newHex = (context["new"] as? String).orEmpty()

        val revlog = try {
            HgBookmarkAncestry.changelogRevlogOf(nativeRepo)
        } catch (e: Exception) {
            logger.warn(
                "changelog revlog를 열지 못해 브랜치 보호 검사를 건너뜁니다(project=${project.owner}/${project.name}): ${e.message}"
            )
            return true
        }
        val changeType = HgBookmarkAncestry.classify(revlog, oldHex, newHex)

        if (rule.requirePullRequest && changeType != HgBookmarkChangeType.DELETE) {
            return reject(branch, "이 브랜치는 Pull Request를 통해서만 갱신할 수 있습니다(require_pull_request).")
        }
        if (rule.disallowDelete && changeType == HgBookmarkChangeType.DELETE) {
            return reject(branch, "이 브랜치는 삭제할 수 없습니다(disallow_delete).")
        }
        if (rule.disallowForcePush && changeType == HgBookmarkChangeType.UPDATE_NONFASTFORWARD) {
            return reject(branch, "이 브랜치는 강제 push할 수 없습니다(disallow_force_push).")
        }
        if (rule.requireSignedCommits && changeType != HgBookmarkChangeType.DELETE) {
            val unverifiedHex = findUnverifiedCommit(nativeRepo, revlog, oldHex, newHex)
            if (unverifiedHex != null) {
                return reject(
                    branch,
                    "이 브랜치는 서명된 커밋만 push할 수 있습니다(require_signed_commits) — " +
                        "커밋 ${unverifiedHex.take(8)}가 서명되지 않았거나 서명 검증에 실패했습니다."
                )
            }
        }
        val allowedPushers = rule.restrictedLoginIds()
        if (allowedPushers.isNotEmpty() && pusher?.loginId !in allowedPushers) {
            return reject(branch, "이 브랜치는 지정된 사용자만 push할 수 있습니다(restrict_push_to).")
        }
        return true
    }

    private fun reject(branch: String, reason: String): Boolean {
        logger.info(
            "bookmark '$branch' protected: $reason (project=${project.owner}/${project.name}, pusher=${pusher?.loginId ?: "anonymous"})"
        )
        return false
    }

    private fun findUnverifiedCommit(nativeRepo: NativeHgRepository, revlog: Revlog, oldHex: String, newHex: String): String? {
        val newRevisions = HgBookmarkAncestry.newlyIntroducedRevisions(revlog, oldHex, newHex)
        if (newRevisions.isEmpty()) return null
        return Hg.open(nativeRepo.directory).use { hg ->
            val commitsByRevision = hg.log().call().associateBy { it.revision }
            for (rev in newRevisions) {
                val commit = commitsByRevision[rev] ?: continue
                if (gpgSignatureVerifier.verify(commit) != GpgVerificationStatus.VERIFIED) {
                    return@use commit.nodeId.toHex()
                }
            }
            null
        }
    }

    private fun isProjectManager(): Boolean {
        val userId = pusher?.id ?: return false
        val projectId = project.id ?: return false
        return projectUserRepository.findByProjectIdAndUserId(projectId, userId)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }
}

/**
 * git의 YonaPostReceiveHook(UpdateLastPushedDate/NotifyPushedCommits/
 * PullRequestCheck)에 대응하는 Mercurial(pushkey) 버전. git의 PostReceiveHook은 push 하나의
 * 커맨드 전체를 한 번에 받지만, hg4j의 Wire1Commands.pushkey()는 북마크 하나당 한 번씩 호출되므로
 * 이 훅도 북마크 하나의 이동만 다룬다 — 여러 북마크를 한 번의 `hg push`로 옮기면 이 훅이 그만큼
 * 여러 번 실행되어 실질적으로 git 쪽과 동일한 최종 상태(모든 북마크에 대해 알림/웹훅/
 * PushedBranch 갱신)에 도달한다.
 */
class HgYonaPostPushkeyHook(
    private val project: Project,
    private val pusher: User,
    private val projectRepository: ProjectRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val pushedBranchRepository: PushedBranchRepository,
    private val eventPublisher: ApplicationEventPublisher,
    // git의 yona.git.push_hook.duration과 대칭인 계측 지점.
    private val meterRegistry: MeterRegistry
) : HgHook {
    private val logger = LoggerFactory.getLogger(HgYonaPostPushkeyHook::class.java)

    override fun run(context: MutableMap<String, Any>): Boolean {
        if (context["namespace"] != BOOKMARKS_NAMESPACE) return true
        val branch = context["key"] as? String ?: return true
        val nativeRepo = context["repository"] as? NativeHgRepository ?: return true
        val oldHex = (context["old"] as? String).orEmpty()
        val newHex = (context["new"] as? String).orEmpty()

        val sample = Timer.start(meterRegistry)
        try {
            val revlog = HgBookmarkAncestry.changelogRevlogOf(nativeRepo)
            val changeType = HgBookmarkAncestry.classify(revlog, oldHex, newHex)
            val move = HgBookmarkMove(branch, oldHex, newHex, changeType)

            updateLastPushedDate()
            eventPublisher.publishEvent(HgPostReceiveEvent(project, pusher, move))

            // git YonaPostReceiveHook.notifyRelatedPullRequestsForUpdatedBranches()와 동일하게
            // 생성이 아닌 갱신(UPDATE/UPDATE_NONFASTFORWARD)에 대해서만 관련 PR 재검사를 발행한다.
            if (changeType == HgBookmarkChangeType.UPDATE || changeType == HgBookmarkChangeType.UPDATE_NONFASTFORWARD) {
                eventPublisher.publishEvent(RelatedPullRequestMergeEvent(project, branch, pusher))
            }
            if (changeType == HgBookmarkChangeType.DELETE) {
                cleanupPullRequestsForDeletedBranch(branch)
            }
            updateRecentlyPushedBranch(branch, changeType)
        } catch (e: Exception) {
            // post-hook 실패가 이미 성공적으로 적용된 push 자체를 되돌리진 않는다 — git
            // YonaPostReceiveHook과 동일한 계약(알림/부가기능 실패는 push 성공에 영향을 주지 않음).
            logger.error(
                "Hg push post-hook 처리 중 오류(project=${project.owner}/${project.name}, bookmark=$branch)", e
            )
        } finally {
            sample.stop(meterRegistry.timer("yona.hg.push_hook.duration"))
        }
        return true
    }

    private fun updateLastPushedDate() {
        project.lastPushedDate = Instant.now()
        projectRepository.save(project)
    }

    private fun cleanupPullRequestsForDeletedBranch(branch: String) {
        val related = pullRequestRepository.findRelatedPullRequests(project, branch)
        if (related.isNotEmpty()) {
            pullRequestRepository.deleteAll(related)
        }
    }

    private fun updateRecentlyPushedBranch(branch: String, changeType: HgBookmarkChangeType) {
        removeOldPushedBranches()

        if (changeType == HgBookmarkChangeType.DELETE) {
            pushedBranchRepository.findByProjectAndName(project, branch).ifPresent { pushedBranchRepository.delete(it) }
            return
        }

        val existing = pushedBranchRepository.findByProjectAndName(project, branch).orElse(null)
        if (existing != null) {
            existing.pushedDate = Instant.now()
            pushedBranchRepository.save(existing)
            return
        }

        // yona isNotExistsPushedBranch() 대응(git YonaPostReceiveHook과 동일) — 이미 이 브랜치를
        // fromBranch로 하는 PR이 있으면 별도로 PushedBranch를 만들지 않는다.
        if (pullRequestRepository.existsByFromProjectAndFromBranch(project, branch)) {
            return
        }

        pushedBranchRepository.save(PushedBranch(name = branch, pushedDate = Instant.now(), project = project))
    }

    private fun removeOldPushedBranches() {
        val cutoff = Instant.now().minus(HG_RECENTLY_PUSHED_WINDOW)
        val old = pushedBranchRepository.findByProjectAndPushedDateBefore(project, cutoff)
        if (old.isNotEmpty()) {
            pushedBranchRepository.deleteAll(old)
        }
    }
}
