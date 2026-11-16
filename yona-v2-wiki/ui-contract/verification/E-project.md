---
title: 프로젝트 영역 발견 사항 재현과 v1.6 대조 (담당 E)
type: ui-contract-verification
updated: 2026-11-11
---

# 프로젝트 영역 발견 사항 재현과 v1.6 대조 (담당 E)

대상은 `project-F1`, `F3`~`F14`, `F16`~`F24`로 22건이다(`project-F2`, `F15`는 이미 수정되어 담당 목록에 없다. F15의 수정은 이번에 화면에서도 확인했다, 아래 부록). v2는 검증용 사본 앱(19414)에서, v1.6은 공식 v1.16.0 릴리스를 직접 실행(19514, 스냅샷 data2)해 같은 요청과 같은 화면 동작으로 대조했다.

## 요약

| 판정 | 개수 | 항목 |
|---|---|---|
| v2 신규 결함 | 0 | |
| v1.6 결함 유지 | 17 | F1, F6, F7, F8, F9, F10, F11, F12, F14, F17, F18, F19, F20, F21, F22, F23, F24 |
| v2 추가 기능의 결함 | 3 | F3(가져오기에서 위키 메뉴 값이 버려짐), F13(생성 화면 이름 검사가 `.wiki`를 모름), F16(Mercurial 설정 화면의 빈 기본 브랜치 선택) |
| 결함 아님 | 1 | F5 |
| 문서 문제 | 1 | F4 |
| 재현 불가 | 0 | |

v2 신규 결함 목록: 없음. v2 추가 기능의 결함은 아래 각 항목에 고칠 곳을 적었다.

참고로 검증 중 앱을 두 번 다시 띄웠다(F3 때문에 `--yona.import-allow-local-sources=true`, F12 때문에 `--yona.display-private-repositories=true`를 자기 사본 앱에만 지정). v1.6은 F12 때문에 자기 사본 `conf/application.conf`에 `application.displayPrivateRepositories = true`를 덧붙였다.

## 항목별 결과

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 실행 결과와 소스 근거 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| project-F1 | 재현됨 | 관리자로 `GET /admin/hglfs/webhooks`(`<html lang="ko-KR">`, 브라우저 ko-KR). 응답에 영어 "Include git push events"가 2번 나옴(체크박스 라벨과 목록 머리). `messages_ko_KR.properties:785`는 대문자 `IncludeGitPush`만 있음 | 같음. v1.6 `GET /tester/proj1/webhooks` 200에 영어 "Include git push events". `conf/messages.ko-KR:741`이 `IncludeGitPush`, 화면은 `includeGitPush`(`app/views/project/webhooks.scala.html:42`) | v1.6 결함 유지 | 장부에 올린다(키 철자 맞추기는 별도 결정) |
| project-F3 | 재현됨 | 관리자로 `/_import` 폼에서 위키 체크박스가 체크된 채(`#menuSettingWiki` checked) 표시됨. 이름 `impwiki`, `url=file:///.../admin/gitproj.git`로 제출, 303 `/admin/impwiki`. 이어서 `GET /admin/impwiki/settingform`의 `name="wiki"` 입력에 `checked` 없음, 프로젝트 메뉴에 위키 탭 없음. 대조군: `POST /projects`(wiki=true)로 만든 `admin/ctlwiki`에는 위키 탭 있음. 원인은 `ImportController.kt:106`의 `ALL_MENUS`에 `wiki`가 없어 `ProjectImportService.kt:116`의 `"wiki" in form.menus`가 항상 거짓 | 해당 없음. v1.6 `ProjectMenuSetting`에 `wiki` 필드가 없고(`app/models/ProjectMenuSetting.java`) 가져오기 폼에도 위키 체크박스가 없다(`importing.scala.html:182-202`는 code, issue, pullRequest, review, milestone, board만). 발견 사항의 "v1.6은 폼 전체를 묶었다"는 위키 항목이 v1.6에 없어 비교 대상이 아니다 | v2 추가 기능의 결함(위키 메뉴 X-17, D-61) | 고친다: `ImportController.kt:106` `ALL_MENUS`에 `"wiki"` 추가(+시험 1건) |
| project-F4 | 재현됨(코드 쪽 서술이 맞음) | v2 `admin/hglfs`(관리자는 프로젝트 소유자 MANAGER): 홈 HTML에 단축키 `"Q"` 있음. 같은 프로젝트의 구성원(plainuser): `"Q"` 없음. 사이트 관리자가 남의 프로젝트(`plainuser/ownerproj`, 비구성원)를 열면 설정 톱니 링크(`settingform`)는 있으나 `"Q"`는 없음, 즉 두 조건이 다름(발견 사항의 말과 같음) | v1.6: 프로젝트 관리자 `tester`와 구성원 `other` 모두 `"Q"` 없음. 로그인 ID가 문자 그대로 `manager`일 때만 켜진다(`app/views/projectMenu.scala.html:148-150`). 위키 문서가 이 v1.6 동작을 적은 것이고 v2는 A-353으로 의도해서 바꿨다 | 문서 문제 | 문서만 고친다(`web/project-layout.md:78`, R-11 서술을 "v1.6은 loginId가 manager일 때만, v2는 프로젝트 MANAGER 역할일 때"로) |
| project-F5 | 재현 안 됨 | 읽을 수 없는 비공개 프로젝트에 비구성원이 `POST /admin/secretproj/watch`는 403(READ 검사 때문). 두 연산이 갈리는 유일한 조합(공개 프로젝트의 게스트 사용자: WATCH는 `user != null`로 참, READ는 `!isGuest` 때문에 거짓)을 직접 만들었다. 관리자가 `xssuser`를 `POST /sites/toggleGuestMode`로 게스트로 바꾸고 로그인: `GET /admin/gitproj` 403이라 구독 단추가 있는 화면 자체가 안 열리고, `POST /admin/gitproj/watch`도 403 | 같음. 게스트 `xssuser`로 `GET /tester/proj1` 403, `POST /tester/proj1/watch` 403, 비구성원 비공개 `POST /tester/priv1/watch` 403. 소스: `app/controllers/WatchProjectApp.java:30`(READ만), `app/utils/AccessControl.java:163-172` | 결함 아님(연산은 다르나 단추가 보이는 모든 경우에 READ도 참이라 눈에 보이는 차이가 없다. 추측이라던 서술은 확인됨) | 조치 없음 |
| project-F6 | 재현됨 | `admin/hglfs`를 "프로젝트 멤버만 코드 접근"으로 저장 후 비구성원 plainuser로 홈: 코드 탭 0개, 단축키 맵엔 `"C": "/admin/hglfs/code"` 있음. `c` 키를 누르면 `/admin/hglfs/code`로 이동해 "권한이 없습니다" 화면, `GET .../code` 403 | 같음. 설정 후 비구성원 `third`로 `/tester/proj1`: 코드 탭 0, `"C": "/tester/proj1/code"` 있음, `c` 키로 `/tester/proj1/code`로 이동, `GET` 403. 소스 `app/views/projectMenu.scala.html:60,76,85`(탭에만 조건), `:136-147`(단축키는 조건 없음) | v1.6 결함 유지 | 장부에 올린다 |
| project-F7 | 재현됨 | 관리자가 plainuser를 `admin/hglfs` 구성원으로 추가(`POST /admin/hglfs/members` 303). plainuser 홈 HTML에 `member-add-link` 있음, `GET /admin/hglfs/members` 403 | 같음. 구성원 `other`로 `/tester/proj1`: `member-add-link` 있음, `GET /tester/proj1/members` 403. 소스 `app/views/project/home.scala.html:123-124`, `ProjectApp.java:358-360`(`@IsAllowed(UPDATE)`) | v1.6 결함 유지 | 장부에 올린다 |
| project-F8 | 재현됨 | 사이트 관리자로 `plainuser/ownerproj` 홈(비구성원)에 탈퇴 단추 있음(`data-href=/plainuser/ownerproj/member/1/delete`), `DELETE`는 200 `{"location":"/plainuser/ownerproj"}`로 아무 일 없이 홈. 관리자가 소유자인 `admin/hglfs`에서 `DELETE .../member/1/delete`는 403 | 같음. 관리자로 `/other/other1`에 탈퇴 단추, `DELETE` 200 `{"location":"/other/other1"}`. 관리자 소유 `admin/adminproj`의 탈퇴는 403. 소스 `AccessControl.java:56`(사이트 관리자 항상 허용), `ProjectApp.java:1006-1017` | v1.6 결함 유지 | 장부에 올린다 |
| project-F9 | 재현됨 | 웹훅 목록 삭제 단추(`data-request-method="delete"`)의 `DELETE`를 가로채 500으로 응답: 요청 1건, 화면에 모달, 알림 등 0개. 프로젝트 삭제는 `project.Delete.js:54-59`에서 `Request.send` 결과를 보고 `$yona.alert`를 띄움(소스 확인) | 같음. v1.6도 같은 방식으로 웹훅 삭제를 500으로 막으면 요청 1건, 화면 알림 0개(`partial_webhooks_list.scala.html:74`의 `data-request-method`, `jquery.requestAs`는 실패 알림 없음) | v1.6 결함 유지 | 장부에 올린다 |
| project-F10 | 재현됨 | 구성원 plainuser로 홈에서 탈퇴 단추, 모달 "아니오", 다시 탈퇴 단추, "예": `DELETE /admin/hglfs/member/67/delete`가 2번 나감 | 같음. `other`로 같은 순서: `DELETE /tester/proj1/member/34/delete` 2번. 소스 `public/javascripts/service/yobi.project.Home.js:124-129` | v1.6 결함 유지 | 장부에 올린다 |
| project-F11 | 재현됨 | `GET /admin/hglfs/statistics` 200, 본문 "Under Construction". 프로젝트 홈 HTML에 `statistics` 링크 없음 | 같음. `GET /tester/proj1/statistics` 200 "Under Construction", 홈에 링크 없음. `statistics.scala.html`, `conf/routes:367` | v1.6 결함 유지 | 장부에 올린다 |
| project-F12 | 재현됨(설정을 켠 경우) | 기본값(false)에서는 목록에 공개 프로젝트만 나와 읽을 수 없는 행이 나올 수 없음. `--yona.display-private-repositories=true`로 띄운 뒤 plainuser로 `GET /projects`: 10행, "do not have permission" 0건, 비공개 `secretproj`가 일반 행으로 나오고 링크(`/admin/secretproj`)도 있음. 즉 영어 고정 문구의 else 가지는 어느 설정에서도 그려지지 않는다 | 같음(실행). `application.displayPrivateRepositories = true`로 띄운 v1.6에서 `third`로 `GET /projects`: 9행, "do not have permission" 0건, 비공개 `priv1` 일반 행과 링크 있음. 소스 `list.scala.html:47`, `ProjectApp.java:1131-1133` | v1.6 결함 유지(사실상 죽은 가지) | 장부에 올린다(정리는 사용자 확인 필요) |
| project-F13 | 재현됨 | 생성 화면에서 이름 `foo.wiki`로 제출: 클라이언트 검사를 통과해 `POST /projects` 1회, 서버가 거절해 같은 폼에 팝오버 "예약된 이름으로 사용할 수 없습니다"가 나옴(왕복 1회 뒤에 표시) | 다름. v1.6은 `foo.wiki`를 그대로 만든다(`tester/foo.wiki` 303로 생성됨). `.wiki` 접미사 규칙은 v2 추가(D-60, `ProjectNameRules.kt`)이고 v1.6 JS도 같은 정규식과 예약어(`yobi.project.New.js:28-29`)만 가짐 | v2 추가 기능의 결함(경미: 거절 안내는 나오나 서버 왕복 뒤) | 고친다: `project.New.js`의 `_validateForm`에 `/\.wiki$/i` 검사를 `project.name.reserved.alert`로 추가(설정 화면 이름 입력에도 같은 검사가 있으면 함께) |
| project-F14 | 재현됨 | 홈에서 설명 편집, 입력란에서 Enter: `PUT` 0건, 화면 그대로. 저장 단추의 `PUT`을 500으로 막으면 요청 1건, 화면 알림 0개, 콘솔에 `err>> {status: 500...}`만 | 같음. v1.6 홈에서 Enter: `PUT` 0건. `PUT` 500: 요청 1건, 알림 0, 콘솔 1건. `home.scala.html:45`(`onsubmit="return false"`), `yobi.project.Home.js:120-122` | v1.6 결함 유지 | 장부에 올린다 |
| project-F16 | 재현됨(Mercurial 부분). 서버가 숨은 필드를 받는 부분은 소스만 | Mercurial 프로젝트 `admin/hglfs`의 `GET .../settingform`: 기본 브랜치 패널(`#defaultBranceSettingPanel`)이 그려지고 `#project-default-branch`의 `<option>`이 0개(Git인 `admin/gitproj`는 1개). 원인은 `setting.html:86` `vcs.pullRequests`로 감싼 안에 기본 브랜치 패널이 있고 Mercurial은 `DEFAULT_BRANCH` 능력이 없어 `branches`가 빈 목록(`ProjectSettingController.kt:97-98`) | 해당 없음(Mercurial은 v1.6에 없음). 코드 메뉴를 끄면 패널을 숨기는 화면 동작과 서버가 필드를 그대로 받는 것은 v1.6도 같다(숨김만, `setting.scala.html`의 메뉴 토글 스크립트). 이 부분은 직접 실행하지 않았고 소스 대조 | v2 추가 기능의 결함(Mercurial 빈 선택지). 나머지는 v1.6 동일 | 고친다: `setting.html:125` 패널을 `th:if="${vcs.defaultBranch}"`로 한 번 더 감싼다 |
| project-F17 | 재현됨 | 라벨 화면 `GET /admin/hglfs/issue/labelsform` 로드 요청은 `labelsform`, `labels.css` 둘뿐(라벨, 분류 조회 요청 없음). 서버 경로는 살아 있음: `GET .../issue/label/categories` 200, `.../label/category/1` 200 | 같음. v1.6 로드 요청은 `labelsform`, `labels.css`뿐(그 밖은 구글 분석). 같은 두 경로 모두 200. 분류 삭제 `DELETE ../label/category/:id`는 `conf/routes:289`에 있으나 화면에 단추 없음 | v1.6 결함 유지(쓰이지 않는 경로) | 장부에 올린다(정리는 사용자 확인 필요) |
| project-F18 | 재현됨 | 라벨 삭제, 확인 후 `POST .../issue/label/5/delete`를 500으로 막음: 요청 1건, 행 6개 그대로, 알림 0개 | 같음. `POST /tester/proj1/issue/label/3/delete` 500: 행 3개 그대로, 알림 0. 소스 `yobi.issue.LabelEditor.js:522-531`(`.done`만 있음) | v1.6 결함 유지 | 장부에 올린다 |
| project-F19 | 재현됨 | 이전 화면에서 없는 사용자 `nosuchuser`를 소유자로 두고 "예": `PUT /admin/gitproj/transfer?owner=nosuchuser` 1건. 모달을 닫고 다시 동의, "예"를 눌러도 요청 합계 1건 그대로(`.one("click")`) | 같음. `PUT /tester/proj1/transfer?owner=nosuchuser` 1건, 두 번째 시도 후에도 합계 1건. `yobi.project.Transfer.js:51` | v1.6 결함 유지 | 장부에 올린다 |
| project-F20 | 재현됨 | (1) 직접 요청: `POST /admin/hglfs/webhooks` form `webhookType=JSON`, `gitPush` 없음 → 303, 목록에서 JSON, git push 체크 해제로 저장됨. (2) 화면: `#gitPush`를 켰다 끈 뒤 JSON 선택 → 체크가 꺼진 채(`checked=false`)이고 `onclick="return false;"`로 잠김, 제출하면 JSON이면서 `gitPush=false`로 저장. 손대지 않은 새 화면에서 JSON을 고르면 체크됨(`attr('checked')` 때문에 이미 눌러 본 뒤에는 상태가 바뀌지 않는다는 추측이 맞음) | 같음. v1.6에서 직접 요청 303 후 `gitPush` 해제로 저장, 같은 화면 순서에서 `checked=false`, `onclick="return false;"`. 소스 `webhooks.scala.html:63-70`, `ProjectApp.java:1308`(`gitPush == null`이면 false) | v1.6 결함 유지 | 장부에 올린다 |
| project-F21 | 재현됨 | 웹훅을 `secret=s3cret-plain`으로 만든 뒤 `GET .../webhooks`에 `s3cret-plain`이 평문으로 나옴, 비면 `NONE` | 같음. v1.6 목록에도 `s3cret-plain` 평문(`partial_webhooks_list.scala.html:58-61`) | v1.6 결함 유지 | 장부에 올린다 |
| project-F22 | 재현됨 | 이름이 `<i id="inj">zzbold</i>`인 사용자(`xssuser`)를 가입시킨 뒤 `GET /users?query=zzbold`(Accept JSON, `Referer: .../members`)가 200으로 `info`에 `<b class='mention_name'><i>zzbold</i></b>`를 이스케이프 없이 담음(`id` 속성은 가입 시 제거됨). `Referer`에 `?x=1`이 붙으면 406, 없으면 406. 구성원 화면 입력란에 `zzbold`를 치면 DOM에 `.mention_name i` 1개가 실제 요소로 삽입됨 | 같음. v1.6 `GET /-_-api/v1/users?query=zzbold`(`UserApp.java:87-123`)가 같은 `info`(이스케이프 없음)를 돌려주고 쿼리 문자열 Referer는 406. 구성원 화면에서 `.mention_name i` 3개(같은 이름 가입자 3명)가 요소로 삽입됨 | v1.6 결함 유지 | 장부에 올린다(이름에 태그가 들어가는 입력 제한은 v1.6 그대로) |
| project-F23 | 재현됨 | 설정 화면에서 설명을 바꿔 저장: `POST .../setting` 303 → `/admin/gitproj/settingform`, 성공 안내(`.alert`, 토스트 등) 0개. 이어서 이름을 `bad name!`, 설명을 바꿔 제출: 400이고 다시 그려진 폼의 설명은 직전에 저장된 값, 이름은 `gitproj`(입력한 값이 아님) | 같음. v1.6도 303 후 `/tester/proj1/settingform`, 안내 0개. 오류 400의 폼은 저장된 설명, 이름 `proj1`로 다시 그려짐 | v1.6 결함 유지 | 장부에 올린다 |
| project-F24 | 재현됨 | 구성원일 때 plainuser가 자기 프로젝트 경로로 남의 프로젝트 라벨을 지움: `POST /plainuser/ownerproj/issue/label/5/delete`(`_method=delete`) 200, `admin/hglfs`의 라벨 5가 사라짐. 같은 사용자가 구성원이 아닐 때는 같은 요청이 403(진짜 경로도 403). 즉 권한은 라벨이 속한 프로젝트로 판정하고 경로의 프로젝트는 보지 않음 | 같음. v1.6에서 `tester`(proj1 관리자, `other/other1`의 구성원 아님)가 `POST /other/other1/issue/label/3/delete` 200으로 `proj1` 라벨이 사라짐. proj1 비구성원 `third`(자기 프로젝트 `third1` 경로 사용)와 `other`는 403. 소스 `IssueLabelApp.java:253-255`(`@IsAllowed(DELETE, ISSUE_LABEL)`, 경로 인자는 "Don't use") | v1.6 결함 유지 | 장부에 올린다(R-12) |

## 부록: 이번에 확인한 그 밖의 사실

- project-F15(수정됨, A-457)가 목록에 없지만 같은 화면에서 확인했다. 생성 화면에서 select2로 Subversion을 고르면 v2는 `#vcs` 값 `Subversion`, `#svn` 경고가 보이고 페이지 오류 0건. v1.6도 같은 동작이다. 수정은 실제로 작동한다.
- 이번 검증은 모두 검증용 사본에서 했으므로 그 DB에 가입자 `xssuser`, 웹훅, 라벨 삭제, `admin/impwiki`, `admin/ctlwiki` 같은 흔적이 남아 있다. 저장소 소스, 템플릿, 시험은 바꾸지 않았다.
- v1.6 계정 `admin`의 실제 비밀번호는 `admin1234`이다(`pw.mjs`의 `ACCOUNTS_V16.admin`은 `admin`이라 `loginV16(..., 'admin')`은 로그인에 실패한다). 이번에는 폼을 직접 보내 로그인했다.

## 범위 점검

| 번호 | 결과 파일에 있음 |
|---|---|
| project-F1 | 예 |
| project-F3 | 예 |
| project-F4 | 예 |
| project-F5 | 예 |
| project-F6 | 예 |
| project-F7 | 예 |
| project-F8 | 예 |
| project-F9 | 예 |
| project-F10 | 예 |
| project-F11 | 예 |
| project-F12 | 예 |
| project-F13 | 예 |
| project-F14 | 예 |
| project-F16 | 예 |
| project-F17 | 예 |
| project-F18 | 예 |
| project-F19 | 예 |
| project-F20 | 예 |
| project-F21 | 예 |
| project-F22 | 예 |
| project-F23 | 예 |
| project-F24 | 예 |
