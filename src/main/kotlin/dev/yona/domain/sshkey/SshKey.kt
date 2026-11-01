package dev.yona.domain.sshkey

import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Date

/**
 * 사용자가 등록한 SSH 공개키이다(추가 개발 항목 X-3, `decisions.md` D-66). 접속한 키의 지문으로 사용자를 알아내는 데 쓴다. 이름과 열은 v1.6 규칙(소문자 스네이크, `<대상>_id`)을 따른다.
 *
 * @property user 키 주인
 * @property name 사용자가 붙인 이름
 * @property fingerprint 지문(`SHA256:...`). 사용자 키와 [DeployKey]를 합쳐 사이트 전체에서 유일하다
 * @property keyType 키 종류(`ssh-ed25519` 등)
 * @property publicKey 공개키 한 줄(`종류 본문`)
 * @property createdDate 등록한 시각
 */
@Entity
@Table(
    name = "ssh_key",
    indexes = [Index(name = "ix_ssh_key_user", columnList = "user_id")],
    uniqueConstraints = [UniqueConstraint(name = "uq_ssh_key_fingerprint", columnNames = ["fingerprint"])]
)
class SshKey(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @Column(length = 100)
    var name: String? = null,
    @Column(length = 100)
    var fingerprint: String? = null,
    @Column(name = "key_type", length = 64)
    var keyType: String? = null,
    @Column(name = "public_key", length = dev.yona.domain.LONG_TEXT)
    var publicKey: String? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null
)

/** [SshKey] 저장소이다. */
interface SshKeyRepository : JpaRepository<SshKey, Long> {

    fun existsByFingerprint(fingerprint: String): Boolean

    fun findByFingerprint(fingerprint: String): SshKey?

    /** 사용자의 키를 최근에 등록한 순서로 읽는다. */
    fun findByUserIdOrderByCreatedDateDescIdDesc(userId: Long): List<SshKey>

    fun countByUserId(userId: Long): Long
}

/**
 * 저장소 한 개에만 접근하는 SSH 공개키이다(배포 키, 추가 개발 항목 X-3, `decisions.md` D-66). GitHub처럼 SSH 공개키 전용이며 기본은 읽기 전용이다.
 *
 * @property project 키가 속한 저장소(프로젝트)
 * @property readOnly 읽기 전용이면 `true`이다. `false`이면 푸시도 할 수 있다
 */
@Entity
@Table(
    name = "deploy_key",
    indexes = [Index(name = "ix_deploy_key_project", columnList = "project_id")],
    uniqueConstraints = [UniqueConstraint(name = "uq_deploy_key_fingerprint", columnNames = ["fingerprint"])]
)
class DeployKey(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    @Column(length = 100)
    var name: String? = null,
    @Column(length = 100)
    var fingerprint: String? = null,
    @Column(name = "key_type", length = 64)
    var keyType: String? = null,
    @Column(name = "public_key", length = dev.yona.domain.LONG_TEXT)
    var publicKey: String? = null,
    @Column(name = "read_only")
    var readOnly: Boolean? = true,
    @Column(name = "created_date")
    var createdDate: Date? = null
)

/** [DeployKey] 저장소이다. */
interface DeployKeyRepository : JpaRepository<DeployKey, Long> {

    fun existsByFingerprint(fingerprint: String): Boolean

    fun findByFingerprint(fingerprint: String): DeployKey?

    fun findByProjectIdOrderByCreatedDateDescIdDesc(projectId: Long): List<DeployKey>

    fun countByProjectId(projectId: Long): Long
}
