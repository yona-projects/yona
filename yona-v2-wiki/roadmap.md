---
title: 이식 로드맵과 진행 상태
type: synthesis
sources: [../AGENTS.md, ../yona-v1.6-wiki/web/templates/*.md]
updated: 2026-11-06
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
| 5c-6 | 풀 리퀘스트 댓글 | **5d에서 완료(2026-11-10 확인)** | 리뷰 스레드 생성 로직을 커밋 댓글과 함께 5d에서 옮긴다 |
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
| 8d | Subversion HTTP(WebDAV) 접근(`/svn/소유자/프로젝트`): SVNKit DAV 서블릿(Jakarta용 포장판, A-617) | 완료 | [web/subversion.md](web/subversion.md). D-26 |
| 9a | 동등성 점검: 라우트 대조(`conf/routes` 대 v2 요청 매핑). `RouteParityTest`가 v1.6 라우트 스냅샷(`audit/v16-routes.txt`)과 v2 요청 매핑을 비교하고, 남은 차이는 의도적 제외(정적 자원, WebDAV 필터, Play 가짜 경로)뿐이다 | 완료 | 누락 17건 가운데 실제로 구현하지 않은 것은 라벨 API 5건([web/label-api.md](web/label-api.md)), 조직 이슈와 PR 목록 3건([web/organization-lists.md](web/organization-lists.md)), 리뷰 목록([web/review-list.md](web/review-list.md)), 홈 별칭 3건([web/home-page.md](web/home-page.md)), `/_init`([web/repository-init.md](web/repository-init.md), D-27)이었다. 코드 메타데이터 AJAX 2건은 오탐이었다 |
| 9b | 동등성 점검: 메시지 키 대조 | 완료 | `MessageParityTest`: 5개 언어 번들의 키가 v1.6과 같고(값은 설정 이름이 바뀐 `error.tooLargeText.admin` 하나만 다르다) 템플릿 참조 562개가 모두 번들에 있다. 코드가 쓰는 키 중 번들에 없는 것은 `common.comment.created`(이슈 엑셀 머리글)뿐이며 v1.6도 같다(R-67) |
| 9c | 동등성 점검: DB 스키마 대조 | 완료 | 기존 `V16SchemaTest`가 v1.6 실제 스키마(TSV)와 세 DB의 테이블, 컬럼, 인덱스, 외래 키를 대조한다([domain/schema-mapping.md](domain/schema-mapping.md)). 새로 할 일이 없다 |
| 9d | 동등성 점검: 템플릿과 JS 소스 대조 | 완료 | (1) v1.6 템플릿 242개의 CSS 클래스와 id가 v2 템플릿, 코드, JS에 나오는지 대조해 프로젝트 홈의 이력, 대시보드 탭, 조직용 403 화면, 로그인 화면의 이메일 인증 안내가 빠진 것을 찾아 이식했다. 호출이 없는 v1.6 죽은 템플릿 11개(`debug`, `uservoice`, `partial_forklist`, `experimental`, `allProjectList`, `allOrganizationList`(`_partial` 포함), `partial_pagination`, `partial_paginationForUserList`, `partial_postings`, `user/partial_milestones`)는 이식하지 않는다. (2) `public` 아래 723개 파일은 빌드 도구 `compiler.jar` 하나를 빼고 v1.6과 해시가 같고, LESS 소스(`app/assets`) 20개도 같다 |
| 9e | 동등성 점검: 컨트롤러 액션 대조와 보강 테스트 | 완료 | 라우트가 있는 v1.6 액션 290개의 요청 매개변수 이름 58개가 모두 v2에 있고, 이름이 v2에 언급되지 않은 22개는 철자가 다른 이름이거나 이미 라우트 대조로 확인된 것이었다. 포괄 라우트(`removeTrailer`)가 라우트 대조의 정규화에 가려졌던 것을 찾아 구현했다([web/error-pages.md](web/error-pages.md)) |

## 추가 개발 항목 (v1.6에 없는 기능, D-28)

v1.6 이식과 동등성 점검이 끝난 뒤 진행한다. `next` 브랜치(`com.github.yonaprojects.yona`)에 구현이 있으므로 설계와 시험을 참고하되, 패키지(`dev.yona`)와 v1.6 계약은 `next2` 기준을 지킨다. 착수 전에 순서와 범위를 선생님께 묻는다.

| # | 항목 | `next`의 참고 위치 | 상태 | 비고 |
|---|---|---|---|---|
| X-1 | OAuth2 인가 서버(PKCE, 불투명 토큰, 갱신 토큰 회전, CIMD 클라이언트 등록, 사용자가 만드는 OAuth 앱과 승인한 앱 화면, 관리자 목록과 설정) | `domain/oauth2server`, `oauth2server` | **구현 완료(2026-11-02), 결정은 D-71** | 설명은 [architecture/oauth2-server.md](architecture/oauth2-server.md), 계획은 [oauth2-server-plan.md](architecture/oauth2-server-plan.md). 동적 클라이언트 등록은 하지 않는다(CIMD). X-39(조직 소유 앱), X-40(OIDC), X-6(MCP)의 기반이다 |
| X-2 | 2단계 인증(TOTP, WebAuthn/패스키, 백업 코드, 알려진 기기, 관리자 강제와 해제) | `domain/twofactor` | **구현 완료(2026-11-02), 결정은 D-69** | 설계는 [architecture/two-factor.md](architecture/two-factor.md). 구현 판단은 A-328. 결정: 끌 때 비밀번호나 코드를 다시 묻지 않는다(추가 논의 항목으로 `decisions.md`에 남김) |
| X-3 | SSH 공개키 등록, SSH 접근, 저장소별 Deploy Key | `domain/sshkey`, `ssh` | **구현 완료(2026-11-02, 내장 SSH 서버), 결정은 D-66** | 설명은 [architecture/ssh.md](architecture/ssh.md). 설정 `yona.ssh.mina.enabled`(기본 꺼짐)로 켜는 Apache MINA SSHD, 사용자 SSH 키와 프로젝트 배포 키(읽기 전용 기본), 목록과 추가 두 화면(삭제 버튼만), REST, 감사와 지표, 프로젝트 홈의 SSH 복제 주소. 실제 `ssh`와 `git`으로 복제, 푸시, 읽기 전용 배포 키의 푸시 거부, 보호 규칙 적용을 확인했다. 명령은 저장소 종류별 처리기라 X-7(Mercurial)이 같은 자리에 더한다. **시스템 sshd 연동(X-3b, D-67, `docs/yona-ssh-system-sshd.md`)과 후속(D-68: 위키 저장소 SSH, SSH 키 서명 검증, 빈 저장소 안내와 위키의 SSH 주소, 배포 키 푸시의 사용자 귀속)도 구현했다. SVN의 SSH 접근은 하지 않기로 했다** |
| X-4 | GPG 커밋 서명 검증(키 등록, 커밋과 태그의 Verified 표시, REST, 서명 커밋 필수 규칙) | `domain/gpgkey` | **구현 완료(2026-11-02), 결정은 D-65** | 일곱 단계가 모두 끝났다. 설명은 [architecture/gpg-signing.md](architecture/gpg-signing.md). BouncyCastle 직접 검증, 실제 gpg 2.4.8과 git의 키와 서명으로 호환 확인. 만료와 폐기 키는 지금 기준으로 Unverified. 설정 화면은 목록과 추가 두 화면(삭제 버튼만) |
| X-5 | 개인용 API 토큰(PAT, 범위 지정) | `domain/apitoken` | **구현 완료(2026-10-29)** | D-36: Classic 토큰을 먼저 도입하고 v1.6 사용자 토큰은 레거시 Classic 토큰으로 이관한다(공존이 아니라 이관). 모든 토큰이 사용자 권한을 확인한다(D-20 대체). Fine-grained는 X-5b로 나눈다. 상세는 [architecture/api-token.md](architecture/api-token.md) |
| X-6 | MCP 서버(OAuth2로 인증한 AI 에이전트의 이슈, 풀 리퀘스트, 위키 접근) | `mcp` | **완료(2026-11-02)** | 구현과 문서는 [architecture/mcp-server.md](architecture/mcp-server.md), 결정은 D-72(A-330). 전송은 Streamable HTTP(세션 없음), OAuth 토큰만(PAT 불가), 읽기 전용 범위 `read:repo`, 토큰 대상 검증(RFC 8707). 공식 MCP SDK 클라이언트로 실제 jar에 인가부터 도구 호출까지 확인했다. 실제 Claude Code, Codex, Antigravity 연결은 선생님이 확인한다(절차는 mcp-server.md 7절) |
| X-7 | Mercurial 저장소(hg4j): 코드 보기, 커밋 이력, bookmark와 named branch, HTTP와 SSH 서빙, zip 내려받기, 브랜치 보호와 push 알림·웹훅, 풀 리퀘스트 병합, GPG 서명 | `domain/vcs`, `config/hg`, `HgController` | **진행 중(2026-11-06 D-86 확정). **구현 완료(2026-11-06, 9단계 모두: 공통화 1~4, 읽기 5, 서빙 6, 포크와 가져오기와 REST와 지표 7, 풀 리퀘스트 8, LFS와 데이터 교환과 GPG 9).** 남은 일: hg4j 1.1.3이 Maven Central에 공개되면 `build.gradle.kts`의 임시 수정본 jar 줄(LFS 가져오기 수정, `setLfsServing`, 원본에 맞춘 `TagCommand`)을 지우고 hg4j 버전을 올린 뒤 전체 시험(D-89). PostgreSQL 전체 시험은 2026-11-07에 통과했다(4,837건). 8단계 숨은 참조는 D-87, 9단계 GPG는 D-88로 결정됨. 보고서는 [mercurial-plan.md](architecture/mercurial-plan.md)** | 저장소 종류 변경(v1.6 `changeVCS`)과 `vcs` 열 값에 영향. hg4j는 `io.github.search5.hg4j` 별도 라이브러리 **선행 정리(2026-10-29)**: 착수 전에 `@IsOnlyGitAvailable`(컨트롤러 7곳)과 `VCS_GIT`, `VCS_SUBVERSION` 분기(9개 파일, 39곳)를 "지원하는 VCS를 선언하는 방식"(예: `@SupportedVcs(GIT, MERCURIAL)`)으로 정리한다. 기능마다 Git 전용, Git과 Mercurial, 모든 VCS로 갈리므로 이름만 바꾸는 일이 아니다. 그때까지는 v1.6 이름을 유지한다 |
| X-8 | Git LFS | `.well-known`, `git-lfs` 라우트 | **완료(2026-11-02)** | [git-lfs.md](architecture/git-lfs.md). 실제 git-lfs 3.8.1로 HTTP, 내장 SSH, tus 이어올리기를 확인했다. 후속은 추가 논의 항목(순수 SSH 전송, 객체 정리, Mercurial 주소, 잠금 목록) |
| X-9 | 감사 로그(보안, 관리 이벤트). 엔터프라이즈 수준: 행위자와 IP와 결과와 변경 전후, 해시 사슬, 365일 보존, 내보내기 | `domain/audit` | **구현 완료(2026-11-01), 결정은 D-62, D-63** | 1차 A1~A8과 2차(해시 v2와 git 이벤트 비움 B2, git 전송 이벤트 B3, 웹훅과 syslog 스트리밍과 미전송 기록 보존 보호 B4~B6)를 모두 구현했다. 설명은 [architecture/audit-log.md](architecture/audit-log.md), 구현 판단은 A-318, A-319 |
| X-10 | 관측성: OpenTelemetry(추적), Actuator와 Prometheus(지표), JSON 로그(logstash) | `build.gradle.kts`의 opentelemetry, actuator, micrometer 스타터 (P3-01) | **완료(2026-11-01)** | D-64: 세 신호 모두 OTLP로 Alloy에 보내 Grafana 스택(Prometheus, Tempo, Loki)에서 본다. 앱 포트에는 health만 연다. 상세는 [architecture/observability.md](architecture/observability.md), 설정은 `observability/` |
| X-11 | 풀 리퀘스트 병합 시 `close(s/d)`, `fix(es/ed)`, `resolve(s/d) #번호`로 참조된 이슈 자동 닫기 | `domain/event/PullRequestMergeEventListener` | **완료(2026-10-29)** | `PullRequestWorkflow.accept`가 병합한 뒤 `PullRequestIssueCloser`로 처리한다(A-188). `next` 정규식의 `fixes/fixed` 인식 실패를 바로잡았다 |
| X-12 | 풀 리퀘스트 리뷰 판정(Approve, Request changes, Comment). 같은 리뷰어의 재판정은 이력으로 쌓고 정책 판단에는 리뷰어별 최신 판정만 쓴다 | `domain/pullrequest/PullRequestReview`, `PullRequestServiceImpl` | **구현 완료(2026-10-29)** | D-35: v1.6의 "리뷰 완료/취소"를 GitHub 리뷰 모델에 흡수한다(별개가 아니다). 새 테이블 `pull_request_review`, 상세는 [web/pull-request-review.md](web/pull-request-review.md) |
| X-13 | 브랜치 보호 규칙(브랜치 패턴, 풀 리퀘스트 필수, 필요한 승인 수, 서명 커밋 필수, push 허용 사용자 제한, force push와 삭제 금지, 매니저 우회). push 훅과 병합 검사에 적용 | `domain/branchprotection`, `web/BranchProtectionController`, `domain/vcs/GitPushHooks` | **구현 완료(2026-10-30): X-32부터 X-34의 규칙 묶음이 대체했다.** 남은 것 없음(push 허용 사용자 지정은 X-35의 바이패스 목록이 맡는다). 서명 커밋 필수는 X-4에서 구현했다(2026-11-02) | 브랜치 패턴, 풀 리퀘스트 필수, 필요한 승인 수, force push와 삭제 금지, 관리자 우회는 규칙 묶음(`Ruleset`)에 있다. 아래 설명은 처음 계획이며 클래스 이름은 구현과 다르다. X-4(GPG), X-12(리뷰 판정)가 있어야 해당 규칙이 의미를 갖는다. 새 테이블 필요 **후보(2026-10-29, 보류)**: `refs/heads`, `refs/tags` 밖의 참조(`refs/remotes/*` 등) 푸시를 거부하는 정책. 푸시로 만들 수 있음을 v2와 v1.6에서 확인했다(D-30 7번). 선생님이 나중에 다루기로 하셨다 |
| X-14 | 풀 리퀘스트 담당자(assignee)와 라벨 | `PullRequest.assignee`, `PullRequest.labels` | **구현 완료(2026-10-29), 후속(Ajax, 2단 화면, 모든 목록 표시, 담당자 필터 선택지, 생성 폼 지정)도 구현 완료** | v1.6 `pull_request`에는 담당자, 라벨 열이 없다. 연결 테이블 추가 필요 **설계 결정(2026-10-29)**: [decisions.md](decisions.md) D-34를 본다. 연결 테이블 2개(`pull_request_assignee`, `pull_request_issue_label`)로 구현하고 담당자는 1명까지이며 나중에 여러 명으로 키울 수 있게 한다. 담당자 변경은 타임라인과 알림, 목록에 담당자와 라벨 필터를 넣는다 후속(D-34 14~16): 이슈와 같은 Ajax 방식, 모든 풀 리퀘스트 목록에 표시, 모양 다듬기 |
| X-15 | 풀 리퀘스트 REST API(조회, diff, 리뷰, 타임라인, 병합, 닫기, 다시 열기, 댓글, 리뷰어, 라벨, 담당자, 출발 브랜치 삭제) | `/api/projects/{}/pullrequests/**`, `/api/v1/projects/{}/{}/pull-requests/**` | **구현 완료(2026-10-29), 결정은 D-39** | 계약은 [architecture/api-pull-requests.md](architecture/api-pull-requests.md)이다. 병합 방식(`method`)은 필드만 예약했고 X-28에서 연다. 풀 리퀘스트 만들기와 고치기(`POST`, `PATCH`)와 첨부(`temporaryUploadFiles`)도 포함한다. 라벨 목록 API는 X-19에서 만든다
| X-16 | 엔터프라이즈 SSO(SAML2, 범용 OIDC) 사이트 관리자 설정과 로그인, **SAML SP 키쌍 관리(요청 서명, Assertion 암호화)**, 계정 연결, **사용자 설정의 연결된 계정 화면** | `config/sso`, `domain/sso`, `SsoAdminController`, `OAuth2AccountMergeService` (P3-06) | **완료(2026-11-02, 단계 0~7, 실제 Keycloak 확인 포함)** | 구현 결과는 [enterprise-sso.md](architecture/enterprise-sso.md), 계획은 [enterprise-sso-plan.md](architecture/enterprise-sso-plan.md). 테이블 `oidc_sso_settings`, `saml2_sso_settings`. v1.6 로그인은 그대로 둔다. OpenSAML은 Shibboleth 저장소가 필요하다 |
| X-17 | 프로젝트 위키: 페이지 만들기, 고치기, 삭제, 이력, 비교, 검색, 사이드바. 프로젝트마다 bare git 저장소(`<소유자>/<프로젝트>.wiki.git`)에 저장 | `domain/wiki`, `WikiViewController`, `/api/v1/projects/{}/{}/wiki/**` (P3-42) | **구현 완료(2026-11-01), 결정은 D-60** | v1.6에는 위키 기능이 없다. 단계 W0~W7은 [architecture/project-wiki.md](architecture/project-wiki.md)를 본다. W0~W7 모두 완료 |
| X-18 | Git 태그 전용 화면(주석 메시지와 태거, 종류 배지, 최신순), 삭제 확인, 브랜치 목록과 선택 목록 정리 | `TagViewController`, `TagApiController` (P3-16) | **완료(2026-10-29)** | 결정은 [decisions.md](decisions.md) D-30을 본다. REST는 X-19로 옮겼다. 주석 태그의 커밋 이력 500(P3-40)은 별도로 확인한다 |
| X-19 | 일반 REST 1차: 라벨 목록, 이슈 목록과 검색, 마일스톤 목록, 프로젝트 조회(정보, 내가 속한 프로젝트, 소유자의 프로젝트), 프로젝트 구성원 목록과 담당자 후보 | `*RestApi` 컨트롤러 (P3-09, P3-35) | **구현 완료(2026-10-29), 결정은 D-40** | 계약은 [architecture/api-project-reads.md](architecture/api-project-reads.md)이다. v1.6의 `/-_-api/v1` 외부 API는 그대로 두고 같은 경로에 읽기 API를 더했다. 이슈 댓글은 `GET .../issues/{number}`가 이미 담고 있다 |
| X-29 | 일반 REST 2차(상위 항목): 이슈 쓰기와 게시판 REST를 네 단계(X-29a~d)로 나눠 만든다 | 이슈와 게시판 REST | **구현 완료(2026-10-30), 결정은 D-47** | 추적표는 [api-issue-board-write.md](architecture/api-issue-board-write.md). 네 단계(a~d)가 모두 끝났다. X-30은 2026-10-30에 끝났고 X-31(OpenAPI)이 다음이다 |
| X-29a | 이슈 수정과 이슈에 붙은 것: `PATCH issues/{n}` 부분 수정, `DELETE issues/{n}`, 댓글 목록과 수정과 삭제, 라벨 하나씩 붙이고 떼기, 담당자 더하고 빼기 | `ExternalIssueApiController` 확장 | **구현 완료(2026-10-30)** | v1.6 계약은 유지하고 필드와 경로만 더했다. 외부 이슈 API의 권한 확인 누락(D-36 4번)도 함께 보정했다 |
| X-29b | 게시판 REST: 글 목록과 한 건, 수정, 삭제, 댓글 목록과 수정과 삭제, 라벨 | `ExternalBoardWriteApiController`(새 파일) | **구현 완료(2026-10-30)** | a단계 패턴을 그대로 썼고 D-48(수정은 구성원, 삭제는 작성자와 관리자)을 따른다 |
| X-29c | 라벨과 마일스톤 관리 REST: 고치기와 지우기 | `ExternalProjectWriteApiController`(새 파일) | **구현 완료(2026-10-30)** | 만들기는 이미 있었다. 웹 화면과 같은 권한을 따른다 |
| X-29d | 이슈의 Yona 고유 쓰기 REST: 공유자, 가중치, 지켜보기 | `ExternalIssueExtrasApiController`(새 파일) | **구현 완료(2026-10-30)** | 웹 화면에만 있던 기능의 토큰 버전 |
| X-30 | 일반 REST 3차(상위 항목): 조직, 웹훅, 검색, 포크를 네 단계(X-30a~d)로 나눠 만든다. 쓰기까지 열고 GitHub 전용 범위를 쓴다. 사용자 상태는 이미 있어 뺀다 | 조직, 웹훅, 검색, 포크 REST | **구현 완료(2026-10-30), 결정은 D-49** | 추적표는 [api-org-search-webhook.md](architecture/api-org-search-webhook.md). 네 단계(a~d)가 모두 끝났다. 검증: H2 전체 2964개 통과, PostgreSQL의 X-30 테스트 24개 통과(2026-10-30). X-31(OpenAPI)이 다음이다 |
| X-30a | 조직 REST: 내 조직과 한 건 조회, 구성원 목록과 더하기와 역할 변경과 빼기(나가기 포함), 만들기, 설정 변경, 삭제 | `ExternalOrganizationApiController`(새 파일) | **구현 완료(2026-10-30)** | 범위 `read:org`, `write:org`, `admin:org` |
| X-30b | 웹훅 REST: 목록, 만들기, 삭제 | `ExternalWebhookApiController`(새 파일) | **구현 완료(2026-10-30)** | 범위 `read:repo_hook`, `write:repo_hook`, `admin:repo_hook` |
| X-30c | 검색 REST: 전체, 조직, 프로젝트 | `ExternalSearchApiController`(새 파일) | **구현 완료(2026-10-30)** | 웹 검색과 같은 판정 |
| X-30d | 포크 REST: 사용자나 조직을 대상으로 포크 만들기 | `ExternalForkApiController`(새 파일) | **구현 완료(2026-10-30)** | 웹의 포크 규칙(이름 중복, 조직 관리자만)과 같다 |
| X-31 | OpenAPI 문서(Swagger UI): `/-_-api/v1` 전체 계약 100개 동작을 한국어로 문서화. springdoc 어노테이션으로 코드에서 만들고 누구나 열람한다 | `OpenApiConfig`, `web/api/docs`, 컨트롤러 어노테이션 | **구현 완료(2026-10-30), 결정은 D-50** | 화면 `/-_-api/docs`, 명세 `/-_-api/docs/openapi`. 구조 시험(`OpenApiDocumentTest`)과 모든 REST 시험에 거는 계약 시험(`OpenApiContractSupport`)이 문서의 정확도를 지킨다. 검증: H2 전체 2970개 통과, PostgreSQL 52개 통과. 설명은 [architecture/openapi-docs.md](architecture/openapi-docs.md). springdoc이 끌어온 Jackson 2가 실제 서버의 화면을 끊는 회귀를 일으켜 고쳤다(A-273) |
| X-20 | 프런트엔드 현대화: Vue 3 웹 컴포넌트 위젯 17개(대화상자, 토스트, 드롭다운, 자동 완성, 첨부, 리뷰 폼, 라벨 편집기 등), Tom Select, Flatpickr, CodeMirror 6와 EasyMDE 에디터, jQuery 제거(순수 JS), Hotwired Turbo(이슈 2단 목록) | `static/lib/yona-vue-widgets`, `frontend/`, `yona.issue.Turbo.js` (P3-46, P3-70) | **보류(2026-11-03 선생님 결정)** | 화면 동작은 v1.6과 같게 둔다는 조건이 필요하다. `yona-v2-wiki`의 Vue 위젯 소스 위치 메모(components 저장소)와 이어진다. **에디터 부분은 `next2`에서 별도로 처리했다(2026-11-09, D-105, D-106)**: CodeMirror 6와 EasyMDE는 쓰지 않고 textarea와 서버 미리보기를 유지하며 서식 단추(markdown-toolbar-element)와 멘션(text-expander-element)을 붙였다([architecture/markdown-input-github-way.md](architecture/markdown-input-github-way.md)). X-20의 나머지(Vue 위젯, Tom Select, Flatpickr, jQuery 제거, Turbo)는 그대로 보류 |
| X-21 | 사용 현황 집계(UA 태그를 GA4 gtag로 교체) | `config/analytics/AnalyticsSettings` | **완료(2026-10-28)** | v1.6의 `yona.send-yona-usage` 스위치를 유지하고 UA 태그를 GA4로 교체했다(A-186). 측정 ID는 `yona.analytics.measurement-id` |
| X-22 | 새 프로젝트의 기본 브랜치를 `main`으로 고정 | P3-11 | **완료(2026-10-28)** | `next` 기준으로 `yona.git-default-branch`(기본 `main`)를 `HEAD`로 고정한다(A-187) |
| X-23 | Apache Magpie 연동(이슈 계약 `contract:tracker`, PR 계약 `contract:change-request`) | 구현 결과와 계획은 [magpie-integration-plan.md](architecture/magpie-integration-plan.md), 분석은 [magpie-integration.md](architecture/magpie-integration.md) | **구현 완료(2026-11-03, 단계 0~7, 실제 서버 확인 64/64)** | 선행: X-5(PAT), X-12(리뷰 판정), X-15(PR REST), X-19(일반 REST). 이 항목들이 채우지 못하는 것은 기간·작성자 조건의 이슈 목록과 이슈·PR 활동 조회뿐이다. 착수 전에 D-29를 결정한다 (Magpie에 기여하는 것은 선생님 결정 대기) |
| X-24 | yona CLI(Go 단일 바이너리, `gh`와 비슷한 사용법). **`../yona-cli`에 이미 약 9,300줄이 있다**(이슈, 풀 리퀘스트, 프로젝트, 라벨, 태그, 조직, 검색, 위키, 상태, 별칭, 설정, 관리자). 이전 구현(`yuna`)의 `/api/v1/...`을 부르도록 만들어져 있어, 이 서버의 `/-_-api/v1` 계약에 맞추는 일이 남았다 | `../yona-cli`의 `internal/api`, `cmd` | **구현 완료(2026-10-30, 위키 명령은 X-17 뒤에 `cmd/wiki.go`로 추가), 결정은 D-51** | 방침: CLI의 클라이언트(`internal/api`)를 `/-_-api/v1` 경로와 응답 모양으로 바꾸고 서버는 바꾸지 않는다. 서버에 대응 REST가 없는 명령은 목록을 만들어 따로 정한다([architecture/cli-api-gap.md](architecture/cli-api-gap.md)) |
| X-37 | CLI를 위한 서버 보강(상위 항목): 대응 REST가 없던 명령의 서버 기능과 기기 인증을 열 단계(X-37a~k)로 만든다 | 외부 REST, 기기 인증 | **구현 완료(2026-10-30), 결정은 D-52** | 근거는 [architecture/cli-api-gap.md](architecture/cli-api-gap.md). 하위 단계 a~j가 모두 끝났거나 결정되었다(h는 서버 변경 없음). k(문서)는 단계마다 한다. 위키 명령은 X-17이 끝난 뒤 추가했다(`wiki attach`, `wiki file` 포함) |
| X-37a | 프로젝트 REST: 만들기(`POST /user/projects`, `POST /orgs/{org}/projects`), 설정 바꾸기(`PATCH $P`), 지우기(`DELETE $P`) | `ExternalProjectManageApiController` | **구현 완료(2026-10-30)** | 일반 사용자도 토큰으로 프로젝트를 만든다. 추적표는 [architecture/api-cli-support.md](architecture/api-cli-support.md) |
| X-37b | 프로젝트 구성원 REST: 더하기, 역할 바꾸기, 빼기 | `ExternalProjectMemberApiController` | **구현 완료(2026-10-30)** | 조회(`GET $P/members`)는 있다 |
| X-37c | Git 태그 REST: 목록, 만들기, 지우기 | `ExternalTagApiController` | **구현 완료(2026-10-30)** | |
| X-37d | 이슈 옮기기 REST: `POST $P/issues/{n}/transfer` | `ExternalIssueExtrasApiController` | **구현 완료(2026-10-30)** | |
| X-37e | 사용자 현황 REST: `GET /user/status`, 내 이슈의 `commented`, `shared` 필터 | `ExternalUserStatusApiController` | **구현 완료(2026-10-30)** | |
| X-37f | 풀 리퀘스트 검색: `GET /search/pullrequests`와 프로젝트 목록 조건 | `ExternalSearchApiController`, `ExternalPullRequestApiController` | **구현 완료(2026-10-30)** | 웹 검색은 그대로 둔다 |
| X-37g | 사이트 백업 REST: `GET /admin/export`, `POST /admin/import` | `ExternalSiteBackupApiController` | **구현 완료(2026-10-30)** | |
| X-37h | 리뷰어 추가와 삭제 | `ExternalPullRequestWriteApiController`(기존 `requestedreviewers`) | **완료(결정: 서버 변경 없음, 2026-11-10 확인)** | CLI는 `pr edit --add-reviewer/--remove-reviewer`(GitHub 방식), D-52 |
| X-37i | 기기 인증(Device Flow): 서버의 기기 코드 발급, 승인 화면, 토큰 발급 | `domain/deviceauth`, `ExternalDeviceAuthApiController`, `DeviceAuthorizationController` | **구현 완료(2026-10-30)** | 새 테이블 `device_authorization`. X-1(OAuth2 서버)과 별개이다 |
| X-37j | CLI(`../yona-cli`): 클라이언트를 `/-_-api/v1`에 맞춤, 새 명령, 기기 로그인, Git 자격 증명 도우미 | `../yona-cli` | **구현 완료(2026-10-30)** | 위키 명령은 서버 위키(X-17)가 끝난 뒤 추가했다 |
| X-37k | 문서: 각 단계의 REST를 OpenAPI와 추적표에 올린다 | `web/api/docs` | **완료(상시: `OpenApiDocumentTest`가 강제, 2026-11-10 확인)** | 구조 시험이 빠지면 실패한다 |
| X-38a | Bootstrap 전환 0단계: 전 화면 확인 도구(구조 검사 시험, 고정 데이터, 화면 목록 121개, 순회 캡처, 상호작용 점검, 전후 비교) | `e2e/`, `LegacyBootstrapLintTest` | **구현 완료(2026-10-30)** | 기준 순회 `pre-bs5`: 121/121 통과(알려진 v1.6 JS 오류 1건 `compare`). 문서는 `architecture/bs-migration-harness.md` |
| X-38b | Bootstrap 2→3(**공식 이전 안내서 "Migrating from v2.x to v3.0"를 기준으로 항목별로 대응**, 선생님 지시): 마크업을 안내서대로 바꾸고 BS3 CSS와 JS(3.4.1)를 들이고 호환 층으로 v1.6 모양을 유지 | 템플릿 192개, `app/assets/stylesheets/less/_bs3-compat.less`, JS 번들, `build-tools/minify-js.sh` | **구현 완료(2026-10-30)** | 전 화면 순회 121/121 통과, 상호작용 점검이 기준과 같음, 캡처 평균 차이 0.7%(중앙값 0.1%). 점검표 `architecture/bs3-migration-checklist.md`, 결정 A-284~286. 태그 `bs3` |
| X-38c | Bootstrap 3→4: 자체 LESS를 SCSS로 재작성(**1구간 완료**: LESS와 CSS 동일, 순회 픽셀 차이 0, 태그 `scss`), 이어서 flexbox 그리드(`col-md`→`col-lg`), `panel`/`well`→`card`, `label`→`badge`, Glyphicons 대체, 폼과 내비게이션과 모달 변경, 호환 층 흡수, BS4 SCSS 도입(공식 이전 안내서 "Migrating to v4" 기준) | 템플릿, `app/assets/stylesheets`, 스타일 빌드 | **구현 완료(2026-10-31)**, 태그 `bs4` | 점검표와 호환 층 의존 장부는 `architecture/bs4-migration-checklist.md`. 순회 121/121, 기준 대비 새 문제 0, 평균 픽셀 차이 0.3%, 시험 3027개 통과. **호환 층에 남은 BS2 규칙(단추 그룹, 표, 알림, 그리드, 요소 기본값 약 1,100줄)은 BS5에서 변수와 마크업으로 옮길지 정한다** |
| X-38d | Bootstrap 4→5(**공식 이전 안내서 "Migrating to v5"를 기준으로 항목별 대응**): 5.3.8 SCSS와 번들, `data-bs-*` 속성, 유틸리티 이름(`float-start`, `ps-*`, `bg-*`, `rounded-pill`, `text-body-secondary`), `btn-close`, `form-group` 폐지, 체크박스와 표와 그리드의 BS5 기본 모양 보정 | 템플릿, 자체 JS, 서버가 만드는 HTML, `app/assets/stylesheets` | **구현 완료(2026-10-31)** | 점검표는 `architecture/bs5-migration-checklist.md` |
| X-38e | 자체 JS의 BS 플러그인 호출을 BS5 API로: `modal`, `popover`, `tab`, `tooltip` 호출을 `bootstrap.*`로, 상태 단추는 자체 함수로 | 자체 JS 약 25개, 템플릿 안의 스크립트 | **구현 완료(2026-10-31)** | 사이트 자체 코드의 jQuery는 그대로(BS 자체는 jQuery 없이 동작) |
| X-38f | 정리: 호환 층(`_bs3-compat.scss`)을 영역별로 이관해 지운다(D-54), 옛 BS 자원과 `less.js` 삭제, `affix`의 `sticky` 대체(A-296), 문서와 위키 갱신 | 정적 자원, 스타일, 위키 | **완료**(2026-10-31) | 호환 층 2,199줄에서 0줄. 영역별 이관 결과는 `architecture/bs4-migration-checklist.md`의 이관 결과 표. 남은 BS2 의존은 전역 `content-box`, 줄 높이 20px 상속, 맨 `input` 모양이다 |
| X-38g | 글자 크기와 줄 높이의 상대 단위화(화면 유지, 1순위, **구현 완료(2026-10-31), 방법은 `rem`(A-298)**): 줄 높이를 비율로(`--bs-body-line-height: 20px` 덮어쓰기를 걷고 규칙마다 `20/글자 크기`를 명시한 뒤 `$line-height-base` 비율만 쓴다), 자체 SCSS의 px 글자 크기(260개 규칙)를 rem으로(`px/16`) | 스타일 | **구현 완료(2026-10-31, A-298)**. 확인(2026-11-10): `line-height`의 px는 0곳이고 `font-size`의 px는 `font/icons.scss`(아이콘 글꼴 크기) 211곳만 남았다 | 목적은 해상도와 사용자 글자 크기 설정에 대한 대응이다(선생님 지적). 기본 크기에서는 `style-diff` 차이 0, 추가로 루트 글자 크기를 키운 조건에서도 순회해 줄 겹침과 넘침을 찾는다. 가로폭과 여백의 px는 X-38h와 함께 다룬다 |
| X-38h | 구조 정리(화면 유지, 2순위): 전역 `content-box`를 `border-box`로(h-1, **구현 완료(2026-10-31), A-299**), 그다음 float 그리드를 flex 그리드로(h-2, **구현 완료(2026-10-31), A-300**) | 스타일 | **h-1, h-2 완료**(D-55) | 자체 CSS 8천 줄의 폭 계산을 함께 고쳐야 하므로 위험이 크다. 전 화면 비교 도구로 확인 |
| X-38i | BS2 고유 모양의 현대화(디자인 변경, 3순위): 그라데이션 단추와 글자 그림자, 안쪽 그림자, 940px 고정 폭, 반응형 | 스타일 | **완료, select2 4.x만 보류(D-56, 2026-10-31)** | BS2 모양을 유지하지 않고 BS5 고유 모양을 쓴다. 컴포넌트마다 전후 캡처를 보여 드린다. 완료: 단추(X-38i-1, A-302), 폼 컨트롤(X-38i-2, A-303, A-304). 완료: 알림·배지·표·탭(X-38i-3, A-305). 완료: 그림자·그라데이션 정리(X-38i-4, A-307). 완료: BS5 반응형 그리드(X-38i-5, D-57, A-308). 완료: 테마 색 BS5 기본 전환(D-58, A-310). 남음: select2 4.x(2026-10-31 선생님 지시로 보류) |
| X-39 | 조직의 OAuth 앱(조직 설정 탭: 앱 목록, 만들기, 수정, 비밀 값 재발급, 지우기, 승인 현황), **앱 소유자 이전**(사용자와 조직 사이), **OAuth app policy**(조직이 서드파티 앱의 접근을 제한), 조직 삭제 때의 정리, 승인 화면의 소유자 표시, 조직 감사 로그 연결 | `domain/oauth2server`(소유자 종류 `ORGANIZATION`), 조직 설정 화면 | **완료(2026-11-02)** | 조직 설정 탭 "OAuth 앱"과 "OAuth 앱 정책", 소유자 이전, 조직 삭제 정리, 승인 화면 경고까지 끝냈다(D-78, A-338~340). 승인 요청 흐름과 GitHub식 설정 사이드바 재구성은 추가 논의 항목이다 |
| X-40 | OIDC 제공자("Sign in with Yona"): ID 토큰, `/userinfo`, `/.well-known/openid-configuration`, `openid`, `profile`, `email` 범위 | `domain/oauth2server` 확장 | **완료(2026-11-02, 결정은 D-77)** | 설명은 [architecture/oauth2-server.md](architecture/oauth2-server.md)의 "OIDC 제공자". 서명 키는 운영자가 정한다(`yona.oauth2.oidc.signing-key-file`, 자동 생성은 추가 논의), `sub`은 사용자 번호, `email_verified`는 보내지 않는다, 사용자 정보 주소는 `/oauth2/userinfo`, 로그아웃 연동은 추가 논의. 공개된 OIDC 클라이언트 라이브러리로 확인했다 |
| X-41 | MCP 도구 확장: 풀 리퀘스트 만들기, 리뷰 판정, 닫기와 다시 열기, 병합, 이슈 삭제, 이슈 댓글 삭제 | `mcp` | **완료(2026-11-02)** | D-74, D-75, D-76. 삭제는 이슈와 이슈 댓글만 넣었다(웹 화면과 같은 규칙). R-76(병합 중 표시 고착)은 선생님 승인으로 개선했다 |
| X-42 | SSO의 IdP 그룹과 역할 매핑: 로그인 때 IdP의 그룹 클레임(OIDC)이나 속성(SAML)을 읽어 사이트 관리자, 조직 구성원과 역할, 프로젝트 구성원을 정한다 | `domain/sso` | **대기(2026-11-02 선생님: 일단 로드맵에 넣고, 할지 말지는 나중에 결정)** | 선행 X-16. `next` 브랜치에도 구현이 없다(조사 2026-11-02). 사이트 관리자 승격은 가로채기 위험이 커서 설계 때 규칙(허용 그룹 이름, 강등 여부, 수동 변경과의 충돌)을 정해야 한다. 계획은 [enterprise-sso-plan.md](architecture/enterprise-sso-plan.md) 7절 |
| X-43 | 인증 구조의 스프링 시큐리티 표준화: `UserDetailsService`, `LegacyPasswordEncoder`(v1.6 해시를 그대로 검증), 인증 관리자와 `AuthenticationProvider`(비밀번호, LDAP, 토큰, 이후 SSO), v1.6 쿠키 형식의 `RememberMeServices`, 마지막에 `formLogin` 설정으로 로그인 입구 교체 | `security`, `config/SecurityConfig`, `LoginController`, `LocalAuthenticator`, `YonaAuthenticationFilter` | **진행(2026-11-10 선생님: "x-43 진행하자", D-129).** 계획서를 현재 코드에 맞게 갱신함(2026-11-10). **구현 완료(2026-11-10): 1단계, 2단계(2-1~2-3), 3단계(`formLogin` 대신 인증 필터)**. 2-4(인증 이벤트)와 2-5(체인 분리)는 하지 않았다. 전체 시험 대기 | 계획은 [architecture/auth-standardization-plan.md](architecture/auth-standardization-plan.md). 사용자에게 보이는 변화 없이 개발자 쪽 이득(표준 인증 이벤트로 감사와 잠금 일관, `oauth2Login`과 `saml2Login`이 같은 인증 관리자에 붙음). **데이터 형식은 바꾸지 않는다**: 비밀번호 해시(`password`와 `password_salt` 열, Shiro SHA-256 1024회)와 로그인 유지 쿠키(`yobi.token`)를 그대로 지켜 마이그레이션 명령이 필요 없다. 3단계로 나눈다(1 비밀번호 확인을 인증 관리자 뒤로, 2 토큰과 쿠키를 제공자와 `RememberMeServices`로, 3 `formLogin`). 해시 강화(**argon2id로 결정**, 2026-11-02)는 되돌릴 수 없어 별도 선택 기능으로 둔다(decisions.md "재결정 대기" 절). **해시 강화의 선행은 이 항목의 1단계와 2단계이다**: 비밀번호 해시를 만들고 확인하는 곳이 8곳(`LocalAuthenticator`, `AccountService`, `PasswordResetController`(둘), `SignupService`, `SiteSecret`, `LdapAuthService`(확인과 동기화), `InitialDataLoader`)이고 로그인 유지 쿠키는 이미 서명 방식으로 재설계되어(R-05 해결) 해시를 값에 담지 않으나 서명 재료로 `user.password`를 쓴다. 해시를 한 곳으로 모은 뒤에 알고리즘을 바꿔야 빠뜨리지 않고, 바꾸면 기존 쿠키는 무효가 된다. 선행은 없고 X-16과는 독립이다 |
| X-44 | 조직 팀: 팀(공개·비밀, 중첩)과 구성원·팀 역할, 팀에 프로젝트 권한(Read, Triage, Write, Maintain, Admin), 팀 멘션과 알림 설정, 팀을 리뷰어로 요청, 규칙 묶음 바이패스·푸시 허용 대상, REST와 `yona team`, Magpie `add_team_member` | `domain/team`(신규), `AccessControl`, `MentionService` | **v2.1.0 대상. 계획 작성 완료, v2.0.0 작업이 끝난 뒤 착수(2026-11-03, 범위는 GitHub, D-84)** | 계획은 [architecture/team-plan.md](architecture/team-plan.md). 새 테이블 3개(`team`, `team_user`, `team_project`). 가장 위험한 부분은 접근 제어 변경이라 팀이 없을 때 기존 시험이 모두 그대로 통과해야 한다. 코드 소유자(CODEOWNERS)는 X-45로 분리 권장. X-35의 바이패스 구조와 X-42의 그룹 매핑과 연결된다 |
| X-46 | Cubrid 11.4와 MSSQL 지원: 드라이버와 Hibernate 방언(`hibernate-community-dialects`) 추가, 두 데이터베이스에서 스키마 생성과 전체 시험 통과, Testcontainers 시험 환경, 설정 안내 | `build.gradle.kts`, `support/TestDatabase`, `schema` | **MSSQL 완료(전체 시험 통과), Cubrid는 전체 연속 4,379건 중 7건 실패(스키마 메타데이터 조회 중단, 단독 통과), `this.con is null`은 해결(2026-11-04, D-85)** | Cubrid는 11.4만 지원한다. 지원 범위는 D-04가 H2, PostgreSQL, MariaDB로 정했던 것을 선생님이 넓히셨다. hg4j(X-7)와는 독립 |
| X-47 | 저장소 종류와 무관한 커밋 후 처리와 SVN 커밋 후 처리: (1) 푸시와 커밋 뒤 본문(감시자 알림, push 웹훅, 이슈 참조 이벤트)을 한 서비스로 모으고 종류별 리스너는 커밋 수집과 중립 표현 변환만 한다. (2) HTTP SVN 커밋 뒤에도 같은 세 가지를 처리한다(v1.6에 없는 새 기능). | `repository/PostReceiveJobs`, `GitHooks`, `domain/notification`, `web/svn` | **대기(2026-11-06 선생님 지시로 추가)**. (1)은 [mercurial-plan.md](architecture/mercurial-plan.md) 6.3절의 푸시 이벤트 중립화와 같은 작업이라 **X-7 선행 정리 3단계와 함께 한다.** (2)는 새 기능이라 착수 전에 선생님께 결정을 묻는다(범위, 알림 대상). | 참고: `next` 브랜치 대상 upstream PR #849(이슈 참조 기록 공통화), #854(SVN 커밋 후 처리), #855(세 종류 공통 서비스 `CommitPostProcessingService`, 작성자와 커미터를 모두 담는 중립 커밋 `PushedVcsCommits`). 모두 `next`(`com.github.yonaprojects.yona`) 구현이라 `next2`에 그대로 병합하지 않고 설계만 참고한다. PR 본문만 읽었고 변경 코드는 착수 때 읽는다. 이 항목은 v1.6의 기존 동작과 계약을 바꾸지 않는다(D-28) |
| X-48 | **소셜 로그인 일체 제거(다음 최우선 항목)**: 외부 제공자(GitHub, Google, Facebook 등)로 로그인하고 가입하는 기능을 모두 없앤다. 대상은 로그인 화면의 소셜 단추, 소셜 로그인 처리(`web/social/SocialLoginController`, `domain/social`), 소셜 가입과 계정 연결 서비스(`SocialAccountService`), 설정의 연결된 계정 화면(`user/edit-linked-accounts`)과 그 컨트롤러, 관련 설정 키와 메시지, 시험과 문서이다. | `web/social`, `domain/social`, `web/user/LoginFormController`, `LoginController`, `LinkedAccountsController`, `UserPageAssembler`, `SiteUserController`, `templates/user/edit-linked-accounts.html`, `templates/restricted.html` | **구현 완료(2026-11-10), 결정은 D-127**. 구현 때 정한 것은 아래 권장안이다(소셜 계정 테이블은 남기고, SSO와 LDAP이 쓰는 공유 부분은 건드리지 않았다). 착수 전 확인 사항이던 것: (1) v1.6 DB 테이블은 같아야 하므로 소셜 계정 테이블(`user_credential`, 연결 계정 테이블)은 **남기고 코드만 없앨지**, 테이블까지 없앨지. (2) 소셜로 가입했던 기존 사용자의 처리(이메일 비밀번호 재설정으로 비밀번호를 만들어 계속 쓰게 할지). 소셜 계정으로만 로그인하던 사용자는 비밀번호가 무작위라서 로그인할 수 없게 된다. (3) SSO(OIDC, SAML)와 LDAP은 `LinkedAccount`, `SsoAccountService`, `generateLoginId`를 함께 쓰므로 **범위 밖으로 두고 공유 부분은 남긴다**는 구분이 맞는지. (4) 이 항목은 v1.6 기능을 없애는 것이라 `v16-retained-defects.md`가 아닌 `differences-from-v16.md` 장부에 기록한다. 결정은 D-127 |
| X-49 | **토큰 범위 판정을 스프링 시큐리티 인가로(선택, 수준 1~2 구현 완료 2026-11-10: 권한 변환과 권한 기준 판정, 필터 체인에 거는 방식은 동작이 달라져 인터셉터에 유지, A-444)**: 수준 1은 `YonaAuthentication`이 `SCOPE_` 표준 권한을 갖게 하고(포함 관계를 펼친 값, 동작 불변), 수준 2는 규칙표(`ApiScopeRules`)를 데이터로 쓰는 `AuthorizationManager`를 `authorizeHttpRequests`에 걸고 401, 403, 감사를 표준 진입점, 거부 처리기, 인가 거부 이벤트로 옮기며, 수준 3은 `ApiScopeInterceptor`를 지우고 OAuth 토큰, MCP, Git HTTP의 범위 확인도 같은 경로로 모은다 | `security/ApiScopeInterceptor`, `ApiScopeRules`, `YonaAuthentication`, `McpAccessFilter`, `config/SecurityConfig` | **대기(2026-11-10 선생님 승인: 선택 항목으로 올림, D-130)**. X-43 2단계(토큰 제공자가 `YonaAuthentication`을 내는 것) 뒤에 이득을 보고 정한다. 수준 1은 동작이 안 바뀌어 X-43 2단계와 함께 할 수도 있다 | 규칙표가 기준이라 `OpenApiDocumentTest`의 범위 대응 확인을 유지해야 한다. 응답 본문, 상태 코드, 감사 기록의 내용과 횟수가 그대로여야 하고 `External*ApiTest`가 수정 없이 통과해야 한다. 메서드 보안(`@PreAuthorize`)은 규칙이 흩어져 쓰지 않는다. 포함 관계를 `RoleHierarchy`로 표현할 수 있는지는 착수 때 확인한다 |
| X-50 | **토큰 인증을 표준 자원 서버 필터로(보류, 하지 않기로 함)**: 표준 `BearerTokenAuthenticationFilter`는 인증 실패 때 체인을 잇지 않아 "틀린 토큰은 익명으로 진행"이라는 현재 계약이 깨진다(A-445). 이미 표준인 부분(인가 서버, 인증 판정, 범위)은 그대로 둔다 | | | |
| X-51 | **Incoming webhook(수신 웹후크)**: 외부 시스템이 프로젝트에 이슈 만들기와 기존 이슈에 댓글 달기를 비밀 값 하나로 요청할 수 있게 한다. 프로젝트 설정의 "수신 웹후크"(이름, 비밀 값, 동작), 비밀 값은 헤더로 받고 항목별 끄기와 지우기, 감사 로그, 요청 횟수 제한. 화면은 목록과 추가 두 화면(A-545 방식) | 새로 만든다(`domain/webhook`의 수신 쪽, `web/project`, REST 문서) | **로드맵 등재(2026-11-13, 결정은 D-142), 구현 착수 전** | 형식 변환기(Slack, GitHub 형식)는 범위에서 뺀다 |
| X-52 | **비교 화면의 커밋 목록과 요약 줄의 커밋 개수(보류, 선생님: "필요하긴 한데 일단 뺌")**: 비교 범위(두 쪽 사이)에 들어 있는 커밋을 번호, 제목, 시각으로 나열한 표를 비교 화면 맨 위 요약 줄 아래에 둔다. 브랜치나 태그 이름이 붙은 커밋은 이름도 보인다. 요약 줄에는 "커밋 N개"를 더한다. 시안은 `shots/p14-code/mock/compare-mock.png`(데모의 커밋 2개, 파일 2개). 이미 있는 것: 코드 주고받기 만들기 화면의 "커밋" 탭 표(`pullrequest/partial-merge-result`), 비교 화면의 변경 요약 줄과 되돌아가는 링크(A-572). 필요한 일: 임의의 두 리비전 사이 커밋을 구하는 서버 기능(`CommitHistoryService`에는 한 커밋 조회와 이력만 있고 범위 조회는 없다, Git은 JGit `RevWalk`로 새 커밋을 구하면 된다), Subversion의 범위 로그 | 새로 만든다(`web/code/CommitViewController.compare`, 위 서버 기능, `code/compare.html`, `code/compare-svn.html`) | **보류(2026-11-13, A-572)**, 착수 전 | 시안만 있고 구현은 없다. 지시가 있으면 시험부터 쓴다. 되돌아가는 링크와 변경 요약 줄은 이미 구현했다 | 
| X-53 | **데이터 가져오기에 TUS 이어 올리기(D-144)**: 사이트 관리의 데이터 가져오기를 TUS(tus.io) 규약으로 올리게 한다. 끊겨도 이어 올리고 진행률을 보인다. 서버 라이브러리(tus-java-server), 조각은 설치 디렉터리 아래 정기 정리, 올리기 완료 후 자동 가져오기, 최대 10GB로 확정(D-144). | 사이트 관리, 업로드 | 구현 완료(2026-11-15, A-597) | 선생님 제안(2026-11-15). 파일을 풀어 데이터를 교체하는 로직과 요청 계약은 그대로이다 |
| X-54 | **관리자가 재설정한 비밀번호를 이메일로 보내기**: 사용자 관리의 "비밀번호 재설정"이 만든 임의 비밀번호를 화면에 한 번 보이는 지금 방식에 더해, 그 사용자에게 이메일로 보내는 선택지를 둔다. 임시 비밀번호를 쓰고 첫 로그인 때 바꾸게 할지는 착수 때 정한다. | 사이트 관리, 메일 | 로드맵에만 등재 | 선생님 지시(2026-11-15: "이메일로 비밀번호 보내기 기능이 있어야 할 것 같은데 로드맵에만 넣어줘") |
| X-55 | **Subversion SSH 접근(`svn+ssh`)**: 내장 SSH 서버로 SVN 저장소를 체크아웃, 갱신, 커밋한다. Git SSH와 같은 사용자별 권한(구성원, 읽기만 되는 사용자, 읽기 전용과 쓰기 배포 키). 서버에 Subversion 패키지가 필요 없도록 `ra_svn` 프로토콜을 직접 구현한다 | `ssh`, `ssh/svn` | **구현 완료(2026-11-16, 브랜치 `ra-svn`, 결정은 A-615)** | 설명은 [architecture/svn-ssh.md](architecture/svn-ssh.md). 시험 68건(부호기 11, 읽기 6, 체크아웃과 갱신 6, 커밋 9, 나머지 작업과 잠금 10, 깊이와 리비전 속성과 svnsync와 삭제 조회와 압축 15, 실제 SSH 접근 11)과 중계 시험 4건. 시스템 `svnserve`가 처리하는 명령은 모두 지원한다(A-616). 진짜 `svnserve`의 대화를 녹화해 형식을 맞췄다. v1.6에는 없는 추가 항목이라 기존 요청 계약은 바꾸지 않는다 |
| X-56 | **이슈 검색 개선 1: 범위 통일과 단어 AND 검색(DB)**: 통합 검색(`SearchService`)의 이슈는 제목과 본문만 보고, 이슈 목록 필터(`IssueSearchService`)는 제목, 본문, 댓글을 본다. 한쪽으로 통일한다. 검색어를 띄어쓰기로 나눠 모두 포함하는(AND) `LIKE`로 바꾼다(`로그인 오류`가 떨어진 단어로도 찾아진다). 인덱스와 새 의존성 없이 6종 DB 모두에서 된다 | GitHub 이슈 #843, #742, #354 | **제안(착수 전 결정 필요, D-28)** | 설명은 [architecture/search-engine-design.md](architecture/search-engine-design.md). v1.6은 구문 전체를 `LIKE`로 비교했으므로 v1.6 요청과 응답 계약은 바꾸지 않고 결과만 넓히는 추가 항목이다 |
| X-57 | **이슈 검색 개선 2: 제목 머리말 필터(`titleHead`)와 `LIKE` 이스케이프 보강**: 목록에서 머리말(`[머리말]`)을 클릭하면 검색어가 아니라 `titleHead=머리말` 필터로 보낸다(칩으로 표시, 검색어와 조합). 기존 `filter=[xxx]` URL은 호환. `LikeEscape`에 `[`를 더한다(SQL Server의 `LIKE`는 `[]`를 문자 클래스로 읽는다. 지금 코드는 `\`, `%`, `_`만 이스케이프한다) | GitHub 이슈 #843, #354 | **제안(착수 전 결정 필요, D-28)** | X-56과 독립이다. MSSQL의 머리말 검색 오류는 코드를 읽은 판단이고 MSSQL에서 실행해 확인하지는 않았다 |
| X-58 | **검색 색인 쓰기 경로의 엔진 무관한 틀(DB 쓰기에서 엔진 갱신까지)**: 검색 엔진은 선택 기능이다. 이슈와 댓글이 생성, 수정, 삭제, 프로젝트 이동, 프로젝트 삭제, 프로젝트 공개 범위가 바뀔 때 같은 트랜잭션에서 "무엇이 바뀌었는가"(종류, 번호, 프로젝트, 동작, 시각)를 아웃박스에 적고(엔진과 무관), **반영 작업이 그 시점의 최신 내용을 DB에서 읽어 검색 엔진에 색인하거나 지운다.** 순서와 중복 반영(멱등), 실패 재시도, 삭제한 것이 되살아나지 않게 하기, 전체 재색인과 대조 작업까지 포함한다. 일괄 경로(`ProjectDeletionService`의 JPQL과 네이티브 삭제, `IssueMassUpdateService`, `ProjectTransferService`, `DataService`의 JDBC 가져오기, v1.6 이관)는 JPA 이벤트를 거치지 않으므로 이음새를 명시적으로 부른다 | GitHub 이슈 #843 | **제안(착수 전 결정 필요, D-28)** | 선생님 의견(2026-11-17): 색인을 제대로 쓰려면 DB 쓰기 지점에 인터페이스가 먼저 필요하고, 과제는 DB에 쓰는 것에 더해 검색 엔진을 갱신하는 데까지이다. 이 항목은 엔진과 무관한 틀(쓰기 이음새, 반영 작업, 엔진 어댑터의 쓰기 쪽 규약 `색인하기`와 `지우기`, 재시도, 재색인과 대조)까지이고 **시험용 메모리 엔진**으로 검증한다. 실제 엔진과 DB의 일치는 각 엔진 항목(X-60, X-61)이 같은 적합성 시험으로 확인한다. **`backend=db`(기본)에서는 아웃박스에 쓰지 않고 반영 작업도 돌리지 않는다**(기존 설치에 비용이 없다). 엔진을 켤 때는 전체 색인을 먼저 만든 뒤 켠다. 착수 첫 단계는 쓰기 경로 지도(이슈, 댓글, 게시글, PR 전부)이다. 알림, 웹훅, 감사 기록 정리에도 쓸 수 있다 |
| X-59 | **검색 쪽: 엔진 선택 구조와 질의**: `yona.search.backend`(`db` 기본)로 구현체를 고른다. 어댑터의 `검색하기`, 공통 질의 모델(여러 단어 AND, 구문, 필터, 정렬), 결과 모양을 엔진 위의 공통 계층에 둔다. **비공개는 결과, 건수, 집계, 스니펫 어디에도 걸리지 않아야 한다(필수)**: 공개 범위를 질의의 필터 조건으로 걸고 돌려주기 전에 DB로 다시 확인하며, 공개 범위 변경이 반영되기 전의 프로젝트는 결과에서 뺀다(설계 문서 4.1) | GitHub 이슈 #843 | **제안(X-58과 함께 설계, 구현은 X-60과 같이)** | 공통 질의 모델은 두 번째 엔진을 붙일 때 일반화한다(처음부터 모든 엔진을 가정하지 않는다). 인덱스 구조는 논리 문서 하나와 엔진별 물리 매핑으로 나누고 문서 단위(이슈와 댓글 분리)는 결정이 필요하다(설계 문서 4.2) |
| X-60 | **내장 Lucene 검색(선택 기능)**: X-58의 적합성 시험을 통과해야 하는 첫 실제 엔진이자 X-59의 첫 검색 구현이다. 한국어는 Nori 분석기, 단일 노드 전용. 관련도순(제목 가중치), 스니펫과 강조, 여러 단어 AND, 따옴표 구문, 상태, 담당자, 라벨, 마일스톤 필터와 조합. 기본값에서는 꺼 두어 기존 설치에 메모리와 디스크 부담이 없다 | GitHub 이슈 #843(제안자의 PoC) | **제안(X-56의 단어 AND로 부족하다는 수치가 나오면)** | 제안자의 PoC 수치(LIKE 17.79%, Lucene 95.78%)는 구문 전체 `LIKE`와의 비교이고 단어 AND `LIKE`와의 비교는 없다. 그 비교를 먼저 받는다 |
| X-61 | **OpenSearch와 Elasticsearch 어댑터(선택 기능)**: API가 거의 같아 어댑터 하나로 묶는다. 대용량과 다중 노드용. Solr는 API가 달라 요청이 있을 때 외부 기여로 받는다 | GitHub 이슈 #843 | **제안(X-60 뒤)** | |
| X-62 | **v1.6의 H2 설치용 이관 스크립트(`scripts/migrate-v16-h2.sh`)**: 옛 H2 파일(H2 1.3)을 v1.6을 켜지 않고 직접 읽어 데이터 가져오기 형식의 JSON으로 바꾸고(`tools/h2-export`), `migrate-v16.sh`가 v2 DB에 넣은 뒤 데이터 보정을 돈다. 옛 사용자 토큰도 옮긴다 | `domain/upgrade`, `scripts`, `tools/h2-export` | **구현 완료(2026-11-17, 결정은 A-624)** | 설명은 [architecture/v16-migration-guide.md](architecture/v16-migration-guide.md). 시험 18건(변환기 7, 가져오기 단계 5, 스크립트 입력 검증 6). 실제 v1.6 1.16.0 H2 파일로 리허설했다 |
| X-63 | **운영체제별 설치 스크립트(Linux, Mac은 셸, Windows는 PowerShell)**: 서버에 v2를 효율적으로 설치하는 도구를 한 쌍으로 제공하고 대화형으로 묻는다. **(1) 버전 선택**: GitHub 릴리스에서 v2 이상만 골라 최근 4건을 보여 주고 그중에서 고른다(v1.x는 보이지 않는다). 내려받은 jar는 체크섬을 검증한다(GitHub 릴리스 API가 자산마다 주는 `sha256` digest와 견준다. 2026-11-20에 `v2.0.0-rc1`로 확인했고 릴리스에 별도 체크섬 파일은 없다). `rc` 같은 사전 배포 이름과 GitHub의 사전 배포 표시는 목록에서 빼지 않고 `(rc)` 등으로 표시한다(지금 v2 릴리스가 `v2.0.0-rc1` 하나뿐이라 빼면 목록이 빈다). **(2) DB 종류 선택**: PostgreSQL(권장 표시), MariaDB, Cubrid, SQL Server, H2 중에서 고른다. H2는 파일 DB로 바로 설치하고 나머지는 아래 (3)을 거친다. **(3) DB 설치 여부와 방법 선택**: 먼저 DB를 **함께 설치할지, 따로 설치되어 있는지**를 묻는다. 따로 설치된 DB(이미 운영 중인 DB 포함)이면 접속 정보(호스트, 포트, 데이터베이스, 사용자, 비밀번호)를 입력받아 연결을 확인하고 설정에 쓴다. 함께 설치하면 설치 방법을 고른다: 컨테이너(Docker, Podman 중 설치된 것) 또는 시스템 패키지이다. 시스템 패키지는 운영체제와 배포본에 맞는 것만 선택지로 보여 주고(예: Cubrid나 SQL Server는 배포본별 저장소 지원 여부로 거른다), 지원하지 않는 조합은 안내만 하고 컨테이너나 따로 설치(접속 정보 입력)로 안내한다. **함께 설치한 DB는 스크립트가 데이터베이스와 사용자를 만든다. 따로 설치된 DB에는 스크립트가 데이터베이스나 사용자를 만들지 않고(관리자 계정도 받지 않는다) 설치자에게 만들어 달라고 요청한다**(선생님 결정 2026-11-20): 필요한 이름, 문자 집합, 권한과 예시 SQL을 보이고, 준비가 끝나면 접속과 권한을 확인한 뒤 이어서 진행한다. **MariaDB의 기본 버전은 12.3.3**이다(컨테이너 이미지와 시스템 패키지 모두. v1.6 위키의 MariaDB 설치 문서도 12.3.3을 안내한다). DB가 정해지면 데이터베이스와 사용자를 만들고(MariaDB는 utf8mb4와 utf8mb4_bin, MSSQL은 `READ_COMMITTED_SNAPSHOT ON`, Cubrid는 UTF-8 로케일과 브로커 설정) 접속 주소를 `application.yml`에 쓴다. **(4) 그 밖의 설치 순서**: JDK 21 이상 확인, 데이터 폴더 준비(`yona.data-dir`), `application.yml` 생성, 서비스 등록과 **부팅 때 자동 시작**(Linux는 systemd, Mac은 launchd, Windows는 서비스 등록 도구. 아래 "X-63 부팅 때 자동 시작"), `/-_-actuator/health`로 설치 확인. **(4-1) 메일 설정을 묻는다**: 메일 서버 사용 여부를 묻고, 쓰면 접속 정보(`spring.mail.host`, 포트, SSL, 사용자, 비밀번호)와 보내는 주소(`yona.smtp.user`, `yona.smtp.domain`)를 입력받는다. 이어서 **실제로 보낼지, 모의 발송(`yona.smtp.mock=true`, D-145)으로 둘지** 묻는다. 서버를 쓰지 않으면 `spring.mail.host`를 쓰지 않는다. 모의 발송은 접속 정보를 입력해 두고도 메일을 내보내지 않으므로 시험 서버나 DB를 복사한 서버에 권한다. 이관 모드에서는 v1.6의 `smtp.mock` 값(없으면 `true`)을 그대로 옮기고, 모의 발송이 켜진 채 이관됨을 알리며 실제로 보내려면 `yona.smtp.mock=false`로 바꾸라고 안내한다. 비밀번호는 설정 파일에 직접 쓰지 않고 소유자만 읽는 파일에 둔다. **(5) SSH 연동도 스크립트에 포함한다**: 내장 서버(`yona.ssh.mina.enabled`)를 고르면 **서빙할 포트 번호를 입력받아**(`yona.ssh.mina.port`, 기본 2222, 1~65535와 사용 중인 포트인지 검사하고 22번처럼 1024 미만이면 권한이나 포트 전달이 필요하다고 안내한다) 설정에 쓴다. 포트 전달 뒤라서 바깥 포트가 다르면 `yona.ssh.public-port`도 묻는다. Linux와 Mac은 내장 서버와 시스템 sshd 연동(`yona.ssh.relay.enabled`, 전용 계정, `yona-ssh` 설치, `/etc/ssh/sshd_config.d/50-yona.conf` 드롭인의 `Match User`와 `AuthorizedKeysCommand`, `sshd -t` 검사 뒤 reload. 서비스 이름은 `ssh`와 `sshd`를 모두 찾는다. 아래 "X-63 2차 검토 결정" 3번) 중에서 고르고, **Windows는 시스템 sshd 연동 없이 내장 MINA 서버만** 켠다(번들 OpenSSH가 `AuthorizedKeysCommand`를 지원하지 않기 때문) | 참고 자료는 `yona-install`(v1.x용 Python 설치 도구)의 분석 `ANALYSIS.md`, `docs/ko/install-mariadb.md`, [architecture/fresh-install-guide.md](architecture/fresh-install-guide.md)(DB별 준비와 SSH 절차), `docs/yona-ssh-system-sshd.md`, `support-script/`, `docker-compose.yml` | **구현 완료(2026-11-20), 결정은 D-146**. 구현과 시험 결과는 [architecture/install-scripts.md](architecture/install-scripts.md). **Windows와 macOS 실기, 시스템 패키지 DB, systemd 재부팅, 실제 v1.6 이관은 시험하지 못했다** | 선생님 지시(2026-11-20): 세 운영체제를 지원하므로 셸 버전과 PowerShell 버전을 동시에 제공하고, DB와 SSH 연동은 설치할 때 묻는다. 검토했던 다른 안은 jar 안의 설치 명령(권장안으로 냈으나 선생님이 두 스크립트로 정하심)과 Python이다. 원본(`yona-install`)의 결함은 되풀이하지 않는다: 반환 코드 확인과 즉시 중단, 지원하지 않는 운영체제와 조합의 사전 차단, 기본 비밀번호 고정 금지(무작위 생성 또는 입력), 체크섬 검증, 기존 설정 파일(`/etc/my.cnf` 등) 덮어쓰기 금지, 설정 값 이스케이프. 구현 전에 `decisions.md`에 선택지와 권장안을 기록하고 승인을 받는다. v1.6에 없는 기능이므로 v1.6 동작과 계약은 바꾸지 않는다. **선행 확인(2026-11-20, 완료)**: MariaDB 12.3.3에서 DB 시험 60건이 통과했다(아래 "X-63 MariaDB 12.3.3 확인 기록"). **첫 질문은 신규 설치인지 v1.6 이관인지이고, 이에 따라 단계와 순서가 달라진다**(아래 "X-63 설치 모드별 흐름"). 설치 스크립트는 서버 전체 설정 파일을 건드리지 않고 데이터베이스, 사용자, 권한까지만 만든다 |
| X-64 | **컨테이너 이미지(Dockerfile)**: Yona v2 서버만 담은 이미지(Java 21 JRE, 비루트, `/data` 볼륨, 8080과 2222, 상태 확인, amd64와 arm64) | 개발용 `docker-compose.yml`(참고만) | **구현 완료(2026-11-20), 결정은 D-147**. `deploy/docker/Dockerfile`, 시험 `deploy/docker/test-image.sh`(진짜 jar). 레지스트리 게시는 아직 하지 않았다 | 기본 설치는 X-63이고 이 항목은 컨테이너로 배포하려는 사이트를 위한 별도 방법이다. 설계는 [architecture/container-image-plan.md](architecture/container-image-plan.md). X-65와 X-66이 이 이미지에 의존한다. 환경 변수만으로 설정이 되는지와 정상 종료는 구현 전에 확인한다. **결정(2026-11-20 선생님)**: 레지스트리는 GitHub Container Registry, 서명은 처음에는 하지 않는다 |
| X-65 | **docker-compose 배포**: Yona와 DB(PostgreSQL 권장, MariaDB 12.3.3, H2)를 `docker compose` 또는 `podman compose` 한 번으로 띄우는 운영용 파일 묶음. 저장소 루트의 개발용 `docker-compose.yml`과 분리 | `docker-compose.yml`(개발용) | **구현 완료(2026-11-20), 결정은 D-147**. `deploy/compose/`. `docker compose`와 `podman compose`가 없어 파일을 직접 실행하지는 못했고 구조 검증과 같은 구성의 `docker run` 시험으로 확인했다 | 설계는 [architecture/compose-deploy-plan.md](architecture/compose-deploy-plan.md). Cubrid와 SQL Server는 외부 DB 접속 정보로 안내. 비밀번호는 `.env`나 `secrets`로 받고 기본값을 고정하지 않는다 |
| X-66 | **Helm 차트**: 쿠버네티스에 Yona를 한 개 복제본(StatefulSet, 보존되는 볼륨)으로 올린다. DB는 외부 DB가 기본, SSH는 내장 서버만 | | **구현 완료(2026-11-20), 결정은 D-147**. `deploy/helm/yona/`. `helm lint`와 `template`, `kubectl --dry-run=client`로 확인했고 클러스터에 설치하지는 못했다 | 설계는 [architecture/helm-chart-plan.md](architecture/helm-chart-plan.md). **결정(2026-11-20 선생님)**: 복제본 1개 고정과 업데이트 때의 끊김을 받아들인다, 게시는 OCI 레지스트리(GitHub Container Registry) |
| X-25 | CI/Actions(GitHub Actions 방식의 워크플로 YAML, 러너 등록과 폴링)와 프런트엔드 분리(SPA) | P3-05, P3-13 | **보류(선생님 결정 2026-11-02)** | `next`에서도 **미착수 또는 계획서만 있다**. CI/Actions는 자동 진행 대상에서 제외하라는 기존 지시(P3-05)가 있다 |
| X-26 | 풀 리퀘스트 리뷰에 줄 단위 댓글 묶기와 임시 저장 리뷰(PENDING): 코드 줄에 단 댓글을 "리뷰 시작"으로 임시 저장했다가 판정과 함께 한꺼번에 제출, 임시 리뷰 버리기 | `domain/pullrequest/PullRequestReview`, `domain/comment/CommentThread` | **구현 완료(2026-10-29), 결정은 D-38** | X-12(D-35)를 먼저 했다. Yona의 줄 댓글(`CommentThread`)은 지금도 즉시 올라가고 판정과 무관하다. 묶으려면 줄 댓글의 저장, 알림 구조를 바꿔야 한다. GitHub은 임시 저장된 줄 댓글을 본인만 보며 제출 전까지 고칠 수 있다(공식 문서). 임시 저장 리뷰는 이 묶음이 있어야 의미가 있다 |
| X-27 | 풀 리퀘스트 리뷰 요청과 다시 요청: 쓰기 권한자가 특정 사람에게 리뷰를 요청하고 요청받은 사람 목록, 알림, 다시 요청을 둔다 | `domain/pullrequest/PullRequestReviewRequest` | **구현 완료(2026-10-29), 결정은 D-37** | X-12(D-35)를 먼저 했다. GitHub은 리뷰 요청에 쓰기 권한이 필요하다(공식 문서). Yona에는 요청 대상 지정 개념이 없어 새 테이블과 화면이 필요하다. 새 테이블은 내보내기, 가져오기에 포함한다 |
| X-28 | 풀 리퀘스트 병합 방식 선택(병합 커밋, 스쿼시, 리베이스)과 프로젝트 설정(허용 방식, 기본 방식) | `PullRequestMerger` 확장 | **구현 완료(2026-10-29), 결정은 D-41** | D-39 4번: GitHub 방식 세 가지. 병합 화면은 선택 상자가 되고 REST(X-15)의 `method` 필드를 연다. 스쿼시는 커밋 메시지 구성(작성자, 원래 커밋 메시지, `Reviewed-by`)을 정하고 X-11(이슈 자동 닫기)이 같은 결과를 내야 한다. 리베이스는 커밋마다 다시 적용(충돌 처리)하고 커밋 번호가 바뀌어 "이미 병합됨" 판단을 살펴야 한다. X-13(선형 이력 필수)과 맞물린다. Forgejo의 나머지 방식(빨리 감기만 허용, 수동 병합 등)은 범위 밖 |
| X-32 | 리뷰어 수 강제 지정을 빼고 브랜치 보호 규칙(브랜치별 필요한 승인 수, 관리자에게도 적용)으로 바꾼다 | `Project`, `PullRequestRules`, `PullRequestWorkflow`, 프로젝트 설정 | **구현 완료(2026-10-30), 결정은 D-42, D-43, D-44** | 기본은 강제 없음. 프로젝트 설정의 리뷰어 수는 규칙이 없는 브랜치의 기본 규칙으로 유지한다(D-43). 화면, REST(`approvals.required`)가 규칙을 따른다 |
| X-33 | GitHub 방식 브랜치 보호(직접 푸시 차단, 필요한 승인 수, 변경 요청과 대화 해결, 선형 이력, 강제 푸시와 삭제 차단, 관리자에게도 적용)로 이행하고 리뷰어 사용과 리뷰어 수 설정을 무력화한다 | `BranchProtectionRule`, `PullRequestRules`, `PullRequestWorkflow`, `GitHooks`, 프로젝트 설정 | **구현 완료(2026-10-30), 결정은 D-45, X-34가 구조를 대체** | X-32를 대체한다. 1단계는 병합 조건과 이전, 2단계는 푸시 검사이다. `is_using_reviewer_count`, `default_reviewer_count`는 이번 버전에서 쓰지 않고 차기 버전에서 제거한다 |
| X-34 | 브랜치 보호를 GitHub의 규칙 묶음 구조로 전환한다(이름, 적용 상태, 바이패스, 대상, 규칙 묶음, 합산, 목록과 편집 화면) | `Ruleset`, `RulesetService`, `GitHooks`, `PullRequestRules`, 프로젝트 설정 메뉴 | **구현 완료(2026-10-30), 결정은 D-46** | X-33을 대체한다. ◐ 항목은 현재 수준으로 둔다 |
| X-35 | 규칙 묶음 ◐ 항목의 완성: 바이패스 목록(구성원, 조직 관리자, 개별 사용자)과 우회 방식(Always, Exempt, For pull requests only), 대상 여러 개와 제외와 기본 브랜치와 미리보기, 선형 이력의 푸시 검사, 오래된 승인 해제와 허용 병합 방식의 규칙 묶음 단위 이전, 승인 수 0~10 정렬, 평가 상태 | `Ruleset` 확장 | **대기(가장 낮은 우선순위, 2026-10-30)** | D-46 6번. 우회 대상은 종류와 대상 번호 구조로 저장하도록 확장한다 |
| X-35a | **규칙 묶음 바이패스 목록(개별 사용자)**: 지금은 `bypassAdmins` 켜짐 하나뿐이라 프로젝트 관리자와 사이트 관리자만 우회한다. 규칙 묶음마다 우회할 개별 사용자(그 프로젝트의 구성원만 선택)를 추가하고 뺄 수 있게 하고, 기존 체크와 합쳐서 판정한다(둘 중 하나면 우회, 여러 규칙 묶음이 막을 때는 지금처럼 모두에서 우회 가능해야 통과). 새 테이블 `ruleset_bypass_user`, 편집 화면의 "바이패스" 구역에 구성원 선택과 사용자 줄(삭제 버튼), 규칙 묶음 REST에 같은 목록. 우회 방식 구분(풀 리퀘스트만 등)과 역할 단위 목록은 뺀다 | `Ruleset` 확장, `AccessControl.canBypass`, `RulesetController`, 규칙 묶음 REST | **대기(2026-11-13 선생님 지시: 로드맵에만 기록하고 착수하지 않음)** | X-35의 바이패스 목록 부분을 분리했다. 실제 푸시와 병합 확인에서 체크 하나뿐인 한계를 발견했다(A-555). 설계는 권장안 그대로이며 착수할 때 결정 기록에 올린다 |
| X-36 | 이슈, 게시글, 댓글의 삭제를 작성자와 관리자로 제한한다(웹과 REST). 수정과 속성 변경은 GitHub처럼 구성원에게 유지 | `AccessControl`(삭제 규칙), 화면의 삭제 단추 | **구현 완료(2026-10-30), 결정은 D-48** | v1.6 권한 모델(구성원은 모든 것을 지운다)과 달라진다. X-29b(게시판 REST)는 이 결정을 반영한다 |


### X-63 설치 모드별 흐름 (구현 완료, 2026-11-20)

설치 명령을 내리면 **먼저 이미 설치된 Yona가 있는지 본다.** 없으면 **신규 설치인지, v1.6에서 이관인지 묻는다.** 같은 버전이 있으면 알리고 끝내고, 다른 버전이 있으면 **업그레이드 모드**로 간다(아래 "X-63 업그레이드 모드"). 신규와 이관은 해야 할 일과 순서가 다르다. 공통 앞부분(운영체제 검사, JDK 21 이상 확인, 포트 충돌 검사)은 두 길이 같다.

| 구분 | 신규 설치 | v1.6 이관 |
|---|---|---|
| 전제 | 빈 서버 | v1.6이 운영 중이거나 중지된 서버. **v1.6을 끄고 백업했는지 확인받은 뒤에만 진행** |
| 첫 입력 | 없음 | v1.6 설치 폴더, v1.6의 `conf/application.conf`, v1.6의 DB 종류(MariaDB/MySQL 또는 H2) |
| DB 종류와 설치 | 5종 중 선택, 함께 설치 또는 따로 설치 | **v1.6이 MariaDB/MySQL이면 묻지 않는다**(같은 DB를 제자리에서 이어받는다, 서버 버전을 올리자고 하지 않는다). **H2이면 대상 DB를 고른다**(비어 있어야 한다) |
| DB 준비 | 함께 설치한 DB는 데이터베이스와 사용자를 만든다. 따로 설치된 DB는 설치자에게 요청한다 | 만들지 않는다(제자리). H2에서 옮길 때 대상 DB가 따로 설치된 것이면 설치자에게 요청하고, 함께 설치하면 만든다 |
| 설정 파일 | 입력값으로 `application.yml`을 만든다 | v1.6 `application.conf`를 변환한 속성 파일을 쓴다(`--v16-conf`). 변환 보고서(알 수 없는 키, 없어진 기능, `include`, `smtp.mock`)를 읽게 하고 확인을 받는다 |
| 비밀 키 | 첫 접속 화면에서 정한다 | **v1.6의 `application.secret` 값을 그대로 `yona.secret`으로 옮기고 바꾸지 않는다**(이미 저장한 암호화 값을 읽지 못하게 되므로) |
| 데이터 폴더 | 새로 만든다 | v1.6 저장소 폴더(`repo/git`, `repo/hg` 등)를 `yona.data-dir`이 가리키게 한다(이관이 브랜치를 읽는다) |
| 데이터 작업 | 없음 | 이관 스크립트(`scripts/migrate-v16.sh`, H2는 `scripts/migrate-v16-h2.sh`)를 서버를 끈 채 실행한다. 몇 번 돌려도 결과가 같고, 데이터 이관은 실패하면 아무것도 바꾸지 않는다 |
| 서비스 시작 뒤 | 환영 화면에서 관리자 계정을 만들고 서버를 다시 시작한다(`secret.properties`를 백업하게 안내) | 환영 화면이 나오지 않는다. 기존 관리자로 로그인해 확인한다 |
| v1.6 서버 | 해당 없음 | 포트 충돌과 이중 쓰기를 막도록 v1.6 서비스를 끄고 시작하지 않게 하라고 안내한다(스크립트가 끄지는 않는다) |
| SSH 연동 | 묻는다 | 묻는다(v1.6에 없던 기능이라 기본은 꺼짐) |

**이관 규약의 범위(2026-11-20 선생님)**: 이관 모드와 이관 규약은 **v1.6에서 v2로 올라올 때만** 둔다. v2끼리의 버전 올림에는 이관 단계, 버전 표시, 되돌리기 절차를 두지 않는다(새 jar로 실행하면 된다). 설치 스크립트의 이관 모드도 입력을 v1.6 형식(`application.conf`, 설치 폴더, H2 또는 MariaDB/MySQL)으로만 받는다.

**신규 설치 순서**: **사전 점검(아래 "X-63 사전 점검과 추가 질문")**, 버전 선택, jar 내려받기와 체크섬 검증, **설치 폴더와 데이터 폴더 질문**, DB 종류 선택, DB 함께 설치 또는 접속 정보 입력, 데이터베이스와 사용자 생성(함께 설치한 DB만, 따로 설치된 DB는 설치자에게 요청), **사이트 주소와 웹 포트 질문**, 메일 질문, **서비스 등록 여부 질문**, **격상이 필요한 단계 안내와 동의**, `application.yml` 생성, SSH 질문과 설정, 서비스 등록(등록하기로 했을 때), 시작, 상태 확인, 설치 결과 문서 작성, 환영 화면 안내.

**이관 순서**: **사전 점검**, v1.6 정보 입력, **v1.6 중지와 백업 확인**, 버전 선택, jar 내려받기와 체크섬 검증, (H2이면 대상 DB 선택과 준비), 설정 변환과 보고서 확인, 설치 폴더와 데이터 폴더 지정, **서비스 등록 여부 질문**, **격상이 필요한 단계 안내와 동의**, **이관 스크립트 실행**, SSH 질문과 설정, 서비스 등록(등록하기로 했을 때), 시작, 상태 확인, 설치 결과 문서 작성, 기존 관리자 로그인 확인 안내.

**미리 정해야 할 것**
1. **(결정됨, 2026-11-20 선생님, 갱신)** 이관 스크립트(`scripts/`)와 H2 변환기(`tools/h2-export`)를 포함해 설치에 필요한 모든 파일은 **GitHub 릴리스 자산에서만 받는다**(저장소의 `main`에서 받지 않는다). 선택한 릴리스의 자산을 쓰므로 jar와 스크립트의 버전이 어긋나지 않는다. 인터넷이 없는 폐쇄 환경에서는 필요한 파일을 한 디렉터리에 모아 두고 **그 디렉터리에서 그대로 실행하면 되게** 한다(아래 "폐쇄 환경 실행"). **선행 과제**: 지금 릴리스(`v2.0.0-rc1`)에는 jar 하나뿐이라 릴리스 절차가 설치 스크립트와 이관 스크립트, H2 변환기 소스, 답 파일 견본, 메시지 파일을 자산으로 함께 올리도록 바꿔야 한다.
2. H2 변환기는 소스를 바로 실행해서 JRE만으로는 안 되고 JDK 21이 필요하다. 이관 모드에서는 JRE가 아니라 JDK를 요구한다.
3. **(결정됨, 2026-11-20 선생님, 갱신)** 이미 설치된 v2를 만나면 버전을 견준다. **같은 버전이면 새로 설치하지 않고 알리고 끝낸다. 다른 버전이면 업그레이드로 본다**(아래 "업그레이드 모드"). 업그레이드는 새 jar를 받아 바꾸고 다시 시작하는 것이며, 이관 단계는 없다(스키마는 서버가 시작할 때 `ddl-auto: update`로 맞춘다).
4. v1.6 MariaDB를 12.3.3으로 올리는 것은 이 도구가 하지 않는다. 이관 리허설은 10.11에서만 해 보았다.

### X-63 사전 점검과 추가 질문 (선생님 수용 2026-11-20)

앞선 검토에서 빠진 것으로 짚은 12개 가운데 1번부터 5번을 받아들여 아래와 같이 넣는다. 6번부터 12번은 아직 정하지 않았다(맨 아래).

**1. 사이트 주소 질문**

| 질문 | 설정 키 | 비고 |
|---|---|---|
| 접속 호스트 이름 | `yona.hostname` | 비우면 요청의 Host 헤더를 쓴다([fresh-install-guide.md](architecture/fresh-install-guide.md) 5절). 메일과 웹훅에 들어가는 주소가 이 값으로 정해진다 |
| 바깥 포트 | `yona.port` | 프록시 뒤라서 웹 포트와 다를 때 |
| 접속 방식 | `yona.scheme`(`http` 또는 `https`) | |
| HTTPS 프록시 뒤에서 운영하는가 | `yona.scheme=https`와 `server.forward-headers-strategy=framework` | 앞의 것은 메일 주소를 `https://`로, 뒤의 것은 프록시 뒤에서도 접속자 주소를 구분하게 한다 |

리버스 프록시와 인증서 발급은 스크립트가 하지 않는다. 주소와 방식만 받아 설정에 쓴다.

**2. 웹 포트(`server.port`) 질문과 사용 중 검사**: 기본은 8080이다. 입력한 포트가 1~65535 범위인지, 이미 쓰이는지 검사하고(SSH 포트 검사와 같은 규칙), 1024 미만이면 권한이나 포트 전달이 필요하다고 안내한다. 웹 포트와 내장 SSH 포트가 같으면 막는다.

**3. 비대화형(무인) 모드**: 모든 질문의 답을 미리 줄 수 있다. 여러 서버 설치와 자동화 도구에서 부르기 위한 것이다.

| 규칙 | 내용 |
|---|---|
| 답을 주는 방법 | 명령줄 옵션, 답 파일(`--answers=파일`), 환경 변수 중 하나. 버전도 `--version`으로 준다(아래 "비대화형 답 항목표"). 우선순위는 옵션, 답 파일, 환경 변수, 질문 순 |
| 비대화형 실행 | `--yes`(또는 `--non-interactive`)를 주면 묻지 않는다. 필요한 답이 빠졌으면 **빠진 항목을 모두 목록으로 알리고 아무것도 설치하지 않은 채 중단한다**(일부만 설치하지 않는다) |
| 비밀 값 | 비밀번호와 `yona.secret`은 옵션이 아니라 **소유자만 읽는 파일 경로**나 환경 변수로 받는다(명령줄은 프로세스 목록에 보인다) |
| 확인 | `--dry-run`은 사전 점검과 질문 검증까지만 하고 설치하지 않으며, 하게 될 일을 목록으로 보인다 |
| 위험한 동작 | 이관의 "대상 DB의 테이블을 비우고 교체"는 `--yes`만으로는 하지 않고 이를 따로 확인하는 옵션(`--confirm-replace-data`)을 요구한다 |

이 모드는 v1.6에 없던 새 범위라 `decisions.md`에 선택지와 권장안을 올려 승인받는다.

**4. JDK가 없거나 낮을 때 (결정됨, 2026-11-20 선생님: "묻고, 동의하면 설치")**

| 상황 | 처리 |
|---|---|
| JDK 21 이상이 있다 | 그대로 쓰고 서비스 파일에 그 경로를 절대 경로로 쓴다 |
| 없거나 21 미만이고 인터넷이 된다 | **설치할지 묻고**, 동의하면 운영체제의 패키지 관리자로 설치한다(Linux는 배포본 저장소, Windows는 winget. **Mac은 SDKMAN을 권장하고 로컬 사용자 계정으로 쓰며, SDKMAN이 없으면 스크립트가 설치하지 않고 사용자에게 설치를 요구한다**, 아래 "2차 검토 결정" 6번). 거절하면 설치 방법을 안내하고 중단한다. 비대화형이면 `--install-jdk`를 줘야만 설치한다 |
| 폐쇄 환경 | 설치하지 않고 필요한 버전과 설치 방법을 안내한 뒤 중단한다(앞서 정한 "JDK와 DB는 서버에 미리 있어야 한다") |
| 이관 모드 | JRE가 아니라 **JDK**를 요구한다(H2 변환기가 소스를 바로 실행하기 때문) |

이유: 설치 도구의 목적이 효율적인 설치이므로 사용자가 동의할 때 알아서 설치하는 편이 낫고, 동의 없이 시스템 패키지를 바꾸지는 않는다. 어느 패키지 관리자를 쓸 수 있는지는 구현 때 확인한다(확인하지 못한 것).

**5. 사전 점검(preflight)**: 설치나 다운로드를 시작하기 **전에** 아래를 한 번에 점검하고, 실패한 항목을 모두 모아 보인 뒤 중단한다(하나씩 실패하며 일부만 설치된 상태를 만들지 않는다). 질문 단계가 끝난 뒤 실제 설치 전에도 선택에 따른 항목을 한 번 더 점검한다.

| 점검 | 내용 |
|---|---|
| 권한 | 선택한 작업에 격상이 필요한지 계산하고, 필요하면 `sudo`를 쓸 수 있는지(Linux, Mac)나 관리자 그룹 소속인지(Windows) 확인한다(위 11번) |
| 운영체제 | 지원하는 운영체제와 배포본인지(DB 시스템 패키지는 배포본별로 거른다) |
| JDK | 위 4번 |
| 디스크 | 설치 폴더와 데이터 폴더의 여유 공간(이관이면 v1.6 저장소 크기 이상) |
| 메모리 | 최소 메모리 미달이면 경고(막지는 않는다) |
| 포트 | 웹 포트, SSH 포트, 함께 설치하는 DB의 포트가 비어 있는지 |
| DB | 따로 설치된 DB는 접속과 인증, 필요한 권한, 버전, **설치자가 데이터베이스와 사용자를 만들어 두었는지**(없으면 만드는 방법을 보이고 준비되면 이어간다). 이관이면 대상 DB가 비어 있는지 |
| 컨테이너 도구 | 컨테이너로 DB를 설치하면 Docker나 Podman이 있고 실행 중인지 |
| 네트워크 | 인터넷 가능 여부(불가면 폐쇄 환경 규칙으로 안내), 필요한 파일이 같은 디렉터리에 있는지 |
| 이미 설치됨 | 같은 버전이면 알리고 끝낸다. **다른 버전이면 업그레이드 모드로 간다**(선생님 결정). 설치된 버전은 설치 기록과 설치 폴더의 jar에서 읽는다 |
| v1.6 이관 | v1.6이 꺼져 있는지(같은 DB를 쓰는 프로세스와 포트 확인), 백업했다는 확인 |

6번부터 12번은 아래 "X-63 나머지 결정(6번부터 12번)"에 있다.

### X-63 나머지 결정(6번부터 12번) (선생님 2026-11-20)

| # | 상태 | 요약 |
|---|---|---|
| 6 | **결정됨(2026-11-20 선생님 수용)** | 실패와 되돌리기: 아래 |
| 7 | **결정됨** | 설치에 필요한 모든 파일을 GitHub 릴리스 자산에서만 받는다(위 "미리 정해야 할 것" 1번) |
| 8 | **결정됨(2026-11-20 선생님 수용)** | 시험 전략: 아래 |
| 9 | **결정됨** | JVM 메모리와 시간대는 컨테이너 문서를 따른다: 아래 |
| 10 | **결정됨(해석을 적음)** | 설치가 끝난 뒤 결과를 문서로 남긴다: 아래 |
| 11 | **결정됨(2026-11-20 선생님이 정정)** | **일반 사용자로 시작하고 필요한 단계에서만 관리자 권한으로 올린다**(처음에는 "처음부터 관리자로 실행"으로 정했다가 바꿨다): 아래 |
| 12 | **결정됨** | 설치 메시지는 한국어와 영어를 모두 지원한다: 아래 |

**6. 실패와 되돌리기 (결정됨)**

| 규칙 | 내용 |
|---|---|
| 설치 기록 | 단계마다 "이번 실행이 만든 것"(파일, 폴더, 서비스, 계정, 데이터베이스, 컨테이너, 볼륨)과 되돌리는 방법을 기록 파일에 적는다. 위치는 Linux와 Mac이 `/var/log/yona-install/`, Windows가 `%ProgramData%\Yona\install\`이다. 소유자만 읽고 비밀 값은 적지 않는다 |
| 실패하면 | **이번 실행이 새로 만든 것만** 기록의 역순으로 되돌린다. 이미 있던 것(기존 DB, 기존 설정, 기존 서비스)은 건드리지 않는다 |
| 지우지 않는 것 | 사용자 데이터가 들어갈 수 있는 것(데이터 폴더, 함께 설치한 DB의 볼륨)은 이번 실행이 방금 만들어 데이터가 없을 때만 지운다. 그 밖에는 남기고 위치를 알린다 |
| 되돌릴 수 없는 것 | 이관의 데이터 교체는 되돌릴 수 없다. 사전 백업 확인과 이관 스크립트의 "실패하면 아무것도 바꾸지 않는다"에 맡긴다 |
| 디버깅 | `--keep-on-failure`를 주면 되돌리지 않고 그대로 남긴다 |
| 제거 | 같은 스크립트의 `uninstall` 명령이 서비스와 설치 폴더를 지운다. 데이터 폴더와 DB는 기본으로 남기고 `--purge`를 줄 때만 두 번 확인한 뒤 지운다 |
| 다시 실행 | 되돌림이 끝났으면 새 설치로 본다. 기록이 "진행 중"으로 남은 상태면(강제 종료) 사전 점검이 알리고 되돌림을 먼저 하게 한다 |

**7. 파일의 출처 (결정됨)**: 설치 스크립트, 이관 스크립트, H2 변환기, 답 파일 견본, 메시지 파일, jar를 모두 **선택한 GitHub 릴리스의 자산**에서 받는다. `main`에서 받지 않는다. **한 줄 설치를 제공한다**(선생님 결정 2026-11-20, 처음에 에이전트가 "두지 않는다"고 했던 것을 바꿨다): 아래 "한 줄 설치".

**8. 시험 전략 (결정됨)**

| 층 | 방법 |
|---|---|
| 원칙 | TDD다. 버전 선택, 입력 검증, 설정 파일 생성, 사전 점검 판정, 순서 계산은 부작용이 없는 함수로 나눠 시험을 먼저 쓴다 |
| 정적 검사 | 셸은 `shellcheck`, PowerShell은 `PSScriptAnalyzer` |
| 단위 시험 | 셸은 `bats-core`(외부 명령은 `PATH` 앞에 둔 가짜 실행 파일로 대신한다), PowerShell은 `Pester`(`Mock`) |
| 두 스크립트의 동등성 | **공통 시나리오 표** 한 벌을 데이터로 둔다(답 파일 입력, 기대하는 `application.yml`, 서비스 파일, 질문 순서, 종료 코드). 셸과 PowerShell이 같은 표를 통과해야 한다 |
| Linux 통합 | 배포본 매트릭스(Ubuntu LTS, Debian, Rocky 또는 Alma 등)를 systemd가 있는 컨테이너에서 실제 jar로 돌려 설치, 상태 확인, 서비스 재시작까지 본다 |
| 폐쇄 환경 | `--network none` 컨테이너에서 같은 디렉터리 실행을 시험한다 |
| 이관 | 이관 리허설에 쓴 합성 v1.6 데이터로 MariaDB(12.3.3 포함)와 H2 이관을 시험한다 |
| 재부팅 | 실제 재부팅은 VM에서 손으로 확인하고 결과를 `log.md`에 적는다 |
| Mac과 Windows | **이 개발 컴퓨터(Linux)에는 없어 시험할 수 없다.** 선생님이 쓰시는 실제 환경이나 별도 머신이 필요하다. 그때까지 두 운영체제의 동작은 "확인하지 못한 것"으로 남긴다 |
| 자동 실행 | CI 연결은 현재 보류 중인 항목(P3-05)이라 이 계획에 넣지 않는다. 시험은 로컬에서 돌리는 것을 전제로 하고, 연결은 재개 지시가 있을 때 정한다 |

**9. JVM 메모리와 시간대 (결정됨, 컨테이너 문서를 따름)**: 힙은 고정 `-Xmx`가 아니라 **서버 메모리에 대한 비율**(`-XX:MaxRAMPercentage`)로 정한다([container-image-plan.md](architecture/container-image-plan.md)와 같은 방식. 비율의 기본값은 구현 때 시험으로 정한다). 시간대는 서비스 환경에 `TZ`로 준다. 신규 설치는 서버의 시간대를 쓰고, **이관 모드에서는 v1.6 서버가 돌던 시간대와 같아야 하므로 묻는다**(옛 시각을 읽는 데 쓰이며 `migrate-v16-h2.sh`의 `V16_TIMEZONE`과 같은 값이다).

**10. 설치가 끝난 뒤의 문서화 (결정됨)**: "설치 끝나고 문서화"를 두 가지로 이해해 모두 한다. (1) 설치 끝 화면에 요약을 보인다. (2) 같은 내용을 **설치 결과 문서**(`INSTALL-REPORT.md`, 설치 폴더에 소유자만 읽도록)로 남긴다. 비밀 값은 적지 않는다. 내용: 접속 주소와 포트(방화벽에서 열어야 할 것), 설치 위치와 데이터 폴더, 서비스 이름과 시작, 중지, 상태 확인 명령, DB 종류와 접속 주소(비밀번호 제외), **백업할 것**(`secret.properties`, `ssh/`, DB, `repo/`, `uploads/`), 다음에 할 일(신규는 환영 화면에서 관리자 계정을 만들고 서버를 다시 시작, 이관은 기존 관리자로 로그인해 확인), 제거 방법, 설치 기록 위치. 다르게 이해했다면 알려 주시면 고친다.

**11. 권한 (결정됨, 일반 사용자로 시작하고 필요하면 격상)**: 스크립트는 **일반 사용자 계정으로 시작한다.** 선택한 작업 가운데 관리자 권한이 필요한 단계가 있을 때만 **권한을 올린다(격상).** 관리자 권한이 필요 없는 선택(예: 홈 폴더에 설치하고 서비스를 등록하지 않는 구성)이면 끝까지 격상하지 않는다. 이미 root나 관리자로 실행했으면 격상 없이 진행한다. 서버 프로세스는 어느 경우에도 관리자로 돌리지 않는다(서비스는 전용 비관리자 계정).

| 항목 | 내용 |
|---|---|
| 격상이 필요한 단계 | 서비스 등록과 부팅 자동 시작, 전용 계정 만들기와 소유권 설정, 시스템 경로(`/opt`, `/var/lib`, `C:\Program Files`, `C:\ProgramData`)와 설치 기록 쓰기, 시스템 패키지 설치(JDK, DB, OpenSSH 서버), 설치한 DB에 데이터베이스와 사용자 만들기, 시스템 `sshd` 연동(드롭인 쓰기와 reload), 1024번 미만 포트, 컨테이너 DB 설치(Docker는 root나 `docker` 그룹) |
| 격상이 필요 없는 단계 | 릴리스 내려받기와 체크섬 검증, 홈 폴더 아래 설치, H2 파일 DB, 일반 사용자로 도는 Podman, 사전 점검 |
| 방법 | Linux와 Mac은 `sudo`, Windows는 UAC(관리자 권한으로 다시 실행하는 PowerShell 창) |
| 미리 알림 | 질문이 끝나면 **격상이 필요한 단계 목록과 이유를 보이고 동의를 받은 뒤** 격상한다. 사전 점검은 `sudo`를 쓸 수 있는지(Linux와 Mac), 관리자 그룹 소속인지(Windows)를 확인한다 |
| 비대화형 | 비밀번호나 UAC 창에 답할 수 없으므로 **격상하지 못한다.** 필요한 단계가 있고 이미 관리자도 아니며 `sudo -n`(비밀번호 없는 `sudo`)도 안 되면, 모자란 권한과 단계를 모두 알리고 아무것도 설치하지 않은 채 중단한다. `--dry-run`은 격상이 필요한 단계를 목록으로 보인다 |
| 거절하거나 실패하면 | 사용자가 `sudo` 비밀번호를 틀리게 하거나 UAC를 거절하면 6번의 되돌리기 규칙(이번 실행이 만든 것만 되돌림)을 적용한다 |

*(에이전트 판단, 선생님 수용 2026-11-20)*: 격상은 단계마다가 아니라 **필요한 단계를 모아 한 번에** 한다(`sudo -v`로 자격을 확인해 비밀번호를 한 번만 묻는다. Windows는 상승된 PowerShell 프로세스가 모은 단계를 실행한다). Windows의 UAC 재실행은 스크립트가 파일로 저장되어 있어야 하므로 한 줄 설치도 받아 둔 파일로 다시 실행한다. **확인하지 못한 것**: Linux의 `sudo` 자격 유지 시간, Windows에서 `irm | iex`로 시작한 경우의 UAC 재실행, Mac의 `sudo`와 `SUDO_USER` 동작은 구현 때 각 운영체제에서 확인한다.

**12. 설치 메시지 언어 (결정됨, 한국어와 영어)**: 질문, 오류, 안내, 설치 결과 문서를 한국어와 영어로 모두 제공한다. 문구는 스크립트 안이 아니라 **메시지 파일**(`ko`, `en`)로 분리하고 셸과 PowerShell이 **같은 메시지 파일**을 읽는다. **언어를 정하는 순서**: (1) `--lang=ko|en`, (2) 환경 변수 `YONA_INSTALL_LANG`, (3) **시스템 언어가 한국어이면 한국어**, (4) 그 밖이거나 알 수 없으면 영어(선생님 결정 2026-11-20). 시스템 언어를 읽는 방법은 운영체제마다 다르다: Linux는 환경 변수 `LC_ALL`, `LC_MESSAGES`, `LANG`을 이 순서로 보고 `ko`로 시작하면 한국어, Mac은 같은 환경 변수가 비어 있을 수 있어 `defaults read -g AppleLocale`(또는 `AppleLanguages`)도 보며, Windows는 `Get-UICulture`의 이름이 `ko`로 시작하면 한국어이다. 감지가 틀릴 수 있으므로(예: 원격 접속이나 `sudo`가 환경 변수를 비운 경우, 영어로 설치된 서버를 한국 사용자가 쓰는 경우) **첫 줄에 쓰고 있는 언어와 바꾸는 방법(`--lang`)을 알린다.** *감지 방법의 세부는 구현 때 각 운영체제에서 확인한다(확인하지 못한 것).* 시험으로 두 언어의 키 집합이 같은지 확인한다.

**X-63의 범위 밖 (확인함, 2026-11-20)**

| 항목 | 이관 모드 | 신규 설치 |
|---|---|---|
| LDAP | **이관 스크립트가 옮긴다.** 변환기가 `ldap.*`를 `yona.ldap.*`로 옮기고, 데이터 이관 5단계가 켜진 LDAP을 사이트 설정(`ldap_settings` 행)으로 옮긴다([v16-migration-guide.md](architecture/v16-migration-guide.md)). 설치 스크립트는 따로 묻지 않는다 | 묻지 않는다. 설치 뒤 사이트 관리 화면에서 설정한다 |
| 메일 수신(IMAP) | **이관 스크립트가 옮긴다.** 변환기가 `imap.*` 9개 키를 `yona.imap.*`로 옮긴다(`V16ConfigConverter`) | 묻지 않는다. 필요하면 `yona.imap.*`를 설정하거나 설치 뒤에 한다 |
| SSO(OIDC, SAML) | 옮길 것이 없다. **v1.6에 없던 v2의 새 기능**이다 | 묻지 않는다. 설치 뒤 사이트 관리 화면에서 설정한다 |
| 리버스 프록시, HTTPS 인증서 발급 | 하지 않는다. 주소와 방식만 받는다(위 1번) | 같다 |

앞서 "LDAP, SSO, IMAP은 이관이 이미 옮긴다"고 한 서술은 SSO에서 틀렸다. SSO는 v1.6에 없던 기능이라 옮길 대상이 아니다.

### X-63 2차 검토 결정 (선생님 2026-11-20)

설치 스크립트에서 빠진 것 9가지를 짚었고, 선생님이 아래처럼 정하셨다.

| # | 항목 | 결정 |
|---|---|---|
| 1 | 설치 폴더와 데이터 폴더 | **질문한다.** 기본값은 아래 표(에이전트 판단, 승인 전) |
| 2 | 스크립트가 직접 설치하는 MariaDB의 접속 호스트 | **`localhost`만 허용한다.** 사용자는 `'yona'@'localhost'`만 만들고 호스트 범위를 묻지 않는다 |
| 3 | sshd 연동의 세부 | **운영체제별로 미리 확인한다. Linux는 Ubuntu, Debian, Rocky Linux, Fedora만 본다.** 확인 결과는 아래 |
| 4 | 회사 프록시 환경 | **범위 밖이다.** 사용자가 알아서 한다(`HTTPS_PROXY` 같은 환경 변수는 각 도구가 읽는 대로 두고 스크립트는 다루지 않는다) |
| 5 | GitHub API 호출 한도 | **나중에.** 필요해지면 별도 항목으로 다룬다 |
| 6 | Mac의 Homebrew | **로컬 사용자 계정이 맞다.** Mac의 JDK는 **SDKMAN을 권장**하고, SDKMAN이 설치되어 있지 않으면 **스크립트가 설치하지 않고 사용자에게 설치를 요구한다.** Mac의 DB는 **컨테이너로만** 설치한다 |
| 7 | 설치 직후 관리자 계정 선점 | **신경 쓰지 않는다**(Yona 설치 환경이라는 전제). 환영 화면에서 처음 접속한 사람이 관리자를 만들 수 있다는 점은 한계로 받아들인다 |
| 8 | Windows 실행 정책 | 막히면 **풀어 주는 방법을 안내한다.** 스크립트가 정책을 바꾸지는 않는다 |
| 9 | 비대화형 | 규칙은 위 3번에 이미 있다. 빠졌던 **답 항목 전체 목록**을 아래에 더한다. 설치 가이드 문서는 구현할 때 새 방법에 맞게 고친다 |

**1. 폴더 기본값 (에이전트 판단, 승인 전. 질문에서 Enter만 치면 이 값을 쓴다)**

| 운영체제 | 설치 폴더(jar, 설정, 스크립트) | 데이터 폴더(`yona.data-dir`) |
|---|---|---|
| Linux | `/opt/yona` | `/var/lib/yona` |
| Mac | `/usr/local/yona` | `/usr/local/var/yona` |
| Windows | `C:\Program Files\Yona` | `C:\ProgramData\Yona` |

운영체제 관례를 따른 선택이며 확인하지 않았다. 입력한 폴더가 이미 있고 비어 있지 않으면 사전 점검이 알린다.

**2. 직접 설치하는 MariaDB의 접속 (결정됨)**: 스크립트가 시스템 패키지로 설치하는 DB는 `localhost`에서만 접속을 허용한다. 원격 접속을 열지 않고 호스트 범위도 묻지 않는다. *(에이전트 판단, 승인 전: 컨테이너로 설치하는 DB는 포트를 `127.0.0.1`에만 공개한다. 컨테이너 안에서는 호스트의 접속이 `localhost`로 보이지 않으므로 이 경우 컨테이너 DB의 사용자 호스트를 어떻게 줄지는 구현 때 시험으로 정한다. 설치가 끝나면 연결과 문자 집합(`utf8mb4`)을 확인한다.)*

**3. sshd 연동: Linux 네 배포본에서 확인한 것 (2026-11-20, 컨테이너에서 OpenSSH 서버 설치 후)**

| 배포본 | OpenSSH | 서비스 유닛 | 설정 구조 |
|---|---|---|---|
| Ubuntu 24.04 | 9.6p1 | **`ssh.service`**와 `ssh.socket` | `Include /etc/ssh/sshd_config.d/*.conf`(12행), 드롭인 폴더 있음 |
| Debian 12 | 9.2 | **`ssh.service`**와 `ssh.socket` | 위와 같다 |
| Rocky Linux 9 | 9.9p1 | **`sshd.service`**와 `sshd.socket` | `Include /etc/ssh/sshd_config.d/*.conf`(15행), 드롭인 폴더 있음 |
| Fedora 45 | 10.5p1 | **`sshd.service`**와 `sshd.socket` | 위와 같다 |

**확인한 동작(네 배포본 모두)**: `/etc/ssh/sshd_config.d/50-yona.conf`에 `Match User git,hg,svn`와 `AuthorizedKeysCommand`를 두고 `sshd -t`가 통과했다. `sshd -T -C user=git`에서만 `AuthorizedKeysCommand`가 켜지고 `user=alice`에서는 `none`이었다. 본 설정 파일에서 `Include` **뒤에** 둔 `MaxAuthTries 2`가 두 사용자 모두에게 적용되어 드롭인의 `Match`가 뒤의 설정으로 번지지 않았다.

**이로써 정한 설계(에이전트 판단, 승인 전)**
- **`sshd_config` 본문을 고치지 않고 드롭인 파일 `/etc/ssh/sshd_config.d/50-yona.conf`만 둔다**(기존 `docs/yona-ssh-system-sshd.md`는 본문 맨 끝에 추가하라고 했다). 실패하면 그 파일만 지우면 되고 원본을 백업할 필요가 없다. `Include`가 없는 오래된 설정이면 본문 끝에 추가하되 백업한다.
- **서비스 이름은 `ssh`와 `sshd` 둘 다 찾아** 있는 쪽을 다시 읽게 한다(Debian 계열 `ssh`, RHEL 계열 `sshd`).
- 순서: 드롭인 작성, `sshd -t`로 검사, 통과하면 **reload**(재시작이 아니라 설정 다시 읽기. 기존 접속은 유지된다), 실패하면 드롭인을 지우고 알린다.
- OpenSSH 서버가 없으면 JDK와 같은 규칙으로 설치를 묻는다(동의하면 배포본 패키지).
- Fedora 45(OpenSSH 10.x)는 `sshd -T` 출력의 키가 `MaxAuthTries`처럼 **대소문자가 섞여 나왔다**(Ubuntu, Debian, Rocky는 소문자). 출력을 비교할 때 대소문자를 무시한다.

**확인하지 못한 것**: 컨테이너에는 systemd가 없어 `systemctl reload`가 실제로 도는지, Debian 계열의 `ssh.socket` 소켓 활성화와 reload가 함께 동작하는지는 보지 못했다. Mac의 sshd(launchd)와 Windows는 이 컴퓨터로 확인할 수 없다(Windows는 내장 MINA만 쓴다). 각 배포본의 다른 버전(Ubuntu 22.04, Debian 13, Rocky 10 등)도 보지 않았다.

**6. Mac 세부 (에이전트 판단, 승인 전)**
- SDKMAN은 로컬 사용자의 홈(`~/.sdkman`)에 설치되므로, LaunchDaemon이 쓸 JDK 경로가 그 사용자의 홈 아래가 된다. 서비스를 어느 계정으로 돌릴지(전용 계정은 그 홈을 못 읽을 수 있다)는 구현 때 Mac에서 확인한다.
- 11번(일반 사용자로 시작, 필요하면 격상)과의 관계: Mac에서는 SDKMAN과 Podman이 로컬 사용자 계정이므로 이 방식과 잘 맞는다. 서비스 등록 같은 시스템 작업만 `sudo`로 올린다.
- Mac은 이 개발 컴퓨터에 없어 확인하지 못했다.

**8. Windows 실행 정책**: 스크립트가 막히면 현재 프로세스에만 정책을 풀어 실행하는 방법(`Set-ExecutionPolicy -Scope Process`로 시작하는 명령)을 안내한다. 명령의 세부는 구현 때 Windows에서 확인한다.

### X-63 3차 점검에서 보강한 것 (선생님 수용 2026-11-20)

전체를 다시 읽고 서로 어긋나거나 빠진 곳을 찾았다. 권한 정책을 "일반 사용자로 시작"으로 바꾸면서 생긴 틈이 대부분이다. 1번부터 7번 모두 선생님이 수용하셨다(1번은 "서비스로 등록할지 물어봐").

| # | 빠졌던 것 | 보강 |
|---|---|---|
| 1 | **서비스 등록 여부를 묻는 질문이 없었다.** 부팅 자동 시작 구간은 항상 서비스를 등록한다고 읽혔다. 격상 없이 끝나는 구성(홈 폴더 설치)은 서비스 등록이 불가능하다 | **서비스로 등록할지 묻는다.** 등록하면 관리자 권한이 필요하고(격상), 등록하지 않으면 격상 없이 끝난다. 답 항목표에 `service.register`를 더했다 |
| 2 | **서비스 없이 어떻게 시작하는가.** 등록하지 않으면 사용자가 서버를 켜고 끄는 방법이 없다 | 설치 폴더에 시작, 중지, 상태 확인 스크립트(셸과 PowerShell)를 둔다. 부팅 때 자동으로 뜨지 않는다는 점을 설치 끝 화면과 결과 문서에 알린다 |
| 3 | **일반 사용자 구성의 설치 기록 위치.** 기록을 `/var/log/yona-install/`(관리자 영역)에 쓰기로 했는데, 격상하지 않는 구성은 쓸 수 없다 | 시스템 구성은 기존 위치를 쓰고, **일반 사용자 구성은 `~/.local/state/yona-install/`(Linux, Mac), `%LOCALAPPDATA%\Yona\install\`(Windows)에 쓴다.** `uninstall`과 "이미 설치됨" 감지는 두 위치를 모두 본다 |
| 4 | **서비스 이름과 한 서버의 설치 수.** 정하지 않았다 | 서비스 이름은 `yona`로 하고 **한 서버에 한 인스턴스만** 설치한다(여러 개는 범위 밖). 이미 `yona` 서비스가 있고 다른 설치 경로를 가리키면 "이미 설치됨"으로 알리고 중단한다 |
| 5 | **격상 시험.** 시험 전략에 격상이 없었다 | `sudo`는 가짜 실행 파일로 단위 시험하고(허용, 거절, 비밀번호 틀림, 비대화형), 실제 UAC 창은 Windows에서 손으로 확인한다 |
| 6 | **스크립트 주석 언어.** 프로젝트 규칙은 코드 주석을 한글로 API 수준으로 쓰는 것이다 | 스크립트의 주석과 함수 설명도 한글로 쓴다. 사용자에게 보이는 문구는 12번의 메시지 파일(한국어, 영어)이다 |
| 7 | **함께 고칠 기존 문서.** 새 방식과 어긋나는 문서가 있다 | `architecture/fresh-install-guide.md`(설치 절차를 새 스크립트 방법으로), `docs/yona-ssh-system-sshd.md`(본문 끝에 추가하라는 부분을 드롭인으로, `systemctl reload sshd`는 Ubuntu와 Debian의 유닛 이름 `ssh`와 다르다). `docs/ko/install-mariadb.md`는 v1.6 문서라 고치지 않는다. 구현할 때 `differences-from-v16.md`에 새 기능으로 기록한다 |

**확인하지 못한 것**: Debian 계열에서 `sshd`라는 이름의 별칭이 동작하는지는 컨테이너에 systemd가 없어 보지 못했다(위 7번의 `reload sshd`가 실제로 되는지 구현 때 확인한다).

### X-63 함께 설치하는 DB의 입력 항목 (선생님 결정 2026-11-20)

DB를 **함께 설치**하는 경우 스크립트가 아래를 설치자에게 입력받는다. 따로 설치된 DB는 이 항목을 받지 않는다(접속 정보만 받고 데이터베이스와 사용자는 설치자가 준비한다, 앞선 결정).

| 입력 | 뜻 | 기본값 |
|---|---|---|
| DB 이름 | Yona가 쓸 데이터베이스(`yona.*` 접속 주소에 들어간다) | `yona` |
| DB 사용자 | Yona가 접속하는 계정 | `yona` |
| DB 비밀번호 | 그 계정의 비밀번호. **비어 있으면 거절한다**(조용히 무작위로 만들지 않는다) | 없음 |
| **DB 시스템 관리자 ID** | 설치한 DB 서버의 관리자 계정. 스크립트가 데이터베이스와 사용자를 만들 때 쓴다 | 아래 표 |
| **DB 시스템 관리자 비밀번호** | 그 관리자 계정의 비밀번호 | 없음 |

**DB마다 시스템 관리자 ID의 기본값**

| DB | 기본 ID | 근거 |
|---|---|---|
| MariaDB | `root` | 시험 컨테이너와 기존 설치 문서가 `root`로 접속한다(확인함) |
| PostgreSQL | `postgres` | PostgreSQL의 표준 관리자 계정이다(일반 지식, 이 저장소에서 확인하지 않았다) |
| SQL Server | `sa` | 표준 관리자 계정이다(일반 지식, 확인하지 않았다) |
| Cubrid | `dba` | `fresh-install-guide.md`의 접속 주소가 `dba`를 쓴다(확인함) |
| H2 | 없음 | 서버가 없는 파일 DB라 관리자 계정이 없다. 이름과 사용자 입력도 받지 않는다(파일 이름만) |

**에이전트 판단(승인 전)**
- **관리자 비밀번호는 파일에 남기지 않는다.** DB 자체에 설정만 하고, 설치 기록과 결과 문서에도 쓰지 않는다. 설치자가 보관한다. DB 사용자 비밀번호는 서비스 환경 파일(소유자만 읽기)에 두어 Yona가 읽는다(부팅 자동 시작 구간의 규칙).
- **이름 검사**: DB 이름과 DB 사용자는 영문 소문자, 숫자, 밑줄만 받고 첫 글자는 영문자이며 길이를 제한한다(SQL에 들어가는 값이라 이스케이프보다 허용 목록이 안전하다). 비밀번호는 모든 문자를 받되 SQL과 설정 파일에 쓸 때 이스케이프한다.
- **패키지 설치의 관리자 인증**: MariaDB 패키지는 `root`가 운영체제 계정 인증(소켓)으로 접속하는 경우가 많아 비밀번호가 필요 없을 수 있다. 입력받은 관리자 비밀번호를 DB에 어떻게 설정할지(비밀번호 인증으로 바꿀지)는 **구현 때 각 배포본과 DB에서 확인한다**(확인하지 못한 것).
- **컨테이너 설치**: 관리자 비밀번호는 컨테이너 시작 때 환경 변수나 비밀 값으로 준다(SQL Server의 `sa`는 설치 때 비밀번호가 필수다). 컨테이너 명령줄에 비밀번호가 보이지 않게 파일로 전달한다.
- 관리자 ID와 비밀번호의 **비대화형 입력**은 파일 경로로만 받는다(답 항목표의 `db.admin_user`, `db.admin_password_file`).

### X-63 비대화형 답 항목표 (초안)

질문마다 답 파일의 키와 옵션 이름을 정한다. 에이전트가 앞서 정한 질문을 모아 만든 초안이다.

| 키 | 옵션 | 뜻 |
|---|---|---|
| `lang` | `--lang` | `ko` 또는 `en` |
| `mode` | `--mode` | `install`, `migrate` (업그레이드는 감지로 정해진다) |
| `version` | `--version` | 설치할 릴리스 태그 |
| `install_dir`, `data_dir` | `--install-dir`, `--data-dir` | 설치 폴더, 데이터 폴더 |
| `db.type` | `--db` | `postgresql`, `mariadb`, `cubrid`, `mssql`, `h2` |
| `db.install` | `--db-install` | `together`(함께 설치), `separate`(따로 설치) |
| `db.method` | `--db-method` | `container`, `package` |
| `db.host`, `db.port`, `db.name`, `db.user` | `--db-host` 외 | 따로 설치된 DB의 접속 정보 |
| `db.password_file` | `--db-password-file` | DB 사용자의 비밀번호가 든 파일(소유자만 읽기) |
| `db.admin_user` | `--db-admin-user` | 함께 설치하는 DB의 시스템 관리자 ID(기본은 DB마다 다르다, 아래 "함께 설치하는 DB의 입력 항목") |
| `db.admin_password_file` | `--db-admin-password-file` | 그 시스템 관리자의 비밀번호가 든 파일 |
| `site.hostname`, `site.port`, `site.scheme` | `--hostname`, `--public-port`, `--scheme` | 사이트 주소 |
| `site.behind_https_proxy` | `--behind-https-proxy` | HTTPS 프록시 뒤 운영 |
| `web.port` | `--port` | 웹 포트 |
| `mail.enabled`, `mail.host`, `mail.port`, `mail.ssl`, `mail.user`, `mail.password_file`, `mail.from_user`, `mail.from_domain`, `mail.mock` | `--mail-*` | 메일 서버와 모의 발송 |
| `ssh.mode` | `--ssh` | `none`, `builtin`, `system`(Linux, Mac만) |
| `ssh.port`, `ssh.public_port` | `--ssh-port`, `--ssh-public-port` | 내장 SSH 포트와 바깥 포트 |
| `v16.dir`, `v16.conf`, `v16.db`, `v16.timezone` | `--v16-dir` 외 | 이관 입력 |
| `service.register` | `--service` | 서비스로 등록하고 부팅 때 자동 시작할지(`yes`, `no`) |
| `install_jdk` | `--install-jdk` | JDK 설치 동의 |
| `confirm.backup`, `confirm.replace_data` | `--confirm-backup`, `--confirm-replace-data` | 위험한 동작의 확인 |
| `yes`, `dry_run`, `offline`, `download_only`, `keep_on_failure`, `allow_downgrade` | 같은 이름 | 동작 방식 |

### X-63 업그레이드 모드 (선생님 결정 2026-11-20)

서버에 이미 설치된 Yona와 **다른 버전**을 고르면 **업그레이드**로 본다. 새로 설치하는 것이 아니라 기존 설치를 **새 jar로 바꾸고 다시 시작하는 것**이다. 질문 흐름은 신규와 이관과 달리 묻는 것이 거의 없다(DB, 사이트 주소, 메일, SSH는 기존 설정을 그대로 쓴다).

| 순서 | 내용 |
|---|---|
| 1 | 사전 점검(권한, 디스크, JDK, 네트워크)과 설치된 버전 읽기 |
| 2 | 버전 방향 확인: 고른 버전이 **더 높으면** 업그레이드. **더 낮으면(되돌리기)** 막고 알린다 |
| 3 | 고른 릴리스의 jar를 받아 sha256 검증 |
| 4 | **백업 확인**: 데이터베이스를 백업했는지 묻는다(스키마가 시작할 때 바뀌어 되돌릴 수 없다). 비대화형은 `--confirm-backup`을 요구한다 |
| 5 | 서비스를 멈추고 옛 jar는 이름을 바꿔 보관(`yona-<옛 버전>.jar.bak`)한 뒤 새 jar를 둔다 |
| 6 | 서비스를 시작하고 `/-_-actuator/health`로 확인한다 |
| 7 | 실패하면 옛 jar로 되돌려 다시 시작한다(6번 결정의 되돌리기 규칙). 단 **서버가 이미 스키마를 바꿨을 수 있어** 옛 jar가 못 뜰 수 있다는 것을 알린다 |
| 8 | 설치 기록과 결과 문서(`INSTALL-REPORT.md`)를 새 버전으로 갱신한다 |

**에이전트 판단(선생님 수용 2026-11-20)**
- **더 낮은 버전으로는 바꾸지 않는다.** 스키마를 서버가 시작할 때 `ddl-auto: update`로만 더하므로 낮은 jar가 높은 스키마에서 동작한다는 보장이 없다. 필요하면 `--allow-downgrade`로 풀되 위험을 알린다.
- **`rc` 같은 사전 배포 버전으로 올릴 때는 확인을 한 번 더 받는다**(정식 릴리스에서 사전 배포로 가는 경우). 사전 배포에서 정식 릴리스로 올리는 것은 그냥 업그레이드다.
- 설정과 데이터(`application.yml`, 데이터 폴더, DB)는 **건드리지 않는다.** 새 버전에 필요한 새 설정 키가 있어도 스크립트가 쓰지 않고 안내만 한다(구현 때 릴리스 노트에서 알릴 방법을 정한다).
- 서비스가 멈춰 있는 동안 끊긴다는 것을 사전에 알린다.

**확인하지 못한 것(선생님이 한계로 수용하심, 2026-11-20)**: 두 버전 사이의 스키마 변경이 `ddl-auto: update`로 안전하게 맞춰지는지는 버전 쌍마다 다르다. 릴리스마다 호환성 시험이 필요하다(이 저장소에 버전 사이 업그레이드 시험은 없다).

### X-63 한 줄 설치 (선생님 결정 2026-11-20)

한 줄로 설치를 시작할 수 있게 한다. 처음에는 에이전트가 두지 않는 쪽(받은 파일을 읽어 볼 수 있어야 한다는 이유)을 택했으나 선생님이 필요하다고 정하셨다. **읽어 보고 실행하는 방법도 함께 안내한다.**

| 운영체제 | 한 줄 | 읽어 보고 실행 |
|---|---|---|
| Linux, Mac | 릴리스 자산의 부트스트랩 스크립트를 받아 실행(`curl -fsSL <릴리스 자산 주소> \| bash`). 관리자 권한은 스크립트가 필요할 때 `sudo`로 올린다 | 같은 주소를 파일로 받아 읽고 `bash 파일`로 실행 |
| Windows | PowerShell에서 `irm <릴리스 자산 주소> \| iex`. 관리자 권한은 스크립트가 필요할 때 UAC로 올린다 | 파일로 받아 읽고 실행 |

*(위 주소와 명령의 모양은 에이전트가 정한 안이며 구현 때 시험으로 확인한다.)*

**설계 규칙(에이전트 판단, 승인 전)**
- 한 줄이 실행하는 것은 **작은 부트스트랩**이다. 부트스트랩이 릴리스 목록에서 버전을 고르게 하고, **고른 릴리스의 설치 스크립트와 메시지 파일 등을 받아 sha256을 검증한 뒤 실행**한다(7번: 모든 파일은 선택한 릴리스 자산에서). 이렇게 해야 부트스트랩의 버전과 설치할 버전이 어긋나지 않는다.
- 주소는 HTTPS만 쓰고 GitHub 릴리스 자산 주소만 쓴다. 최신 릴리스를 가리키는 고정 주소를 쓸지, 버전을 적은 주소를 쓸지는 구현 때 정한다.
- **입력**: `curl | bash`에서는 표준 입력이 스크립트 본문이라 질문을 받을 수 없다. 질문은 터미널(`/dev/tty`)에서 읽고, 터미널이 없으면 비대화형 모드로 간주해 답이 모자라면 아무것도 설치하지 않고 중단한다.
- **끊김 방지**: 스크립트를 다 받은 뒤에만 실행되도록 마지막 줄에서 `main`을 부른다(받다가 끊긴 반쪽이 실행되지 않게).
- **관리자 권한**: 11번대로 **일반 사용자로 시작하고 필요한 단계에서만 격상한다.** 격상할 수 없으면(비대화형이고 `sudo -n`도 안 될 때) 모자란 권한을 알리고 중단한다.
- **폐쇄 환경에서는 쓸 수 없다.** 인터넷이 없으면 `--download-only`로 만든 묶음을 그 디렉터리에서 실행한다.

**체크섬 후속(선생님 결정 2026-11-20)**: 지금은 릴리스 API의 `sha256` digest로 검증한다(일단 수용). **차후에 릴리스에 sha256 체크섬 파일을 함께 올려** 그 파일로도 점검한다. 폐쇄 환경 묶음에서는 이 파일이 검증 근거가 된다.

### X-63 부팅 때 자동 시작 (선생님 지시 2026-11-20)

등록만 하고 끝나면 서버를 다시 켠 뒤 Yona가 뜨지 않는다. **서비스 등록은 "부팅 때 자동으로 시작"까지를 한 단계로 본다**(서비스로 등록하기로 했을 때. 등록 여부는 설치자에게 묻고, 등록하지 않으면 격상 없이 끝나며 시작 스크립트로 직접 켠다. 위 "3차 점검" 1번과 2번). Yona와 함께 설치한 DB도 같은 기준으로 부팅 때 자동으로 올라와야 하고, DB가 먼저 뜬 뒤에 Yona가 시작되어야 한다.

| 운영체제 | Yona | 함께 설치한 DB | 순서 |
|---|---|---|---|
| Linux | `yona.service`(systemd)를 만들고 `enable`한다. 전용 계정으로 실행하고 비정상 종료 때 다시 시작한다(`Restart=on-failure`). 첫 시작은 스키마를 만드느라 오래 걸릴 수 있어 시작 제한 시간을 넉넉히 둔다 | 시스템 패키지: 해당 서비스(`mariadb`, `postgresql` 등)를 `enable`한다. 컨테이너: Docker는 재시작 정책(`unless-stopped` 등)과 `docker.service`의 `enable`, Podman은 컨테이너를 systemd 단위로 만들어 `enable`한다(비루트 Podman은 로그인 없이도 뜨도록 `loginctl enable-linger`가 필요하다) | `After=`와 `Requires=`(또는 `Wants=`)로 DB 단위 뒤에 시작하게 한다. 네트워크는 `network-online.target` 뒤 |
| Mac | LaunchDaemon(`/Library/LaunchDaemons`)으로 등록해 **로그인하지 않아도** 부팅 때 뜨게 하고 `RunAtLoad`와 `KeepAlive`를 둔다. 로그인할 때만 뜨는 LaunchAgent는 쓰지 않는다 | **Mac은 DB를 컨테이너로만 설치한다**(시스템 패키지 설치는 선택지에서 뺀다). 컨테이너는 Docker Desktop보다 Podman을 권장한다(Docker Desktop은 로그인해야 뜨는 경우가 많아 서버 용도에 맞지 않을 수 있다) | launchd에는 단위 사이의 의존 관계가 없어, 시작 스크립트가 DB 접속이 될 때까지 기다린 뒤 서버를 시작하게 한다 |
| Windows | 서비스 등록 도구(WinSW 또는 `sc`)로 Windows 서비스를 만들고 시작 유형을 **자동(지연된 시작)**으로 한다. 실패 때 다시 시작하는 복구 동작을 둔다. 로그인 없이 뜬다 | 시스템 패키지(설치 관리자가 만든 서비스)는 시작 유형이 자동인지 확인한다. 컨테이너는 Docker Desktop보다 Podman을 권장한다고 안내한다(Docker Desktop은 로그인해야 뜨는 경우가 많아 서버 용도에 맞지 않을 수 있다) | 서비스 의존 관계(`depend=`)로 DB 서비스 뒤에 시작하게 한다 |

**공통 규칙**
- 서비스 파일에는 비밀 값(DB 비밀번호, `yona.secret`)을 직접 쓰지 않고 소유자만 읽는 파일(`0600` 또는 그에 준하는 권한)을 가리킨다.
- 서비스 등록에 쓰는 JDK 경로와 jar 경로, `yona.data-dir`은 절대 경로로 고정한다(폐쇄 환경에서 같은 디렉터리의 jar를 쓰더라도 설치 위치로 복사해 등록한다).
- 이관 모드에서는 **v1.6 서비스를 끄고 부팅 때도 시작하지 않게(`disable`)** 해야 한다. 같은 포트와 DB를 두고 부딪치기 때문이다. 스크립트는 v1.6 서비스를 임의로 지우지 않고 안내한다.
- 이미 설치된 서비스가 있으면 덮어쓰지 않고 알린다.

**설치 완료의 기준(검증)**: 설치 끝의 상태 확인(`/-_-actuator/health`)에 더해, **다시 부팅한 뒤에도** DB와 Yona가 올라와 같은 상태 확인이 통과해야 한다. 구현 때 세 운영체제에서 재부팅 시험을 한다.

**구현 전에 확인할 것(지금은 일반 지식이고 이 저장소에서 확인한 것이 아니다)**
- Podman 컨테이너의 부팅 자동 시작 방식(systemd 단위 생성 또는 Quadlet), 비루트일 때의 `linger` 필요 여부
- Mac에서 LaunchDaemon으로 DB와 Yona를 순서대로 시작하는 방법
- Mac과 Windows에서 Podman(`podman machine`)이 **로그인 없이 부팅 때 시작되는지**와 그 방법. 권장은 선생님의 지시이나 부팅 자동 시작이 되는지는 확인하지 못했다. 되지 않으면 안내 문구와 설치 방법을 다시 정한다
- Windows에서 서비스 등록 도구로 WinSW를 쓸지 `sc`만 쓸지(WinSW는 외부 구성요소라 폐쇄 환경 묶음에 포함해야 한다)

### X-63 폐쇄 환경 실행 (선생님 지시 2026-11-20)

인터넷에 나갈 수 없는 서버에서도 설치할 수 있어야 한다. 필요한 파일을 한 디렉터리에 모아 옮겨 놓고 그 디렉터리에서 스크립트를 실행하면 된다.

| 규칙 | 내용 |
|---|---|
| 실행 위치 | 스크립트는 **자기 디렉터리를 기준으로** 같은 디렉터리의 파일을 먼저 찾는다. 현재 작업 디렉터리나 절대 경로에 의존하지 않는다 |
| 같은 디렉터리의 파일 | 배포 jar와 체크섬 파일, 이관 스크립트(`migrate-v16.sh`, `migrate-v16-h2.sh`), H2 변환기(`tools/h2-export/H2Export.java`), (선택) 컨테이너 이미지 묶음 |
| 있으면 쓴다 | jar가 있으면 **버전 목록 조회와 내려받기를 건너뛰고** 그 jar를 쓴다. 체크섬 파일이 함께 있으면 검증하고, 없으면 검증하지 못했다고 알린다 |
| 없으면 받는다 | 같은 디렉터리에 없는 파일만 **선택한 GitHub 릴리스의 자산**에서 받는다. 네트워크가 막혀 있으면 어떤 파일이 모자란지 목록으로 알리고 중단한다(일부만 설치해 두지 않는다) |
| 인터넷이 필요한 단계 | 버전 목록 조회, 내려받기, DB 시스템 패키지 설치, 컨테이너 이미지 받기. 폐쇄 환경에서는 **DB를 따로 설치한 것으로 보고 접속 정보를 입력받는 길**이 기본이 되고, 컨테이너는 이미지가 이미 있을 때만 쓴다 |
| 폐쇄 환경 점검 | 시작할 때 외부 접속 가능 여부를 한 번 확인하고, 안 되면 폐쇄 모드로 안내한다(`--offline`으로 강제할 수도 있다) |
| 준비 방법 | 인터넷이 되는 곳에서 같은 스크립트로 파일만 내려받는 모드(`--download-only`, 설치는 하지 않음)를 두어 묶음을 만든다 |

**결정(2026-11-20 선생님)**: `--download-only` 묶음은 jar, 체크섬, 이관 스크립트, H2 변환기까지만 담는다. JDK와 DB 설치 파일은 묶지 않고, 서버에 미리 있어야 한다고 안내한다(운영체제별 설치 파일 형식이 달라 묶음이 매우 커지기 때문).

### X-63 MariaDB 12.3.3 확인 기록 (2026-11-20)

기본 버전을 12.3.3으로 정하기 전에, v2가 이 버전에서 동작하는지 DB 시험 일부를 돌려 확인했다.

| 항목 | 내용 |
|---|---|
| 서버 | `mariadb:12.3.3` 컨테이너(`12.3.3-MariaDB-ubu2404`). 기존 10.11 컨테이너는 건드리지 않았다 |
| 방법 | `YONA_IT_MARIADB_IMAGE=mariadb:12.3.3 ./gradlew test -Dyona.it.db=mariadb --tests ...` |
| 시험 | `V16SchemaTest` 9건, `AddedTablesSchemaTest` 9건, `SearchTest` 35건, `DatabaseDefaultsTest` 7건 |
| 결과 | 60건 모두 통과(실패 0, 건너뜀 0), 소요 1분 34초 |
| 서버 기본값 | `character_set_server=utf8mb4`, `collation_server=utf8mb4_uca1400_ai_ci`, `lower_case_table_names=0` |

**알게 된 것**
- 새 설치에 `lower_case_table_names=1`은 필요하지 않았다(기본값 0에서 통과).
- 서버의 기본 정렬 규칙이 10.11과 달라도, 데이터베이스를 `utf8mb4_bin`으로 만들면 통과했다. 스크립트는 서버 설정 파일을 건드리지 않고 데이터베이스, 사용자, 권한만 만들면 된다.

**확인하지 못한 것**
- 12.3.3에서의 v1.6 이관 리허설(`migrate-v16.sh`)과 전체 시험은 돌리지 않았다. 이관 모드에서 12.3.3을 쓰려면 리허설을 먼저 한다.
- 위키 문서의 `query_cache_*`, `init-connect`, `skip-character-set-client-handshake`의 효과는 확인하지 않았고 스크립트에 넣지 않는다.

**시험 코드 변경**: `TestDatabase.kt`가 환경 변수 `YONA_IT_MARIADB_IMAGE`로 MariaDB 이미지를 바꿀 수 있게 했다(지정하지 않으면 `mariadb:10.11`).

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


## 보류한 항목 (선생님 결정)

- **Cubrid 성능 추가 개선 (2026-11-17):** 선생님이 여기서 마무리하기로 하셨다. 나중에 문서를 더 보거나 기여로 이어 간다. 적용한 것(A-629, A-630, A-631)과 시도해 효과가 없던 것은 [db-performance-comparison.md](architecture/db-performance-comparison.md) 6절과 10절에 있다. 이어서 할 후보는 둘이다. (1) 요청당 질의를 더 줄이기: 규칙(`rulesets`, 즉시 로딩 D-46)을 `RequestMemo`로 요청 범위에 보관하면 이슈 단건 API가 18개에서 약 14개로 줄 것으로 추정한다(구현하지 않음). (2) 운영 안내에 CPU 증설 효과를 적기: CPU를 4개에서 8개로 늘리면 JDBC 단독 읽기가 2배가 된다. 설정으로 더 얻을 것은 없다고 판단했다.

- **도움말 다국어 (R-51-2, 2026-11-05):** 도움말(`help/toc`, `help/markdown`, `help/keymap`)을 언어별 템플릿 파일로 나누는 일은 가장 나중의 영역으로 보류했다. 지금 하지 않는다. 한국어는 v1.6 그대로 두고 영어를 새로 쓰며, 일본어, 러시아어, 우즈베크어는 영어와 같게 둔다.
