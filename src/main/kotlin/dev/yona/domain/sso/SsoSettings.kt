package dev.yona.domain.sso

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Date

/**
 * OIDC SSO 설정이다(추가 개발 항목 X-16, `decisions.md` D-80 3, 4, 5번). 사이트에 한 행(번호 1)이다. 값은 관리자 화면에서만 바꾼다.
 *
 * @property displayName 로그인 단추의 이름("{이름}으로 로그인")
 * @property issuerUri IdP 발급자 주소(발견 문서는 이 주소 뒤에 `/.well-known/openid-configuration`)
 * @property clientSecret 클라이언트 비밀 값. `SecretCipher`로 암호화한 글(`v1.…`)이다
 * @property scopes 요청할 범위(공백으로 나눔)
 * @property emailClaim 이메일을 읽을 클레임 이름
 * @property nameClaim 이름을 읽을 클레임 이름
 * @property loginIdClaim 로그인 ID 후보를 읽을 클레임 이름(비우면 이메일 앞부분)
 * @property initiateEnabled IdP(회사 포털)가 여는 로그인 시작 주소(`/sso/oidc/initiate`)를 받는다(`decisions.md` D-80 14번, 23번, 기본 끔)
 * @property clockSkewSeconds ID 토큰 시간 검증에서 허용하는 시계 오차(초)
 * @property discoveryJson 저장할 때 확인한 발견 문서의 필요한 값(JSON). 로그인 때마다 다시 받지 않는다
 */
@Entity
@Table(name = "oidc_sso_settings")
class OidcSsoSettings(
    @Id var id: Long = 1,
    var enabled: Boolean? = false,
    @Column(name = "display_name", length = 100) var displayName: String? = null,
    @Column(name = "issuer_uri", length = 500) var issuerUri: String? = null,
    @Column(name = "client_id", length = 255) var clientId: String? = null,
    @Column(name = "client_secret", length = 2000) var clientSecret: String? = null,
    @Column(length = 255) var scopes: String? = "openid profile email",
    @Column(name = "email_claim", length = 100) var emailClaim: String? = "email",
    @Column(name = "name_claim", length = 100) var nameClaim: String? = "name",
    @Column(name = "login_id_claim", length = 100) var loginIdClaim: String? = "preferred_username",
    @Column(name = "auto_create") var autoCreate: Boolean? = true,
    @Column(name = "link_by_email") var linkByEmail: Boolean? = true,
    @Column(name = "allow_unverified_email") var allowUnverifiedEmail: Boolean? = false,
    @Column(name = "name_sync") var nameSync: Boolean? = false,
    @Column(name = "initiate_enabled") var initiateEnabled: Boolean? = false,
    @Column(name = "clock_skew_seconds") var clockSkewSeconds: Int? = 60,
    @Column(name = "discovery_json", length = dev.yona.domain.LONG_TEXT) var discoveryJson: String? = null,
    @Column(name = "discovery_checked_date") var discoveryCheckedDate: Date? = null,
    @Column(name = "updated_date") var updatedDate: Date? = null
)

/** [OidcSsoSettings] 저장소이다. */
interface OidcSsoSettingsRepository : JpaRepository<OidcSsoSettings, Long>

/** 관리자 화면이 보내는 OIDC 설정 입력이다. [clientSecret]이 비었거나 `null`이면 이전 값을 유지한다. */
data class OidcSsoForm(
    val enabled: Boolean, val displayName: String, val issuerUri: String, val clientId: String, val clientSecret: String?, val scopes: String, val emailClaim: String, val nameClaim: String,
    val loginIdClaim: String, val autoCreate: Boolean, val linkByEmail: Boolean, val allowUnverifiedEmail: Boolean, val nameSync: Boolean, val initiateEnabled: Boolean, val clockSkewSeconds: Int
)

/** 설정을 저장하지 못한 이유이다. [messageKey]는 관리자 화면에 보일 메시지 키이다. */
enum class SsoSaveError(val messageKey: String) {
    INVALID_ISSUER("sso.admin.error.invalidIssuer"),
    HTTPS_REQUIRED("sso.admin.error.httpsRequired"),
    MISSING_CLIENT("sso.admin.error.missingClient"),
    UNREACHABLE("sso.admin.error.unreachable"),
    ISSUER_MISMATCH("sso.admin.error.issuerMismatch"),
    INVALID_DISCOVERY("sso.admin.error.invalidDiscovery")
}

/** [SsoSettingsService.saveOidc]의 결과이다. */
sealed interface SsoSaveResult {
    data class Saved(val settings: OidcSsoSettings) : SsoSaveResult
    data class Rejected(val error: SsoSaveError) : SsoSaveResult
}

/** 저장할 때 확인해 캐시하는 OIDC 발견 문서의 값이다. */
data class OidcDiscovery(val issuer: String, val authorizationEndpoint: String, val tokenEndpoint: String, val jwksUri: String, val userInfoEndpoint: String? = null)
