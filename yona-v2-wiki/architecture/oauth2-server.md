# OAuth2 인가 서버(X-1, D-71)

v1.6에는 없는 추가 개발 항목이다. Yona가 OAuth 2.1 인가 서버가 되어, 사용자의 승인을 받은 외부 앱이 개인용 토큰(PAT)을 손으로 붙여 넣지 않고 외부 API(`/-_-api/v1`)에 접근한다. 직접 쓰임은 MCP 서버(X-6)의 인증 기반과 제3자 앱의 위임 접근이다. 계획과 결정 근거는 [oauth2-server-plan.md](oauth2-server-plan.md)와 `decisions.md` D-71이다. 이 문서는 구현된 모습이다.

## 구성

| 부분 | 내용 |
|---|---|
| 라이브러리 | Spring Security 7.1.1의 인가 서버(`spring-security-oauth2-authorization-server`). 인가 코드, PKCE, 토큰 교환, 갱신 토큰 회전은 라이브러리가 맡고 저장소와 클라이언트 조회, 토큰 생성은 직접 구현했다 |
| 보안 체인 | 인가 서버 엔드포인트만 별도 체인(`AuthorizationServerConfig`, `@Order(1)`)이 맡고 나머지는 기존 체인이 맡는다. 사용자는 두 체인 모두 `YonaAuthenticationFilter`로 식별한다 |
| 엔드포인트 | `GET /oauth2/authorize`, `POST /oauth2/authorize`(승인 제출), `POST /oauth2/token`, `POST /oauth2/revoke`, `POST /oauth2/introspect`(기밀 앱), `GET /.well-known/oauth-authorization-server`(RFC 8414), `GET /.well-known/oauth-protected-resource[/-_-api/v1]`(RFC 9728). **동적 클라이언트 등록은 없다**(D-71) |
| 토큰 | 액세스 `yona_oat_<무작위>`(1시간), 갱신 `yona_ort_<무작위>`(30일, 쓸 때마다 회전), 인가 코드 5분. **불투명 토큰이고 SHA-256 해시만 저장**한다. 클라이언트 비밀 값은 `yona_cs_`이고 해시만 저장한다 |
| PKCE | 모든 앱에 S256 필수. 승인 화면도 모든 앱에 필수 |
| 범위 | PAT와 같은 GitHub 클래식 범위(D-36). OAuth 앱에 **주지 않는 범위**: `site_admin`과 SSH 키, GPG 키, 배포 키의 모든 범위 9개(D-71 11번). 앱이 요청할 수 있는 범위의 상한은 앱 소유자가 정한다. 승인 화면에서 범위를 줄일 수 없다(허용 또는 거부, D-71 10번) |

## 앱을 등록하는 방법

| 방법 | 설명 |
|---|---|
| 직접 등록 | 설정의 "OAuth 앱"에서 공개 앱(PKCE) 또는 기밀 앱(비밀 값)을 만든다. 클라이언트 ID는 `yona_app_...`. 사용자당 20개, 리디렉션 주소 10개까지 |
| CIMD(클라이언트 ID 메타데이터 문서) | `client_id`가 `https://` 주소이면 인가 서버가 그 주소의 문서를 가져와 이름과 리디렉션 주소를 알아낸다(IETF 초안 -02). Claude Code와 Codex가 이 방식으로 자동 등록한다. 공개 앱만 받는다. 관리자 "보안"에서 끌 수 있다 |
| 수동 등록 | CIMD나 동적 등록이 없는 클라이언트(Antigravity 등)는 설정에서 앱을 만들어 클라이언트 ID를 직접 입력한다. Antigravity의 리디렉션은 `https://antigravity.google/oauth-callback` |

### CIMD 안전 규칙

서버가 외부가 준 주소로 연결하므로 SSRF를 막는 규칙을 모두 지킨다: HTTPS만, 이름을 풀어 얻은 **모든** 주소가 공인 주소(루프백, 사설, 링크 로컬, 공유 주소 공간, 문서용, 예약, 멀티캐스트, IPv4 매핑 포함 거부), 확인한 주소로 **직접** 연결(재바인딩 방지), HTTP 리디렉션 따라가지 않음, 상태 200만, 압축과 JSON이 아닌 응답 거부, 연결과 응답 5초, 본문 8 KB, 문서의 `client_id`가 주소와 같아야 함, `client_secret`과 공유 비밀 값 인증과 개인 키가 든 문서 거부, 리디렉션 주소 검증. 캐시는 응답의 `max-age`를 1분에서 24시간으로 맞춘다(기본 1시간). 가져오기 실패는 캐시하지 않고, 만료된 행은 가져오기에 실패했을 때만 7일까지 쓴다. 문서가 검증에 실패하면 이전 행도 지운다. 서버 전체 분당 60번, 같은 주소는 10초에 한 번만 가져온다. `yona.oauth2.cimd-allow-private-hosts`(기본 꺼짐)는 개발과 시험 전용이다.

## 리디렉션 규칙

`https` 주소와 루프백(`localhost`, `127.0.0.1`, `[::1]`)의 `http`만 등록할 수 있다(조각, 사용자 정보, 500자 초과 거부). 요청한 주소는 등록한 주소와 **정확히** 같아야 하되, 등록한 주소가 루프백이면 포트를 무시하고 세 루프백 호스트를 같게 본다(RFC 8252). 틀린 리디렉션 주소는 사용자를 그 주소로 보내지 않고 오류 화면을 낸다.

## 흐름

1. 앱이 `/oauth2/authorize`로 사용자를 보낸다. 로그인하지 않았으면 요청 전체를 세션에 보관하고 `/users/loginform?redirectUrl=/oauth2/resume`으로 보낸다(로그인 화면이 이동 주소의 질의를 인코딩해 망가뜨리는 v1.6 동작을 피하려고 `/oauth2/resume`만 넘긴다). 로그인(2단계 인증과 강제 이동 포함)이 끝나면 보관한 요청으로 돌아온다.
2. 처음이거나 새 범위이면 승인 화면(`/oauth2/consent`)을 보인다: 앱 이름, 만든 사용자(CIMD 앱은 문서를 게시한 호스트), 돌아갈 도메인, 요청한 범위의 설명, `localhost`로 돌아오는 앱에는 경고. `frame-ancestors 'none'`과 `X-Frame-Options: DENY`, 캐시 금지. 이미 허용한 범위만 요청하면 건너뛴다.
3. 승인하면 `redirect_uri`로 코드를 돌려주고, 앱이 코드와 PKCE 검증값으로 `/oauth2/token`에서 토큰을 받는다. 공개 앱의 갱신 토큰 교환과 취소는 `client_id`만으로 인증한다(`PublicClientToken*`, 라이브러리의 공개 클라이언트 인증이 PKCE에 묶여 있어 직접 더했다). 기밀 앱은 `client_secret_basic` 또는 `client_secret_post`.
4. 자원 쪽: `/-_-api/v1`에서 `Authorization: Bearer yona_oat_...`를 `YonaAuthenticationFilter`가 받아(`OAuthAccessTokenService`) PAT와 같은 `YonaAuthentication(viaToken, scopes)`로 만든다. 같은 실패 제한과 범위 검사를 받는다. 만료, 취소, 앱 삭제, 잠긴 사용자는 401이고 401에는 `WWW-Authenticate: Bearer resource_metadata="..."`가 실린다(RFC 9728).
5. **git과 SVN의 HTTP는 OAuth 토큰을 받지 않는다**(D-70, 선생님 결정). `yona_oat_` 값은 비밀번호처럼 401로 거부하고 실패로 세지 않는다.

## 갱신 토큰 재사용 탐지

갱신 토큰은 쓸 때마다 새로 돌린다. 바로 앞에 쓰던 토큰의 해시(`previous_refresh_token_hash`)를 남겨 두고, 그 토큰이 다시 오면 탈취를 의심해 **인가 전체를 지우고**(새로 받은 토큰도 쓸 수 없다) `oauth.refresh_reuse_detected`(거부)를 기록한다.

## 화면

| 화면 | 주소 | 내용 |
|---|---|---|
| 설정 탭 "OAuth 앱" | `/user/editform/oauth-apps` | 한 화면에 "내가 만든 앱"(목록, 삭제, 새 앱)과 "승인한 앱"(앱, 만든 사용자나 호스트, 범위, 승인한 날, 취소) |
| 앱 만들기와 수정 | `/user/editform/oauth-apps/new`, `/{id}` | 이름, 설명, 홈 주소, 리디렉션 주소(여러 줄), 종류(만든 뒤 바꿀 수 없음), 범위. 기밀 앱의 비밀 값은 만든 직후와 다시 만든 직후에 한 번만 보이고 캐시를 막는다 |
| 조직 설정 탭 "OAuth 앱"(X-39) | `/organizations/{조직}/oauth-apps`, `/new`, `/{id}` | 조직이 소유한 앱의 목록, 만들기, 수정, 비밀 값 다시 만들기, 지우기. 사용자 설정과 같은 템플릿 조각과 규칙을 쓰고 조직 관리자와 사이트 관리자만 들어간다. 다른 조직의 앱은 404이다 |
| 관리자 "OAuth 앱" | `/sites/oauth-apps` | 사이트 전체 앱(소유자, 등록 방식, 승인한 사용자 수)과 삭제(토큰 전부 무효) |
| 관리자 "보안" | `/sites/security` | "CIMD 허용", "앱 만들기 허용(사용자와 조직)"(둘 다 기본 허용) |

## 테이블

`oauth_client`(소유자 종류 `USER`/`ORGANIZATION`과 번호 구조, 조직 소유 화면은 X-39), `oauth_authorization`(토큰 해시, 만료, 승인 범위, 이전 갱신 토큰 해시), `oauth_consent`(사용자와 앱별 승인 범위). 모두 사이트 내보내기와 가져오기에 들어가고(v1.6 파일에는 없다) 사용자 삭제 처리와 앱 삭제에서 함께 지운다. 쓸모없어진 인가(승인 도중 1시간 넘게 토큰이 없는 것, 모든 토큰이 7일 전에 만료된 것)는 하루에 한 번 지운다.

## 감사와 지표

감사: `oauth.client_created`, `oauth.client_updated`, `oauth.client_secret_regenerated`, `oauth.client_deleted`, `oauth.client_transferred`, `oauth.authorized`, `oauth.token_issued`, `oauth.authorization_revoked`, `oauth.refresh_reuse_detected`, `site.oauth_cimd_changed`, `site.oauth_user_apps_changed`. 토큰과 비밀 값은 남기지 않는다. 지표: `yona.oauth.tokens{grant, client_type}`(발급 수). 토큰 실패는 `auth.token_failure`(채널 `oauth`).

## 검증

단위(리디렉션 규칙, CIMD 문서 검증, 주소 정책), 실제 TLS 서버로 CIMD 전송(리디렉션 거부, 크기와 시간 제한, 재바인딩 이름 거부), 인가 코드 + PKCE 전체 흐름(MockMvc), CIMD 클라이언트 흐름, 승인 화면, 자원 쪽 접근과 git 거부, 설정과 관리자 화면 시험. Playwright로 승인 화면과 설정 화면을 확인했다.

## OIDC 제공자 ("Sign in with Yona", X-40, D-77)

다른 앱이 Yona 계정으로 로그인하게 하는 OpenID Connect 제공자이다. X-1의 인가 서버 위에 얹었고 방향은 X-16(Yona가 외부 IdP로 로그인)과 반대이다. **운영자가 ID 토큰 서명 키를 정해 줘야 켜진다**(키의 자동 생성은 추가 논의 항목).

**켜는 법**: PKCS#8 PEM의 RSA 개인키(2048비트 이상)를 만들어 `yona.oauth2.oidc.signing-key-file`에 경로를 준다. 키를 정하지 않으면 OIDC는 꺼져 있다(발견 문서와 사용자 정보 404, `openid` `profile` `email` 범위를 앱에 줄 수 없고 저장된 앱의 OIDC 범위도 무시한다). 정했는데 읽을 수 없거나 형식이 틀리거나 2048비트 미만이면 서버가 시작하지 않는다.

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /etc/yona/oidc-key.pem && chmod 600 /etc/yona/oidc-key.pem
```

| 주소 | 설명 |
|---|---|
| `/.well-known/openid-configuration` | 발견 문서(발급자, 엔드포인트, `id_token_signing_alg_values_supported: [RS256]`, `scopes_supported`, `claims_supported`, `code_challenge_methods_supported: [S256]`) |
| `/oauth2/jwks` | 공개키 목록(Spring Authorization Server가 서명 키 `JWKSource`가 있으면 자동으로 연다. 개인키 성분은 걸러서 낸다. `kid`는 공개키의 SHA-256 지문) |
| `/oauth2/userinfo` | 사용자 정보(`GET`, `POST`, `Authorization: Bearer <액세스 토큰>`). 토큰이 없으면 401, 틀리면 401 `invalid_token`, `openid` 범위가 없으면 403 `insufficient_scope` |

**흐름**: 앱을 만들 때 `openid`, `profile`, `email` 범위를 고르고(앱 만들기 화면과 승인 화면에 5개 언어 설명이 나온다), 승인 요청에 `scope=openid ...`와 `nonce`를 보내면 토큰 응답에 `id_token`이 함께 온다. 액세스 토큰은 지금처럼 불투명 토큰(`yona_oat_`)이고 ID 토큰만 RS256 JWT이다.

**화면**: OIDC 전용 화면은 따로 없고 기존 화면을 쓴다. 앱 만들기와 승인 화면에 `openid` `profile` `email` 범위와 설명이 나오고, **이미 만든 앱의 수정 화면**에는 OIDC가 켜져 있을 때 "Yona로 로그인(OIDC)" 영역으로 발급자와 발견 문서 주소가 보인다(앱 개발자가 OIDC 라이브러리에 넣을 값). **사이트 관리의 "보안" 화면**에는 "OIDC 제공자" 영역으로 켜짐/꺼짐, 켜졌으면 발급자, 발견 문서, 공개키 목록 주소, 서명 알고리즘, 서명 키 번호가, 꺼졌으면 켜는 방법이 보인다. 서명 키는 파일로만 정하므로 이 화면에서 바꾸는 항목은 없다(읽기 전용).

**ID 토큰**: 유효 10분. `iss`는 발견 문서와 같은 값(요청의 출처와 컨텍스트 경로), `sub`은 **사용자 번호**(불변), `aud`는 앱, `nonce`는 요청 값 그대로, 범위가 정한 사용자 클레임을 함께 싣는다.

| 범위 | 클레임 |
|---|---|
| `openid` | `sub` |
| `profile` | `name`, `preferred_username`(로그인 ID), `picture`(프로필 사진의 절대 주소), `updated_at` |
| `email` | `email` |

`email_verified`는 **보내지 않는다**(기본 이메일에 인증 표시가 없어 어느 값도 거짓이 되기 때문이다). 조직 소속(`groups`) 같은 Yona 고유 클레임도 없다. 사용자 정보 주소도 같은 클레임을 같은 범위 규칙으로 돌려준다.

**주소가 `/userinfo`가 아닌 이유**: 로드맵에는 `/userinfo`로 적었으나 v1.6의 `/{사용자}` 경로와 겹쳐 `userinfo`라는 사용자의 프로필 주소를 가리므로, 이미 예약한 `oauth2` 아래(`/oauth2/userinfo`)에 두었다. 발견 문서가 알려 주므로 라이브러리 사용에는 영향이 없다.

**알려진 한계**
- 갱신 토큰으로 새 ID 토큰을 주지 않는다(ID 토큰을 저장하지 않는다. 첫 로그인에서 받은 것을 쓴다).
- 로그아웃 연동(`end_session_endpoint`)이 없다(추가 논의 항목).
- 서명 키의 교체는 파일을 바꾸고 재시작하는 방식이고, 이전 키로 서명된 ID 토큰은 새 공개키로 검증되지 않는다(ID 토큰은 10분만 유효하다). 자동 순환은 없다.
- CIMD로 등록하는 앱은 OIDC 범위를 받지 않는다(문서의 `scope`에서 무시한다).
- 보호 자원 메타데이터(`/.well-known/oauth-protected-resource`)의 지원 범위에는 OIDC 범위를 넣지 않는다(자원 접근 범위가 아니다).
- 발견 문서의 `issuer`와 ID 토큰의 `iss`는 같은 값(요청 기준 출처)이지만, 기존 `/.well-known/oauth-authorization-server`의 `issuer`는 라이브러리가 요청에서 만든 값이다. 역방향 프록시 뒤에서는 두 값이 다를 수 있어 `X-Forwarded-*` 설정을 맞춰야 한다.

**검증**: 시험 `OidcProviderTest`(발견 문서, 공개키 목록, ID 토큰 서명과 클레임과 nonce와 유효 시간, 범위별 클레임, 사용자 정보의 401/403, 앱 화면)와 `OidcDisabledTest`(꺼진 상태)이다. 실제 jar에 공개된 OIDC 클라이언트 라이브러리(`openid-client` 6.8)로 발견, PKCE, 승인, 토큰 교환, **ID 토큰의 서명과 `iss`와 `aud`와 `exp`와 `nonce` 검증**, 사용자 정보를 확인했다.


## 조직이 소유하는 앱 (X-39, D-78)

- 앱의 소유자는 `owner_type`(`USER`, `ORGANIZATION`)과 `owner_id`이다(테이블은 처음부터 이 구조). 서비스는 `ClientOwner`로 소유자를 받고, 조직 앱은 조직 관리자가 `OrganizationOAuthAppsController`로 다룬다.
- 앱 개수 한도 20개는 소유자별로 센다. 사용자가 20개를 만들어도 그 사용자가 관리하는 조직은 따로 20개이다.
- 조직을 지우면 그 조직의 앱과 앱의 인가(토큰)와 승인이 함께 지워진다. 사용자를 지울 때와 같은 규칙이고 앱마다 감사 기록이 남는다.
- 감사 기록은 조직 앱이면 `organizationId`를 채워 조직 감사 로그에 나타난다. 승인 화면과 "승인한 앱"과 사이트 앱 목록에는 소유자가 "{조직} 조직"으로 보인다.
- **소유자 이전**: 앱의 수정 화면에서 앱 이름을 다시 입력해 소유자를 바꾼다. 클라이언트 ID, 비밀 값, 인가, 승인은 그대로이다. 사용자는 **자신이 관리자인 조직**으로, 조직 관리자는 **자기 계정이나 자신이 관리자인 다른 조직**으로 넘길 수 있다(조직에 속하기만 한 사용자는 그 조직으로 넘길 수 없다). 받는 쪽도 앱 20개 한도를 넘지 못한다. 감사 `oauth.client_transferred`.
- 조직의 OAuth app policy는 같은 항목의 다음 단계이다.
