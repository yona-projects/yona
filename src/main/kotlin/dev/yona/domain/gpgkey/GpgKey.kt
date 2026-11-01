package dev.yona.domain.gpgkey

import dev.yona.domain.user.User
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Date

/**
 * 사용자가 등록한 GPG 공개키의 키 한 개이다(추가 개발 항목 X-4, `decisions.md` D-65). 공개키 한 묶음은 주 키 한 행과 하위 키 행들로 저장한다(GitHub 방식).
 * 서명에서 읽은 발행 키 ID로 주 키와 하위 키를 모두 같은 방법으로 찾아 소유자를 알아내려는 구조이다. 이름과 열은 v1.6 규칙(소문자 스네이크, `<대상>_id`)을 따른다.
 *
 * @property user 키 주인. 주 키와 하위 키 행이 같다
 * @property parent 하위 키 행이면 그 주 키 행이다. 주 키 행은 `null`이다
 * @property name 사용자가 붙인 이름. 주 키 행에만 있다
 * @property fingerprint 지문(대문자 16진수). 사이트 전체에서 유일하다
 * @property keyId 키 ID(16자리 대문자 16진수). 서명의 발행자 표시와 맞춘다
 * @property publicKey 공개키 묶음의 ASCII armor. 주 키 행에만 있다
 * @property emails 사용자 ID에 든 이메일(소문자, 쉼표로 이음). 주 키 행에만 있다
 * @property canSign 서명 용도로 쓸 수 있으면 `true`이다
 * @property createdDate 등록한 시각
 * @property expiresDate 키의 만료 시각. 없으면 만료 없음이다
 */
@Entity
@Table(
    name = "gpg_key",
    indexes = [Index(name = "ix_gpg_key_user", columnList = "user_id"), Index(name = "ix_gpg_key_key_id", columnList = "key_id"), Index(name = "ix_gpg_key_parent", columnList = "parent_id")],
    uniqueConstraints = [UniqueConstraint(name = "uq_gpg_key_fingerprint", columnNames = ["fingerprint"])]
)
class GpgKey(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @ManyToOne @JoinColumn(name = "parent_id")
    var parent: GpgKey? = null,
    @Column(length = 100)
    var name: String? = null,
    @Column(length = 64)
    var fingerprint: String? = null,
    @Column(name = "key_id", length = 16)
    var keyId: String? = null,
    @Column(name = "public_key", length = dev.yona.domain.LONG_TEXT)
    var publicKey: String? = null,
    @Column(length = 1000)
    var emails: String? = null,
    @Column(name = "can_sign")
    var canSign: Boolean? = true,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @Column(name = "expires_date")
    var expiresDate: Date? = null
)

/** [GpgKey] 저장소이다. */
interface GpgKeyRepository : JpaRepository<GpgKey, Long> {

    fun existsByFingerprintIn(fingerprints: Collection<String>): Boolean

    /** 사용자의 주 키 행을 최근에 등록한 순서로 읽는다. */
    fun findByUserIdAndParentIsNullOrderByCreatedDateDescIdDesc(userId: Long): List<GpgKey>

    fun countByUserIdAndParentIsNull(userId: Long): Long

    fun findByParentIdOrderById(parentId: Long): List<GpgKey>

    fun findByKeyId(keyId: String): List<GpgKey>

    fun deleteByParentId(parentId: Long)
}
