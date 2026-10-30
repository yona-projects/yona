package dev.yona.domain.project

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 규칙 묶음 편집 화면이 보내는 입력이다(`decisions.md` D-46). 조건은 GitHub 기본에 맞춰 삭제 제한과 강제 푸시 차단만 켠 것이 기본이다.
 *
 * @property approvals 필요한 승인 수 문자열(검증 전). 비우면 0으로 본다
 */
data class RulesetInput(
    val name: String?,
    val enforcement: String? = Ruleset.ACTIVE,
    val pattern: String?,
    val approvals: String? = "0",
    val bypassAdmins: Boolean = false,
    val restrictDeletions: Boolean = true,
    val blockForcePushes: Boolean = true,
    val requirePullRequest: Boolean = false,
    val requireLinearHistory: Boolean = false,
    val requireNoChangesRequested: Boolean = false,
    val requireConversationResolution: Boolean = false
)

/**
 * 규칙 묶음을 만들고 고치고 지운다(추가 개발 항목 X-34, `decisions.md` D-46). 프로젝트가 들고 있는 규칙 묶음 목록도 함께 갱신해 같은 트랜잭션 안의 판정이 바로 따른다.
 */
@Service
class RulesetService(private val rulesets: RulesetRepository) {

    /**
     * 입력이 올바르지 않은 항목의 이름 목록이다. 올바르면 비어 있다. 이름은 공백이 아닌 1~255자이고 프로젝트 안에서 유일해야 하며(고치는 중이면 [editingId] 자신은 제외),
     * 적용 상태는 `active`나 `disabled`이다. 대상 패턴은 공백 없는 1~255자이고 `refs/`로 시작하지 않으며(브랜치 짧은 이름이나 `*` 패턴만 받는다),
     * 필요한 승인 수는 0~[MAX_APPROVALS]의 정수이다(비우면 0이다).
     */
    fun violations(project: Project, input: RulesetInput, editingId: Long? = null): List<String> = buildList {
        val name = input.name?.trim().orEmpty()
        if (name.isEmpty() || name.length > MAX_LENGTH || name.any { it.isISOControl() }) add("name")
        else if (rulesets.findByProjectIdAndName(requireNotNull(project.id), name)?.takeIf { it.id != editingId } != null) add("name")
        if (input.enforcement != Ruleset.ACTIVE && input.enforcement != Ruleset.DISABLED) add("enforcement")
        if (!isValidPattern(input.pattern)) add("pattern")
        if (!isValidApprovals(input.approvals)) add("approvals")
    }

    /** 대상 패턴이 올바르면 `true`이다. */
    fun isValidPattern(pattern: String?): Boolean {
        val trimmed = pattern?.trim().orEmpty()
        return trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH && trimmed.none { it.isWhitespace() || it.isISOControl() } && !trimmed.startsWith("refs/")
    }

    /** 필요한 승인 수 문자열이 올바르면 `true`이다. 비어 있으면 0으로 본다. */
    fun isValidApprovals(approvals: String?): Boolean {
        val count = if (approvals.isNullOrBlank()) 0 else approvals.trim().toIntOrNull() ?: return false
        return count in 0..MAX_APPROVALS
    }

    /** 규칙 묶음을 만든다. 입력은 [violations]가 비어 있어야 한다. */
    @Transactional
    fun create(project: Project, input: RulesetInput): Ruleset {
        val created = rulesets.save(apply(Ruleset(project = project, createdDate = Date()), input))
        project.rulesets.add(created)
        return created
    }

    /** [project]의 규칙 묶음 [id]를 고친다. 없거나 다른 프로젝트의 규칙 묶음이면 `null`이다. 입력은 [violations]가 비어 있어야 한다. */
    @Transactional
    fun update(project: Project, id: Long, input: RulesetInput): Ruleset? {
        val ruleset = find(project, id) ?: return null
        return rulesets.save(apply(ruleset, input))
    }

    /** [project]의 규칙 묶음 [id]를 지운다. 없거나 다른 프로젝트의 규칙 묶음이면 `false`이다. */
    @Transactional
    fun delete(project: Project, id: Long): Boolean {
        val ruleset = find(project, id) ?: return false
        project.rulesets.removeIf { it.id == ruleset.id }
        rulesets.delete(ruleset)
        return true
    }

    /** [project]의 규칙 묶음 [id]이다. 없거나 다른 프로젝트의 것이면 `null`이다. */
    fun find(project: Project, id: Long): Ruleset? = rulesets.findById(id).orElse(null)?.takeIf { it.project?.id == project.id }

    private fun apply(ruleset: Ruleset, input: RulesetInput): Ruleset = ruleset.also {
        it.name = requireNotNull(input.name).trim()
        it.enforcement = input.enforcement
        it.pattern = requireNotNull(input.pattern).trim()
        it.requiredApprovals = if (input.approvals.isNullOrBlank()) 0 else input.approvals.trim().toInt()
        it.bypassAdmins = input.bypassAdmins
        it.restrictDeletions = input.restrictDeletions
        it.blockForcePushes = input.blockForcePushes
        it.requirePullRequest = input.requirePullRequest
        it.requireLinearHistory = input.requireLinearHistory
        it.requireNoChangesRequested = input.requireNoChangesRequested
        it.requireConversationResolution = input.requireConversationResolution
    }

    companion object {
        const val MAX_LENGTH = 255
        const val MAX_APPROVALS = 100
    }
}
