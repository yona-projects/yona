package dev.yona.domain.deviceauth

import dev.yona.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.Date

/** 기기 인증 요청의 진행 상태이다. */
enum class DeviceAuthorizationStatus {
    /** 사용자가 아직 승인하지 않았다. */
    PENDING,

    /** 사용자가 승인했다. 다음 교환 요청이 토큰을 만든다. */
    APPROVED,

    /** 사용자가 거절했다. */
    DENIED
}

/**
 * 기기 인증(Device Flow, RFC 8628) 요청 한 건이다(추가 개발 항목 X-37i, `decisions.md` D-52). CLI가 코드를 요청할 때 만들어지고, 토큰을 내주거나 거절을 알리거나 만료되면 지워진다.
 * 기기 코드는 원문을 저장하지 않고 해시만 둔다. 토큰 원문도 저장하지 않는다(교환 요청 때 만들어 바로 내준다). 이름과 열은 v1.6 규칙(소문자 스네이크)을 따른다.
 *
 * @property deviceCodeHash 기기 코드의 SHA-256 16진수. 유일하다. CLI만 원문을 안다
 * @property userCode 사용자가 웹에서 입력하는 코드(`BCDF-GHJK` 모양). 유일하다
 * @property clientName CLI가 밝힌 기기 이름. 토큰 이름(`Yona CLI: <이름>`)과 승인 화면에 쓴다
 * @property scopes 요청한 범위(쉼표로 이은 [dev.yona.domain.apitoken.ApiScopes]). 승인할 때 승인한 사용자가 가질 수 있는 범위로 줄어든다
 * @property status 진행 상태
 * @property user 승인한 사용자. 승인 전에는 없다
 * @property createdDate 요청한 시각
 * @property expiresDate 만료 시각. 이 시각 뒤에는 승인도 교환도 되지 않는다
 * @property intervalSeconds 교환 요청의 최소 간격(초). `slow_down`을 받을 때마다 5초씩 늘어난다
 * @property lastPolledDate 마지막으로 교환을 요청한 시각
 */
@Entity
@Table(
    name = "device_authorization",
    indexes = [Index(name = "ix_device_authorization_expires", columnList = "expires_date")],
    uniqueConstraints = [UniqueConstraint(name = "uq_device_authorization_code", columnNames = ["device_code_hash"]), UniqueConstraint(name = "uq_device_authorization_user_code", columnNames = ["user_code"])]
)
class DeviceAuthorization(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "device_code_hash", length = 64)
    var deviceCodeHash: String? = null,
    @Column(name = "user_code", length = 16)
    var userCode: String? = null,
    @Column(name = "client_name", length = 100)
    var clientName: String? = null,
    @Column(length = 1000)
    var scopes: String? = null,
    @Column(length = 20)
    var status: String = DeviceAuthorizationStatus.PENDING.name,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @Column(name = "expires_date")
    var expiresDate: Date? = null,
    @Column(name = "interval_seconds")
    var intervalSeconds: Int = 5,
    @Column(name = "last_polled_date")
    var lastPolledDate: Date? = null
)

/** [DeviceAuthorization] 저장소이다. */
interface DeviceAuthorizationRepository : JpaRepository<DeviceAuthorization, Long> {

    fun findByDeviceCodeHash(deviceCodeHash: String): DeviceAuthorization?

    fun findByUserCode(userCode: String): DeviceAuthorization?

    /** 만료 시각이 [before]보다 앞선 요청을 지우고 지운 개수를 돌려준다. */
    @Modifying
    @Query("delete from DeviceAuthorization d where d.expiresDate < :before")
    fun deleteExpiredBefore(before: Date): Int
}
