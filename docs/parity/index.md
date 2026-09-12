# yona → yona 동치성 회귀 백로그

`yona`(Play/Java)에서 `yona`(Spring Boot/Kotlin)로 이식하며 발견된 기능 격차를 중요도 순으로 번호를 매겨 정리한 작업 백로그다. 원본 감사 리포트: 이 세션에서 생성한 아티팩트 "요나·유나 동치성 감사" 참고.

**템플릿(뷰) 이식은 별도 문서로 관리한다 → [`docs/TEMPLATE_BACKLOG.md`](../TEMPLATE_BACKLOG.md)**(legacy `app/views/**/*.scala.html` 242개 전체를 파일별 작업 순서로 정리, Play/Scala→Thymeleaf 문법 치환만 허용되는 아키텍처 차이).

## 진행 규칙 (TDD + JaCoCo)

1. 항목마다 **먼저 실패하는 회귀 테스트**를 작성한다 (yona의 기대 동작을 yona에서 명세).
2. 테스트가 레드 상태임을 확인한 뒤, 최소 구현으로 그린으로 만든다.
3. `./gradlew test`는 `build.gradle.kts`에 설정된 JaCoCo(`jacocoTestReport`)로 `finalizedBy` 연결되어 있어, 테스트를 돌릴 때마다 `build/reports/jacoco/test/jacocoTestReport.xml`(+ html)에 커버리지가 자동 갱신된다.
4. 각 항목을 마치면 아래 표의 상태를 `[x]`로 바꾸고, 관련 커밋/테스트 파일 경로와 커버리지 수치는 `tickets/<id>.md`의 완료 로그에 남긴다(아래 문서 규칙 참고).
5. 번호는 고정 ID다 — 순서가 바뀌어도 번호는 재사용하지 않는다.

상태 기호: `[x]` 완료 · `[~]` 진행중 · `[ ]` 대기

## 문서 규칙 (2026-09-07 위키 이관)

이 문서는 원래 표 한 줄 아래 서술형 완료 로그가 무한히 누적되는 단일 파일(`PARITY_BACKLOG.md`)이었으나, `docs/yona-wiki/`의 관례(인덱스=표만, 상세=개별 파일, frontmatter로 상태 추적, `[[wikilink]]`로 상호참조)를 따라 이관했다. 표-서술 불일치가 반복되고 특정 항목을 찾으려면 큰 파일 전체를 훑어야 했던 문제 때문이다.

- **인덱스(`index.md`)는 표만 담는다** — # / 상태 / 제목 / 한줄요약 / 링크. 서술은 100% `tickets/<id>.md`로 이동한다.
- **티켓 1개 = 파일 1개** (`tickets/p{0-3}-{NN}.md`). frontmatter(`id`/`type`/`status`/`created`/`updated`/`relates_to`/`source`)로 상태를 추적한다.
- **신규 항목은 이 구조로만 기록한다**: 표에 한 줄 추가 + `tickets/<id>.md` 생성. 표 아래에 서술을 직접 이어 쓰지 않는다.
- 다른 티켓을 언급할 때는 `[[tickets/pX-NN]]` wikilink를 쓴다.
- 이관 시 원문은 요약 없이 그대로 옮겼다 — 원본(이관 전 버전)은 git 히스토리에 남아있다.

---

## P0 — 치명적 (보안 / 데이터 손실 / 핵심 기능 마비)

| # | 상태 | 제목 | 한줄요약 | 링크 |
|---|---|---|---|---|
| P0-01 | [x] | 알림 메일 발송 파이프라인 부재 | 완료 | [[tickets/p0-01]] |
| P0-02 | [x] | IMAP 수신메일→이슈/댓글 생성 부재 | 완료 | [[tickets/p0-02]] |
| P0-03 | [x] | 웹훅 발송 미연결 | 완료 | [[tickets/p0-03]] |
| P0-04 | [x] | 웹훅 gitPush 필터 로직 반전 | 완료 | [[tickets/p0-04]] |
| P0-05 | [x] | 이슈 생성 시 첨부파일 연결 안 됨 | 완료 | [[tickets/p0-05]] |
| P0-06 | [x] | 게시글 생성 시 첨부파일 연결 안 됨 | 완료 | [[tickets/p0-06]] |
| P0-07 | [x] | 사이트 백업/복원 데이터 유실 | 완료 | [[tickets/p0-07]] |
| P0-08 | [x] | 마크다운 새니타이저 XSS 약화 | 완료 | [[tickets/p0-08]] |
| P0-09 | [x] | 프로젝트 이전 수락 인가 검증 누락 | 완료 | [[tickets/p0-09]] |
| P0-10 | [x] | git push 예약 ref 보호 훅 부재 | 완료 | [[tickets/p0-10]] |
| P0-11 | [x] | git push 시 커밋 알림 이벤트 훅 부재 | 완료 | [[tickets/p0-11]] |
| P0-12 | [x] | 브랜치 삭제 시 관련 PR 정리 훅 부재 | 완료 | [[tickets/p0-12]] |
| P0-13 | [x] | LOCKED/DELETED 계정 로그인 차단 안 됨 | 완료 | [[tickets/p0-13]] |
| P0-14 | [x] | PullRequest 라우트 누락 | 완료 | [[tickets/p0-14]] |
| P0-15 | [x] | Board 라우트 누락 (postlabel) | 완료 | [[tickets/p0-15]] |
| P0-16 | [x] | CodeHistory 라우트 누락 (커밋 댓글) | 완료 | [[tickets/p0-16]] |
| P0-17 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 게시판 도메인)** 조직 게시판 목록에 프로젝트 가시성 필터 없어 비공개 프로젝트 게시글 노출(접근제... | 완료(아래 완료 로그 참고, P0-20과 함께 처리) | [[tickets/p0-17]] |
| P0-18 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — PR/코드리뷰 도메인)** 리뷰 스레드 열기/닫기에 권한 체크 전무, 무관한 사용자가 임의 프로젝트... | 완료(아래 완료 로그 참고) | [[tickets/p0-18]] |
| P0-19 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 프로젝트 도메인)** 계단식 삭제(PR/이슈/게시글/라벨/웹훅 등) 미이식, cascade 선언 없어... | 완료(아래 완료 로그 참고) | [[tickets/p0-19]] |
| P0-20 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 조직 도메인)** `getVisibleProjects` 필터 없이 비공개 포함 전체 프로젝트 노출 | 완료(P0-17과 동일 원인·동일 커밋, 아래 완료 로그 참고) | [[tickets/p0-20]] |
| P0-21 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 조직 도메인)** 사이트매니저 전역 우회 로직 부재, REST API에서 설정변경/삭제 시 403 가... | 완료(아래 완료 로그 참고, P2-16 판정 정정) | [[tickets/p0-21]] |
| P0-22 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 첨부파일 도메인)** 소유권/원컨테이너 검증 우회, 임의 첨부파일 강제 재배선 가능(보안) | 완료(아래 완료 로그 참고) | [[tickets/p0-22]] |
| P0-23 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 사이트관리/통계/검색 도메인)** `HIDE_PROJECT_LISTING` 플래그 및 관련 분기 전무... | 완료(아래 완료 로그 참고, 범위를 ProjectApp.java/OrganizationApp.java/UserApp.ja... | [[tickets/p0-23]] |
| P0-24 | [x] | **(2026-08-21 백엔드 전수 감사 재검증 중 발견 — PR/코드리뷰 도메인)** PR 코드 리뷰 댓글 작성(`newPullRequestComment... | 완료(아래 완료 로그 참고, newCommitComment도 같은 파일에서 함께 발견해 처리) | [[tickets/p0-24]] |
| P0-25 | [x] | **(2026-08-21 P0-23 구현 중 발견)** 사용자 프로필 화면(`/user/{loginId}`)이 대상 사용자가 작성한 이슈/PR을 방문자의 프... | 완료(아래 완료 로그 참고) | [[tickets/p0-25]] |
| P0-26 | [x] | **(2026-08-31 사용자가 웹 폼으로 프로젝트 생성 후 README 커밋 실패를 재현하는 중 발견)** 웹 폼(`/projectform`, `/pro... | 완료 | [[tickets/p0-26]] |

## P1 — 주요 (기능 결손 / 권한 로직 오류)

| # | 상태 | 제목 | 한줄요약 | 링크 |
|---|---|---|---|---|
| P1-01 | [x] | LDAP 인증 부재 | 완료 | [[tickets/p1-01]] |
| P1-02 | [x] | API 토큰 인증 미작동 | 완료 | [[tickets/p1-02]] |
| P1-03 | [x] | OAuth 다중 계정 연동/병합 소실 | 완료(의도적 축소, 아래 참고) | [[tickets/p1-03]] |
| P1-04 | [x] | 이메일 도메인 allowlist 미시행 | 완료 | [[tickets/p1-04]] |
| P1-05 | [x] | Related-PR 재병합 로직 스텁 | 완료 | [[tickets/p1-05]] |
| P1-06 | [x] | 커밋→이슈 자동 참조 리스너가 로깅만 함 | 완료 | [[tickets/p1-06]] |
| P1-07 | [x] | 이슈 타임라인(IssueEvent) 부재 | 완료 | [[tickets/p1-07]] |
| P1-08 | [x] | PR 타임라인(PullRequestEvent) 부재 | 완료 | [[tickets/p1-08]] |
| P1-09 | [x] | RecentIssue(최근 본 이슈) 부재 | 완료 | [[tickets/p1-09]] |
| P1-10 | [x] | 라벨 수정 기능 없음 | 완료 | [[tickets/p1-10]] |
| P1-11 | [x] | 라벨 카테고리 수정 기능 없음 | 완료 | [[tickets/p1-11]] |
| P1-12 | [x] | 라벨 복사(copyLabels) 기능 없음 | 완료 | [[tickets/p1-12]] |
| P1-13 | [x] | 프로젝트 라벨 attach/detach 없음 | 완료 | [[tickets/p1-13]] |
| P1-14 | [x] | 멘션 자동완성(mentionList) 없음 | 완료 | [[tickets/p1-14]] |
| P1-15 | [x] | pushed-branch 삭제 API 없음 | 완료(P1-24와 함께 구현, 아래 참고) | [[tickets/p1-15]] |
| P1-16 | [x] | Project enroll() 중복 멤버십 가드 누락 | 완료 | [[tickets/p1-16]] |
| P1-17 | [x] | 조직 멤버 추가 시 게스트 역할 검증 누락 | 완료 | [[tickets/p1-17]] |
| P1-18 | [x] | 게시판 알림 미발송 | 완료 | [[tickets/p1-18]] |
| P1-19 | [x] | 게시판 편집이력/댓글수/라벨필터 저하 | 완료 | [[tickets/p1-19]] |
| P1-20 | [x] | CodeCommentThread.isOutdated() 없음 | 완료 | [[tickets/p1-20]] |
| P1-21 | [x] | Watch 권한 필터링(allowedWatchersOnly) 무시됨 | 완료 | [[tickets/p1-21]] |
| P1-22 | [x] | 프로젝트별 알림 뮤트 토글 미반영 | 완료 | [[tickets/p1-22]] |
| P1-23 | [x] | SVN 권한 모델 단순화 | 완료 | [[tickets/p1-23]] |
| P1-24 | [x] | 최근 push된 브랜치 추적(PushedBranch) 기능 없음 | 완료 | [[tickets/p1-24]] |
| P1-25 | [x] | git push(NEW_COMMIT) 이벤트는 웹훅이 발송되지 않음 | 완료 | [[tickets/p1-25]] |
| P1-26 | [x] | PULL_REQUEST 리소스 타입은 웹훅 payload를 만들 수 없음 | 완료 | [[tickets/p1-26]] |
| P1-27 | [x] | 알림 메일이 이벤트별 즉시 발송이며 다이제스트 병합/언어별 그룹핑이 없음 | 완료(재착수, 아래 완료 로그 참고) | [[tickets/p1-27]] |
| P1-28 | [x] | 알림 메일에 IMAP 답장용 Reply-To 헤더 없음 | 완료 | [[tickets/p1-28]] |
| P1-29 | [x] | 수신메일 MIME multipart/HTML 본문·첨부파일·cid 이미지 치환 미지원 | 완료 | [[tickets/p1-29]] |
| P1-30 | [x] | 리뷰 댓글/커밋 댓글 스레드로의 메일 답장 미지원 | 완료 | [[tickets/p1-30]] |
| P1-31 | [x] | "help" 자동응답 및 실패 사유 회신 메일 없음 | 완료 | [[tickets/p1-31]] |
| P1-32 | [x] | 수신 주소 detail에 리소스 경로 직접 명시 방식 미지원 | 완료 | [[tickets/p1-32]] |
| P1-33 | [x] | 복원 후 auto-increment 채번이 백업된 PK와 충돌할 수 있음 | 완료 | [[tickets/p1-33]] |
| P1-34 | [x] | PostgreSQL 방언 경로는 통합테스트로 검증되지 않음 | 완료 | [[tickets/p1-34]] |
| P1-35 | [x] | PR 수정 화면(editPullRequestForm/editPullRequest) 미구현 | **완료**(아래 참고) | [[tickets/p1-35]] |
| P1-36 | [x] | doClone 전용 라우트 없음(기능은 forkProject로 커버) | 완료(코드 변경 없음, 검증만) | [[tickets/p1-36]] |
| P1-37 | [x] | 이슈 타임라인에 라벨/본문/이동/공유자 변경 이벤트 기록 없음 | 완료 | [[tickets/p1-37]] |
| P1-38 | [x] | IssueEvent draft-time 병합/취소 최적화 없음 | 완료 | [[tickets/p1-38]] |
| P1-39 | [x] | PR 생성/리뷰 상태변경이 NotificationEvent·PullRequestEvent 모두 미기록 | 완료 | [[tickets/p1-39]] |
| P1-40 | [x] | PullRequestEvent draft-time 병합/취소 최적화 없음 | 완료 | [[tickets/p1-40]] |
| P1-41 | [x] | 최근 본 이슈/게시글 조회 UI·엔드포인트, 탈퇴 시 정리 없음 | 완료 | [[tickets/p1-41]] |
| P1-42 | [x] | mentionList 추가 후보 소스(공유자/작성자·댓글러/워처, "@project all"·"@group all") 미지원 | 완료(1건 의도적 편차, 아래 참고) | [[tickets/p1-42]] |
| P1-43 | [x] | mentionListAtCommitDiff/mentionListAtPullRequest 엔드포인트 없음 | 완료 | [[tickets/p1-43]] |
| P1-44 | [x] | 게시글 수정 시 알림 미발송(옵션 체크박스 미연동) | 완료 | [[tickets/p1-44]] |
| P1-45 | [x] | GitAuthorizationFilter도 SvnAuthorizationFilter와 동일한 권한 축소 보유 | 완료 | [[tickets/p1-45]] |
| P1-46 | [x] | git push(NEW_COMMIT) 알림 메일은 여전히 발송되지 않음(수신자 미계산) | 완료 | [[tickets/p1-46]] |
| P1-47 | [x] | 수신메일 HTML 서식 보존 및 cid 인라인 이미지 치환 미지원 | 완료 | [[tickets/p1-47]] |
| P1-48 | [x] | 이슈를 다른 프로젝트로 이동하는 기능 자체가 없음(ISSUE_MOVED) | 완료(백엔드, 아래 완료 로그 참고) | [[tickets/p1-48]] |
| P1-49 | [x] | `PullRequestService.addReviewer/removeReviewer`(REST `PullRequestController`가 사용)는 알림/이... | 완료(부분, 아래 참고) | [[tickets/p1-49]] |
| P1-50 | [x] | 코드리뷰 댓글/스레드 열기·닫기가 알림(NEW_REVIEW_COMMENT/REVIEW_THREAD_STATE_CHANGED)을 전혀 발행하지 않음 | 완료 | [[tickets/p1-50]] |
| P1-51 | [x] | P1-50이 새 producer(NEW_REVIEW_COMMENT/REVIEW_THREAD_STATE_CHANGED)를 추가하면서 P1-27의 소비자 쪽 두... | 완료 | [[tickets/p1-51]] |
| P1-52 | [x] | 관련 PR 재검사(attemptMerge)가 새 커밋 발견 시의 부수효과(커밋 영속화·PR 타임라인 기록·리뷰어 초기화·알림)와 diff 소멸 시 자동 ME... | 완료 | [[tickets/p1-52]] |
| P1-53 | [x] | P1-52의 `PullRequestEvent.oldValue`가 yona의 가상 병합 커밋 ref 메커니즘(`mergedCommitIdFrom`/`merge... | 완료 | [[tickets/p1-53]] |
| P1-54 | [x] | 이슈 라벨 유일성 정책이 yona의 project+category+name 복합 유일성 대신 project+name 단일 유일성으로 축약됨(P1-12 완료... | 완료 | [[tickets/p1-54]] |
| P1-55 | [x] | IMAP 수신메일 폴링(`ImapMailboxPoller`)이 yona의 IDLE 우선+영속 UID 워터마크 방식 대신 `\Seen` 플래그를 자체 북마크로... | 사용자 요청으로 2026-08-20 코드 대조 분석. yona는 (1) IMAP `IDLE` 명령으로 실시간 push 수... | [[tickets/p1-55]] |
| P1-56 | [x] | OAuth 로그인 중 "다른 계정으로 로그인 중인데 인증한 provider가 이미 다른 계정에 속함" 상황을 처리하는 play-authenticate `me... | 완료 | [[tickets/p1-56]] |
| P1-57 | [x] | 조직 그룹멤버에게 PUBLIC/PROTECTED 프로젝트의 읽기·일부 쓰기 권한을 부여하는 `AccessControl.isAllowedIfGroupMembe... | 완료 | [[tickets/p1-57]] |
| P1-58 | [x] | mentionList가 실제로는 존재하는 `User.getDisplayName()`/`englishName`을 쓰지 않고 `user.name`을 그대로 사용... | 완료 | [[tickets/p1-58]] |
| P1-59 | [x] | 메일 답장으로 코드리뷰/커밋 댓글을 생성할 때(P1-30) 첨부파일과 cid 인라인 이미지가 조용히 유실됨 | 완료 | [[tickets/p1-59]] |
| P1-60 | [x] | UI에서 직접 작성한 리소스(이슈/리뷰 댓글 등)의 첫 알림 메일에 대한 IMAP 답장이 In-Reply-To/References만으로는 해당 스레드로 연결... | 완료 | [[tickets/p1-60]] |
| P1-61 | [x] | 수신메일 HTML 서식 보존 시 yona의 `HtmlCompressor`(태그 사이 불필요한 개행 제거) 미이식 | 완료 | [[tickets/p1-61]] |
| P1-62 | [x] | PR 리뷰어 추가/제거가 yona엔 없는 구조로 `PullRequestService`/`CodeReviewService` 두 서비스에 중복 구현돼 있음(P1... | 완료 | [[tickets/p1-62]] |
| P1-63 | [x] | PR 리뷰어 참여/취소 알림 제목이 yona `NotificationEvent.afterReviewed()`의 `formatReplyTitle()`("Re:... | 완료 | [[tickets/p1-63]] |
| P1-64 | [x] | SVN/Git 저장소 접근 권한 필터에 조직 그룹멤버 우회(`isAllowedIfGroupMember`)가 미구현 — P1-57이 웹 컨트롤러 전체에 이식했... | 완료 | [[tickets/p1-64]] |
| P1-65 | [x] | 이슈를 초안(draft)으로 저장했다가 나중에 발행(publish)하는 전환 플로우 자체가 없음(P1-48/P1-27 완료 로그에서 각각 발견된 같은 뿌리의... | 완료 | [[tickets/p1-65]] |
| P1-66 | [x] | 이슈 수정 폼에 "다른 프로젝트로 이동" UI(대상 프로젝트 선택)가 없음 — 백엔드(P1-48)는 이미 완료 | 코드 레벨 완료로 재분류(2026-08-20) | [[tickets/p1-66]] |
| P1-67 | [x] | 최근 방문 이슈/게시글을 보여주는 사이드바 UI가 없음 — 백엔드(P1-41)는 이미 완료 | 코드 레벨 완료로 재분류(2026-08-20), 이후 UI도 실제로 완료됨(정정, 2026-08-23) | [[tickets/p1-67]] |
| P1-68 | [x] | PR 수정 시 브랜치(from/toBranch) 재할당 불가+브랜치 변경 시 중복 PR 검사 없음+제목/본문 변경 시 이슈 참조 이벤트 재동기화 없음(`ed... | 완료 | [[tickets/p1-68]] |
| P1-69 | [x] | 신규 리뷰 댓글(`NEW_REVIEW_COMMENT`)/커밋 댓글(`NEW_COMMENT`) `NotificationEvent`가 웹훅 리스너·payload... | 완료 | [[tickets/p1-69]] |
| P1-70 | [x] | 이슈를 다른 프로젝트로 이동(P1-48)해도 `ISSUE_MOVED` 이벤트가 이슈 타임라인(`IssueEvent`)에 기록되지 않음 — P1-37이 "이동... | 완료 | [[tickets/p1-70]] |
| P1-71 | [x] | PR 병합 재검사(`processMergeCheck`) 시 PR의 conflict 상태가 바뀌어도(충돌 없다가 발생 / 충돌이 해소됨) 알림·PR 타임라인... | 완료 | [[tickets/p1-71]] |
| P1-72 | [x] | 프로젝트 이전(transfer) 수락 시 목적지에 동명 프로젝트가 있어도 이름 충돌 처리(자동 rename) 없이 그대로 이전을 진행함 | 완료 | [[tickets/p1-72]] |
| P1-73 | [x] | 프로젝트 이전 완료 후 `project.organization` FK가 갱신되지 않아 조직 소속 정보가 stale 상태로 남음 | 완료 | [[tickets/p1-73]] |
| P1-74 | [x] | **표현 정정(2026-08-20)**: "반대 방향 대기 요청 정리"가 아니라, yona `ProjectApp.disableProjectTransferLi... | 완료 | [[tickets/p1-74]] |
| P1-75 | [x] | **정정(2026-08-20)**: 재확인 결과 이미 구현돼 있었음(서비스 계층만 확인하고 컨트롤러 계층은 못 본 오탐) — `ProjectViewContr... | 완료(검증만, 코드 변경 없음) | [[tickets/p1-75]] |
| P1-76 | [x] | 프로젝트 이름/소유자 변경(이전 포함) 후 예전 URL(git remote 등)로 접근하면 더 이상 프로젝트를 찾지 못함 — yona의 "이전 위치 폴백"... | 완료 | [[tickets/p1-76]] |
| P1-77 | [x] | 회원가입 시 관리자 승인 대기(signup.require.admin.confirm) 정책이 전혀 구현되지 않음 — 신규 가입자가 항상 즉시 활성화됨 | 완료 | [[tickets/p1-77]] |
| P1-78 | [x] | PR 리뷰어 추가/제거에 권한 검증이 없어(경로에 따라) 프로젝트 멤버가 아닌 인증된 사용자 누구나 아무 PR에나 리뷰어로 자신을 등록/해제할 수 있음 | 완료 | [[tickets/p1-78]] |
| P1-79 | [x] | 리뷰 스레드 상태변경(open/close) 시 알림 발행이 실패하면 yona와 반대로 상태변경 자체가 롤백됨(장애 격리 정반대) | 완료 | [[tickets/p1-79]] |
| P1-80 | [x] | 이슈 라벨의 배타 카테고리(exclusive) 제약이 서버에서 전혀 검증되지 않아, 같은 배타 카테고리의 라벨을 한 이슈에 여러 개 붙일 수 있음 | 완료 | [[tickets/p1-80]] |
| P1-81 | [x] | 검색 시 본인이 작성했거나 담당자로 지정된 이슈는 프로젝트 접근권한과 무관하게 노출돼야 하는데(yona), yona는 프로젝트 가시성으로만 필터링함 | 완료 | [[tickets/p1-81]] |
| P1-82 | [x] | 이슈 공유(share) 기능이 데이터 모델·관리 API는 완전히 구현돼 있지만, 실제 이슈 조회 API의 읽기 권한 판단에는 전혀 연결돼 있지 않음(공유받아... | 완료 | [[tickets/p1-82]] |
| P1-83 | [x] | 검색 시 "본인이 작성했으면 프로젝트 접근권한과 무관하게 노출"되는 예외가 게시글(Posting)·이슈댓글(IssueComment)·게시글댓글(PostCom... | 완료 | [[tickets/p1-83]] |
| P1-84 | [x] | 초안(draft) 이슈를 작성자 본인이 아닌 사용자도 조회 가능 — yona는 `issue()` 핸들러에서 `AccessControl.isAllowed()`... | 완료 | [[tickets/p1-84]] |
| P1-85 | [x] | **(구 P2-05, 2026-08-20 재분류)** 접근제어가 컨트롤러별 산발적 인라인 체크로 분산 — P1-86~98(그룹 A~E 전수조사에서 확정된 개... | **2026-08-20 사용자 지시로 재착수, 설계 확정(구현 미착수)**: yona `app/utils/AccessCo... | [[tickets/p1-85]] |
| P1-86 | [x] | **(구 P2-12, 2026-08-20 재분류)** `isAllowedIfAssignee`는 담당자 지정이 프로젝트 멤버십과 무관하게 성립 가능하므로(담당... | 완료 | [[tickets/p1-86]] |
| P1-87 | [x] | **(구 P2-13, 2026-08-20 재분류 및 범위 구체화)** **[최우선]** `WebhookController`에 로그인 체크 자체가 전혀 없음... | 2026-08-20 P2-05 설계 조사 중 발견, P2-16 확정 조사로 수정 범위 구체화. **완료** | [[tickets/p1-87]] |
| P1-88 | [x] | **(구 P2-14, 2026-08-20 재분류)** 접근제어 중앙화 2단계: `checkReadPermission` 계열(그룹 A/A', 9개 파일 헬퍼형... | 2026-08-20 P2-05 설계 조사 중 발견(`docs/P1-85_PLAN.md` 참고). P1-85(중앙 서비스... | [[tickets/p1-88]] |
| P1-89 | [x] | **(2026-08-20 P2-16에서 분리)** `WatchController.checkWatchPermission`이 WATCH 연산에 READ 규칙(`... | 2026-08-20 P2-05 설계 조사 중 발견(`docs/P1-85_PLAN.md` 참고). 원래 P2-16(그룹 E... | [[tickets/p1-89]] |
| P1-90 | [x] | **(구 P2-17, 2026-08-20 재분류)** `CommentServiceImpl.hasPermission()`이 yona 댓글 UPDATE/DELE... | 2026-08-20 P2-05 설계 조사 중 발견(`docs/P1-85_PLAN.md` 참고). **완료** | [[tickets/p1-90]] |
| P1-91 | [x] | **(2026-08-20 P2-15 확정 조사에서 발견)** `BoardController.isManagerOrAuthor`가 게시글 UPDATE/DELET... | 2026-08-20 P2-15 조사에서 확정. **완료** | [[tickets/p1-91]] |
| P1-92 | [x] | **(2026-08-20 P2-15 확정 조사에서 발견)** `PullRequestController.updatePullRequest`가 PR 수정을 담당자... | 2026-08-20 P2-15 조사에서 확정. **완료** | [[tickets/p1-92]] |
| P1-93 | [x] | **(2026-08-20 P2-15 확정 조사에서 발견)** `CodeHistoryController.isAuthorOrManager`가 커밋 댓글 삭제를... | 2026-08-20 P2-15 조사에서 확정. **완료** | [[tickets/p1-93]] |
| P1-94 | [x] | **(2026-08-20 P2-15 확정 조사에서 발견)** `IssueLabelController.isProjectManager`가 라벨/카테고리 CRUD... | 2026-08-20 P2-15 조사에서 확정. **완료** | [[tickets/p1-94]] |
| P1-95 | [x] | **(2026-08-20 P2-15 확정 조사에서 발견)** `MilestoneController.isProjectManager`가 마일스톤 생성/수정/삭제... | 2026-08-20 P2-15 조사에서 확정. **완료** | [[tickets/p1-95]] |
| P1-96 | [x] | **(2026-08-20 P2-16 확정 조사에서 발견, 보안)** 첨부파일 다운로드/목록 조회에 권한 체크 자체가 없음 — PRIVATE 프로젝트 이슈/게... | 2026-08-20 P2-16 조사에서 발견. P1-87(웹훅 무인증)과 동급 심각도의 보안 이슈로 취급 권장. **완료** | [[tickets/p1-96]] |
| P1-97 | [x] | **(2026-08-20 P2-16 확정 조사에서 발견)** 브랜치 삭제가 프로젝트 멤버 전원에게 허용됨(과잉 허용) — yona는 PROJECT 리소스의... | 2026-08-20 P2-16 조사에서 발견. yona 대비 느슨한(과잉 허용) 방향의 결손 | [[tickets/p1-97]] |
| P1-98 | [x] | **(2026-08-20 P2-15 조사 중 부수 발견)** 프로젝트 멤버 제거 시 "프로젝트 오너는 제거할 수 없다"는 가드가 없음 | 2026-08-20 P2-15 조사 중 그룹 D(`isProjectManager`) 판정 과정에서 부수적으로 발견, 범위... | [[tickets/p1-98]] |
| P1-99 | [x] | **(2026-08-20 P1-85 1b 구현 중 발견)** 사이트 전역 "익명 접근 허용" 설정(`application.allowsAnonymousAcce... | 완료 | [[tickets/p1-99]] |
| P1-100 | [x] | **(2026-08-21 전체 백로그 재점검 중 발견)** P1-76(프로젝트 이전 후 예전 URL 폴백)이 "완료(범위 조정)"로 표시돼 있는데, 그 범위... | 완료 | [[tickets/p1-100]] |
| P1-101 | [x] | **(2026-08-21 컨트롤러/모델 전수 대조에서 발견)** 이슈 가중치(weight) 투표 API가 전혀 없음 — `Issue.weight: Int`... | 완료 | [[tickets/p1-101]] |
| P1-102 | [x] | **(2026-08-21 컨트롤러/모델 전수 대조에서 발견)** 이슈/댓글 동시편집 충돌 감지(`detectChange`) 기능이 전혀 없음 — 두 사용자가... | 완료 | [[tickets/p1-102]] |
| P1-103 | [x] | **(2026-08-21 컨트롤러/모델 전수 대조에서 발견)** 이슈 제목 자동완성/중복이슈 제안(`titleHeads`) 기능이 전혀 없음 — 새 이슈 작... | 완료 | [[tickets/p1-103]] |
| P1-104 | [x] | **(2026-08-21 검증규칙 전수 대조에서 발견)** 로그인ID 형식 검증(정규식)이 전혀 없음 — 원본은 `@Pattern`으로 허용 문자(영문/숫자... | 완료 | [[tickets/p1-104]] |
| P1-105 | [x] | **(2026-08-21 검증규칙/정렬순서 전수 대조에서 발견)** 페이지당 항목 수가 원본과 다르고 yona 내부에서도 API/웹이 제각각임 — 원본은 이... | 완료 | [[tickets/p1-105]] |
| P1-106 | [x] | **(2026-08-21 이슈/PR 상세 화면 요소 단위 직접 대조에서 발견)** 이슈/PR 상세 화면에 상태·라벨·담당자 변경 등 타임라인 이벤트가 전혀... | 완료 | [[tickets/p1-106]] |
| P1-107 | [x] | **(2026-08-21 P1-102 구현 중 부수 발견)** 게시글(Posting)에도 이슈와 동일한 동시편집 충돌 감지(`isModifiedByOther... | 완료 | [[tickets/p1-107]] |
| P1-108 | [x] | **(2026-08-21 P1-104 구현 중 부수 발견)** 조직(Organization)명에도 로그인ID와 동일한 문자 제약(`LOGIN_ID_PATTE... | 완료 | [[tickets/p1-108]] |
| P1-109 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 게시판 도메인)** README 게시글 중복 생성 방지 로직 없음, 반복 작성 시 README 게시글... | 완료(아래 완료 로그 참고) | [[tickets/p1-109]] |
| P1-110 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 게시판 도메인)** `issueTemplate=true` write-path 분기 없음, 제출 시 실... | 완료(아래 완료 로그 참고) | [[tickets/p1-110]] |
| P1-111 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 게시판 도메인)** 코드브라우저 "편집"의 임의 텍스트 파일 온라인 커밋 write-path 없음,... | 완료(아래 완료 로그 참고, P1-135와 함께 처리) | [[tickets/p1-111]] |
| P1-112 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 게시판 도메인)** `parentCommentId` DTO 필드 없어 대댓글 생성이 API로 노출 안 됨 | 완료(아래 완료 로그 참고) | [[tickets/p1-112]] |
| P1-113 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 게시판 도메인)** 공개 프로젝트 비멤버의 게시글 작성 권한이 yona보다 과도하게 제한(회귀) —... | 완료(아래 완료 로그 참고) | [[tickets/p1-113]] |
| P1-114 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — PR/코드리뷰 도메인)** `getCodeCommentThreadsForChanges` 필터링 전무,... | 완료(아래 완료 로그 참고) | [[tickets/p1-114]] |
| P1-115 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — PR/코드리뷰 도메인)** JPQL 연산자 우선순위 버그로 CLOSED/MERGED PR도 브랜치 삭... | 완료(아래 완료 로그 참고) | [[tickets/p1-115]] |
| P1-116 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — PR/코드리뷰 도메인)** 리뷰/커밋 댓글 삭제 권한이 "작성자 또는 MANAGER"로 과도 제한(P... | 완료(아래 완료 로그 참고) | [[tickets/p1-116]] |
| P1-117 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 프로젝트 도메인)** 조직 그룹 기반 담당자 후보(조직 관리자/멤버/사이트매니저) 확장 로직 없음 | 완료(아래 완료 로그 참고) | [[tickets/p1-117]] |
| P1-118 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 사용자/인증 도메인)** 사이트관리자 전용 벌크 사용자 생성(`newUser`)/API 전용 토큰 로... | 완료(아래 완료 로그 참고) | [[tickets/p1-118]] |
| P1-119 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 사용자/인증 도메인)** `loginId=="admin"`이면 상태 무관 항상 `isSiteManag... | 완료(아래 완료 로그 참고) | [[tickets/p1-119]] |
| P1-120 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 조직 도메인)** `HIDE_PROJECT_LISTING` 403 체크 및 `@GuestProhibi... | 완료(아래 완료 로그 참고) | [[tickets/p1-120]] |
| P1-121 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 조직 도메인)** 게스트 계정 조직 생성 차단(`@GuestProhibit`) 미이식 | 완료(아래 완료 로그 참고) | [[tickets/p1-121]] |
| P1-122 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 조직 도메인)** 중복 가입 신청 가드 없어 재신청 시 알림 중복 발행(Project P1-16과 동... | 완료(아래 완료 로그 참고) | [[tickets/p1-122]] |
| P1-123 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 조직 도메인)** 대기 신청 여부 확인 없이 무조건 취소 알림 발행, isGuest 가드도 없음 | 완료(아래 완료 로그 참고) | [[tickets/p1-123]] |
| P1-124 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 조직 도메인)** 조직 로고 업로드 시 이미지 타입/크기(`LOGO_FILE_LIMIT_SIZE`)... | 완료(아래 완료 로그 참고) | [[tickets/p1-124]] |
| P1-125 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 알림/메일 도메인)** 멘션 인덱스 엔티티 자체가 yona에 없음(2, 3번의 근본 원인) | 완료(동등 기능으로 대체, 아래 완료 로그 참고) | [[tickets/p1-125]] |
| P1-126 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 알림/메일 도메인)** 조직/프로젝트 그룹 멘션 확장 없음, `@owner/project` 정규식 매... | 완료(아래 완료 로그 참고) | [[tickets/p1-126]] |
| P1-127 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 알림/메일 도메인)** 신규 이슈/게시글/PR 생성 시 본문 `@멘션` 알림 수신자 계산 자체가 없음 | 완료(아래 완료 로그 참고) | [[tickets/p1-127]] |
| P1-128 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 마일스톤 도메인)** orderBy/orderDir 정렬 파라미터 및 완료율 정렬 로직 전체 없음 | 완료(아래 완료 로그 참고) | [[tickets/p1-128]] |
| P1-129 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 마일스톤 도메인)** 벌크 마일스톤 임포트 API 전체 미이식(단건 생성만 지원) | 완료(아래 완료 로그 참고) | [[tickets/p1-129]] |
| P1-130 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 첨부파일 도메인)** ORGANIZATION/COMMIT_COMMENT/REVIEW_COMMENT/U... | 완료(아래 완료 로그 참고) | [[tickets/p1-130]] |
| P1-131 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 감시/즐겨찾기 도메인)** 감시자 목록이 명시적 Watch row만 반환, 작성자/담당자/투표자/프로... | 완료(아래 완료 로그 참고) | [[tickets/p1-131]] |
| P1-132 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 웹훅 도메인)** 모든 이벤트 텍스트 메시지에 리소스 링크 전혀 없음 | 완료(아래 완료 로그 참고) | [[tickets/p1-132]] |
| P1-133 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 웹훅 도메인)** DETAIL_SLACK attachment의 이슈 필드 축소, PR attachme... | 완료(아래 완료 로그 참고) | [[tickets/p1-133]] |
| P1-134 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 웹훅 도메인)** Hangout Chat 스레드 키가 댓글 이벤트에서 부모 리소스가 아닌 댓글 자신으... | 완료(아래 완료 로그 참고, P1-143 직후 처리) | [[tickets/p1-134]] |
| P1-135 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 코드/Git/SVN 도메인)** refName이 "refs/heads/master" 하드코딩, 브랜치... | 완료(아래 완료 로그 참고, P1-111과 함께 처리) | [[tickets/p1-135]] |
| P1-136 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 코드/Git/SVN 도메인)** 빈 저장소 NoHeadException 미처리, 전역 핸들러도 없어... | 완료(아래 완료 로그 참고) | [[tickets/p1-136]] |
| P1-137 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 사이트관리/통계/검색 도메인)** 진단 체크 내용이 원본 3개(메일수신/hostname/중복설정)와... | **완료(아래 완료 로그 참고 | [[tickets/p1-137]] |
| P1-138 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 사이트관리/통계/검색 도메인)** 익명 사용자를 프로젝트 스코프 확인 전에 무조건 403 — PUBL... | 완료(아래 완료 로그 참고) | [[tickets/p1-138]] |
| P1-139 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 접근제어/검증 유틸 도메인)** 코드 브라우저에서 `.md`/README 마크다운 렌더링 자체가 없음... | 완료(아래 완료 로그 참고) | [[tickets/p1-139]] |
| P1-140 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 접근제어/검증 유틸 도메인)** lang 오버로드/렌더 캐시 없음, 다이제스트 메일 배치 스레드에서... | **완료(lang 오버로드, 아래 완료 로그 참고 | [[tickets/p1-140]] |
| P1-141 | [x] | **(2026-08-21 백엔드 전수 감사 재검증 중 발견 — PR/코드리뷰 도메인)** PR 생성(`createPullRequest`)이 yona보다 과도... | 완료(아래 완료 로그 참고) | [[tickets/p1-141]] |
| P1-142 | [x] | **(2026-08-21 P1-123 구현 중 발견)** Organization의 `cancelEnroll()`과 동일한 유형의 결함이 Project 쪽에도... | P1-123(Organization의 동일 결함) 수정 중 대칭 지점인 `ProjectUserServiceImpl.can... | [[tickets/p1-142]] |
| P1-143 | [x] | **(2026-08-21 P1-134 착수 중 발견)** Hangout Chat 웹훅 응답에서 `thread.name`을 파싱해 `WebhookThread`... | 완료(아래 완료 로그 참고) | [[tickets/p1-143]] |
| P1-144 | [x] | **(2026-08-21 P2-27 조사 중 발견)** 같은 소유자를 유지한 채 프로젝트 이름만 바꾸는 "개명" 기능이 yona에 아예 없음 — yona `... | P2-27(즐겨찾기 owner/projectName 동기화) 조사 중, yona의 동기화 호출이 정확히 이 "개명" 경로... | [[tickets/p1-144]] |
| P1-145 | [x] | **(2026-08-26 골든 체크 버킷C 사람 검토에서 발견)** 프로젝트명에 예약 패턴(`.`/`..`/`.git` 등)을 막는 검증이 yona에 전혀... | 완료 | [[tickets/p1-145]] |
| P1-146 | [x] | **(2026-08-26 골든 체크 버킷C 사람 검토에서 발견)** 브랜치 갱신 시 관련 PR을 재검사(변경사항 반영/충돌 재확인)하는 이벤트가 실제 프로덕... | 완료 | [[tickets/p1-146]] |
| P1-147 | [x] | **(2026-08-26 골든 체크 버킷C 사람 검토에서 발견)** 이슈/게시글 등 리소스 삭제 시 관련 `Watch`/`Unwatch` 레코드를 정리하던... | 완료 | [[tickets/p1-147]] |

## P2 — 참고 (경미 / 확인 필요)

| # | 상태 | 제목 | 한줄요약 | 링크 |
|---|---|---|---|---|
| P2-01 | [x] | ReservedWordsValidator(예약어 검증) 없음 | 완료 | [[tickets/p2-01]] |
| P2-02 | [x] | DiffUtil 워드단위 diff 하이라이팅 없음 | 완료 | [[tickets/p2-02]] |
| P2-03 | [x] | 사이트 관리자 아바타 지정 API가 빈 스텁 | 완료 | [[tickets/p2-03]] |
| P2-04 | [x] | 웹훅 JSON 페이로드 단순화 | 완료 | [[tickets/p2-04]] |
| P2-06 | [x] | SVN 컨트롤러 라우트 커버리지 오탐 | 완료(검증만, 코드 변경 없음) | [[tickets/p2-06]] |
| P2-07 | [x] | 데이터 백업/복원 시 auto-increment 재설정 방식이 원본과 달라, 삭제로 생긴 시퀀스 갭이 다르게 처리됨(id 재사용 가능성) | 완료 | [[tickets/p2-07]] |
| P2-08 | [x] | P2-04(웹훅 push JSON payload)가 커밋 목록/head_commit 골격은 갖췄지만, 필드 단위로 대조하면 아직 레거시와 다른 지점이 4곳... | 완료 | [[tickets/p2-08]] |
| P2-09 | [x] | git 프로토콜(clone/push)로만 프로젝트에 접근하는 사용자는 "최근 방문 프로젝트"에 기록되지 않음(웹 UI로 한 번이라도 들어가면 잡힘 — 영향 작음) | 완료 | [[tickets/p2-09]] |
| P2-10 | [x] | **표현 정정**: "yona 1시간 기본값 대비 24배 차이"는 코드 레벨 fallback만 본 것 — 실제 배포용 conf 템플릿(`application... | 완료 | [[tickets/p2-10]] |
| P2-11 | [x] | **불확실 해소**: 백엔드만 없는 게 아니라 **프론트엔드(템플릿)는 이미 `POST /user/setDefaultLoginPage` 호출 버튼을 갖고 있... | 완료 | [[tickets/p2-11]] |
| P2-15 | [x] | **조사 완료, 종결**: 그룹 C/D(`isManagerOrAuthor`/`isManagerOrContributor`/`isAuthorOrManager`/... | 2026-08-20 확정 조사 결과: **BoardController.isManagerOrAuthor(P1-91), Pu... | [[tickets/p2-15]] |
| P2-16 | [x] | **조사 완료, 종결**: 그룹 E 잔여(`checkCodeAccessibility`, `isOrgAdmin`) + 나머지 리소스 타입(CODE/ORGANI... | 2026-08-20 확정 조사 결과: **CODE(코드 브라우저 접근), ORGANIZATION, PROJECT_TRAN... | [[tickets/p2-16]] |
| P2-17 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 게시판 도메인)** 새 댓글 알림의 "인용 이전 내용"(oldValue) 미채움 — `Notifica... | 완료(아래 완료 로그 참고) | [[tickets/p2-17]] |
| P2-18 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — PR/코드리뷰 도메인)** `getCommitComments()`(SVN 커밋코멘트 ↔ PR 매핑)... | 완료(조사 결과 이식 불필요로 판정, 아래 완료 로그 참고) | [[tickets/p2-18]] |
| P2-19 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 조직 도메인)** 조직명 변경 시 `FavoriteOrganization.organizationNam... | 완료(아래 완료 로그 참고) | [[tickets/p2-19]] |
| P2-20 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 알림/메일 도메인)** 가입요청/취소 알림 수신자 계산이 Watch 여부를 무시 | 완료(아래 완료 로그 참고) | [[tickets/p2-20]] |
| P2-21 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 알림/메일 도메인)** 조직 가입 신청 oldValue/newValue 페어링이 비대칭이라 드래프트... | 완료(아래 완료 로그 참고) | [[tickets/p2-21]] |
| P2-22 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 마일스톤 도메인)** 마일스톤 상세의 이슈 목록 정렬(번호 내림차순) 없음, 쿼리에도 ORDER BY 없음 | 완료(아래 완료 로그 참고) | [[tickets/p2-22]] |
| P2-23 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 마일스톤 도메인)** dueDate 파싱 실패 시 조용히 null로 저장(에러 알림 없음) | 완료(아래 완료 로그 참고) | [[tickets/p2-23]] |
| P2-24 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 첨부파일 도메인)** DB dedup 미이식 + isNew 판정 오류로 201 응답 도달 불가, 재업... | 완료(아래 완료 로그 참고) | [[tickets/p2-24]] |
| P2-25 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 첨부파일 도메인)** MIME 감지가 Tika(콘텐츠기반)→JDK probeContentType(확장... | 완료(아래 완료 로그 참고) | [[tickets/p2-25]] |
| P2-26 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 첨부파일 도메인)** 임시 첨부 정리 스케줄러의 createdDate 비교 방향이 yona와 반대(사... | 완료(레거시 그대로 포팅, 아래 완료 로그의 TODO 참고) | [[tickets/p2-26]] |
| P2-27 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 감시/즐겨찾기 도메인)** 프로젝트 개명/이전 시 `FavoriteProject.owner/proje... | 완료(아래 완료 로그 참고, P1-144 별도 등록) | [[tickets/p2-27]] |
| P2-28 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 웹훅 도메인)** payloadUrl/secret 길이·필수 검증 미이식, DB 제약 위반 500 노... | 완료(아래 완료 로그 참고) | [[tickets/p2-28]] |
| P2-29 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 코드/Git/SVN 도메인)** Git 전용 가드 누락, SVN 프로젝트에 호출 시 no-op이나 성... | 완료(아래 완료 로그 참고) | [[tickets/p2-29]] |
| P2-30 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 코드/Git/SVN 도메인)** zip 다운로드 시 경로 사전 존재 검증 및 path 파라미터 자체 소실 | 완료(아래 완료 로그 참고) | [[tickets/p2-30]] |
| P2-31 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 사이트관리/통계/검색 도메인)** SearchType.NA/PROJECT 400 처리 없이 조용히 빈... | 완료(아래 완료 로그 참고) | [[tickets/p2-31]] |
| P2-32 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 접근제어/검증 유틸 도메인)** noreferrer 로직이 알림메일 경로에만 있고 일반 마크다운 렌더... | 완료(아래 완료 로그 참고) | [[tickets/p2-32]] |
| P2-33 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 접근제어/검증 유틸 도메인)** 본문 순수 이슈URL 자동 링크화(+권한체크) 미이식 | 완료(아래 완료 로그 참고) | [[tickets/p2-33]] |
| P2-34 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 접근제어/검증 유틸 도메인)** 글로벌 리소스 생성 권한 판단 함수 미이식(영향 제한적) | 완료(아래 완료 로그 참고, 조사 중 REST `/api/projects` 미이식 발견해 P2-45로 별도 등록) | [[tickets/p2-34]] |
| P2-35 | [x] | **(2026-08-21 백엔드 전수 감사에서 발견 — 접근제어/검증 유틸 도메인)** `project.isCodeAvailable()` 체크 없이 vcs=... | 완료(아래 완료 로그 참고) | [[tickets/p2-35]] |
| P2-36 | [x] | **(2026-08-21 P1-133 작업 중 발견)** yona `Webhook.java`는 Posting 이벤트에 DETAIL_SLACK 전용 분기가 없... | 완료(아래 완료 로그 참고) | [[tickets/p2-36]] |
| P2-37 | [x] | **(2026-08-21 P0-19 구현 중 발견, 2026-08-22 재조사로 판정 정정)** fork가 제3의 프로젝트로 보낸 PR(또는 원본 프로젝트... | 완료(아래 완료 로그 참고) | [[tickets/p2-37]] |
| P2-38 | [x] | **(2026-08-21 P0-25 구현 중 발견)** 사용자 프로필의 이슈 목록이 `daysAgo` 파라미터로 최근 N일 필터링되지 않고 항상 전체 기간을... | 완료(아래 완료 로그 참고) | [[tickets/p2-38]] |
| P2-39 | [x] | **(2026-08-21 P1-114 구현 중 발견, 2026-08-22 사용자 결정으로 되돌림)** PR "conversation" 탭이 댓글 스레드(co... | **완료(아래 완료 로그 참고) | [[tickets/p2-39]] |
| P2-40 | [x] | **(2026-08-21 P1-122/123 구현 중 발견, P2-21과 중복)** `OrganizationServiceImpl`의 조직 가입 신청 알림 3... | **완료 | [[tickets/p2-40]] |
| P2-41 | [x] | **(2026-08-21 P1-125 구현 중 발견)** "나를 멘션한 이슈" 필터(`UserViewController.userIssues`의 `mentio... | 완료(사용자 지시로 yona의 Mention 인덱스 테이블 아키텍처를 로직·구조·한계까지 그대로 포팅, 아래 완료 로그 참고) | [[tickets/p2-41]] |
| P2-42 | [x] | **(2026-08-21 P1-139 구현 중 발견)** yona `partial_readme.scala.html:38-42`는 `!project.menuS... | 완료(아래 완료 로그 참고) | [[tickets/p2-42]] |
| P2-43 | [x] | **(2026-08-21 P1-140 구현 중 발견)** `Markdown.renderWithHighlight()`의 `CacheStore.renderedM... | 완료(사용자 지시로 yona 구조 그대로 포팅, 아래 완료 로그 참고) | [[tickets/p2-43]] |
| P2-44 | [x] | **(2026-08-21 P0-19 구현 중 발견, 2026-08-22 번호중복(P2-36) 정정)** PR/리뷰스레드 삭제 시 REVIEW_COMMENT·... | **완료 | [[tickets/p2-44]] |
| P2-45 | [x] | **(2026-08-22 P2-34 조사 중 발견, 2026-08-22 사용자 지시로 재착수)** yona `ProjectApi.java:135` `newP... | 완료(아래 완료 로그 참고). `exports()` 자매 엔드포인트는 별도 대규모 항목(P2-46)으로 분리 등록, 조사... | [[tickets/p2-45]] |
| P2-46 | [x] | **(2026-08-22 P2-45 구현 중 발견, 2026-08-22 사용자 지시로 착수)** yona `ProjectApi.java:46-72` `exp... | 완료(아래 완료 로그 참고) | [[tickets/p2-46]] |
| P2-47 | [x] | **(2026-08-26 골든 체크 버킷C 사람 검토에서 발견, 2026-08-26 TDD로 완료)** README 파일 탐색 전용 헬퍼(README 후보... | 완료 | [[tickets/p2-47]] |
| P2-48 | [x] | **(2026-08-26 골든 체크 버킷C 사람 검토에서 발견, 2026-08-26 TDD로 완료)** Apache Combined Log Format 방식... | 완료 | [[tickets/p2-48]] |
| P2-49 | [x] | **(2026-08-26 골든 체크 버킷C 사람 검토에서 발견, 2026-08-26 TDD로 완료)** 첨부파일 목록 인메모리 캐싱이 yona에 없음(`@C... | 완료 | [[tickets/p2-49]] |
| P2-50 | [x] | **(2026-08-26 골든 체크 버킷C 사람 검토에서 발견, 2026-08-26 TDD로 완료)** ref 갱신 실패 시 전용 예외 타입 대신 일반 `I... | 완료 | [[tickets/p2-50]] |
| P2-51 | [x] | **(2026-08-26 골든 체크 버킷C 사람 검토에서 발견, 2026-08-26 재검증 후 CLOSED)** 메일 처리 실패 사유별 커스텀 예외 5종이... | 공백 아님, CLOSED | [[tickets/p2-51]] |
| P2-52 | [x] | **(2026-08-26 골든 체크 버킷C 사람 검토에서 발견, 2026-08-26 TDD로 완료)** `IssueFilterType`(assigned/cr... | 완료 | [[tickets/p2-52]] |
| P2-53 | [x] | **(2026-08-26 골든 체크 버킷C 사람 검토에서 발견, 2026-08-26 재검증 후 CLOSED)** legacy `GitBranch`의 `pul... | 공백 아님, CLOSED | [[tickets/p2-53]] |
| P2-54 | [x] | **(2026-08-28 legacy `-_-api/v1` Open API 전수 대조에서 발견)** `GlobalApi.hello()` 헬스체크 엔드포인트 미이식 | 완료 | [[tickets/p2-54]] |
| P2-55 | [x] | **(2026-08-28 legacy `-_-api/v1` Open API 전수 대조에서 발견, 2026-08-28 구현 중 실제 의미 정정)** `Issu... | 완료 | [[tickets/p2-55]] |
| P2-56 | [x] | **(2026-08-28 legacy `-_-api/v1` Open API 전수 대조에서 발견)** 이슈 Open API 전체가 legacy `owner/p... | `IssueApiControllerSpec.kt`/`CommentControllerSpec.kt`/`IssueServic... | [[tickets/p2-56]] |
| P2-57 | [x] | **(2026-08-28 legacy `-_-api/v1` Open API 전수 대조에서 발견)** 게시글(Board) Open API가 legacy `ow... | `BoardApiControllerSpec.kt`/`CommentControllerSpec.kt`/`PostingServ... | [[tickets/p2-57]] |
| P2-58 | [x] | **(2026-08-28 legacy `-_-api/v1` Open API 전수 대조에서 발견)** 마일스톤 생성 Open API가 legacy `owner... | 완료 | [[tickets/p2-58]] |
| P2-59 | [x] | **(2026-08-28 legacy `-_-api/v1` Open API 전수 대조에서 발견)** 프로젝트 생성/라벨생성/exports Open API 경... | 완료 | [[tickets/p2-59]] |
| P2-60 | [x] | **(2026-08-28 legacy `-_-api/v1` Open API 전수 대조에서 발견)** 기본 로그인 페이지 설정 API 경로가 legacy와 다름 | 완료 | [[tickets/p2-60]] |
| P2-61 | [x] | **(2026-08-28 legacy `-_-api/v1` Open API 전수 대조에서 발견)** `StatisticsController`의 `GET /-... | 완료 | [[tickets/p2-61]] |

## P3 — 신규 인프라 개선 (yona 동치성과 무관)

| # | 상태 | 제목 | 한줄요약 | 링크 |
|---|---|---|---|---|
| P3-01 | [x] | Observability(메트릭/로깅/트레이싱) 인프라 도입 | **2026-08-21 사용자 제안, 2026-08-28 TDD로 완료**: yona-wiki 우선순위 2위 항목. `d... | [[tickets/p3-01]] |
| P3-02 | [~] | yona CLI 설계 | **2026-08-21 사용자 제안, 설계 논의만 진행됨 | [[tickets/p3-02]] |
| P3-03 | [x] | SSH git 인증 + GPG 커밋 서명 검증(SSH 키 관리·GPG 키 관리 모두 포함) | **2026-08-21 사용자 제안, 서버 쪽 코드 전무 | [[tickets/p3-03]] |
| P3-04 | [x] | 2단계: 브랜치 보호 | **2026-08-21 사용자 제안, 전무 | [[tickets/p3-04]] |
| P3-05 | [ ] | CI/Actions — GitHub Actions 러너 아키텍처 이식 (워크플로 YAML + 러너 등록/폴링) | **2026-08-24 사용자 결정: GitHub Actions 아키텍처를 그대로 이식.** 범위는 핵심만 | [[tickets/p3-05]] |
| P3-06 | [x] | 엔터프라이즈 SSO(SAML2 / 범용 OIDC) | **2026-08-24 사용자 제안, 코드 검증 완료(LDAP은 이미 이식 완료 확인)**: `LdapService.kt... | [[tickets/p3-06]] |
| P3-07 | [x] | yona MCP 서버 (이슈/PR 읽기·쓰기) | **2026-08-24 사용자 제안, 범위: 읽기+쓰기 결정.** AI 에이전트(Claude Code 등)가 MCP(Mo... | [[tickets/p3-07]] |
| P3-08 | [x] | H2(내장형 DB) 지원 추가 — legacy가 기본값으로 제공하던 "설치 없이 바로 써보기" 옵션 대응(2026-08-28 사용자 요청, TDD로 완료) | 완료 | [[tickets/p3-08]] |
| P3-09 | [x] | Swagger/OpenAPI UI 노출 (2026-08-28 사용자 제안, TDD로 완료) | 완료 | [[tickets/p3-09]] |
| P3-10 | [x] | 전체 의존성 최신 버전 업데이트 (2026-08-28 사용자 요청, TDD/전체 스위트로 완료) | 완료 | [[tickets/p3-10]] |
| P3-11 | [x] | 새 프로젝트 기본 브랜치를 "main"으로 결정론적 고정 (2026-08-31 사용자 요청, TDD로 완료) | 완료 | [[tickets/p3-11]] |
| P3-12 | [x] | Mercurial(hg4j) 저장소 지원 추가 (2026-08-31 사용자 제안) | 완료(2라운드) — 1라운드: hg4j 연결 + 저장소 골격(생성/조회/커밋 이력). 2라운드: 브랜치(bookmark)/태그 CRUD, HTTP 프로토콜 서빙(`HgController`+`HgAuthorizationFilter`), 1라운드에서 발견한 `.git` 접미사 버그 수정(SVN도 함께), SSH 쪽 read-only/비멤버 push 거부 테스트. 실제 `hg` 바이너리로 HTTP/SSH clone·push 모두 end-to-end 검증. 의도적 범위 밖(사용자 확정): named branch, diff/patch/archive, Hg 전용 브랜치 보호 정책, push 알림/웹훅 배선 | [[tickets/p3-12]] |
| P3-13 | [ ] | 프런트엔드 분리 (React/Vue3/Angular 등 SPA) (2026-08-31 사용자 제안, 계획서만 작성 — 코드 0줄) | 계획서: [`docs/yona-wiki/plans/p3-13-decoupled-spa-frontend.md`](yona-... | [[tickets/p3-13]] |
| P3-14 | [x] | yona를 OAuth2 서버(Authorization Server)로 제공 — 제3자 앱의 "Sign in with yona"/위임 API 접근 지원 | 완료(2라운드) — 1라운드: 리소스 레지스트리 일반화/`/api/v1` OAuth/앱 등록 UI. 2라운드: OIDC discovery/ID 토큰/`/userinfo`, identity 스코프(openid/profile/email) 선택형 부여, 스코프-클레임 게이팅 검증 | [[tickets/p3-14]] |
| P3-15 | [x] | PR 승인/변경요청(리뷰 판정) 워크플로 추가 — GitHub의 Approve/Request changes에 대응 | **2026-09-06 사용자 제안, P3-04(브랜치 보호) 작업 중 스파이크로 발견된 갭 | [[tickets/p3-15]] |
| P3-16 | [x] | Git 태그(tag) 지원 (2026-09-03 사용자 제안, 2026-09-07 TDD로 완료) | 완료 | [[tickets/p3-16]] |
| P3-17 | [x] | OAuth2 앱 등록을 사이트 관리자 전용 → 사용자 셀프서비스로 전환 (2026-09-07 사용자 제안) | 완료 — `/user/editform/oauth-apps-owned` 셀프서비스 등록 신설, 관리자 화면(`/site/oauth-apps`)은 전체 조회/강제 삭제 감사(audit) 용도로 축소, IDOR 방지 검증 포함 | [[tickets/p3-17]] |
| P3-18 | [x] | SSH forced command가 실제 git/hg 바이너리 exec 대신 인프로세스 JGit/hg4j 로직을 재사용하도록 전환 (2026-09-07 사용자 제안) | 완료 — 유닉스 도메인 소켓 릴레이 + `GitSshProtocolHandler`/`HgSshProtocolHandler` 신설, `ssh-auth.sh`+`socat` 실배선까지 완료. 실제 컨테이너(sshd+yona)에 end-to-end로 검증(브랜치 보호 push 거부, PRIVATE 비멤버 clone 거부 포함) — 이 과정에서 nologin 셸 버그/역슬래시 이스케이프 버그 실측 발견·수정. Hg 쪽 read-only push 거부 자동 테스트만 P3-12 2라운드로 이월 | [[tickets/p3-18]] |
| P3-19 | [x] | GPG 커밋 서명 검증(Verified/Unverified 배지)을 Git 실사용 검증 + Mercurial까지 확장 (2026-09-08 사용자 제안) | 완료 — Git 실사용 검증 완료(버그 없음), Mercurial은 changelog `extra`에 git `gpgsig`와 동일한 셰이프로 내장하는 방식으로 hg4j·yona 양쪽 구현+테스트+실사용 검증까지 완료 | [[tickets/p3-19]] |
| P3-20 | [x] | Mercurial 저장소 zip 아카이브 다운로드 미구현(`HgRepository.getArchive()`가 빈 no-op) | 완료 — hg4j `ArchiveCommand`(임시 파일 경유 스트림 복사)로 실구현, 실사용 검증(실제 hg push + 다운로드 + unzip 내용 확인). 검증 과정에서 브랜치명(bookmark/named branch) 해석이 전혀 안 되던 잠재 버그와 Mercurial 코드 브라우저 기본 진입 리다이렉트가 항상 "master"로 가던 버그를 함께 발견·수정 | [[tickets/p3-20]] |
| P3-21 | [x] | Mercurial 브랜치 보호 정책(require_pull_request 등) 미적용 — HTTP/SSH 어느 경로에도 없음 | 완료 — hg4j에 pushkey 전용 pre/post 훅(`registerPre/PostPushkeyHook`) 신설, `HgBranchProtectionPrePushkeyHook`(git `BranchProtectionPreReceiveHook`과 대응)로 HTTP/SSH 양쪽에 배선. 실제 컨테이너(sshd+yona)로 보호된 북마크 직접 push 거부(HTTP/SSH 둘 다)와 require_signed_commits(실제 gpg) 검증 완료. Mercurial PR 병합은 여전히 JGit 전용이라 미동작함을 실측 확인·문서화(범위 밖) | [[tickets/p3-21]] |
| P3-22 | [x] | Mercurial push 시 알림/웹훅/PushedBranch(최근 push 브랜치) 추적 미발행 | 완료 — P3-21의 pushkey post-훅에 `HgYonaPostPushkeyHook`(git `YonaPostReceiveHook` 대응) 연결. 실제 컨테이너로 웹훅 발송(Git과 동일 JSON 스키마)·알림 이벤트 기록·PushedBranch DB 반영까지 실사용 검증(REST 조회 API 자체는 기존 순환참조 직렬화 버그로 깨져 있어 SQL 로그로 대체 확인) | [[tickets/p3-22]] |
| P3-23 | [x] | Mercurial named branch(`hg branch`) 읽기/쓰기 지원 및 UI 노출 없음 | 완료 — 읽기: hg4j `BranchesCommand`로 코드브라우저/커밋히스토리 셀렉터에 "Bookmarks"/"Branches" 그룹 분리 노출. 쓰기: 기존 온라인 커밋(P1-111)에 Mercurial 분기+named branch 입력 필드 추가, `hg branch`→커밋→(신규 시)bookmark 전진까지 실배선. 실사용 검증(`hg log -b`로 서버 쪽 확인) 중 온라인 커밋이 고아 루트 커밋을 만들어 기존 파일이 사라지는 실제 데이터 유실 버그를 발견·수정 | [[tickets/p3-23]] |
| P3-24 | [x] | hg4j `GpgSignature`가 RSA 키로 하드코딩돼 있어 EdDSA 등 다른 알고리즘 서명을 검증 못 함 | (2026-09-09 사용자 지시) | [[tickets/p3-24]] |
| P3-25 | [x] | hg4j `Wire1CommandsCoverageTest`의 기존 flaky 실패(빈 저장소 changegroup) 근본 수정 | 완료 — 테스트 자체의 낡은 기대값(예전 getBundle 버그를 검증하던 것)이었음을 확인, 실제 페이로드 파싱 검증으로 수정 | [[tickets/p3-25]] |
| P3-26 | [x] | `/api/{owner}/{projectName}/pushedBranches`가 순환 참조로 부풀고 비밀번호 해시까지 노출됨 | 완료 — DTO 변환으로 수정(Git/Hg 공통 기존 결함, 코디네이터가 P3-22 검증 중 발견) | [[tickets/p3-26]] |
| P3-27 | [x] | Mercurial 프로젝트의 Pull Request 병합이 동작하지 않음(JGit 하드코딩) | 완료 — hg4j `TreeMergeCommand`(순수 충돌계산)+`MergeCommand`+`CommitCommand`(임시 클론에서 실제 2-parent 머지 커밋 생성 후 push)로 `PullRequestServiceImpl`의 4개 메서드 전부 구현. 조사 중 발견한 hg4j 전제 버그(병합 커밋만 push 시 changegroup 손상)도 함께 수정. 관련 테스트 204개(P3-27 신규 7개 포함) 전부 GREEN, Git 경로 무회귀 | [[tickets/p3-27]] |
| P3-28 | [x] | `PullRequestController`의 8개 엔드포인트가 순환 참조로 부풀고 비밀번호 해시까지 노출됨 | 완료 — P3-26과 동일 근본원인(raw 엔티티 직렬화), PR 생성/조회/수정/담당자/라벨 등 전체 엔드포인트 DTO 변환으로 수정 | [[tickets/p3-28]] |
| P3-29 | [x] | MCP `review_pull_request` 도구가 P3-15(실제 Approve/Request changes) 대신 낡은 "리뷰어 등록"에만 매핑돼 있음 | 완료 — 사용자가 기억하던 "PR 승인 이슈", submitReview()에 연결, 기존 동작은 add_reviewer로 분리 보존 | [[tickets/p3-29]] |
| P3-30 | [x] | `IssueController`/`IssueApiController`/`BoardController`/`CommentController`/`MilestoneController`(24개 엔드포인트)에도 P3-26/28과 동일한 순환참조/비밀번호 노출 | 완료 — P3-26/28과 동일 근본원인, 커밋 `d1023909b`로 5개 컨트롤러 전부 DTO 변환(관련 테스트 253개 GREEN). 실사용 로그인 검증 중 별도 로그인 버그를 발견해 [[tickets/p3-31|P3-31]]로 분리·수정 | [[tickets/p3-30]] |
| P3-31 | [x] | 로컬 로그인 시 비밀번호가 틀리면 정상 오류 대신 500이 나던 회귀 | 완료 — 전역 AuthenticationManagerBuilder 초기화 순서 경쟁으로 DaoAuthenticationProvider가 몰래 끼어들던 문제, `@Autowired configureGlobalAuthentication()` 훅으로 결정적 등록해 수정. OAuth2/OIDC/LDAP/DeployKey 등 47개 테스트 무회귀 확인 | [[tickets/p3-31]] |
| P3-32 | [x] | SAML 로그인 사용자가 yona를 OAuth2 공급자로 쓰면 동의/토큰 발급이 500으로 깨짐 | 완료 — 실제 test-saml-idp 컨테이너+브라우저로 전체 플로우 실사용 검증 중 발견. YonaSaml2AuthenticatedPrincipal이 Jackson 다형성 화이트리스트에 없었고(1단계), 그 안에 raw User 엔티티를 물고 있어 화이트리스트 추가 후에도 재발(2단계) — YonaUserDetails와 동일하게 평탄화해 수정. 토큰 교환까지 200 성공, id_token sub 클레임 일치 확인, 166개 테스트 무회귀 | [[tickets/p3-32]] |
| P3-33 | [x] | hg4j PR 병합(P3-27 hgMerge)이 매번 대상 저장소 전체를 재구성하는 성능 문제 | hg4j에 `MergeCommitCommand` 신설(TreeMergeResult+두 부모 노드ID+커밋 메타데이터만으로 filelog/manifest/changelog에 직접 새 리비전을 씀, 작업 디렉터리/dirstate 무관, fail-fast `lockStore()`만 사용) — `TreeMergeCommand`에 copy/rename 메타데이터 보존(`getCopiedFiles()`) 추가. yona `hgMerge()`를 임시클론/체크아웃/push/삭제 없이 toProject 저장소에 직접 병합 커밋을 쓰도록 재작성, JGit식 낙관적 동시성(bookmark 기준점 확인, fail-fast) 도입. hg4j `MergeCommitCommandTest` 7/7 + real hg CLI interop 2/2, yona `PullRequestServiceSpec` 112/112(P3-27 7개 시나리오 무회귀) + 관련 회귀(`PullRequestApiControllerSpec`/`PullRequestMcpToolsSpec`/`RepositoryServiceSpec`/`HgRepositorySpec`) 전부 통과 | [[tickets/p3-33]] |
| P3-34 | [x] | SVN도 Git/Hg처럼 실제 서버(`RANDOM_PORT`)+실제 CLI 바이너리로 HTTP interop 검증하는 테스트가 없음 (2026-09-09 사용자 제안) | `SvnHttpProtocolIntegrationSpec` 신설(PUBLIC checkout, 멤버 Basic 인증 commit+별도 checkout 재검증, Deploy Key checkout/commit round-trip 3개 시나리오, 실제 `svn` 1.14.5 바이너리+RANDOM_PORT 실서버). 실측 중 진짜 버그 발견·수정: DAVServlet이 커밋 협상 과정에서 정상적으로 던지는 `sendError(404)`(신규 파일 존재 확인용 HEAD)를 Spring Boot 전역 에러페이지 재-dispatch가 가로채 BasicErrorController가 `text/xml` 컨텐츠타입을 그대로 존중하려다 XML 컨버터 부재로 `HttpMessageNotWritableException`을 던져 빈 바디 500으로 뒤바뀌는 문제(SVNKit DAVServlet 응답에 한정) — `SvnServletResponseWrapper` 신설로 sendError를 setStatus로 치환해 컨테이너 재-dispatch를 우회, 실제 svn commit이 정상 성공하도록 수정. SVN 관련 스펙 전체(10개 클래스) 재실행 전부 GREEN. 이후 사용자 요청으로 checkout/commit 왕복 하나만으로는 부족하다는 지적을 받아 mkdir/add/edit/revert/copy/move/propset/delete/update/log/cat/export 전체를 왕복하는 4번째 시나리오 추가(관련 SVN 스펙 10개 122개 테스트 GREEN) | [[tickets/p3-34]] |
| P3-35 | [x] | `project fork`가 REST API/`yona-cli`로는 조직(organization) 목적지를 지원하지 않음 (2026-09-09 사용자 요청으로 yona-cli 미구현 항목 재조사 중 코디네이터 발견) | 구현 중 **실제 보안 취약점 발견·수정**: `ProjectServiceImpl.forkProject()`에 목적지 소유권 검증이 전혀 없어, 로그인만 한 임의 사용자가 자신이 속하지 않은 조직/다른 사용자 이름으로 fork해 그 네임스페이스에 프로젝트를 만들고 스스로 MANAGER가 될 수 있는 인가 우회(namespace squatting)였다 — `acceptTransfer()`가 이미 쓰던 `isAuthorizedToAcceptTransfer()`를 재사용해 본인 계정이거나 ORG_ADMIN인 조직만 허용하도록 근본 수정. 이후 REST API(`ProjectController.ForkProjectRequest`)/`yona-cli`(`--to-owner`/`--to-name`)에 조직 목적지 노출. 실사용 검증(관리 조직 fork 성공/무단 namespace 거부/실존 타사용자 사칭 거부 3개 시나리오 실제 서버+실 CLI로 확인) | [[tickets/p3-35]] |
| P3-36 | [x] | `/api/v1/search/**`, `/api/v1/organizations/**`는 Fine-grained PAT 스코프 토큰으로 인증되지 않음(세션/레거시 전권 토큰만 가능) (2026-09-09 사용자 질문으로 재조사 후 TDD로 즉시 착수) | P3-02 계획의 "미해결" 리스크 항목이 이미 낡은 기록이었음을 확인 — 16라운드(`gh status`)가 만든 `AccountLevelTarget` 메커니즘을 확장하는 것만으로 해소(새 스코프 축 설계 불필요). `/search/issues`→ISSUES, `/search/prs`→PULL_REQUESTS, `/search/projects`+`/organizations/**`→ADMINISTRATION(`ResourceType.ORGANIZATION`은 이미 이 그룹에 매핑돼 있던 기존 타입 재사용). TDD로 RED(수정 전 전부 401 확인)→GREEN(신규 10개 테스트 통과) 진행, `config.*` 패키지 전체(~700개 테스트)+관련 컨트롤러 스펙 재실행 전부 무회귀 | [[tickets/p3-36]] |
| P3-37 | [x] | `UserApi.java` 이식 4종(newUser/newToken/users/updateUserState)+통계 1종이 `-_-api/v1` 원본 경로를 안 지킨 판단 오류(P1-118, 2026-08-21) 수정 | 사용자가 "레거시에 /api/admin/users 라는게 있어?"로 재조사를 유도해 발견 — P1-118 당시 "`-_-api/v1`은 Play 프레임워크 아티팩트라 이식 대상 아님"이라 검증 없이 판단했는데, 실제로는 v1.6 원본에 그대로 있던 API였고 일주일 뒤 P2-54~61이 정반대 결론(35개 엔드포인트 원본 경로 그대로 이식)을 내렸는데도 이 4종만 재검토에서 빠졌던 것. TDD로 1차는 원본 경로를 별칭 추가했다가, 사용자 지적으로 `/api/*` 경로를 내부에서 쓰는 곳이 전혀 없음을 전수 확인한 뒤 2차로 완전히 제거(별칭이 아니라 치환) — `UserControllerSpec`(74)/`StatisticsControllerSpec`(4) GREEN + 실제 서버로 원본 경로 200/제거된 경로 404 확인, 무관한 `GET /api/users`(검색) 무회귀 | [[tickets/p3-37]] |
| P3-38 | [x] | v1.6 원본이 `-_-api/v1` 밖에도 이중 매핑해둔 댓글 수정 경로(PATCH) 2개가 이식에서 누락됨 | 사용자가 "레거시에도 -_-api/v1 아닌 애가 있어?"로 재조사 유도 — v1.6 `conf/routes`를 `controllers.api.*` 기준 전수 재검색해 `IssueApi.updateIssueComment`/`BoardApi.updatePostingComment`가 원본에서부터 `-_-api/v1`(PUT) 공식 경로와 bare 경로(PATCH, 웹 UI 자체 AJAX용으로 추정) 둘 다로 이중 매핑돼 있었음을 발견 — yona는 전자만 이식돼 있었다. `CommentController.kt`의 두 메서드에 `@RequestMapping(value=[...], method=[PUT,PATCH])`로 원본 bare 경로 추가. `CommentControllerSpec`(66, 신규 2) GREEN + 실제 서버로 이슈 생성→댓글 생성→bare PATCH 경로로 실제 수정 성공(sha1 충돌 409도 재현) end-to-end 검증. (후속: 이 검증이 실은 잘못된 프로토콜 가정 위에서 이뤄졌음을 P3-39가 발견) | [[tickets/p3-38]] |
| P3-39 | [x] | legacy Open API 동시편집 충돌감지 4곳(이슈/게시글 본문·댓글 수정)이 실제 프로토콜과 다른 필드명(`sha1`)/검증 방식(한쪽만 해시)을 쓰고 있던 결함 | P3-38 검증 중 "v1.6 템플릿에서 PATCH를 호출하는 게 있는지" 확인하려 실제 클라이언트 JS(`yona.Tasklist.js`, 마크다운 체크박스 저장)를 열어보니 `{content, original}`(원문 텍스트 그대로)을 보내고 있었다 — v1.6 원본 서버도 양쪽(클라이언트 원문+서버 현재값)을 각각 해시해서 비교하는데, yona는 "클라이언트가 이미 해시를 보낸다"는 검증 없는 가정으로 별도의 틀린 함수(`isModifiedByOthersLegacyChecksum`)를 4곳에 써서 실제 legacy 클라이언트라면 거의 항상 409로 거부됐을 결함이었다. 이미 정확하게 이식돼 있던 `isModifiedByOthers()`(양쪽 다 해시)로 통일하고 DTO 필드명도 `sha1`→`original`로 정정. `CommentControllerSpec`(66)/`IssueApiControllerSpec`(15)/`BoardApiControllerSpec`(6) GREEN + 실제 서버로 해시 없이 원문 그대로 보내는 진짜 프로토콜로 재검증(200 성공/충돌 409 둘 다 확인) | [[tickets/p3-39]] |
| P3-40 | [x] | annotated 태그로 커밋 히스토리 조회 시 500(`IncorrectObjectTypeException`) | 사용자가 직접 실제 서버에서 `git`으로 만든 annotated 태그(v1.0.0)의 커밋 히스토리 화면(`/{owner}/{project}/commits/{tag}`)을 열어 500을 재현·신고 — `GitRepository.getHistory()`가 annotated 태그(ref가 커밋이 아니라 별도 태그 오브젝트를 가리킴)의 ObjectId를 peel 없이 `LogCommand.add()`에 그대로 넘겨 발생. 같은 파일의 다른 14곳 `resolve()` 호출부는 전부 `RevWalk.parseCommit()`/`parseTree()`로 이미 peel하고 있었는데 `getHistory()`만 그 관례를 빠뜨렸음(전수 검색으로 확인). `RevWalk(repo).parseCommit(objectId)`로 한 줄 수정. TDD로 RED(수정 되돌려 동일 예외 재현 확인)→GREEN, 사용자가 신고했던 바로 그 URL로 재검증(200) | [[tickets/p3-40]] |
| P3-41 | [x] | README/이슈 템플릿 편집 진입점에서 제목이 항상 빈칸으로 시작해 매번 재입력해야 함 | 사용자가 `postform?readme=true`에서 실사용 재현·신고 — 프로젝트 홈의 "README 만들기/편집" 버튼이 항상 create 폼으로 가고 기존 제목을 조회하지 않는 구조 자체는 v1.6과 동일(회귀 아님)했지만, v1.6은 title을 고정 문자열("Update README.md"/"ISSUE_TEMPLATE.md: Project Issue Template")로 프리필해 재입력 부담이 없었는데 그 프리필이 이식에서 빠져있었음. `BoardViewController.createPostForm()`에 `preparedTitle` 계산 추가 + `board/create.html`에 반영. `BoardViewControllerSpec`(87) GREEN + 실제 서버로 신고 URL 재검증 | [[tickets/p3-41]] |
| P3-42 | [x] | GitHub/Forgejo 수준의 프로젝트 위키 기능 추가 (2026-09-09 사용자 제안, 2026-09-10 완료) | legacy v1.6엔 위키 자체가 없어(완전 신규 기능) 동치성 이식 대상 아님. Forgejo 방식대로 DB 엔티티 없이 프로젝트별 bare git 저장소(`<owner>/<project>.wiki.git`)만으로 구현 — 페이지 CRUD/`_Sidebar`·`_Footer`·`Home` 특수 페이지/페이지별 히스토리·diff/커밋 메시지 커스터마이징/git clone·push 접근/멤버 권한/슬래시 중첩 페이지/제목 검색 8개 전부 + yona-cli(`yona wiki` 명령)/MCP 서버 도구(`WikiMcpTools`)까지 구현. 실제 서버+`git`/`yona-cli` 바이너리로 골든패스 실사용 검증 완료. 코디네이터 재검증(dead code 제거, 주석 트리밍, 79건+14건 재실행 GREEN 확인) 및 사용자가 실사용 중 발견한 후속 결함(`isWikiEnabled` 프로젝트 토글 누락 — Code/Issue/PR/Review/Milestone/Board는 전부 있는데 위키만 없어 `/projectform`/설정 화면에서 켜고 끌 수 없었음) 수정 완료 — 상세는 티켓 완료 로그 참고. | [[tickets/p3-42]] |
| P3-43 | [x] | yona 자체(로컬) 로그인에 2FA(WebAuthn 우선 + TOTP 폴백) 선택 인증 옵션 추가 | legacy v1.6엔 2FA 자체가 없어(완전 신규 기능) 동치성 이식 대상 아님. 계정별 선택적 2단계 인증 완료 — WebAuthn/TOTP 둘 다 계정당 여러 개 등록 가능(신규 테이블 3개: `user_totp_credential`/`user_webauthn_credential`/`user_backup_code`, `ssh_key`와 일관된 1:N 설계, User FK는 ON DELETE CASCADE). 로그인 시 WebAuthn 우선(Spring Security 7.1.1 내장 `WebAuthnRelyingPartyOperations`를 building block으로 직접 사용, HttpSecurity DSL은 미사용), 실패/거부 시 "다시 시도"/"다른 방법 사용(TOTP)"/"복구 코드 사용" 노출(`Pre2faAuthenticationToken` + `Pre2faGateFilter`로 대기 상태 구현). TOTP는 `dev.samstevens.totp`(secret은 AES 암호화 저장), 백업코드 8개·재발급 시 전량 무효화(SHA-256 해시 저장). 사용자 본인 비활성화(비밀번호 재확인) + 관리자 강제 비활성화(`POST /-_-api/v1/admin/users/{loginId}/disable-2fa`) 모두 구현. WebAuthn4J 가상 인증기로 실제 서명 검증까지 통과하는 통합테스트 포함, 전체 회귀 스위트(6424건) GREEN. 상세는 티켓 완료 로그 참고. | [[tickets/p3-43]] |
| P3-44 | [x] | 로그아웃 성공 시 302 리다이렉트 대신 204가 반환됨(CSRF 재활성화 작업 중 부수 발견, 2026-09-10) | 근본 원인을 라이브러리 소스(spring-security-config 7.1.1)로 직접 확인 — `SimpleUrlLogoutSuccessHandler` 자체 결함이 아니라 `.httpBasic { }`가 `HttpBasicConfigurer.registerDefaultLogoutSuccessHandler()`를 통해 `LogoutConfigurer`에 `defaultLogoutSuccessHandlerFor(HttpStatusReturningLogoutSuccessHandler(204), preferredMatcher)`를 자동 등록해두는데, 그 매처가 "X-Requested-With: XMLHttpRequest" 또는 "Accept가 text/html이 아닌(헤더 자체가 없는 경우 포함) 요청"에 매치돼 `.logoutSuccessUrl(...)`로 지정한 리다이렉트를 덮어써버린 것 — jQuery `$.post`가 자동으로 붙이는 `X-Requested-With` 헤더가 정확히 여기 걸렸다. `SecurityConfig.kt`의 `.logout { }`을 `.logoutSuccessHandler(SimpleUrlLogoutSuccessHandler(...))`로 핸들러를 직접 지정해 이 기본 매핑을 완전히 무시하도록 수정, 이제 AJAX 요청에도 항상 302+`Location`이 나간다. `LogoutCsrfIntegrationSpec.kt`를 204 기대값에서 302+`Location` 기대값으로 갱신(RED로 204 재확인 후 GREEN 전환 확인). `site/layout.html::scripts`의 `.js-logout-link` 핸들러는 XHR이 same-origin 302를 조용히 따라가 버려 브라우저 내비게이션이 발생하지 않으므로, 폼 제출 전환 대신 기존 AJAX 콜백 골격을 유지하되 `.always()`→`.done()`으로 좁혀 성공(2xx) 시에만 로그인 페이지로 이동시키고 실패 시엔 사용자에게 알리도록 수정(실패해도 무조건 "로그아웃됨"으로 보이던 오인 가능성 제거). 전체 회귀(`-Dyona.it.db=h2`) 재실행 결과는 티켓 완료 로그 참고 | [[tickets/p3-44]] |
| P3-46 | [x] | 클라이언트 위젯 라이브러리 현대화 — Select2/Pikaday/Jcrop/atjs/ViewerJS/marked.js/highlight.js가 오래되거나 유지보수가 느림 (2026-09-10 사용자 제안, [[tickets/p3-45]] 논의 중 파생) | 계획서 없음(권장 교체안만 정리) — Select2→Tom Select/Choices.js, Pikaday→Flatpickr, Jcrop→Cropper.js, atjs→Tribute.js, ViewerJS→PhotoSwipe(또는 jQuery 래퍼 제거한 순정 Viewer.js), marked.js/highlight.js는 교체보다 메이저 버전업 우선 검토. "마크다운 에디터"는 실제로는 라이브러리가 아니라 textarea+수제 탭 UI(`site/layout.html:359`)임을 확인 — EasyMDE/CodeMirror 6로 가면 스왑이 아니라 재설계이므로 별도 승인 필요. 착수 순서·마크다운 에디터 방향은 미결정 | [[tickets/p3-46]] |
| P3-47 | [x] | board/edit.html 인라인 스크립트의 따옴표 없는 문자열 대입으로 `ReferenceError` 발생 (2026-09-10, P3-46 리뷰 중 발견·Playwright로 실제 재현·수정 완료) | `var owner = [[${project.owner}]];`가 `var owner = admin;`으로 렌더링돼 `ReferenceError`. `code/nohead.html`이 이미 쓰는 정석 패턴(`/*[[...]]*/ ''`)으로 수정, Playwright로 해소 확인. **정정**: 같은 패턴처럼 보였던 5개 파일은 `th:inline="javascript"`가 있어 실제로는 정상이었음(오탐, 변경분 원복). 저장이 최종적으로 되려면 별도 발견된 [[tickets/p3-48\|P3-48]](CSRF 403)도 필요 | [[tickets/p3-47]] |
| P3-48 | [x] | (긴급) CSRF 재활성화 이후 raw `$.ajax` PUT/DELETE/PATCH 호출이 CSRF 토큰 없이 403으로 거부될 가능성 (2026-09-10, P3-47 검증 중 발견) | 나머지 11개 화면 전부 Playwright 실사용 재현 완료(403/CSRF 오류 없음). 재현 과정에서 CSRF와 무관한 버그 5건 추가 발견·수정: (1) `jQuery.ajaxSetup(beforeSend)`가 `yona.Tasklist.js`처럼 개별 호출이 자기 `beforeSend`를 넘기면 완전히 덮어써지는 구조적 결함(진짜 CSRF 버그) — `jQuery(document).ajaxSend`로 전면 교체, (2)(3) 이슈/게시글 tasklist 폼 action URL이 owner/projectName→project.id, board는 id→number까지 2차 정정 필요, (4) 이슈/게시글 새 댓글 등록 폼이 존재하지 않는 경로를 가리켜 로그인 사용자도 최초 댓글을 못 달던 버그, (5) 마크다운 렌더러에 GFM tasklist 확장이 빠져있었고 있어도 항상 disabled라 체크박스를 아무도 못 눌렀던 버그, (6) SVN 프로젝트 커밋 상세 화면이 SpEL 빈 참조 문법 오류로 항상 응답이 끊기던 버그 | [[tickets/p3-48]] |
| P3-49 | [x] | 이슈/게시글 댓글 화면 파일 업로드(드래그드롭/클릭/붙여넣기)가 죽어있음 (2026-09-10 발견, 2026-09-11 수정 완료) | v1.6 재대조로 원 티켓의 "원인 2"(새 댓글 textarea 즉시업로드 미포팅) 진단이 틀렸음을 발견·정정 — `common.fileUploader`가 formId="upload"를 하드코딩하는 legacy 구조상 새 댓글 폼의 클릭/드롭존/textarea 드래그드롭/붙여넣기는 `yobi.Files.js` 하나로 완결되고, `yona.CommentAttachmentsUpdate.js`는 실제로는 "기존 댓글 수정 시 첨부파일 관리"([[tickets/p3-50|P3-50]]로 분리) 전용이었다. 실제 결함은 `issue/view.html`(script 안 `<th:block>` 중첩으로 JS 파싱 파괴) + `issue/view.html`·`board/view.html`(원 티켓엔 없던 후자도 전수 발견) 둘 다의 `uploadForm(...)` 호출이 `formId=null`이라 `id="upload"` 미렌더링 두 가지. `<th:block>` 제거는 `th:inline="javascript"` 블록주석으로 1차 시도했다가 같은 스크립트의 다른 `[[...]]` 표현식이 JSON 재이스케이프되는 회귀를 겪어(따옴표 중복/슬래시 이스케이프, curl로 발견) 조건부 호출을 별도 `<script th:if>` 태그로 분리하는 2차 수정으로 해결. 신규 스펙 4건 + 회귀 스펙 289건 GREEN, 실서버로 CSRF 포함 전체 흐름 재검증 | [[tickets/p3-49]] |
| P3-50 | [x] | 댓글 수정 폼에서 기존 첨부파일 목록이 안 보이고 관리도 불가능함 (2026-09-11, P3-49 재조사 중 발견) | `commentUpdateForm.html`을 legacy 마크업으로 재작성 + 댓글별 첨부파일 모델 전달 + `yona.CommentAttachmentsUpdate.js` 로드 + 삭제 API `_method=delete` 파라미터 수정 + "알림 메일 받기" 체크박스와 `COMMENT_UPDATED` 알림 발행 백엔드까지 구현. 착수 중 이보다 큰 사전 버그 2건 발견·해결: (1) 새 댓글 생성 시 첨부파일이 컨테이너에 전혀 연결되지 않던 버그, (2) **`yobi.Files.js`/`yobi.Attachments.js`가 어느 템플릿에서도 로드된 적이 없어 EasyMDE를 모르는 `yona-lib.js` 구버전 사본이 대신 실행 중이었던 버그**(`yobi.Markdown.js`와 동일한 함정) — 업로드 후 카드 클릭으로 링크를 넣어도 CodeMirror가 인지 못해 저장 시점에 그 링크가 통째로 사라지는 데이터 손실급 결함이었음. 스크립트 로드 순서 정정 + raw textarea 계산 결과를 `easyMDE.value()`로 강제 재동기화하는 방식으로 해결 | [[tickets/p3-50]] |
| P3-51 | [x] | `spring.messages.fallback-to-system-locale`(Spring Boot 기본값 true)로 인해 배포 환경의 JVM 기본 로케일에 따라 i18n 메시지가 요청 로케일과 무관하게 뒤바뀔 수 있음 (2026-09-11, Twirl/Thymeleaf 렌더링 감사 중 발견) | `fallback-to-system-locale: false` + 신규 `localeResolver` 빈(`Locale.ENGLISH` 기본값)으로 "Accept-Language 없음→영어/en→영어/ko→한국어"가 결정적으로 동작하도록 수정. 이로 인해 새로 깨진 53건(16개 스펙 파일)을 개별 검토해 한국어 UI 동치성이 목적인 테스트는 `.locale(Locale.KOREAN)` 명시, 우연히 기본 로케일에 의존했던 테스트는 기대값을 root(영어) 번들로 수정. curl+Playwright로 en/ko/헤더없음 세 케이스 모두 재검증 완료 | [[tickets/p3-51]] |
| P3-52 | [x] | legacy가 로드하던 일부 스크립트가 포팅본에 로드되지 않아 생긴 기능 격차 묶음 (2026-09-11, Twirl/Thymeleaf 렌더링 감사 카테고리 C 전수 스캔) | 4개 항목 모두 해소. (4) PR 코드리뷰 diff 화면에 새 인라인 댓글 작성 UI가 실제로 없었음을 확인해 code/diff.html과 동일한 클릭→폼삽입 패턴으로 이식(백엔드 codeRange API는 이미 존재). (1) 이슈 댓글 알림수신자 미리보기용 신규 REST 엔드포인트(`commentNotiReceivers`) + JS 연동. (2) 이슈 상세 화면 변경 감지 폴링(`detectChange`) 배선(백엔드는 이미 존재). (3) 댓글 폼 빈값 제출 방지/Ctrl+Shift+Enter/이탈 경고/임시저장을 기존 AJAX 제출 핸들러에 직접 재구현(레거시 스크립트 원본은 이중 제출 버그를 유발해 로드하지 않기로 결정, 파일은 삭제하지 않고 유지). Playwright 실브라우저 검증 중 EasyMDE/CodeMirror keydown 미도달 버그와 인라인 스크립트 따옴표 누락 버그 2건을 추가로 발견해 수정 | [[tickets/p3-52]] |
| P3-53 | [x] | P3-46 8-1/8-2단계(EasyMDE 셸 교체·미리보기 연동)에서 남겨진 죽은 코드·미정리 항목 8개 묶음 (2026-09-11, 사용자 요청으로 재정리) | (1)~(4)는 이슈/게시글 댓글 파일업로드 관련 죽은 코드·로드 누락으로 [[tickets/p3-49|P3-49]]에서 대부분 해소(화면 연동만 [[tickets/p3-50|P3-50]]에 남음), (5) `PullRequestListTemplateEquivalenceSpec`의 stale한 select2.js 로드 단언, (6) 고아가 된 `.nav-tabs.small` CSS 규칙 삭제 완료, (7) `.markdown-preview`의 `editorMode` 분기는 8-2단계에서 완전히 죽은 코드로 확정(해소 완료), (8) 실브라우저 재현 없이 "문제 없음"으로 결론 냈던 것에 대한 작업방식 교훈(코드 변경 아님). (2) `yona.SubComment.js` 미로드는 CSS `.comments` 조상 스코프 불일치가 진짜 원인(대댓글 입력창 상시노출 UI 회귀)으로 재규명해 마크업에 `.comments` 클래스 추가 + 스크립트 로드로 완료(Playwright 재검증 완료) | [[tickets/p3-53]] |
| P3-54 | [x] | `site/postList.html`/`site/issueList.html`이 authorLoginId가 null인 게시글/이슈를 만나면 500 에러 (2026-09-11, P3-48/50/51/52 완료 후 전체 회귀 재검증 중 재확인 — Twirl/Thymeleaf 감사 baseline 실패 2건과 동일) | `post.authorLoginId != null ? userRepository.findByLoginId(...).orElse(null) : null` 널 가드로 수정. authorLoginId가 null인 게시글/이슈 픽스처로 RED→GREEN 확인, 실제 서버+DB(H2 AUTO_SERVER 직접 연결로 실제 데이터를 null로 만든 뒤)로 Playwright 재현까지 완료 | [[tickets/p3-54]] |
| P3-55 | [x] | PR "Changes" 탭 코드리뷰 댓글 UX가 두 갈래(범위없는 `#review-form`/라인별 인라인삽입 폼)로 쪼개져 있고, `#review-form`은 트리거가 없어 죽어있고 라인별 쪽은 `<yona-markdown-editor>`가 아예 안 붙어있음 (2026-09-11, P3-46 CM6-7단계 16개 화면 전면 회귀 중 발견 — 원인은 마크다운 에디터와 무관한 오래된 뷰 배선 문제라 별도 분리) | legacy(v1.6) 재조사 결과 `#review-form`/`yobi.CodeCommentBox.js`는 애초에 diff 라인/스레드로 동적 이동되는 팝오버 템플릿일 뿐이었고, "범위 없는 일반 댓글 작성"은 포팅 과정에서 통째로 누락된 별도 파일 `common.commentForm`이 담당하던 기능이었음을 확인 — `#review-form`/`yobi.CodeCommentBox.js` 완전 삭제, `common/commentForm.html` 신규 작성(legacy와 동일 위치, `<yona-markdown-editor>` 부착)으로 대체. 라인별 인라인삽입 폼도 `<yona-markdown-editor>`로 교체 완료. Playwright로 두 경로 모두 실제 작성/제출/스레드 반영 확인 | [[tickets/p3-55]] |
| P3-56 | [x] | SVN 커밋 상세 화면(`code/svnDiff.html`)의 댓글 기능이 legacy 대비 크게 축소된 즉석 구현으로 남아있음 — 마크다운 에디터/파일첨부/멘션/권한기반삭제/삭제확인모달/마크다운렌더링 전부 없음 (2026-09-11, TEMPLATE_BACKLOG.md 호출부 완전성 감사에서 발견 — `common/commentForm.scala.html`(#24)/`common/commentDeleteModal.scala.html`(#26)의 실제 legacy 호출부에 `code/svnDiff.scala.html`이 있었는데 문서에 전혀 언급되지 않아 검증망을 빠져나간 사례, P3-55와 동일 패턴의 재발) | 백엔드(`ReviewViewController`/`ReviewApiController`/`CodeReviewServiceImpl`)는 이미 legacy와 동일한 댓글 CRUD+권한체크를 갖추고 있어 순수 템플릿 레이어의 문제로 확인됨. 착수 전 조사만 완료, 구현은 아직 안 함 | [[tickets/p3-56]] |
| P3-57 | [x] | 이슈 상세/이슈 목록 화면에 키보드 단축키 안내(`help/keymap`) 도움말이 빠져 있거나(issue/view.html) 리팩터 이전 방식(issue/list.html에 하드코딩 복제)으로 남아있음 (2026-09-11, TEMPLATE_BACKLOG.md #236 호출부 완전성 감사 — 실제 legacy 호출부 4곳 중 문서가 board 2곳만 "함께 수정"했다고 기록, issue 쪽 2곳이 감사망을 빠져나감) | board/view.html·board/list.html은 TASK-0249에서 파라미터화된 `help/keymap :: keymap(section, project)` fragment로 정상 전환됐지만 issue 쪽은 대상에서 누락됨. 착수 전 조사만 완료 | [[tickets/p3-57]] |
| P3-58 | [x] | 조직(organization) 범위 검색 결과 화면(`search/list.html`)에 조직 헤더/내비게이션 메뉴(`organization/header`, `organization/menu`)가 빠져 있음 (2026-09-11, TEMPLATE_BACKLOG.md #196/#197 호출부 완전성 감사 — 실제 legacy 호출부에 `search/result.scala.html`이 있었는데 문서에 전혀 기재되지 않아 발견) | `error/forbidden_organization.html`은 동일 fragment를 이미 정상 재사용 중(격차 아님, 대조군). `search/list.html`만 즉석 `.org-header` 블록으로 대체돼 있어 조직 탐색 메뉴/로고/브레드크럼/가입요청 드롭다운이 전부 사라짐. 우선순위는 낮을 수 있음(부가 기능). 착수 전 조사만 완료 | [[tickets/p3-58]] |
| P3-59 | [x] | 게시판 목록(`board/list.html`)에 "2단 모드" 체크박스 마크업이 없어 `yona.twoColumnMode.js`가 로드는 되지만 조작할 대상이 없는 죽은 스크립트 include로 남아있음 (2026-09-11, TEMPLATE_BACKLOG.md #36 호출부 완전성 감사 — `common/twoColumnModeCheckboxArea.scala.html`의 실제 legacy 호출부 7곳 중 문서가 "확인 완료"로 뭉뚱그린 나머지를 개별 검증하다 발견) | 나머지 6개 호출부(issue/list, issue/my_partial_search, user/view, organization/boardList, organization/issueSearch_partial, pullrequest/partial_search)는 전부 정상 이식 확인됨 — board/list.html 한 곳만 예외. 저위험(스타일/토글 UI). 착수 전 조사만 완료 | [[tickets/p3-59]] |
| P3-60 | [x] | 이슈/게시글 댓글 본문의 GFM 체크리스트(tasklist)에 진행률 바/상호작용 셸(`.tasklist`)이 없음 — 본문(issue/board 상단)에만 있고 댓글에는 없음, `data-allowed-update` 속성도 댓글 쪽엔 없어 수정 권한 없는 사용자의 체크박스가 비활성화되지 않는 문제도 함께 발견 (2026-09-11, TEMPLATE_BACKLOG.md #35 호출부 완전성 감사 — `common/tasklistBar.scala.html`의 실제 legacy 호출부 4곳 중 댓글 쪽 2곳(issue/partial_comment, board/partial_comments)이 문서에 기재돼 있지 않아 발견) | `issue/view.html`/`board/view.html` 본문에는 `.tasklist` 셸+`data-allowed-update`가 있지만 댓글 루프에는 둘 다 없음(각 파일 grep 결과 `.tasklist` 매치 1건뿐). 체크박스 클릭 시 실제 저장까지 되는지(PATCH 경로가 댓글 구조에서 올바른 폼을 찾는지)는 추가 조사 필요(확실치 않음으로 티켓에 기록). 착수 전 조사만 완료 | [[tickets/p3-60]] |
| P3-61 | [x] | 이슈 목록 화면(프로젝트 범위)에서 마일스톤으로 필터링해도 마일스톤 진행률 카드가 안 보임 (2026-09-11, Twirl 컴파일러 산출물(target/scala-2.10/twirl) 기반 실제 호출 그래프 정적 분석에서 발견 — 정규식 기반 감사보다 한 단계 더 정확한 방법으로 재검증) | `docs/TEMPLATE_BACKLOG.md` #153(TASK-0253)이 "`issue/partial_searchform`는 cross-project 전용이라 포팅 범위 밖"이라 내린 제외 판단이 틀렸음을 확인 — `issue/partial_searchform.scala.html`은 project를 필수 파라미터로 받고 프로젝트 범위 이슈 목록(`issue/list.html`)에서 쓰이는 파일이었음. 프로젝트 범위 이슈 목록에 마일스톤 진행률 카드(`milestone/partial_status` 재사용)를 배선 완료. "내 이슈"(`issue/my_partial_search.html`) 쪽은 마일스톤 필터 UI 자체가 legacy엔 있었는데 yona엔 통째로 없어(진행률 카드 배선이 아니라 신규 기능 구현이 필요한 별도 범위) 코디네이터 판단으로 [[tickets/p3-62\|P3-62]]로 분리 등록(착수 보류) | [[tickets/p3-61]] |
| P3-62 | [x] | "내 이슈"(cross-project, `/user/issues`) 화면에 마일스톤 필터 자체가 없음(legacy는 `milestoneId` 파라미터 지원 + 진행률 카드가 있었음) (2026-09-12, P3-61 작업 중 "내 이슈 화면도 배선하라"는 원 지시를 이행하려다 발견 — 카드 배선이 아니라 신규 기능 구현이 필요한 범위라 코디네이터가 분리 지시) | 착수 전 재확인 결과 legacy도 이 화면에서 milestoneId로 이슈 목록을 필터링하지 않음(진행률 카드 표시 목적뿐)을 확인 — 리포지토리 변경 없이 `UserViewController.userIssues()`에 milestoneId 파라미터 추가 + `issue/my_partial_search.html`에 진행률 카드 배선으로 범위가 축소됨. TDD+전체회귀(6,638건)+Playwright 실측 검증 완료. 마일스톤 선택 드롭다운 신규 추가는 legacy에도 없던 기능이라 향후 개선 방안으로 티켓에 남기고 착수 보류 | [[tickets/p3-62]] |
