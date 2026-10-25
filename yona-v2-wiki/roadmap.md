---
title: 이식 로드맵과 진행 상태
type: synthesis
sources: [../AGENTS.md, ../yona-v1.6-wiki/web/templates/*.md]
updated: 2026-10-22
---

# 이식 로드맵

선생님 지시(2026-10-22): **의존 방향 순으로 진행하고, 결정이 필요하면 먼저 묻는다.** 템플릿은 242개이며 하나씩 직접 이식한다. 단위마다 TDD, 위키, 커밋을 함께 한다.

## 진행 상태

| 단계 | 내용 | 상태 | 기록 |
|---|---|---|---|
| 0 | 골격, 스키마 61개 테이블 검증(H2, PostgreSQL, MariaDB), 초기 데이터 | 완료 | [domain/schema-mapping.md](domain/schema-mapping.md), [subsystems/initial-data.md](subsystems/initial-data.md) |
| 0 | 정적 자원, LESS 빌드, 다국어 메시지 | 완료 | [web/static-assets-and-i18n.md](web/static-assets-and-i18n.md) |
| 0 | 인증(해시, 로그인, 세션, remember-me, 토큰), `@AnonymousCheck` | 완료 | [subsystems/authentication.md](subsystems/authentication.md), [subsystems/access-control.md](subsystems/access-control.md) |
| 0 | 레이아웃, 로그인 화면, `/messages.js`, 오류 화면 | 완료 | [web/layout-and-login-page.md](web/layout-and-login-page.md), [web/error-pages.md](web/error-pages.md) |
| 1 | 홈 화면(소개, 환영 표, 기본 로그인 페이지) | 완료 | [web/home-page.md](web/home-page.md) |
| 2a | `AccessControl` 권한 규칙 | 완료 | [subsystems/access-control.md](subsystems/access-control.md) |
| 2b-1 | `ProjectService`(조회, 소속, 메뉴 개수) | 완료 | [domain/project-service.md](domain/project-service.md) |
| 2b-2 | 프로젝트 화면 기반: `projectLayout`, `projectMenu`, 프로젝트 헤더, 프로젝트용 오류 화면 | 완료 | [web/project-layout.md](web/project-layout.md) |
| 2b-3 | 컨트롤러 검사 애너테이션: `@IsAllowed`, `@IsCreatable`, `@GuestProhibit`, `@IsOnlyGitAvailable`, 프로젝트 존재 검사 | 완료 | [web/project-checks.md](web/project-checks.md) |
| 2b-4 | Git 저장소 서비스(JGit): 저장소 생성/삭제, 경로 설정 | 완료 | [subsystems/git-repository.md](subsystems/git-repository.md) |
| 2b-5a | 프로젝트 목록(`/projects`) | 완료 | [web/project-list.md](web/project-list.md) |
| 2b-5b | 프로젝트 생성(`/projectform`, `POST /projects`) | 완료 | [web/project-create.md](web/project-create.md) |
| 2b-5c-1 | 프로젝트 홈(README, 설명 수정, 구성원, 마일스톤 요약), 마크다운 미리보기 | 완료 | [web/project-home.md](web/project-home.md) |
| 2b-5c-2 | 마크다운 프로젝트 문맥 후처리(자동 링크, 이슈 링크, 캐시 동작) | 완료 | [web/markdown.md](web/markdown.md) |
| 2b-5c-3a | 첨부 파일 핵심(저장, 내려받기) | 완료 | [web/attachments.md](web/attachments.md) |
| 2b-5c-3b | 프로젝트 설정(로고 업로드, 이름 변경, 기본 브랜치) | 완료 | [web/project-setting.md](web/project-setting.md) |
| 2b-5c-3c | 구성원 관리 | 완료 | [web/project-members.md](web/project-members.md) |
| 2b-5c-3d | 프로젝트 삭제 | 완료 | [web/project-delete.md](web/project-delete.md) |
| 2b-5c-3e | 프로젝트 이전, 저장소 종류 변경 | 완료 | [web/project-transfer.md](web/project-transfer.md) |
| 2c | 조직(생성, 홈, 목록, 설정, 구성원, 삭제, 탈퇴)과 가입 신청(조직, 프로젝트) | 완료 | [web/organization.md](web/organization.md) |
| 2d-1 | 가입, 이메일 인증, 사용 여부 확인 | 완료 | [web/signup-and-password.md](web/signup-and-password.md) |
| 2d-2 | 비밀번호 재설정 | 완료 | [web/signup-and-password.md](web/signup-and-password.md) |
| 2d-3 | 계정 설정(프로필, 비밀번호, 이메일, 토큰), 아바타, 방문 목록 초기화, 첨부 올리기와 임시 첨부 정리 | 완료 | [web/account.md](web/account.md) |
| 2d-4a | 사용자 검색 API(자동 완성) | 완료 | [web/account.md](web/account.md) |
| 2d-4b | 사용자 페이지(`/:user`: 프로필 상자, 이슈·풀 리퀘스트·프로젝트 탭), 내 파일 목록(`/user/files`), 프로젝트 탈퇴(`/info/leave`) | 완료 | [web/user-page.md](web/user-page.md) |
| 2d-4c | 내 이슈(`/user/issues`: 프로젝트를 가리지 않는 이슈 검색, 빠른 검색 여섯 항목, pjax·JSON 응답) | 완료 | [web/my-issues.md](web/my-issues.md). 처음 로드맵에서 빠져 있다가 2d-4b 때 발견했다 |
| 3a | 이슈 라벨과 분류 | 완료 | [web/issue-labels.md](web/issue-labels.md) |
| 3b-0 | 공용 편집기 부품(편집기, 업로더, 마크다운 도움말), 멘션 후보, 첨부 목록, 폼 본문 크기 한도 | 완료 | [web/editor-components.md](web/editor-components.md) |
| 3b | 마일스톤 | 완료 | [web/milestones.md](web/milestones.md) |
| 3c-1 | 이슈 목록, 검색, 엑셀, 일괄 변경 막대(화면), 마일스톤 보기의 이슈 영역 | 완료 | [web/issue-list.md](web/issue-list.md) |
| 3c-2a | 이슈 등록 폼과 등록, 제목 머리말, 멘션, 타임라인 이벤트 기록, 폼 자동 완성 API | 완료 | [web/issue-create.md](web/issue-create.md) |
| 3c-2b | 이슈 보기(읽기), 타임라인, 다음 상태, 삭제 | 완료 | [web/issue-view.md](web/issue-view.md) |
| 3c-2c | 이슈 수정(프로젝트 이동, 초안 게시, 변경 이력) | 완료 | [web/issue-edit.md](web/issue-edit.md) |
| 3c-3a | 이슈 댓글 등록, 수정, 삭제, 이슈와 댓글 추천 | 완료 | [web/issue-comments.md](web/issue-comments.md) |
| 3c-3b | 일괄 변경(`POST issues`) | 완료 | [web/issue-mass-update.md](web/issue-mass-update.md) |
| 3c-3c-1 | 구독, 이슈 즐겨찾기 | 완료 | [web/watch-and-favorite.md](web/watch-and-favorite.md) |
| 3c-3c-2 | 이슈 화면 API(담당자, 공유자, 가중치, 변경 감지, 본문과 댓글 갱신) | 완료 | [web/issue-api.md](web/issue-api.md) |
| 3c-3c-3a | 번역 API | 완료 | [web/issue-api.md](web/issue-api.md) |
| 3e | 외부 REST API(`/-_-api/v1`) | 완료 | 사용자, 이슈, 프로젝트, 게시판, 마일스톤, 구독자, hello. [web/external-issue-api.md](web/external-issue-api.md), [web/external-project-api.md](web/external-project-api.md) |
| 3d | 게시판(목록, 보기, 작성, 수정, 삭제, 댓글, 라벨 API, 조직 게시판)과 온라인 커밋 | 완료 | [web/board.md](web/board.md) |
| 2d-5 | 사용자 메뉴(사이드바 내용, 고정 사이드바), 프로젝트 바로가기, 프로젝트·조직 즐겨찾기 API | 완료 | [web/user-menu.md](web/user-menu.md) |
| 4a | 알림 이벤트 생성(활동 → 알림 이벤트, 수신자 계산, 합치기) | 완료 | [subsystems/notifications.md](subsystems/notifications.md) |
| 4b | 알림 메일 발송(스케줄러, 메시지, HTML 메일, 묶기)과 시스템 메일(비밀번호 재설정, 가입 인증, 이메일 확인, 이전 요청) | 완료 | [subsystems/notifications.md](subsystems/notifications.md) |
| 4c | 홈의 알림 목록(More 포함) | 완료 | [subsystems/notifications.md](subsystems/notifications.md) |
| 4d | 보관 기간 정리, 프로젝트 구독·해제·구독자 목록, 프로젝트별 알림 설정 탭 | 완료 | [subsystems/notifications.md](subsystems/notifications.md) |
| 5a-1 | Git smart-HTTP(복제, 푸시), Basic 인증, 푸시 훅(마지막 푸시 시각, 최근 푸시 브랜치, 예약 ref 거부) | 완료 | [subsystems/git-http.md](subsystems/git-http.md) |
| 5b-1 | 코드 브라우저(폴더, 파일, 원본, 이미지, 열기, 내려받기, 메타데이터 JSON) | 완료 | [web/code-browser.md](web/code-browser.md) |
| 5b-2 | 브랜치 목록, 기본 브랜치, 브랜치 삭제 | 완료 | [web/code-browser.md](web/code-browser.md) |
| 5b-3 | 커밋 이력 | 완료 | [web/code-browser.md](web/code-browser.md) |
| 5c-0a | diff 엔진(`DiffService`, `FileDiff`: 커밋, 두 리비전, 서로 다른 저장소 비교, 헝크 계산) | 완료 | [subsystems/diff.md](subsystems/diff.md) |
| 5c-0b | 공용 diff 화면 조각(줄 그리기, 스레드 조각), 커밋 보기와 비교 화면(읽기), 커밋 구독, 커밋 diff 멘션 후보 | 완료 | [web/commit-view.md](web/commit-view.md). 댓글 등록과 스레드 상태 변경은 5d |
| 5c-1 | 포크(`newFork`, `fork`, `clone`) | 완료 | [web/fork.md](web/fork.md) |
| 5c-2 | 풀 리퀘스트 목록(열림, 닫힘, 보냄), 검색, 최근 푸시 브랜치 | 완료 | [web/pull-request-list.md](web/pull-request-list.md) |
| 5c-3 | 풀 리퀘스트 만들기(`newPullRequestForm`, `mergeResult`, `newPullRequest`), 병합 시도, 커밋 동기화, 알림 | 완료 | [web/pull-request-create.md](web/pull-request-create.md), [subsystems/pull-request-merge.md](subsystems/pull-request-merge.md) |
| 5c-4 | 풀 리퀘스트 보기, 상태 조각, 변경 내용, 리뷰·리뷰 취소, 멘션 후보 | 완료 | [web/pull-request-view.md](web/pull-request-view.md) |
| 5c-5 | 받아들이기(병합), 닫기와 열기, 수정, 브랜치 삭제와 복원 | 완료 | [web/pull-request-actions.md](web/pull-request-actions.md) |
| 5c-6 | 풀 리퀘스트 댓글 | 5d로 이동 | 리뷰 스레드 생성 로직을 커밋 댓글과 함께 5d에서 옮긴다 |
| 5d | 코드 리뷰 댓글(커밋 댓글, 풀 리퀘스트 댓글, 스레드 열기와 닫기, 댓글 삭제), 푸시 후 훅(새 커밋 알림, 이슈 언급, 풀 리퀘스트 확인과 삭제) | 완료 | [web/review-comments.md](web/review-comments.md), [subsystems/post-receive.md](subsystems/post-receive.md). Subversion 커밋 코멘트는 8단계 |
| 5e | 웹훅(설정 화면, 발송), 이벤트와 웹훅 연결 | 완료 | [web/webhooks.md](web/webhooks.md), [subsystems/webhooks.md](subsystems/webhooks.md) |
| 5f | 외부 저장소 가져오기(`/_import`) | 완료 | [web/import.md](web/import.md) |
| 6a | 검색(전체, 조직, 프로젝트 범위: 이슈, 사용자, 프로젝트, 게시글, 마일스톤, 이슈 댓글, 게시글 댓글, 코드 리뷰 댓글) | 완료 | [web/search.md](web/search.md) |
| 6b | 도움말(`/_help`), 프로젝트 통계(`/:user/:project/statistics`, v1.6도 "Under Construction"), UI 견본(`/_UIKit`) | 완료 | [web/help-statistics.md](web/help-statistics.md). `/restricted`는 소셜 로그인 세션이 필요해 7단계에서 옮긴다(A-88) |
| 6c-1 | 사이트 관리 틀(`siteMngLayout`)과 사용자 관리(`/sites/userList`: 목록, 검색, 잠금, 게스트 전환, 관리자 권한, 삭제, 비밀번호 초기화) | 완료 | [web/site-admin.md](web/site-admin.md) |
| 6c-2 | 사이트 관리: 프로젝트, 게시글, 이슈 목록과 프로젝트 삭제 | 완료 | [web/site-admin.md](web/site-admin.md) |
| 6c-3 | 사이트 관리: 메일 쓰기와 대량 메일 | 완료 | [web/site-admin.md](web/site-admin.md) |
| 6c-4 | 사이트 관리: 업데이트 확인과 진단, 모든 화면의 업데이트 알림 | 완료 | [web/site-admin.md](web/site-admin.md). 진단의 메일 수신 점검은 7단계(IMAP)에서 더한다 |
| 6d | 데이터 내보내기와 가져오기(`/sites/data`, `/sites/export`, `/sites/import`: 47개 테이블 JSON), 아바타 없는 사용자 목록과 아바타 지정(`/sites/noAvatarUsers`, `/sites/setAttachmentToUserAvatar`) | 완료 | [web/site-admin.md](web/site-admin.md), [subsystems/data-exchange.md](subsystems/data-exchange.md) |
| 6e | 최초 기동 화면(`welcome/secret`, `welcome/restart`): 사이트 비밀 키가 기본값이면 모든 요청이 기본 관리자 계정을 정하는 화면 | 완료 | [web/welcome.md](web/welcome.md). D-22 |
| 6f | 이전(Migration) 화면(`/migration`, 프로젝트 데이터 JSON 내보내기, GitHub 토큰 교환) | 완료 | [web/migration.md](web/migration.md) |
| 7a | 소셜 로그인(GitHub, Google), 로그인 화면 버튼, 소셜 로그아웃, `/restricted` | 완료 | [web/social-login.md](web/social-login.md). D-24 |
| 7b | LDAP 인증(폼 로그인, Git HTTP 기본 인증), 소셜 로그인 전용 모드의 로그인 거부 | 완료 | [web/ldap.md](web/ldap.md). JNDI(D-24) |
| 7c | 이메일 수신(IMAP): 주소와 본문 읽기, 이슈와 댓글 만들기, 메일 처리기, IMAP 연결과 수신 대기, 진단의 "Email Receiver" 점검 | 완료 | [web/mailbox.md](web/mailbox.md). Jakarta Mail(D-24), D-25 |
| 8a | Subversion 저장소(SVNKit): 만들기, 지우기, 옮기기, 읽기, 이력, 패치, 프로젝트 만들기와 저장소 종류 바꾸기, README와 이슈 템플릿 | 완료 | [web/subversion.md](web/subversion.md). D-13, A-98 |
| 8b | Subversion 코드 브라우저(폴더, 파일, 원본, 내려받기), 빈 저장소 안내 | 완료 | [web/subversion.md](web/subversion.md) |
| 8c | Subversion 커밋 이력, 커밋 보기, 비교, 커밋 코멘트(만들기, 지우기, 알림), 멘션 후보 | 완료 | [web/subversion.md](web/subversion.md) |
| 8d | Subversion HTTP(WebDAV) 접근(`/svn/소유자/프로젝트`): SVNKit DAV 서블릿과 javax 서블릿 어댑터 | 완료 | [web/subversion.md](web/subversion.md). D-26 |
| 9a | 동등성 점검: 라우트 대조(`conf/routes` 대 v2 요청 매핑). `RouteParityTest`가 알려진 누락 목록과 실제 누락을 비교한다 | 진행 중 | 누락 17건 발견. 라벨 API 5건은 이식 완료([web/label-api.md](web/label-api.md)). 조직 이슈와 PR 목록 3건도 이식 완료([web/organization-lists.md](web/organization-lists.md)). 리뷰 목록(`/:user/:project/reviews`, xls)도 이식 완료([web/review-list.md](web/review-list.md)). 남은 6건: 코드 메타데이터 AJAX 2개, `/_init`, `/users/login`, `/-_-api`, `/-_-api/v1/`. 누락 항목은 위키에 v1.6 동작을 먼저 적고 구현하며, 구현할 때마다 테스트의 목록에서 지운다 |
| 9b | 동등성 점검: 메시지 키 대조 | 예정 | |
| 9c | 동등성 점검: DB 스키마 대조 | 예정 | |
| 9d | 동등성 점검: 템플릿과 JS 소스 대조 | 예정 | |
| 9e | 동등성 점검: 컨트롤러 점검표와 보강 테스트 | 예정 | |

## 화면 검증 기록

- 2026-10-25, 2d-4b(사용자 페이지, 내 파일): Playwright로 새 v2 화면 3곳(사용자 페이지의 이슈 탭과 프로젝트 탭, 내 파일 목록)을 캡처하고, 가입, 프로젝트 만들기, 구성원 추가, 첨부가 있는 이슈 등록 뒤 이슈 탭의 항목, 프로젝트 탭의 구독과 탈퇴 버튼, 내 파일의 위치 링크를 확인했다. 콘솔 오류는 구성원 추가 폼의 `pattern` 속성 정규식(v1.6 템플릿 그대로) 하나뿐이다.
- 2026-10-23, 3단계(이슈, 마일스톤, 라벨, 게시판, 사용자 메뉴): Playwright로 새 v2 화면 14곳(이슈 목록, 보기, 수정, 등록, 마일스톤 목록과 보기, 라벨, 게시판 목록, 보기, 작성, 수정, 사용자 메뉴 세 탭, 고정 사이드바)을 캡처하고 체크리스트, 구독, 댓글, 일괄 변경, 라벨, 즐겨찾기 상호작용을 확인했다. 이 과정에서 타임리프 JS 인라인 뒤 덧셈이 지워지는 오류, `commentNotiReceivers` 404, `usermenuTabContentList` 403을 찾아 고쳤다. 남은 콘솔 오류는 v1.6 코드 자체의 R-30뿐이다.
- 2026-10-23, 4단계(알림 서브시스템): Playwright로 새 v2 화면 3곳(홈 알림 목록, 알림 설정 탭, 프로젝트 구독자)을 캡처하고, 사용자 가입, 프로젝트 구독, 이슈와 글 작성, 이슈 닫기로 알림 3건이 홈에 쌓이는 것, 설정 스위치가 저장되는 것, 구독 해제 시 설정 탭이 비는 것, 스케줄러가 알림 메일 3통(To=사이트 주소, Bcc=구독자, 새 이슈와 새 글의 Message-ID, 멘션 링크, 절대 주소)을 간이 SMTP 수신기로 보낸 것을 확인했다. 브라우저 콘솔 오류는 없었다. 이 과정에서 영어 사용자에게 누락 메시지 키가 `??키_en_US??`로 보이는 것을 찾아 A-62로 고쳤다.

## 단계별로 남겨 둔 연결 고리

앞 단계에서 일부러 비워 둔 곳이다. 해당 단계에서 채운다.

| 비워 둔 곳 | 채울 단계 |
|---|---|
| 홈의 알림 항목 표시와 More 버튼, `GET /notification` | 4에서 완료 |
| `GET /user/sidebar`와 사이드바 목록, `GET /user/usermenuTabContentList` | 2d-5에서 완료 |
| `ErrorViews`의 조직 변형 | 소셜 로그인 버튼 영역, 사이트 업데이트 알림 | 7, 6 |
| 최초 기동 시 관리자 비밀번호 설정 화면(`welcome/secret`) | 6e에서 완료 |
| `AccessControl`의 엔티티별 `asResource()` | 각 엔티티의 화면 단계 |
| Play 언어 쿠키(`PLAY_LANG`) | 2d |

## 규칙 요약

- v1.6을 그대로 따른다. 결함도 재현하고 [refactoring-backlog.md](refactoring-backlog.md)에 올린다.
- 기술 스택은 [architecture/technology-decisions.md](architecture/technology-decisions.md)의 결정을 따르고, 새 결정이 필요하면 선생님께 먼저 묻는다.
- 검증은 H2, PostgreSQL, MariaDB 세 DB에서 모두 통과해야 한다. 화면 캡처는 Playwright로만 하며 v1.6 화면은 캡처할 수 없으므로 새 화면만 캡처한다. v1.6과의 일치는 템플릿 소스 대조와 테스트로 확인한다.
