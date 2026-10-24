---
title: 사이트 관리 (틀과 사용자 관리)
type: web
sources: [app/controllers/SiteApp.java:74-276, app/utils/SiteManagerAuthAction.java, app/views/site/siteMngLayout.scala.html, app/views/site/userList.scala.html, app/models/User.java:399-433, 694-720, app/models/Project.java:246-258, app/controllers/UserApp.java:1338-1362]
updated: 2026-10-25
---

# 사이트 관리

코드: `web/access/SiteManagerOnly`(애너테이션과 인터셉터), `domain/site/SiteUserService`, `web/site/SiteUserController`. 템플릿은 `layout/site-mng-layout`, `site/user-list`. 이 문서는 6c-3(틀, 사용자 관리, 프로젝트·게시글·이슈 목록, 메일)까지이다. 업데이트, 진단, 데이터 내보내기는 이어서 더한다. 코드는 `domain/site/SiteContentService`, `web/site/SiteContentController`와 템플릿 `site/project-list`, `post-list`, `issue-list`가 더해졌다.

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

## 프로젝트, 게시글, 이슈 목록

| 요청 | 규칙 |
|---|---|
| `GET /sites/projectList?filter=&pageNum=` | 한 쪽 25개. `filter`가 비면 만든 최근 순, 아니면 이름에 대소문자 구분 없이 포함된 프로젝트(정렬 없음, 번호 순). 소유자와 개요는 찾지 않는다. **`pageNum`은 0부터이다**(기본 0, R-53). 줄: 로고, `소유자/이름`, 개요, 만든 날짜(`yyyy-MM-dd`), 삭제 버튼(확인 모달) |
| `DELETE /sites/project/delete/:id` | 프로젝트와 딸린 데이터를 지우고 `/sites/projectList`로 303. 저장소 디렉터리는 지우지 않는다(v1.6, R-53). 없는 프로젝트는 404(A-24) |
| `GET /sites/postList?pageNum=` | 모든 프로젝트의 게시글(공지, 비공개 포함)을 만든 최근 순으로 한 쪽 30개. 쪽 번호는 1부터. 줄: 프로젝트 로고와 링크, 글 제목, 작성자(없는 사용자는 `anonymous`), 시각, 댓글 수 |
| `GET /sites/issueList?pageNum=&state=` | 열림(기본)과 닫힘 탭. `state`는 대문자로 바꿔 읽고(`all`은 빈 목록, 모르는 값은 400) 한 쪽 30개, 만든 최근 순 |

## 메일 쓰기와 대량 메일

코드: `domain/site/SiteMailService`, `web/site/SiteMailController`. 템플릿은 `site/mail`, `site/mass-mail`. 대량 메일 화면은 `site.MassMail` 모듈(`yobi.site.MassMail.js`)을 쓴다.

- `GET /sites/mail?errorMessage=&sended=`: 보내는 사람(사이트의 보내는 주소가 미리 채워짐), 받는 사람, 제목, 본문 폼. 메일 서버 설정(`spring.mail.host`, `username`, `password`) 중 빠진 것을 오류 안내로 나열한다(A-90). `errorMessage`는 오류 안내, `sended=true`는 성공 안내이다.
- `POST /sites/mail`: 폼 `from`, `to`, `subject`, `body`를 UTF-8 일반 텍스트 한 통으로 보내고 **이동하지 않고** 같은 응답으로 메일 쓰기 화면을 성공 안내와 함께 그린다. 본문이 비었거나 주소가 올바르지 않으면 500이다. 보내지 못해도 성공으로 보인다(R-54).
- `GET /sites/massmail`: 받는 사람 종류(전체, 프로젝트별)와 프로젝트 입력(`/projects` 자동 완성), 선택된 프로젝트 라벨, "메일 쓰기" 버튼이 있다. 서버는 메일을 보내지 않는다.
- `POST /sites/mailList`(JSON): `all=true`이면 모든 사용자의 이메일, 폼에 `all`이 있으나 `true`가 아니면 빈 목록, 그 밖에는 폼의 모든 값을 `소유자/이름`으로 보고 그 프로젝트들의 구성원 이메일(사이트 관리자 역할 행은 뺌, 중복 없음). 폼이 비면 빈 목록, `Accept`가 JSON을 받지 않으면 406, 없는 프로젝트나 형식 오류는 500이다. 화면의 스크립트가 결과로 `mailto:` 주소를 만들어 메일 프로그램을 연다.
