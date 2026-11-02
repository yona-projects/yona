package dev.yona.domain.oauth2server

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Date

/**
 * OAuth 앱(클라이언트) 한 개이다(추가 개발 항목 X-1, `decisions.md` D-71). v1.6에는 없는 테이블이다. 이름과 열은 v1.6 규칙(소문자 스네이크)을 따른다.
 *
 * @property clientId 클라이언트 ID. 직접 등록한 앱은 `yona_app_<무작위>`이고, 클라이언트 ID 메타데이터 문서(CIMD)로 가져온 앱은 문서의 `https://` 주소이다. 전체에서 유일하다
 * @property name 승인 화면에 보이는 이름
 * @property description 설명
 * @property homeUrl 앱의 홈 주소
 * @property redirectUris 등록한 리디렉션 주소들(줄 단위, [RedirectUris])
 * @property confidential 기밀 앱(비밀 값이 있는 서버 앱)이면 `true`이고 공개 앱(PKCE만 쓰는 앱)이면 `false`이다
 * @property secretHash 비밀 값의 SHA-256(16진수). 기밀 앱만 있다. 비밀 값은 만든 직후 한 번만 보인다
 * @property allowedScopes 이 앱이 요청할 수 있는 범위(쉼표로 이음, `ApiScopes`의 클래식 범위)
 * @property registration 등록 방식. `USER`(직접 등록)와 `CIMD`(문서에서 가져온 캐시)
 * @property ownerType 소유자 종류. `USER`이고 `ORGANIZATION`은 로드맵 X-39에서 연다(테이블은 처음부터 이 구조이다)
 * @property ownerId 소유자 번호
 * @property fetchedDate CIMD 문서를 가져온 시각
 * @property expiresDate CIMD 캐시가 만료되는 시각
 */
@Entity
@Table(
    name = "oauth_client",
    indexes = [Index(name = "ix_oauth_client_owner", columnList = "owner_type, owner_id")],
    uniqueConstraints = [UniqueConstraint(name = "uq_oauth_client_client_id", columnNames = ["client_id"])]
)
class OAuthClient(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "client_id", length = 500)
    var clientId: String? = null,
    @Column(length = 100)
    var name: String? = null,
    @Column(length = 500)
    var description: String? = null,
    @Column(name = "home_url", length = 500)
    var homeUrl: String? = null,
    @Column(name = "redirect_uris", length = dev.yona.domain.LONG_TEXT)
    var redirectUris: String? = null,
    var confidential: Boolean? = false,
    @Column(name = "secret_hash", length = 64)
    var secretHash: String? = null,
    @Column(name = "allowed_scopes", length = 1000)
    var allowedScopes: String? = null,
    @Column(length = 10)
    var registration: String? = USER,
    @Column(name = "owner_type", length = 20)
    var ownerType: String? = USER,
    @Column(name = "owner_id")
    var ownerId: Long? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @Column(name = "updated_date")
    var updatedDate: Date? = null,
    @Column(name = "fetched_date")
    var fetchedDate: Date? = null,
    @Column(name = "expires_date")
    var expiresDate: Date? = null
) {
    companion object {
        const val USER = "USER"
        const val CIMD = "CIMD"
        const val ORGANIZATION = "ORGANIZATION"
    }
}

/** [OAuthClient] 저장소이다. */
interface OAuthClientRepository : JpaRepository<OAuthClient, Long> {

    fun findByClientId(clientId: String): OAuthClient?

    fun findByOwnerTypeAndOwnerIdOrderByCreatedDateDescIdDesc(ownerType: String, ownerId: Long): List<OAuthClient>

    fun countByOwnerTypeAndOwnerId(ownerType: String, ownerId: Long): Long

    fun deleteByOwnerTypeAndOwnerId(ownerType: String, ownerId: Long)

    fun findAllByOrderByCreatedDateDescIdDesc(): List<OAuthClient>
}
