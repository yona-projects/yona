---
title: 계정 설정과 첨부 올리기
type: web
sources: [app/controllers/UserApp.java:582-626, 877-1007, 1035-1142, app/views/user/edit*.scala.html, app/views/user/partial_edit_tabmenu.scala.html, app/controllers/AttachmentApp.java:60-130, 174-279, app/models/Attachment.java:440-484, app/models/Email.java]
updated: 2026-10-22
---

# 계정 설정

모두 로그인해야 한다(안내 `user.login.alert`와 함께 로그인 화면으로). 화면은 `siteLayout(로그인ID, USER)`이고 위쪽에 탭 다섯 개(프로필, 비밀번호, 알림, 이메일, 토큰)가 있다.

## 프로필 (`GET /user/editform`, `POST /user/edit`)

- 화면: 아이디(읽기 전용), 이름, 이메일(`type=email`), 아바타 영역(파일 선택, 자르기 대화상자), 최근 방문 목록 초기화 버튼. 아바타 폼은 이름과 이메일을 숨은 값으로 가진다. 스크립트 `user.Setting`이 아바타를 `/files`로 올린 뒤 `avatarId`를 담아 `POST /user/edit`로 저장한다.
- 저장: 이메일이 비었으면 `user.wrongEmail.alert`, 바꾸는 이메일을 다른 곳에서 쓰고 있으면 `user.email.duplicate`로 `400`(안내와 함께 편집 화면). 이메일 **형식은 검사하지 않는다**. 이름은 서식 태그만 남기고 정화한다. 성공하면 `303`으로 `/로그인ID?daysAgo=14&selected=projects`.
- 아바타: `avatarId`의 첨부가 **본인이 올린 임시 첨부**이고 이미지일 때만 바꾼다. 이전 아바타는 지운다. 1MB를 넘어도 v1.6처럼 거부하지 않는다(R-21). v1.6은 남의 첨부 번호도 받았지만 v2는 받지 않는다(A-31).
- 새 이메일이 다른 사용자의 인증 전 보조 이메일과 같으면 그 행을 지운다.

## 비밀번호 (`GET /user/editform/password`, `POST /user/resetPassword`)

현재 비밀번호, 새 비밀번호, 확인 입력란과 "비밀번호 재설정 메일" 링크. 현재 비밀번호가 맞으면 소금은 그대로 두고 해시를 바꾼 뒤 **로그아웃시키고** 안내 `user.loginWithNewPassword`와 함께 `303` 로그인 화면으로 보낸다. 틀리면 `user.wrongPassword.alert`로 `400`(프로필 화면). 새 비밀번호가 비어 있어도 받는다(v1.6).

## 토큰 (`GET|POST /user/editform/token`, `.../token_reset`)

개인 API 토큰을 보여 준다. 없으면 만든다. 다시 만들기(`token_reset`)는 지우고 새로 만든다. v1.6은 시각 문자열의 SHA-256이라 예측할 수 있었다. v2는 무작위 32바이트의 Base64(44자)를 쓴다(A-32). 토큰은 `Yona-Token` 헤더로 API 요청을 인증한다.

## 방문 목록 (`POST /user/resetVisitedList`)

최근 방문 프로젝트 기록을 지우고 `303` `/user/editform`(안내 `userinfo.reset.visited.project.list.done`).

## 이메일 (`GET /user/editform/emails`와 보조 이메일 관리)

대표 이메일과 보조 이메일 목록(아바타는 Gravatar 40px). 각 줄에 삭제 버튼과, 인증된 이메일이면 "대표로 지정", 아니면 "확인 메일 보내기" 버튼.

| 요청 | 동작 |
|---|---|
| `POST /user/email` | 비었으면 `error.required`, 형식이 틀리면 `error.email`로 안내 후 `303`. 로그인하지 않았으면 `403`(찾을 수 없음 화면). 다른 곳에서 쓰는 이메일(대표 이메일, 인증된 보조 이메일)이거나 내 보조 이메일이면 중복 안내. 아니면 인증 전 상태로 추가 |
| `DELETE /user/email/delete/:id` | 본인(또는 사이트 관리자)만. 지우고 `303` |
| `PUT /user/email/setAsMain/:id` | 본인만. 보조 이메일을 대표로 올리고, 이전 대표 이메일을 **인증된** 보조 이메일로 내린다. 인증 전 이메일도 올릴 수 있다(v1.6) |
| `POST /user/email/sendValidationEmail/:id` | 본인만. 숫자 50자 토큰을 만들고 확인 메일 요청을 이벤트로 알린다(`EmailValidationRequestedEvent`, A-30). 안내는 v1.6처럼 한국어 고정 문구 |
| `GET /user/email/confirm/:id/:token` | 토큰이 맞으면 인증 처리하고 **그 이메일의 사용자로 로그인**시킨 뒤 `303` `/user/editform`. 틀리거나 없으면 `403`(찾을 수 없음 화면) |

(email, valid) 조합이 유일하므로 다른 사용자가 이미 같은 주소를 인증 전 상태로 갖고 있으면 추가가 DB 오류(500)가 된다. v1.6도 같다.

## 알림 탭

알림 서브시스템(4단계)에서 만든다. 그때까지 `GET /user/editform/notifications`는 오류이다. 탭 링크는 이미 있다.

# 첨부 올리기와 정리 (`AttachmentController`)

- `POST /files` (필드 `filePath`): 로그인한 사용자(또는 `Yona-Token`)가 올리면 그 사용자의 임시 첨부(컨테이너 `USER`)로 저장한다. 파일 이름은 NFC로 정규화한다. 새로 만들면 `201`, 같은 첨부가 이미 있으면 `200`. 헤더 `Location: /files/id`, `Vary: Accept`, 본문은 JSON `{id, mimeType, name, url, size}`(모두 문자열)이고 JSON을 받을 수 없는 클라이언트에는 `Content-Type: text/html`로 알린다. 파일이 없으면 `400`, 익명이면 `403`. 폼의 `authorEmail`, `authorLoginId`로 다른 사용자 명의로 올리는 v1.6 기능은 받지 않는다(A-31).
- `GET /files?containerType=&containerId=`: 컨테이너의 첨부 목록 JSON `{"attachments": [...]}`. 하나라도 읽을 수 없으면 `403`. 파라미터가 없으면 `{}`.
- `POST /files/:id` (멀티파트이면 `_method=delete` 필요): `DELETE` 권한이 있으면 지운다. 같은 해시를 쓰는 첨부가 남아 있으면 "원본 파일은 남아 있다" 문구, 아니면 "모두 지웠다" 문구. 없는 첨부 `404`, 권한 없음 `403`, `_method` 오류 `400`.
- **임시 첨부 정리**(`TemporaryFileCleaner`): 서버가 뜬 뒤 보관 기간(`yona.temporary-files-keep-up-time`, 기본 하루)이 지나면 처음 실행하고 같은 주기로 반복한다. v1.6의 조회 조건이 `createdDate >= (지금 - 보관 기간)`이라 **오래된 것이 아니라 최근 보관 기간 안에 올린** 임시 첨부를 지운다. 같게 재현한다(R-20).

## 검증

`ProfileEditTest` 23건, `EmailSettingsTest` 14건, `AttachmentUploadTest` 16건.
