# View Hierarchy

legacy Yona의 `docs/ko/technical/view-hierarchy.md`를 옮김. **주의**: 이 문서는 legacy의
242개 `.scala.html` 포함 관계를 그대로 옮긴 것이며, yona의 실제 템플릿(`templates/**/*.html`,
Thymeleaf) 242개 전부와 파일 단위로 재대조하지는 않았다 — `docs/TEMPLATE_BACKLOG.md`가
파일별 이식 상태를 추적하는 원 소스이니, 특정 화면의 정확한 include 구조가 궁금하면 그쪽을
먼저 확인하는 게 안전하다. 아래는 legacy 파일명(`views.a.b.scala.html`)을 yona의 명명
관례(`templates/a/b.html`, [views-naming-guide.md](views-naming-guide.md) 참고)로 기계적으로
치환한 것이다 — URL 자체는 프로젝트 원칙상(`docs/parity/index.md`) legacy와 최대한 동일하게
유지됐다.

## yona 홈

- *http://[yona URL]/*
- `templates/site/layout.html`
  - `templates/site/siteLayout.html`
    - `templates/common/usermenu.html`
    - (조건) `templates/index/partial_intro.html`
    - `templates/common/alert.html`
    - **`templates/index/index.html`**
    - (조건) `templates/index/partial_notifications.html`, `templates/index/myprojectList.html`

## 계정

### 로그인

- *http://[yona URL]/users/loginform*
- layout → siteLayout → usermenu, alert → **`templates/user/login.html`**

### 회원가입

- *http://[yona URL]/users/signupform*
- layout → siteLayout → usermenu, alert → **`templates/user/signup.html`**

### 아바타 > 프로필

- *http://[yona URL]/[UserName]?daysAgo=&selected=*
- layout → siteLayout → usermenu, alert → **`templates/user/view.html`**
  - `templates/user/partial_projectlist.html`
  - `templates/user/partial_postings.html`
  - `templates/user/partial_issues.html`
  - `templates/user/partial_pullRequests.html`
  - `templates/user/partial_milestones.html`

### 아바타 > 설정

- *http://[yona URL]/user/editform*
- layout → siteLayout → usermenu, alert → **`templates/user/edit.html`**

## 도움말

- *http://[yona URL]/help*
- layout → siteLayout → usermenu, alert → **`templates/help/toc.html`**

## 프로젝트

### 프로젝트 목록

- *http://[yona URL]/projects*
- layout → siteLayout → usermenu, alert → **`templates/project/list.html`**

### 새 프로젝트 만들기

- *http://[yona URL]/projectform*
- layout → siteLayout → usermenu, alert → **`templates/project/create.html`**

#### 새 프로젝트 만들기 > Git 저장소에서 코드 가져오기

- *http://[yona URL]/import*
- layout → siteLayout → usermenu, alert → **`templates/project/importing.html`**

### 프로젝트 홈

- *http://[yona URL]/[owner]/[project]*
- `templates/site/layout.html` → `templates/project/projectLayout.html`
  - `templates/project/navbar.html`
  - `templates/project/header.html`
  - **`templates/project/overview.html`**
    - `templates/project/projectMenu.html`
    - `templates/common/markdown.html`

## 게시판

### 게시판 홈

- *http://[yona URL]/[owner]/[project]/posts*
- projectLayout → navbar, header → **`templates/board/list.html`**
  - projectMenu, `templates/board/partial_list.html`, `templates/help/keymap.html`

### 게시판 > 글쓰기

- *http://[yona URL]/[owner]/[project]/postsform*
- projectLayout → navbar, header → **`templates/board/create.html`**
  - projectMenu, `templates/help/markdown.html`, (조건) `templates/common/fileUploader.html`,
    `templates/common/markdown.html`

### 게시판 > 항목선택

- *http://[yona URL]/[owner]/[project]/post/[post id]*
- projectLayout → navbar, header → **`templates/board/view.html`**
  - projectMenu, `templates/common/commentForm.html`(→ help/markdown, 조건부 fileUploader),
    `templates/help/keymap.html`, `templates/common/markdown.html`

### 게시판 > 항목선택 > 수정

- *http://[yona URL]/[owner]/[project]/post/[post id]/editform*
- projectLayout → navbar, header → **`templates/board/edit.html`**
  - projectMenu, commentForm, markdown

## 코드

### 코드 > 파일 / 파일선택

- *http://[yona URL]/[owner]/[project]/code/[branch]/[path]*
- projectLayout → navbar, header → **`templates/code/view.html`**
  - `templates/code/partial_branchitem.html`

### 코드 > 변경이력

- *http://[yona URL]/[owner]/[project]/commits/[branch]/[path]*
- projectLayout → navbar, header → **`templates/code/history.html`**
  - projectMenu, code/partial_branchitem

### 코드 > 변경이력 > 항목선택 / 커밋 > 항목선택

- *http://[yona URL]/[owner]/[project]/commit/[commit id]?branch=...&path=...*
- SVN이면 **`templates/code/svnDiff.html`**, Git이면 **`templates/code/diff.html`** —
  둘 다 projectMenu, `templates/partial_diff.html`(→ partial_filediff, common/mergely),
  commentForm, markdown을 포함한다.

## Pull Request

### Pull Request 홈

- *http://[yona URL]/[owner]/[project]/pullRequests*
- projectLayout → navbar, header → **`templates/pullrequest/list.html`**
  - projectMenu, `templates/pullrequest/partial_recently_pushed_branches.html`,
    `templates/pullrequest/partial_list.html`

### Pull Request > 새로 보내기

- *http://[yona URL]/[owner]/[project]/newPullRequestForm*
- projectLayout → navbar, header → **`templates/pullrequest/create.html`**
  - projectMenu, `templates/pullrequest/partial_diff.html`(→ fileUploader, markdown, 조건부
    partial_diff → partial_filediff, mergely)

### Pull Request > 항목선택 (개요/커밋/변경내역)

- *http://[yona URL]/[owner]/[project]/pullRequest/[id]*,
  *.../pullRequest/[id]/commits*, *.../pullRequest/[id]/changes*
- projectLayout → navbar, header → **`templates/pullrequest/view.html`** /
  **`viewCommits.html`** / **`viewChanges.html`**
  - projectMenu, `templates/pullrequest/partial_info.html`,
    `templates/pullrequest/partial_state.html`, commentForm, markdown, mergely(해당 시)

### Pull Request > 항목선택 > 커밋 > 항목선택

- *http://[yona URL]/[owner]/[project]/pullRequest/[id]/commit/[commit id]*
- projectLayout → navbar, header → **`templates/pullrequest/diff.html`**
  - partial_diff(→ partial_filediff, mergely), commentForm, markdown, mergely

## 이슈

### 이슈 홈

- *http://[yona URL]/[owner]/[project]/issues?state=open*
- projectLayout → navbar, header → **`templates/issue/list.html`**
  - projectMenu, `templates/issue/partial_search.html`(→
    `templates/milestone/partial_status.html`, 조건부 partial_massupdate/partial_list,
    help/keymap)

### 이슈 > 새 이슈 / 수정

- *http://[yona URL]/[owner]/[project]/issueform*,
  *.../issue/[id]/editform*
- projectLayout → navbar, header → **`templates/issue/create.html`** /
  **`templates/issue/edit.html`**
  - projectMenu, help/markdown, help/experimental, 조건부 fileUploader, markdown

### 이슈 > 항목선택

- *http://[yona URL]/[owner]/[project]/issue/[id]*
- projectLayout → navbar, header → **`templates/issue/view.html`**
  - projectMenu, commentForm, help/keymap, markdown

## 마일스톤

### 마일스톤 목록 / 새 마일스톤 / 항목선택 / 수정

- *http://[yona URL]/[owner]/[project]/milestones*,
  *.../newMilestoneForm*, *.../milestone/[id]*, *.../milestone/[id]/editform*
- projectLayout → navbar, header → **`templates/milestone/list.html`** /
  **`create.html`** / **`view.html`** / **`edit.html`**
  - projectMenu, fileUploader, markdown, (view는) issue/partial_massupdate, issue/partial_list

## 프로젝트 설정

### 설정 / 멤버 / 삭제

- *http://[yona URL]/[owner]/[project]/settingform*, *.../members*, *.../deleteform*
- projectLayout → navbar, header → **`templates/project/setting.html`** /
  **`templates/project/members.html`** / **`templates/project/delete.html`**
  - projectMenu, `templates/project/partial_settingmenu.html`
