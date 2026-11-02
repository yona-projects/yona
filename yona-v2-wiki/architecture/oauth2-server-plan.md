---
title: OAuth2 인증 서버(X-1) 구현 계획
type: plan
updated: 2026-11-02
---

# OAuth2 인증 서버(X-1) 구현 계획

> **상태: 계획만 있다. 선생님이 착수를 지시하기 전에는 구현하지 않는다**(2026-11-02 지시). 결정은 [decisions.md](../decisions.md) D-71에서 2026-11-02에 확정되었다(OIDC는 X-40으로 미루고, git과 SVN HTTP에는 OAuth 토큰을 받지 않는다).

## 1. 목적

Yona를 OAuth 2.1 인가 서버로 만들어, 사용자의 승인을 받은 외부 앱이 개인용 토큰(PAT)을 손으로 붙여 넣지 않고 Yona의 API에 접근하게 한다. 직접 쓰임은 두 가지이다.

1. **MCP 서버(X-6)의 인증 기반.** Claude 같은 대화형 클라이언트는 원격 서버가 401을 주면 인가 서버를 스스로 찾아(RFC 9728, RFC 8414) 클라이언트를 등록하고(CIMD) 브라우저 승인 화면을 띄운 뒤 토큰을 받는다. 이 흐름이 되려면 인가 서버가 있어야 한다.
2. **제3자 앱의 위임 접근**(GitHub OAuth Apps와 같다). 사이트 관리자가 앱을 등록하고, 사용자가 승인 화면에서 범위를 확인하고 허용한다.

PAT(X-5)와 기기 인증(X-37i)은 그대로 두고 병행한다. PAT는 스크립트와 CI, 기기 인증은 CLI, OAuth는 브라우저 승인이 가능한 앱이 쓴다.

## 2. 이 저장소에서 이미 있는 것과 `next`의 구현

| 이미 있는 것 | 이 계획에서 쓰는 방법 |
|---|---|
| 클래식 범위 모델 `ApiScopes`(`repo`, `read:user` 등, D-36)와 범위 규칙 `ApiScopeRules` | OAuth 범위의 이름과 의미를 **그대로 쓴다**(새 범위 체계를 만들지 않는다). 승인 화면은 이 범위를 풀어서 보인다 |
| `ApiTokenService`, 토큰 인증 필터 경로(`/-_-api/v1`은 토큰 전용), `TokenFailureLimiter`, 요청 범위 `YonaAuthentication(viaToken, scopes, tokenId)` | OAuth 액세스 토큰도 **같은 인증 객체와 같은 범위 검사**로 받는다(별도 필터 체계를 만들지 않는다) |
| 기기 인증(`/-_-api/v1/oauth/device/code`, `/-_-api/v1/oauth/token`) | 경로가 겹치지 않게 인가 서버 경로는 `/oauth2/*`로 둔다. 기기 인증이 발급한 토큰은 PAT 취급 그대로 |
| 2단계 인증과 강제 이동(X-2) | 승인 화면(`/oauth2/authorize`)은 로그인한 세션이 필요하므로 2단계와 강제 규칙을 자동으로 따른다 |
| `site_setting`과 "보안" 관리자 화면(X-2) | CIMD 허용 여부 같은 설정을 같은 곳에 둔다 |
| 감사 로그(X-9), 지표, 사이트 내보내기·가져오기, 사용자 삭제 처리 | 새 테이블과 이벤트를 같은 규칙으로 붙인다 |
| 설정 화면 구성 규칙(GPG, SSH 키는 목록과 추가 두 화면, 삭제만) | 앱 목록은 수정과 비밀 값 재발급이 있어 한 화면의 구역으로 두고 만들기와 수정만 별도 화면으로 한다(3-7절) |

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
| (등록 엔드포인트 없음) | **동적 클라이언트 등록(RFC 7591)은 하지 않는다**(D-71 4번 변경). 자동 등록은 CIMD로만 한다: `client_id`가 `https://` 주소이면 `/oauth2/authorize` 요청 때 인가 서버가 그 주소의 문서를 가져와 확인한다(3-9절) |
| `GET /.well-known/oauth-authorization-server` | RFC 8414 메타데이터 |
| `GET /.well-known/oauth-protected-resource[...]` | RFC 9728. 보호 자원(`/-_-api/v1`, 나중에 MCP)별 메타데이터 |
| `GET/POST /oauth2/consent` | 승인 화면(GitHub 모양) |

`oauth2`와 `.well-known`은 로그인 ID 예약어(`LoginIdRules`)에 더해야 한다(`authenticate` 등은 이미 있다). v1.6 라우트와 겹치지 않는지 `RouteParityTest` 계열 시험으로 확인한다.

### 3-4. 자원 쪽(`/-_-api/v1`)

`YonaAuthenticationFilter`의 API 경로가 `Authorization: Bearer yona_oat_...`를 받아 해시로 찾고, 만료와 취소를 확인한 뒤 `YonaAuthentication(viaToken=true, scopes=승인 범위, tokenId)`를 만든다. 범위는 **승인한 범위와 주인의 실제 권한의 교집합**이다(PAT와 같다). `resource`(RFC 8707)가 있으면 발급된 대상(audience)과 요청 경로를 비교한다. 실패는 `TokenFailureLimiter`로 세고 401 `WWW-Authenticate`에 보호 자원 메타데이터 주소를 싣는다(RFC 9728).

git과 SVN의 HTTP는 D-70대로 클래식 토큰(`yona_pat_`)만 받는다. **OAuth 토큰(`yona_oat_`)은 받으면 안 된다**(D-71 6번, 선생님 결정). 시험으로 고정한다: `yona_oat_` 값을 git과 SVN HTTP의 Basic 비밀번호 칸에 넣으면 비밀번호와 같이 401로 거부하고 실패로 세지 않는다.

### 3-5. 데이터

| 테이블 | 내용 |
|---|---|
| `oauth_client` | 클라이언트 번호, 이름, 설명, 홈 주소, 리디렉션 주소들, 공개/기밀, 비밀 값 해시(기밀), 허용 범위, 등록 방식(`USER` 직접 등록, `CIMD` 문서에서 가져온 캐시 행). CIMD 행은 문서 주소를 클라이언트 번호로 쓰고 문서의 내용과 가져온 시각과 만료 시각을 담는다, **소유자 종류(`USER`, `ORGANIZATION`)와 소유자 번호**, 생성 시각. 이번 구현은 `USER`만 쓰고, 조직 소유 화면은 X-39(X-1 이후)에서 연다. 테이블은 처음부터 이 구조로 잡아 나중에 바꾸지 않는다. 비밀 값 재발급과 리디렉션 주소 수정을 지원한다 |
| `oauth_authorization` | 인가 코드, 액세스 토큰 해시, 갱신 토큰 해시, 사용자, 클라이언트, 승인 범위, 발급 대상(resource), 만료, 취소 시각, PKCE 값 |
| `oauth_consent` | 사용자와 클라이언트별 승인한 범위(다음 승인 때 이미 허용한 범위는 묻지 않는다) |

모두 사이트 내보내기·가져오기에 넣고(v1.6 파일에는 없다), 사용자 삭제 처리에서 지운다. 해시 열 너비는 `SchemaColumnWidener` 규칙을 확인한다.

### 3-5a. OAuth 앱에 주지 않는 범위

사이트 관리자 범위(`site_admin`)와 SSH 키, GPG 키, 배포 키의 모든 범위(`admin:`, `write:`, `read:` 각각 3종, 총 9개)는 OAuth 앱이 요청할 수 없고 앱을 만들 때도 고를 수 없다(D-71 11번, 선생님 결정). 앱이 자기 SSH 키를 등록해 계정에 지속적으로 접근하는 일을 막기 위해서이다. 이 범위가 필요하면 개인용 토큰(PAT)을 쓴다.

### 3-6. 수명과 정책

| 항목 | 값 |
|---|---|
| 인가 코드 | 5분, 한 번만 |
| 액세스 토큰 | 1시간 |
| 갱신 토큰 | 30일, 쓸 때마다 회전, 이미 쓴 토큰이 다시 오면 그 인가 전체를 취소 |
| PKCE | S256만. 모든 클라이언트에 필수 |
| 리디렉션 주소 | 등록한 값과 정확히 일치. 공개 클라이언트의 `http`는 루프백(`localhost`, `127.0.0.1`, `[::1]`)만 허용 |

### 3-7. 화면

Yona가 OAuth **제공자**가 되므로 "앱을 만드는 쪽(개발자)"과 "앱을 승인하는 쪽(사용자)" 화면이 모두 필요하다. Forgejo(설정의 Applications)와 GitHub(Developer settings, Authorized OAuth Apps)처럼 **한 곳에서 둘을 다룬다**.

| 화면 | 주소 | 내용 |
|---|---|---|
| **설정 탭 "OAuth 앱"** (한 화면, 두 구역) | `/user/editform/oauth-apps` | ① **내가 만든 앱**: 이름, 클라이언트 ID, 공개/기밀, 리디렉션 주소를 목록으로 보이고 앱마다 "수정"(이름, 홈 주소, 리디렉션 주소), "비밀 값 다시 만들기"(기밀 앱), "삭제" 단추를 둔다. 위쪽에 "새 앱 만들기" 단추. ② **승인한 앱**: 내가 승인한 외부 앱과 범위, 승인한 날짜를 목록으로 보이고 "취소"(그 앱의 모든 토큰이 즉시 무효) 단추를 둔다 |
| 앱 만들기와 수정 | `/user/editform/oauth-apps/new`, `/user/editform/oauth-apps/{id}` | 이름, 설명, 홈 주소, 리디렉션 주소(여러 줄), 공개/기밀 선택. 만든 직후(그리고 비밀 값을 다시 만든 직후) 기밀 앱의 **클라이언트 비밀 값을 한 번만** 보인다. 수정 화면에는 클라이언트 ID와 가려진 비밀 값 자리, 이 앱을 승인한 사용자 수가 나온다 |
| **승인(동의) 화면** | `/oauth2/consent` | 외부 앱이 접근을 요청할 때 사용자가 본다. 앱 이름과 만든 사용자, 요청 범위를 풀어서 보이고 허용과 거부 단추를 둔다. 이미 허용한 범위만 요청하면 건너뛴다 |
| **관리자 "OAuth 앱"** | `/sites/oauth-apps` | **사이트 전체의 앱 목록**(소유자, 등록 방식, 승인한 사용자 수). 관리자는 문제 있는 앱을 삭제(모든 토큰 취소)할 수 있다. CIMD로 가져온 앱도 여기에 보이고 지울 수 있다 |
| 관리자 "보안" | `/sites/security` | "CIMD 클라이언트 허용"과 "사용자의 앱 만들기 허용" 설정을 더한다 |

이번 구현에서 앱 소유자는 사용자 한 명이다. 조직이 앱을 소유하는 기능(조직 설정 화면의 앱 구역, 조직 관리자만 만들고 고침, Gitea와 같다)은 **X-39로 X-1이 끝난 뒤에 진행한다**(선생님 결정, D-71 질문 3번). 테이블은 소유자 종류와 번호로 잡아 두므로 그때 테이블을 바꾸지 않는다. 목록과 추가를 두 화면으로 나누는 기존 규칙(GPG, SSH 키)은 **삭제만 있는 목록**에 적용하던 것이라, 수정과 비밀 값 재발급이 있는 앱 목록에는 맞지 않으므로 Forgejo처럼 한 화면의 구역으로 두고 만들기와 수정만 별도 화면으로 한다.

로그인 화면은 새로 만들지 않는다. 로그인하지 않은 채 승인 요청이 오면 기존 `/users/loginform`으로 보내고 끝나면 승인 화면으로 돌아온다(2단계 인증과 강제 이동이 자동으로 적용된다).

### 3-8. 감사, 지표, 보안

- 감사: `oauth.client_registered`, `oauth.client_deleted`, `oauth.authorized`(사용자가 승인), `oauth.denied`, `oauth.token_issued`(교환 성공, 갱신 구분), `oauth.revoked`, `oauth.refresh_reuse_detected`(탈취 의심), 토큰 실패는 `auth.token_failure`에 채널 `oauth`. 비밀 값과 토큰은 남기지 않는다.
- 지표: `yona.oauth.tokens{grant,client_type,result}`, `yona.oauth.authorizations{result}`.
- CIMD 문서 가져오기는 주소당 분당 10회, 같은 문서 주소는 캐시 시간 안에 다시 가져오지 않는다. 승인 화면에는 클릭재킹을 막는 `frame-ancestors 'none'`을 건다.
- 리디렉션 주소의 `javascript:`와 데이터 스킴, 프래그먼트 포함 값을 거부한다.

### 3-9. Claude Code 연동

Claude Code는 **MCP 서버**에 붙을 때 OAuth를 쓴다. 즉 Claude Code가 접속할 주소는 MCP 서버(X-6)이고, X-1은 그 서버가 가리키는 **인가 서버**이다. 흐름은 다음과 같다(MCP 인가 규격 기준, 출처는 아래 참고).

1. Claude Code가 MCP 주소를 부르면 서버가 401과 `WWW-Authenticate`로 보호 자원 메타데이터 주소를 알려 준다(RFC 9728).
2. Claude Code가 인가 서버 메타데이터(RFC 8414)를 읽는다.
3. **등록**: 클라이언트 ID 메타데이터 문서(CIMD, 인가 서버 메타데이터에 `client_id_metadata_document_supported`가 있을 때)로 스스로 등록한다. **이 계획은 CIMD를 구현하고 동적 클라이언트 등록(DCR)은 하지 않는다**(D-71 4번과 12번, 선생님 결정). CIMD가 안 되는 클라이언트는 수동 등록을 쓴다.
4. 브라우저가 열리고 승인 화면에서 사용자가 허용한다(PKCE, `resource`).
5. 로컬 콜백(`http://localhost:<포트>/callback`)으로 코드를 받아 토큰으로 바꾼다.

두 가지 등록 길이 있다.

| 길 | 방법 | X-1에서 필요한 것 |
|---|---|---|
| **자동**(CIMD, Claude Code와 Codex) | `claude mcp add --transport http yona https://호스트/mcp` 뒤 `/mcp`로 인증. 별도 입력이 없다 | CIMD 지원과 SSRF 방어, 메타데이터, 로컬 콜백 허용 |
| **수동**(미리 등록) | 사용자가 설정의 "OAuth 앱"에서 공개 앱을 만들고 리디렉션 주소를 `http://localhost:8080/callback`처럼 등록한 뒤 `claude mcp add ... --client-id <ID> --callback-port 8080`(기밀 앱이면 `--client-secret`도) | 앱 만들기 화면, 공개 앱 지원 |

이를 위해 계획에 더하는 규칙은 다음과 같다.

- **CIMD 처리**: `client_id`가 `https://` 주소이면 인가 서버가 그 문서를 가져와 확인한다. 안전 규칙은 HTTPS만, 이름을 풀어 얻은 주소가 사설, 루프백, 링크 로컬이면 거부(가져오는 시점의 주소로 검사), 리디렉션 따라가지 않기, 응답 크기와 시간 제한, 짧은 캐시, 문서 안의 `client_id`가 요청한 주소와 같아야 함, 리디렉션 주소는 문서에 있는 것만 허용, 승인 화면에 문서의 이름과 도메인을 보임이다. 공개 클라이언트(`token_endpoint_auth_method: none`)만 받는다.
- **클라이언트별 공식 문서 확인(2026-11-02)**: Claude Code(`code.claude.com/docs/en/mcp`)는 CIMD와 동적 등록을 모두 지원하고 자동으로 찾으며, 미리 등록한 앱은 `--client-id`, `--client-secret`, 고정 포트는 `--callback-port`이다. Codex(OpenAI MCP 문서)는 인가 서버가 CIMD를 광고하면 CIMD를 고르고 클라이언트 ID를 주면 항상 그것이 우선한다. Antigravity(`antigravity.google/docs/mcp`)는 동적 등록 서버에는 자동으로 붙고 **CIMD는 문서에 없다**. 그래서 선생님 결정(D-71 4번, 12번)으로 **CIMD를 구현하고 동적 등록은 하지 않으며, Antigravity는 수동 등록으로만 유지한다**: 설정의 "OAuth 앱"에서 공개 앱을 만들고 리디렉션 `https://antigravity.google/oauth-callback`(HTTPS 주소는 허용한다)을 등록한 뒤 클라이언트 ID를 Antigravity에 직접 입력한다. 루프백 `http`는 `localhost`와 `127.0.0.1`을 같게 보고 포트를 무시한다(Claude Code의 한 버전이 `127.0.0.1`을 보낸 적이 있다).
- **루프백 리디렉션은 포트를 무시한다**(RFC 8252): 등록한 주소가 `http://localhost/callback`이나 `http://127.0.0.1/callback`이면 요청의 포트가 달라도 허용한다. Claude Code는 기본으로 임의의 로컬 포트를 쓰기 때문이다. 루프백이 아닌 `http` 주소는 계속 거부한다.
- **요청 범위가 없을 때의 기본 범위**: Claude Code가 `scope`를 보내지 않을 수 있으므로, 앱의 허용 범위 안에서 기본 범위(읽기 계열)를 정한다. 구체적인 목록은 단계 2에서 제시한다.
- **헤드리스 환경**(`claude -p`, Agent SDK)은 브라우저 인증을 못 하므로 PAT를 헤더로 쓰는 기존 길을 안내한다.
- **MCP 서버(X-6)가 있어야 Claude Code로 끝까지 확인할 수 있다.** X-1만 끝난 시점의 확인은 공식 MCP Java SDK 클라이언트나 `curl`과 간단한 앱으로 같은 흐름을 외부에서 돌려서 한다(단계 8). X-6이 끝나면 실제 Claude Code로 다시 확인한다.

## 4. 단계 (모두 TDD, 단계마다 위키와 커밋)

| 단계 | 내용 | 확인 |
|---|---|---|
| 0 | **스파이크**: Boot 4.1.1에서 인가 서버 지원 아티팩트와 시작 설정, 기존 `SecurityConfig`와 `YonaAuthenticationFilter`와 같이 쓰는 필터 체인 구성, `/oauth2/*`와 `.well-known`이 v1.6 라우트와 겹치지 않는지. 결과를 D-71 확정 뒤 이 문서에 반영 | 작은 시험 3~4개 |
| 1 | 테이블과 저장소와 서비스(클라이언트, 인가, 승인), 사이트 내보내기와 삭제 처리, 로그인 ID 예약어 | 서비스 시험, `DataTablesTest`, 스키마 시험 |
| 2 | 인가 서버 엔드포인트: authorize, token(인가 코드, 갱신 회전, 재사용 탐지), revoke, introspect, 메타데이터(RFC 8414), PKCE 강제, 리디렉션 검증 | 전체 흐름을 거치는 MockMvc 시험(인가 코드부터 토큰까지), RFC 시험 벡터 |
| 3 | 승인 화면과 로그인 이동(redirectUrl, 2단계 인증과 강제 이동과 함께), 이미 허용한 범위 건너뛰기, 거부 | 웹 시험, Playwright 화면 |
| 4 | 자원 쪽: `YonaAuthenticationFilter`의 Bearer `yona_oat_` 경로, 범위 교집합, 취소와 만료, audience, `WWW-Authenticate`와 보호 자원 메타데이터(RFC 9728), 실패 제한 | 기존 외부 API 시험을 OAuth 토큰으로도 돌리는 공통 시험 |
| 5 | **CIMD**(문서 가져오기와 검증, SSRF 방어, 캐시, 속도 제한, 메타데이터의 `client_id_metadata_document_supported`), 설정(관리자 "보안"에 CIMD와 사용자 앱 만들기의 켜기와 끄기) | CIMD 시험(SSRF 거부 사례 포함), 속도 제한 시험 |
| 6 | 설정 탭 "OAuth 앱"(내가 만든 앱, 승인한 앱), 앱 만들기와 수정과 비밀 값 재발급, 관리자 "OAuth 앱" 목록, 메시지 5개 언어 | 화면 시험, Playwright |
| 7 | 감사 이벤트, 지표, OpenAPI 문서와 구조 시험(`AuditCoverageTest` 등), 위키(`oauth2-server.md`, 구성 키, 감사, 차이 장부), 전체 시험 | 전체 시험 |
| 8 | **실제 클라이언트 확인**: 공식 MCP Java SDK나 `curl`과 간단한 앱으로 인가 코드 + PKCE 전체를 외부 프로세스로 확인하고, 브라우저에서 승인 화면을 Playwright로 확인 | 캡처와 로그 |

X-6(MCP 서버)은 이 계획의 범위가 아니다. 단계 4의 보호 자원 레지스트리가 MCP 경로를 더할 자리를 남긴다.

## 5. 위험과 확인할 것

- Spring Security 7과 Boot 4.1.1에서 인가 서버 설정 방식(0단계). 안 맞으면 선택지를 다시 올린다.
- 불투명 토큰과 라이브러리의 토큰 생성기 연결(`OAuth2TokenGenerator`)이 필요하다. 라이브러리의 `OAuth2Authorization` 직렬화(속성 맵)를 JPA에 담는 부분이 `next`에서도 가장 까다로웠다.
- 기존 `SecurityConfig`가 하나의 체인에 사용자 정의 필터를 쓰므로, 인가 서버 체인이 앞에서 요청을 가로채는 범위를 엔드포인트로 좁혀야 한다.
- 승인 화면 도입으로 로그인 직후 이동(`redirectUrl`)이 긴 주소를 다룬다. v1.6의 `redirectUrl` 처리(외부 주소로 열리는 결함, R-06)를 승인 흐름에서 악용하지 못하게 `/oauth2/authorize`만 허용하는 확인이 필요하다.
- 이 단계에서 하지 않는 것: OIDC("Sign in with Yona", ID 토큰, `/userinfo`)는 로드맵 X-40이다(D-71 5번). 조직이 소유하는 앱은 X-39이다(테이블 구조만 이번에 맞춘다).
