package dev.yona.domain.twofactor

import dev.yona.domain.user.User
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Date

/**
 * 사용자가 등록한 2단계 인증 수단 한 개이다(추가 개발 항목 X-2, `decisions.md` D-69). 지금은 인증 앱(`TOTP`)이고 패스키(WebAuthn)는 따로 둔다. 이름과 열은 v1.6 규칙(소문자 스네이크, `<대상>_id`)을 따른다.
 *
 * @property user 수단의 주인
 * @property type 수단 종류. `TOTP`
 * @property name 사용자가 보는 이름
 * @property secretEncrypted 암호화한 TOTP 비밀 값([SecretCipher]). 평문은 저장하지 않는다
 * @property confirmed 등록할 때 첫 코드를 확인했으면 `true`이다. 확인하지 않은 수단은 로그인에 쓰이지 않는다
 * @property lastUsedStep 마지막으로 인증에 쓴 TOTP 구간 번호. 같은 코드를 다시 쓰지 못하게 한다
 */
@Entity
@Table(name = "two_factor_credential", indexes = [Index(name = "ix_two_factor_credential_user", columnList = "user_id")])
class TwoFactorCredential(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @Column(length = 16)
    var type: String? = TOTP,
    @Column(length = 100)
    var name: String? = null,
    @Column(name = "secret_encrypted", length = 500)
    var secretEncrypted: String? = null,
    var confirmed: Boolean? = false,
    @Column(name = "last_used_step")
    var lastUsedStep: Long? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @Column(name = "last_used_date")
    var lastUsedDate: Date? = null
) {
    companion object {
        const val TOTP = "TOTP"
    }
}

/** [TwoFactorCredential] 저장소이다. */
interface TwoFactorCredentialRepository : JpaRepository<TwoFactorCredential, Long> {

    fun findByUserIdAndTypeOrderById(userId: Long, type: String): List<TwoFactorCredential>

    fun findByUserIdOrderById(userId: Long): List<TwoFactorCredential>

    fun existsByUserIdAndConfirmedTrue(userId: Long): Boolean

    fun deleteByUserId(userId: Long)

    /**
     * 인증에 쓴 구간 번호를 더 큰 값으로 올린다. 이미 같거나 더 큰 값이 기록되어 있으면(같은 코드의 동시 사용이나 재사용) 아무것도 바꾸지 않고 0을 돌려준다.
     * 조건을 데이터베이스에서 한 번에 확인해 두 요청이 같은 코드로 동시에 통과하지 못하게 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TwoFactorCredential c set c.lastUsedStep = :step, c.lastUsedDate = :now where c.id = :id and (c.lastUsedStep is null or c.lastUsedStep < :step)")
    fun advance(@Param("id") id: Long, @Param("step") step: Long, @Param("now") now: Date): Int
}

/**
 * 백업 코드 한 개이다(X-2, D-69). 코드 자체는 저장하지 않고 해시만 둔다([BackupCodes]). 한 번 쓰면 [usedDate]가 찬다.
 */
@Entity
@Table(
    name = "two_factor_backup_code",
    indexes = [Index(name = "ix_two_factor_backup_code_user", columnList = "user_id")],
    uniqueConstraints = [UniqueConstraint(name = "uq_two_factor_backup_code", columnNames = ["user_id", "code_hash"])]
)
class TwoFactorBackupCode(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @Column(name = "code_hash", length = 64)
    var codeHash: String? = null,
    @Column(name = "used_date")
    var usedDate: Date? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null
)

/** [TwoFactorBackupCode] 저장소이다. */
interface TwoFactorBackupCodeRepository : JpaRepository<TwoFactorBackupCode, Long> {

    fun findByUserIdAndCodeHashAndUsedDateIsNull(userId: Long, codeHash: String): TwoFactorBackupCode?

    fun countByUserIdAndUsedDateIsNull(userId: Long): Long

    fun deleteByUserId(userId: Long)

    /** 코드를 쓴 것으로 표시한다. 이미 쓴 코드이면(동시 사용) 0을 돌려준다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TwoFactorBackupCode b set b.usedDate = :now where b.id = :id and b.usedDate is null")
    fun markUsed(@Param("id") id: Long, @Param("now") now: Date): Int
}

/**
 * 알려진 기기 한 대이다(X-2, D-69). 2단계 인증을 거친 사용자가 "이 기기에서 30일 동안 묻지 않기"를 고르면 만든다. 기기 쿠키의 값은 무작위이고 서버에는 해시만 둔다.
 *
 * @property tokenHash 기기 쿠키 값의 SHA-256(16진수). 전체에서 유일하다
 * @property name 기기를 알아보는 이름(브라우저와 운영체제 요약)
 * @property ip 등록한 주소
 * @property expiresDate 이 시각이 지나면 쓰이지 않는다
 */
@Entity
@Table(
    name = "known_device",
    indexes = [Index(name = "ix_known_device_user", columnList = "user_id")],
    uniqueConstraints = [UniqueConstraint(name = "uq_known_device_token", columnNames = ["token_hash"])]
)
class KnownDevice(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @Column(name = "token_hash", length = 64)
    var tokenHash: String? = null,
    @Column(length = 100)
    var name: String? = null,
    @Column(length = 64)
    var ip: String? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @Column(name = "last_used_date")
    var lastUsedDate: Date? = null,
    @Column(name = "expires_date")
    var expiresDate: Date? = null
)

/** [KnownDevice] 저장소이다. */
interface KnownDeviceRepository : JpaRepository<KnownDevice, Long> {

    fun findByTokenHash(tokenHash: String): KnownDevice?

    fun findByUserIdOrderByLastUsedDateDescIdDesc(userId: Long): List<KnownDevice>

    fun countByUserId(userId: Long): Long

    fun deleteByUserId(userId: Long)
}
