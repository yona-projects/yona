---
title: 가입, 이메일 인증, 비밀번호 재설정
type: web
sources: [app/controllers/UserApp.java:369-405, 478-495, 582-626, 1008-1024, 1237-1289, 1338-1362, app/controllers/PasswordResetApp.java, app/utils/PasswordReset.java, app/models/UserVerification.java, app/views/user/signup.scala.html, app/views/user/verified.scala.html, app/views/user/resetPassword.scala.html, app/views/site/lostPassword.scala.html]
updated: 2026-10-22
---

# 가입, 이메일 인증, 비밀번호 재설정

## 가입 (`GET /users/signupform`, `POST /users/signup`)

- 화면: 제목 `Sign up`, `Sign up for <사이트 이름>`, 소개 문구, 입력란(아이디, 이름, 이메일, 비밀번호, 비밀번호 확인), 로그인 링크. 관리자 승인제이면 안내 두 줄이 더 있고 둘째 줄에 **사이트 관리자 이메일을 거꾸로 쓴 값**이 나온다(수집을 막으려는 v1.6 동작). 소셜 로그인 전용이면 안내만 있다. 이미 로그인했으면 홈으로 보낸다. 스크립트 `user.SignUp`이 사용 여부 확인 주소(`/user/isUsed?name=`, `/user/isEmailExist?email=`)를 받는다.
- 검증(실패하면 `400`으로 **빈 폼**을 다시 보여 준다. v1.6 화면은 어느 항목이 틀렸는지 알리지 않고 입력을 채워 주지도 않는다): 아이디는 필수이고 로그인 ID 형식과 예약어 규칙을 따르며 공백이 없어야 한다, 이메일은 있으면 형식이 맞아야 한다, 비밀번호는 비어 있으면 안 된다, 아이디가 다른 사용자나 조직과 겹치면(대소문자 무시) 안 되고 이메일이 다른 사용자의 대표 이메일이나 인증을 마친 보조 이메일과 겹치면(대소문자 무시) 안 된다.
- 허용 이메일 도메인 설정(`yona.allowed-sending-mail-domains`, 쉼표로 이은 목록, 대소문자와 공백 무시)이 있으면 목록에 없는 도메인은 안내 `user.unacceptable.email.domain`과 함께 `400`이다.
- 만들기: 비밀번호는 새 소금으로 해시하고(SHA-256 1024회, v1.6과 같은 방식), 이름은 서식 태그(`<b>` 등)만 남기고 정화하며, 가입 시각을 기록한다. 관리자 승인제(`yona.signup.require-admin-confirm`)이거나 이메일 인증(`yona.use-email-verification`)을 쓰면 **잠금**, 아니면 **활성**이다. 같은 주소의 인증 전 보조 이메일 행은 지운다.
- 이메일 인증을 쓰면 인증 정보(무작위 UUID 코드, 시각)를 만들고 인증 메일 요청을 이벤트로 알린다(`UserVerificationRequestedEvent`, 메일은 메일 단계에서, A-30). 안내 `user.verification.mail.sent`.
- 승인제이고 잠겼으면 안내 `user.signup.requested`(위 안내를 덮는다)만 하고 로그인시키지 않는다. 잠겼지만 승인제가 아니면(이메일 인증) 로그인시키지 않는다. 활성이면 가입과 동시에 로그인된다. 어느 경우든 `303`으로 홈.
- 폼이 보내지 않는 항목(`state`, `isGuest`, `token` 등)은 받지 않는다(A-24).

## 이메일 인증 (`GET /verify/:loginId/:code`)

로그인한 사용자는 홈으로 보낸다. 아이디와 코드가 맞는 인증 정보가 없으면 `404` `Invalid verification`. 만든 지 하루가 지났으면 인증 정보를 지우고 같은 `404`. 맞으면 사용자를 활성으로 바꾸고 인증 정보를 지운 뒤 "Verified User" 화면을 보여 준다.

## 사용 여부 확인 (`GET /user/isUsed?name=`, `GET /user/isEmailExist?email=`)

`{"isExist": ..., "isReserved": ...}`와 `{"isExist": ...}`를 돌려준다. 아이디는 사용자 로그인 ID나 조직 이름과 겹치는지(대소문자 무시)를, 이메일은 대표 이메일이나 인증을 마친 보조 이메일과 겹치는지를 본다. 인증 전 보조 이메일은 "없음"이다.

## 비밀번호 재설정

- `GET /lostPassword`: 아이디와 이메일 입력. 로그인했으면 자기 정보로 채워진다.
- `POST /lostPassword`: 아이디의 사용자가 있고 이메일이 **대소문자까지 같으면** 일회용 값(무작위 40자 16진수)을 만들어 기억하고 메일 요청을 이벤트로 알린다(`PasswordResetRequestedEvent`). 화면에 "Mail has been sent."를 보여 준다. 틀리면 "Failed to send mail." 아래에 "Invalid password reset request"를 보여 준다. 응답은 어느 쪽이든 `200`.
- 일회용 값은 v1.6처럼 **메모리**에 두며 서버를 다시 시작하면 사라진다. 사용자마다 하나만 유효하고 새로 요청하면 이전 값은 쓸 수 없다. 한 시간이 지나면 만료된다.
- `GET /resetPassword?s=값`: 새 비밀번호 입력 폼(값은 숨은 입력란). `s`가 없으면 `400`.
- `POST /resetPassword`: 값이 유효하면 비밀번호를 바꾸고(기존 소금을 그대로 쓴다) 값을 지운 뒤 같은 응답으로 로그인 화면을 보여 준다(안내 `user.loginWithNewPassword`). 유효하지 않으면 `400` "Wrong url to reset password.". 비밀번호가 비어 있어도 v1.6처럼 받는다.
- 사이트 관리자: `POST /:사용자?action=resetPassword`가 그 사용자의 비밀번호를 무작위 값의 앞 여섯 글자로 바꾸고 `{"loginId","name","newPassword","isSuccess":true}`를 돌려준다. 사이트 관리자가 아니거나 없는 사용자이면 `403` `{"isSuccess":false,"reason":"FORBIDDEN"}`, `action`이 다르면 `400` `BAD_REQUEST`.

## 그 밖의 변경

- 필수 요청 파라미터가 없거나 타입이 맞지 않으면 오류 처리기가 400 화면을 보여 준다(Play `onBadRequest`).
- `/favicon.ico`를 자원 처리기 대신 컨트롤러가 제공한다. `POST /{user}` 같은 한 구간 경로가 생기면서 자원 처리기보다 먼저 걸려 405로 처리되었기 때문이다.
- 로그인 세션을 만드는 부분을 `LoginSessionService`로 뽑았다(로그인과 가입이 공유한다).

## 검증

`SignupTest` 17건, 설정 변형 3개 클래스 5건, `PasswordResetTest` 12건.
