---
title: 소셜 로그인
type: web
sources: [app/controllers/Application.java:65-143, app/Global.java:184-235, app/service/YonaUserServicePlugin.java, app/models/UserCredential.java, app/models/LinkedAccount.java, app/controllers/UserApp.java:132-152,417-475,1278-1312, app/controllers/Secured.java, app/controllers/Restricted.java, app/views/user/login.scala.html, app/views/common/usermenu.scala.html, conf/social-login.conf.default, conf/routes:12-16]
updated: 2026-10-25
---

# 소셜 로그인 (GitHub, Google)

코드: `domain/social/SocialAccountService`(계정 연결 규칙), `OAuth2SocialProvider`(제공자 통신), `SocialProviders`(쓸 수 있는 제공자), `web/social/SocialLoginController`, `SocialSession`(세션 값). 결정은 [decisions.md](../decisions.md) D-24(직접 구현), A-95, 결함은 R-59. v1.6은 play-authenticate 0.6.9를 썼고 v2는 `RestClient`로 직접 구현한다. 라이브러리 전환은 리팩터링 후보 T-1이다.

## 주소
| 주소 | 설명 |
|---|---|
| `GET /authenticate/:provider` | 코드가 없으면 제공자의 인증 화면으로 보낸다(`state` 포함). 코드가 있으면 토큰 교환, 사용자 정보 읽기, 로그인 또는 가입. `error=access_denied`는 거절 안내로 보낸다 |
| `GET /authenticate/:provider/denied` | 홈으로 보내며 안내 `You need to accept the OAuth connection in order to use this website!`를 남긴다 |
| `GET /logout` | 로컬 세션과 소셜 세션을 모두 지우고 Referer(없으면 홈)로 보낸다. 메뉴의 로그아웃은 소셜 세션이 있으면 이 주소를 쓴다 |
| `GET /restricted` | 소셜 세션이 있어야 열리는 시험 화면. 없으면 홈으로 보내며 안내를 남긴다 |

## 설정 (`yona.social.*`)
| 키 | v1.6 | 설명 |
|---|---|---|
| `login-support` | `application.social.login.support` | 쓸 제공자(쉼표). 비면 쓰지 않는다 |
| `name-sync` | `application.use.social.login.name.sync` | 로그인마다 이름 동기화 |
| `github-name` | `application.social.login.github.name` | GitHub 버튼 이름 |
| `github.*`, `google.*` | `play-authenticate.github.*`, `google.*` | `client-id`, `client-secret`, `scope`, `authorization-url`, `access-token-url`, `user-info-url`, `redirect-secure`, `redirect-host` |

기본 설정 파일의 시험용 ID와 비밀은 옮기지 않는다(A-95). 계정 연결은 `accountAutoLink=true`, `accountMergeEnabled=false`로 고정이다.

## 흐름
1. 인증 코드를 토큰으로, 토큰으로 사용자 정보(`SocialIdentity`: 제공자, 제공자 쪽 ID, 이메일, 이름)를 읽는다.
2. 소셜 세션이 이미 있고 다른 신원이면: 이미 연결된 신원이면 그 신원으로 로그인한다. 연결되지 않았으면 현재 소셜 세션의 자격 증명에 연결한다(로컬 세션은 그대로).
3. 그 밖에는 `SocialAccountService.signIn`:
   - 연결된 활성 자격 증명이 있으면 소셜 이메일, 자격 증명 이메일, 연결된 사용자 순으로 로컬 사용자를 찾는다. 없고 자격 증명에 로그인 ID도 없으면 사용자를 만든다. 사용자가 `ACTIVE`가 아니면 안내 `user.locked`와 함께 강제 로그아웃한다(`pa.url.orig`를 `/logout`으로 바꾼다). 이름 동기화가 켜져 있으면 자격 증명과 사용자 이름을 고친다.
   - 처음 보는 신원이면 자격 증명과 연결 계정을 만들고, 같은 이메일의 사용자가 없으면 사용자를 만든다. 사용자가 `ACTIVE`이면 로그인한다.
   - 사용자 만들기: 이메일이 없거나 `"null"`이면 안내 `app.warn.cannot.access.email.information`, 허용 이메일 도메인이 아니면 `user.unacceptable.email.domain`을 남기고 자격 증명을 지운다. 로그인 ID는 이메일의 `@` 앞이며 겹치면 `-yona`, 또 겹치면 숫자를 이어 붙인다(R-59). 비밀번호는 무작위이고 소셜 가입은 이메일 확인 없이 `ACTIVE`이다.
4. 로그인한 사용자에게는 로그인 유지 쿠키(`yobi.token`)를 항상 남긴다. 소셜 세션(`pa.p.id`, `pa.u.id`, `pa.u.exp`)을 시작한다.
5. 인증 전에 보던 주소(`pa.url.orig`)가 있으면 그곳으로, 없으면 홈으로 보낸다.

## 인증 후 돌아갈 주소
사용자 메뉴와 사이드바(전체 화면)가 익명 사용자에게 렌더링될 때마다 `SocialSession.rememberOriginalUrl`이 현재 주소(`redirectUrl` 질의가 있으면 그 값)를 `pa.url.orig`에 기억한다(v1.6 `storeOriginalUrl`). 소셜 세션이 있으면 기억하지 않는다.

## 로그인 화면
허용되고 설정된 제공자마다 버튼(`a.oauth-login-btn`, 순서는 GitHub, Google)과 구분선(`title.or`)을 보인다. 소셜 세션이 있으면 버튼 대신(대화상자) 캐시 안내와 로그아웃 버튼을 보인다. 소셜 세션만 있고 로컬 세션이 없는 채로 로그인 화면에 오면 `SocialAccountService.resume`으로 사용자를 이어받아 `redirectUrl`(없으면 홈)로 보낸다.

## 원본 확인
play-authenticate 0.6.9의 동작은 jar(`~/.ivy2/cache/com.feth/play-authenticate_2.10/jars/play-authenticate_2.10-0.6.9.jar`)를 Fernflower(Android Studio의 `java-decompiler.jar`)로 디컴파일해 읽었다. 세션 키, 인증 흐름(`handleAuthentication`), 제공자 구현(`OAuth2AuthProvider`, `GithubAuthProvider`, `GoogleAuthProvider`), 기본 설정(`reference.conf`)이 그 근거이다. v1.6과 다르게 한 부분은 A-95, 재현한 결함은 R-59에 있다.

- 세션 키: `pa.u.id`(제공자 쪽 사용자 ID), `pa.p.id`(제공자), `pa.u.exp`(만료 시각, 끝이 없으면 키를 지운다), `pa.url.orig`(돌아갈 주소). `PlayAuthenticate.logout`은 이 네 키를 모두 지운다.
- 흐름: 소셜 세션이 있으면 자격 증명을 확인해 없으면 소셜 세션을 지운다. 새 신원이 연결되어 있으면 로그인, 연결되어 있지 않고 소셜 세션이 없으면 가입(`save`), 연결되어 있지 않고 소셜 세션이 있으면 자동 연결(`accountAutoLink=true`)이다. 병합은 꺼져 있다.
- 돌아갈 주소(`storeOriginalUrl`): GET 요청이고 경로가 로그인 주소(홈)가 아니면 현재 주소를 기억하고, 그 밖에는 지운다.
- 제공자: 토큰 요청은 `client_id`, `redirect_uri`, `client_secret`, `grant_type`, `code`를 폼으로 보낸다. 응답의 `error`가 있으면 그 문구로 실패한다. 사용자 정보는 `id`, `email`, `name`을 `n.has(필드) ? n.get(필드).asText()`로 읽는다.
