package com.github.yonaprojects.yona.domain.oauth2server

import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

// OAuth 앱(사전등록 클라이언트) 등록/삭제 로직을 한 곳에 모은다. 처음에는
// 이 로직이 OAuthAppsAdminController(사이트 관리자 전용) 안에만 있었지만, 사용자 셀프서비스
// 등록 화면(UserViewController)이 새로 생기면서 두 컨트롤러가 "등록"(register)과 "삭제 시 관련 동의/토큰
// 레코드 정리"(deleteClientAndRelatedRecords) 로직을 그대로 공유해야 한다 — 컨트롤러마다 복제하는 대신
// 도메인 서비스로 뽑아 하나의 구현만 유지한다. 오너십(누가 등록했는지) 판단/검증은 이 서비스가 갖고
// 있는 ownerId 필드 기준으로 하되, "그 소유자만 삭제 가능"이라는 정책(IDOR 방지)은 호출부
// (UserViewController.deleteOwnedOAuthApp)가 findOwned()로 확인한 뒤에만 삭제를 호출하는 방식으로
// 강제한다 — 반대로 사이트 관리자(OAuthAppsAdminController)는 소유자와 무관하게 강제 삭제할 수 있어야
// 하므로 이 서비스의 deleteClientAndRelatedRecords() 자체에는 소유자 검증을 넣지 않는다.
@Service
class OAuthAppRegistrationService(
    private val clientRepository: OAuthRegisteredClientRepository,
    private val consentRepository: OAuthAuthorizationConsentRepository,
    private val authorizationRepository: OAuthAuthorizationRepository,
    private val passwordEncoder: PasswordEncoder
) {

    // 스코프 선택지 — DCR(McpOAuthScopes.ALL)과 동일한 축(ApiTokenScopeGroup x READ/WRITE)을
    // 재사용한다(신규 축 설계 없음, 기존 스코프 체계와 일관성 유지). 여기에
    // identityScopes()(openid/profile/email)도 합쳐서 반환한다 — register()의 화이트리스트
    // 필터(`scopes.filter { it in availableScopes() }`)가 이 목록 하나만 기준으로 삼으므로, identity
    // 스코프도 API 스코프와 동일한 검증 경로를 타게 하려면 반드시 여기 포함돼야 한다.
    fun availableScopes(): List<String> =
        ApiTokenScopeGroup.entries.flatMap { group ->
            listOf(ApiTokenPermission.READ, ApiTokenPermission.WRITE).map {
                "${group.name.lowercase()}:${it.name.lowercase()}"
            }
        } + identityScopes()

    // 사용자 결정사항("identity 스코프는 전
    // 클라이언트 자동 포함이 아니라 앱 등록 시 사용자가 개별 선택")에 따라 API 스코프
    // (ApiTokenScopeGroup 기반)와는 완전히 별개 축으로 둔다 — OIDC 표준 스코프 리터럴이라
    // ApiTokenScopeGroup에서 파생할 수 없다(신규 그룹을 추가하는 게 아니라 별도 상수 목록).
    // 등록 화면(edit_oauth_apps_owned_new.html)이 API 스코프와 시각적으로 구분해 보여주기 위해
    // 별도 메서드로 노출한다.
    fun identityScopes(): List<String> = listOf("openid", "profile", "email")

    // 사이트 전체 앱 목록(관리자 감사/오버사이트 화면용) — DCR로 자동등록된 것과 사전등록된 것
    // 전부 포함한다.
    fun listAll(): List<OAuthRegisteredClient> =
        clientRepository.findAll().sortedBy { it.clientName.lowercase() }

    // 특정 사용자가 셀프서비스로 등록한 앱만(사용자용 목록 화면).
    fun listByOwner(ownerId: Long): List<OAuthRegisteredClient> =
        clientRepository.findByOwnerId(ownerId).sortedBy { it.clientName.lowercase() }

    // id로 조회하되 ownerId가 일치하는 경우에만 반환한다 — 호출부가 이 결과가 null이면 IDOR로 보고
    // 403 처리해야 한다(다른 사용자 소유 앱이거나 애초에 존재하지 않는 id를 구분해서 알려주지 않는다).
    fun findOwned(id: String, ownerId: Long): OAuthRegisteredClient? {
        val client = clientRepository.findById(id).orElse(null) ?: return null
        return if (client.ownerId == ownerId) client else null
    }

    fun findById(id: String): OAuthRegisteredClient? = clientRepository.findById(id).orElse(null)

    data class Registered(val client: OAuthRegisteredClient, val plainSecret: String?)

    fun register(
        clientName: String,
        redirectUri: String,
        confidential: Boolean,
        scopes: List<String>?,
        ownerId: Long?
    ): Registered {
        if (clientName.isBlank()) {
            throw IllegalArgumentException("앱 이름은 필수입니다.")
        }
        if (redirectUri.isBlank()) {
            throw IllegalArgumentException("Redirect URI는 필수입니다.")
        }

        val id = UUID.randomUUID().toString()
        val clientId = UUID.randomUUID().toString()
        val plainSecret = if (confidential) generateSecret() else null

        val client = OAuthRegisteredClient(
            id = id,
            clientId = clientId,
            clientIdIssuedAt = Instant.now(),
            clientSecret = plainSecret?.let { passwordEncoder.encode(it) },
            clientName = clientName,
            // client_secret_basic은 confidential 클라이언트가 Authorization 헤더로 자신을 증명하는
            // 표준 방식(Spring 기본 지원) — none은 PKCE 전용 공개 클라이언트(DCR 클라이언트와 동일한
            // 방식).
            clientAuthenticationMethods = if (confidential) "client_secret_basic" else "none",
            authorizationGrantTypes = "authorization_code,refresh_token",
            redirectUris = redirectUri,
            scopes = (scopes.orEmpty().filter { it in availableScopes() }).joinToString(",").ifEmpty { "issues:read" },
            dynamicallyRegistered = false,
            // confidential 클라이언트는 client_secret로 이미 자신을 증명하므로 PKCE까지 강제하지
            // 않는다(공개 클라이언트만 PKCE 필수 — DCR과 동일한 원칙).
            requireProofKey = !confidential,
            requireAuthorizationConsent = true,
            ownerId = ownerId
        )
        clientRepository.save(client)

        return Registered(client, plainSecret)
    }

    // 이 클라이언트를 참조하는 동의/발급 토큰 레코드도 함께 정리한다 — 삭제 후에도 남아있으면
    // OAuthAuthorizedAppsService가 존재하지 않는 클라이언트를 가리키는 고아 레코드를 만나게 된다.
    // 소유자 검증은 호출부 책임(위 클래스 주석 참고).
    @Transactional
    fun deleteClientAndRelatedRecords(client: OAuthRegisteredClient) {
        consentRepository.deleteByRegisteredClientId(client.id)
        authorizationRepository.deleteByRegisteredClientId(client.id)
        clientRepository.deleteById(client.id)
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
