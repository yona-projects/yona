---
title: 사이트 관리 (틀과 사용자 관리)
type: web
sources: [app/controllers/SiteApp.java:74-276, app/utils/SiteManagerAuthAction.java, app/views/site/siteMngLayout.scala.html, app/views/site/userList.scala.html, app/models/User.java:399-433, 694-720, app/models/Project.java:246-258, app/controllers/UserApp.java:1338-1362]
updated: 2026-10-25
---

# 사이트 관리

코드: `web/access/SiteManagerOnly`(애너테이션과 인터셉터), `domain/site/SiteUserService`, `web/site/SiteUserController`. 템플릿은 `layout/site-mng-layout`, `site/user-list`. 이 문서는 6c-1(틀과 사용자 관리)까지이다. 프로젝트, 게시글, 이슈 목록, 메일, 업데이트, 진단, 데이터 내보내기는 이어서 더한다.

## 접근

`@SiteManagerOnly`(v1.6 `@With(SiteManagerAuthAction)`)가 붙은 요청은 사이트 관리자가 아니면 `error.auth.unauthorized.waringMessage`를 담은 403 화면이다. 로그인 검사(`@AnonymousCheck`)가 먼저라 사이트가 익명 접근을 막으면 익명은 로그인 폼으로 간다. v1.6에 있던 액션 안의 재검사(세션 `loginId`로 다시 묻기)는 만들지 않았다(A-89).

## 틀 (`siteMngLayout`)

사이트 레이아웃(`SITE_SETTING` 메뉴) 안에 제목줄(`site.sidebar`)과 왼쪽 사이드바(사용자, 게시글, 이슈, 프로젝트, 메일 보내기, 대량 메일, 업데이트(알림 배지), 진단)가 있다. 활성 항목은 현재 요청 경로와 같은 항목이다. 데이터 내보내기 화면은 사이드바에 없다(v1.6도 같다).

## 사용자 목록 (`GET /sites/userList`)

- 쿼리: `pageNum`(1부터, 한 쪽 30명), `query`, `state`(`ACTIVE` 기본, `LOCKED`, `DELETED`, `GUEST`, `SITE_ADMIN`. 모르는 값은 400, A-89).
- 항상 기본 관리자(사용자 번호 1)와 익명 사용자를 뺀다. `GUEST`는 게스트 표시가 있는 사용자(상태 무관), `SITE_ADMIN`은 사이트 관리자로 등록된 사용자(관리자 행 번호 1은 뺀다), 그 밖에는 그 상태이다.
- `query`는 로그인 ID, 이름, 영문 이름, 이메일에서 대소문자 구분 없이 찾는다. 정렬은 가입 최근 순이다.
- 화면: 제목, 검색 폼(숨은 `state`), 탭 다섯 개(사이트 관리자 탭에는 관리자 수에서 하나를 뺀 배지), 목록(아바타, 이름, `@ID`, 이메일, 가입 시각(`socialDate`)), 쪽 번호. 삭제된 탭에는 조작 버튼 대신 상태가 바뀐 시각이 있다.
- 줄의 버튼 다섯 개: 게스트 전환, 잠금 전환, 비밀번호 초기화, 관리자 권한 전환, 삭제(확인 모달). 조작 주소의 쿼리는 v1.6 역방향 라우터와 같게 `loginId`, `state`, 있으면 `query` 순이다.

## 조작

| 요청 | 동작 | 응답 |
|---|---|---|
| `POST /sites/toggleAccountLock` | 활성이면 잠그고 그 밖의 상태는 활성으로 되돌린다 | 같은 응답으로 목록(1쪽) 화면 200. 없는 사용자는 안내 `user.notExists.name`과 함께 `/sites/userList?pageNum=0`으로 303 |
| `POST /sites/toggleGuestMode` | 게스트 여부만 뒤집는다 | 위와 같다 |
| `POST /sites/toggleSiteAdminRole/:loginId` | 관리자이면 권한을 거두고 아니면 부여한다(자기 보호 없음, R-52) | `/sites/userList`로 303 |
| `DELETE /sites/user/delete{id}` | 프로젝트의 유일한 매니저이면 안내 `site.userList.deleteAlert`와 빈 403. 아니면 프로젝트 구성원에서 모두 빼고 삭제 처리 | `/sites/userList`로 303 |
| `POST /:user?action=resetPassword` | 사이트 관리자가 여섯 글자 임시 비밀번호를 만들어 JSON으로 알려 준다([signup-and-password.md](signup-and-password.md)) | JSON |

삭제 처리는 상태를 `DELETED`로 바꾸고 이름 앞에 `[DELETED]`, 이메일을 `deleted-<ID>@noreply.yona.io`, 비밀번호와 소금을 빈 값으로 하며 사용자 행은 남긴다. 알림 수신 기록과 프로젝트 가입 신청을 지우고 담당 이슈의 담당자를 비운다.
