package dev.yona.domain.project

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 설정 폼이 보내는 브랜치 보호 규칙 한 줄이다(`decisions.md` D-44, D-45). 조건은 모두 꺼진 것이 기본이다.
 *
 * @property pattern 브랜치 이름이나 패턴
 * @property approvals 필요한 승인 수 문자열(검증 전). 비우면 0으로 본다
 */
data class ProtectionRuleInput(
    val pattern: String?,
    val approvals: String? = "0",
    val requirePullRequest: Boolean = false,
    val requireNoChangesRequested: Boolean = false,
    val requireConversationResolution: Boolean = false,
    val requireLinearHistory: Boolean = false,
    val allowForcePushes: Boolean = false,
    val allowDeletions: Boolean = false,
    val enforceAdmins: Boolean = false
)

/**
 * 브랜치 보호 규칙을 만들고 지운다(추가 개발 항목 X-32, `decisions.md` D-42).
 */
@Service
class BranchProtectionService(private val rules: BranchProtectionRuleRepository) {

    /**
     * 입력이 올바르면 `true`이다. 패턴은 공백 없는 1~255자이고 `refs/`로 시작하지 않으며(브랜치 짧은 이름이나 `*` 패턴만 받는다),
     * 필요한 승인 수는 0~[MAX_APPROVALS]의 정수이다(비우면 0이다).
     */
    fun isValid(pattern: String?, approvals: String?): Boolean {
        val trimmed = pattern?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.length > MAX_PATTERN_LENGTH || trimmed.any { it.isWhitespace() || it.isISOControl() } || trimmed.startsWith("refs/")) return false
        val count = if (approvals.isNullOrBlank()) 0 else approvals.trim().toIntOrNull() ?: return false
        return count in 0..MAX_APPROVALS
    }

    /**
     * 규칙을 만든다. 같은 패턴이 이미 있으면 그 규칙의 조건을 고친다. 입력은 [isValid]를 통과해야 한다.
     * 프로젝트가 들고 있는 규칙 목록도 함께 갱신해 같은 트랜잭션 안의 판정이 바로 따른다.
     */
    @Transactional
    fun save(project: Project, input: ProtectionRuleInput): BranchProtectionRule {
        val name = requireNotNull(input.pattern).trim()
        val approvals = if (input.approvals.isNullOrBlank()) 0 else input.approvals.trim().toInt()
        val existing = rules.findByProjectIdAndPattern(requireNotNull(project.id), name)
        val rule = existing ?: BranchProtectionRule(project = project, pattern = name, createdDate = Date())
        rule.requiredApprovals = approvals
        rule.requirePullRequest = input.requirePullRequest
        rule.requireNoChangesRequested = input.requireNoChangesRequested
        rule.requireConversationResolution = input.requireConversationResolution
        rule.requireLinearHistory = input.requireLinearHistory
        rule.allowForcePushes = input.allowForcePushes
        rule.allowDeletions = input.allowDeletions
        rule.enforceAdmins = input.enforceAdmins
        val saved = rules.save(rule)
        if (existing == null) project.branchProtectionRules.add(saved)
        return saved
    }

    /** 승인 수와 관리자 적용만 정하는 짧은 형태이다. 나머지 조건은 모두 끈다. */
    @Transactional
    fun save(project: Project, pattern: String, approvals: Int, enforceAdmins: Boolean): BranchProtectionRule =
        save(project, ProtectionRuleInput(pattern, approvals.toString(), enforceAdmins = enforceAdmins))

    /**
     * 설정 폼이 보낸 규칙 목록으로 [project]의 규칙을 통째로 맞춘다(`decisions.md` D-44). 목록에 없는 규칙은 지우고 같은 패턴은 고치며 새 패턴은 만든다.
     * 입력은 [violations]가 비어 있어야 한다.
     */
    @Transactional
    fun replaceAll(project: Project, inputs: List<ProtectionRuleInput>) {
        val wanted = inputs.associateBy { requireNotNull(it.pattern).trim() }
        project.branchProtectionRules.filter { it.pattern !in wanted.keys }.forEach { rule ->
            project.branchProtectionRules.removeIf { it.id == rule.id }
            rules.delete(rule)
        }
        wanted.values.forEach { save(project, it) }
    }

    /** 규칙 목록의 오류를 설명하는 문구 모음이다. 올바르면 비어 있다. 입력이 올바르지 않거나 같은 패턴이 두 번 나오면 오류이다. */
    fun violations(inputs: List<ProtectionRuleInput>): List<String> {
        val errors = mutableListOf<String>()
        if (inputs.any { !isValid(it.pattern, it.approvals) }) errors += "error.invalid"
        val patterns = inputs.mapNotNull { it.pattern?.trim() }
        if (patterns.size != patterns.toSet().size) errors += "error.invalid"
        return errors
    }

    /** [project]의 규칙 [id]를 지운다. 없거나 다른 프로젝트의 규칙이면 `false`이다. */
    @Transactional
    fun delete(project: Project, id: Long): Boolean {
        val rule = rules.findById(id).orElse(null) ?: return false
        if (rule.project?.id != project.id) return false
        project.branchProtectionRules.removeIf { it.id == rule.id }
        rules.delete(rule)
        return true
    }

    companion object {
        const val MAX_PATTERN_LENGTH = 255
        const val MAX_APPROVALS = 100
    }
}
