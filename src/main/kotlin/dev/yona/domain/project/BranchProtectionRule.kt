package dev.yona.domain.project

import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Date

/**
 * 브랜치 보호 규칙 한 건이다(추가 개발 항목 X-32, `decisions.md` D-42). GitHub의 브랜치 보호 규칙처럼 규칙이 가리키는 브랜치로 가는 풀 리퀘스트의 병합에
 * 필요한 승인 수를 요구한다. 규칙이 없는 브랜치는 승인 수와 변경 요청으로 막지 않는다.
 *
 * @property project 규칙이 속한 프로젝트
 * @property pattern 브랜치 이름(`master`)이나 별표(`*`)를 쓴 패턴(예: release 뒤에 슬래시와 별표). 별표 하나는 슬래시를 넘지 않고 별표 두 개는 슬래시도 넘는다
 * @property requiredApprovals 병합에 필요한 승인 수. 1 이상이다. 작성자 본인의 승인은 세지 않는다
 * @property enforceAdmins 켜면 프로젝트 관리자와 사이트 관리자도 이 규칙을 우회하지 못한다. 기본은 끔이다(GitHub의 "관리자에게도 적용")
 * @property createdDate 만든 시각
 */
@Entity
@Table(
    name = "branch_protection_rule",
    uniqueConstraints = [UniqueConstraint(name = "uq_branch_protection_rule_project_pattern", columnNames = ["project_id", "pattern"])],
    indexes = [Index(name = "ix_branch_protection_rule_project", columnList = "project_id")]
)
class BranchProtectionRule(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    @Column(name = "pattern", length = 255)
    var pattern: String? = null,
    @Column(name = "required_approvals")
    @ColumnDefault("1")
    var requiredApprovals: Int? = 1,
    @Column(name = "enforce_admins")
    @ColumnDefault("false")
    var enforceAdmins: Boolean? = false,
    @Column(name = "created_date")
    var createdDate: Date? = null
) {

    /** 패턴에 `*`가 없으면 `true`이다. 이름이 정확히 같은 규칙이 패턴 규칙보다 우선한다. */
    val isExact: Boolean get() = pattern?.contains('*') != true

    /**
     * [branch]가 이 규칙이 가리키는 브랜치이면 `true`이다. `refs/heads/` 접두사는 떼고 비교한다.
     */
    fun matches(branch: String?): Boolean {
        val name = branch?.removePrefix("refs/heads/") ?: return false
        val rule = pattern ?: return false
        if (isExact) return name == rule
        val regex = buildString {
            var i = 0
            while (i < rule.length) {
                if (rule[i] == '*') {
                    if (i + 1 < rule.length && rule[i + 1] == '*') { append(".*"); i += 2 } else { append("[^/]*"); i++ }
                } else {
                    append(Regex.escape(rule[i].toString())); i++
                }
            }
        }
        return Regex(regex).matches(name)
    }
}

interface BranchProtectionRuleRepository : JpaRepository<BranchProtectionRule, Long> {
    fun findByProjectIdOrderByPatternAsc(projectId: Long): List<BranchProtectionRule>
    fun findByProjectIdAndPattern(projectId: Long, pattern: String): BranchProtectionRule?
}
