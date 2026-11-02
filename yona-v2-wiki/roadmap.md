---
title: 이식 로드맵과 진행 상태
type: synthesis
sources: [../AGENTS.md, ../yona-v1.6-wiki/web/templates/*.md]
updated: 2026-11-02
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
| X-3 | SSH 공개키 등록, SSH 접근, 저장소별 Deploy Key | `domain/sshkey`, `ssh` | **구현 완료(2026-11-02, 내장 SSH 서버), 결정은 D-66** | 설명은 [architecture/ssh.md](architecture/ssh.md). 설정 `yona.ssh.enabled`(기본 꺼짐)로 켜는 Apache MINA SSHD, 사용자 SSH 키와 프로젝트 배포 키(읽기 전용 기본), 목록과 추가 두 화면(삭제 버튼만), REST, 감사와 지표, 프로젝트 홈의 SSH 복제 주소. 실제 `ssh`와 `git`으로 복제, 푸시, 읽기 전용 배포 키의 푸시 거부, 보호 규칙 적용을 확인했다. 명령은 저장소 종류별 처리기라 X-7(Mercurial)이 같은 자리에 더한다. **시스템 sshd 연동(X-3b, D-67, `docs/yona-ssh-system-sshd.md`)과 후속(D-68: 위키 저장소 SSH, SSH 키 서명 검증, 빈 저장소 안내와 위키의 SSH 주소, 배포 키 푸시의 사용자 귀속)도 구현했다. SVN의 SSH 접근은 하지 않기로 했다** |
| X-4 | GPG 커밋 서명 검증(키 등록, 커밋과 태그의 Verified 표시, REST, 서명 커밋 필수 규칙) | `domain/gpgkey` | **구현 완료(2026-11-02), 결정은 D-65** | 일곱 단계가 모두 끝났다. 설명은 [architecture/gpg-signing.md](architecture/gpg-signing.md). BouncyCastle 직접 검증, 실제 gpg 2.4.8과 git의 키와 서명으로 호환 확인. 만료와 폐기 키는 지금 기준으로 Unverified. 설정 화면은 목록과 추가 두 화면(삭제 버튼만) |
| X-5 | 개인용 API 토큰(PAT, 범위 지정) | `domain/apitoken` | **구현 완료(2026-10-29)** | D-36: Classic 토큰을 먼저 도입하고 v1.6 사용자 토큰은 레거시 Classic 토큰으로 이관한다(공존이 아니라 이관). 모든 토큰이 사용자 권한을 확인한다(D-20 대체). Fine-grained는 X-5b로 나눈다. 상세는 [architecture/api-token.md](architecture/api-token.md) |
| X-6 | MCP 서버(OAuth2로 인증한 AI 에이전트의 이슈, 풀 리퀘스트, 위키 접근) | `mcp` | **완료(2026-11-02)** | 구현과 문서는 [architecture/mcp-server.md](architecture/mcp-server.md), 결정은 D-72(A-330). 전송은 Streamable HTTP(세션 없음), OAuth 토큰만(PAT 불가), 읽기 전용 범위 `read:repo`, 토큰 대상 검증(RFC 8707). 공식 MCP SDK 클라이언트로 실제 jar에 인가부터 도구 호출까지 확인했다. 실제 Claude Code, Codex, Antigravity 연결은 선생님이 확인한다(절차는 mcp-server.md 7절) |
| X-7 | Mercurial 저장소(hg4j): 코드 보기, 커밋 이력, bookmark와 named branch, HTTP와 SSH 서빙, zip 내려받기, 브랜치 보호와 push 알림·웹훅, 풀 리퀘스트 병합, GPG 서명 | `domain/vcs`, `config/hg`, `HgController` | 대기 | 저장소 종류 변경(v1.6 `changeVCS`)과 `vcs` 열 값에 영향. hg4j는 `io.github.search5.hg4j` 별도 라이브러리 **선행 정리(2026-10-29)**: 착수 전에 `@IsOnlyGitAvailable`(컨트롤러 7곳)과 `VCS_GIT`, `VCS_SUBVERSION` 분기(9개 파일, 39곳)를 "지원하는 VCS를 선언하는 방식"(예: `@SupportedVcs(GIT, MERCURIAL)`)으로 정리한다. 기능마다 Git 전용, Git과 Mercurial, 모든 VCS로 갈리므로 이름만 바꾸는 일이 아니다. 그때까지는 v1.6 이름을 유지한다 |
| X-8 | Git LFS | `.well-known`, `git-lfs` 라우트 | 대기 | |
| X-9 | 감사 로그(보안, 관리 이벤트). 엔터프라이즈 수준: 행위자와 IP와 결과와 변경 전후, 해시 사슬, 365일 보존, 내보내기 | `domain/audit` | **구현 완료(2026-11-01), 결정은 D-62, D-63** | 1차 A1~A8과 2차(해시 v2와 git 이벤트 비움 B2, git 전송 이벤트 B3, 웹훅과 syslog 스트리밍과 미전송 기록 보존 보호 B4~B6)를 모두 구현했다. 설명은 [architecture/audit-log.md](architecture/audit-log.md), 구현 판단은 A-318, A-319 |
| X-10 | 관측성: OpenTelemetry(추적), Actuator와 Prometheus(지표), JSON 로그(logstash) | `build.gradle.kts`의 opentelemetry, actuator, micrometer 스타터 (P3-01) | **완료(2026-11-01)** | D-64: 세 신호 모두 OTLP로 Alloy에 보내 Grafana 스택(Prometheus, Tempo, Loki)에서 본다. 앱 포트에는 health만 연다. 상세는 [architecture/observability.md](architecture/observability.md), 설정은 `observability/` |
| X-11 | 풀 리퀘스트 병합 시 `close(s/d)`, `fix(es/ed)`, `resolve(s/d) #번호`로 참조된 이슈 자동 닫기 | `domain/event/PullRequestMergeEventListener` | **완료(2026-10-29)** | `PullRequestWorkflow.accept`가 병합한 뒤 `PullRequestIssueCloser`로 처리한다(A-188). `next` 정규식의 `fixes/fixed` 인식 실패를 바로잡았다 |
| X-12 | 풀 리퀘스트 리뷰 판정(Approve, Request changes, Comment). 같은 리뷰어의 재판정은 이력으로 쌓고 정책 판단에는 리뷰어별 최신 판정만 쓴다 | `domain/pullrequest/PullRequestReview`, `PullRequestServiceImpl` | **구현 완료(2026-10-29)** | D-35: v1.6의 "리뷰 완료/취소"를 GitHub 리뷰 모델에 흡수한다(별개가 아니다). 새 테이블 `pull_request_review`, 상세는 [web/pull-request-review.md](web/pull-request-review.md) |
| X-13 | 브랜치 보호 규칙(브랜치 패턴, 풀 리퀘스트 필수, 필요한 승인 수, 서명 커밋 필수, push 허용 사용자 제한, force push와 삭제 금지, 매니저 우회). push 훅과 병합 검사에 적용 | `domain/branchprotection`, `web/BranchProtectionController`, `domain/vcs/GitPushHooks` | **구현 완료(2026-10-30): X-32부터 X-34의 규칙 묶음이 대체했다.** 남은 것 없음(push 허용 사용자 지정은 X-35의 바이패스 목록이 맡는다). 서명 커밋 필수는 X-4에서 구현했다(2026-11-02) | 브랜치 패턴, 풀 리퀘스트 필수, 필요한 승인 수, force push와 삭제 금지, 관리자 우회는 규칙 묶음(`Ruleset`)에 있다. 아래 설명은 처음 계획이며 클래스 이름은 구현과 다르다. X-4(GPG), X-12(리뷰 판정)가 있어야 해당 규칙이 의미를 갖는다. 새 테이블 필요 **후보(2026-10-29, 보류)**: `refs/heads`, `refs/tags` 밖의 참조(`refs/remotes/*` 등) 푸시를 거부하는 정책. 푸시로 만들 수 있음을 v2와 v1.6에서 확인했다(D-30 7번). 선생님이 나중에 다루기로 하셨다 |
| X-14 | 풀 리퀘스트 담당자(assignee)와 라벨 | `PullRequest.assignee`, `PullRequest.labels` | **구현 완료(2026-10-29), 후속(Ajax, 2단 화면, 모든 목록 표시, 담당자 필터 선택지, 생성 폼 지정)도 구현 완료** | v1.6 `pull_request`에는 담당자, 라벨 열이 없다. 연결 테이블 추가 필요 **설계 결정(2026-10-29)**: [decisions.md](decisions.md) D-34를 본다. 연결 테이블 2개(`pull_request_assignee`, `pull_request_issue_label`)로 구현하고 담당자는 1명까지이며 나중에 여러 명으로 키울 수 있게 한다. 담당자 변경은 타임라인과 알림, 목록에 담당자와 라벨 필터를 넣는다 후속(D-34 14~16): 이슈와 같은 Ajax 방식, 모든 풀 리퀘스트 목록에 표시, 모양 다듬기 |
| X-15 | 풀 리퀘스트 REST API(조회, diff, 리뷰, 타임라인, 병합, 닫기, 다시 열기, 댓글, 리뷰어, 라벨, 담당자, 출발 브랜치 삭제) | `/api/projects/{}/pullrequests/**`, `/api/v1/projects/{}/{}/pull-requests/**` | **구현 완료(2026-10-29), 결정은 D-39** | 계약은 [architecture/api-pull-requests.md](architecture/api-pull-requests.md)이다. 병합 방식(`method`)은 필드만 예약했고 X-28에서 연다. 풀 리퀘스트 만들기와 고치기(`POST`, `PATCH`)와 첨부(`temporaryUploadFiles`)도 포함한다. 라벨 목록 API는 X-19에서 만든다
| X-16 | 엔터프라이즈 SSO(SAML2, 범용 OIDC) 사이트 관리자 설정과 로그인, 소셜 로그인 계정 병합 | `config/sso`, `domain/sso`, `SsoAdminController`, `OAuth2AccountMergeService` (P3-06) | 대기 | 테이블 `oidc_sso_settings`, `saml2_sso_settings` 필요. v1.6 소셜 로그인은 그대로 둔다 |
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
| X-20 | 프런트엔드 현대화: Vue 3 웹 컴포넌트 위젯 17개(대화상자, 토스트, 드롭다운, 자동 완성, 첨부, 리뷰 폼, 라벨 편집기 등), Tom Select, Flatpickr, CodeMirror 6와 EasyMDE 에디터, jQuery 제거(순수 JS), Hotwired Turbo(이슈 2단 목록) | `static/lib/yona-vue-widgets`, `frontend/`, `yona.issue.Turbo.js` (P3-46, P3-70) | 대기 | 화면 동작은 v1.6과 같게 둔다는 조건이 필요하다. `yona-v2-wiki`의 Vue 위젯 소스 위치 메모(components 저장소)와 이어진다 |
| X-21 | 사용 현황 집계(UA 태그를 GA4 gtag로 교체) | `config/analytics/AnalyticsSettings` | **완료(2026-10-28)** | v1.6의 `yona.send-yona-usage` 스위치를 유지하고 UA 태그를 GA4로 교체했다(A-186). 측정 ID는 `yona.analytics.measurement-id` |
| X-22 | 새 프로젝트의 기본 브랜치를 `main`으로 고정 | P3-11 | **완료(2026-10-28)** | `next` 기준으로 `yona.git-default-branch`(기본 `main`)를 `HEAD`로 고정한다(A-187) |
| X-23 | Apache Magpie 연동(이슈 계약 `contract:tracker`, PR 계약 `contract:change-request`) | 없음([magpie-integration.md](architecture/magpie-integration.md)) | **보류(쌓아 둠, 2026-10-28 선생님)** | 선행: X-5(PAT), X-12(리뷰 판정), X-15(PR REST), X-19(일반 REST). 이 항목들이 채우지 못하는 것은 기간·작성자 조건의 이슈 목록과 이슈·PR 활동 조회뿐이다. 착수 전에 D-29를 결정한다 |
| X-24 | yona CLI(Go 단일 바이너리, `gh`와 비슷한 사용법). **`../yona-cli`에 이미 약 9,300줄이 있다**(이슈, 풀 리퀘스트, 프로젝트, 라벨, 태그, 조직, 검색, 위키, 상태, 별칭, 설정, 관리자). 이전 구현(`yuna`)의 `/api/v1/...`을 부르도록 만들어져 있어, 이 서버의 `/-_-api/v1` 계약에 맞추는 일이 남았다 | `../yona-cli`의 `internal/api`, `cmd` | **구현 완료(2026-10-30, 위키 명령은 X-17 뒤에 `cmd/wiki.go`로 추가), 결정은 D-51** | 방침: CLI의 클라이언트(`internal/api`)를 `/-_-api/v1` 경로와 응답 모양으로 바꾸고 서버는 바꾸지 않는다. 서버에 대응 REST가 없는 명령은 목록을 만들어 따로 정한다([architecture/cli-api-gap.md](architecture/cli-api-gap.md)) |
| X-37 | CLI를 위한 서버 보강(상위 항목): 대응 REST가 없던 명령의 서버 기능과 기기 인증을 열 단계(X-37a~k)로 만든다 | 외부 REST, 기기 인증 | **구현 완료(2026-10-30), 결정은 D-52** | 근거는 [architecture/cli-api-gap.md](architecture/cli-api-gap.md). 하위 단계 a~j가 모두 끝났거나 결정되었다(h는 서버 변경 없음). k(문서)는 단계마다 한다. 위키 명령은 X-17이 끝난 뒤 추가했다(`wiki attach`, `wiki file` 포함) |
| X-37a | 프로젝트 REST: 만들기(`POST /user/projects`, `POST /orgs/{org}/projects`), 설정 바꾸기(`PATCH $P`), 지우기(`DELETE $P`) | `ExternalProjectManageApiController` | **구현 완료(2026-10-30)** | 일반 사용자도 토큰으로 프로젝트를 만든다. 추적표는 [architecture/api-cli-support.md](architecture/api-cli-support.md) |
| X-37b | 프로젝트 구성원 REST: 더하기, 역할 바꾸기, 빼기 | `ExternalProjectMemberApiController` | **구현 완료(2026-10-30)** | 조회(`GET $P/members`)는 있다 |
| X-37c | Git 태그 REST: 목록, 만들기, 지우기 | `ExternalTagApiController` | **구현 완료(2026-10-30)** | |
| X-37d | 이슈 옮기기 REST: `POST $P/issues/{n}/transfer` | `ExternalIssueExtrasApiController` | **구현 완료(2026-10-30)** | |
| X-37e | 사용자 현황 REST: `GET /user/status`, 내 이슈의 `commented`, `shared` 필터 | `ExternalUserStatusApiController` | **구현 완료(2026-10-30)** | |
| X-37f | 풀 리퀘스트 검색: `GET /search/pullrequests`와 프로젝트 목록 조건 | `ExternalSearchApiController`, `ExternalPullRequestApiController` | **구현 완료(2026-10-30)** | 웹 검색은 그대로 둔다 |
| X-37g | 사이트 백업 REST: `GET /admin/export`, `POST /admin/import` | `ExternalSiteBackupApiController` | **구현 완료(2026-10-30)** | |
| X-37h | 리뷰어 추가와 삭제 | `ExternalPullRequestWriteApiController`(기존 `requestedreviewers`) | **결정: 서버 변경 없음** | CLI는 `pr edit --add-reviewer/--remove-reviewer`(GitHub 방식), D-52 |
| X-37i | 기기 인증(Device Flow): 서버의 기기 코드 발급, 승인 화면, 토큰 발급 | `domain/deviceauth`, `ExternalDeviceAuthApiController`, `DeviceAuthorizationController` | **구현 완료(2026-10-30)** | 새 테이블 `device_authorization`. X-1(OAuth2 서버)과 별개이다 |
| X-37j | CLI(`../yona-cli`): 클라이언트를 `/-_-api/v1`에 맞춤, 새 명령, 기기 로그인, Git 자격 증명 도우미 | `../yona-cli` | **구현 완료(2026-10-30)** | 위키 명령은 서버 위키(X-17)가 끝난 뒤 추가했다 |
| X-37k | 문서: 각 단계의 REST를 OpenAPI와 추적표에 올린다 | `web/api/docs` | 단계마다 | 구조 시험이 빠지면 실패한다 |
| X-38a | Bootstrap 전환 0단계: 전 화면 확인 도구(구조 검사 시험, 고정 데이터, 화면 목록 121개, 순회 캡처, 상호작용 점검, 전후 비교) | `e2e/`, `LegacyBootstrapLintTest` | **구현 완료(2026-10-30)** | 기준 순회 `pre-bs5`: 121/121 통과(알려진 v1.6 JS 오류 1건 `compare`). 문서는 `architecture/bs-migration-harness.md` |
| X-38b | Bootstrap 2→3(**공식 이전 안내서 "Migrating from v2.x to v3.0"를 기준으로 항목별로 대응**, 선생님 지시): 마크업을 안내서대로 바꾸고 BS3 CSS와 JS(3.4.1)를 들이고 호환 층으로 v1.6 모양을 유지 | 템플릿 192개, `app/assets/stylesheets/less/_bs3-compat.less`, JS 번들, `build-tools/minify-js.sh` | **구현 완료(2026-10-30)** | 전 화면 순회 121/121 통과, 상호작용 점검이 기준과 같음, 캡처 평균 차이 0.7%(중앙값 0.1%). 점검표 `architecture/bs3-migration-checklist.md`, 결정 A-284~286. 태그 `bs3` |
| X-38c | Bootstrap 3→4: 자체 LESS를 SCSS로 재작성(**1구간 완료**: LESS와 CSS 동일, 순회 픽셀 차이 0, 태그 `scss`), 이어서 flexbox 그리드(`col-md`→`col-lg`), `panel`/`well`→`card`, `label`→`badge`, Glyphicons 대체, 폼과 내비게이션과 모달 변경, 호환 층 흡수, BS4 SCSS 도입(공식 이전 안내서 "Migrating to v4" 기준) | 템플릿, `app/assets/stylesheets`, 스타일 빌드 | **구현 완료(2026-10-31)**, 태그 `bs4` | 점검표와 호환 층 의존 장부는 `architecture/bs4-migration-checklist.md`. 순회 121/121, 기준 대비 새 문제 0, 평균 픽셀 차이 0.3%, 시험 3027개 통과. **호환 층에 남은 BS2 규칙(단추 그룹, 표, 알림, 그리드, 요소 기본값 약 1,100줄)은 BS5에서 변수와 마크업으로 옮길지 정한다** |
| X-38d | Bootstrap 4→5(**공식 이전 안내서 "Migrating to v5"를 기준으로 항목별 대응**): 5.3.8 SCSS와 번들, `data-bs-*` 속성, 유틸리티 이름(`float-start`, `ps-*`, `bg-*`, `rounded-pill`, `text-body-secondary`), `btn-close`, `form-group` 폐지, 체크박스와 표와 그리드의 BS5 기본 모양 보정 | 템플릿, 자체 JS, 서버가 만드는 HTML, `app/assets/stylesheets` | **구현 완료(2026-10-31)** | 점검표는 `architecture/bs5-migration-checklist.md` |
| X-38e | 자체 JS의 BS 플러그인 호출을 BS5 API로: `modal`, `popover`, `tab`, `tooltip` 호출을 `bootstrap.*`로, 상태 단추는 자체 함수로 | 자체 JS 약 25개, 템플릿 안의 스크립트 | **구현 완료(2026-10-31)** | 사이트 자체 코드의 jQuery는 그대로(BS 자체는 jQuery 없이 동작) |
| X-38f | 정리: 호환 층(`_bs3-compat.scss`)을 영역별로 이관해 지운다(D-54), 옛 BS 자원과 `less.js` 삭제, `affix`의 `sticky` 대체(A-296), 문서와 위키 갱신 | 정적 자원, 스타일, 위키 | **완료**(2026-10-31) | 호환 층 2,199줄에서 0줄. 영역별 이관 결과는 `architecture/bs4-migration-checklist.md`의 이관 결과 표. 남은 BS2 의존은 전역 `content-box`, 줄 높이 20px 상속, 맨 `input` 모양이다 |
| X-38g | 글자 크기와 줄 높이의 상대 단위화(화면 유지, 1순위, **구현 완료(2026-10-31), 방법은 `rem`(A-298)**): 줄 높이를 비율로(`--bs-body-line-height: 20px` 덮어쓰기를 걷고 규칙마다 `20/글자 크기`를 명시한 뒤 `$line-height-base` 비율만 쓴다), 자체 SCSS의 px 글자 크기(260개 규칙)를 rem으로(`px/16`) | 스타일 | **진행 승인**(D-55, 2026-10-31) | 목적은 해상도와 사용자 글자 크기 설정에 대한 대응이다(선생님 지적). 기본 크기에서는 `style-diff` 차이 0, 추가로 루트 글자 크기를 키운 조건에서도 순회해 줄 겹침과 넘침을 찾는다. 가로폭과 여백의 px는 X-38h와 함께 다룬다 |
| X-38h | 구조 정리(화면 유지, 2순위): 전역 `content-box`를 `border-box`로(h-1, **구현 완료(2026-10-31), A-299**), 그다음 float 그리드를 flex 그리드로(h-2, **구현 완료(2026-10-31), A-300**) | 스타일 | **h-1, h-2 완료**(D-55) | 자체 CSS 8천 줄의 폭 계산을 함께 고쳐야 하므로 위험이 크다. 전 화면 비교 도구로 확인 |
| X-38i | BS2 고유 모양의 현대화(디자인 변경, 3순위): 그라데이션 단추와 글자 그림자, 안쪽 그림자, 940px 고정 폭, 반응형 | 스타일 | **완료, select2 4.x만 보류(D-56, 2026-10-31)** | BS2 모양을 유지하지 않고 BS5 고유 모양을 쓴다. 컴포넌트마다 전후 캡처를 보여 드린다. 완료: 단추(X-38i-1, A-302), 폼 컨트롤(X-38i-2, A-303, A-304). 완료: 알림·배지·표·탭(X-38i-3, A-305). 완료: 그림자·그라데이션 정리(X-38i-4, A-307). 완료: BS5 반응형 그리드(X-38i-5, D-57, A-308). 완료: 테마 색 BS5 기본 전환(D-58, A-310). 남음: select2 4.x(2026-10-31 선생님 지시로 보류) |
| X-39 | 조직의 OAuth 앱(조직 설정 탭: 앱 목록, 만들기, 수정, 비밀 값 재발급, 지우기, 승인 현황), **앱 소유자 이전**(사용자와 조직 사이), **OAuth app policy**(조직이 서드파티 앱의 접근을 제한), 조직 삭제 때의 정리, 승인 화면의 소유자 표시, 조직 감사 로그 연결 | `domain/oauth2server`(소유자 종류 `ORGANIZATION`), 조직 설정 화면 | **진행(2026-11-02, 결정 D-78 확정). (가) 조직 소유 앱과 정리와 소유자 표시 완료, (나) 소유자 이전과 (다) 정책 남음** | 조직 설정은 v1.6 구조의 가로 탭이라 "OAuth 앱" 탭을 더하는 방식이다(GitHub식 사이드바 재구성은 별도 항목). 단계: 조직 소유 앱, 소유자 이전, 정책 |
| X-40 | OIDC 제공자("Sign in with Yona"): ID 토큰, `/userinfo`, `/.well-known/openid-configuration`, `openid`, `profile`, `email` 범위 | `domain/oauth2server` 확장 | **완료(2026-11-02, 결정은 D-77)** | 설명은 [architecture/oauth2-server.md](architecture/oauth2-server.md)의 "OIDC 제공자". 서명 키는 운영자가 정한다(`yona.oauth2.oidc.signing-key-file`, 자동 생성은 추가 논의), `sub`은 사용자 번호, `email_verified`는 보내지 않는다, 사용자 정보 주소는 `/oauth2/userinfo`, 로그아웃 연동은 추가 논의. 공개된 OIDC 클라이언트 라이브러리로 확인했다 |
| X-41 | MCP 도구 확장: 풀 리퀘스트 만들기, 리뷰 판정, 닫기와 다시 열기, 병합, 이슈 삭제, 이슈 댓글 삭제 | `mcp` | **완료(2026-11-02)** | D-74, D-75, D-76. 삭제는 이슈와 이슈 댓글만 넣었다(웹 화면과 같은 규칙). R-76(병합 중 표시 고착)은 선생님 승인으로 개선했다 |
| X-25 | CI/Actions(GitHub Actions 방식의 워크플로 YAML, 러너 등록과 폴링)와 프런트엔드 분리(SPA) | P3-05, P3-13 | **보류(선생님 결정 2026-11-02)** | `next`에서도 **미착수 또는 계획서만 있다**. CI/Actions는 자동 진행 대상에서 제외하라는 기존 지시(P3-05)가 있다 |
| X-26 | 풀 리퀘스트 리뷰에 줄 단위 댓글 묶기와 임시 저장 리뷰(PENDING): 코드 줄에 단 댓글을 "리뷰 시작"으로 임시 저장했다가 판정과 함께 한꺼번에 제출, 임시 리뷰 버리기 | `domain/pullrequest/PullRequestReview`, `domain/comment/CommentThread` | **구현 완료(2026-10-29), 결정은 D-38** | X-12(D-35)를 먼저 했다. Yona의 줄 댓글(`CommentThread`)은 지금도 즉시 올라가고 판정과 무관하다. 묶으려면 줄 댓글의 저장, 알림 구조를 바꿔야 한다. GitHub은 임시 저장된 줄 댓글을 본인만 보며 제출 전까지 고칠 수 있다(공식 문서). 임시 저장 리뷰는 이 묶음이 있어야 의미가 있다 |
| X-27 | 풀 리퀘스트 리뷰 요청과 다시 요청: 쓰기 권한자가 특정 사람에게 리뷰를 요청하고 요청받은 사람 목록, 알림, 다시 요청을 둔다 | `domain/pullrequest/PullRequestReviewRequest` | **구현 완료(2026-10-29), 결정은 D-37** | X-12(D-35)를 먼저 했다. GitHub은 리뷰 요청에 쓰기 권한이 필요하다(공식 문서). Yona에는 요청 대상 지정 개념이 없어 새 테이블과 화면이 필요하다. 새 테이블은 내보내기, 가져오기에 포함한다 |
| X-28 | 풀 리퀘스트 병합 방식 선택(병합 커밋, 스쿼시, 리베이스)과 프로젝트 설정(허용 방식, 기본 방식) | `PullRequestMerger` 확장 | **구현 완료(2026-10-29), 결정은 D-41** | D-39 4번: GitHub 방식 세 가지. 병합 화면은 선택 상자가 되고 REST(X-15)의 `method` 필드를 연다. 스쿼시는 커밋 메시지 구성(작성자, 원래 커밋 메시지, `Reviewed-by`)을 정하고 X-11(이슈 자동 닫기)이 같은 결과를 내야 한다. 리베이스는 커밋마다 다시 적용(충돌 처리)하고 커밋 번호가 바뀌어 "이미 병합됨" 판단을 살펴야 한다. X-13(선형 이력 필수)과 맞물린다. Forgejo의 나머지 방식(빨리 감기만 허용, 수동 병합 등)은 범위 밖 |
| X-32 | 리뷰어 수 강제 지정을 빼고 브랜치 보호 규칙(브랜치별 필요한 승인 수, 관리자에게도 적용)으로 바꾼다 | `Project`, `PullRequestRules`, `PullRequestWorkflow`, 프로젝트 설정 | **구현 완료(2026-10-30), 결정은 D-42, D-43, D-44** | 기본은 강제 없음. 프로젝트 설정의 리뷰어 수는 규칙이 없는 브랜치의 기본 규칙으로 유지한다(D-43). 화면, REST(`approvals.required`)가 규칙을 따른다 |
| X-33 | GitHub 방식 브랜치 보호(직접 푸시 차단, 필요한 승인 수, 변경 요청과 대화 해결, 선형 이력, 강제 푸시와 삭제 차단, 관리자에게도 적용)로 이행하고 리뷰어 사용과 리뷰어 수 설정을 무력화한다 | `BranchProtectionRule`, `PullRequestRules`, `PullRequestWorkflow`, `GitHooks`, 프로젝트 설정 | **구현 완료(2026-10-30), 결정은 D-45, X-34가 구조를 대체** | X-32를 대체한다. 1단계는 병합 조건과 이전, 2단계는 푸시 검사이다. `is_using_reviewer_count`, `default_reviewer_count`는 이번 버전에서 쓰지 않고 차기 버전에서 제거한다 |
| X-34 | 브랜치 보호를 GitHub의 규칙 묶음 구조로 전환한다(이름, 적용 상태, 바이패스, 대상, 규칙 묶음, 합산, 목록과 편집 화면) | `Ruleset`, `RulesetService`, `GitHooks`, `PullRequestRules`, 프로젝트 설정 메뉴 | **구현 완료(2026-10-30), 결정은 D-46** | X-33을 대체한다. ◐ 항목은 현재 수준으로 둔다 |
| X-35 | 규칙 묶음 ◐ 항목의 완성: 바이패스 목록(구성원, 조직 관리자, 개별 사용자)과 우회 방식(Always, Exempt, For pull requests only), 대상 여러 개와 제외와 기본 브랜치와 미리보기, 선형 이력의 푸시 검사, 오래된 승인 해제와 허용 병합 방식의 규칙 묶음 단위 이전, 승인 수 0~10 정렬, 평가 상태 | `Ruleset` 확장 | **대기(가장 낮은 우선순위, 2026-10-30)** | D-46 6번. 우회 대상은 종류와 대상 번호 구조로 저장하도록 확장한다 |
| X-36 | 이슈, 게시글, 댓글의 삭제를 작성자와 관리자로 제한한다(웹과 REST). 수정과 속성 변경은 GitHub처럼 구성원에게 유지 | `AccessControl`(삭제 규칙), 화면의 삭제 단추 | **구현 완료(2026-10-30), 결정은 D-48** | v1.6 권한 모델(구성원은 모든 것을 지운다)과 달라진다. X-29b(게시판 REST)는 이 결정을 반영한다 |


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
