---
title: 프로젝트 설정 계열 화면 계약
type: ui-contract
sources:
  - src/main/resources/templates/project/partial-settingmenu.html
  - src/main/resources/templates/project/setting.html
  - src/main/resources/templates/project/members.html
  - src/main/resources/templates/project/transfer.html
  - src/main/resources/templates/project/delete.html
  - src/main/resources/templates/project/change-vcs.html
  - src/main/resources/templates/project/webhooks.html
  - src/main/resources/templates/project/issue-labels.html
  - src/main/resources/templates/project/partial-issue-labels-list.html
  - src/main/resources/templates/project/deploykeys.html
  - src/main/resources/templates/project/deploykey-new.html
  - src/main/resources/templates/project/lfs.html
  - src/main/resources/templates/project/lfs-hg.html
  - src/main/resources/templates/project/rulesets.html
  - src/main/resources/templates/project/ruleset-form.html
  - src/main/kotlin/dev/yona/web/project/
  - src/main/kotlin/dev/yona/web/issue/IssueLabelController.kt
  - src/main/resources/static/assets/javascripts/esm/service/
updated: 2026-11-11
---

# 프로젝트 설정 계열 화면 계약 (2/2)

공통 계약(접근 검사 체인, CSRF, 응답 규칙, 반응형, 단축키 구조)은 [project.md](project.md) 1절을 따른다. 이미 있는 글: [web/project-setting.md](../web/project-setting.md), [web/project-members.md](../web/project-members.md), [web/project-transfer.md](../web/project-transfer.md), [web/project-delete.md](../web/project-delete.md), [web/webhooks.md](../web/webhooks.md), [web/label-api.md](../web/label-api.md), [architecture/rulesets.md](../architecture/rulesets.md), [architecture/git-lfs.md](../architecture/git-lfs.md), [architecture/ssh.md](../architecture/ssh.md).

## 0. 화면 목록과 공통 사항

| 템플릿 | URL (메서드) | 컨트롤러 | 필요한 권한 |
|---|---|---|---|
| `partial-settingmenu.html` | (조각) | | |
| `setting.html` | `GET /{u}/{p}/settingform`, `POST /{u}/{p}/setting` | `ProjectSettingController` `:47`, `:55` | 프로젝트 UPDATE |
| `members.html` | `GET /members`, `POST /members`, `POST /member/{id}/edit`, `DELETE /member/{id}/delete` | `ProjectMemberController` `:53,81,112,132` | 목록, 추가, 역할 변경은 UPDATE. 삭제는 읽기 + (본인이거나 UPDATE) |
| `transfer.html` | `GET /transfer`, `PUT /transfer`, `GET /project/transfer/{id}/{key}` | `ProjectTransferController` `:42,49,77` | DELETE(관리자). 수락은 받는 쪽 |
| `delete.html` | `GET /deleteform`, `DELETE /delete` | `ProjectDeleteController` `:41,50` | DELETE |
| `change-vcs.html` | `GET /changeVCS`, `POST /changeVCS` | `ProjectVcsController` `:34,57` | UPDATE |
| `webhooks.html` | `GET /webhooks`, `POST /webhooks`, `DELETE /webhooks/{id}` | `WebhookController` `:49,65,85` | UPDATE |
| `issue-labels.html` + `partial-issue-labels-list.html` | `GET /issue/labelsform` 외 | `web/issue/IssueLabelController` `:88` 외 | 화면은 UPDATE |
| `deploykeys.html`, `deploykey-new.html` | `GET/POST /setting/deploykeys`, `GET /setting/deploykeys/new`, `POST /setting/deploykeys/{id}/delete` | `DeployKeyController` `:45,53,62,78` | UPDATE, `@RequiresVcs(DEPLOY_KEYS)` |
| `lfs.html`, `lfs-hg.html` | `GET /setting/lfs`, `POST /setting/lfs/locks/{id}/delete` | `ProjectLfsController` `:53,78` | UPDATE, `@RequiresVcs(LFS_SCREEN)` (잠금 해제는 `LFS`) |
| `rulesets.html`, `ruleset-form.html` | `GET /setting/rulesets`, `GET /setting/rulesets/new`, `POST /setting/rulesets`, `GET/POST /setting/rulesets/{id}`, `POST /setting/rulesets/{id}/delete` | `RulesetController` `:37-98` | UPDATE, `@RequiresVcs(PROTECTED_REFS)` |

모든 화면은 `layout/project-layout`의 `PROJECT_SETTING`이고(마일스톤 제외) 맨 앞에 `project/menu`(톱니 활성)와 `partial-settingmenu`를 끼운다. 설정 화면에 들어가려면 프로젝트 관리자(`MANAGER` 역할), 그룹 관리자, 사이트 관리자여야 한다(`AccessControl.kt:159-170`). 일반 멤버는 403 `error.forbidden`(프로젝트 레이아웃), 익명은 403 로그인 화면이다. 이 영역에서 로그인이 필요한 요청은 모두 같은 규칙이므로 이후 표의 "권한" 칸에는 필요한 연산만 쓴다.

## 1. 설정 하위 탭 (`partial-settingmenu.html`)

조각 `settingmenu(view, requestPath)`. `requestPath`가 탭의 주소와 같으면(또는 규칙 묶음, 배포 키, LFS는 앞부분이 같으면) `active`.

| 탭 (`id`) | 보이는 조건 | 링크 | 근거 |
|---|---|---|---|
| 프로젝트 설정 (`subMenuProjectSetting`) | 항상 | `/settingform` | `partial-settingmenu.html:8-9` |
| 구성원 (`subMenuProjectMember`) | 항상 | `/members`, 가입 신청 수 배지(`enrolledUserCount > 0`) | `:10-11` |
| 규칙 묶음 (`subMenuRuleset`) | `vcs.protectedRefs`(Git, Mercurial) | `/setting/rulesets` | `:12-13` |
| 배포 키 (`subMenuDeployKey`) | `vcs.deployKeys`(Git, Mercurial) | `/setting/deploykeys` | `:14-15` |
| LFS (`subMenuLfs`) | `vcs.lfsScreen`. 글자는 Git이면 `project.lfs`, 아니면 `project.lfs.hg` | `/setting/lfs` | `:16-17` |
| 이슈 라벨 (`subMenuIssueLabel`) | 항상 | `/issue/labelsform` | `:18-19` |
| 웹훅 (`subMenuWebhook`) | 항상 | `/webhooks` | `:20-21` |
| 이전 (`subMenuProjectTransfer`) | 항상 | `/transfer` | `:22-23` |
| 삭제 (`subMenuProjectDelete`) | 항상 | `/deleteform` | `:24-25` |
| 저장소 종류 변경 (`subMenuProjectChangeVCS`) | `menuSetting.code == true`일 때 보임(아니면 `display:none`). 설정 화면에서 코드 체크를 끄면 스크립트가 숨김 | `/changeVCS` | `:26-27` |

탭 링크의 `href`는 모두 `@{${base + '...'}}`로 컨텍스트 경로를 가진다(`partial-settingmenu.html:9-27`). 탭 링크 `active`는 `requestPath`(모델)로 정한다. 배포 키 추가 화면은 `base + '/setting/deploykeys'`를 넘겨 "배포 키" 탭이 활성이다. 규칙 묶음 편집 화면도 같다.

## 2. 프로젝트 설정 (`project/setting.html`)

### 2.1 개요
`GET /{u}/{p}/settingform` → 모델 `title`, `projectView`, `branches`(참조 이름 중 `refs/heads/*`만, `DEFAULT_BRANCH` 능력이 있을 때만 읽음. 없으면 빈 목록), `defaultBranchName`, `branchItemNames`(이름 → 짧은 이름), `oldPlaceHtml`(이전 위치 안내 HTML), `requestPath` (`ProjectSettingController.kt:95-115`). 템플릿 머리 주석의 `maxReviewerCount`는 모델에 없고 쓰지도 않는다. `POST /{u}/{p}/setting`(multipart): 필드 `id`, `watchingCount`(숨김, 서버가 읽지 않음), `name`, `overview`, `projectScope`, `isCodeAccessibleMemberOnly`, `isDismissingStaleApprovals`, `allowMergeCommit`, `allowSquashMerge`, `allowRebaseMerge`, `defaultMergeMethod`, `defaultBranch`, 메뉴 체크박스(`code`, `issue`, `pullRequest`, `review`, `milestone`, `board`, `wiki`), 파일 `logoPath`. 서버는 이 이름들만 읽는다(`bind`, `:79-91`, 결정 A-24).

### 2.2 레이아웃
`bubble-wrap gray` 안에 `box-wrap` 줄이 이어진다: 위(`top`): 로고 상자(`setting-box left`, `logo-wrap`의 `background-image`로 현재 로고(`@{${projectView.logoUrl}}`, 컨텍스트 경로를 가짐, `setting.html:24`), 안내 `bmp, jpg, gif, png`, 최대 `5MB`, 올리기 단추)와 이름, 설명(`setting-box right`). 중간 줄: 공개 범위, 이슈 템플릿(`vcs.onlineEditing`), 코드 열람 제한, (`vcs.pullRequests`일 때) 오래된 승인 해제 / 병합 방식 / (`vcs.defaultBranch`, 즉 Git일 때만) 기본 브랜치, 메뉴 설정. 아래(`bottom`): 저장 단추. 이름과 설명 필드는 `maxlength` 250(이름), 250(설명)이 클라이언트에만 있다.

### 2.3 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 근거 |
|---|---|---|---|---|
| 로고 올리기 `#logoPath` | 파일 | 항상(`accept="image/*"`) | 파일을 고르면 `$yona.isImageFile`로 확인, 이미지가 아니면 경고 `project.logo.alert` 후 값 비움, 이미지면 **폼 전체를 바로 제출** | `setting.html:34`, `project.Setting.js:78-88` |
| 이름 `#project-name` | 입력 | 항상 | 입력란에 팝오버(`project.transfer.description6`: 이름 바꾸기 안내, 왼쪽) | `:46,190` |
| 이전 위치 안내 | HTML | `oldPlaceHtml != null`(이전 이름이 있음) | 빨간 `소유자/이전이름` 안내 | `:47`, `ProjectSettingController.kt:100-104` |
| 공개 범위 | 라디오 | 보호(`protected`)는 `project.hasGroup()`일 때만 | PUBLIC, PROTECTED, PRIVATE | `:61-66` |
| 이슈 템플릿 편집 | 링크(새 탭) | `vcs.onlineEditing` | `/postform?issueTemplate=true` | `:69-76` |
| 코드 열람을 구성원으로 제한 | 라디오 `true`/`false` | 항상 | | `:80-81` |
| 오래된 승인 해제 | 라디오 `true`/`false` | `vcs.pullRequests` | 새 커밋이 오면 승인을 해제할지(추가 항목 X-12). 기본은 켬(`!= false`). 패널은 코드 메뉴가 꺼져 있으면 `display:none` | `:87-99` |
| 병합 방식 허용 | 체크박스 셋 + 숨은 `false` | `vcs.pullRequests` | 체크하지 않으면 뒤따르는 숨은 `false`가 간다(서버는 첫 값을 읽으므로 체크하면 `true`가 앞섬) | `:105-113` |
| 기본 병합 방식 | 라디오 `merge`/`squash`/`rebase` | `vcs.pullRequests` | | `:116-120` |
| 기본 브랜치 `#project-default-branch` | select2 | `vcs.pullRequests` 그리고 `vcs.defaultBranch`(`DEFAULT_BRANCH` 능력, Git과 Mercurial. Mercurial은 2026-11-11부터 지원하며 선택지는 북마크이고 저장하면 그 이름을 기본으로 기억한다). 지원하지 않는 종류(Subversion)는 패널 `#defaultBranceSettingPanel` 전체가 그려지지 않는다. 패널은 코드 메뉴가 꺼지면 숨김 | 이름은 `defaultBranch`, 값은 `refs/heads/...` 전체 이름 | `:125-137` |
| 메뉴 설정 체크박스 일곱 | 체크박스 | PR 체크는 `vcs.pullRequests`일 때만 | 코드 끄면 코드, PR, 리뷰 체크를 끄고 `#reviewerCountSettingPanel`, `#defaultBranceSettingPanel`, 하위 탭 `#subMenuProjectChangeVCS`를 숨김. PR 켜면 코드도 켜고 승인 해제 패널을 보임, PR 끄면 그 패널을 숨김. 리뷰 켜면 코드도 켬 | `:140-172`, `project.Setting.js:104-136` |
| 저장 `#save` | 제출 | 항상 | 클릭 처리기가 이름 형식(`/^[0-9A-Za-z-_\.가-힣]+$/`)과 예약어(`.`, `..`, `.git`)를 검사해 `$yona.showAlert(project.name.alert / project.name.reserved.alert)` 후 중단 | `:176`, `project.Setting.js:90-102`. 이 처리기는 `.wiki` 접미사와 대문자 예약어를 알지 못해 서버가 400으로 거절한다(project-F13) |

### 2.4 서버 검증과 응답
`ProjectSettingService.validate`(`domain/project/ProjectSettingService.kt:87-124`): 이름 규칙(필수, 형식, 예약어, `.wiki` 접미사), 공개 범위 값, 불리언 값, 병합 방식(모르는 값 거절, 허용 방식이 하나 이상이고 기본 방식이 그 안이어야 함), 같은 소유자에 같은 이름(자기 제외), 로고(확장자 `jpg, jpeg, png, gif, bmp`, 크기 5,120,000바이트 `LOGO_FILE_LIMIT_SIZE`)을 본다. 실패하면 **400**으로 설정 화면을 **저장된 값으로** 다시 그리고(입력값이 아니라 DB 값) 경고 메시지(`project.name.duplicate`, `project.logo.alert`, `project.logo.fileSizeAlert`, `project.name.alert`, `project.name.reserved.alert`)를 일회성으로 보인다. 성공하면 **303 `/{u}/{p}/settingform`**(성공 안내 메시지 없음). 이름을 바꾸면 24시간이 지났을 때 이전 위치를 기록하고 저장소 디렉터리를 옮긴다(옮기지 못하면 예외, 트랜잭션 취소). 메뉴 설정은 체크된 것만 켜고 나머지는 끈다.

### 2.5 권한, 시험
UPDATE. 시험: `ProjectSettingTest`, `ProjectSettingScriptTest`.

## 3. 구성원 (`project/members.html`)

### 3.1 개요, 레이아웃
`GET /members` (`ProjectMemberController.kt:53`). 이 화면을 열 때 이미 구성원인 사용자의 남은 가입 신청을 정리하고 승인 알림을 낸다(`cleanEnrolledUsers`). 모델: `memberItems`(사이트 관리자 제외, 이름 오름차순, 각 항목 `userId, loginId, displayName, avatarUrl, guest, owner, roleName`), `roles`(`MANAGER`, `MEMBER` 두 개, id 순), `enrolled`. 위: 추가 폼(`inner-bubble`), 가운데: 구성원 카드 목록(`col-lg-6`), 아래: 가입 신청 목록(있을 때만).

### 3.2 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 근거 |
|---|---|---|---|---|---|---|
| 로그인 ID 입력 `#loginId` | 입력 | 항상 | HTML 패턴 `^[a-zA-Z0-9가-힣-]+([_.][a-zA-Z0-9가-힣-]+)*$`(한글 허용, 구분 기호 `_`, `.`는 연달아 쓸 수 없음), 필수. 타입어헤드(`yona.ui.Typeahead`)가 1글자부터 `GET /users?query=`로 최대 10명을 제안하고 선택하면 로그인 ID를 채움 | `GET /users` | JSON `[{info, loginId}]`(요청 주소는 `sActionURL` 옵션 `@{/users}`로 컨텍스트 경로를 가짐). `Referer`에서 `#`와 `?` 이하를 뗀 경로가 `members`로 끝나지 않거나 `Accept`가 JSON 계열(`application/json`, `*/*`, `application/*`)이 아니면 406(`Accept`가 없으면 통과). 빈 질의는 빈 배열. `info`는 아바타 `img`와 이름, `@로그인ID`를 담은 HTML 조각이고 이름과 아이디는 HTML 이스케이프되어 들어간다 | `members.html:20-23`, `project.Member.js:43-84`, `user/UserApiController.kt:31-49` |
| 추가 단추 | 폼 제출 | 항상 | 제출을 가로채 XHR로 보냄(`dataType json`) | `POST /{u}/{p}/members` `loginId` | 성공이면 `location.reload()`. 실패(400 JSON `{"loginId":[메시지]}`: `project.member.notExist`, `project.member.alreadyMember`)면 토스트 3초 | `project.Member.js:148-168`, `ProjectMemberController.kt:81-110` |
| 구성원 이름, 아바타 | 링크 | 항상 | `/{loginId}`. 아바타 `src`는 `@{${member.avatarUrl}}`로 컨텍스트 경로를 가짐 | GET | | `members.html:30-31` |
| `GUEST` 표시 | 표시 | `user.isGuest` 계정 | | | | `:34` |
| 역할 드롭다운 (`.btn-group[data-name=roleof-<loginId>]`) | 드롭다운 | 소유자가 아닌 구성원 | 항목을 고르면 레이아웃의 `yona.ui.Dropdown`(`common/scripts.html:77-83`이 `.btn-group[data-name]`마다 인스턴스를 만듦)이 라벨과 숨은 `input[name=roleof-<loginId>]` 값을 바꾸고, 이어서 `project.Member`의 `[data-action=apply]` 처리기가 역할 번호를 읽어 전송 | `POST /{u}/{p}/member/{userId}/edit` 폼 `id=<역할 번호>` | 204(화면 변화는 드롭다운 라벨뿐, 다시 그리지 않음). 소유자 대상이면 400 `project.member.ownerMustBeAManager`(오류 페이지), 번호가 없거나 숫자 아님은 400 | `members.html:36-45`, `project.Member.js:236-250`, `ProjectMemberController.kt:112-130`. 역할 항목의 `data-href`와 삭제 링크의 `data-href`는 `@{|${projectBase}/member/${member.userId}/edit|}`, `.../delete|}`로 컨텍스트 경로를 가진다(`:41,43`) |
| 삭제 | 링크 | 소유자가 아닌 구성원 | `$yona.ajaxConfirm(project.member.deleteConfirm)`(아니오/예) 후 `DELETE /{u}/{p}/member/{userId}/delete` | | 200 JSON `{location}`(컨텍스트 경로가 붙음, `UrlPaths.withContext`)으로 `replace`(본인이면 홈 또는 읽기 불가면 `/`, 남이면 구성원 화면). 오류: 403이면 본문에 `project.member.ownerCannotLeave` 문구가 있으면 그 문구, 아니면 `error.forbidden`, 404 `project.is.empty`, 그 밖 `error.badrequest`(`$yona.alert`) | `:43-45`, `project.Member.js:186-232` |
| 소유자 배지 | 표시 | 소유자 | `user.role.owner` | | | `:47` |
| 가입 신청 수락 `.enrollAcceptBtn` | 단추 | 신청이 있을 때 | `#loginId`에 그 로그인 ID를 넣고 추가 폼을 제출 | 위 추가와 같음 | 추가되면 신청이 정리됨 | `:67`, `project.Member.js:176-181`. 신청자 아바타 `src`는 `@{}`로 컨텍스트 경로를 가진다(`:61`) |

### 3.3 스크립트, 키, 팝업
모듈 `project.Member`(옵션 `sActionURL: "/users"`), 열 때 입력란에 초점. 팝업: 삭제 확인(`$yona.ajaxConfirm`). 화면 범위 단축키 없음. 추가에서 서버는 사용자가 있고 이미 `project_user` 행이 있으면(역할 무관, 사이트 관리자 행 포함) `alreadyMember`, 없으면 `MEMBER` 역할로 만들고 신청을 정리하고 승인 이벤트와 구독을 만든다. JSON을 원하지 않는 요청(예: Accept가 JSON이 아님)은 실패 시 303 구성원 화면과 경고(`project.member.notExist` 등), 성공 시 303이다.

### 3.4 권한
목록, 추가, 역할 변경은 UPDATE. 삭제는 `@DefaultProjectCheck`(읽기) 후 코드 안에서 `본인이거나 UPDATE`(아니면 403 `error.forbidden`)와 소유자 금지(403 `project.member.ownerCannotLeave`)를 본다.

### 3.5 시험
`ProjectMemberTest`, `EnrollTest`.

## 4. 프로젝트 이전 (`project/transfer.html`)

### 4.1 개요
`GET /transfer`(`@IsAllowed(DELETE)`). 화면: 새 소유자 입력 `#owner`(검증 없음), 안내 다섯 줄(`project.transfer.description1~5`), 동의 체크 `#accept`, 이전 단추 `#btnTransfer`(`href="#alertTransfer" data-bs-toggle="modal"`), 확인 모달 `#alertTransfer`(예 `#btnTransferExec`, 아니오).

### 4.2 동작
- `#btnTransfer` 클릭: 동의를 체크하지 않았으면 `$yona.alert(project.transfer.alert)`하고 `false`를 돌려줘 모달이 열리지 않는다(`project.Transfer.js:79-84`).
- "예": 요청 중에는 겹쳐 보내지 않는(`bSending` 플래그) 처리기가 `PUT {base}/transfer?owner=<입력값>`을 보낸다(`:51-77`). 응답이 204이고 `Location`이 있으면 그리로 이동, 아니면 새로고침. 실패하면 플래그를 풀고 모달을 닫고 `$yona.alert(project.transfer.error)`한다(같은 화면에서 다시 "예"를 눌러 다시 보낼 수 있다, project-F19).
- 서버(`ProjectTransferController.transfer`, `:49-75`): 사용자 또는 조직이 아닌 이름이면 400 오류 페이지, 현재 소유자와 같으면 200으로 폼을 다시 그리고 안내 `project.transfer.has.same.owner`(info). 요청이 만들어지면 Ajax일 때 204 + `Location: /{owner}/{name}`와 다음 화면용 안내 `project.transfer.is.requested`. 받는 사람에게 이벤트(알림)로 수락 링크가 간다.
- 수락: `GET /project/transfer/{id}/{key}` (로그인 필수). 요청이 하루 안이고 아직 수락되지 않아야 하며(없으면 404), 열쇠가 다르면 400, 받는 쪽(대상 사용자 본인 또는 대상 조직의 관리자, 사이트 관리자)이 아니면 403, 성공하면 소유자, 이름, 조직을 바꾸고 저장소를 옮기고 303 새 프로젝트 홈(`ProjectTransferService.accept`, `AccessControl.kt:178-187`).

### 4.3 권한
화면과 요청은 DELETE 권한(프로젝트 관리자, 그룹 관리자, 사이트 관리자). 시험: `ProjectTransferTest`.

## 5. 프로젝트 삭제 (`project/delete.html`)

화면: 경고 문구 `project.delete.description`, 동의 체크 `#accept`, 삭제 단추 `#btnDelete`(모달 `#alertDeletion`), 모달 "예" `#btnDeleteExec`. 스크립트 `project.Delete`(`project.Delete.js`, 옵션 `sDeleteURL`은 `[[@{...}]]`로 컨텍스트 경로를 가짐, `delete.html:55`): `#btnDelete`는 동의가 없으면 `$yona.alert(project.delete.alert)`와 모달 차단. "예": 기본 동작을 막고 `yona.Request.send("delete", sDeleteURL)`(`DELETE /{u}/{p}/delete`). 성공 204 + `Location: /`이면 `Request`가 이동(`esm/Request.js`). `!ok`이면 모달을 닫고 `$yona.alert(project.delete.error)`. 서버(`ProjectDeleteController.delete`, `:50-77`): 프로젝트가 없으면 303 `/`, 있으면 내 즐겨찾기를 먼저 지우고 프로젝트와 저장소, 위키, LFS를 삭제. Ajax이면 204 + `Location: /`, 아니면 303 `/`. 권한은 DELETE. 시험: `ProjectDeleteTest`.

## 6. 저장소 종류 변경 (`project/change-vcs.html`)

`GET /changeVCS`(UPDATE). 모델 `targets`(저장소를 만들 수 있게 등록된 종류에서 지금 종류를 뺀 목록), `texts`(종류별 `description`, `requestion` 문구). 화면: `지금 종류 → 다음 종류`(`project.nextVcs()`), `targets`가 둘 이상일 때만 선택 `#targetVcs`(기본 선택은 번갈아 바꾸는 종류), 안내 두 줄, 동의 체크 `#acceptChangeVCS`, 단추 `#btnChangeVCS`(모달 `#alertChangeVCS`), "예" `#btnChangeVCSExec`. 스크립트 `project.ChangeVCS.js`: 선택을 바꾸면 `.next-vcs`, `.vcs-description`, `.vcs-requestion` 글자를 `oTexts`로 갈아 끼움(`:60-67`). `#btnChangeVCS`는 동의가 없으면 `$yona.alert(project.changeVCS.alert)`와 모달 차단. "예": `POST /changeVCS`(선택이 있으면 `targetVcs`). 서버(`ProjectVcsController.change`, `:57-74`): `targetVcs`가 목록에 없으면 400(본문 없음), 성공 204 + `Location: /{u}/{p}`(스크립트가 이동), 예외는 본문 없는 500. 실패하면 모달을 닫고 `$yona.alert(project.changeVCS.error)`. README 게시글 표시를 지우고 포크의 종류도 바꾼다(`ProjectVcsService.change`). 시험: `ProjectChangeVcsTest`.

## 7. 웹훅 (`project/webhooks.html`)

### 7.1 개요
`GET /webhooks`(UPDATE). 모델: `creatable`(웹훅 만들기 권한), `action`(`{base}/webhooks`), `webhooks`(행: `id, payloadUrl, secret, type, gitPush, deleteUrl`). 폼은 `creatable`일 때만 그려진다. 목록은 머리줄과 행(주소, 토큰 평문, 형식, git push 포함 여부 체크박스(읽기 전용), 삭제 단추). 비면 `project.webhook.list.empty`.

### 7.2 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 근거 |
|---|---|---|---|---|---|---|
| 주소 `payloadUrl` | 입력(2000자) | `creatable` | 제출 때 비어 있으면 `$yona.alert(project.webhook.payloadUrl.empty)` 후 중단 | | | `webhooks.html:22`, `project.Webhook.js:53-59` |
| 토큰 `secret` | 입력(250자) | | 선택 | | | `:23` |
| 형식 라디오 `webhookType` | 라디오 넷 | | `SIMPLE`(기본), `DETAIL_SLACK`, `DETAIL_HANGOUT_CHAT`, `JSON`. 형식을 바꾸면 인라인 스크립트가 `JSON`이면 `gitPush` 체크를 `prop('checked', true)`로 켜고 클릭 불가(`attr('onclick', 'return false;')`), 그 밖이면 `prop('checked', false)`로 해제하고 클릭 가능(`attr('onclick', '')`) | | `:27-38`, `:81-91` |
| `gitPush` 체크 | 체크박스 | | | | | `:40-42` |
| 추가 단추 | 제출 | | `POST /{u}/{p}/webhooks` 폼(`payloadUrl, secret, webhookType, gitPush`) | | 성공 303 목록. 서버 검증 실패(주소 없음, 주소 2000자 초과, 토큰 250자 초과, 모르는 형식)는 400 오류 페이지에 오류 JSON 문자열이 문구로 들어감 | `WebhookController.kt:65-83` |
| 삭제 | 단추 | 행마다 | `data-request-method="delete"`, `data-request-uri={base}/webhooks/{id}` → `yona.Request`가 `fetch DELETE` | | 200이면 새로고침. 없으면 404이고 `Request.js`가 `$yona.alert(error.notfound)`를 띄움(403은 `error.forbidden`, 그 밖 `error.internalServerError`) | `:66`, `WebhookController.kt:85-90` |

`gitPush` 체크는 켜면 값 `on`이 오고 끄면 오지 않는다(서버는 `true`, `on`, `yes`, `1`을 참으로 봄). 서버는 `JSON` 형식일 때 `gitPush`를 강제하지 않는다(project-F20).

### 7.3 권한
목록, 추가, 삭제 모두 UPDATE. 폼 표시는 `isProjectResourceCreatable(WEBHOOK)`(멤버 이상)이므로 UPDATE를 통과한 사용자에게는 사실상 항상 보인다. 시험: `WebhookSettingsTest`.

## 8. 이슈 라벨 (`project/issue-labels.html`, `partial-issue-labels-list.html`)

### 8.1 개요
`GET /issue/labelsform`(`IssueLabelController.kt:88`, UPDATE). 모델: `canCreate`(`isProjectResourceCreatable(ISSUE_LABEL)`), `groups`(분류별 `category{id,name,exclusive}`, `canUpdate`, `labels[{id,name,color,canDelete,canUpdate}]`), `editableCategories`. 같은 목록 조각(`partial-issue-labels-list`, 조각 `labelList`와 `page`)은 `GET /issue/labels`가 `HX-Request: true`일 때도 그려진다(`:64-78`, 캐시 금지 헤더). 화면이 그 요청을 보내는 곳은 이 영역에 없다(project-F17).

### 8.2 레이아웃
`label-editor-wrap` 안에: 라벨 복사 폼(`#copyLabel`), 새 라벨 폼(`#frmNewLabel`), 목록 `#labelsList`(머리줄 분류/이름, 분류 행 `div.category-wrap[data-category, data-category-name]`: 왼쪽 분류 이름과 단일/다중 아이콘(툴팁)과 `분류 수정` 단추, 오른쪽 라벨 표). 모달 둘 `#editCategory`, `#editLabel`. 폼 둘은 `canCreate`일 때만.

### 8.3 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 근거 |
|---|---|---|---|---|---|---|
| 라벨 복사 (`owner`, `projectName`) | 폼 | `canCreate` | 일반 제출 | `POST /{u}/{p}/copyLabels` | 항상 303 `/issue/labelsform`(대상이 없거나 읽을 수 없으면 아무것도 복사하지 않음) | `issue-labels.html:19-28`, `IssueLabelController.kt:193-207`. 폼 `action`은 `@{${base + '/copyLabels'}}`로 컨텍스트 경로를 가지고, 이슈 메뉴를 끈 프로젝트에서는 이 주소가 접근 검사에서 404이다(`ProjectCheckStage.checkMenuEnabled`의 `copyLabels`) |
| 분류 입력 `category` | 입력(250자) | `canCreate` | 타입어헤드로 기존 분류 제안. Enter는 막힘 | | | `:33`, `issue.LabelEditor.js` `_initVar`, `_preventSubmitWhenEnterKeyPressed` |
| 이름 `name` | 입력(250자) | | 초점을 받으면 색 프리셋을 보이고 비어 있으면 색 입력란에 기존 분류의 첫 색 또는 무작위 프리셋 색을 채움 | | | `:34`, `LabelEditor` `_onFocusInputName` |
| 프리셋 색 단추 17개 | 단추 | | 누르면 색 입력란과 이름란 배경이 그 색으로 | | | `:37-53` |
| 색 `color` | 입력 | | 직접 입력. `#`로 시작하면 길이 4 또는 7만, 아니면 색 이름을 `$yona.getHexColor`로 변환. 잘못되면 `$yona.alert(label.error.color)` | | | `:54`, `_isValidColorExpr` |
| 추가 `label.add` | 제출 | | 가로채서 검증: 셋 다 필수(`label.error.empty`), 색 유효성. **새 분류**이면 `$yona.confirm(label.category.new.confirm)`에서 "다중/단일 선택" 중 하나를 고르게 하고 다시 제출. 같은 분류에 같은 이름이 이미 화면에 있으면 `label.error.duplicated` | `POST /{u}/{p}/issue/labels` 폼 `labelName, labelColor, categoryName, categoryIsExclusive`(템플릿 입력 이름 `category/name/color`와 다름, 스크립트가 바꿔 보냄) | 201(+JSON `{id,name,color,category,categoryId}`)이면 새로고침(`reload(true)`). 204(같은 분류에 같은 이름 있음)는 `label.error.creationFailed` 경고. 400 JSON 오류 `{항목:[메시지]}`는 줄 바꿔 `$yona.alert` | `:57`, `LabelEditor` `_onSubmitForm`, `_requestAddLabel`, `IssueLabelController.kt:108-132`. 폼 `action`은 `@{${base + '/issue/labels'}}`(`issue-labels.html:29`) |
| 분류 수정 | 단추 | `group.canUpdate` | 모달 `#editCategory`(이름, 단일/다중 선택) 열기 | | | `partial-issue-labels-list.html:38-41`, `LabelEditor` `_onClickBtnEditCategory` |
| 분류 모달 저장 | 단추 | | `PUT {category-update-uri}` `id, name, isExclusive, project.id` | `PUT /{u}/{p}/issue/label/category/{id}` | 200이면 새로고침. 같은 이름의 다른 분류가 있으면 400. 실패는 `label.failedTo` 경고. 모달은 항상 닫음 | `LabelEditor` `_onClickBtnSubmitEditCategory`, `IssueLabelController.kt:234-249` |
| 라벨 삭제 | 단추 | `label.canDelete` | `$yona.confirm(label.confirm.delete)`의 "예"면 `POST {delete-uri}` 폼 `_method=delete` | `POST /{u}/{p}/issue/label/{id}/delete` | 200이면 그 행을 지우고 분류에 라벨이 없으면 분류 행도 지움. 실패하면 `_showError`가 `error.failedTo`(또는 응답 JSON이 있으면 `label.failedTo`와 오류 문구) 알림을 띄우고 행은 그대로 둠. 경로의 프로젝트에 속한 라벨이 아니면 404 | `partial-issue-labels-list.html:52-54`, `LabelEditor` `_requestRemoveLabel`(`:522-536`), `IssueLabelController.kt:135-145` |
| 라벨 수정 | 단추 | `label.canUpdate` | 모달 `#editLabel`(분류 선택, 이름, 색 프리셋 12개, 색 입력) 열기 | | | `:55-58`, `issue-labels.html:90-121` |
| 라벨 모달 저장 | 단추 | | 같은 분류에 같은 이름이 이미 있으면 팝오버 `label.error.duplicated.in.category`, 색이 잘못이면 팝오버 `label.error.color`, 통과하면 `PUT {update-uri}` `name, color, category.id` | `PUT /{u}/{p}/issue/label/{id}` | 200이면 새로고침. 400 JSON(`category` `error.required` 또는 `error.invalid`(다른 프로젝트의 분류 포함), `name`, `color`) | `LabelEditor` `_onClickBtnSubmitEditLabel`, `_updateLabel`, `IssueLabelController.kt:148-175`. 경로의 프로젝트에 속하지 않은 라벨은 404 |
| `labels.css` 링크 | 스타일 | 항상 | 라벨 색 스타일시트(ETag 304) | `GET /issue/labels.css` | 읽기 권한 | `partial-issue-labels-list.html:65`(`href`는 `@{}`로 컨텍스트 경로를 가짐), `IssueLabelController.kt:176-190` |

비면(`groups.isEmpty()`) 안내 `label.list.empty`(`ico-err1`). 모달 닫기는 X 단추, 취소 단추, 배경, Esc. 모달 `#editCategory`, `#editLabel`의 select2(`data-toggle="select2"`)는 `common/select2` 조각이 초기화한다.

### 8.4 권한
화면은 프로젝트 UPDATE(관리자). 서버 요청은 더 넓다: 라벨 만들기와 복사는 `@IsCreatable(ISSUE_LABEL)`(멤버 이상, 그룹 구성원 포함), 라벨 수정, 삭제는 라벨 리소스에 대한 UPDATE, DELETE(멤버 이상이고, 라벨이 경로의 프로젝트에 속하지 않으면 404, project-F24), 분류 수정은 UPDATE, 분류 삭제(`DELETE .../label/category/{id}`)와 분류 만들기(`POST .../label/categories`), 분류 조회 JSON은 서버에는 있으나 이 화면이 부르지 않는다(project-F17). 화면에서는 `canUpdate`, `canDelete`로 단추를 감추지만 화면 자체를 관리자만 열 수 있어 결국 관리자만 쓴다. 시험: `IssueLabelTest`, `LabelApiTest`.

## 9. 배포 키 (`deploykeys.html`, `deploykey-new.html`)

### 9.1 목록
`GET /setting/deploykeys`(UPDATE, `@RequiresVcs(DEPLOY_KEYS)`: 지원하지 않는 종류는 400). 모델 `keys`(이름, 지문, 읽기 전용 여부, 만든 시각). 안내 `deployKey.description`, 표(이름, 지문, 권한 `읽기 전용`/`쓰기 허용`(경고색 배지), 등록일, 삭제), 비면 `deployKey.list.empty`, 아래 "추가" 링크(`#deploy-key-new`).

| 요소 | 동작 | 요청 | 응답 | 근거 |
|---|---|---|---|---|
| 삭제 단추 | 폼 제출 전에 브라우저 `confirm(deployKey.delete.confirm)`. 폼 `action`은 `@{}`로 컨텍스트 경로를 가짐 | `POST /{u}/{p}/setting/deploykeys/{id}/delete` | 303 목록. 이 저장소의 키가 아니거나 없으면 404 | `deploykeys.html:32-34`, `DeployKeyController.kt:78-86` |
| 추가 링크 | 추가 화면으로 | GET | | `:41` |

### 9.2 추가
`GET /setting/deploykeys/new`. 폼 `POST /{u}/{p}/setting/deploykeys`: `name`(100자, 비우면 키 끝 주석), `publicKey`(8줄 텍스트상자), `writable`(체크, 값 `true`, 기본 읽기 전용). 취소 링크는 목록으로. 폼 `action`과 취소 링크는 `@{${base + '/setting/deploykeys'}}`로 컨텍스트 경로를 가진다. 서버 거절(`DeployKeyAddition.Rejected`)은 **400**으로 추가 화면을 다시 그리고 입력을 되돌려 주며 위쪽 `alert-danger`에 오류 키 문구를 보인다: `deployKey.error.empty`, `malformed`, `private_key`, `multiple_keys`, `unsupported`, `weak`(RSA 2048비트 미만), `duplicate`(다른 배포 키나 사용자 SSH 키와 지문이 같음), `limit`(프로젝트당 50개), `name`(100자 초과). 성공은 303 목록. 이 화면에는 스크립트가 없고 클라이언트 검증도 없다. 시험: `DeployKeySettingsTest`.

## 10. LFS (`lfs.html`, `lfs-hg.html`)

`GET /setting/lfs`(UPDATE, `@RequiresVcs(LFS_SCREEN)`). 같은 주소가 종류에 따라 다른 템플릿이다: Git이면 `lfs.html`, Mercurial이면 `lfs-hg.html`(`ProjectLfsController.kt:57-76`).

**Git(`lfs.html`)**: 설명, 비활성 경고(`enabled`가 거짓이면 `lfs.disabled`), 사용량(`lfs.usage(개수, 용량)`, 프로젝트 한도가 있으면 `lfs.usage.quota`, 객체 최대 크기 `lfs.usage.maxObject`), 잠금 목록(경로, 소유자, 잠근 시각, "해제" 단추). 비면 `lfs.locks.empty`. 해제 단추: 브라우저 `confirm(lfs.locks.release.confirm)` 후(폼 `action`은 `@{}`로 컨텍스트 경로를 가짐) `POST /{u}/{p}/setting/lfs/locks/{id}/delete`(`@RequiresVcs(LFS)`) → 강제로 풀고 303 같은 화면과 성공 안내 `lfs.lock.released`. 이 프로젝트의 잠금이 아니면 404, 실행 사용자가 DB에 없으면 403. 용량 표기는 1024 단위 `B/KB/MB/GB/TB`, 소수 한 자리(`readable`, `:79-85`).

**Mercurial(`lfs-hg.html`)**: 설명, 사용량(개수, 용량, "용량 제한과 잠금이 없다" 안내), 기본 위치 머리의 LFS 파일 표(경로, 크기, 객체 id 앞 12자와 전체는 툴팁), 비면 `lfs.hg.files.empty`, 클라이언트 설정 안내(`[extensions] lfs =`, `[lfs] track = size(">10MB")` 예시와 주소 안내). 동작 요소 없음(읽기 전용).

스크립트 없음. 권한은 관리자만.

## 11. 규칙 묶음 (`rulesets.html`, `ruleset-form.html`)

### 11.1 목록
`GET /setting/rulesets`(UPDATE, `@RequiresVcs(PROTECTED_REFS)`: Git, Mercurial). 모델: `rulesets`(이름순), `migrated`(이름이 `ReviewerSettingMigration.MIGRATED_NAME`으로 시작하는 묶음이 있는지). "새 규칙 묶음" 단추(`#newRuleset`), 설명, 이전 안내(`#rulesetMigrated`, `migrated`일 때 `project.ruleset.legacy`), 표(이름 링크 → `/setting/rulesets/{id}`, 대상 패턴 `code`, 적용 상태 배지(활성은 초록), 규칙 배지: 승인 수, 풀 리퀘스트 필요, 변경 요청 해결, 대화 해결, 선형 이력, 서명 커밋, 갱신 제한(경고색), 삭제 제한, 강제 푸시 차단). 비면 `project.ruleset.empty`(4열 합침).

### 11.2 편집
`GET /setting/rulesets/new`, `GET /setting/rulesets/{id}`(이 프로젝트의 것이 아니면 404). 폼 `POST /setting/rulesets`(만들기) 또는 `POST /setting/rulesets/{id}`(고치기). 필드: `name`(255자), `enforcement`(`active`/`disabled` 라디오, 새 묶음 기본 활성), `bypassAdmins`(체크), `pattern`(255자), 규칙 체크박스 `restrictUpdates`, `restrictDeletions`, `requireLinearHistory`, `requireSignedCommits`, `requirePullRequest`, 하위(풀 리퀘스트 필요 아래의 카드) `approvals`(0~100 숫자), `requireNoChangesRequested`, `requireConversationResolution`, 그리고 `blockForcePushes`. 하위 카드(`#rulesetPullRequestOptions`)는 풀 리퀘스트 필요가 켜져 있거나 승인 수, 변경 요청, 대화 해결 중 하나라도 켜져 있으면 처음부터 펼쳐진다. 인라인 스크립트(`ruleset-form.html:126-141`): 풀 리퀘스트 필요를 켜면 펼치고, 끌 때는 하위 값이 모두 기본(승인 0, 체크 없음)일 때만 접는다. 단추: 저장 `#saveRuleset`, 취소(목록 링크), 삭제 `#rulesetDeleteForm`(편집일 때만, 확인창 없이 `POST .../{id}/delete`).

서버 검증(`RulesetService.violations`, `:39-58`): 이름은 공백 제거 후 1~255자, 제어 문자 없음, 같은 프로젝트에서 유일(자기 제외), `enforcement`는 `active`/`disabled`, 대상 패턴은 공백 없는 1~255자이고 `refs/`로 시작하지 않음, 승인 수는 0~`MAX_APPROVALS`(비우면 0). 실패하면 **400**으로 편집 화면을 입력값과 함께 다시 그리고 위쪽 `alert-danger`(`project.ruleset.error`)와 항목 옆 문구(`project.ruleset.error.name`, `.pattern`, 승인 수는 `project.ruleset.error`)를 보인다. 성공은 303 목록. 삭제는 303 목록, 이 프로젝트의 것이 아니면 404. 체크하지 않은 규칙은 값이 안 오므로 꺼짐으로 본다(`bind`, `:106-115`). 시험: `RulesetWebTest`, `RulesetFormWidthTest`.

## 12. 권한 행렬 (설정 계열)

| 기능 | 비로그인 | 로그인 비멤버 | 멤버 | 프로젝트 관리자 | 그룹 관리자 | 사이트 관리자 |
|---|---|---|---|---|---|---|
| 톱니 메뉴 보임 | 숨김 | 숨김 | 숨김 | 보임 | 보임 | 보임 |
| 설정, 구성원, 웹훅, 라벨 화면, 배포 키, LFS, 규칙 묶음, 저장소 종류 변경 | 403 로그인 화면 | 403 | 403 | 허용 | 허용 | 허용 |
| 이전, 삭제 화면과 요청 | 403 | 403 | 403 | 허용 | 허용 | 허용 |
| 이전 수락 | 303 로그인 | 받는 사용자 본인이면 허용 | 같음 | 받는 쪽일 때만 | 받는 조직의 관리자면 허용 | 허용 |
| 구성원 삭제(자기 탈퇴) | 303 로그인 | 읽을 수 있으면 요청은 통과하나 변화 없음 | 허용(소유자 제외) | 허용 | 해당 없음 | 허용 |
| 라벨 만들기, 수정, 삭제 요청(API 직접) | 403 | 403(공개 프로젝트도) | 허용 | 허용 | 허용 | 허용 |
| 라벨 복사 요청 | 403 | 403 | 허용 | 허용 | 허용 | 허용 |

## 13. 발견 사항 (project-F16 ~ F24)

project.md의 project-F1~F15에 이어 번호를 붙인다.

| 번호 | 내용 | 확인 방법 | 비고 |
|---|---|---|---|
| project-F16 **[검증: v2 추가 기능의 결함, 수정됨 A-460]** | 설정 화면은 코드 메뉴를 끄면 승인 해제, 기본 브랜치 패널과 저장소 종류 변경 탭을 숨기지만, 서버는 그 필드(`defaultBranch` 등)를 그대로 받는다. Mercurial은 `vcs.pullRequests`가 참이라 기본 브랜치 선택이 그려지는데 `DEFAULT_BRANCH` 능력이 없어 `branches`가 빈 목록이라 선택지가 비어 있었다 | `setting.html:86,125-137`, `ProjectSettingController.kt:97-98`, `VcsType.kt:32-39` | 코드 대조 (2026-11-11 수정: 기본 브랜치 패널에 `th:if="${vcs.defaultBranch}"`를 더해 지원하지 않는 종류에서는 패널을 그리지 않는다, `setting.html:126`. 같은 날 Mercurial이 `DEFAULT_BRANCH`를 지원하게 되어 이제 Mercurial은 북마크 선택지가 있는 패널을 그린다, code-F7) |
| project-F17 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 서버에는 있으나 이 화면이 부르지 않는 경로: `GET /issue/labels`의 `HX-Request` 조각 응답(스크립트는 `reload(true)`로 갱신하고 `listURL` 옵션은 저장만 함), `GET /issue/label/categories`, `GET /issue/label/category/{id}`, `POST /issue/label/categories`, `DELETE /issue/label/category/{id}`(분류 삭제 단추가 없음) | grep, `LabelEditor` `_initVar` | |
| project-F18 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 라벨 삭제 실패는 화면에 아무 표시가 없다(`catch`에서 `return`) | `LabelEditor` `_requestRemoveLabel` | (2026-11-11 수정: 실패하면 `_showError(failure, "button.delete")`로 알림, `issue.LabelEditor.js:529-533`) |
| project-F19 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 이전 화면의 "예" 처리기가 `.one("click")`이라 요청이 실패해 모달이 닫힌 뒤에는 같은 화면에서 다시 눌러도 요청이 나가지 않는다(새로고침 필요) | `project.Transfer.js:51` | (2026-11-11 수정: `.on("click")`과 요청 중 플래그 `bSending`으로 바꿔 실패하면 다시 보낼 수 있다, `project.Transfer.js:51-77`) |
| project-F20 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 웹훅 형식을 `JSON`으로 고르면 스크립트가 `gitPush` 체크를 켜고 고정하지만, 서버는 값을 강제하지 않아 직접 보낸 요청은 `JSON`이면서 `gitPush=false`로 저장된다. 스크립트는 `attr('checked')`로 켜므로 사용자가 이미 그 체크를 눌러 본 뒤에는 상태가 바뀌지 않을 수 있다(추측) | `webhooks.html:81-91`, `WebhookService.create` | (2026-11-11 수정: 스크립트가 `prop('checked', ...)`로 바꿨다, `webhooks.html:84,89`. 서버가 `JSON`일 때 `gitPush`를 강제하지 않는 점은 그대로이다) |
| project-F21 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 웹훅 목록이 토큰(`secret`)을 평문으로 그린다(비면 `NONE`). 목록은 UPDATE 사용자에게만 보인다 | `webhooks.html:62` | v1.6 동일 추정 |
| project-F22 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 구성원 타입어헤드 API `/users`는 `Referer`가 `members`로 끝나야 하고(쿼리 문자열이 붙으면 거절) `info` HTML에 사용자 이름을 이스케이프 없이 넣는다. 구성원 화면의 렌더러는 이 HTML을 그대로 그린다 | `UserApiController.kt:31-49`, `project.Member.js:61-63` | 코드 확인 (2026-11-11 수정: `Referer`는 `#`와 `?` 이하를 뗀 경로로 판정하고 이름과 아이디를 `HtmlUtils.htmlEscape`로 이스케이프한다, `UserApiController.kt:33-47`. 렌더러는 서버 HTML을 그대로 그린다) |
| project-F23 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 설정 저장 성공 시 성공 안내가 없고 같은 화면으로 303 이동한다. 실패 시 400 화면은 입력값이 아니라 저장된 값으로 다시 그려진다 | `ProjectSettingController.kt:69-75` | |
| project-F24 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 마일스톤, 라벨은 번호만으로 리소스를 찾아 경로의 프로젝트와 달라도 권한 검사를 통과한다(R-12). 라벨 수정은 분류의 프로젝트가 같은지 추가로 본다(A-24) | `ProjectResourceLocator.kt:46-62`, `IssueLabelController.kt:141-142,168`, `MilestoneController.kt` `inProject` | 알려진 v1.6 결함 (2026-11-11 수정: 라벨 삭제와 수정은 경로의 프로젝트에 속하지 않은 라벨이면 404이다. 마일스톤은 [milestone.md](milestone.md) 참조) |

## 14. 범위 점검 (이 파일이 다룬 템플릿)

| 템플릿 | 다룬 절 |
|---|---|
| `project/partial-settingmenu.html` | 1 |
| `project/setting.html` | 2 |
| `project/members.html` | 3 |
| `project/transfer.html` | 4 |
| `project/delete.html` | 5 |
| `project/change-vcs.html` | 6 |
| `project/webhooks.html` | 7 |
| `project/issue-labels.html` | 8 |
| `project/partial-issue-labels-list.html` | 8 |
| `project/deploykeys.html` | 9.1 |
| `project/deploykey-new.html` | 9.2 |
| `project/lfs.html` | 10 |
| `project/lfs-hg.html` | 10 |
| `project/rulesets.html` | 11.1 |
| `project/ruleset-form.html` | 11.2 |

## 갱신 기록 (2026-11-11 재조사)

- 다시 확인한 템플릿 15개: `partial-settingmenu.html`, `setting.html`, `members.html`, `transfer.html`, `delete.html`, `change-vcs.html`, `webhooks.html`, `issue-labels.html`, `partial-issue-labels-list.html`, `deploykeys.html`, `deploykey-new.html`, `lfs.html`, `lfs-hg.html`, `rulesets.html`, `ruleset-form.html`. 스크립트 `project.Setting.js`, `project.Member.js`, `project.Transfer.js`, `project.Webhook.js`, `issue.LabelEditor.js`, `Request.js`, 컨트롤러 `IssueLabelController`, `ProjectMemberController`, `ProjectCheckStage`도 대조했다.
- 내용을 고친 행: 약 25개(설정 탭, 설정 화면의 기본 브랜치 표시 조건과 로고, 구성원 입력 패턴(한글 허용)과 컨텍스트 경로, 구성원 삭제 응답의 `location`, 웹훅 형식 체크의 `prop`과 삭제 실패 알림, 라벨 복사의 메뉴 꺼짐 404, 라벨 삭제 실패 알림과 프로젝트 소속 확인, 배포 키, LFS, 삭제 스크립트 주소, 발견 사항 F16, F18, F19, F20, F22, F24).
- 줄 번호만 고친 곳: 약 12개(`setting.html` 기본 브랜치 아래 +1, `IssueLabelController` 아래 +2에서 +3, `webhooks.html`).
- 더한 행: 없음. 없앤 행: 없음. 새 발견 사항: 없음.
- 확인 사항: 설정 화면의 저장 처리기(`project.Setting.js:90-102`)는 고쳐지지 않아 `.wiki` 접미사를 모른다(project.md의 project-F13 비고에 적음).
