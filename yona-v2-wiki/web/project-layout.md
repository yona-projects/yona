---
title: 프로젝트 레이아웃 (레이아웃, 메뉴, 헤더, 오류 화면)
type: web
sources: [app/views/projectLayout.scala.html, app/views/projectMenu.scala.html, app/views/project/header.scala.html, app/views/error/forbidden.scala.html, app/views/error/notfound.scala.html, app/views/error/badrequest.scala.html, app/utils/TemplateHelper.scala:185-204, app/controllers/CodeApp.java:219-240, app/utils/Config.java:95-150]
updated: 2026-10-22
---

# 프로젝트 레이아웃

모든 프로젝트 화면(`/:user/:project/...`)이 공유하는 틀이다. 사이트 레이아웃 위에 프로젝트 전용 상단 메뉴, 프로젝트 헤더, 프로젝트 메뉴를 얹는다. 변환 규칙은 [layout-and-login-page.md](layout-and-login-page.md)를 따른다.

## 파일 대응

| v1.6 | v2 (`templates/`) |
|---|---|
| `projectLayout.scala.html` | `layout/project-layout.html` (`projectLayout(제목키, view, 메뉴, 본문)`과 내부용 `projectBody`) |
| `projectMenu.scala.html` | `project/menu.html` (`projectMenu(view, 메뉴종류, 사용안함)`) |
| `project/header.scala.html` | `project/header.html` (`header(view)`) |
| `error/forbidden.scala.html` | `error/forbidden.html` |
| `error/notfound.scala.html` | `error/notfound.html` |
| `error/badrequest.scala.html` | `error/badrequest.html` |

페이지는 `<html th:replace="~{layout/project-layout :: projectLayout(제목키, projectView, 메뉴종류, ~{::본문})}">`를 걸고, 본문 안에서 `project/menu`를 직접 부른다(v1.6도 각 페이지가 `projectMenu`를 직접 불렀다).

## 데이터: `ProjectViewContext`

v1.6 템플릿이 `Project`의 메서드와 정적 도우미로 구하던 값을 모델 `projectView`로 모았다. `ProjectViewFactory.of(project)`가 현재 요청의 사용자와 요청 정보로 만든다. DB가 필요한 값은 템플릿이 쓸 때 한 번만 읽는다.

| 속성 | 의미 | v1.6 |
|---|---|---|
| `logoUrl`, `backgroundUrl` | 프로젝트(`PROJECT`)에 올린 첫 첨부(`/files/<id>`), 없으면 `/assets/images/project_default_logo.png`, `project_default.jpg`. 로고와 배경이 같은 첨부를 쓴다 | `urlToProjectLogo`, `urlToProjectBG` |
| `favorite` | 즐겨찾기 여부 | `FavoriteProject.findByProjectId` |
| `watching`, `watchingCount` | 구독 행이 있고 구독 해제 행이 없어야 구독 중. 구독 행 수 | `Watch.isWatching`, `getWatchingCount` |
| `guest` | 로그인했지만 사이트 관리자가 아니고 프로젝트 역할 행이 없음 | `ProjectUser.isGuest` |
| `enrolled`, `enrolledUserCount` | 가입 신청 여부, 신청자 수 | `User.enrolled`, `project.enrolledUsers.size` |
| `canWatch`, `canUpdate` | `WATCH`, `UPDATE` 권한 | `AccessControl.isAllowed` |
| `member`, `codeAccessible` | 구성원 여부, 코드 열람 가능 여부(코드 열람을 구성원으로 제한한 프로젝트는 구성원만) | `Project.hasMember` |
| `forked`, `pullRequestPath` | 포크 여부, 풀 리퀘스트 목록 주소(포크는 `sentPullRequests`) | `isForkedFromOrigin`, `getPullRequestURL` |
| `openIssueCount`, `openPullRequestCount`, `openReviewCount`, `postingCount` | 메뉴 배지 | [project-service.md](../domain/project-service.md) |
| `repositoryUrl` | 저장소 복제 주소(로그인했으면 `사용자@` 포함) | `CodeApp.getURLWithLoginId` |
| `settingShortcutEnabled` | 설정 단축키 `Q`를 켤지 여부 | 아래 R-11 |

### 저장소 주소 (`RepositoryUrls`)

`<스킴>://<호스트[:포트]><컨텍스트>/<소유자>/<이름>`(Git), `.../svn/<소유자>/<이름>`(Subversion). 스킴과 호스트는 설정(`yona.scheme`, `yona.hostname`, `yona.port`)이 있으면 그 값을, 없으면 요청의 값을 쓴다. 포트가 80이면 생략한다. 소유자와 이름은 URL 인코딩한다.

## 문서 제목

제목이 비어 있으면 `이름 - 소유자`, 있으면 `번역한 제목 - 소유자/이름`이다. 이미 번역된 문자열을 넘겨도 그대로 쓴다.

## 프로젝트 메뉴

| 항목 | 표시 조건 | 단축키 |
|---|---|---|
| 프로젝트 홈 | 항상 | H |
| 코드 | 메뉴 설정 `code` ∧ 코드 열람 가능 | C |
| 이슈 | `issue`. 열린 이슈 수 배지 | I (`?state=open`) |
| 풀 리퀘스트 | `pullRequest` ∧ Git ∧ 코드 열람 가능. 열린 풀 리퀘스트 수 배지 | P |
| 리뷰 | `review` ∧ 코드 열람 가능. 열린 리뷰 스레드 수 배지 | |
| 마일스톤 | `milestone` | M |
| 게시판 | `board`. 게시글 수 배지 | B |
| 설정(톱니바퀴) | `UPDATE` 권한. 가입 신청자 수 배지 | Q (R-11) |

배지는 0이면 표시하지 않는다. 메뉴 아래 스크립트가 `project.Global` 모듈에 단축키 지도와 저장소 주소를 넘긴다.

## 프로젝트 안의 오류 화면

| 함수 | 상태 | 내용 |
|---|---|---|
| `ErrorViews.forbidden(키, view)` | 403 | 프로젝트 레이아웃과 메뉴 안에서 안내. 익명이면 `/users/loginform?redirectUrl=<쿼리 포함 요청 주소>` 버튼 |
| `ErrorViews.notFound(키, view, 종류)` | 404 | `issue_post`, `board_post`, `milestone`, `code`이면 `error.notfound.<종류>` 문구, 활성 메뉴, 목록 링크(이슈 `?state=all`, 게시판, 마일스톤, 코드는 설정 화면). 그 외에는 일반 문구와 `javascript:history.back();` |
| `ErrorViews.badRequest(키, view, 메뉴)` | 400 | 메시지만 보여 준다 |

`ErrorViews.notFound(키, project)`처럼 종류 없이 프로젝트를 넘기는 v1.6 변형(`NotFound.render(키, project)`)은 프로젝트를 쓰지 않고 일반 404 화면을 돌려줬다. 같은 결과를 내는 `ErrorViews.notFound(키)`로 대체된다.

## v1.6 결함을 그대로 재현한 부분

- R-11: 키보드 단축키 `Q`(설정 화면)는 세션의 **로그인 ID**가 문자열 `manager`와 같을 때만 켜진다. 역할 이름과 로그인 ID를 혼동한 코드다(`projectMenu.scala.html`의 `case Some(role) if role.equals("manager")`).

## 검증

`ProjectLayoutTest` 29건(제목, 테마, 검색 범위, 로고와 배경, 이동 경로, 비공개와 보호 표시, 포크, 즐겨찾기, 가입 신청, 구독, 메뉴 항목과 배지와 권한, 단축키, 저장소 주소), `ProjectErrorPagesTest` 7건. 테스트 전용 엔드포인트와 템플릿은 `src/test/`에만 있다(`ProjectPageTestController`, `templates/support/project-page.html`).
