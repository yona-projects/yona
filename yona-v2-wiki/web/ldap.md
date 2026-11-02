---
title: LDAP 인증
type: web
sources: [app/utils/LdapService.java, app/models/support/LdapUser.java, app/controllers/UserApp.java:154-345,1144-1226, app/utils/BasicAuthAction.java:90-111, conf/application.conf.default:366-396]
updated: 2026-10-25
---

# LDAP 인증

코드: `domain/ldap/Ldap.kt`(`LdapUser`, `LdapDirectory`, `LdapQueries`, `JndiLdapDirectory`), `LdapAuthService`(로컬 사용자와 맞추기), `security/LocalAuthenticator`(로그인 판정에 연결), `web/user/LoginController`. 결정은 D-24(JNDI), A-96, 결함은 R-60. Spring LDAP 전환은 리팩터링 후보 T-2이다.

## 설정

**관리자 화면이 우선한다(D-82, 2026-11-03).** 사이트 관리의 "SSO" 화면의 LDAP 탭에서 설정하고(테이블 `ldap_settings`, 한 행), 화면에서 한 번도 저장하지 않았으면 아래 설정 파일 값을 그대로 쓴다(v1.6에서 올라온 사이트가 끊기지 않게). 저장하면 그 뒤로는 화면 값이 이기고 파일 값은 쓰이지 않는다. 연결 확인(서버 주소와 포트로 접속)과 감사 기록(`site.sso_changed`, `details.protocol`이 `ldap`)이 있다. 바인드 비밀번호는 저장하지 않는다(로그인하는 사용자의 비밀번호로 바인드한다). 이 변경은 v1.6의 설정 파일 계약을 바꾸는 것이므로 [differences-from-v16.md](../architecture/differences-from-v16.md)에 적었다.

### 설정 파일 (`yona.ldap.*`, 화면에서 저장하기 전의 값)
| 키 | v1.6 | 기본 |
|---|---|---|
| `enabled` | `application.use.ldap.login.supoort`(오타가 실제 키) | `false` |
| `host`, `port` | `ldap.host`, `ldap.port` | `127.0.0.1`, `389` |
| `base-dn` | `ldap.baseDN` | 빈 값 |
| `distinguished-name-postfix` | `ldap.distinguishedNamePostfix` | 빈 값 |
| `login-property`, `display-name-property`, `user-name-property`, `email-property` | `ldap.loginProperty` 등 | `sAMAccountName`, `displayName`, `CN`, `mail` |
| `use-email-base-login`, `fallback-to-local-login`, `english-name-attribute-name` | `ldap.options.*` | `false`, `false`, 빈 값 |
| `yona.protocol` | 최상위 `protocol` | `ldap` (R-60) |

## 인증
1. 이름에 `@`가 있으면 그대로, 없으면 `<userNameProperty>=<이름>,<postfix>`를 주체로 JNDI `simple` 바인드한다(연결 제한 5초).
2. 바인드에 성공하면 `baseDn` 아래에서 `(속성=이름)`을 검색한다. 이름에 `@`가 있으면 이메일 속성, 아니면 로그인 속성이다. 결과가 정확히 하나일 때만 사용자로 본다.
3. 속성: 표시 이름, 이메일, 로그인 이름, `department`, (설정하면) 영문 이름.

## 로컬 사용자와 맞추기 (`LdapAuthService`)
- `use-email-base-login`이면 입력한 로그인 ID로 찾은 로컬 사용자의 이메일로 바꿔 인증한다.
- 이메일로 로컬 사용자를 찾는다. 있으면 이름(`이름 [부서]`)과 영문 이름을 고치고, 입력한 비밀번호가 로컬과 다르면 로컬 비밀번호를 바꾼다. 없으면 입력한 비밀번호로 새로 만든다. 로그인 ID는 LDAP 로그인 ID이고, 비었거나 이메일 기반 로그인이면 이메일 앞부분(겹치면 `-yona` 또는 숫자 이어 붙이기)이다. 가입 승인제나 이메일 인증으로 잠기면 안내 `user.signup.requested`를 남기고 로그인시키지 않는다.
- 연결하지 못하면(`CommunicationException`) 폴백이 켜져 있을 때 로컬 비밀번호로 다시 시도한다. 인증에 실패하면(`AuthenticationException`) 안내 `user.login.invalid`를 남기고 같은 폴백을 한다. 그 밖의 오류는 실패이다.

## 로그인 판정과 Git 기본 인증
LDAP을 켜면 `LocalAuthenticator.authenticate`는 계정이 없어도 LDAP 인증을 시도한다(계정 잠김/삭제 확인은 계정이 있을 때만). 결과의 안내는 로그인 화면 이동에 함께 실린다. Git HTTP 기본 인증은 `authenticateBasic`이 LDAP으로 확인한다.

## 소셜 로그인 전용 모드
`yona.social-login-only=true`이면 로그인 요청(폼, AJAX 모두)은 거부되고 안내 `app.warn.support.social.login.only`와 함께 홈 화면이 200으로 그려진다(v1.6 `UserApp.login`).
