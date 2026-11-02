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
    INVALID_DISCOVERY("sso.admin.error.invalidDiscovery"),
    MISSING_IDP("sso.admin.error.missingIdp"),
    INVALID_CERTIFICATE("sso.admin.error.invalidCertificate"),
    INVALID_KEY("sso.admin.error.invalidKey"),
    KEY_MISMATCH("sso.admin.error.keyMismatch"),
    SP_KEY_REQUIRED("sso.admin.error.spKeyRequired"),
    LDAP_HOST_REQUIRED("sso.admin.error.ldapHost"),
    LDAP_PORT_INVALID("sso.admin.error.ldapPort"),
    LDAP_BASE_DN_REQUIRED("sso.admin.error.ldapBaseDn"),
    LDAP_PROTOCOL_INVALID("sso.admin.error.ldapProtocol"),
    LDAP_UNREACHABLE("sso.admin.error.ldapUnreachable")
}

/** [SsoSettingsService.saveOidc]의 결과이다. */
sealed interface SsoSaveResult {
    data class Saved(val settings: Any) : SsoSaveResult
    data class Rejected(val error: SsoSaveError) : SsoSaveResult
}

/** 저장할 때 확인해 캐시하는 OIDC 발견 문서의 값이다. */
data class OidcDiscovery(val issuer: String, val authorizationEndpoint: String, val tokenEndpoint: String, val jwksUri: String, val userInfoEndpoint: String? = null)

/**
 * SAML 2 SSO 설정이다(추가 개발 항목 X-16, `decisions.md` D-80). 사이트에 한 행(번호 1)이다.
 *
 * @property idpEntityId IdP의 발급자(Entity ID). 로그인 응답의 발급자가 이 값과 같아야 한다
 * @property idpSsoUrl IdP의 로그인 주소(SingleSignOnService)
 * @property idpSsoBinding 로그인 요청을 보내는 방식(`REDIRECT` 또는 `POST`)
 * @property idpCertificates IdP가 서명에 쓰는 인증서(PEM, 여러 개 가능). 교체 기간에는 둘 다 받는다
 * @property spEntityId 이 사이트(SP)의 Entity ID. 비우면 SP 메타데이터 주소를 쓴다
 * @property nameIdFormat 요청할 NameID 형식(비우면 요청하지 않는다)
 * @property signRequests 로그인 요청(AuthnRequest)에 서명한다(SP 키가 있어야 한다, 기본 끔)
 * @property spPrivateKey SP 비밀 키. `SecretCipher`로 암호화한 글(`v1.…`)이다. [spCertificate]와 짝이다
 * @property spPreviousPrivateKey 교체 중인 이전 SP 비밀 키(암호화). IdP가 새 인증서를 올릴 때까지 함께 공개한다
 */
@Entity
@Table(name = "saml2_sso_settings")
class Saml2SsoSettings(
    @Id var id: Long = 1,
    var enabled: Boolean? = false,
    @Column(name = "display_name", length = 100) var displayName: String? = null,
    @Column(name = "idp_entity_id", length = 500) var idpEntityId: String? = null,
    @Column(name = "idp_sso_url", length = 1000) var idpSsoUrl: String? = null,
    @Column(name = "idp_sso_binding", length = 10) var idpSsoBinding: String? = "REDIRECT",
    @Column(name = "idp_certificates", length = dev.yona.domain.LONG_TEXT) var idpCertificates: String? = null,
    @Column(name = "sp_entity_id", length = 500) var spEntityId: String? = null,
    @Column(name = "name_id_format", length = 200) var nameIdFormat: String? = null,
    @Column(name = "email_attribute", length = 200) var emailAttribute: String? = "email",
    @Column(name = "name_attribute", length = 200) var nameAttribute: String? = "name",
    @Column(name = "login_id_attribute", length = 200) var loginIdAttribute: String? = null,
    @Column(name = "auto_create") var autoCreate: Boolean? = true,
    @Column(name = "link_by_email") var linkByEmail: Boolean? = true,
    @Column(name = "allow_unverified_email") var allowUnverifiedEmail: Boolean? = false,
    @Column(name = "name_sync") var nameSync: Boolean? = false,
    @Column(name = "initiate_enabled") var initiateEnabled: Boolean? = false,
    @Column(name = "sign_requests") var signRequests: Boolean? = false,
    @Column(name = "clock_skew_seconds") var clockSkewSeconds: Int? = 60,
    @Column(name = "sp_private_key", length = dev.yona.domain.LONG_TEXT) var spPrivateKey: String? = null,
    @Column(name = "sp_certificate", length = dev.yona.domain.LONG_TEXT) var spCertificate: String? = null,
    @Column(name = "sp_previous_private_key", length = dev.yona.domain.LONG_TEXT) var spPreviousPrivateKey: String? = null,
    @Column(name = "sp_previous_certificate", length = dev.yona.domain.LONG_TEXT) var spPreviousCertificate: String? = null,
    @Column(name = "sp_key_created_date") var spKeyCreatedDate: Date? = null,
    @Column(name = "updated_date") var updatedDate: Date? = null
)

/** [Saml2SsoSettings] 저장소이다. */
interface Saml2SsoSettingsRepository : JpaRepository<Saml2SsoSettings, Long>

/** 관리자 화면이 보내는 SAML 2 설정 입력이다. */
data class Saml2SsoForm(
    val enabled: Boolean, val displayName: String, val idpEntityId: String, val idpSsoUrl: String, val idpCertificates: String, val spEntityId: String, val nameIdFormat: String,
    val emailAttribute: String, val nameAttribute: String, val loginIdAttribute: String, val autoCreate: Boolean, val linkByEmail: Boolean, val allowUnverifiedEmail: Boolean,
    val nameSync: Boolean, val initiateEnabled: Boolean, val signRequests: Boolean, val clockSkewSeconds: Int, val idpSsoBinding: String = "REDIRECT"
)

/** IdP 메타데이터(XML)를 읽은 결과이다. */
sealed interface Saml2MetadataImport {
    /** 읽었다. [certificatesPem]은 서명용 인증서이고 [binding]은 `REDIRECT` 또는 `POST`이다. */
    data class Imported(val entityId: String, val ssoUrl: String, val binding: String, val certificatesPem: List<String>) : Saml2MetadataImport

    /** 읽지 못했다. */
    data class Invalid(val error: SsoSaveError = SsoSaveError.INVALID_DISCOVERY) : Saml2MetadataImport
}
