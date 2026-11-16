---
title: v1.6 결함 수정 W4 (그룹, 사이트 관리, 사용자와 인증, OAuth)
type: ui-contract-verification
updated: 2026-11-11
---

# v1.6 결함 수정 결과 W4

담당 37건(그룹 14, 사이트 관리 6, 사용자 8, 사용자 보안 9, OAuth 1)을 분류했다. 근거는 `D-organization-site.md`, `G-user-oauth.md`와 각 계약 파일이다.

## 요약

- 고침(FIX) 24건, 그대로 둠(SKIP) 9건, 확인을 받음(ASK) 4건.
- 시험: Kotlin 20개 클래스 통과(아래), JS 시험(`node src/test/js/run.mjs`) 전체 실행.
- 먼저 실패를 확인한(Red) 시험은 organization-F2(JS)뿐이다. 나머지는 시험과 수정을 같은 작업에서 만들고 수정 뒤 통과를 확인했다. 수정을 되돌려 실패를 보는 단계는 하지 못했다.
- 메시지 키 9개를 5개 번들과 `MessageParityTest.addedKeys`에 더했다.
- 공용 파일(`messages*.properties`, `MessageParityTest`)은 커밋 하나에 같이 들어 있다. 커밋은 항목별이 아니라 영역별 묶음이다.
- 죽은 코드는 지우지 않았다(`user/files.html`의 `searche!` 핸들러가 남아 있다).

## 항목별 결과

| 항목 | 분류 | 한 일 또는 이유 | 시험 |
|---|---|---|---|
| oauth-F7 | FIX | v1.6은 로그인 폼으로 보낼 때 경로만 싣고 질의가 사라졌다. 지금은 GET이면 질의 문자열도 인코딩해 `redirectUrl`에 싣는다 (`AnonymousCheckStage`) | AnonymousCheckTest |
| organization-F2 | FIX | v1.6은 역할 변경이 실패해도 안내 없이 라벨이 바뀐 채 남았다. 지금은 안내하고 라벨과 값을 서버 역할로 되돌린다 (`organization.Member.js`) | member.test.mjs |
| organization-F3 | FIX | v1.6은 Referer가 `members?x=1`이면 자동 완성이 406이었다. 지금은 쿼리와 해시를 떼고 판정한다 | UserApiTest |
| organization-F4 | FIX | v1.6은 빈 이름이나 형식 오류로 설정을 저장하면 500이었다. 지금은 400과 이름 아래 안내(`organization.name.alert`) | OrganizationSettingTest |
| organization-F5 | ASK | 아래 ASK 절 | |
| organization-F6 | FIX | v1.6은 `ul.project-util` 안에 button과 div를 바로 넣었다. 지금은 `li`로 감싼다 | TemplateContractRegressionTest |
| organization-F7 | FIX | v1.6은 게스트에게 생성 폼은 열고 제출만 막았다. 지금은 폼(GET)도 막는다 (`@GuestProhibit`) | OrganizationCreateTest |
| organization-F9 | FIX | v1.6은 주소 대소문자나 한글 인코딩이 다르면 활성 탭이 비었다. 지금은 디코딩하고 소문자로 비교한다 | OrganizationViewTest |
| organization-F10 | FIX | v1.6은 `created`, `by`, `at`, `group`, `Group Project`, 권한 없음 문구가 번역되지 않았다. 지금은 메시지 키 6개 | TemplateContractRegressionTest |
| organization-F12 | FIX | v1.6은 자동 완성 `info`에 이름을 이스케이프 없이 넣었다. 지금은 이름과 아이디를 HTML 이스케이프한다 | UserApiTest |
| organization-F16 | FIX | v1.6은 설정을 저장해도 안내가 없었다. 지금은 저장 안내(`organization.setting.saved`)를 남긴다 | OrganizationSettingTest |
| organization-F17 | FIX | v1.6은 게시판 정렬 링크가 검색어와 고른 프로젝트를 버렸다. 지금은 이어 받는다 | OrganizationBoardTest |
| organization-F18 | SKIP | 라벨 배지가 프로젝트 이슈 목록으로 가는 것은 라벨이 프로젝트 소속이라 맞는 동작이고, "전체" 탭 없음은 v1.6 설계이다. 무시되는 `data-project-names`는 폼의 select가 이미 값을 들고 있어 사용자에게 영향이 없다 | |
| organization-F19 | FIX | v1.6은 `#pagination` div를 `ul` 안에 두었다. 지금은 `ul` 밖이다 | OrganizationListsTest |
| site-F4 | ASK | 아래 ASK 절 | |
| site-F5 | FIX | v1.6은 프로젝트 삭제가 실패(이미 지워짐 등)해도 모달이 남고 안내가 없었다. 지금은 모달을 닫고 404와 403은 화면을 다시 열며 그 밖은 알린다 (`site/project-list.html`) | site.test.mjs |
| site-F7 | SKIP | 업데이트 화면이 요청 안에서 원격을 읽는 것은 현재 상태를 보이려는 의도이고 v2는 이미 10초 시간 제한을 두었다. 백그라운드 전환은 설계 변경이다 | |
| site-F9 | FIX | v1.6은 프로젝트를 고르지 않으면 수신자 없는 `mailto:`를 열고 요청이 실패하면 단추가 처리 중으로 남았다. 지금은 안내하고 단추를 되돌린다 (`site.MassMail.js`) | misc.test.mjs |
| site-F10 | SKIP | 메일 보내기가 200으로 다시 그리는 것은 v1.6 계약이고 v2는 실패를 정확히 안내한다(결함 없음) | |
| site-F13 | FIX | v1.6은 데이터 가져오기 제출 단추에 글자가 없었다. 지금은 `site.data.import`를 쓴다 | TemplateContractRegressionTest |
| user-F1 | SKIP | `GET`과 `POST` 모두 받는 것은 v1.6 라우트이고 영향이 없다 | |
| user-F2 | SKIP | 비로그인이면 로그인 폼으로 정상 이동한다. 결함이 아니다 | |
| user-F3 | FIX | v1.6은 확인 메일 안내가 한국어 고정이었다. 지금은 메시지 키 `user.email.validationSent` | EmailSettingsTest |
| user-F4 | FIX | v1.6은 탭을 바꾼 뒤 `daysAgo`에서 Enter를 치면 처음 탭으로 돌아갔다. 지금은 `shown.bs.tab`에서 기억한다 (`user.View.js`) | userview.test.mjs |
| user-F6 | SKIP | 이메일 확인 링크로 로그인되는 것은 v1.6 의도이고 2단계 인증 사용자는 이미 코드 화면을 거친다 | |
| user-F8 | ~~FIX~~ 되돌림(A-500, 2026-11-12) | v1.6은 파일 검색 입력란이 늘 비었다. 한때 검색한 값을 보였으나 선생님 지시로 v1.6과 같게 비운다 | UserFilesTest |
| user-F9 | SKIP | 구독 단추를 비로그인이 누르면 로그인 폼으로 가며 서버가 막는다. 오동작이 아니다 | |
| user-F10 | FIX | v1.6은 본인 초안이 이슈 건수에만 들어가 배지와 목록 합이 달랐다. 지금은 초안을 건수에서 모두 뺀다 | UserPageTest |
| usersec-F2 | FIX | v1.6은 비밀번호 확인을 서버가 보지 않았다. 지금은 가입, 재설정, 변경 세 곳에서 확인 입력이 있고 다르면 400 (확인 입력이 아예 없는 요청은 허용) | SignupTest, PasswordResetTest, ProfileEditTest |
| usersec-F3 | ASK | 아래 ASK 절 | |
| usersec-F4 | FIX | v1.6은 사용 여부 확인 요청에 값을 인코딩하지 않아 `+`가 공백으로 읽혔다. 지금은 `encodeURIComponent` (`user.SignUp.js`) | signup.test.mjs |
| usersec-F7 | FIX | v1.6은 가짜나 만료된 토큰에도 재설정 폼이 200으로 떴다. 지금은 폼을 열 때 400 | PasswordResetTest |
| usersec-F8 | SKIP | 재설정 성공 뒤 같은 응답으로 로그인 화면을 그리는 것은 v1.6 계약이고 새로 고침하면 명확한 400이 난다. 영향이 작아 계약을 유지한다 | |
| usersec-F11 | ASK | 아래 ASK 절 | |
| usersec-F13 | FIX | v1.6은 Referer 없는 로그아웃이 `Location: null`이었다. 지금은 홈(`/`). 잠긴 계정 로그인 실패 경로도 같다 | LoginControllerTest |
| usersec-F14 | SKIP | 사이트 이름은 운영자가 설정 파일에 넣는 값이라 사용자가 주입할 수 없다 | |
| usersec-F15 | FIX | v1.6은 로그인 화면에 `loginIdOrEmailD`가 둘이었다. 지금은 페이지 폼을 `loginIdOrEmail`로 바꿔 id가 겹치지 않고, 스크립트의 초점(`$('#loginIdOrEmail').focus()`)도 동작한다 | LoginFormPageTest |

## 돌린 시험

Kotlin(`-Dyona.it.db=h2`, 모두 통과): OrganizationSettingTest, OrganizationViewTest, OrganizationCreateTest, OrganizationListsTest, OrganizationBoardTest, UserApiTest, TemplateContractRegressionTest, MessageParityTest, UserFilesTest, UserPageTest, EmailSettingsTest, SignupTest, PasswordResetTest, ProfileEditTest, LoginControllerTest, LoginFormPageTest, AnonymousCheckTest, RedirectLocationEncodingTest, SiteDataTest, DeviceAuthorizationPageTest.
JS: `node src/test/js/run.mjs` (member, misc, site, userview, signup 시험을 새로 더하거나 늘렸다).

## v1.6과 달라진 점 (differences-from-v16.md 옮김용)

위 표의 "v1.6은 ... 지금은 ..." 문장이 그대로 항목이다. 추가로 알릴 것: 이메일 확인 안내와 조직 화면 고정 문구가 언어 설정을 따른다. 비밀번호 확인 불일치는 400이다.

## ASK (확인 필요)

### organization-F5 이름 규칙이 세 군데에서 다름
서버는 `a..b`를 받고 만들기와 설정의 화면 검사는 거부하며, 구성원 추가 입력의 `pattern`은 한글을 거부하지만 현재 브라우저는 그 `pattern`을 무시한다.
- 선택지 A: 서버 규칙을 화면 규칙에 맞춰 `a..b` 같은 이름을 거절한다. B: 화면 규칙을 서버에 맞춘다. C: 그대로 둔다.
- 권장: A. 이유: 이미 `a..b`로 만들 수 없는 화면이 기준이고, 서버가 느슨하면 직접 요청으로만 만들어지는 이름이 생겨 URL과 저장소 경로 문제로 이어질 수 있다. 다만 이미 있는 이름과 로그인 ID 규칙(`LoginIdRules`)에 영향이 있어 정책 결정이 필요하다.

### site-F4 자기 자신 삭제와 잠금, 기본 관리자 삭제
사이트 관리자가 자기 계정을 삭제하거나 잠그고, 기본 관리자(`admin`)를 삭제할 수 있다. 권한 회수는 이미 보호 규칙이 있다.
- 선택지 A: 자기 자신의 삭제와 잠금, 기본 관리자의 삭제를 거절(403)하고 안내한다. B: 확인 문구만 강하게 한다. C: 그대로 둔다.
- 권장: A. 이유: 마지막 관리자가 사라져 사이트를 못 쓰게 되는 사고를 막고, 권한 회수의 기존 보호 규칙과 일관된다. 계정 관리 정책이라 결정이 필요하다.

### usersec-F3 한글 로그인 ID
화면은 한글 ID를 경고하지만 서버 패턴은 한글을 허용해 직접 요청으로 한글 ID 가입이 된다.
- 선택지 A: 서버 패턴에서 한글을 뺀다(기존 한글 ID 사용자는 로그인 불가 위험이 있어 이전 데이터 점검 필요). B: 화면 경고를 없애고 한글을 공식 허용한다. C: 그대로 둔다.
- 권장: B. 이유: 이미 한글 ID 사용자와 한글 조직 이름이 존재하고(그룹 정규식도 한글 허용), 서버를 좁히면 기존 계정이 깨진다. 정책 결정이 필요하다.

### usersec-F11 로그인 실패 제한과 삭제된 계정 응답
틀린 비밀번호를 12번 넣어도 잠금이나 지연이 없고, 삭제된 계정은 비밀번호가 틀려도 맞아도 `404 user.deleted`라 계정 존재가 드러난다.
- 선택지 A: 실패 횟수 제한(예: 2단계 인증의 `TwoFactorAttempts`와 같은 5회/15분)을 더하고 삭제된 계정도 일반 실패(`403 user.login.invalid`)로 통일한다. B: 횟수 제한만 더한다. C: 그대로 둔다.
- 권장: A. 이유: 무차별 대입 방어가 없고 계정 열거가 가능하다. 다만 응답 코드가 바뀌어 요청 계약이 달라지므로 결정이 필요하다.
