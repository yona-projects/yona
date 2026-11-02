# 2단계 인증(X-2, D-69)

v1.6에는 없는 추가 개발 항목이다. 2단계 인증을 켜지 않은 사용자의 로그인은 v1.6 그대로이다(켜는 것은 선택이고, 관리자가 강제할 수 있다).

## 수단

| 수단 | 내용 |
|---|---|
| 인증 앱(TOTP, RFC 6238) | HMAC-SHA1, 6자리, 30초, 앞뒤 한 구간 허용. 이미 쓴 구간은 데이터베이스에서 한 번에 올려 재사용을 막는다. 비밀 값은 AES-256-GCM으로 암호화해 저장(키는 사이트 비밀 키에서 HKDF로 만든다) |
| 보안 키(WebAuthn) | Yubico `java-webauthn-server` 2.10.1. **두 번째 단계로만** 쓴다(비밀번호 없는 로그인 없음). 서명 횟수가 줄면 거부. 사용자 식별자는 사이트 비밀 키의 HMAC이라 사용자 번호가 나가지 않는다. 호스트 이름과 출처는 요청에서 정한다 |
| 백업 코드 | 10개, `xxxxx-xxxxx`, 헷갈리는 글자 제외, 해시(HMAC)만 저장, 한 번만 쓴다. 처음 켤 때 한 번 보인다 |
| 알려진 기기 | 2단계를 마친 뒤 "30일 동안 묻지 않기". 무작위 값을 쿠키(`yona_device`, HttpOnly)로 주고 SHA-256만 저장한다. 사용자당 20대 |

## 로그인 입구

모든 입구가 `TwoFactorLogin`을 거친다. 2단계 없이 `LoginSessionService.start`를 부르면(알려진 기기도 아니면) 예외이므로 새 입구가 우회할 수 없다.

| 입구 | 처리 |
|---|---|
| 웹 폼 `POST /users/login` | 비밀번호가 맞으면 세션에 진행 중인 로그인을 두고 `/users/login/2fa`로 이동 |
| AJAX 팝업 | JSON `{"twoFactor": true, "securityKey": ...}`. 팝업이 코드 단계로 바뀌어 `POST /users/login/2fa`(JSON)로 확인 |
| 소셜 로그인과 세션 이어받기 | 같은 화면. 로그인 유지 쿠키는 2단계 뒤에 만든다 |
| 메일의 이메일 확인 링크 | 링크를 연 사용자가 2단계를 켰을 때만 2단계 화면 |
| 가입 | 새 사용자라 요구하지 않는다 |
| 로그인 유지 쿠키 `yobi.token` | 2단계 사용자는 알려진 기기 쿠키가 함께 있어야 인정한다 |
| git과 SVN HTTP | 클래식 토큰만 받으므로 해당 없음(D-70) |

진행 중인 로그인은 10분 유효하다. 사용자마다 연달아 5번 틀리면 15분 잠근다(잠긴 동안은 맞는 코드도 받지 않는다, 서버 메모리 기준).

## 화면

- 설정 탭 "2단계 인증"(`/user/editform/two-factor`): 수단 목록(삭제), 인증 앱 추가(QR은 인라인 SVG, 비밀 값이 밖으로 나가지 않는다), 보안 키 추가, 백업 코드 남은 수와 다시 만들기, 알려진 기기 목록(삭제). 비밀 값과 백업 코드 응답은 캐시를 막는다. 마지막 수단을 지우면 2단계 인증이 꺼지고 백업 코드와 기기도 지워진다.
- 관리자 "보안"(`/sites/security`): 강제 방식 `OFF`(기본), `ADMINS`, `ALL`. 테이블 `site_setting`에 저장하고 변경은 감사 기록(`site.two_factor_enforcement_changed`)에 남긴다. 강제 대상이 켜지 않았으면 로그인 직후 등록 화면으로 보낸다(허용: 등록 화면, `/user/two-factor/*`, 로그아웃, 로그인 화면, 정적 자원, 메시지 스크립트, 보안 설정 화면, `/-_-api`). 토큰 요청은 대상이 아니다.
- 관리자 사용자 목록의 "2단계 인증 해제" 단추(켠 사용자에게만).
- 긴급 해제: `yona.two-factor.reset-user=<로그인ID>`로 시작하면 그 사용자의 2단계 인증을 지운다(쓴 뒤 설정을 지운다).

## 테이블

`two_factor_credential`(종류 TOTP/WEBAUTHN, 암호화한 비밀 값 또는 보안 키의 자격 증명 번호와 공개키와 서명 횟수), `two_factor_backup_code`, `known_device`, `site_setting`. 모두 사이트 내보내기와 가져오기에 들어가고(v1.6 파일에는 없다), 사용자 삭제 처리에서 지운다. **사이트 비밀 키를 바꾸면** 저장한 비밀 값과 백업 코드를 읽을 수 없게 되어 사용자가 다시 등록해야 한다(관리자가 해제할 수 있다).

## 감사 기록

`two_factor.enabled`, `two_factor.disabled`(`by`: user, admin, emergency), `two_factor.method_removed`, `two_factor.backup_code_used`, `two_factor.backup_codes_regenerated`, `auth.login_failure`(`reason`: `two_factor_invalid`, `two_factor_locked`), 로그인 성공은 `details.method`에 `password+two_factor`처럼 남는다. 비밀 값과 코드는 남기지 않는다.

## 검증

단위 시험(RFC 6238 벡터, 암호화, 백업 코드, QR를 다시 읽기), 소프트웨어 인증기(ES256과 CBOR 증명을 직접 만든다)로 WebAuthn 등록과 인증과 변조와 서명 횟수 역행, 로그인 입구별 웹 시험, Playwright 가상 인증기로 실제 브라우저의 등록, 보안 키 로그인, 백업 코드, 알려진 기기, 팝업을 확인했다.
