package dev.yona.repository

import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.domain.vcs.RefUpdate
import dev.yona.domain.vcs.RefUpdateType
import org.springframework.stereotype.Component

/**
 * 푸시를 막은 한 건이다. [update]를 거부하고 클라이언트에 [message]를 보여 준다.
 *
 * @property kind 지표와 감사에 쓰는 거부 이유의 고정된 값(`reserved`, `ruleset_force`, `ruleset_signed_commits` 등)
 */
data class PushRejection(val update: RefUpdate, val message: String, val kind: String)

/**
 * 푸시 전 검사이다. v1.6 `RepositoryService.createPreReceiveHook`(`app/playRepository/RepositoryService.java:253-270`)에 해당한다. 저장소 종류와
 * 무관하며, 종류마다 다른 것은 서명을 확인할 커밋을 구하는 일뿐이라 호출한 쪽이 넘긴다(Mercurial 도입을 위한 공통화 3단계, D-86).
 *
 * 내부용으로 예약된 `refs/yona` 아래 ref에는 밀어 넣지 못하게 거부하고, 규칙 묶음(`decisions.md` D-45, D-46)이 막는 푸시를 거부한다.
 * 거부한 푸시는 지표와 감사 기록(`<종류>.push_rejected`)에 남긴다.
 */
@Component
class PushPolicy(
    private val accessControl: dev.yona.access.AccessControl,
    private val audit: dev.yona.domain.audit.AuditRecorder,
    private val metrics: dev.yona.observability.VcsMetrics
) {

    /**
     * [updates]마다 [project]의 규칙을 따져 막아야 하는 것을 모은다. 막지 않는 변경은 결과에 없다.
     *
     * 규칙 묶음이 바이패스를 허용하면 프로젝트 관리자와 사이트 관리자인 [sender]는 그 규칙 묶음을 우회한다. 풀 리퀘스트 병합처럼 서버가 직접 브랜치를
     * 갱신하는 것은 이 검사를 거치지 않는다.
     *
     * @param vcsTag 지표와 감사에 쓰는 종류 이름(`git`, `hg`)
     * @param unverifiedCommits 서명 커밋을 요구하는 규칙이 있을 때 이번 푸시가 더하는 커밋 가운데 Verified 서명이 아닌 것의 (짧은 번호, 이유 코드) 목록을 구한다
     */
    fun evaluate(
        project: Project, sender: User?, vcsTag: String, updates: List<RefUpdate>,
        unverifiedCommits: (RefUpdate) -> List<Pair<String, String>>
    ): List<PushRejection> {
        val rejections = mutableListOf<PushRejection>()
        for (update in updates) {
            val ref = update.ref
            if (ref == "refs/yona" || ref.startsWith("refs/yona/")) {
                rejections += reject(project, vcsTag, update, "refs/yona/* is reserved for internal use", "reserved")
                continue
            }
            if (!ref.startsWith(HEADS)) continue
            // 맞는 활성 규칙 묶음마다 따지고, 우회하지 못하는 규칙 묶음이 막으면 거부한다. 우회는 규칙 묶음마다 판정한다(바이패스를 허용한 규칙 묶음만 우회)
            val violated = project.rulesetsFor(ref).mapNotNull { rule -> rule.pushViolation(update.type)?.let { rule to it } }
                .filterNot { (rule, _) -> accessControl.canBypass(sender, project, listOf(rule)) }
            val first = violated.firstOrNull()
            if (first == null) {
                unverified(project, sender, vcsTag, update, unverifiedCommits)?.let { rejections += it }
                continue
            }
            val rule = first.first
            val reason = first.second
            val branch = ref.removePrefix(HEADS)
            val message = when (reason) {
                "deletion" -> "protected branch: deleting '$branch' is not allowed (ruleset '${rule.name}')"
                "update" -> "protected branch: updates to '$branch' are restricted (ruleset '${rule.name}')"
                "force" -> "protected branch: force pushes to '$branch' are not allowed (ruleset '${rule.name}')"
                else -> "protected branch: direct pushes to '$branch' are not allowed, open a pull request instead (ruleset '${rule.name}')"
            }
            // 지표 태그는 고정된 값만 쓴다(규칙 묶음이 막은 이유)
            rejections += reject(project, vcsTag, update, message, if (reason in setOf("deletion", "update", "force")) "ruleset_$reason" else "ruleset_pull_request")
        }
        return rejections
    }

    /**
     * 서명 커밋을 요구하는 규칙 묶음이 있는 브랜치로 푸시하는 새 커밋이 모두 Verified 서명인지 확인하고, 아니면 거부한다(추가 개발 항목 X-4, `decisions.md` D-65).
     * 바이패스를 허용한 규칙 묶음은 프로젝트 관리자와 사이트 관리자가 우회한다. 삭제는 커밋이 없으므로 검사하지 않는다.
     */
    private fun unverified(
        project: Project, sender: User?, vcsTag: String, update: RefUpdate, unverifiedCommits: (RefUpdate) -> List<Pair<String, String>>
    ): PushRejection? {
        if (update.type == RefUpdateType.DELETE) return null
        val rule = project.rulesetsFor(update.ref).filter { it.requireSignedCommits == true }.firstOrNull { !accessControl.canBypass(sender, project, listOf(it)) } ?: return null
        val bad = try {
            unverifiedCommits(update)
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("서명 검사 중 오류가 나 푸시를 거부한다: {}", project.name, e)
            listOf("?" to "error")
        }
        if (bad.isEmpty()) return null
        val shown = bad.take(MAX_LISTED_UNVERIFIED).joinToString(", ") { (id, reason) -> "$id ($reason)" } + if (bad.size > MAX_LISTED_UNVERIFIED) ", ..." else ""
        val message = "protected branch: commits to '${update.ref.removePrefix(HEADS)}' must have verified signatures (ruleset '${rule.name}'): $shown"
        return reject(project, vcsTag, update, message, "ruleset_signed_commits")
    }

    /** 거부한 푸시를 지표와 감사 기록으로 남기고 거부 값을 만든다. 실패해도 푸시 처리에는 영향이 없다. */
    private fun reject(project: Project, vcsTag: String, update: RefUpdate, message: String, kind: String): PushRejection {
        metrics.push(vcsTag, update.ref, "rejected", kind)
        audit.recordIndependent(dev.yona.domain.audit.auditProject("$vcsTag.push_rejected", project, mapOf("ref" to update.ref, "reason" to message), dev.yona.domain.audit.AuditOutcome.DENIED))
        return PushRejection(update, message, kind)
    }

    private companion object {
        const val HEADS = "refs/heads/"

        /** 서명 검사에서 거부 메시지에 늘어놓을 커밋 수의 상한이다. */
        const val MAX_LISTED_UNVERIFIED = 5
    }
}
