package dev.yona.domain.project

import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Date

/**
 * 규칙 묶음 한 건이다(추가 개발 항목 X-34, `decisions.md` D-46). GitHub의 규칙 묶음처럼 이름, 적용 상태, 바이패스, 대상 브랜치, 규칙 묶음으로 이루어지며, 대상 브랜치로 가는
 * 병합과 푸시에 조건을 건다. 대상에 맞는 활성 규칙 묶음이 없는 브랜치는 아무것도 제한하지 않는다. 한 브랜치에 여러 규칙 묶음이 겹치면 합산한다([Project.rulesetsFor]).
 *
 * 바이패스(◐)와 대상(◐)은 지금 수준이다. 바이패스는 프로젝트 관리자와 사이트 관리자의 우회 가능 여부 하나이고 대상은 패턴 하나이다(완성은 X-35).
 *
 * @property project 규칙 묶음이 속한 프로젝트
 * @property name 규칙 묶음 이름. 프로젝트 안에서 유일하다
 * @property enforcement 적용 상태이다. [ACTIVE]는 적용하고 [DISABLED]는 지우지 않고 적용만 끈다. 비어 있으면 적용으로 본다
 * @property pattern 대상 브랜치 이름(`master`)이나 별표(`*`)를 쓴 패턴(예: release 뒤에 슬래시와 별표). 별표 하나는 슬래시를 넘지 않고 별표 두 개는 넘는다
 * @property bypassAdmins 켜면 프로젝트 관리자와 사이트 관리자가 이 규칙 묶음을 우회한다. 기본은 끔이다(GitHub의 빈 바이패스 목록)
 * @property restrictUpdates 켜면 바이패스 대상이 아닌 사용자는 이 브랜치를 갱신하지 못한다(GitHub의 Restrict updates). 직접 푸시와 풀 리퀘스트 병합이 모두 막히고, 바이패스가 비어 있으면 아무도 갱신하지 못한다. 기본은 끔이다
 * @property restrictDeletions 켜면 이 브랜치를 지우는 것을 막는다. 기본은 켬이다(GitHub 기본)
 * @property blockForcePushes 켜면 이 브랜치에 강제 푸시를 막는다. 기본은 켬이다(GitHub 기본)
 * @property requirePullRequest 켜면 이 브랜치에 직접 푸시하지 못하고 풀 리퀘스트로만 반영한다
 * @property requireLinearHistory 켜면 병합 커밋 방식으로 병합하지 못한다(스쿼시와 리베이스만 쓴다)
 * @property requiredApprovals 병합에 필요한 승인 수. 0이면 승인으로는 막지 않는다. 작성자 본인의 승인은 세지 않는다
 * @property requireNoChangesRequested 켜면 변경 요청이 남아 있는 풀 리퀘스트는 병합하지 못한다
 * @property requireConversationResolution 켜면 열려 있는 리뷰 스레드가 있는 풀 리퀘스트는 병합하지 못한다
 * @property requireSignedCommits 켜면 이 브랜치로 푸시하는 커밋과 이 브랜치로 병합하는 풀 리퀘스트의 커밋이 모두 Verified 서명이어야 한다(X-4, `decisions.md` D-65). 서버가 직접 만드는 병합 커밋은 서명하지 못하므로 검사하지 않는다
 * @property createdDate 만든 시각
 */
@Entity
@Table(
    name = "ruleset",
    uniqueConstraints = [UniqueConstraint(name = "uq_ruleset_project_name", columnNames = ["project_id", "name"])],
    indexes = [Index(name = "ix_ruleset_project", columnList = "project_id")]
)
class Ruleset(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    @Column(name = "name", length = 255)
    var name: String? = null,
    @Column(name = "enforcement", length = 10)
    @ColumnDefault("'active'")
    var enforcement: String? = ACTIVE,
    @Column(name = "pattern", length = 255)
    var pattern: String? = null,
    @Column(name = "bypass_admins")
    @ColumnDefault("false")
    var bypassAdmins: Boolean? = false,
    @Column(name = "restrict_updates")
    @ColumnDefault("false")
    var restrictUpdates: Boolean? = false,
    @Column(name = "restrict_deletions")
    @ColumnDefault("true")
    var restrictDeletions: Boolean? = true,
    @Column(name = "block_force_pushes")
    @ColumnDefault("true")
    var blockForcePushes: Boolean? = true,
    @Column(name = "require_pull_request")
    @ColumnDefault("false")
    var requirePullRequest: Boolean? = false,
    @Column(name = "require_linear_history")
    @ColumnDefault("false")
    var requireLinearHistory: Boolean? = false,
    @Column(name = "required_approvals")
    @ColumnDefault("0")
    var requiredApprovals: Int? = 0,
    @Column(name = "require_no_changes_requested")
    @ColumnDefault("false")
    var requireNoChangesRequested: Boolean? = false,
    @Column(name = "require_conversation_resolution")
    @ColumnDefault("false")
    var requireConversationResolution: Boolean? = false,
    @Column(name = "require_signed_commits")
    @ColumnDefault("false")
    var requireSignedCommits: Boolean? = false,
    @Column(name = "created_date")
    var createdDate: Date? = null
) {

    /** 적용 중이면 `true`이다. 비활성이 아니면 적용으로 본다. */
    val isActive: Boolean get() = enforcement != DISABLED

    /**
     * 푸시 명령 [type]이 이 규칙 묶음에 어긋나면 그 이유를 돌려주고, 어긋나지 않으면 `null`이다(푸시 검사, `decisions.md` D-45, D-46). 이유는 `deletion`(삭제),
     * `force`(강제 푸시), `update`(갱신 제한), `pull-request`(풀 리퀘스트를 요구하는데 직접 푸시)이다. 브랜치를 새로 만드는 푸시는 막지 않는다.
     */
    fun pushViolation(type: org.eclipse.jgit.transport.ReceiveCommand.Type): String? = when (type) {
        org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE -> if (restrictDeletions == true) "deletion" else null
        org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE_NONFASTFORWARD -> when {
            blockForcePushes == true -> "force"
            restrictUpdates == true -> "update"
            requirePullRequest == true -> "pull-request"
            else -> null
        }
        org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE -> when {
            restrictUpdates == true -> "update"
            requirePullRequest == true -> "pull-request"
            else -> null
        }
        else -> null
    }

    /** 패턴에 `*`가 없으면 `true`이다. */
    val isExact: Boolean get() = pattern?.contains('*') != true

    /**
     * [branch]가 이 규칙 묶음의 대상이면 `true`이다. `refs/heads/` 접두사는 떼고 비교한다. 적용 상태는 보지 않는다([isActive]).
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

    companion object {
        /** 적용 상태: 적용한다. */
        const val ACTIVE = "active"

        /** 적용 상태: 적용하지 않는다(규칙 묶음은 남긴다). */
        const val DISABLED = "disabled"
    }
}

interface RulesetRepository : JpaRepository<Ruleset, Long> {
    fun findByProjectIdOrderByNameAsc(projectId: Long): List<Ruleset>
    fun findByProjectIdAndName(projectId: Long, name: String): Ruleset?
}
