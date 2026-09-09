package com.github.yonaprojects.yona.config.sso

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal

/**
 * yona-wiki P3-06(엔터프라이즈 SSO) Step3 — `config/sso/YonaOidcUser`/`config/oauth2/YonaOAuth2User`와
 * 동일한 취지. `getName()`이 SAML NameID가 아니라 로컬 User.loginId를 반환해야 기존
 * `authentication.name` 기반 관례(Git/SVN 인증 필터, `userRepository.findByLoginId` 등)가 그대로
 * 동작한다.
 *
 * P3-32(회귀 수정, 2026-09-09) — `YonaUserDetails`가 이미 겪은 것과 동일한 문제(OAuthObjectMapper
 * 참고: OAuth2 동의 처리 과정에서 이 principal이 attributes 맵에 담겨 JSON 직렬화/역직렬화됨)를
 * 여기서도 겪었다. 처음엔 `val user: User`로 JPA 엔티티 전체를 직접 물고 있었는데, 이러면
 * `YonaSaml2AuthenticatedPrincipal` 자신을 화이트리스트에 추가해도 그 안에 중첩된 `User` 타입
 * 역시 별도의 다형성 슬롯이라 다시 `InvalidTypeIdException`이 났다(엔티티를 통째로 화이트리스트에
 * 추가하는 건 이 저장소가 이미 여러 번 겪은 "raw 엔티티 직렬화 시 순환 참조/필드 노출" 문제군과
 * 같은 함정이라 채택하지 않음). `YonaUserDetails`와 동일하게 필요한 값만 원시 타입으로 평탄화하고
 * `@JsonCreator`/`@JsonProperty`로 생성자 파라미터와 getter 기반 직렬화 프로퍼티명을 맞췄다.
 */
class YonaSaml2AuthenticatedPrincipal @JsonCreator constructor(
    @JsonProperty("userId") val userId: Long?,
    @JsonProperty("loginId") private val loginId: String,
    @JsonProperty("attributes") private val attributesVal: Map<String, List<Any>>,
    @JsonProperty("relyingPartyRegistrationId") private val relyingPartyRegistrationIdVal: String?,
    @JsonProperty("sessionIndexes") private val sessionIndexesVal: List<String>
) : Saml2AuthenticatedPrincipal {

    constructor(user: User, delegate: Saml2AuthenticatedPrincipal) : this(
        userId = user.id,
        loginId = user.loginId,
        attributesVal = delegate.attributes,
        relyingPartyRegistrationIdVal = delegate.relyingPartyRegistrationId,
        sessionIndexesVal = delegate.sessionIndexes
    )

    override fun getName(): String = loginId
    override fun getAttributes(): Map<String, List<Any>> = attributesVal
    override fun getRelyingPartyRegistrationId(): String? = relyingPartyRegistrationIdVal
    override fun getSessionIndexes(): List<String> = sessionIndexesVal
}
