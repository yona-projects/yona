package dev.yona.domain.project

import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Date

/**
 * 브랜치 보호 규칙 한 건이다(추가 개발 항목 X-32, X-33, `decisions.md` D-42, D-45). GitHub의 브랜치 보호 규칙처럼 규칙이 가리키는 브랜치로 가는 병합과 푸시에
 * 조건을 건다. 규칙이 없는 브랜치는 아무것도 제한하지 않는다. 조건은 모두 꺼진 상태가 기본이고, 켠 것만 적용한다.
 *
 * @property project 규칙이 속한 프로젝트
 * @property pattern 브랜치 이름(`master`)이나 별표(`*`)를 쓴 패턴(예: release 뒤에 슬래시와 별표). 별표 하나는 슬래시를 넘지 않고 별표 두 개는 넘는다
 * @property requirePullRequest 켜면 이 브랜치에 직접 푸시하지 못하고 풀 리퀘스트로만 반영한다(푸시 검사, 2단계)
 * @property requiredApprovals 병합에 필요한 승인 수. 0이면 승인으로는 막지 않는다. 작성자 본인의 승인은 세지 않는다
 * @property requireNoChangesRequested 켜면 변경 요청이 남아 있는 풀 리퀘스트는 병합하지 못한다
 * @property requireConversationResolution 켜면 열려 있는 리뷰 스레드가 있는 풀 리퀘스트는 병합하지 못한다
 * @property requireLinearHistory 켜면 병합 커밋 방식으로 병합하지 못한다(스쿼시와 리베이스만 쓴다)
 * @property allowForcePushes 켜면 이 브랜치에 강제 푸시를 허용한다. 기본은 끔이다(푸시 검사, 2단계)
 * @property allowDeletions 켜면 이 브랜치를 지우는 것을 허용한다. 기본은 끔이다(푸시 검사, 2단계)
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
    @ColumnDefault("0")
    var requiredApprovals: Int? = 0,
    @Column(name = "require_pull_request")
    @ColumnDefault("false")
    var requirePullRequest: Boolean? = false,
    @Column(name = "require_no_changes_requested")
    @ColumnDefault("false")
    var requireNoChangesRequested: Boolean? = false,
    @Column(name = "require_conversation_resolution")
    @ColumnDefault("false")
    var requireConversationResolution: Boolean? = false,
    @Column(name = "require_linear_history")
    @ColumnDefault("false")
    var requireLinearHistory: Boolean? = false,
    @Column(name = "allow_force_pushes")
    @ColumnDefault("false")
    var allowForcePushes: Boolean? = false,
    @Column(name = "allow_deletions")
    @ColumnDefault("false")
    var allowDeletions: Boolean? = false,
    @Column(name = "enforce_admins")
    @ColumnDefault("false")
    var enforceAdmins: Boolean? = false,
    @Column(name = "created_date")
    var createdDate: Date? = null
) {

    /**
     * 푸시 명령 [type]이 이 규칙에 어긋나면 그 이유를 돌려주고, 어긋나지 않으면 `null`이다(푸시 검사, `decisions.md` D-45 2단계). 이유는 `deletion`(삭제),
     * `force`(강제 푸시), `pull-request`(풀 리퀘스트를 요구하는데 직접 푸시)이다. 브랜치를 새로 만드는 푸시는 막지 않는다.
     */
    fun pushViolation(type: org.eclipse.jgit.transport.ReceiveCommand.Type): String? = when (type) {
        org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE -> if (allowDeletions != true) "deletion" else null
        org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE_NONFASTFORWARD ->
            if (allowForcePushes != true) "force" else if (requirePullRequest == true) "pull-request" else null
        org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE -> if (requirePullRequest == true) "pull-request" else null
        else -> null
    }

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
