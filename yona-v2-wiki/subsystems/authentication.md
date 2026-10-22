---
title: 인증 (로컬 로그인 판정과 패스워드 해시)
type: subsystem
sources: [app/controllers/UserApp.java:197-259, app/controllers/UserApp.java:1027-1032, app/controllers/UserApp.java:1144-1154, app/models/User.java:358-366, app/models/User.java:530-533, app/models/AuthInfo.java]
updated: 2026-10-22
---

# 인증

v1.6의 네 가지 인증 경로(로컬, LDAP, 소셜, 토큰/Basic) 중 **로컬 계정 판정**부터 구현했다. 나머지는 이후 단계에서 이 문서에 추가한다. v1.6 분석은 `~/yona-convert/yona-v1.6-wiki/subsystems/auth-ldap-social.md`를 본다.

## 구현된 구성요소

| 클래스 | 역할 | 대응하는 v1.6 코드 |
|---|---|---|
| `dev.yona.security.LegacyPasswordHasher` | 솔트 포함 SHA-256 1024회 반복 + Base64. 솔트 생성(16바이트 난수 Base64) | `UserApp.hashedPassword`, `User.getHashedStringForPassword` |
| `dev.yona.domain.user.UserRepository` | 로그인 ID(대소문자 무시) 우선, 없으면 이메일로 조회 | `User.findByLoginKey` |
| `dev.yona.security.LocalAuthenticator` | 계정 상태와 패스워드로 `LoginResult` 판정 | `UserApp.loginByFormRequest` |

## 패스워드 해시

Shiro의 `Sha256Hash(평문, 솔트, 1024).toBase64()`와 비트 단위로 같다. Shiro 의존성 없이 `MessageDigest`로 구현했고, 테스트(`LegacyPasswordHasherTest`)에서 Shiro 1.13.0을 참조 구현으로 불러 여러 입력의 결과가 같음을 확인한다(Shiro는 테스트에서만 쓴다).

- 첫 반복은 `SHA-256(솔트 UTF-8 바이트 + 평문 UTF-8 바이트)`, 이후 반복은 직전 해시에 `SHA-256`을 다시 적용한다.
- 솔트는 문자열의 UTF-8 바이트다. v1.6 초기 데이터의 `[B@1032a4`도 문자열 그대로 솔트가 된다.
- 비교는 상수 시간(`MessageDigest.isEqual`)으로 한다. v1.6은 `StringUtils.equals`를 썼으므로 이 부분만 더 안전하다. 동작 결과는 같다.

## 로그인 판정 순서 (`LocalAuthenticator`)

1. 로그인 ID 또는 이메일로 계정을 찾는다. 없으면 `InvalidCredentials`.
2. 가입 승인제(`yona.signup.require-admin-confirm`, v1.6 `signup.require.admin.confirm`)이고 계정이 LOCKED이면 패스워드를 보기 전에 `Locked`.
3. 계정이 DELETED이면 `Deleted`.
4. 패스워드가 틀리면 `InvalidCredentials`.
5. 패스워드가 맞아도 LOCKED이면 `Locked`.
6. 그 외 `Success`.

이 판정은 화면과 독립이다. 결과를 안내 문구(`user.locked`, `user.deleted`, `user.login.invalid`)로 바꾸는 일은 로그인 컨트롤러가 맡는다(다음 단계).

## 설정 키 대응

| v1.6 키 | v2 키 | 기본값 |
|---|---|---|
| `signup.require.admin.confirm` | `yona.signup.require-admin-confirm` | false |

v1.6의 구버전 키 `signup.require.confirm`은 아직 옮기지 않았다.

## v1.6 결함을 그대로 재현한 부분

- 솔트나 패스워드가 비어 있는 계정으로 로그인하면 `IllegalArgumentException("Bad password or passwordSalt!")`이 발생한다(v1.6은 HTTP 500). [refactoring-backlog.md](../refactoring-backlog.md)에 올렸다.

## 초기 관리자 계정과 비밀번호

`initial-data.yml`의 관리자 해시(`5v4TVj...`)는 평문을 알 수 없다. 원래 솔트가 바이트 배열이었는데 YAML에는 `byte[].toString()` 결과(`[B@1032a4`)만 남았기 때문에, 문자열 솔트로는 어떤 평문으로도 재현되지 않는다(`admin`, `1234`, `yona`, `yobi` 등을 확인했다). v1.6은 최초 기동 시 `application.secret`이 기본값이면 관리자 비밀번호를 정하는 화면으로 유도해 이 값을 덮어쓴다(`Global.java:249-411`). 이 흐름은 아직 구현하지 않았다. 개발 환경에서 `admin / 1234`로 로그인하려면 이 흐름이나 개발용 설정이 필요하며 다음 단계의 과제다.
