---
title: OAuth2 인증 서버(X-1) 구현 계획
type: plan
updated: 2026-11-02
---

# OAuth2 인증 서버(X-1) 구현 계획

> **상태: 계획만 있다. 선생님이 착수를 지시하기 전에는 구현하지 않는다**(2026-11-02 지시). 결정이 필요한 항목은 [decisions.md](../decisions.md) D-71에 질문으로 올려 두었고, 아직 답을 받지 않았다. 이 계획은 D-71의 권장안을 전제로 쓴다. 답이 달라지면 해당 단계를 고친다.

## 1. 목적

Yona를 OAuth 2.1 인가 서버로 만들어, 사용자의 승인을 받은 외부 앱이 개인용 토큰(PAT)을 손으로 붙여 넣지 않고 Yona의 API에 접근하게 한다. 직접 쓰임은 두 가지이다.

1. **MCP 서버(X-6)의 인증 기반.** Claude 같은 대화형 클라이언트는 원격 서버가 401을 주면 인가 서버를 스스로 찾아(RFC 9728, RFC 8414) 클라이언트를 등록하고(RFC 7591) 브라우저 승인 화면을 띄운 뒤 토큰을 받는다. 이 흐름이 되려면 인가 서버가 있어야 한다.
2. **제3자 앱의 위임 접근**(GitHub OAuth Apps와 같다). 사이트 관리자가 앱을 등록하고, 사용자가 승인 화면에서 범위를 확인하고 허용한다.

PAT(X-5)와 기기 인증(X-37i)은 그대로 두고 병행한다. PAT는 스크립트와 CI, 기기 인증은 CLI, OAuth는 브라우저 승인이 가능한 앱이 쓴다.

## 2. 이 저장소에서 이미 있는 것과 `next`의 구현

| 이미 있는 것 | 이 계획에서 쓰는 방법 |
|---|---|
| 클래식 범위 모델 `ApiScopes`(`repo`, `read:user` 등, D-36)와 범위 규칙 `ApiScopeRules` | OAuth 범위의 이름과 의미를 **그대로 쓴다**(새 범위 체계를 만들지 않는다). 승인 화면은 이 범위를 풀어서 보인다 |
| `ApiTokenService`, 토큰 인증 필터 경로(`/-_-api/v1`은 토큰 전용), `TokenFailureLimiter`, 요청 범위 `YonaAuthentication(viaToken, scopes, tokenId)` | OAuth 액세스 토큰도 **같은 인증 객체와 같은 범위 검사**로 받는다(별도 필터 체계를 만들지 않는다) |
| 기기 인증(`/-_-api/v1/oauth/device/code`, `/-_-api/v1/oauth/token`) | 경로가 겹치지 않게 인가 서버 경로는 `/oauth2/*`로 둔다. 기기 인증이 발급한 토큰은 PAT 취급 그대로 |
| 2단계 인증과 강제 이동(X-2) | 승인 화면(`/oauth2/authorize`)은 로그인한 세션이 필요하므로 2단계와 강제 규칙을 자동으로 따른다 |
| `site_setting`과 "보안" 관리자 화면(X-2) | DCR 허용 여부 같은 설정을 같은 곳에 둔다 |
| 감사 로그(X-9), 지표, 사이트 내보내기·가져오기, 사용자 삭제 처리 | 새 테이블과 이벤트를 같은 규칙으로 붙인다 |
| 설정 화면의 "목록과 추가 두 화면, 목록에는 삭제만" 규칙 | "승인한 앱" 탭에 적용한다 |

`next` 브랜치에 같은 항목이 구현되어 있다(`config/oauth2server/*`, `domain/oauth2server/*`, `OAuthConsentController`, `OAuthAppsAdminController`, 계획서 `p3-07`, `p3-14`). 구조를 참고하되 아래가 다르다.

- `next`는 JWT 액세스 토큰과 `ApiTokenScopeGroup:read|write` 형식의 자체 범위를 쓴다. 이 저장소는 PAT가 GitHub 클래식 범위(D-36)이므로 OAuth도 같은 범위를 쓴다.
- `next`는 보안 필터 체인을 세 개로 나누고(`@Order`) 리소스 서버 체인을 더한다. 이 저장소는 `YonaAuthenticationFilter` 하나로 세션, 토큰, 로그인 유지 쿠키를 가르므로 인가 서버 엔드포인트만 별도 체인에 두고 나머지는 기존 필터에 더한다.
- 이 저장소는 D-70으로 git과 SVN의 HTTP를 클래식 토큰 전용으로 바꾸었고, 2단계 인증이 있다. 두 규칙과 부딪치지 않는지 단계마다 시험한다.

## 3. 설계

### 3-1. 라이브러리

Spring Security의 인가 서버 지원(Spring Authorization Server가 Spring Security 7에 합쳐졌다. 정확한 아티팩트 이름과 Boot 4.1.1과의 호환은 **0단계 스파이크에서 확인**한다)을 쓴다. 인가 코드와 PKCE와 토큰 교환과 갱신 토큰 회전은 보안에 민감해 직접 만들지 않는다. 저장은 JPA로 직접 구현한다(`OAuth2AuthorizationService`, `RegisteredClientRepository`, `OAuth2AuthorizationConsentService`).

### 3-2. 토큰 형식: 불투명(reference) 토큰

JWT가 아니라 `yona_oat_` 접두어의 불투명 토큰을 쓰고, 서버가 해시로 저장한다(PAT와 같은 방식).

- 취소가 즉시 반영된다(JWT는 만료까지 유효해 별도 취소 목록이 필요하다).
- 자원 쪽 검증이 데이터베이스 조회 한 번이라 `YonaAuthenticationFilter`의 토큰 경로에 그대로 더해진다. 서명 키 관리와 회전이 필요 없다.
- 범위가 클래식 범위 문자열이라 PAT와 같은 `ApiScopeRules` 검사를 쓴다.

### 3-3. 엔드포인트

| 경로 | 설명 |
|---|---|
| `GET /oauth2/authorize` | 승인 요청. 로그인하지 않았으면 `/users/loginform?redirectUrl=...`로 보낸다. PKCE(S256) 필수 |
| `POST /oauth2/token` | 인가 코드 교환, 갱신 토큰 교환(회전). 클라이언트 인증은 `none`(공개 클라이언트, PKCE), `client_secret_basic`, `client_secret_post` |
| `POST /oauth2/revoke` | RFC 7009 취소 |
| `POST /oauth2/introspect` | RFC 7662(기밀 클라이언트만) |
| `POST /oauth2/register` | RFC 7591 동적 클라이언트 등록(공개 클라이언트만, 허용 여부는 설정, 속도 제한) |
| `GET /.well-known/oauth-authorization-server` | RFC 8414 메타데이터 |
| `GET /.well-known/oauth-protected-resource[...]` | RFC 9728. 보호 자원(`/-_-api/v1`, 나중에 MCP)별 메타데이터 |
| `GET/POST /oauth2/consent` | 승인 화면(GitHub 모양) |

`oauth2`와 `.well-known`은 로그인 ID 예약어(`LoginIdRules`)에 더해야 한다(`authenticate` 등은 이미 있다). v1.6 라우트와 겹치지 않는지 `RouteParityTest` 계열 시험으로 확인한다.

### 3-4. 자원 쪽(`/-_-api/v1`)

`YonaAuthenticationFilter`의 API 경로가 `Authorization: Bearer yona_oat_...`를 받아 해시로 찾고, 만료와 취소를 확인한 뒤 `YonaAuthentication(viaToken=true, scopes=승인 범위, tokenId)`를 만든다. 범위는 **승인한 범위와 주인의 실제 권한의 교집합**이다(PAT와 같다). `resource`(RFC 8707)가 있으면 발급된 대상(audience)과 요청 경로를 비교한다. 실패는 `TokenFailureLimiter`로 세고 401 `WWW-Authenticate`에 보호 자원 메타데이터 주소를 싣는다(RFC 9728).

git과 SVN의 HTTP는 D-70대로 클래식 토큰(`yona_pat_`)만 받는다. OAuth 토큰을 받을지는 D-71에 질문으로 올렸다.

### 3-5. 데이터

| 테이블 | 내용 |
|---|---|
| `oauth_client` | 클라이언트 번호, 이름, 설명, 홈 주소, 리디렉션 주소들, 공개/기밀, 비밀 값 해시(기밀), 허용 범위, 등록 방식(관리자, 동적), 등록한 사용자, 생성 시각 |
| `oauth_authorization` | 인가 코드, 액세스 토큰 해시, 갱신 토큰 해시, 사용자, 클라이언트, 승인 범위, 발급 대상(resource), 만료, 취소 시각, PKCE 값 |
| `oauth_consent` | 사용자와 클라이언트별 승인한 범위(다음 승인 때 이미 허용한 범위는 묻지 않는다) |

모두 사이트 내보내기·가져오기에 넣고(v1.6 파일에는 없다), 사용자 삭제 처리에서 지운다. 해시 열 너비는 `SchemaColumnWidener` 규칙을 확인한다.

### 3-6. 수명과 정책

| 항목 | 값 |
|---|---|
| 인가 코드 | 5분, 한 번만 |
| 액세스 토큰 | 1시간 |
| 갱신 토큰 | 30일, 쓸 때마다 회전, 이미 쓴 토큰이 다시 오면 그 인가 전체를 취소 |
| PKCE | S256만. 모든 클라이언트에 필수 |
| 리디렉션 주소 | 등록한 값과 정확히 일치. 공개 클라이언트의 `http`는 루프백(`localhost`, `127.0.0.1`, `[::1]`)만 허용 |

### 3-7. 화면

- **승인 화면**: 앱 이름과 등록한 사용자, 요청한 범위를 풀어서(`repo` = 프로젝트 읽기와 쓰기 등) 보이고 허용과 거부 단추를 둔다. 이미 허용한 범위만 요청하면 묻지 않고 넘어간다. 거부는 `error=access_denied`로 돌려보낸다.
- **설정 탭 "승인한 앱"**(`/user/editform/oauth-apps`): 목록과 취소 단추만(GPG, SSH, 2단계 인증 탭과 같은 규칙).
- **관리자 "OAuth 앱"**(`/sites/oauth-apps`): 앱 등록(공개/기밀, 리디렉션 주소, 허용 범위), 목록, 삭제. 기밀 비밀 값은 만든 직후 한 번만 보인다. 동적 등록으로 생긴 앱도 여기서 보고 지울 수 있다.
- 관리자 "보안"에 "동적 클라이언트 등록 허용" 설정을 더한다.

### 3-8. 감사, 지표, 보안

- 감사: `oauth.client_registered`, `oauth.client_deleted`, `oauth.authorized`(사용자가 승인), `oauth.denied`, `oauth.token_issued`(교환 성공, 갱신 구분), `oauth.revoked`, `oauth.refresh_reuse_detected`(탈취 의심), 토큰 실패는 `auth.token_failure`에 채널 `oauth`. 비밀 값과 토큰은 남기지 않는다.
- 지표: `yona.oauth.tokens{grant,client_type,result}`, `yona.oauth.authorizations{result}`.
- 동적 등록은 주소당 분당 10회 제한(`next`와 같은 값). 승인 화면에는 클릭재킹을 막는 `frame-ancestors 'none'`을 건다.
- 리디렉션 주소의 `javascript:`와 데이터 스킴, 프래그먼트 포함 값을 거부한다.

## 4. 단계 (모두 TDD, 단계마다 위키와 커밋)

| 단계 | 내용 | 확인 |
|---|---|---|
| 0 | **스파이크**: Boot 4.1.1에서 인가 서버 지원 아티팩트와 시작 설정, 기존 `SecurityConfig`와 `YonaAuthenticationFilter`와 같이 쓰는 필터 체인 구성, `/oauth2/*`와 `.well-known`이 v1.6 라우트와 겹치지 않는지. 결과를 D-71 확정 뒤 이 문서에 반영 | 작은 시험 3~4개 |
| 1 | 테이블과 저장소와 서비스(클라이언트, 인가, 승인), 사이트 내보내기와 삭제 처리, 로그인 ID 예약어 | 서비스 시험, `DataTablesTest`, 스키마 시험 |
| 2 | 인가 서버 엔드포인트: authorize, token(인가 코드, 갱신 회전, 재사용 탐지), revoke, introspect, 메타데이터(RFC 8414), PKCE 강제, 리디렉션 검증 | 전체 흐름을 거치는 MockMvc 시험(인가 코드부터 토큰까지), RFC 시험 벡터 |
| 3 | 승인 화면과 로그인 이동(redirectUrl, 2단계 인증과 강제 이동과 함께), 이미 허용한 범위 건너뛰기, 거부 | 웹 시험, Playwright 화면 |
| 4 | 자원 쪽: `YonaAuthenticationFilter`의 Bearer `yona_oat_` 경로, 범위 교집합, 취소와 만료, audience, `WWW-Authenticate`와 보호 자원 메타데이터(RFC 9728), 실패 제한 | 기존 외부 API 시험을 OAuth 토큰으로도 돌리는 공통 시험 |
| 5 | 동적 클라이언트 등록(RFC 7591), 속도 제한, 설정(관리자 "보안"에 켜기와 끄기) | 등록 시험, 속도 제한 시험 |
| 6 | 관리자 "OAuth 앱" 화면과 설정 "승인한 앱" 탭, 메시지 5개 언어 | 화면 시험, Playwright |
| 7 | 감사 이벤트, 지표, OpenAPI 문서와 구조 시험(`AuditCoverageTest` 등), 위키(`oauth2-server.md`, 구성 키, 감사, 차이 장부), 전체 시험 | 전체 시험 |
| 8 | **실제 클라이언트 확인**: 공식 MCP Java SDK나 `curl`과 간단한 앱으로 인가 코드 + PKCE 전체를 외부 프로세스로 확인하고, 브라우저에서 승인 화면을 Playwright로 확인 | 캡처와 로그 |

X-6(MCP 서버)은 이 계획의 범위가 아니다. 단계 4의 보호 자원 레지스트리가 MCP 경로를 더할 자리를 남긴다.

## 5. 위험과 확인할 것

- Spring Security 7과 Boot 4.1.1에서 인가 서버 설정 방식(0단계). 안 맞으면 선택지를 다시 올린다.
- 불투명 토큰과 라이브러리의 토큰 생성기 연결(`OAuth2TokenGenerator`)이 필요하다. 라이브러리의 `OAuth2Authorization` 직렬화(속성 맵)를 JPA에 담는 부분이 `next`에서도 가장 까다로웠다.
- 기존 `SecurityConfig`가 하나의 체인에 사용자 정의 필터를 쓰므로, 인가 서버 체인이 앞에서 요청을 가로채는 범위를 엔드포인트로 좁혀야 한다.
- 승인 화면 도입으로 로그인 직후 이동(`redirectUrl`)이 긴 주소를 다룬다. v1.6의 `redirectUrl` 처리(외부 주소로 열리는 결함, R-06)를 승인 흐름에서 악용하지 못하게 `/oauth2/authorize`만 허용하는 확인이 필요하다.
- 이 단계에서 정하지 않는 것: OIDC("Sign in with yona", ID 토큰, `/userinfo`)는 D-71의 질문 5번이다. 사용자가 만드는 앱(사용자 소유)은 질문 3번이다.
