# yona E2E 커버리지 매트릭스

이 표는 `e2e/specs/`의 모든 스펙 파일이 실제 화면 인벤토리(컨트롤러 GET 라우트 기준)를
얼마나 커버하는지 추적한다. `상태` 컬럼: `done`(스펙 존재, 통과 확인) / `todo`(아직 없음) /
`partial`(일부 버튼·입력만 커버).

새 스펙을 추가하면 이 표도 같이 갱신할 것 — 표가 실제 커버리지와 어긋나면 그 자체가 하나의
버그다.

## 0. 부트스트랩 (모든 시나리오의 전제)

| 화면 | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| GET/POST /bootstrap-setup | specs/global.setup.ts | done | 최초 관리자 생성, `.auth/admin.json` 저장 |

## 1. 인증 (Auth)

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| GET /users/loginform | specs/01-auth/auth.spec.ts | done | 정상/오류 로그인, remember-me·lostPassword 링크 |
| GET /signup | specs/01-auth/auth.spec.ts | done | 정상 가입+로그인, 비밀번호 불일치 거부 |
| GET /lostPassword | specs/01-auth/auth.spec.ts | partial | 폼 존재만 확인, 실제 메일 발송 플로우 미검증 |
| GET /user/reset-password | | todo | 유효 토큰 필요(메일 인프라 연동 필요) |
| GET /users/login/2fa | | todo | 2FA 활성 계정 필요 (14번 참고) |
| GET /verify/{loginId}/{code} | | todo | 가입 인증코드 필요 |

## 2. 사용자 프로필/설정 (User)

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| GET /user/{loginId} | specs/02-user/user-settings.spec.ts | done | |
| GET /user/editform (기본정보 수정+저장, 최근방문 초기화) | specs/02-user/user-settings.spec.ts | done | |
| GET /user/editform/emails (추가) | specs/02-user/user-settings.spec.ts | done | 삭제/대표이메일 지정 버튼은 미검증 |
| GET /user/editform/notifications | specs/02-user/user-settings.spec.ts | partial | 화면 로드만 확인, 체크박스 토글 저장까지는 미검증 |
| GET /user/editform/password | specs/02-user/user-settings.spec.ts | done | admin이 아닌 01-auth 시드 두번째 유저로 변경(관리자 세션 보호), 변경 후 재로그인까지 확인 |
| GET /user/editform/token, /tokens, /tokens/new (발급) | specs/02-user/user-settings.spec.ts | done | 발급된 토큰이 목록에 뜨는지 확인 + revoke 버튼 실제 클릭(네이티브 confirm() 다이얼로그 승인 포함) 후 목록에서 사라지는지까지 확인. `ApiTokenServiceImpl.revoke()`는 소프트 삭제가 아니라 실제 row 삭제임을 확인 |
| GET /user/editform/oauth-apps (인가한 앱 목록) | specs/02-user/user-settings.spec.ts | done | **버그 아님, 사양 확인**: 이 목록은 `OAuth2AuthorizationConsent` 레코드 기반이라 `scope=openid` 단독 요청(자동승인, consent 화면 자체를 건너뜀)으로는 절대 채워지지 않는다 — `openid profile`로 실제 consent 화면 도달 후 Authorize 버튼까지 눌러 진짜 동의 레코드를 만든 뒤 목록 등재 확인 + revoke 버튼 클릭 후 사라지는지까지 확인 |
| GET /user/editform/oauth-apps-owned(-new) (등록) | specs/02-user/user-settings.spec.ts | done | client name/redirect URI/scope 체크박스 입력 후 등록 확인 + 삭제 버튼 실제 클릭(confirm() 승인) 후 목록에서 사라지는지까지 확인 |
| GET /user/editform/ssh-keys(-new) | specs/02-user/user-settings.spec.ts | done | 실제 ed25519 공개키로 정상 등록 + 잘못된 형식 거부 둘 다 확인 + 삭제 버튼 실제 클릭(confirm() 승인) 후 목록에서 사라지는지까지 확인 |
| GET /user/editform/gpg-keys(-new) | specs/02-user/user-settings.spec.ts | done | **제품/테스트 갭 발견 및 수정**: GPG 키 등록은 UID 이메일이 계정의 인증된 이메일과 일치해야 성공하는데(`GpgKeyServiceImpl`), 기존 "정상 등록" 테스트는 매번 무작위 미인증 이메일을 써서 실제로는 항상 거부되고 있었고 URL만 보는 약한 단언(re-render와 성공 리다이렉트가 같은 URL로 귀결) 때문에 통과로 오판되고 있었음 — UID를 admin의 실제 인증 이메일(`admin@yona-e2e.test`)로 고치고 `.alert-success` 단언 추가로 진짜 성공을 확인하게 수정. 삭제 버튼도 실제 클릭(confirm() 승인) 후 목록에서 사라지는지까지 확인 |
| GET /user/files | specs/02-user/user-settings.spec.ts | partial | 빈 상태 로드만 확인 |
| GET /user/issues, /user/issues/new/mine | specs/02-user/user-settings.spec.ts | partial | 화면 로드만 확인(500 아님) |
| GET /user/editform/security, /totp/new, /webauthn/new, /backup-codes/show | | todo | 14번(2FA)과 연계 — 이번 fork 범위 밖 |

## 3. 조직 (Organization)

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| GET /orgs | specs/03-organization/organization-crud.spec.ts | done | |
| GET /organizations/new → 생성 | specs/03-organization/organization-crud.spec.ts | done | 이름 공백 거부 포함 |
| GET /organizations/{orgName} | specs/03-organization/organization-crud.spec.ts | done | |
| .../members | specs/03-organization/organization-crud.spec.ts | done | 화면 로드 확인(생성자 자동 ORG_ADMIN 등록) + 실제 멤버 초대(`POST /api/organizations/{orgId}/members`) → 역할 드롭다운으로 org_admin(role.id=6)으로 변경(`PUT .../role`) → 삭제(`DELETE .../members/{userId}`, 네이티브 `<dialog>` confirm 포함)까지 왕복 확인, 끝에 초대분 제거해 부작용 없음. **테스트 인프라 주의점(제품 버그 아님)**: 역할 드롭다운의 `<li>` 옵션들은 이 페이지에서 Bootstrap dropdown이 실제로 열리지 않아(`.dropdown-menu`가 계속 `display:none`) 일반 클릭도 `{force:true}` 클릭도 통하지 않음 — `.role-apply-btn`은 어차피 페이지 로드시 `document.querySelectorAll(...).forEach(el=>el.addEventListener("click",...))`로 개별 배선되는 plain 리스너라, `elementHandle.evaluate(el=>el.click())`로 네이티브 DOM `.click()`을 직접 호출해 우회 |
| .../issues, /boards, /pullrequests | specs/03-organization/organization-crud.spec.ts | partial | 하위 프로젝트가 없는 상태(500 아님)만 확인 — 실제 프로젝트를 조직 소유로 만든 뒤의 취합 결과는 미검증 |
| .../settingform → 저장(이름 변경 포함) | specs/03-organization/organization-crud.spec.ts | done | 로고 업로드는 미검증 |
| .../deleteForm | specs/03-organization/organization-crud.spec.ts | partial | 화면 로드만, 실제 삭제는 의도적으로 미실행(뒤 스펙이 seed.orgName을 계속 참조) |

## 4. 프로젝트 (Project)

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| GET /projectform → 홈 | specs/04-project/project-create.spec.ts | done | GIT/HG/SVN 세 VCS, PUBLIC/PRIVATE, 빈 이름 거부 |
| GET /{owner}/{projectName} | specs/04-project/project-create.spec.ts | partial | 생성 후 리다이렉트만 확인, 홈 화면 위젯 전체는 미검증 |
| /members | specs/04-project/project-members.spec.ts | done | 소유자 표시 확인 + 실제 멤버 초대(POST /api/projects/{id}/members) → 역할 드롭다운으로 Member↔Manager 전환(PUT .../role) 실제 확인 → 별도 throwaway 계정 초대 후 삭제(DELETE .../{userId}, 네이티브 confirm() 처리)까지 확인. seed.secondUserLoginId는 Manager로 영구 유지(07-pull-request가 재사용) |
| /setting | specs/04-project/project-settings.spec.ts | done | 설명 수정 후 AJAX 저장이 실제로 반영되는지 재조회로 확인 + 메뉴 토글(review) 실제로 끄고 저장→재조회로 반영 확인→다시 켜서 원복까지 확인(issue/wiki/board/milestone/pullRequest는 06-10이 의존해서 review로만 검증) |
| /changeVCS | specs/04-project/project-change-vcs.spec.ts | partial | 화면 로드만(체크박스+버튼 존재) — 실제 VCS 전환은 되돌리기 어려워 미실행 |
| /transfer | specs/04-project/project-transfer.spec.ts | partial | 화면 로드+입력만, 실제 이관은 미실행(owner가 05/06/07 시드) |
| /deleteform | specs/04-project/project-delete.spec.ts | partial | 화면 로드만, 실제 삭제는 미실행(05/06/07 시드 보호) |
| /issue/labelsform | specs/04-project/project-issue-labels.spec.ts | partial | copy-labels 폼(유일한 정적 `<form>`)만 검증 — 신규 라벨 생성 UI는 JS 위젯(`attachLabelListAdapter`)이 동적 렌더링해서 미검증 |
| /newFork | specs/04-project/project-fork.spec.ts | done | 실제 포크 실행 + 3초 지연 AJAX 리다이렉트까지 확인, seed에 `forkedProjectOwner`/`forkedProjectName` 저장(07-pull-request가 재사용 가능) |
| /pull/... clone 안내(pullrequest/clone.html) | specs/04-project/project-fork.spec.ts | partial | 포크 인터스티셜을 경유하지만 그 화면 자체(진행 메시지 등)는 별도 검증 안 함, 최종 리다이렉트 결과만 확인 |
| /projects/{owner}/{p}/branch-protections | specs/04-project/project-branch-protection.spec.ts | done | 모든 체크박스 on으로 규칙 추가 + 빈 패턴 서버측 400 거부(raw fetch로 검증, HTML5 required 우회) |
| /projects/{owner}/{p}/deploy-keys | specs/04-project/project-deploy-keys.spec.ts | done | 더미 ed25519 공개키로 read-only 키 등록 + 삭제 폼(네이티브 `confirm()` 승인 포함, `<form method=post>` 실제 제출) 클릭 후 목록에서 사라지는지까지 확인 |
| /projects/{owner}/{p}/webhooks | specs/04-project/project-webhooks.spec.ts | done | Slack 포맷 + git push 이벤트 포함으로 등록, 4개 포맷 옵션 존재 확인 + 삭제 버튼(`requestAs()` fetch DELETE) 실제 클릭 후 목록에서 사라지는지까지 확인 |
| /{owner}/{projectName}/setting (로고 업로드, POST /api/projects/{id}/logo) | specs/04-project/project-logo.spec.ts | done | `#logoPath` change 이벤트 즉시 업로드(별도 저장 버튼 없음) → 업로드 응답 확인 후 `GET /projects/{id}/logo`로 실제 서빙되는 바이트가 업로드한 파일과 정확히 일치하는지 확인(같은 프로젝트에 재실행 시 이전 실행의 로고와 우연히 같아지는 "before/after 크기 비교" 방식은 오탐 가능성이 있어 바이트 단위 일치 비교로 설계) |
| /watchers | specs/04-project/project-watchers.spec.ts | done | 화면 로드 확인 + 헤더의 실제 watch/unwatch 버튼(Bootstrap dropdown 안에 있어 `.down-arrow` 토글로 먼저 열어야 클릭 가능함을 확인) 클릭 → watcher count 증감과 `/watchers` 목록 반영까지 확인 → 마지막에 다시 watch로 원복(admin은 프로젝트 생성 시 자동 watch 상태라 다른 스펙에 부작용 안 남게) |
| /statistics | specs/04-project/project-statistics.spec.ts | partial | 화면 로드만(200), 차트 렌더링 내용은 미검증 |
| /new/import | specs/04-project/project-import.spec.ts | done | 필드 입력 확인 + 빈 URL 서버측 거부(재렌더링) 확인. 실제 외부 git clone은 네트워크 의존이라 미실행 |

## 5. 코드뷰어 (Code)

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| /code, /code/{branch}[/{path}] | specs/05-code/00-code-browser-empty.spec.ts | done | 0-commit 상태(빈 저장소 안내 문구)는 이 파일에서, 실제 커밋 이후 상태는 아래 code-diff.spec.ts에서 확인 |
| /code/download/{branch} | | todo | ZIP 다운로드 |
| /commits, /commits/{branch}[/{path}] | specs/05-code/code-diff.spec.ts | done | `01-code-git-setup.spec.ts`가 실제 git CLI로 main에 서로 다른 커밋 2개 push(DIFF_FIXTURE.md 추가→수정) → 히스토리 목록에 두 커밋 메시지 모두 렌더링되는지 확인 |
| /commit/{commitId} | specs/05-code/code-diff.spec.ts | done | diff 본문(추가/삭제 라인) 렌더링 확인 + 커밋 댓글 작성(`textarea[data-editor-mode="commit-comment-body"]` → ReviewViewController.newCommitComment 실제 POST) 후 화면 반영까지 확인 |
| /branches | specs/05-code/code-diff.spec.ts | done | 실제 main 브랜치가 목록에 뜨는지 확인(00-code-browser-empty.spec.ts는 0-commit 상태만 별도 확인) |
| /tags | specs/05-code/00-code-browser-empty.spec.ts | done | 0-commit 상태는 이 파일에서, 실제 태그 목록/삭제는 `branch-tag-management.spec.ts`에서(태그 생성 자체는 화면에 UI가 없어 CLI로만 가능 — git tag push 후 화면에서 삭제) |
| /compare/{a}..{b} | specs/05-code/code-diff.spec.ts | done | 두 실제 커밋 SHA로 비교 뷰 렌더링 확인 |
| 브랜치 기본 지정 (POST .../code/{branch}/setAsDefault), 브랜치 삭제 (DELETE .../code/{branch}), 태그 삭제 (DELETE .../tags/{tag}) | specs/05-code/branch-tag-management.spec.ts | done | 실제 git CLI로 브랜치/태그를 별도 push한 뒤(다른 05-code 스펙의 커밋 히스토리와 독립) 기본 브랜치 지정→main으로 원복, 브랜치/태그 삭제까지 실제 클릭으로 확인. **테스트 인프라 주의점(제품 버그 아님)**: 이 세 액션 모두 `yona.Common.js`의 `requestAs()`가 처리하는 같은-URL `document.location.reload()`라 `page.waitForNavigation()`이 안정적으로 감지하지 못함(경험적으로 확인, Chromium/Playwright의 same-URL reload 감지 한계로 보임) — 응답을 직접 `page.waitForResponse()`로 기다린 뒤 `page.goto()`로 명시적 재이동하는 패턴으로 우회. setAsDefault/delete 컨트롤러가 `redirect:...`(3xx)를 반환해 `fetch()`가 내부적으로 따라가므로 `response.ok()`(2xx 전용) 대신 `status() < 400`으로 단언 |

**Mercurial 프로젝트(hg)** — `01-hg-svn-code-setup.spec.ts`가 실제 `hg` CLI로 clone → 서로 다른 내용의 커밋 2개 push(hg의 첫 push는 원격에 알려진 브랜치가 없어 `--new-branch` 필요함을 실측 확인) → 아래에서 검증.

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| /commits | specs/05-code/hg-diff.spec.ts | done | 두 실제 hg 커밋 메시지 모두 렌더링 확인 |
| /commit/{changesetHash} | specs/05-code/hg-diff.spec.ts | done | code/diff.html(git과 공용)이 실제 hg 체인지셋 diff를 렌더링함을 확인(변경된 라인 내용으로 검증) |
| /compare/{a}..{b} | specs/05-code/hg-diff.spec.ts | done | 두 실제 hg 체인지셋 해시로 비교 뷰 렌더링 확인 |
| /branches | specs/05-code/hg-diff.spec.ts | done | **처음에 hg도 브랜치 목록이 뜰 거라 잘못 가정했다가 실패로 발견**: `BranchViewController.branches()`가 `vcsType != "GIT"`이면 무조건 `error/400`("This request is only supported in a git project.")을 렌더링하는 git 전용 화면임을 실측 확인(뷰 이름이 실제 HTTP 상태를 정하지 않는 이 코드베이스 관례대로 200으로 렌더링됨) — hg 자체의 named branch 기능과 무관하게 이 UI 화면 자체가 git 전용이라는 실제 동작을 검증 |
| /code | specs/05-code/hg-diff.spec.ts | done | 실제 커밋 이후 파일 트리(ajax fragment) 렌더링 확인 |

**Subversion 프로젝트(svn)** — 같은 `01-hg-svn-code-setup.spec.ts`가 실제 `svn` CLI(브루로 신규 설치)로 checkout → 서로 다른 내용의 리비전 2개 commit → 아래에서 검증.

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| /commits | specs/05-code/svn-diff.spec.ts | done | 두 실제 svn 리비전 커밋 메시지 모두 렌더링 확인 |
| /commit/{revision} | specs/05-code/svn-diff.spec.ts | done | `code/svnDiff.html`(git/hg의 diff.html과 별도 템플릿, `repository.getPatch()` 사용)이 실제 unified diff patch를 렌더링함을 확인 |
| /compare/{revA}..{revB} | specs/05-code/svn-diff.spec.ts | done | `code/compare_svn.html`이 실제 두 리비전 간 patch를 렌더링함을 확인 |
| /branches | specs/05-code/svn-diff.spec.ts | done | hg와 동일하게 git 전용 화면이라 `error/400`("only supported in a git project")을 렌더링함을 확인(SVN에 브랜치 개념이 약해서가 아니라 이 화면 자체가 git 전용) |
| /code | specs/05-code/svn-diff.spec.ts | done | 실제 커밋 이후 파일 트리 렌더링 확인 |

## 6. 이슈 (Issue)

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| /issueform → 생성 | specs/06-issue/issue-crud.spec.ts | done | **제목 공백이 실제로 허용됨을 실측 확인(제품 검증 갭, 테스트 버그 아님)** — README 참고 |
| /issue/{number} | specs/06-issue/issue-crud.spec.ts | done | |
| /issue/{number}/editform | specs/06-issue/issue-crud.spec.ts | done | |
| /issues (목록) | specs/06-issue/issue-crud.spec.ts | done | |
| POST /api/projects/{id}/issues/{number}/comments (댓글) | specs/06-issue/issue-crud.spec.ts | done | `#comment-form`의 `th:action`은 죽은 라우트(템플릿 자체 주석에 명시)이고 실제로는 JS가 fetch AJAX로 이 REST 경로에 제출함을 확인 후 실제 댓글 작성·렌더링까지 확인 |
| 이슈 작성 폼 첨부파일 업로드 (`<yona-attachments>`, POST /files) | specs/06-issue/issue-attachments.spec.ts | done | Vue 3 SFC 컴파일 커스텀 엘리먼트라 Shadow DOM일 가능성을 염두에 뒀으나 Playwright 로케이터가 그대로 뚫고 `input[type=file]`을 찾아냄 — 실제 파일 업로드 후 `.attached-file.complete` 상태로 렌더링되는지까지 확인 |
| /issue/{number}/timeline (fragment) | | todo | ajax 폴링 |
| /reviews (ReviewThreadController) | | todo | |
| 이슈 목록의 일괄수정 위젯 — 담당자 지정 | specs/06-issue/issue-management.spec.ts | done | `#assignee` yona-dropdown에서 "나에게 할당" 옵션(항상 두 번째 `<li>`, 로케일 텍스트 대신 위치로 선택 — 이 환경은 영어로 렌더링됨을 실측) 클릭 → 실제 massupdate POST(`assignee.id=<currentUserId>`) → 이슈뷰의 담당자 hidden input(`#assignee[value]`)에 반영되는지까지 확인 |
| 이슈 목록의 일괄수정 위젯 — 마일스톤 지정 | specs/06-issue/issue-management.spec.ts | done | `#milestone` 드롭다운에서 seed 마일스톤 선택 → massupdate POST(`milestone.id=<id>`) → 이슈뷰의 `<select id="milestone">` value로 반영 확인 |
| 이슈 목록의 일괄수정 위젯 — 상태(열림/닫힘) 토글 | specs/06-issue/issue-management.spec.ts | done | CLOSED→OPEN 왕복 확인, 배지 텍스트("Closed"/"Open")까지 단언. **제품 버그#4 수정 완료(BUGFIXES.md 참고)**: 상태 배지 텍스트가 `#{'issue.state.' + issue.state}`로 원본(대문자) enum 이름을 그대로 메시지 키에 쓰던 것을 293행과 동일하게 `#strings.toLowerCase(issue.state)`로 감싸도록 수정 — 배지 텍스트가 `??issue.state.CLOSED_en_US??` 대신 정상 렌더링됨을 Playwright로 확인 |
| 이슈 목록의 일괄수정 위젯 — 라벨 추가/제거 | specs/06-issue/issue-management.spec.ts | done | **제품 버그#3 수정 완료(BUGFIXES.md 참고)**: 원 추정(컨트롤러의 `attachingLabelIds`/`detachingLabelIds` 바인딩·저장 문제)은 틀렸음 — curl로 직접 확인한 결과 라벨은 실제로 DB에 정상 반영됨. 진짜 원인은 #6과 동일한 프론트엔드 버그(`yona.ui.TomSelect.js`의 milestone 렌더러가 `data.state` 없을 때 raw text를 반환 → Tom Select의 `getDom()`이 그 텍스트를 CSS 셀렉터로 오인해 크래시 → `[data-toggle="tomselect"]` 자동초기화 forEach가 `#milestone`에서 멈춰 `#labelIds`의 Tom Select 인스턴스가 아예 초기화되지 않아 라벨이 DB엔 있어도 화면엔 안 보였음) — 같은 한 군데(TomSelect.js milestone 렌더러)를 고쳐 해결, 이제 `.issue-label[data-label-id]` 뱃지가 정상 렌더링됨을 확인 |
| 이슈 추천(vote)/추천취소 | specs/06-issue/issue-management.spec.ts | done | 추천과 추천취소 둘 다 실제 UI 클릭으로 왕복 확인(href/class 전환 포함). **제품 버그#5 수정 완료(BUGFIXES.md 참고)**: 추천취소 버튼의 `th:href`가 `hasVoted` 여부와 무관하게 항상 `/vote`로 고정되어 있던 것을 `hasVoted`에 따라 `/vote`·`/unvote`(`VoteController.kt`의 실제 별도 라우트)로 갈리도록 수정 — 더 이상 `/unvote`를 직접 호출하는 정리 코드 없이 UI 클릭만으로 취소됨을 확인 |
| 댓글 수정/삭제 | specs/06-issue/issue-management.spec.ts | done | **제품 버그#6 수정 완료(BUGFIXES.md 참고)**: 원 진단의 증상(괄호 있는 마일스톤 제목에서 `querySelector` 크래시 → 인라인 스크립트 나머지 초기화 불발)은 맞았지만 원인 후보(`_toElement()`/`Assginee.js`/`Sharer.js`)는 전부 틀렸음 — 실제 호출부는 `yona.ui.TomSelect.js`의 milestone 렌더러: `data.state`가 없으면 `return data.text`로 가공 없는 원본 텍스트를 반환해 Tom Select 라이브러리의 `getDom()`이 이를 CSS 셀렉터로 오인, `document.querySelector(text)`를 호출함(괄호 등 셀렉터로 파싱 안 되는 문자가 있으면 SyntaxError, 아니면 null 반환 후 다음 줄 setAttribute가 null 참조로 크래시). 항상 `<div>...</div>` HTML을 반환하도록 수정해 해결 |
| 이슈 삭제 (DELETE /api/projects/{id}/issues/{number}) | specs/06-issue/issue-management.spec.ts | done | 삭제 확인 모달(`#deleteConfirm`) 실제 클릭 → 목록에서 사라지는지까지 확인 |

## 7. Pull Request

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| (사전 준비) 실제 git clone/commit/push로 main+feature/e2e-pr 브랜치 생성 | specs/07-pull-request/00-pull-request-git-setup.spec.ts | done | `child_process`로 real git CLI 셸아웃, HTTP Basic 인증(`/git/*`). **직접 실행해 clone→commit→push 전 과정이 실제로 성공함을 확인함**(정적 검증이 아니라 실측). **버그 발견 및 수정**: 같은 프로젝트에 이미 이 스펙이 성공적으로 한 번 실행된 적이 있으면(예: 이전 전체 스위트 실행), 두 번째 실행 시 README.md 내용이 이전과 동일해 `git commit`이 "nothing to commit"으로 실패함을 실측 확인 — `--force` push는 push 단계만 보호할 뿐 로컬 commit 단계는 보호 못 함. `uniqueSuffix()`를 커밋 내용에 포함시켜 재실행 시에도 항상 실제 diff가 생기도록 수정. |
| /pull/new → 생성 | specs/07-pull-request/01-pull-request-crud.spec.ts | done | PR 생성 폼이 `<form>`에 action이 없고 JS(`yona.pullrequest.Write.js`)가 AJAX 제출 후 성공 시 목록 페이지로 이동하는 구조 — `_getRedirectURL()`이 "new" 모드에서 항상 `/pulls`(PR 자신의 URL 아님)로 보낸다는 점까지 확인 후 목록에서 PR 번호를 역추출하는 방식으로 실제 실행·통과 확인(전체 스위트 클린 실행에서 매번 재현) |
| /pull/{number} | specs/07-pull-request/01-pull-request-crud.spec.ts | done | 실제 실행 확인 |
| /pull/{number}/changes (댓글) | specs/07-pull-request/01-pull-request-crud.spec.ts | done | 범위 없는 일반 댓글 폼(`common/commentForm.html`)은 이슈 댓글과 달리 AJAX 가로채기 없는 진짜 네이티브 `<form>` POST(ReviewViewController.newPullRequestComment)임을 확인 — 실제 댓글 작성 후 `/pullRequest/{number}/changes`로 리다이렉트되는 것까지 확인. 코드라인 단위 스레드 댓글(reviewForm 팝오버)은 스코프 밖(별도 팝오버 위젯, 미검증) |
| /pull/{number}/edit | specs/07-pull-request/01-pull-request-crud.spec.ts | done | 제목 값은 `<input value>`라 `toHaveValue()`로 검증(텍스트 노드 아님) — 실제 실행 확인 |
| /pulls, /closedPullRequests, /sentPullRequests | specs/07-pull-request/01-pull-request-crud.spec.ts | done | 실제 실행 확인 |
| /pull/{number}/state (fragment) | specs/07-pull-request/01-pull-request-crud.spec.ts | done | 실제 실행 확인 |
| 담당자 지정/해제 (PUT/DELETE .../assignee) | specs/07-pull-request/02-pull-request-workflow.spec.ts | done | `#pr-assignee-select`는 TomSelect로 숨겨진 `<select>`라 `selectOption()` 대신 `.value` 직접 대입+`change` 이벤트 디스패치로 지정/해제 왕복 확인 |
| 라벨 추가/제거 (POST/DELETE .../labels) | specs/07-pull-request/02-pull-request-workflow.spec.ts | done | `#labelIds`는 프로젝트에 라벨이 하나도 없으면 DOM에 아예 렌더링되지 않아(issue/partial_select_label.html의 th:if), 실제 라벨 생성 REST API(`POST /api/v1/projects/{owner}/{project}/labels`)를 `page.evaluate` 내부 `fetch()`로 먼저 호출(패치된 전역 fetch가 CSRF 헤더를 자동 주입)한 뒤 select2 add/remove 왕복 확인 |
| 리뷰어 자기등록/해제 (#btn-review/#btn-unreview) | specs/07-pull-request/02-pull-request-workflow.spec.ts | done | `project.isUsingReviewerCount`(기본 false) 설정을 켜야 이 UI 자체가 렌더링됨을 확인하고 `/setting` 화면에서 실제로 켠 뒤 검증. PR 컨트리뷰터(admin)는 자기 자신에 대한 APPROVE/REQUEST_CHANGES가 `SelfReviewException`(400)으로 막혀 있어, 전용 throwaway 리뷰어 계정을 새로 가입시켜 프로젝트 멤버로 초대한 뒤 그 계정으로 등록/승인/등록해제까지 확인 |
| 리뷰 판정 제출 (APPROVE/REQUEST_CHANGES/COMMENT, POST .../reviews) | specs/07-pull-request/02-pull-request-workflow.spec.ts | done | throwaway 리뷰어가 APPROVE 제출, admin(컨트리뷰터 본인)은 COMMENT만 제출 가능함을 확인(APPROVE/REQUEST_CHANGES 버튼 자체가 `canApproveOrRequestChanges=false`로 숨겨짐) |
| PR 닫기/재오픈 (POST .../state?state=CLOSED\|OPEN) | specs/07-pull-request/02-pull-request-workflow.spec.ts | done | 실제 클릭으로 CLOSED→OPEN 왕복 확인(로케일 의존 텍스트 대신 `data-request-uri` 패턴으로 검증) |
| /pull/mergeResult (fragment), 실제 머지 (POST .../merge) | specs/07-pull-request/02-pull-request-workflow.spec.ts | done | 클린 순차 전체 실행으로 재검증한 결과 애초 의심했던 "포크 간 git 충돌"은 사실이 아니었음(진짜 원인: 이 파일 자체의 리뷰어 등록취소 테스트가 리뷰어 수 0명을 남기는데 `isUsingReviewerCount`가 여전히 켜져 있어 `meetsReviewerCount`가 거짓이 된 것 — 실제 git 충돌 아님). 머지 직전 리뷰어수 강제 옵션을 다시 끄도록 수정 후 실제 병합 성공(`#btnAccept` 클릭 → confirm() → "Merged" 배지) 확인 |

## 8. 위키 (Wiki)

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| /wiki (빈 상태) | specs/08-wiki/wiki-crud.spec.ts | done | 실제 실행 확인 |
| /wiki/_new → 생성 | specs/08-wiki/wiki-crud.spec.ts | done | `display:none` textarea에 `.fill({force:true})`가 focus() no-op 때문에 이전 포커스(제목 입력창)로 값이 새는 버그를 발견 — `el.value` 직접 대입 + `input`/`change` 이벤트 디스패치로 수정 후 실제 실행 확인 |
| /wiki/{title} (뷰) | specs/08-wiki/wiki-crud.spec.ts | done | |
| /wiki/_edit/{title} | specs/08-wiki/wiki-crud.spec.ts | done | |
| /wiki/_history/{title} | specs/08-wiki/wiki-crud.spec.ts | done | |
| /wiki/_search | specs/08-wiki/wiki-crud.spec.ts | done | `WikiServiceImpl.search()`는 제목만 필터링(본문 검색 아님)함을 확인하고 검색어를 제목 부분문자열로 조정 |
| 위키 페이지 삭제 (POST /wiki/_delete/{title}) | specs/08-wiki/wiki-crud.spec.ts | done | 별도 throwaway 페이지를 만들어서 삭제(시드 페이지는 보존) — `onsubmit="return confirm(...)"` 네이티브 confirm() 승인 후 실제 삭제·목록 반영까지 확인 |
| **환경 오염 주의(제품/내 변경 버그 아님)** | specs/08-wiki/wiki-crud.spec.ts | - | 원래 있던 "create a wiki page" 테스트(하드코딩된 고정 제목 `E2E-Seed-Page`, uniqueSuffix() 없음)가 이번 세션의 매우 긴 반복 디버깅 동안 영속 H2 DB에 같은 제목의 페이지가 이미 여러 번 쌓여 "이미 존재" 오류로 막힘 — `wikiTitle`이 11-search에서도 참조돼 임의로 고치지 않음. 서버 재시작/DB 초기화 후에는 정상 통과할 것으로 예상 |

## 9. 게시판 (Board)

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| /postform → 생성 | specs/09-board/board-crud.spec.ts | done | 실제 실행 확인 |
| /post/{number} (뷰) | specs/09-board/board-crud.spec.ts | done | |
| /post/{number}/editform | specs/09-board/board-crud.spec.ts | done | **제품 버그 발견 및 수정**: 게시글 수정 AJAX 성공 핸들러가 `window.location.href` 리다이렉트 전에 블로킹 네이티브 `alert()`를 띄우는데, Playwright는 다이얼로그를 자동 승인하지 않아 리다이렉트가 멈춤 — `page.once('dialog', d => d.accept())`를 클릭 전에 등록해 해결 |
| /posts (목록) | specs/09-board/board-crud.spec.ts | done | |
| 게시글 라벨 지정 (PUT /api/projects/{id}/posts/{postId}/labels) | specs/09-board/board-crud.spec.ts | done | **제품 버그 발견 및 수정**(버그 #2): board/view.html이 이슈와 동일한 라벨 `<select>` 위젯을 렌더링하고 서버 라우트(`BoardController#updatePostLabels`)도 이미 정상 동작했지만, `yona.board.View.js`에 이 select의 change를 저 PUT 라우트로 저장하는 배선이 전혀 없었음 — `yona.issue.View.js`의 `_delegate(issueInfoWrap, "change", "[data-toggle=tomselect]", ...)` 패턴과 동일하게 `yona.board.View.js`에 배선을 추가해 수정. 테스트를 "선택이 저장 안 됨을 문서화"에서 "리로드 후에도 유지됨을 확인"으로 전환 |
| 게시글 삭제 (DELETE /api/projects/{id}/posts/{postId}) | specs/09-board/board-crud.spec.ts | done | **제품 버그 발견 및 수정**(버그 #1): 사이트 전역 `yona.Common.js`의 `requestAs()`가 `[data-request-method]` 전체를 자동 배선하는데, board/view.html이 같은 삭제 버튼에 자기만의 별도 클릭 핸들러를 또 붙여 클릭 한 번에 DELETE 요청이 **두 번** 나가던 레이스 컨디션 — board/view.html이 자체 리스너를 붙이는 대신 idempotent한 `$yona.requestAs(el)`(이미 자동배선으로 캐시된 인스턴스를 재사용, 새 리스너 추가 없음)의 `"load"` 이벤트에만 걸어 목록으로 이동하도록 수정. `test.fixme()`를 실제 `test()`로 전환하고 DELETE 요청 횟수를 직접 세는 단언 추가, 5회 연속 재실행으로 회귀 없음 확인 |

## 10. 마일스톤 (Milestone)

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| /milestone/new → 생성 | specs/10-milestone/milestone-crud.spec.ts | done | title/dueDate/설명(hidden textarea, wiki와 동일한 focus() no-op 문제라 evaluate() 방식으로 수정)/state 라디오까지 채워 실제 실행 확인 |
| /milestone/{id} (뷰) | specs/10-milestone/milestone-crud.spec.ts | done | |
| /milestone/{id}/editform | specs/10-milestone/milestone-crud.spec.ts | done | |
| /milestones (목록) | specs/10-milestone/milestone-crud.spec.ts | done | |
| 마일스톤 열기/닫기 (POST .../close, .../open) | specs/10-milestone/milestone-crud.spec.ts | done | seed 마일스톤을 닫기→"열기" 버튼 노출 확인→다시 열어서 원복까지 실제 클릭으로 확인 |
| 마일스톤 삭제 (DELETE /{owner}/{projectName}/milestone/{id}) | specs/10-milestone/milestone-crud.spec.ts | done | 별도 throwaway 마일스톤을 만들어서 삭제(시드 마일스톤은 보존) — 삭제 확인 모달 실제 클릭, 204+Location 응답을 `requestAs()`가 따라가 `/milestones` 목록으로 실제 이동하는지까지 확인 |
| **테스트 인프라 수정(제품 버그 아님)** | specs/10-milestone/milestone-crud.spec.ts | - | 원래 "E2E seed milestone"이 다른 모든 엔티티(프로젝트/조직/이슈)와 달리 `uniqueSuffix()` 없이 하드코딩돼 있었음 — 마일스톤 제목은 프로젝트 내 유일해야 해서(`MilestoneViewController`가 "This milestone title already exists."로 거부, 실측 확인) 영속 DB에 반복 재실행하면 결정적으로 깨짐. 다른 엔티티들과 같은 관례로 `uniqueSuffix()`를 붙여 수정(throwaway 마일스톤 제목도 동일하게 수정) |

## 11. 검색 (Search)

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| GET /search (헤더 검색창 폼 제출 포함) | specs/11-search/search.spec.ts | done | 빈 키워드 400 거부, 06-issue가 만든 이슈 제목으로 실제 검색 적중 확인 |
| GET /{owner}/{projectName}/search | specs/11-search/search.spec.ts | done | |
| GET /org/{orgName}/search | specs/11-search/search.spec.ts | partial | 03-organization과 병행 작성이라 순서 보장 안 됨 — `seed.orgName` 없으면 `test.skip`. org 시드 있으면 로드만 확인(실제 조직 소속 콘텐츠 검색 적중까지는 미검증) |

## 12. 알림/기타 (Notifications & misc)

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| / (index) | specs/12-notifications/notifications.spec.ts | done | 로그인/비로그인 렌더링 둘 다 확인 |
| /notifications | specs/12-notifications/notifications.spec.ts | done | 로그인 시 로드, 비로그인 시 로그인폼 리다이렉트 |
| /_help | specs/15-misc/misc.spec.ts | done | 로그인/비로그인 둘 다 200 (인증 불필요 화면) |
| /oauth2/consent | specs/15-misc/misc.spec.ts | partial | 필수 파라미터(client_id/scope/state) 없이 접근 시 400 확인(실측, curl로 재확인함)만 — 실제 동의 화면 렌더링은 OAuth2 클라이언트 앱 등록이 선행돼야 해서 14번(admin/oauth) 영역으로 넘김 |
| /migration | specs/15-misc/misc.spec.ts | done | 실측 확인(curl): 이 환경(h2 프로파일, 외부 마이그레이션 소스 미설정)에서는 `isAllowMigration()`이 false라 항상 "error/403" 뷰를 **HTTP 200으로** 렌더함(뷰 이름이 실제 상태 코드를 정하지 않음) — 이 동작 그대로 검증 |
| 즐겨찾기(star): 프로젝트 (POST /-_-api/v1/favoriteProjects/{id}) | specs/15-misc/favorites.spec.ts | done | GNB "Favorite" 사이드바 패널(로그인 시 항상 DOM에 존재, `#sidebar-open-btn`으로 열어야 함)에서 본인 소유 프로젝트 star 실제 클릭→즐겨찾기 반영→다시 클릭해 원복까지 확인 |
| 즐겨찾기(star): 조직 (POST /-_-api/v1/favoriteOrganizations/{id}) | specs/15-misc/favorites.spec.ts | done | 같은 패널에서 소속 조직 star 실제 토글 확인(개인 프로젝트용 빈 placeholder star-org는 `data-organization-id` 없음으로 구분해 제외) |
| 즐겨찾기(star): 이슈 (POST /-_-api/v1/favoriteIssues/{id}) | specs/15-misc/favorites.spec.ts | done | 이슈 상세 화면의 star 아이콘 실제 토글 확인. **UI 버그 발견(수정 안 함, 최종 보고 참고)**: 토글 성공 시 뜨는 `$yona.notify()` 토스트가 내부적으로 공유 `<yona-dialog id="yonaDialog">`를 건드리는데, 이 커스텀 엘리먼트가 이후 페이지 전체에 걸쳐 눈에 안 보이는(자기 bounding box는 0-width로 화면 밖에 있지만 실제 히트테스트는 어느 좌표에서든 이 엘리먼트로 잡힘) 클릭 차단 오버레이를 영구히 남김 — 실제 사용자가 토스트/알림을 한 번이라도 본 뒤에는 페이지의 다른 어떤 클릭도 조용히 씹힐 수 있는 심각한 UX 버그. 테스트는 `el.click()` 인페이지 디스패치로 우회 |

## 13. 관리자 (Admin/Site) — 전체 범위 포함(사용자 확정)

**URL 정정**: 실제 컨트롤러(`SiteViewController.kt`)는 `@RequestMapping(["/site", "/sites"])`라
아래 URL 전부 `/site` 접두사가 붙는다(matrix.md 최초 버전에 접두사가 빠져 있었음 — 정정).

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| /site/userList, /site/users | specs/13-admin/site-admin.spec.ts | done | 쿼리/상태 필터 입력 포함 |
| /site/projectList | specs/13-admin/site-admin.spec.ts | done | 프로젝트명 필터 입력 포함 |
| /site/issueList | specs/13-admin/site-admin.spec.ts | done | 로드만(필터 UI는 목록 자체엔 없음, 서버 파라미터만) |
| /site/postList | specs/13-admin/site-admin.spec.ts | done | 로드만 |
| /site/mail, /site/mails | specs/13-admin/site-admin.spec.ts | done | 발신자 필드 프리필 확인만, 실제 발송은 안 함 |
| /site/massmail(s) | specs/13-admin/site-admin.spec.ts | done | 라디오 전환(전체↔프로젝트별)까지, **실제 발송 버튼은 클릭 안 함**(드라이런 모드가 따로 없어 실제 이메일이 나갈 수 있음) |
| /site/data | specs/13-admin/site-admin.spec.ts | done | import 폼 존재 확인(실제 파일 업로드는 안 함) |
| /site/diagnostic | specs/13-admin/site-admin.spec.ts | done | 자가진단 실행 결과 렌더 확인 |
| /site/update | specs/13-admin/site-admin.spec.ts | done | 현재 버전 표시 확인 |
| 비관리자 접근 시 403 | specs/13-admin/site-admin.spec.ts | done | 별도 신규 계정으로 실측(`/user/editform/oauth-apps-owned/new`이 아니라 `/site/userList`로) |
| /site/oauth-apps | specs/13-admin/oauth-apps-admin.spec.ts | done | **스코프 정정**: 이 화면은 등록 폼이 없다 — 등록 기능은 `OAuthAppsAdminController.kt`의 자체 주석에 따라 사용자 셀프서비스(`/user/editform/oauth-apps-owned`, 02-user/14번 영역)로 이전됐고, `/site/oauth-apps`는 전체 앱 감사(소유자 포함 조회) + 강제 삭제 전용 읽기전용 화면이다. 셀프서비스로 등록한 앱이 소유자 정보와 함께 이 감사 목록에 실제로 나타나는지 확인 + 관리자가 강제 삭제 버튼을 실제로 클릭(confirm() 승인)해서 소유자와 무관하게 삭제되는지까지 확인 |
| /site/sso | specs/13-admin/sso-admin.spec.ts | done | OIDC/SAML2 두 폼 모두 저장→재렌더 확인, 테스트 끝에 둘 다 다시 비활성화(다음 실행에 가짜 IdP 설정이 활성 상태로 남지 않게) |
| 계정 잠금/해제 (POST /site/toggleAccountLock) | specs/13-admin/site-admin.spec.ts | done | 전용 더미 계정으로 잠금→로그인 불가 실측 확인→해제까지, 실제 클릭으로 검증 |
| 관리자 권한 부여/회수 (POST /site/toggleSiteAdminRole) | specs/13-admin/site-admin.spec.ts | done | 부여→SITE_ADMIN 탭 반영 확인→즉시 회수(불필요한 admin 계정 남기지 않음)까지 실제 클릭으로 검증 |
| 게스트 모드 토글 (POST /site/toggleGuestMode) | specs/13-admin/site-admin.spec.ts | done | **제품 버그 수정됨(BUGFIXES.md #7)**: `SiteService.toggleGuestMode()`가 `isGuest` 불리언만 뒤집고 `state` enum은 안 건드려서 "게스트 사용자" 탭 목록 쿼리(`UserRepository.findUsersForAdminQuery`, `state` 컬럼만 필터링)에 절대 반영되지 않던 버그. `toggleGuestMode()`가 이제 `toggleAccountLock()`/`toggleSiteAdminRole()`과 동일한 패턴으로 `state`를 ACTIVE↔GUEST로 함께 뒤집도록 수정(`isGuest`도 계속 동기화 — 실제 게스트 권한 판단 로직 전체가 `isGuest`를 직접 읽으므로). 테스트가 ACTIVE→GUEST 탭 이동을 재조회로 실제 검증하도록 강화됨 |
| 관리자의 비밀번호 강제 초기화 (POST /site/users/{loginId}/reset-password) | specs/13-admin/site-admin.spec.ts | done | **제품 버그 수정됨(BUGFIXES.md #8)**: userList.html의 "비밀번호 초기화" 버튼 `data-href`가 `/{loginId}?action=resetPassword`로 잘못 빌드돼 매번 404였던 것과, 그 성공/대기 알림을 그리는 인라인 스크립트가 이미 제거된 jQuery의 `$.tmpl(...).appendTo(...)`를 여전히 호출해 `$ is not defined`로 fetch 이전에 죽어 있던 것(둘 다 수정) — data-href를 실제 라우트로 고치고 알림 렌더링을 `$yona.tmpl` vanilla 헬퍼로 교체. 실제 신규 비밀번호로 로그인 성공까지 확인 |

## 14. OAuth2 / 2FA / SSO — 전체 범위 포함(사용자 확정)

| URL | 스펙 파일 | 상태 | 비고 |
|---|---|---|---|
| /user/editform/security | specs/14-oauth2-2fa-sso/two-factor-settings.spec.ts | done | 자격증명 0개 상태에서 로드, TOTP/WebAuthn 추가 링크 존재 확인 |
| /user/editform/security/totp/new | specs/14-oauth2-2fa-sso/two-factor-settings.spec.ts | partial | QR코드+base32 시크릿 렌더 확인, 틀린 코드 제출 시 재표시까지 확인. **실제 활성화(올바른 TOTP 코드 제출)는 미검증** — RFC 6238 계산기를 직접 구현하거나 otplib 설치가 필요해 과설계로 판단, 화면/실패경로까지만(지시받은 스코프 그대로) |
| /user/editform/security/webauthn/new | specs/14-oauth2-2fa-sso/two-factor-settings.spec.ts | partial | 화면 로드만 — 실제 인증기 없이 완주 불가(README 명시 제약) |
| /user/editform/security/backup-codes/show | specs/14-oauth2-2fa-sso/two-factor-settings.spec.ts | done | "fresh 코드 없으면 설정화면으로 리다이렉트"라는 실제 동작을 그대로 검증(2FA 미활성 상태라 도달 자체가 안 됨 — 이것도 실제 동작) |
| /users/login/2fa | specs/14-oauth2-2fa-sso/two-factor-login.spec.ts | partial | "2FA 활성 계정으로 실제 로그인 시도"는 미검증(위와 동일 이유) — 대신 `Pre2faAuthenticationToken` 없이(즉 2FA 안 켠 일반 로그인 상태로) 직접 URL 접근 시 `/users/loginform`으로 리다이렉트되는 실제 동작을 실측 확인 |
| /oauth2/consent | specs/14-oauth2-2fa-sso/oauth2-consent.spec.ts | done | 자체적으로 OAuth 앱을 셀프서비스 등록(다른 fork/영역에 의존 안 함) → `/oauth2/authorize` 전체 왕복 → consent 화면 도달 및 앱 이름 렌더까지 확인. 파라미터 누락(4xx) / 존재하지 않는 client_id(404) 케이스도 포함 |
| /site/oauth-apps (관리자) | specs/13-admin/oauth-apps-admin.spec.ts | done | 13번 표 참고 |
| /site/sso | specs/13-admin/sso-admin.spec.ts | done | 13번 표 참고 — SAML/OIDC 실제 IdP 연동은 스코프 밖, 설정 폼 저장/재렌더까지만 |

---

**범례**: 이 저장소에는 REST 전용(JSON) 엔드포인트(`ReviewApiController`, `CommentThreadController`,
`MarkdownController`, `BranchApiController` 등)는 별도 화면이 없어 이 매트릭스에서 제외했다 —
화면에 도달하려면 어차피 위 화면들의 버튼/폼을 거쳐 간접 호출된다.
