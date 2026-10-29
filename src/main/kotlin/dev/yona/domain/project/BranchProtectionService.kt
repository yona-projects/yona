package dev.yona.domain.project

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 브랜치 보호 규칙을 만들고 지운다(추가 개발 항목 X-32, `decisions.md` D-42).
 */
@Service
class BranchProtectionService(private val rules: BranchProtectionRuleRepository) {

    /**
     * 입력이 올바르면 `true`이다. 패턴은 공백 없는 1~255자이고 `refs/`로 시작하지 않으며(브랜치 짧은 이름이나 `*` 패턴만 받는다),
     * 필요한 승인 수는 1~[MAX_APPROVALS]의 정수이다.
     */
    fun isValid(pattern: String?, approvals: String?): Boolean {
        val trimmed = pattern?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.length > MAX_PATTERN_LENGTH || trimmed.any { it.isWhitespace() || it.isISOControl() } || trimmed.startsWith("refs/")) return false
        val count = approvals?.trim()?.toIntOrNull() ?: return false
        return count in 1..MAX_APPROVALS
    }

    /**
     * 규칙을 만든다. 같은 패턴이 이미 있으면 그 규칙의 승인 수와 적용 범위를 고친다. 입력은 [isValid]를 통과해야 한다.
     * 프로젝트가 들고 있는 규칙 목록도 함께 갱신해 같은 트랜잭션 안의 판정이 바로 따른다.
     */
    @Transactional
    fun save(project: Project, pattern: String, approvals: Int, enforceAdmins: Boolean): BranchProtectionRule {
        val name = pattern.trim()
        val existing = rules.findByProjectIdAndPattern(requireNotNull(project.id), name)
        if (existing != null) {
            existing.requiredApprovals = approvals
            existing.enforceAdmins = enforceAdmins
            return rules.save(existing)
        }
        val created = rules.save(BranchProtectionRule(project = project, pattern = name, requiredApprovals = approvals, enforceAdmins = enforceAdmins, createdDate = Date()))
        project.branchProtectionRules.add(created)
        return created
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
