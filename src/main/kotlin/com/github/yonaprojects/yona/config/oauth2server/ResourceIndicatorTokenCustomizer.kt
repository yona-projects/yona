package com.github.yonaprojects.yona.config.oauth2server

import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.core.oidc.StandardClaimNames
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.stereotype.Component

// RFC8707 §3의 공식 오류 코드. Spring의 OAuth2ErrorCodes 상수 목록에는 아직 없어 리터럴로 둔다.
private const val INVALID_TARGET = "invalid_target"

// RFC8707(Resource Indicators) 오디언스 스탬핑. Spring Authorization Server가 기본 제공하지
// 않는 이 부분만 직접 구현한다(PKCE/DCR은 Spring이 기본 제공).
//
// 발급 가능한 리소스는 ProtectedResource(MCP/API) 레지스트리로 일반화했다. 클라이언트는
// `/oauth2/token` 요청에 `resource` 파라미터로 발급받고 싶은 리소스의 URI를 명시해야 한다(MCP는
// 스펙상 필수, 일반 API 클라이언트에도 동일하게 강제 — "토큰이 어느 리소스 서버 대상인지 항상
// 명시적으로 선언한다"는 이 서버의 설계 원칙을 리소스 종류와 무관하게 일관되게 유지하기 위함).
// 이 값을 그대로 액세스 토큰의 `aud` 클레임에 스탬핑해, 리소스 서버(ResourceServerConfig)가
// 자신 앞으로 발급된 토큰인지 검증할 수 있게 한다 — 다른 리소스 서버용으로 발급된 토큰을 그대로
// 받아주는 "토큰 패스스루" 취약점을 막는 핵심 지점이다.
//
// ID 토큰의 identity 클레임(name/email/email_verified/picture) 매핑도 이 클래스에 합쳤다(별도
// 클래스로 분리하지 않은 이유가 중요): Spring Authorization
// Server의 OAuth2ConfigurerUtils.getJwtCustomizer()는 `OAuth2TokenCustomizer<JwtEncodingContext>`
// 타입 빈을 `ApplicationContext.getBeanProvider(type).getIfUnique()`로 조회한다(공식 소스 확인) —
// getIfUnique()는 후보가 2개 이상이면 @Primary가 없는 한 그냥 null을 반환한다(예외를 던지지
// 않는다!). 즉 이 타입의 빈을 하나 더 추가하면 "둘 다 적용"이 아니라 "둘 다 무시"된다 — RFC8707
// 오디언스 스탬핑(핵심 보안 장치)까지 조용히 사라지는 심각한 회귀였을 것이다. 그래서
// 이 프로젝트 전체에서 `OAuth2TokenCustomizer<JwtEncodingContext>` 빈은 반드시 이 클래스
// 하나여야 한다.
@Component
class ResourceIndicatorTokenCustomizer(
    @Value("\${yona.base-url:http://localhost:8080}")
    private val baseUrl: String,
    private val userRepository: UserRepository,
    private val attachmentRepository: AttachmentRepository
) : OAuth2TokenCustomizer<JwtEncodingContext> {

    override fun customize(context: JwtEncodingContext) {
        when {
            context.tokenType == OAuth2TokenType.ACCESS_TOKEN -> customizeAccessToken(context)
            context.tokenType.value == OidcParameterNames.ID_TOKEN -> customizeIdToken(context)
        }
    }

    private fun customizeAccessToken(context: JwtEncodingContext) {
        val grant = context.getAuthorizationGrant<Authentication>()
        val resource = (grant as? OAuth2AuthorizationGrantAuthenticationToken)
            ?.additionalParameters
            ?.get(OAuth2ParameterNames.RESOURCE) as? String

        if (resource.isNullOrBlank() || ProtectedResource.fromUri(resource, baseUrl) == null) {
            val knownResources = ProtectedResource.entries.joinToString(", ") { it.uri(baseUrl) }
            throw OAuth2AuthenticationException(
                OAuth2Error(
                    INVALID_TARGET,
                    "resource 파라미터가 없거나 알려진 리소스 서버($knownResources)를 가리키지 않습니다.",
                    null
                )
            )
        }

        context.claims.audience(listOf(resource))
    }

    // "UserInfo는 profile+email 스코프 전부 노출, GitHub OAuth App과 동등한 수준"을 그대로
    // 구현한다. sub은 JwtGenerator가 이미
    // principal.getName()(로그인 세션의 Authentication.name = loginId, YonaUserDetails 등 이
    // 프로젝트의 모든 UserDetails 구현체가 공통으로 따르는 관례)으로 채워두므로(공식 소스
    // JwtGenerator.generate() 확인) 여기서 다시 설정할 필요가 없다 — profile/email 스코프가 허용한
    // 클레임만 추가로 얹는다. `/userinfo`는 별도 커스텀 매퍼 없이 프레임워크 기본
    // DefaultOidcUserInfoMapper를 그대로 쓴다 — 그 기본 구현이 "ID 토큰에 이미 있는 클레임을
    // 액세스 토큰의 authorized scope로 다시 한번 필터링해 반환"하는 방식이라(공식 소스 확인),
    // 여기서 authorizedScopes 기준으로 클레임을 먼저 걸러 ID 토큰에 넣어두기만 하면
    // "스코프가 클레임을 게이트"하는 요구사항이 ID 토큰과 /userinfo 양쪽에 동시에 자동으로
    // 적용된다 — 커스텀 UserInfo 매퍼를 따로 만드는 과잉설계를 피할 수 있다.
    private fun customizeIdToken(context: JwtEncodingContext) {
        val principal = context.getPrincipal<Authentication>() ?: return
        val user = userRepository.findByLoginId(principal.name).orElse(null) ?: return
        val scopes = context.authorizedScopes

        if (scopes.contains(OidcScopes.PROFILE)) {
            context.claims.claim(StandardClaimNames.NAME, user.name)
            context.claims.claim(StandardClaimNames.PICTURE, resolvePictureUrl(user))
        }
        if (scopes.contains(OidcScopes.EMAIL)) {
            context.claims.claim(StandardClaimNames.EMAIL, user.email)
            context.claims.claim(StandardClaimNames.EMAIL_VERIFIED, true)
        }
    }

    // User.avatarUrl은 컨트롤러가 미리 채워주는 @Transient avatarId(fillAvatarId() 패턴, 예:
    // UserViewController)에 의존하는 상대경로 getter라, 여기서는 그 패턴을 직접 재현하되 값을
    // 엔티티에 되써넣지 않고(트랜잭션 범위 밖의 조회 전용 엔티티라 불필요한 부작용을 피함) 절대
    // URL로 반환한다 — OIDC picture 클레임은 제3자 클라이언트가 그대로 렌더링해야 하므로 이
    // 애플리케이션 내부 화면 렌더링 전용 상대경로로는 안 된다.
    private fun resolvePictureUrl(user: User): String {
        val avatarId = user.id?.let { id ->
            attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, id.toString())
                .lastOrNull()?.id
        }
        val path = avatarId?.let { "/files/$it" } ?: user.avatarUrl(64)
        return "$baseUrl$path"
    }
}
