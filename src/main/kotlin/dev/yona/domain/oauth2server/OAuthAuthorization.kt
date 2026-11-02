package dev.yona.domain.oauth2server

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Date

/**
 * OAuth 인가 한 건이다(추가 개발 항목 X-1, `decisions.md` D-71). 사용자가 앱에 접근을 허용한 기록으로, 인가 코드, 액세스 토큰, 갱신 토큰의 **해시**와 만료, 승인한 범위를 담는다.
 * 토큰 값 자체는 저장하지 않는다(개인용 토큰과 같은 방식). 열은 Spring Authorization Server의 JDBC 저장 구조를 따르되 해시 열이다.
 *
 * @property id 인가 번호(UUID)
 * @property clientRowId 앱의 행 번호(`oauth_client.id`)
 * @property userId 승인한 사용자 번호(소유자 이름의 숫자 값)
 * @property attributes 인가 요청의 속성(JSON). PKCE 값과 요청 내용이 든다
 * @property state 요청의 `state`
 */
@Entity
@Table(
    name = "oauth_authorization",
    indexes = [
        Index(name = "ix_oauth_authorization_client", columnList = "client_row_id"),
        Index(name = "ix_oauth_authorization_user", columnList = "user_id"),
        Index(name = "ix_oauth_authorization_code", columnList = "code_hash"),
        Index(name = "ix_oauth_authorization_access", columnList = "access_token_hash"),
        Index(name = "ix_oauth_authorization_refresh", columnList = "refresh_token_hash")
    ]
)
class OAuthAuthorization(
    @Id @Column(length = 100)
    var id: String? = null,
    @Column(name = "client_row_id")
    var clientRowId: Long? = null,
    @Column(name = "user_id")
    var userId: Long? = null,
    @Column(name = "grant_type", length = 100)
    var grantType: String? = null,
    @Column(name = "authorized_scopes", length = 1000)
    var authorizedScopes: String? = null,
    @Column(length = dev.yona.domain.LONG_TEXT)
    var attributes: String? = null,
    @Column(length = 500)
    var state: String? = null,
    @Column(name = "code_hash", length = 64)
    var codeHash: String? = null,
    @Column(name = "code_issued_date")
    var codeIssuedDate: Date? = null,
    @Column(name = "code_expires_date")
    var codeExpiresDate: Date? = null,
    @Column(name = "code_metadata", length = dev.yona.domain.LONG_TEXT)
    var codeMetadata: String? = null,
    @Column(name = "access_token_hash", length = 64)
    var accessTokenHash: String? = null,
    @Column(name = "access_token_issued_date")
    var accessTokenIssuedDate: Date? = null,
    @Column(name = "access_token_expires_date")
    var accessTokenExpiresDate: Date? = null,
    @Column(name = "access_token_metadata", length = dev.yona.domain.LONG_TEXT)
    var accessTokenMetadata: String? = null,
    @Column(name = "access_token_scopes", length = 1000)
    var accessTokenScopes: String? = null,
    @Column(name = "refresh_token_hash", length = 64)
    var refreshTokenHash: String? = null,
    @Column(name = "refresh_token_issued_date")
    var refreshTokenIssuedDate: Date? = null,
    @Column(name = "refresh_token_expires_date")
    var refreshTokenExpiresDate: Date? = null,
    @Column(name = "refresh_token_metadata", length = dev.yona.domain.LONG_TEXT)
    var refreshTokenMetadata: String? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null
)

/** [OAuthAuthorization] 저장소이다. */
interface OAuthAuthorizationRepository : JpaRepository<OAuthAuthorization, String> {

    fun findByState(state: String): OAuthAuthorization?

    fun findByCodeHash(hash: String): OAuthAuthorization?

    fun findByAccessTokenHash(hash: String): OAuthAuthorization?

    fun findByRefreshTokenHash(hash: String): OAuthAuthorization?

    fun findByUserIdOrderByCreatedDateDescIdDesc(userId: Long): List<OAuthAuthorization>

    @Modifying
    @Query("delete from OAuthAuthorization a where a.clientRowId = :clientRowId")
    fun deleteByClient(@Param("clientRowId") clientRowId: Long): Int

    @Modifying
    @Query("delete from OAuthAuthorization a where a.userId = :userId")
    fun deleteByUser(@Param("userId") userId: Long): Int

    @Modifying
    @Query("delete from OAuthAuthorization a where a.userId = :userId and a.clientRowId = :clientRowId")
    fun deleteByUserAndClient(@Param("userId") userId: Long, @Param("clientRowId") clientRowId: Long): Int
}

/**
 * 사용자가 앱에 승인한 범위이다(X-1, D-71). 같은 앱이 이미 허용한 범위만 다시 요청하면 승인 화면을 건너뛰는 데 쓴다.
 *
 * @property clientRowId 앱의 행 번호
 * @property userId 사용자 번호
 * @property scopes 승인한 범위(쉼표로 이음)
 */
@Entity
@Table(
    name = "oauth_consent",
    uniqueConstraints = [UniqueConstraint(name = "uq_oauth_consent", columnNames = ["client_row_id", "user_id"])],
    indexes = [Index(name = "ix_oauth_consent_user", columnList = "user_id")]
)
class OAuthConsent(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "client_row_id")
    var clientRowId: Long? = null,
    @Column(name = "user_id")
    var userId: Long? = null,
    @Column(length = 1000)
    var scopes: String? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null
)

/** [OAuthConsent] 저장소이다. */
interface OAuthConsentRepository : JpaRepository<OAuthConsent, Long> {

    fun findByClientRowIdAndUserId(clientRowId: Long, userId: Long): OAuthConsent?

    fun findByUserIdOrderByCreatedDateDescIdDesc(userId: Long): List<OAuthConsent>

    @Modifying
    @Query("delete from OAuthConsent c where c.clientRowId = :clientRowId")
    fun deleteByClient(@Param("clientRowId") clientRowId: Long): Int

    @Modifying
    @Query("delete from OAuthConsent c where c.userId = :userId")
    fun deleteByUser(@Param("userId") userId: Long): Int

    @Modifying
    @Query("delete from OAuthConsent c where c.userId = :userId and c.clientRowId = :clientRowId")
    fun deleteByUserAndClient(@Param("userId") userId: Long, @Param("clientRowId") clientRowId: Long): Int
}
