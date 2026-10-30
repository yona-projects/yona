---
title: 이식 로드맵과 진행 상태
type: synthesis
sources: [../AGENTS.md, ../yona-v1.6-wiki/web/templates/*.md]
updated: 2026-10-29
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
| X-1 | OAuth2 Authorization Server(동적 클라이언트 등록 포함) | `domain/oauth2server`, `config/oauth2server` | 대기 | X-6 MCP 서버의 인증 기반 |
| X-2 | 2단계 인증(TOTP, WebAuthn/패스키, 백업 코드, 알려진 기기) | `domain/twofactor` | 대기 | 로그인 흐름이 바뀌므로 v1.6 로그인 계약과 충돌하지 않게 선택 사용으로 둔다. `next`는 로그인 브루트포스 방지, 알려진 기기 인식(`user_known_device`), 사이트 관리자의 2단계 인증 해제도 함께 둔다 |
| X-3 | SSH 공개키 등록, SSH 접근, 저장소별 Deploy Key | `domain/sshkey`, `config/ssh` | 대기 | |
| X-4 | GPG 커밋 서명 검증 | `domain/gpgkey` | 대기 | |
| X-5 | 개인용 API 토큰(PAT, 범위 지정) | `domain/apitoken` | **구현 완료(2026-10-29)** | D-36: Classic 토큰을 먼저 도입하고 v1.6 사용자 토큰은 레거시 Classic 토큰으로 이관한다(공존이 아니라 이관). 모든 토큰이 사용자 권한을 확인한다(D-20 대체). Fine-grained는 X-5b로 나눈다. 상세는 [architecture/api-token.md](architecture/api-token.md) |
| X-6 | MCP 서버(OAuth2로 인증한 AI 에이전트의 이슈, 풀 리퀘스트, 위키 접근) | `mcp` | 대기 | X-1, X-5 이후 |
| X-7 | Mercurial 저장소(hg4j): 코드 보기, 커밋 이력, bookmark와 named branch, HTTP와 SSH 서빙, zip 내려받기, 브랜치 보호와 push 알림·웹훅, 풀 리퀘스트 병합, GPG 서명 | `domain/vcs`, `config/hg`, `HgController` | 대기 | 저장소 종류 변경(v1.6 `changeVCS`)과 `vcs` 열 값에 영향. hg4j는 `io.github.search5.hg4j` 별도 라이브러리 **선행 정리(2026-10-29)**: 착수 전에 `@IsOnlyGitAvailable`(컨트롤러 7곳)과 `VCS_GIT`, `VCS_SUBVERSION` 분기(9개 파일, 39곳)를 "지원하는 VCS를 선언하는 방식"(예: `@SupportedVcs(GIT, MERCURIAL)`)으로 정리한다. 기능마다 Git 전용, Git과 Mercurial, 모든 VCS로 갈리므로 이름만 바꾸는 일이 아니다. 그때까지는 v1.6 이름을 유지한다 |
| X-8 | Git LFS | `.well-known`, `git-lfs` 라우트 | 대기 | |
| X-9 | 감사 로그(보안, 관리 이벤트) | `domain/event` | 대기 | |
| X-10 | 관측성: OpenTelemetry(추적), Actuator와 Prometheus(지표), JSON 로그(logstash) | `build.gradle.kts`의 opentelemetry, actuator, micrometer 스타터 (P3-01) | 대기 | |
| X-11 | 풀 리퀘스트 병합 시 `close(s/d)`, `fix(es/ed)`, `resolve(s/d) #번호`로 참조된 이슈 자동 닫기 | `domain/event/PullRequestMergeEventListener` | **완료(2026-10-29)** | `PullRequestWorkflow.accept`가 병합한 뒤 `PullRequestIssueCloser`로 처리한다(A-188). `next` 정규식의 `fixes/fixed` 인식 실패를 바로잡았다 |
| X-12 | 풀 리퀘스트 리뷰 판정(Approve, Request changes, Comment). 같은 리뷰어의 재판정은 이력으로 쌓고 정책 판단에는 리뷰어별 최신 판정만 쓴다 | `domain/pullrequest/PullRequestReview`, `PullRequestServiceImpl` | **구현 완료(2026-10-29)** | D-35: v1.6의 "리뷰 완료/취소"를 GitHub 리뷰 모델에 흡수한다(별개가 아니다). 새 테이블 `pull_request_review`, 상세는 [web/pull-request-review.md](web/pull-request-review.md) |
| X-13 | 브랜치 보호 규칙(브랜치 패턴, 풀 리퀘스트 필수, 필요한 승인 수, 서명 커밋 필수, push 허용 사용자 제한, force push와 삭제 금지, 매니저 우회). push 훅과 병합 검사에 적용 | `domain/branchprotection`, `web/BranchProtectionController`, `domain/vcs/GitPushHooks` | 대기 | X-4(GPG), X-12(리뷰 판정)가 있어야 해당 규칙이 의미를 갖는다. 새 테이블 필요 **후보(2026-10-29, 보류)**: `refs/heads`, `refs/tags` 밖의 참조(`refs/remotes/*` 등) 푸시를 거부하는 정책. 푸시로 만들 수 있음을 v2와 v1.6에서 확인했다(D-30 7번). 선생님이 나중에 다루기로 하셨다 |
| X-14 | 풀 리퀘스트 담당자(assignee)와 라벨 | `PullRequest.assignee`, `PullRequest.labels` | **구현 완료(2026-10-29), 후속(Ajax, 2단 화면, 모든 목록 표시, 담당자 필터 선택지, 생성 폼 지정)도 구현 완료** | v1.6 `pull_request`에는 담당자, 라벨 열이 없다. 연결 테이블 추가 필요 **설계 결정(2026-10-29)**: [decisions.md](decisions.md) D-34를 본다. 연결 테이블 2개(`pull_request_assignee`, `pull_request_issue_label`)로 구현하고 담당자는 1명까지이며 나중에 여러 명으로 키울 수 있게 한다. 담당자 변경은 타임라인과 알림, 목록에 담당자와 라벨 필터를 넣는다 후속(D-34 14~16): 이슈와 같은 Ajax 방식, 모든 풀 리퀘스트 목록에 표시, 모양 다듬기 |
| X-15 | 풀 리퀘스트 REST API(조회, diff, 리뷰, 타임라인, 병합, 닫기, 다시 열기, 댓글, 리뷰어, 라벨, 담당자, 출발 브랜치 삭제) | `/api/projects/{}/pullrequests/**`, `/api/v1/projects/{}/{}/pull-requests/**` | **구현 완료(2026-10-29), 결정은 D-39** | 계약은 [architecture/api-pull-requests.md](architecture/api-pull-requests.md)이다. 병합 방식(`method`)은 필드만 예약했고 X-28에서 연다. 풀 리퀘스트 만들기와 고치기(`POST`, `PATCH`)와 첨부(`temporaryUploadFiles`)도 포함한다. 라벨 목록 API는 X-19에서 만든다
| X-16 | 엔터프라이즈 SSO(SAML2, 범용 OIDC) 사이트 관리자 설정과 로그인, 소셜 로그인 계정 병합 | `config/sso`, `domain/sso`, `SsoAdminController`, `OAuth2AccountMergeService` (P3-06) | 대기 | 테이블 `oidc_sso_settings`, `saml2_sso_settings` 필요. v1.6 소셜 로그인은 그대로 둔다 |
| X-17 | 프로젝트 위키: 페이지 만들기, 고치기, 삭제, 이력, 비교, 검색, 사이드바. 프로젝트마다 bare git 저장소(`<소유자>/<프로젝트>.wiki.git`)에 저장 | `domain/wiki`, `WikiViewController`, `/api/v1/projects/{}/{}/wiki/**` (P3-42) | 대기 | v1.6에는 위키 기능이 없다 |
| X-18 | Git 태그 전용 화면(주석 메시지와 태거, 종류 배지, 최신순), 삭제 확인, 브랜치 목록과 선택 목록 정리 | `TagViewController`, `TagApiController` (P3-16) | **완료(2026-10-29)** | 결정은 [decisions.md](decisions.md) D-30을 본다. REST는 X-19로 옮겼다. 주석 태그의 커밋 이력 500(P3-40)은 별도로 확인한다 |
| X-19 | 일반 REST 1차: 라벨 목록, 이슈 목록과 검색, 마일스톤 목록, 프로젝트 조회(정보, 내가 속한 프로젝트, 소유자의 프로젝트), 프로젝트 구성원 목록과 담당자 후보 | `*RestApi` 컨트롤러 (P3-09, P3-35) | **구현 완료(2026-10-29), 결정은 D-40** | 계약은 [architecture/api-project-reads.md](architecture/api-project-reads.md)이다. v1.6의 `/-_-api/v1` 외부 API는 그대로 두고 같은 경로에 읽기 API를 더했다. 이슈 댓글은 `GET .../issues/{number}`가 이미 담고 있다 |
| X-29 | 일반 REST 2차: 이슈 쓰기(만들기, 수정, 상태, 담당자, 라벨, 마일스톤, 댓글)와 게시판(글과 댓글) | 이슈와 게시판 REST | **대기(2026-10-29, 선생님이 추가 항목으로 올림)** | X-19 1차 뒤. v1.6 외부 이슈 쓰기 API(`IssueApi`)가 있으므로 그 계약과의 관계(호환 유지 또는 새 경로)를 정한 뒤 시작한다 |
| X-30 | 일반 REST 3차: 조직(구성원, 프로젝트), 검색, 사용자 상태, 웹훅, 포크를 조직 대상으로 만드는 API | 조직, 검색, 웹훅 REST | **대기(2026-10-29)** | X-19 1차와 X-29 뒤 |
| X-31 | OpenAPI 문서(Swagger UI): `/-_-api/v1` 전체 계약(풀 리퀘스트, 일반 REST 1~3차)을 한 번에 문서화 | OpenAPI 설정, `*RestApi` | **대기(2026-10-29, 선생님: 한 번에 만들어도 됨)** | 모든 REST 영역이 끝난 뒤 한 번에 만든다 |
| X-20 | 프런트엔드 현대화: Vue 3 웹 컴포넌트 위젯 17개(대화상자, 토스트, 드롭다운, 자동 완성, 첨부, 리뷰 폼, 라벨 편집기 등), Tom Select, Flatpickr, CodeMirror 6와 EasyMDE 에디터, jQuery 제거(순수 JS), Hotwired Turbo(이슈 2단 목록) | `static/lib/yona-vue-widgets`, `frontend/`, `yona.issue.Turbo.js` (P3-46, P3-70) | 대기 | 화면 동작은 v1.6과 같게 둔다는 조건이 필요하다. `yona-v2-wiki`의 Vue 위젯 소스 위치 메모(components 저장소)와 이어진다 |
| X-21 | 사용 현황 집계(UA 태그를 GA4 gtag로 교체) | `config/analytics/AnalyticsSettings` | **완료(2026-10-28)** | v1.6의 `yona.send-yona-usage` 스위치를 유지하고 UA 태그를 GA4로 교체했다(A-186). 측정 ID는 `yona.analytics.measurement-id` |
| X-22 | 새 프로젝트의 기본 브랜치를 `main`으로 고정 | P3-11 | **완료(2026-10-28)** | `next` 기준으로 `yona.git-default-branch`(기본 `main`)를 `HEAD`로 고정한다(A-187) |
| X-23 | Apache Magpie 연동(이슈 계약 `contract:tracker`, PR 계약 `contract:change-request`) | 없음([magpie-integration.md](architecture/magpie-integration.md)) | **보류(쌓아 둠, 2026-10-28 선생님)** | 선행: X-5(PAT), X-12(리뷰 판정), X-15(PR REST), X-19(일반 REST). 이 항목들이 채우지 못하는 것은 기간·작성자 조건의 이슈 목록과 이슈·PR 활동 조회뿐이다. 착수 전에 D-29를 결정한다 |
| X-24 | yona CLI(Go 단일 바이너리, `gh`와 비슷한 사용법; 이슈, 풀 리퀘스트, 관리, 자동화) | `docs/yona-wiki/plans/p3-02-cli-and-rest-api.md` (P3-02) | 대기 | `next`에서도 **설계만 있고 코드는 없다** |
| X-25 | CI/Actions(GitHub Actions 방식의 워크플로 YAML, 러너 등록과 폴링)와 프런트엔드 분리(SPA) | P3-05, P3-13 | 대기 | `next`에서도 **미착수 또는 계획서만 있다**. CI/Actions는 자동 진행 대상에서 제외하라는 기존 지시(P3-05)가 있다 |
| X-26 | 풀 리퀘스트 리뷰에 줄 단위 댓글 묶기와 임시 저장 리뷰(PENDING): 코드 줄에 단 댓글을 "리뷰 시작"으로 임시 저장했다가 판정과 함께 한꺼번에 제출, 임시 리뷰 버리기 | `domain/pullrequest/PullRequestReview`, `domain/comment/CommentThread` | **구현 완료(2026-10-29), 결정은 D-38** | X-12(D-35)를 먼저 했다. Yona의 줄 댓글(`CommentThread`)은 지금도 즉시 올라가고 판정과 무관하다. 묶으려면 줄 댓글의 저장, 알림 구조를 바꿔야 한다. GitHub은 임시 저장된 줄 댓글을 본인만 보며 제출 전까지 고칠 수 있다(공식 문서). 임시 저장 리뷰는 이 묶음이 있어야 의미가 있다 |
| X-27 | 풀 리퀘스트 리뷰 요청과 다시 요청: 쓰기 권한자가 특정 사람에게 리뷰를 요청하고 요청받은 사람 목록, 알림, 다시 요청을 둔다 | `domain/pullrequest/PullRequestReviewRequest` | **구현 완료(2026-10-29), 결정은 D-37** | X-12(D-35)를 먼저 했다. GitHub은 리뷰 요청에 쓰기 권한이 필요하다(공식 문서). Yona에는 요청 대상 지정 개념이 없어 새 테이블과 화면이 필요하다. 새 테이블은 내보내기, 가져오기에 포함한다 |
| X-28 | 풀 리퀘스트 병합 방식 선택(병합 커밋, 스쿼시, 리베이스)과 프로젝트 설정(허용 방식, 기본 방식) | `PullRequestMerger` 확장 | **구현 완료(2026-10-29), 결정은 D-41** | D-39 4번: GitHub 방식 세 가지. 병합 화면은 선택 상자가 되고 REST(X-15)의 `method` 필드를 연다. 스쿼시는 커밋 메시지 구성(작성자, 원래 커밋 메시지, `Reviewed-by`)을 정하고 X-11(이슈 자동 닫기)이 같은 결과를 내야 한다. 리베이스는 커밋마다 다시 적용(충돌 처리)하고 커밋 번호가 바뀌어 "이미 병합됨" 판단을 살펴야 한다. X-13(선형 이력 필수)과 맞물린다. Forgejo의 나머지 방식(빨리 감기만 허용, 수동 병합 등)은 범위 밖 |
| X-32 | 리뷰어 수 강제 지정을 빼고 브랜치 보호 규칙(브랜치별 필요한 승인 수, 관리자에게도 적용)으로 바꾼다 | `Project`, `PullRequestRules`, `PullRequestWorkflow`, 프로젝트 설정 | **구현 완료(2026-10-30), 결정은 D-42, D-43, D-44** | 기본은 강제 없음. 프로젝트 설정의 리뷰어 수는 규칙이 없는 브랜치의 기본 규칙으로 유지한다(D-43). 화면, REST(`approvals.required`)가 규칙을 따른다 |
| X-33 | GitHub 방식 브랜치 보호(직접 푸시 차단, 필요한 승인 수, 변경 요청과 대화 해결, 선형 이력, 강제 푸시와 삭제 차단, 관리자에게도 적용)로 이행하고 리뷰어 사용과 리뷰어 수 설정을 무력화한다 | `BranchProtectionRule`, `PullRequestRules`, `PullRequestWorkflow`, `GitHooks`, 프로젝트 설정 | **구현 중(2026-10-30), 결정은 D-45** | X-32를 대체한다. 1단계는 병합 조건과 이전, 2단계는 푸시 검사이다. 두 컬럼은 차기 버전에서 제거한다 |


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
