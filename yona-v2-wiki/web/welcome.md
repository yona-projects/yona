---
title: 최초 기동 화면
type: web
sources: [app/Global.java:73-79, 158, 238-259, 281-370, app/views/welcome/secret.scala.html, app/views/welcome/restart.scala.html, app/models/SiteAdmin.java, conf/routes:5-6]
updated: 2026-10-25
---

# 최초 기동 화면 (`welcome/secret`, `welcome/restart`)

코드: `security/SiteSecret`, `web/welcome/SiteSecretInterceptor`, `web/welcome/FakeController`. 템플릿은 `welcome/secret`, `welcome/restart`. 결정은 [decisions.md](../decisions.md) D-22(선생님: 설정으로 v1.6과 같은 흐름).

## 언제 나타나는가

사이트 비밀 키(`yona.secret`)가 비어 있거나 기본값(`YonaProperties.DEFAULT_SECRET`, v1.6 `application.secret`의 기본 문자열)이면 서버를 시작할 때 "정하지 않음"으로 본다. 이 동안에는 **컨트롤러로 가는 모든 요청**(어떤 주소든, 어떤 방식이든)이 이 화면으로 간다. 정적 자원(`/assets/...`)은 그대로 나간다. 업데이트 확인 스케줄러도 시작하지 않는다.

## 흐름

1. 입력 화면(`welcome/secret`, 200): 제목 `Tada! Welcome to <사이트 이름>!`, 경고 문구, 폼(`POST /`). 항목은 ID(`admin`으로 고정, 읽기 전용), 이름, 이메일, 비밀번호, 비밀번호 확인이다.
2. 제출(`POST`, 어느 주소든) 검사: ID가 비었거나 `admin`이 아니면 `user.wrongloginId.alert`, 비밀번호가 비면 `user.wrongPassword.alert`, 확인과 다르면 `user.confirmPassword.alert`, 이메일이 비면 `validation.invalidEmail`, 이미 쓰는 이메일이면 `user.email.duplicate`. 오류가 있으면 400으로 입력 화면을 다시 그리며 이름과 이메일은 그대로 채워 둔다.
3. 통과하면 기본 관리자(`admin`) 계정의 이름, 이메일, 새 소금과 비밀번호 해시를 저장한다. 새 비밀 키(보안 난수 130비트의 32진수)를 `<데이터 디렉터리>/config/secret.properties`(`yona.secret=값`)에 **먼저** 쓰고, 쓰기에 성공했을 때만 위 계정을 저장한다. 쓰지 못하면 계정은 바뀌지 않고 500과 함께 폼을 다시 보이며 `app.secret.writeFailed`로 권한을 확인하고 다시 시도하라고 안내한다(v1.6은 계정을 먼저 바꾸고 직접 고치라고 했다, R-57-5, A-372). 이 파일은 시작할 때 `spring.config.import`로 읽는다(A-93).
4. 재시작 안내(`welcome/restart`, 200): `app.restart.notice`. 파일에 쓰지 못한 경우는 위처럼 폼으로 되돌아가므로 이 화면에 오지 않는다(`app.restart.updateSecretYourself` 키는 v1.6 번들과의 대조를 위해 남겨 두었다). 이 뒤로는 **어떤 요청이든** 재시작 안내이다. 서버를 다시 시작하면 정상으로 돌아오고 새 비밀번호로 로그인된다.

`POST /`는 비밀 키를 정한 뒤에는 400이다(v1.6 `Application.fake`, "호출하지 마시오"). 이 경로는 폼을 컨트롤러까지 보내려고 있다.
