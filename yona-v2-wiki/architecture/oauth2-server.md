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
| 관리자 "OAuth 앱" | `/sites/oauth-apps` | 사이트 전체 앱(소유자, 등록 방식, 승인한 사용자 수)과 삭제(토큰 전부 무효) |
| 관리자 "보안" | `/sites/security` | "CIMD 허용", "사용자의 앱 만들기 허용"(둘 다 기본 허용) |

## 테이블

`oauth_client`(소유자 종류 `USER`/`ORGANIZATION`과 번호 구조, 조직 소유 화면은 X-39), `oauth_authorization`(토큰 해시, 만료, 승인 범위, 이전 갱신 토큰 해시), `oauth_consent`(사용자와 앱별 승인 범위). 모두 사이트 내보내기와 가져오기에 들어가고(v1.6 파일에는 없다) 사용자 삭제 처리와 앱 삭제에서 함께 지운다. 쓸모없어진 인가(승인 도중 1시간 넘게 토큰이 없는 것, 모든 토큰이 7일 전에 만료된 것)는 하루에 한 번 지운다.

## 감사와 지표

감사: `oauth.client_created`, `oauth.client_updated`, `oauth.client_secret_regenerated`, `oauth.client_deleted`, `oauth.authorized`, `oauth.token_issued`, `oauth.authorization_revoked`, `oauth.refresh_reuse_detected`, `site.oauth_cimd_changed`, `site.oauth_user_apps_changed`. 토큰과 비밀 값은 남기지 않는다. 지표: `yona.oauth.tokens{grant, client_type}`(발급 수). 토큰 실패는 `auth.token_failure`(채널 `oauth`).

## 검증

단위(리디렉션 규칙, CIMD 문서 검증, 주소 정책), 실제 TLS 서버로 CIMD 전송(리디렉션 거부, 크기와 시간 제한, 재바인딩 이름 거부), 인가 코드 + PKCE 전체 흐름(MockMvc), CIMD 클라이언트 흐름, 승인 화면, 자원 쪽 접근과 git 거부, 설정과 관리자 화면 시험. Playwright로 승인 화면과 설정 화면을 확인했다.
