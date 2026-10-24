---
title: 사용자 페이지와 내 파일, 프로젝트 탈퇴
type: web
sources: [app/controllers/UserApp.java:709-807, 992-996, app/views/user/view.scala.html, app/views/user/partial_issues.scala.html, app/views/user/partial_pullRequests.scala.html, app/views/user/partial_projectlist.scala.html, app/views/user/userFiles.scala.html, app/models/Issue.java:524-529, app/models/PullRequest.java:219-225, app/models/Attachment.java:524-537, app/utils/RouteUtil.java:26-62]
updated: 2026-10-25
---

# 사용자 페이지와 내 파일, 프로젝트 탈퇴

코드: `web/user/UserPageController`, `UserPageAssembler`, `UserPageModels`, `UserFilesController`, `UserLeaveController`, `domain/misc/ResourceUrls`, `web/ProviderLogos`. 템플릿은 `user/view`, `partial-issues`, `partial-pull-requests`, `partial-projectlist`, `files`.

## 사용자 페이지 (`GET /:user`)

- 이름이 조직이면(대소문자 무시) `303`으로 `/organizations/<이름>`에 보낸다. 없는 사용자는 404(`user.notExists.name`)이다. `@AnonymousCheck`만 붙어 있어 사이트가 익명 접근을 막으면 로그인 폼으로 간다.
- 쿼리 `daysAgo`(기본 0)와 `selected`(기본 `issues`)를 받는다. 기간은 쿼리 값이 우선이다. 쿼리에 없으면(0) 쿠키 `daysAgo`를 쓰고, 쿠키도 없으면 14일이며 쿠키에 저장한다. 쿼리 값이 음수이면 1이다. 쿼리 값은 쿠키에 저장한다(경로 `/`, HttpOnly, 세션 쿠키). 없는 사용자에게도 쿠키를 먼저 저장하고 404를 돌려준다(R-48).
- 프로필 상자: 이름(제목줄), 256 크기 아바타, 게스트 표시(`OUR GUEST`), 영문 이름, `@로그인ID`, 이메일(`yona.show-user-email`), 본인 페이지에만 프로필 수정 버튼, `SITE ADMIN`과 `BLOCKED` 배지, 가입일(`MMM dd, yyyy`, 영어), 연결된 소셜 로그인 로고(GitHub, Google).
- 활동 상자는 보는 사람이 게스트이면 통째로 없다. 사이트가 프로젝트 목록을 숨기고(`hide-project-listing`) 보는 사람이 익명이면 세 탭 모두 비어 있다.
- 탭은 이슈, 풀 리퀘스트, 프로젝트이며 항목이 있으면 개수 배지가 붙는다. `selected`가 가리키는 탭이 열린다. 이슈 탭 안에는 열림과 닫힘 하위 탭, 칼럼 보기와 하위 이슈 보기 체크박스가 있다. 기간 입력(`#daysAgoBtn`)은 `user.View` 모듈이 쓴다.

### 조회 규칙

| 탭 | 조회 | 정렬 |
|---|---|---|
| 이슈 | 담당자이거나 작성자(`authorId`)이고 `updatedDate >= 지금 - daysAgo일` | 갱신 최근 순, 같으면 상태 순 |
| 풀 리퀘스트 | 보낸 사람(`contributor`)이고 `updated >= 지금 - daysAgo일`. 상태는 가리지 않는다 | 갱신 최근 순, 같으면 상태 순 |
| 프로젝트 | 구성원 행(`project_user`)이 있는 프로젝트(역할 불문), 같은 프로젝트는 한 번 | 마지막 푸시가 최근인 것이 앞, 푸시 기록이 없으면 뒤, 같으면 이름 순 |

모든 항목은 **보는 사람이 프로젝트를 읽을 수 있을 때만** 보인다(`Operation.READ`, 프로젝트마다 한 번만 검사). 초안 이슈는 거르지 않는다(R-48).

### 줄의 내용

- 이슈 줄: 프로젝트 이름(링크), `#번호`, 제목, 댓글과 추천 수, 하위 이슈 진행 막대와 부모 링크, 라벨(색은 라벨 색), 접힌 하위 이슈 목록, 작성자와 담당자의 부서를 뺀 이름(이름이 비면 `issue.noAuthor`), 시각(갱신된 적이 있으면 `Last Updated …`와 갱신 시각, 아니면 `Created at …`와 생성 시각), 마일스톤(프로젝트가 마일스톤 메뉴를 켠 경우), 마감일(열린 이슈가 지났으면 `overdue`). 목록과 달리 머리말 링크, 가중치 화살표, 공유자 수는 없다.
- 풀 리퀘스트 줄: 받는 프로젝트의 로고와 이름, 번호, 제목(충돌이면 `conflict`), 보낸 사람, 시각, 스레드 수, 받는 사람 아바타, 상태(충돌이면 `pullRequest.state.conflict`).
- 프로젝트 줄: 로고, 이름, 비공개 자물쇠, 포크 원본, 개요, 구성원 수(사이트 관리자 역할 제외), 소유자, 만든 시각, 마지막 푸시. 보는 사람이 소유자가 아니면 구독 버튼(구독 중이면 해제, 아니고 공개 프로젝트이면 구독), 본인 페이지이고 소유자가 아니면 `탈퇴` 링크(`/info/leave/<소유자>/<프로젝트>`).

## 내 파일 (`GET /user/files`)

- `?pageNum=`(1부터), `?filter=`. 현재 사용자가 올린 첨부(`owner_login_id`)를 만든 시각 내림차순으로 한 쪽에 50개씩 보인다. 이름은 대소문자 구분 없이 부분 일치한다. 익명 사용자는 `anonymous`의 첨부(없음)라서 빈 목록이다.
- 줄: 이미지 미리보기, 이름(열기 링크, 확장자 아이콘은 `filetype.js`), 크기(1000 단위, `1.5 kB`), 내려받기(`?action=download`), 날짜(`yyyy-MM-dd h:mm a`), 위치(첨부가 붙은 대상의 화면 주소, 새 창). 위치는 `ResourceUrls.urlOf`로 구하며 이슈, 이슈 댓글, 게시글, 게시글 댓글, 프로젝트, 코드 리뷰 댓글과 스레드, 커밋 댓글, 풀 리퀘스트, 사용자 아바타만 지원한다. 나머지 종류는 위치가 비어 있다.
- 쪽 번호는 `yobi.Pagination.update`가 채운다. 위쪽 탭은 알림, 내 이슈, 내 파일이다.

## 프로젝트 탈퇴 (`GET /info/leave/:user/:project`)

현재 사용자를 프로젝트 구성원에서 뺀다(소유자는 빠지지 않는다). 결과와 관계없이 `303`으로 `/<내 로그인ID>?daysAgo=14&selected=projects`에 보낸다. 로그인하지 않았으면 `anonymous`를 쓴다. 없는 프로젝트는 404이다(A-86). 상태를 바꾸는 GET이며 v1.6도 같다(R-48).

## 아키텍처 차이

- 알림 메일의 링크에 쓰던 `resourceUrl`을 `ResourceUrls`로 꺼냈다. 동작은 같다.
- 이슈 줄은 목록과 같은 `IssueListAssembler`로 만든다. 사용자 페이지는 다른 사람의 초안도 담는 옵션(`includeOthersDrafts`)으로 v1.6을 따른다.
- 사용자 페이지의 소셜 로그인 로고는 `user_credential`과 `linked_account`에서 읽는다. 소셜 로그인 자체는 7단계이다.
