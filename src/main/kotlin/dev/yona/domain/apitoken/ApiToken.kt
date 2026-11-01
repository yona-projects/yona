package dev.yona.domain.apitoken

import dev.yona.domain.user.User
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.Date

/**
 * 개인용 API 토큰(Classic) 한 개이다(추가 개발 항목 X-5, `decisions.md` D-36). 원문은 저장하지 않고 해시와 목록에 보일 앞부분만 둔다.
 * 이름과 열은 v1.6 규칙(소문자 스네이크, `<대상>_id`)을 따른다.
 *
 * @property user 토큰 주인
 * @property name 사용자가 붙인 이름
 * @property tokenHash 토큰 값의 SHA-256 16진수. 유일하다
 * @property tokenPrefix 목록에 보일 앞부분(새 토큰은 `yona_pat_` 포함 13자, 이관한 옛 토큰은 4자)
 * @property scopes 쉼표로 이은 범위([ApiScopes])
 * @property isLegacy v1.6의 사용자 토큰(`n4user.token`)에서 옮긴 것이면 `true`이다. 값이 추측 가능해 설정 화면에 경고를 보인다
 * @property createdDate 만든 시각
 * @property expiresDate 만료 시각. 없으면 만료 없음이다
 * @property lastUsedDate 마지막으로 쓴 시각(1분에 한 번만 갱신한다)
 */
@Entity
@Table(
    name = "api_token",
    indexes = [Index(name = "ix_api_token_user", columnList = "user_id")],
    uniqueConstraints = [UniqueConstraint(name = "uq_api_token_hash", columnNames = ["token_hash"])]
)
class ApiToken(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @Column(length = 100)
    var name: String? = null,
    @Column(name = "token_hash", length = 64)
    var tokenHash: String? = null,
    @Column(name = "token_prefix", length = 16)
    var tokenPrefix: String? = null,
    @Column(length = 1000)
    var scopes: String? = null,
    @Column(name = "is_legacy")
    var isLegacy: Boolean? = false,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @Column(name = "expires_date")
    var expiresDate: Date? = null,
    @Column(name = "last_used_date")
    var lastUsedDate: Date? = null
)

/** [ApiToken] 저장소이다. */
interface ApiTokenRepository : JpaRepository<ApiToken, Long> {

    fun findByTokenHash(tokenHash: String): ApiToken?

    /** 사용자의 토큰을 최근에 만든 순서로 읽는다. */
    fun findByUserIdOrderByCreatedDateDescIdDesc(userId: Long): List<ApiToken>

    fun countByUserId(userId: Long): Long

    fun findByUserIdAndName(userId: Long, name: String): List<ApiToken>

    fun existsByUserIdAndIsLegacyTrue(userId: Long): Boolean

    /** 이관하지 않은 토큰 가운데 마지막 사용(없으면 만든 시각)이 [cutoff]보다 오래된 것을 지운다. 지운 개수를 돌려준다. */
    @Modifying
    @Query("delete from ApiToken t where (t.isLegacy is null or t.isLegacy = false) and coalesce(t.lastUsedDate, t.createdDate) < :cutoff")
    fun deleteUnusedSince(cutoff: Date): Int
}
