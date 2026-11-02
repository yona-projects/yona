---
title: 엔터프라이즈 SSO(SAML 2, OIDC) 구현 결과
type: synthesis
sources: [enterprise-sso-plan.md, ../decisions.md, ../roadmap.md]
updated: 2026-11-02
---

# 엔터프라이즈 SSO 구현 결과 (X-16)

회사의 신원 제공자(IdP)로 Yona에 로그인하게 하는 **추가 개발 항목**이다(`decisions.md` D-80). v1.6에는 없으며 v1.6의 로그인과 계약은 바꾸지 않았다. 계획과 질문은 [enterprise-sso-plan.md](enterprise-sso-plan.md)에 있고, 이 문서는 구현된 동작과 구현하면서 정한 판단을 적는다.

## 1. 구성

| 구분 | 위치 |
|---|---|
| 보안 필터 체인(`/sso/` 아래만, `@Order(2)`) | `sso/SsoSecurityConfig` |
| OIDC 등록(요청마다 DB 설정에서 만든다) | `sso/SsoClientRegistrations`, 발견 문서는 저장할 때 확인해 캐시 |
| SAML 등록(요청마다 DB 설정에서 만든다) | `sso/SsoSaml2Registrations` |
| SAML 응답 검증 제공자 | `sso/SsoSaml2Provider`(서명, 발급자, 대상, 수신 주소, 요청 ID, 시간, 재사용 방지) |
| 로그인 시작 주소와 SP 메타데이터 | `sso/SsoLoginController` |
| 성공과 실패 처리, 계정 맞추기로 잇기 | `sso/SsoLoginHandlers`(`SsoSignInCompleter`) |
| 계정 규칙(안정 식별자, 이메일 연결, 자동 생성) | `domain/sso/SsoAccountService` |
| 설정 저장과 SP 키 관리 | `domain/sso/SsoSettingsService`, `Saml2Certificates` |
| 사이트 관리 화면(탭: 일반, OpenID Connect, SAML 2.0, SAML SP 키. 저장하면 같은 탭이 열린다, `?tab=`) | `web/site/SiteSsoController`, `templates/site/sso.html` |
| 로그인 화면 단추와 SSO 전용 모드 | `sso/SsoLoginOptions`, `templates/user/login.html`, `common/login-dialog.html` |
| 연결된 계정(사용자 설정과 관리자) | `domain/sso/LinkedAccountService`, `web/user/LinkedAccountsController` |
| 지표 | `observability/SsoMetrics`(`yona.sso.logins{protocol, result, initiated}`) |

스프링 시큐리티의 `oauth2-client`와 `saml2-service-provider`는 **SSO 전용 필터 체인에서만** 쓴다. 기존 `YonaAuthenticationFilter`와 섞이지 않고, 성공하면 스프링의 인증 객체는 지우고 `LoginSessionService.start`로 우리 세션 로그인을 시작한다.

## 2. 주소

| 주소 | 용도 |
|---|---|
| `GET /sso/oidc/login?returnTo=` | OIDC 로그인 시작(꺼져 있으면 404) |
| `GET /sso/oidc/callback/oidc` | OIDC 콜백(리디렉션 주소로 IdP에 등록한다) |
| `GET /sso/oidc/initiate?iss=&login_hint=&target_link_uri=` | IdP 시작 로그인(OIDC Core 4절, 설정으로 켠 곳만) |
| `GET /sso/saml2/login?returnTo=` | SAML 로그인 시작 |
| `POST /sso/saml2/acs` | SAML ACS(IdP에 등록한다) |
| `GET /sso/saml2/metadata/saml2` | SP 메타데이터(Entity ID, ACS, 서명과 암호화 인증서) |
| `/sites/sso`, `/sites/sso/...` | 사이트 관리 화면(사이트 관리자만) |
| `/user/editform/linked-accounts` | 연결된 계정(본인) |

돌아갈 곳(`returnTo`, `target_link_uri`, `RelayState`)은 **같은 사이트의 경로만** 받는다(열린 리디렉션 방지).

## 3. 동작

- **OIDC**: 인가 코드 흐름에 PKCE(S256), `state`, `nonce`를 쓰고 ID 토큰의 서명(RS256, RS384, RS512, ES256, ES384, PS256), 발급자, 대상, 만료, nonce를 검증한다. 클레임은 ID 토큰에서만 읽고(사용자 정보 엔드포인트는 부르지 않는다) 토큰은 저장하지 않는다.
- **SAML 2**: 로그인 시작이 AuthnRequest를 만들고 응답은 요청 ID로 짝을 짓는다. 응답 또는 어설션에 서명이 있어야 하고 발급자, Audience, Destination, Recipient, 유효 시간을 검증한다. 같은 응답을 다시 보내면 거절한다.
- **계정**: `발급자|subject`(SAML은 `발급자|NameID`)를 `linked_account`(`sso-oidc`, `sso-saml2`)에 안정 식별자로 저장한다. 처음 보는 신원은 같은 이메일의 로컬 계정에 연결하되 **IdP가 이메일을 확인했다고 알린 경우에만**(OIDC `email_verified`, SAML은 관리자가 신뢰하기로 한 IdP의 속성) 연결한다. 없으면 자동으로 만든다(설정으로 끈다).
- **2단계 인증**: SSO 로그인은 Yona의 2단계 인증을 거치지 않는다(IdP가 MFA를 맡는다, D-80 11번). 로그인 유지 쿠키는 만들지 않는다.
- **SSO 전용 모드**(사이트 관리, 기본 끔): 로컬 비밀번호 로그인, LDAP, 소셜 로그인을 막고 SSO 단추만 보인다. **사이트 관리자만 로컬 로그인을 쓸 수 있다**(비상 경로, `/users/loginform?local=true`). 켜려면 켠 SSO가 하나 이상 있어야 한다.
- **IdP 시작 로그인**(프로토콜마다 관리자 설정, 기본 끔): SAML은 요청 없는 응답을 받되 요청 ID가 없어야 하고 어설션 ID 재사용을 [SsoReplayCache]로 막으며 RelayState는 같은 사이트의 경로만 받는다. OIDC는 `iss`가 설정과 같아야 한다.
- **SAML SP 키**: 앱 안에서 RSA 3072 키쌍과 5년 자체 서명 인증서를 만들거나 PKCS#8 키와 인증서를 올린다(짝 확인). 비밀 키는 `SecretCipher`(용도 `yona.sso.secret`)로 암호화해 저장하고 화면에 다시 보이지 않는다. 다시 만들면 이전 키가 "교체 중"으로 남아 암호화된 어설션을 풀 때 함께 쓰고, 관리자가 IdP 갱신을 확인한 뒤 지운다. 요청 서명은 기본 끔이고 SP 키가 있어야 켤 수 있다.
- **연결된 계정**: 본인은 자기 연결을 보고 해제한다(SSO 전용 모드가 꺼져 있을 때만). 사이트 관리자는 사용자 목록에서 SSO 연결을 모두 해제한다(소셜 연결은 두고, 전용 모드여도 가능). 해제는 `auth.sso_unlinked`로 남는다.
- **감사와 지표**: 로그인 성공은 `auth.login_success`(`details.method`가 `sso-oidc`, `sso-saml2`, 새 사용자인지와 연결인지, IdP 시작이면 `initiated`), 거절과 실패는 `auth.sso_failure`(`details.protocol`, `details.reason`), 설정 변경은 `site.sso_changed`(비밀 값 제외)이다([audit-log.md](audit-log.md)).

### Yona가 OIDC 제공자이면서 사내 IdP로 로그인하는 경우(X-40과 X-16의 조합)

외부 앱(예: Woodpecker)이 Yona로 로그인을 요청하면 인가 서버가 로그인하지 않은 사용자를 `/users/loginform?redirectUrl=/oauth2/resume`로 보낸다. 사용자는 이 화면에서 사내 IdP 단추를 눌러 로그인하고(`returnTo`가 `/oauth2/resume`을 이어받는다), 로그인이 끝나면 보관해 둔 승인 요청이 이어져 승인 화면을 거쳐 외부 앱이 인가 코드와 ID 토큰을 받는다. ID 토큰의 `sub`는 SSO로 만들어졌거나 연결된 Yona 사용자의 번호이다. 사내 IdP 로그인이 거절되면(예: 이메일 미확인) 승인 요청은 이어지지 않는다. 외부 앱이 먼저 사내 IdP로 가는 것이 아니라 **사용자가 Yona 로그인 화면에서 고르는** 구조이며, SSO 전용 모드이면 사내 IdP 단추만 남는다. 시험은 `SsoWithOidcProviderTest`, 실제 확인은 `e2e/ssoprovider.mjs`(외부 앱 대신 `openid-client` 라이브러리)이다.

## 4. 알아 둘 점

- **암호화된 어설션은 응답에도 서명이 있어야 풀린다.** 스프링 시큐리티가 서명 래핑 공격을 막으려고 서명 없는 응답의 암호화 어설션을 해독하지 않는다. 대부분의 IdP(Okta, Entra ID, Keycloak)는 응답과 어설션 모두에 서명할 수 있다.
- IdP가 SP 메타데이터를 읽을 때 이전 키의 인증서는 **암호화용으로만** 공개한다(서명용은 현재 키뿐이다). 요청 서명을 켠 사이트에서 키를 바꿀 때는 IdP에 새 인증서를 등록한 뒤 쓴다.
- 어설션 재사용 기록은 메모리에 두며 서버를 다시 시작하면 비워진다(어설션 유효 시간이 짧아 위험이 작다).
- IdP 시작 로그인은 로그인 CSRF가 있는 방식이다(공격자가 자기 계정의 응답을 피해자에게 보낼 수 있다). 그래서 기본은 끄고 서명 필수, 요청 ID 금지, 재사용 방지, 짧은 유효 시간으로 줄였다.
- 로그아웃은 Yona 세션만 끝낸다(IdP 단일 로그아웃은 추가 논의).
- 시험 중 서버가 만든 사용자의 로그인 ID에서 `sso`는 예약어이다(`/sso/` 주소와 겹치지 않게).

## 5. 시험

- **가짜 IdP(자동 시험)**: OIDC는 JDK HTTP 서버가 Discovery, JWKS, 토큰을 흉내 낸다(`FakeOidcIdp`). SAML은 OpenSAML로 서명, 암호화한 응답을 만든다(`FakeSamlIdp`). 서명, 발급자, 대상, 수신 주소, 요청 ID, 만료, 재사용, 이메일 미확인, 잠긴 사용자, 열린 리디렉션, IdP 시작 로그인, 암호화 어설션, 키 교체, SSO 전용 모드를 확인한다.
- **실제 IdP(수동 확인, 2026-11-02)**: Keycloak 26.4 컨테이너(`e2e/keycloak/realm-corp.json`, 실행은 `e2e/ssokeycloak.mjs`)로 OIDC와 SAML 2 로그인(서명된 응답과 **암호화된 어설션**), IdP 시작 SAML 로그인, 이메일 미확인 거절, 연결된 계정, SSO 전용 모드를 Playwright로 15건 확인했다. 관리자 화면에서 설정하고 IdP 메타데이터는 **주소로 가져왔다**.
